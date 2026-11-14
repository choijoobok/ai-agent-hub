// @feat FEAT-002 FEAT-040
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.support.deployment.ExternalDataSources;
import com.aiagent.domain.authz.ActionType;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.aiagent.admin.shared.configuration.ConfigurationCleanup.*;
@Service
public class DataSourceCleanupService {
    private final JdbcTemplate jdbc;private final DataSourceQueryService query;private final DataSourceService owner;private final DataSourceReferenceService refs;
    private final DataSourceAccess access;private final ExternalDataSources loaded;private final ConfigurationDeploymentService deployment;
    private final AssetJournal journal;private final ManagedRegistry registry;private final ManagedAdmission admission;private final ManagedCommandGuard guard;private final ManagedLocks locks;private final ManagedIds ids;private final ManagedHistoryService history;private final Clock clock;
    public DataSourceCleanupService(DataSource source,DataSourceQueryService query,DataSourceService owner,DataSourceReferenceService refs,DataSourceAccess access,ExternalDataSources loaded,ConfigurationDeploymentService deployment,AssetJournal journal,ManagedRegistry registry,ManagedAdmission admission,ManagedCommandGuard guard,ManagedLocks locks,ManagedIds ids,ManagedHistoryService history,Clock clock){jdbc=new JdbcTemplate(source);this.query=query;this.owner=owner;this.refs=refs;this.access=access;this.loaded=loaded;this.deployment=deployment;this.journal=journal;this.registry=registry;this.admission=admission;this.guard=guard;this.locks=locks;this.ids=ids;this.history=history;this.clock=clock;}
    @Transactional public Preview preview(Query request){
        access.require(ActionType.READ,ActionType.SAVE);locks.ordered(List.of(ConfigurationDeploymentService.boundary(loaded.environment())));deployment.requireReady(loaded);
        var candidates=query.cleanupCandidates(request);lock(candidates);String token=UUID.randomUUID().toString();var expires=clock.instant().plusSeconds(300);
        jdbc.update("INSERT INTO AI_ADMIN_CLEANUP_PREVIEW VALUES (?,?,?,?,?,?)",token,access.actor(),"DATASOURCE",digest(request),loaded.settingsId(),Timestamp.from(expires));
        var targets=new ArrayList<PreviewTarget>();for(var c:candidates){var t=c.target();targets.add(new PreviewTarget(t,c.name(),blocking(c)));
            jdbc.update("INSERT INTO AI_ADMIN_CLEANUP_PREVIEW_ITEM VALUES (?,?,?,?,?)",token,"DATASOURCE",Long.valueOf(t.id()),Long.valueOf(t.expectedVersion()),Long.valueOf(c.diagnosisId()));}
        return new Preview(token,request,loaded.settingsId(),targets,!targets.isEmpty()&&targets.stream().allMatch(t->t.reason()==null),expires);
    }
    @Transactional(propagation=Propagation.MANDATORY) public Result execute(ManagedCommand raw){try{return apply(raw);}catch(org.springframework.dao.DataAccessException failure){throw ManagedErrorCode.UNAVAILABLE.failure();}}
    private Result apply(ManagedCommand raw){
        var c=ManagedCommandService.normalize(owner,raw);String actor=admission.saveActor("API-076",c);access.require(ActionType.READ,ActionType.SAVE);if(guard.expired(c.commandKey()))throw ManagedErrorCode.COMMAND_EXPIRED.failure();
        String digest=ManagedDigest.of(actor,"FEAT-002","API-076/DATA_SOURCE/CONFIGURATION_CLEANUP",c);var previous=registry.find(c.commandKey());
        if(previous.isPresent()){var p=previous.get();if(!p.actor().equals(actor)||!p.feature().equals("FEAT-002")||!p.kind().equals("DATA_SOURCE")||!p.digestVersion().equals("managed/v1")||!p.digest().equals(digest))throw ManagedErrorCode.COMMAND_CONFLICT.failure();return result(p);}
        var values=ConfigurationCleanup.parse(c);locks.ordered(List.of(ConfigurationDeploymentService.boundary(loaded.environment())));deployment.requireReady(loaded);
        var p=jdbc.queryForList("SELECT * FROM AI_ADMIN_CLEANUP_PREVIEW WHERE PREVIEW_ID=?",values.previewToken());
        if(p.size()!=1||!actor.equals(p.getFirst().get("ACTOR_ID"))||!"DATASOURCE".equals(p.getFirst().get("TARGET_KIND"))||!loaded.settingsId().equals(p.getFirst().get("CONFIG_ID"))||!digest(values.query()).equals(p.getFirst().get("FILTER_DIGEST"))||!clock.instant().isBefore(((Timestamp)p.getFirst().get("EXPIRES_DTM")).toInstant()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var candidates=query.cleanupCandidates(values.query());lock(candidates);
        if(candidates.isEmpty()||!candidates.stream().map(Candidate::target).toList().equals(values.confirmedTargets()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var stored=jdbc.queryForList("SELECT * FROM AI_ADMIN_CLEANUP_PREVIEW_ITEM WHERE PREVIEW_ID=? ORDER BY TARGET_ID",values.previewToken());
        if(stored.size()!=candidates.size())throw ManagedErrorCode.REVISION_CONFLICT.failure();
        for(int n=0;n<candidates.size();n++){var t=candidates.get(n);var s=stored.get(n);if(!t.target().id().equals(s.get("TARGET_ID").toString())||!t.target().expectedVersion().equals(s.get("EXPECTED_VERSION").toString())||!t.diagnosisId().equals(s.get("DIAGNOSIS_ID").toString()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
            var blocked=blocking(t);if(blocked!=null)throw (blocked.equals("REFERENCED")?ManagedErrorCode.REFERENCE_CONFLICT:ManagedErrorCode.UNAVAILABLE).failure();}
        var at=clock.instant().truncatedTo(ChronoUnit.MICROS);var results=new ArrayList<Applied>();
        for(var target:candidates){var t=target.target();var stamp=new ManagedAssetAdapter.Stamp(c.commandKey(),actor,Long.toString(Math.addExact(ManagedCommand.positive(t.expectedVersion()),1)),ids.next("AI_ADMIN_HISTORY_NO"),at,"DELETE",c.reason());
            owner.history().capture(t.id(),stamp);history.protectManagedRecord(owner.history().event(stamp.historyNo()));resolve(t.id(),at);
            for(String table:List.of("AI_ADMIN_QUERY_GUIDE","AI_ADMIN_DATA_SOURCE_GROUP","AI_ADMIN_DATA_SOURCE"))jdbc.update("DELETE FROM "+table+" WHERE DATA_SOURCE_ID=?",Long.valueOf(t.id()));
            results.add(new Applied(t.id(),"DATASOURCE",null,t.expectedVersion(),stamp.revision(),Long.toString(stamp.historyNo())));
        }
        var entry=new ManagedJournal.Entry(c.commandKey(),"FEAT-002",actor,digest,"managed/v1","DATA_SOURCE",null,null,null,"APPLIED",at,at);journal.insert(entry,"CONFIGURATION_CLEANUP");
        for(var r:results)jdbc.update("INSERT INTO AI_ADMIN_ASSET_COMMAND_RESULT VALUES (?,?,?,?,?)",c.commandKey(),"DATASOURCE",Long.valueOf(r.id()),Long.valueOf(r.resultVersion()),Long.valueOf(r.historyNo()));
        return new Result(c.commandKey(),"CONFIGURATION_CLEANUP",at,results.size(),results);
    }
    Result result(ManagedJournal.Entry e){var rows=jdbc.query("SELECT * FROM AI_ADMIN_ASSET_COMMAND_RESULT WHERE COMMAND_KEY=? AND TARGET_KIND='DATASOURCE' ORDER BY TARGET_ID",(rs,n)->new Applied(rs.getString("TARGET_ID"),"DATASOURCE",null,Long.toString(rs.getLong("RESULT_VERSION")-1),rs.getString("RESULT_VERSION"),rs.getString("HISTORY_NO")),e.commandKey());if(rows.isEmpty())throw ManagedErrorCode.UNAVAILABLE.failure();return new Result(e.commandKey(),"CONFIGURATION_CLEANUP",e.appliedAt(),rows.size(),rows);}
    private void lock(List<Candidate> rows){locks.ordered(rows.stream().map(c->new ManagedLocks.Key(3,"DATA_SOURCE",c.target().id())).toList());}
    private String digest(Query request){Long control=jdbc.queryForObject("SELECT CONTROL_VERSION FROM AI_ADMIN_TARGET_DEPLOYMENT WHERE ENVIRONMENT_ID=?",Long.class,loaded.environment());return ConfigurationCleanup.digest(request,loaded.bootId().toString(),loaded.settingsId(),control);}
    private String blocking(Candidate c){String key=jdbc.queryForObject("SELECT DATA_SOURCE_KEY FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,Long.valueOf(c.target().id()));
        Long unsafe=jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT B WHERE B.ENVIRONMENT_ID=? AND B.ALLOWED_YN='Y' AND (B.READY_YN<>'Y' OR NOT EXISTS(SELECT 1 FROM AI_ADMIN_CONFIG_DIAGNOSIS D WHERE D.ENVIRONMENT_ID=B.ENVIRONMENT_ID AND D.CONFIG_ID=B.CONFIG_ID AND D.TARGET_KIND='DATASOURCE' AND D.TARGET_KEY=? AND D.MISMATCH_YN='Y'))",Long.class,loaded.environment(),key);
        if(unsafe>0)return "DEPLOYMENT_UNCONFIRMED";return switch(refs.references(c.target().id())){case PRESENT->"REFERENCED";case UNKNOWN->"REFERENCE_UNKNOWN";case ABSENT->null;};
    }
    private void resolve(String id,Instant time){var at=Timestamp.from(time);String key=jdbc.queryForObject("SELECT DATA_SOURCE_KEY FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,Long.valueOf(id));
        for(var row:jdbc.queryForList("SELECT ALERT_ID,CONFIG_ID FROM AI_ADMIN_CONFIG_ALERT WHERE ENVIRONMENT_ID=? AND TARGET_KIND='DATASOURCE' AND TARGET_KEY=? AND STATUS_CD='OPEN'",loaded.environment(),key)){
            jdbc.update("UPDATE AI_ADMIN_CONFIG_ALERT SET STATUS_CD='RECOVERED',RECOVERED_DTM=?,LAST_CHECKED_DTM=?,MOD_DTM=? WHERE ALERT_ID=?",at,at,at,row.get("ALERT_ID"));
            jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT_EVENT(EVENT_ID,ALERT_ID,EVENT_CD,CONFIG_ID,EVIDENCE,REG_DTM) VALUES (?,?,'RECOVERED',?,'METADATA_REMOVED',?)",ids.next("AI_ADMIN_CONFIG_ID"),row.get("ALERT_ID"),row.get("CONFIG_ID"),at);
        }
    }
}
