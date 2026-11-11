// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;

/** DB 수락 원장과 선택 payload 저장소의 요청/발행 프로토콜. 제품 계정 판정은 소유 포트다. */
public final class SessionRuntime implements SessionInspectionPort {
    public record AuthorityBinding(DataSource source,PlatformTransactionManager manager,SessionAuthority authority) {
        public AuthorityBinding {Objects.requireNonNull(authority);}
    }
    public record SlotPolicy(boolean enabled,int maximum,boolean preventLogin) {
        public SlotPolicy {if(maximum<1)throw new IllegalArgumentException("동시 접속 한도는 양수여야 한다");}
    }
    private final SessionAdmissionLedger ledger;
    private final SessionStoreAdapter store;
    private final SessionTransactions transactions;
    private final Map<AuthDomain,Settings> selected;
    private final SessionAuthority authority;
    private final Duration timeout;
    private final SecureRandom random=new SecureRandom();
    public SessionRuntime(SessionAdmissionLedger ledger,SessionStoreAdapter store,SessionTransactions transactions,Settings selected,Set<AuthDomain> enabled,AuthorityBinding authority,Duration timeout){
        this(ledger,store,transactions,enabled.stream().collect(java.util.stream.Collectors.toMap(domain->domain,domain->selected)),authority,timeout);
    }
    public SessionRuntime(SessionAdmissionLedger ledger,SessionStoreAdapter store,SessionTransactions transactions,Map<AuthDomain,Settings> selected,AuthorityBinding authority,Duration timeout){
        this.ledger=Objects.requireNonNull(ledger);this.store=Objects.requireNonNull(store);this.transactions=Objects.requireNonNull(transactions);this.selected=Map.copyOf(selected);
        if(!transactions.boundTo(authority.source(),authority.manager())||timeout.isNegative()||timeout.isZero()||selected.isEmpty())throw new IllegalArgumentException("소유 보호와 세션은 같은 REQUIRED DB 조립이어야 한다");
        this.authority=authority.authority();this.timeout=timeout;
        selected.forEach((domain,settings)->{if(settings.provider()!=store.provider())throw new IllegalArgumentException("선택 저장소 종류가 다르다");checkSettings(domain,false);});store.verifyAvailable();
    }
    private void checkSettings(AuthDomain domain,boolean lock){if(!Objects.equals(selected.get(domain),ledger.settings(domain,lock)))throw new Failure(Outcome.UNAVAILABLE);}
    private String nonce(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hash(AuthDomain domain,String cookie){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((domain.name()+":"+cookie).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException failure){throw new IllegalStateException("SHA-256을 사용할 수 없다",failure);}
    }
    private PrincipalRef principal(Grant grant){return grant.subjectId()==null?null:new PrincipalRef(grant.ref().domain(),grant.subjectId());}
    private void validatePayload(Grant grant,Stored stored){
        if(!grant.ref().equals(stored.payload().ref()))throw new Failure(Outcome.INVALID_SESSION);Identity identity=stored.payload().identity();
        if(grant.level()==AuthLevel.ANONYMOUS?identity!=null:identity==null||!principal(grant).equals(identity.principal())||identity.authLevel()!=grant.level()||!Objects.equals(grant.authenticatedAt(),identity.authenticatedAt()))throw new Failure(Outcome.INVALID_SESSION);
    }
    public Scope open(SessionExchange request,ModuleApiCatalog.Operation operation,String cookie){
        if(operation.domain()==ModuleApiCatalog.Domain.NONE||!request.domain().name().equals(operation.domain().name())||request.capability()!=operation.capability()||!selected.containsKey(request.domain()))throw new Failure(Outcome.INVALID_SESSION);
        var scope=new Scope(request,operation,cookie);scope.publicRead=operation.path().contains("/public/")&&operation.capability()==Capability.READ;
        if(scope.publicRead){if(!operation.levels().equals(Set.of(AuthLevel.values())))throw new Failure(Outcome.INVALID_SESSION);return scope;}checkSettings(request.domain(),false);
        if(cookie!=null){
            if(!cookie.matches("[A-Za-z0-9_-]{43}"))throw new Failure(Outcome.INVALID_SESSION);
            Grant grant=ledger.byCookie(request.domain(),hash(request.domain(),cookie),false).orElse(null);
            if(grant!=null){
                if(!grant.current(ledger.now())){if(request.capability()!=Capability.PREPARE_CSRF||grant.status()==Status.PENDING)throw new Failure(Outcome.INVALID_SESSION);}
                else{
                    if(grant.provider()!=store.provider()||!grant.ref().epoch().equals(selected.get(request.domain()).epoch()))throw new Failure(Outcome.INVALID_SESSION);
                    scope.loaded=store.load(grant.ref(),principal(grant)).orElseThrow(()->new Failure(Outcome.INVALID_SESSION));validatePayload(grant,scope.loaded);scope.grant=grant;
                }
            }
        }
        if(scope.grant==null&&request.capability()!=Capability.PREPARE_CSRF)throw new Failure(Outcome.INVALID_SESSION);
        if(!operation.levels().contains(scope.grant==null?AuthLevel.ANONYMOUS:scope.grant.level()))throw new com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected(scope.grant==null||scope.grant.level()==AuthLevel.ANONYMOUS?401:403);return scope;
    }
    private SessionAuthority.Context context(Scope scope,Identity identity,Ref ref){return new SessionAuthority.Context(scope.request.domain(),identity==null?null:identity.principal(),ref,identity,scope.operation.id(),scope.operation.requiredAction(),scope.operation.selfScope(),scope.request.capability(),scope.request.traceId());}
    private Grant current(Scope scope,boolean lock){
        checkSettings(scope.request.domain(),lock);Grant latest=ledger.find(scope.grant.ref(),lock).orElseThrow(()->new Failure(Outcome.INVALID_SESSION));
        if(!latest.current(ledger.now())||!latest.cookieHash().equals(hash(scope.request.domain(),scope.cookie)))throw new Failure(Outcome.INVALID_SESSION);validatePayload(latest,scope.loaded);return latest;
    }
    public <T>T execute(Scope scope,Supplier<T> command){
        if(scope.executed)throw new IllegalStateException("업무 요청은 한 번만 실행한다");scope.executed=true;
        try{return transactions.required(scope.request,()->{
            var result=new Object[1];int[] calls={0};
            authority.protect(context(scope,scope.loaded==null?null:scope.loaded.payload().identity(),scope.grant==null?null:scope.grant.ref()),scope.operation.exclusive(),()->{
                transactions.attach(scope.request);if(++calls[0]!=1)throw new IllegalStateException("소유 보호 callback은 한 번만 실행한다");scope.protectedNow=true;
                try{lockRequests(scope);if(scope.grant!=null)scope.grant=current(scope,false);
                    result[0]=command.get();if(scope.publication==null&&scope.grant!=null&&!scope.publicRead&&!scope.logout)touch(scope);
                }finally{scope.protectedNow=false;}
            });if(calls[0]!=1)throw new IllegalStateException("소유 보호 callback이 실행되지 않았다");
            @SuppressWarnings("unchecked") T value=(T)result[0];return value;
        });}catch(RuntimeException failure){scope.request.close();throw failure;}
    }
    private void lockRequests(Scope scope){
        if(scope.publicRead)return;checkSettings(scope.request.domain(),true);Set<Ref> refs=new HashSet<>();
        if(scope.grant!=null)refs.add(scope.grant.ref());if(scope.inspection!=null)refs.addAll(scope.inspection.candidates);
        refs.stream().sorted(Comparator.comparingLong(Ref::grantId)).forEach(ref->ledger.find(ref,true));
    }
    private void touch(Scope scope){
        Grant old=scope.grant;Instant now=ledger.now();if(!old.current(now))throw new Failure(Outcome.INVALID_SESSION);boolean first=old.level()!=AuthLevel.ANONYMOUS&&!scope.operation.path().contains("/public/");
        Instant accessed=now.isAfter(old.lastAccessAt())?now:old.lastAccessAt();Instant proposed=accessed.plus(old.level()==AuthLevel.ANONYMOUS?Duration.ofMinutes(10):timeout);Instant expiry=proposed.isAfter(old.expiresAt())?proposed:old.expiresAt();
        Grant changed=copy(old,old.ref(),old.status(),old.cookieHash(),old.managementRef(),old.subjectId(),old.level(),old.authenticatedAt(),accessed,expiry,old.publishDeadline(),first&&old.firstUsedAt()==null?now:old.firstUsedAt(),old.confirmDeadline(),old.terminalAt());
        if(scope.request.state()==SessionExchange.State.OPEN)scope.request.mutation();if(!ledger.replace(old,changed))throw new Failure(Outcome.INVALID_SESSION);scope.grant=changed;
        if(store.provider()==Provider.JDBC)checkFlush(scope,store.updateExisting(scope.loaded,scope.delta==null?scope.loaded.payload():scope.delta,changed.expiresAt()));else scope.flush=true;
    }
    private static Grant copy(Grant old,Ref ref,Status status,String cookie,String management,String subject,AuthLevel level,Instant authenticated,Instant accessed,Instant expires,Instant publish,Instant first,Instant confirm,Instant terminal){return new Grant(ref,old.provider(),cookie,management,subject,level,status,authenticated,old.createdAt(),accessed,expires,publish,first,confirm,terminal);}
    private void checkFlush(Scope scope,Outcome outcome){
        if(outcome==Outcome.APPLIED)return;
        if(outcome==Outcome.STALE_REVISION){Grant latest=current(scope,false);Stored stored=store.load(latest.ref(),principal(latest)).orElseThrow(()->new Failure(Outcome.INVALID_SESSION));validatePayload(latest,stored);return;}
        throw new Failure(outcome==Outcome.UNAVAILABLE?Outcome.UNAVAILABLE:Outcome.INVALID_SESSION);
    }
    private void revoke(Grant grant){if(grant.status()==Status.REVOKED)return;Instant now=ledger.now();if(!ledger.replace(grant,copy(grant,grant.ref(),Status.REVOKED,grant.cookieHash(),grant.managementRef(),grant.subjectId(),grant.level(),grant.authenticatedAt(),grant.lastAccessAt(),grant.expiresAt(),grant.publishDeadline(),grant.firstUsedAt(),grant.confirmDeadline(),now)))throw new Failure(Outcome.INVALID_SESSION);}
    private void requireProtected(Scope scope){transactions.requireCurrent();if(!scope.protectedNow)throw new IllegalStateException("현재 소유 권한 보호 안에서만 수행한다");transactions.attach(scope.request);}
    private Identity normalized(Identity identity){return identity==null?null:new Identity(identity.principal(),identity.authLevel(),identity.authMode(),identity.credentialVersion(),identity.authenticatedAt().truncatedTo(ChronoUnit.MICROS),identity.displayName(),identity.locale());}
    private void publish(Scope scope,SessionAdmissionsV2.Proof proof,SlotPolicy policy){
        requireProtected(scope);if(!scope.operation.exclusive())throw new IllegalStateException("발급은 최초 진입부터 배타 보호해야 한다");Instant now=ledger.now();
        if(proof.kind()==SessionAdmissionsV2.Kind.ROTATION&&(scope.loaded==null||scope.loaded.payload().identity()==null
                ||proof.identity()==null||!scope.loaded.payload().identity().principal().equals(proof.identity().principal())))throw new Failure(Outcome.INVALID_SESSION);
        if(!Objects.equals(proof.entry(),scope.grant==null?null:scope.grant.ref())||!proof.consume(scope.request,now))throw new Failure(Outcome.INVALID_SESSION);
        Identity identity=normalized(proof.identity());var ref=new Ref(scope.request.domain(),proof.kind()==SessionAdmissionsV2.Kind.ROTATION?scope.grant.ref().grantId():ledger.nextId(),proof.kind()==SessionAdmissionsV2.Kind.ROTATION?scope.grant.ref().generation()+1:1,selected.get(scope.request.domain()).epoch());
        int[] calls={0};authority.protect(context(scope,identity,ref),true,()->{
            transactions.attach(scope.request);if(++calls[0]!=1)throw new IllegalStateException("발급 보호 callback은 한 번만 실행한다");checkSettings(scope.request.domain(),true);
            if(scope.grant!=null)scope.grant=current(scope,true);
            if(identity!=null){
                var slots=ledger.principal(identity.principal(),true).stream().filter(grant->scope.grant==null||!grant.ref().equals(scope.grant.ref())).filter(grant->grant.reservesSlot(now)).sorted(Comparator.comparing(Grant::lastAccessAt).thenComparing(Grant::createdAt)).toList();
                if(policy.enabled()&&slots.size()>=policy.maximum()){if(policy.preventLogin())throw new Failure(Outcome.INVALID_SESSION);slots.stream().limit(slots.size()-policy.maximum()+1L).forEach(this::revoke);}
                if(proof.kind()==SessionAdmissionsV2.Kind.ROTATION)slots.stream().filter(grant->grant.status()==Status.PENDING).forEach(this::revoke);
            }
            scope.request.mutation();String cookie=nonce();var payload=new Payload(ref,identity,new Secret(nonce()),identity==null?null:identity.displayName(),identity==null?null:identity.locale(),Set.of());
            Instant expiry=now.plus(identity==null?Duration.ofMinutes(10):timeout);boolean redis=store.provider()==Provider.REDIS;
            Grant grant=new Grant(ref,store.provider(),hash(scope.request.domain(),cookie),nonce(),identity==null?null:identity.principal().subjectId(),identity==null?AuthLevel.ANONYMOUS:identity.authLevel(),redis?Status.PENDING:Status.ACTIVE,identity==null?null:identity.authenticatedAt(),scope.grant!=null&&proof.kind()==SessionAdmissionsV2.Kind.ROTATION?scope.grant.createdAt():now,now,expiry,redis?now.plusSeconds(60):null,null,identity==null||redis?null:now.plusSeconds(60),null);
            if(scope.grant!=null){if(store.provider()==Provider.JDBC)store.delete(scope.loaded);
                if(proof.kind()==SessionAdmissionsV2.Kind.ROTATION){if(!ledger.replace(scope.grant,grant))throw new Failure(Outcome.INVALID_SESSION);}else{revoke(scope.grant);ledger.insert(grant);}
            }else ledger.insert(grant);
            if(!redis)scope.loaded=store.create(payload,expiry);scope.grant=grant;scope.publication=payload;scope.issue=new CookieIssue(scope.request,cookie,payload.csrf());
        });if(calls[0]!=1)throw new IllegalStateException("발급 보호 callback이 실행되지 않았다");
    }
    public Outcome finish(Scope scope){
        SessionTransactions.requireOutsideTransaction();if(scope.finished)throw new IllegalStateException("요청 완료는 한 번만 수행한다");scope.finished=true;
        if(scope.request.state()!=SessionExchange.State.DB_COMMITTED){scope.request.close();return Outcome.APPLIED;}
        try{
            if(scope.publication!=null&&store.provider()==Provider.REDIS){
                scope.loaded=store.create(scope.publication,scope.grant.expiresAt());var continuation=new SessionExchange(scope.request.domain(),scope.request.traceId(),scope.request.capability());
                transactions.required(continuation,()->{
                    int[] calls={0};authority.protect(context(scope,scope.publication.identity(),scope.grant.ref()),true,()->{
                        transactions.attach(continuation);if(++calls[0]!=1)throw new IllegalStateException("발행 보호 callback은 한 번만 실행한다");checkSettings(scope.request.domain(),true);
                        Grant pending=ledger.find(scope.grant.ref(),true).orElseThrow(()->new Failure(Outcome.INVALID_SESSION));Instant now=ledger.now();
                        if(pending.status()!=Status.PENDING||pending.publishDeadline()==null||!now.isBefore(pending.publishDeadline())||!now.isBefore(pending.expiresAt())||!pending.cookieHash().equals(scope.grant.cookieHash()))throw new Failure(Outcome.INVALID_SESSION);
                        validatePayload(pending,scope.loaded);continuation.mutation();Grant active=copy(pending,pending.ref(),Status.ACTIVE,pending.cookieHash(),pending.managementRef(),pending.subjectId(),pending.level(),pending.authenticatedAt(),pending.lastAccessAt(),pending.expiresAt(),null,null,pending.level()==AuthLevel.ANONYMOUS?null:now.plusSeconds(60),null);
                        if(!ledger.replace(pending,active))throw new Failure(Outcome.INVALID_SESSION);scope.grant=active;
                    });if(calls[0]!=1)throw new IllegalStateException("발행 보호 callback이 실행되지 않았다");return null;
                });if(continuation.state()!=SessionExchange.State.DB_COMMITTED)throw new Failure(Outcome.INDETERMINATE);
            }else if(scope.flush){
                try{checkFlush(scope,store.updateExisting(scope.loaded,scope.delta==null?scope.loaded.payload():scope.delta,scope.grant.expiresAt()));}
                catch(RuntimeException failure){if(!scope.managementRevocation)throw failure;scope.cleanupFailures++;org.slf4j.LoggerFactory.getLogger(SessionRuntime.class).warn("확정된 관리 회수 요청의 세션 갱신을 완료하지 못했다");}
            }
            if(scope.issue==null){scope.request.close();cleanup(scope);return Outcome.APPLIED;}scope.request.storeConfirmed();scope.request.activated();cleanup(scope);return Outcome.DELIVERABLE;
        }catch(RuntimeException failure){
            scope.request.close();cleanup(scope);
            if(scope.publication!=null&&store.provider()==Provider.REDIS){
                try{transactions.required(()->{ledger.find(scope.grant.ref(),true).filter(grant->grant.status()==Status.PENDING).ifPresent(this::revoke);return null;});}catch(RuntimeException cleanupFailure){failure.addSuppressed(cleanupFailure);}
                if(Set.of(Capability.PASSWORD_CHANGE,Capability.RESET).contains(scope.request.capability()))throw new Failure(Outcome.REAUTH_REQUIRED);
            }if(failure instanceof Failure boundary)throw boundary;throw new Failure(Outcome.UNAVAILABLE);
        }
    }
    private void cleanup(Scope scope){for(Stored stored:scope.cleanup){try{if(store.provider()==Provider.JDBC)transactions.required(()->store.delete(stored));else store.delete(stored);}catch(RuntimeException failure){scope.cleanupFailures++;org.slf4j.LoggerFactory.getLogger(SessionRuntime.class).warn("회수된 세션 payload 정리를 완료하지 못했다");}}}
    private record Inspection(SessionRuntime owner,PrincipalRef principal,List<Ref> candidates,Map<Ref,Stored> payloads) implements Plan {}
    public Plan prepare(PrincipalRef principal){
        SessionTransactions.requireOutsideTransaction();if(!selected.containsKey(principal.domain()))throw new Failure(Outcome.INVALID_SESSION);checkSettings(principal.domain(),false);
        var grants=ledger.principal(principal,false);Map<Ref,Stored> payloads=new HashMap<>();for(Grant grant:grants)if(grant.current(ledger.now()))store.load(grant.ref(),principal).ifPresent(stored->{validatePayload(grant,stored);payloads.put(grant.ref(),stored);});return new Inspection(this,principal,grants.stream().map(Grant::ref).toList(),Map.copyOf(payloads));
    }
    public final class Scope implements SessionInspectionPort.Authorized {
        private final SessionExchange request;private final ModuleApiCatalog.Operation operation;private final String cookie;
        private Grant grant;private Stored loaded;private Payload publication,delta;private CookieIssue issue;private Inspection inspection;
        private boolean executed,protectedNow,finished,flush,publicRead,logout,managementRevocation;private int cleanupFailures;
        private final List<Stored> cleanup=new ArrayList<>();
        private Scope(SessionExchange request,ModuleApiCatalog.Operation operation,String cookie){this.request=request;this.operation=operation;this.cookie=cookie;}
        public SessionExchange exchange(){return request;}
        public void bindInspection(Plan plan){SessionTransactions.requireOutsideTransaction();if(executed||inspection!=null||!(plan instanceof Inspection candidate)||candidate.owner!=SessionRuntime.this||candidate.principal.domain()!=request.domain())throw new IllegalStateException("현재 요청의 서버 조회계획을 실행 전에 한 번만 결합한다");inspection=candidate;}
        public Plan inspectionFor(PrincipalRef target){if(inspection==null||!inspection.principal.equals(target))throw new com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected(403);return inspection;}
        public Optional<Snapshot> current(){return grant==null?Optional.empty():Optional.of(new Snapshot(grant.ref(),publication!=null?publication.identity():loaded.payload().identity(),grant.createdAt(),grant.lastAccessAt(),grant.expiresAt(),grant.managementRef()));}
        public boolean matchesCsrf(String token){return loaded!=null&&loaded.payload().csrf().matches(token);}
        public int cleanupFailures(){return cleanupFailures;}
        public void updateSnapshot(String displayName,String locale,Set<String> authorities){requireProtected(this);if(loaded==null||publicRead)throw new Failure(Outcome.INVALID_SESSION);var identity=loaded.payload().identity();var changed=identity==null?null:new Identity(identity.principal(),identity.authLevel(),identity.authMode(),identity.credentialVersion(),identity.authenticatedAt(),displayName,locale);delta=new Payload(loaded.payload().ref(),changed,loaded.payload().csrf(),displayName,locale,authorities);}
        public void prepareCsrf(){requireProtected(this);if(request.capability()!=Capability.PREPARE_CSRF)throw new Failure(Outcome.INVALID_SESSION);
            if(grant==null)publish(SessionAdmissionsV2.anonymous(request,ledger.now().plusSeconds(60)),new SlotPolicy(false,1,false));else{request.mutation();issue=new CookieIssue(request,cookie,loaded.payload().csrf());}
        }
        public void publish(SessionAdmissionsV2.Proof proof,SlotPolicy policy){SessionRuntime.this.publish(this,proof,policy);}
        public void logout(){requireProtected(this);if(request.capability()!=Capability.LOGOUT||grant==null)throw new Failure(Outcome.INVALID_SESSION);request.mutation();SessionRuntime.this.revoke(grant);cleanup.add(loaded);logout=true;issue=CookieIssue.expiration(request);}
        public void emitCookies(CookieSink sink){if(issue==null)throw new IllegalStateException("발급 증거가 없다");issue.emit(request,sink);}
        private Inspection inspection(Plan plan){requireProtected(this);if(inspection==null||plan!=inspection||request.domain()!=AuthDomain.ADMIN||operation.requiredAction()==null)throw new Failure(Outcome.INVALID_SESSION);return inspection;}
        public List<View> inspect(Plan plan){
            var inspected=inspection(plan);if(!operation.requiredAction().equals("READ"))throw new Failure(Outcome.INVALID_SESSION);List<View> views=new ArrayList<>();
            for(Grant target:ledger.principal(inspected.principal,false)){Stored stored=inspected.payloads.get(target.ref());if(stored!=null&&target.current(ledger.now())){validatePayload(target,stored);views.add(new View(target.managementRef(),grant!=null&&grant.ref().equals(target.ref()),target.createdAt(),target.lastAccessAt(),target.expiresAt()));}}return List.copyOf(views);
        }
        public int revoke(Plan plan,List<String> refs){
            var inspected=inspection(plan);if(!operation.requiredAction().equals("EXECUTE"))throw new Failure(Outcome.INVALID_SESSION);
            if(refs.stream().anyMatch(ref->ref==null||!ref.matches("[A-Za-z0-9_-]{43}")))throw new IllegalArgumentException("세션 관리 참조 형식이 올바르지 않다");Set<String> targets=Set.copyOf(refs);int count=0;
            if(!operation.exclusive())throw new IllegalStateException("관리 회수는 최초 진입부터 배타 보호해야 한다");
            managementRevocation=true;
            for(Grant target:ledger.principal(inspected.principal,false))if(inspected.candidates.contains(target.ref())&&target.current(ledger.now())&&targets.contains(target.managementRef())){SessionRuntime.this.revoke(target);count++;if(grant!=null&&target.ref().equals(grant.ref()))logout=true;Stored stored=inspected.payloads.get(target.ref());if(stored!=null)cleanup.add(stored);}
            if(count>0&&request.state()==SessionExchange.State.OPEN)request.mutation();return count;
        }
    }
}
