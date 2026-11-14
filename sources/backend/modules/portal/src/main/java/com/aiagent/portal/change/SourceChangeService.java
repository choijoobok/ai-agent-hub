// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.security.portal.PortalChangeOwner;
import com.aiagent.core.security.portal.PortalChangeOwner.*;
import com.aiagent.domain.authz.*;
import com.aiagent.domain.notification.NotificationSender;
import com.aiagent.domain.notification.source.*;
import com.aiagent.domain.notification.source.SourceNotification.*;
import com.aiagent.support.config.ConfigLookup;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

/** 원천 검증은 DB 밖, 사건/명령→수신자→달력/Portal→현재판/순서→원결과/통지는 하나의 commit이다. */
@Service
public class SourceChangeService {
    public static final String PATH="/api/integration/public/identity/receiveChange";
    public static final class Verified {
        private final SourceChangeService owner;private final SourceChangeVerifier verifier;private final SourceChangeVerifier.Configuration configuration;
        private final ExternalUserChange event;private final SourceChangeVerifier.Envelope envelope;
        private Verified(SourceChangeService owner,SourceChangeVerifier verifier,SourceChangeVerifier.Configuration configuration,ExternalUserChange event,SourceChangeVerifier.Envelope envelope){this.owner=owner;this.verifier=verifier;this.configuration=configuration;this.event=event;this.envelope=envelope;}
        public String toString(){return "VerifiedUserChange[보호됨]";}
    }
    private record Prepared(Binding binding,SourceChangeVerifier.Cursor cursor,SourceChangeVerifier.Freshness freshness) {}
    private final Map<String,SourceChangeVerifier> verifiers;private final ChangeStore store;private final PortalChangeOwner accounts;
    private final SourceNotificationService recipients;private final NotificationSender sender;private final ConfigLookup configs;private final Environment environment;
    private final TransactionTemplate tx;private final LongAdder rejected=new LongAdder();
    public SourceChangeService(List<SourceChangeVerifier> installed,ChangeStore store,PortalChangeOwner accounts,SourceNotificationService recipients,NotificationSender sender,ConfigLookup configs,Environment environment,PlatformTransactionManager manager) {
        var values=new HashMap<String,SourceChangeVerifier>();
        for(var verifier:installed)if(values.putIfAbsent(verifier.configuration().sourceKey(),verifier)!=null||verifier.testOnly()&&!Arrays.asList(environment.getActiveProfiles()).contains("system-auth-test"))throw new IllegalStateException("외부 변경 검증기 등록이 올바르지 않다");
        verifiers=Map.copyOf(values);this.store=store;this.accounts=accounts;this.recipients=recipients;this.sender=sender;this.configs=configs;this.environment=environment;tx=new TransactionTemplate(manager);
    }
    public long rejectedEnvelopes(){return rejected.sum();}
    public Verified verify(String method,String path,Map<String,List<String>> headers,byte[] body) {
        outsideTransaction();
        try {
            if(!method.equals("POST")||!path.equals(PATH))throw new ChangeFailure(403,"ROUTE_DENIED");
            var event=ExternalUserChange.parse(body);var sources=headers.getOrDefault("x-aihub-source",List.of());
            if(sources.size()!=1)throw new ChangeFailure(401,"SOURCE_PROOF_REQUIRED");
            if(verifiers.isEmpty())throw new ChangeFailure(503,"VERIFIER_UNAVAILABLE");
            var verifier=verifiers.get(sources.getFirst());if(verifier==null)throw new ChangeFailure(403,"SOURCE_UNREGISTERED");
            var configuration=verifier.configuration();
            if(!configuration.sourceKey().equals(sources.getFirst()))throw new ChangeFailure(403,"SOURCE_CHANGED");
            var envelope=verifier.verifyEnvelope(method,path,headers,body.clone(),event);
            if(envelope==null||!event.businessDigest().equals(envelope.businessDigest())||envelope.bindingKey()==null||!digest(envelope.evidenceDigest()))throw new ChangeFailure(401,"SOURCE_PROOF_REJECTED");
            var verified=new Verified(this,verifier,configuration,event,envelope);current(verified,false);return verified;
        } catch(ChangeFailure failure){rejected.increment();throw failure;}
        catch(RuntimeException failure){rejected.increment();throw new ChangeFailure(503,"VERIFIER_UNAVAILABLE");}
    }
    private void outsideTransaction(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("외부 원천 검증은 DB 보호 밖에서 수행해야 한다");}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private String eventKey(Verified v){return v.event.eventKey(v.configuration.stableNamespace(),v.configuration.epoch());}
    private String subjectDigest(Verified v){return ExternalUserChange.digest("subject/v1",v.configuration.stableNamespace(),v.event.subject());}
    private void current(Verified v,boolean committing) {
        if(v==null||v.owner!=this||!v.configuration.equals(v.verifier.configuration()))throw new ChangeFailure(committing?409:401,"VERIFIER_CHANGED");
        var now=store.now();var e=v.envelope;
        if(e.verifiedAt()==null||e.expiresAt()==null||e.verifiedAt().isAfter(now)||!now.isBefore(e.expiresAt())||!e.verifiedAt().isBefore(e.expiresAt()))throw new ChangeFailure(committing?409:401,"EVIDENCE_EXPIRED");
    }
    private Prepared prepare(Verified v) {
        var binding=accounts.readBinding(v.configuration.sourceKey(),v.event.subject());
        if(binding==null||!binding.mode().equals("EXTERNAL"))return new Prepared(binding,new SourceChangeVerifier.Cursor(0,null,null),null);
        if(!binding.bindingKey().equals(v.envelope.bindingKey()))throw new ChangeFailure(409,"BINDING_CHANGED");
        var cursor=store.cursor(v.configuration,subjectDigest(v),binding.bindingKey());
        var freshness=v.verifier.verifyFreshness(v.event,v.envelope,binding,cursor);
        if(freshness==null||freshness.order()==null||!Objects.equals(freshness.bindingKey(),binding.bindingKey())||freshness.previousCursorVersion()!=cursor.version()||!v.event.sourceVersion().equals(freshness.sourceVersion())||!Set.of("MONOTONIC","CURRENT_STATE").contains(freshness.orderMode())||!digest(freshness.digest())||freshness.expiresAt()==null)throw new ChangeFailure(409,"FRESHNESS_REQUIRED");
        return new Prepared(binding,cursor,freshness);
    }
    public ChangeStore.Receipt receive(Verified v) {
        outsideTransaction();
        try {
            current(v,false);
            String key=eventKey(v);Prepared prepared=null;
            if(store.findEvent(key)==null&&!accounts.expiredEvent(key)) {
                try{prepared=prepare(v);}catch(RuntimeException failure){if(store.findEvent(key)==null)throw failure;}
            }
            Prepared ready=prepared;
            return configs.inNewSnapshot(()->tx.execute(status->accept(v,ready)));
        } catch(ChangeFailure failure){throw failure;}
        catch(RuntimeException failure){
            org.slf4j.LoggerFactory.getLogger(SourceChangeService.class).warn("외부 사건 처리 실패 type={} at={}",failure.getClass().getName(),Arrays.stream(failure.getStackTrace()).filter(frame->frame.getClassName().startsWith("com.aiagent.")).limit(6).toList());
            throw new ChangeFailure(503,"RECEIPT_UNAVAILABLE");
        }
    }
    private ChangeStore.Receipt accept(Verified v,Prepared prepared) {
        String key=eventKey(v);store.lock(key);current(v,true);
        if(accounts.expiredEvent(key))throw new ChangeFailure(409,"EXPIRED");
        var old=store.findEvent(key);
        if(old!=null) {
            if(!old.businessDigest().equals(v.event.businessDigest()))throw new ChangeFailure(409,"EVENT_BODY_CONFLICT");
            accounts.protectCommand(old.commandKey());
            if(old.receipt().notificationState().equals("PENDING_NO_RECIPIENT"))notifyWithin(v,old.receipt().receiptId(),old.portalUserId(),()->old);
            current(v,true);
            return publicReceipt(store.findEvent(key),true);
        }
        if(prepared==null)throw new ChangeFailure(409,"FRESHNESS_REQUIRED");
        String command=UUID.randomUUID().toString();accounts.protectCommand(command);
        long receipt=store.next("AI_PORTAL_USER_CHANGE");String id=Long.toString(receipt);
        notifyWithin(v,id,prepared.binding()==null?null:prepared.binding().portalUserId(),()->{
            var actual=accounts.protectBinding(prepared.binding());
            if(prepared.binding()==null)actual=accounts.readBinding(v.configuration.sourceKey(),v.event.subject());
            current(v,true);Instant now=store.now();
            if(!Objects.equals(prepared.binding(),actual))throw new ChangeFailure(409,"PORTAL_REVISION_CHANGED");
            var freshness=prepared.freshness();Applied applied;String code;
            if(actual==null||!actual.mode().equals("EXTERNAL")){applied=new Applied("REJECTED",null,null,null,null);code="BINDING_UNAVAILABLE";}
            else {
                if(!actual.bindingKey().equals(v.envelope.bindingKey()))throw new ChangeFailure(409,"BINDING_CHANGED");
                var cursor=store.cursor(v.configuration,subjectDigest(v),actual.bindingKey());
                if(!cursor.equals(prepared.cursor()))throw new ChangeFailure(409,"CURSOR_CHANGED");
                if(freshness==null||!now.isBefore(freshness.expiresAt())||freshness.order()==SourceChangeVerifier.Order.UNKNOWN)throw new ChangeFailure(409,"FRESHNESS_REQUIRED");
                if(v.event.sourceVersion().equals(cursor.sourceVersion()))throw new ChangeFailure(409,"SOURCE_VERSION_REPLAY");
                if(freshness.order()==SourceChangeVerifier.Order.PAST){applied=new Applied("REJECTED",actual.portalUserId(),actual.portalRevision(),null,null);code="STALE_EVENT";}
                else {
                    applied=accounts.apply(new Origin(v.configuration.sourceKey(),id,command,key,v.event.kind()),actual,now);code=applied.outcome();
                    store.advance(v.configuration,subjectDigest(v),freshness,receipt,now);
                }
            }
            var inserted=store.insert(receipt,command,v.configuration,v.event,v.envelope,freshness,applied,code,now);
            if(applied.historyNo()!=null)accounts.protectHistory(id,applied);
            return inserted;
        });
        return publicReceipt(store.findEvent(key),false);
    }
    private void notifyWithin(Verified v,String receipt,String user,Supplier<ChangeStore.Stored> effect) {
        var origin=new VerifiedSourceNotificationOrigin(receipt,eventKey(v),ChangeKind.valueOf(v.event.kind().name()),new SafeTargetRef("HUB_PORTAL_USER",user==null?"{}":"{\"portalUserId\":\""+user+"\"}"));
        var executed=new AtomicBoolean();
        var policy=new RecipientPolicyRef(environment.getProperty("app.deployment.id","unconfigured"),environment.getProperty("app.portal.change.recipient-policy-version","unconfigured"),Arrays.stream(environment.getProperty("app.portal.change.recipient-roles","").split(",")).toList(),Set.of(new MenuAction("HUB_PORTAL_USER",ActionType.READ)));
        var result=recipients.withCurrentSourceNotificationRecipients(origin,policy,snapshot->{
            executed.set(true);var record=effect.get();current(v,true);
            if(record.receipt().notificationState().equals("NOT_REQUIRED"))return new NoRecipients();
            var stored=sender.storeSourceChangeNotification(origin,snapshot);store.notifications(record,stored.notifications(),stored.policyVersion(),store.now());return stored;
        });
        if(result instanceof Unavailable)throw new ChangeFailure(503,"NOTIFICATION_UNAVAILABLE");
        if(!executed.get()){effect.get();current(v,true);}
    }
    private ChangeStore.Receipt publicReceipt(ChangeStore.Stored stored,boolean duplicate){var r=stored.receipt();return new ChangeStore.Receipt(r.receiptId(),r.sourceEventId(),r.outcome(),r.safeCode(),r.appliedAt(),r.notificationState(),duplicate);}
}
