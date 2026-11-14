// @feat FEAT-001 FEAT-040
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationCleanup;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.support.deployment.TargetConnections;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.aiagent.admin.shared.configuration.ConfigurationCleanup.*;

/** 후보 전체를 고정하고 현재 권한·설정·참조를 같은 경계에서 재검사하는 원자 명령. */
@Service
public class TargetCleanupService {
    private final JdbcTemplate jdbc;
    private final TargetSystemQueryService queries;
    private final TargetSystemService owner;
    private final TargetReferenceService references;
    private final TargetSystemAccess access;
    private final TargetConnections loaded;
    private final ConfigurationDeploymentService deployment;
    private final AssetJournal journal;
    private final ManagedRegistry registry;
    private final ManagedAdmission admission;
    private final ManagedLocks locks;
    private final ManagedCommandGuard guard;
    private final ManagedIds ids;
    private final ManagedHistoryService history;
    private final Clock clock;
    public TargetCleanupService(DataSource source,TargetSystemQueryService queries,TargetSystemService owner,TargetReferenceService references,
        TargetSystemAccess access,TargetConnections loaded,ConfigurationDeploymentService deployment,AssetJournal journal,ManagedRegistry registry,
        ManagedAdmission admission,ManagedLocks locks,ManagedCommandGuard guard,ManagedIds ids,ManagedHistoryService history,Clock clock){
        jdbc=new JdbcTemplate(source);this.queries=queries;this.owner=owner;this.references=references;this.access=access;this.loaded=loaded;
        this.deployment=deployment;this.journal=journal;this.registry=registry;this.admission=admission;this.locks=locks;this.guard=guard;
        this.ids=ids;this.history=history;this.clock=clock;
    }
    @Transactional
    public Preview preview(Query query){
        access.require(ActionType.READ,ActionType.SAVE);
        locks.ordered(List.of(ConfigurationDeploymentService.boundary(loaded.environment())));
        deployment.requireReady(loaded);
        var candidates=queries.cleanupCandidates(query);
        if(candidates.size()>10_000)throw ManagedErrorCode.VALIDATION.failure();
        lockParents(candidates);
        String token=UUID.randomUUID().toString();var expires=clock.instant().plusSeconds(300);
        String actor=access.actor();
        jdbc.update("INSERT INTO AI_ADMIN_CLEANUP_PREVIEW(PREVIEW_ID,ACTOR_ID,TARGET_KIND,FILTER_DIGEST,CONFIG_ID,EXPIRES_DTM) VALUES (?,?,?,?,?,?)",token,actor,query.scope(),fingerprint(query),loaded.settingsId(),Timestamp.from(expires));
        var targets=new ArrayList<PreviewTarget>();
        for(var candidate:candidates){
            var t=candidate.target();String reason=blocking(candidate);
            targets.add(new PreviewTarget(t,candidate.name(),reason));
            jdbc.update("INSERT INTO AI_ADMIN_CLEANUP_PREVIEW_ITEM(PREVIEW_ID,TARGET_KIND,TARGET_ID,EXPECTED_VERSION,DIAGNOSIS_ID) VALUES (?,?,?,?,?)",token,t.kind(),Long.valueOf(t.id()),Long.valueOf(t.expectedVersion()),Long.valueOf(candidate.diagnosisId()));
        }
        return new Preview(token,query,loaded.settingsId(),List.copyOf(targets),!targets.isEmpty()&&targets.stream().allMatch(t->t.reason()==null),expires);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Result execute(ManagedCommand raw){
        try{return apply(raw);}catch(org.springframework.dao.DataAccessException failure){throw ManagedErrorCode.UNAVAILABLE.failure();}
    }
    private Result apply(ManagedCommand raw){
        var input=ManagedCommandService.normalize(owner,raw);
        String actor=admission.saveActor("API-074",input);
        access.require(ActionType.READ,ActionType.SAVE);
        if(guard.expired(input.commandKey()))throw ManagedErrorCode.COMMAND_EXPIRED.failure();
        String digest=ManagedDigest.of(actor,owner.feature(),"API-074/TARGET_SYSTEM/CONFIGURATION_CLEANUP",input);
        var previous=registry.find(input.commandKey());
        if(previous.isPresent()){
            var old=previous.get();
            if(!old.actor().equals(actor))throw ManagedErrorCode.FORBIDDEN.failure();
            if(!old.feature().equals(owner.feature())||!old.kind().equals(owner.kind())||!old.digestVersion().equals("managed/v1")||!old.digest().equals(digest))throw ManagedErrorCode.COMMAND_CONFLICT.failure();
            return result(old);
        }
        var values=ConfigurationCleanup.parse(input);
        locks.ordered(List.of(ConfigurationDeploymentService.boundary(loaded.environment())));
        deployment.requireReady(loaded);
        var previews=jdbc.queryForList("SELECT * FROM AI_ADMIN_CLEANUP_PREVIEW WHERE PREVIEW_ID=?",values.previewToken());
        if(previews.size()!=1)throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var preview=previews.getFirst();
        if(!actor.equals(preview.get("ACTOR_ID"))||!values.query().scope().equals(preview.get("TARGET_KIND"))||!loaded.settingsId().equals(preview.get("CONFIG_ID"))
            ||!fingerprint(values.query()).equals(preview.get("FILTER_DIGEST"))||!clock.instant().isBefore(((Timestamp)preview.get("EXPIRES_DTM")).toInstant()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var candidates=queries.cleanupCandidates(values.query());
        lockParents(candidates);
        if(!candidates.stream().map(Candidate::target).toList().equals(values.confirmedTargets()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var stored=jdbc.queryForList("SELECT * FROM AI_ADMIN_CLEANUP_PREVIEW_ITEM WHERE PREVIEW_ID=? ORDER BY TARGET_KIND,TARGET_ID",values.previewToken());
        if(stored.size()!=candidates.size()||candidates.isEmpty())throw ManagedErrorCode.REVISION_CONFLICT.failure();
        for(int i=0;i<candidates.size();i++){
            var candidate=candidates.get(i);var item=stored.get(i);var t=candidate.target();
            if(!t.kind().equals(item.get("TARGET_KIND"))||!t.id().equals(item.get("TARGET_ID").toString())||!t.expectedVersion().equals(item.get("EXPECTED_VERSION").toString())||!candidate.diagnosisId().equals(item.get("DIAGNOSIS_ID").toString()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
            String reason=blocking(candidate);
            if(reason!=null)throw reason.equals("REFERENCED")?ManagedErrorCode.REFERENCE_CONFLICT.failure():ManagedErrorCode.UNAVAILABLE.failure();
        }
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        for(var candidate:candidates)resolveRemovedMetadata(candidate,now);
        var entries=new ArrayList<Applied>();
        if(values.query().scope().equals("SYSTEM")){
            for(var candidate:candidates){var t=candidate.target();var stamp=stamp(input,actor,t.expectedVersion(),now,"DELETE");
                owner.history().capture(t.id(),stamp);
                jdbc.update("DELETE FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=?",Long.valueOf(t.id()));
                jdbc.update("DELETE FROM AI_ADMIN_TARGET_SYSTEM WHERE TARGET_SYSTEM_ID=?",Long.valueOf(t.id()));
                entries.add(new Applied(t.id(),t.kind(),null,t.expectedVersion(),stamp.revision(),Long.toString(stamp.historyNo())));
            }
        }else{
            var first=candidates.getFirst().target();
            var stamp=stamp(input,actor,first.expectedVersion(),now,"CHANGE");
            for(var candidate:candidates)jdbc.update("DELETE FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE CONNECTION_RECORD_ID=? AND TARGET_SYSTEM_ID=?",Long.valueOf(candidate.target().id()),Long.valueOf(first.parentId()));
            jdbc.update("UPDATE AI_ADMIN_TARGET_SYSTEM SET MANAGEMENT_VERSION=?,MOD_ID=?,MOD_DTM=?,MOD_SOURCE_CD='ADMIN',CHANGE_REASON=?,COMMAND_KEY=?,ACCEPTED_DTM=? WHERE TARGET_SYSTEM_ID=?",Long.valueOf(stamp.revision()),actor,Timestamp.from(now),input.reason(),input.commandKey(),Timestamp.from(now),Long.valueOf(first.parentId()));
            owner.history().capture(first.parentId(),stamp);
            // 직전 전체 이력을 보호해 제거된 연결의 값을 보존한다. 현재 판은 제거 후 전체다.
            jdbc.update("UPDATE AI_ADMIN_TARGET_SYSTEM_HIST SET HIST_SUMMARY='CONFIGURATION_CLEANUP' WHERE HISTORY_NO=?",stamp.historyNo());
            history.protectManagedRecord(owner.history().event(stamp.historyNo()));
            for(var candidate:candidates){var t=candidate.target();entries.add(new Applied(t.id(),t.kind(),t.parentId(),t.expectedVersion(),stamp.revision(),Long.toString(stamp.historyNo())));}
        }
        var entry=new ManagedJournal.Entry(input.commandKey(),owner.feature(),actor,digest,"managed/v1",owner.kind(),null,null,null,"APPLIED",now,now);
        journal.insert(entry,"CONFIGURATION_CLEANUP");
        for(var applied:entries)jdbc.update("INSERT INTO AI_ADMIN_ASSET_COMMAND_RESULT(COMMAND_KEY,TARGET_KIND,TARGET_ID,RESULT_VERSION,HISTORY_NO) VALUES (?,?,?,?,?)",input.commandKey(),applied.kind(),Long.valueOf(applied.id()),Long.valueOf(applied.resultVersion()),Long.valueOf(applied.historyNo()));
        return new Result(input.commandKey(),"CONFIGURATION_CLEANUP",now,entries.size(),List.copyOf(entries));
    }
    Result result(ManagedJournal.Entry entry){
        var targets=jdbc.query("SELECT R.*,H.TARGET_SYSTEM_ID FROM AI_ADMIN_ASSET_COMMAND_RESULT R JOIN AI_ADMIN_TARGET_SYSTEM_HIST H ON H.HISTORY_NO=R.HISTORY_NO WHERE R.COMMAND_KEY=? ORDER BY R.TARGET_KIND,R.TARGET_ID",(r,n)->new Applied(r.getString("TARGET_ID"),r.getString("TARGET_KIND"),r.getString("TARGET_KIND").equals("CONNECTION")?r.getString("TARGET_SYSTEM_ID"):null,Long.toString(r.getLong("RESULT_VERSION")-1),r.getString("RESULT_VERSION"),r.getString("HISTORY_NO")),entry.commandKey());
        if(targets.isEmpty())throw ManagedErrorCode.UNAVAILABLE.failure();
        return new Result(entry.commandKey(),"CONFIGURATION_CLEANUP",entry.appliedAt(),targets.size(),targets);
    }
    private ManagedAssetAdapter.Stamp stamp(ManagedCommand input,String actor,String previous,Instant at,String action){
        long next;try{next=Math.addExact(ManagedCommand.positive(previous),1);}catch(ArithmeticException failure){throw ManagedErrorCode.REVISION_CONFLICT.failure();}
        return new ManagedAssetAdapter.Stamp(input.commandKey(),actor,Long.toString(next),ids.next("AI_ADMIN_HISTORY_NO"),at,action,input.reason());
    }
    private String fingerprint(Query query){long control=jdbc.queryForObject("SELECT CONTROL_VERSION FROM AI_ADMIN_TARGET_DEPLOYMENT WHERE ENVIRONMENT_ID=?",Long.class,loaded.environment());return ConfigurationCleanup.digest(query,loaded.bootId().toString(),loaded.settingsId(),control);}
    /** 참조 없는 메타 제거로 점검 원인이 사라진 사실이다. 외부 접속 성공을 뜻하지 않는다. */
    private void resolveRemovedMetadata(Candidate candidate,Instant now){
        var target=candidate.target();
        String system=jdbc.queryForObject("SELECT SYSTEM_KEY FROM AI_ADMIN_TARGET_SYSTEM WHERE TARGET_SYSTEM_ID=?",String.class,Long.valueOf(target.kind().equals("SYSTEM")?target.id():target.parentId()));
        var keys=new ArrayList<String>();
        if(target.kind().equals("SYSTEM")){keys.add(system);for(String connection:jdbc.queryForList("SELECT CONNECTION_ID FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=?",String.class,Long.valueOf(target.id())))keys.add(system+"/"+connection);}
        else keys.add(system+"/"+candidate.connectionId());
        var at=Timestamp.from(now);
        for(String key:keys)for(var alert:jdbc.queryForList("SELECT ALERT_ID,CONFIG_ID FROM AI_ADMIN_CONFIG_ALERT A WHERE ENVIRONMENT_ID=? AND TARGET_KEY=? AND TARGET_KIND IN ('SYSTEM','CONNECTION') AND STATUS_CD='OPEN' AND EXISTS (SELECT 1 FROM AI_ADMIN_TARGET_BOOT B WHERE B.ENVIRONMENT_ID=A.ENVIRONMENT_ID AND B.CONFIG_ID=A.CONFIG_ID AND B.ALLOWED_YN='Y' AND B.READY_YN='Y')",loaded.environment(),key)){
            jdbc.update("UPDATE AI_ADMIN_CONFIG_ALERT SET STATUS_CD='RECOVERED',RECOVERED_DTM=?,LAST_CHECKED_DTM=?,MOD_DTM=? WHERE ALERT_ID=?",at,at,at,alert.get("ALERT_ID"));
            jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT_EVENT(EVENT_ID,ALERT_ID,EVENT_CD,CONFIG_ID,EVIDENCE,REG_DTM) VALUES (?,?,'RECOVERED',?,'METADATA_REMOVED',?)",ids.next("AI_ADMIN_CONFIG_ID"),alert.get("ALERT_ID"),alert.get("CONFIG_ID"),at);
        }
    }
    private void lockParents(List<Candidate> candidates){locks.ordered(candidates.stream().map(c->new ManagedLocks.Key(3,"TARGET_SYSTEM",c.target().kind().equals("SYSTEM")?c.target().id():c.target().parentId())).toList());}
    private String blocking(Candidate candidate){
        var t=candidate.target();String system=t.kind().equals("SYSTEM")?t.id():t.parentId();
        String key=jdbc.queryForObject("SELECT SYSTEM_KEY FROM AI_ADMIN_TARGET_SYSTEM WHERE TARGET_SYSTEM_ID=?",String.class,Long.valueOf(system));
        String diagnosisKey=t.kind().equals("SYSTEM")?key:key+"/"+candidate.connectionId();
        // 모든 허용 기동에서 같은 항목의 확정 MISSING이 필요하다. 미준비/구세대 유효/판정 누락은 차단한다.
        long unsafe=jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT B WHERE B.ENVIRONMENT_ID=? AND B.ALLOWED_YN='Y' AND (B.READY_YN<>'Y' OR NOT EXISTS (SELECT 1 FROM AI_ADMIN_CONFIG_DIAGNOSIS D WHERE D.ENVIRONMENT_ID=B.ENVIRONMENT_ID AND D.CONFIG_ID=B.CONFIG_ID AND D.TARGET_KIND=? AND D.TARGET_KEY=? AND D.MISMATCH_YN='Y'))",Long.class,loaded.environment(),t.kind(),diagnosisKey);
        if(unsafe>0)return "DEPLOYMENT_UNCONFIRMED";
        var found=references.reference(system,candidate.connectionId());
        if(found==TargetReferencePort.Reference.PRESENT)return "REFERENCED";
        return found==TargetReferencePort.Reference.ABSENT?null:"REFERENCE_UNKNOWN";
    }
}
