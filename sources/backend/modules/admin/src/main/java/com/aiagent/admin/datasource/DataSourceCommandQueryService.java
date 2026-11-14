// @feat FEAT-002 FEAT-040
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.domain.authz.ActionType;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public class DataSourceCommandQueryService {
    public record Key(String commandKey){} public record Result(String status,Object result){}
    private final JdbcTemplate jdbc;private final ManagedAdmission admission;private final DataSourceAccess access;private final AssetJournal journal;private final ManagedCommandGuard guard;private final DataSourceCleanupService cleanup;
    public DataSourceCommandQueryService(DataSource source,ManagedAdmission admission,DataSourceAccess access,AssetJournal journal,ManagedCommandGuard guard,DataSourceCleanupService cleanup){jdbc=new JdbcTemplate(source);this.admission=admission;this.access=access;this.journal=journal;this.guard=guard;this.cleanup=cleanup;}
    public Result getResult(Key r){String key=ManagedCommand.requireKey(r.commandKey()),actor=admission.queryActor("API-018",key,"FEAT-002");access.require(ActionType.READ,ActionType.SAVE);
        if(guard.expiredOwner(key).filter("FEAT-002"::equals).isPresent())throw ManagedErrorCode.COMMAND_EXPIRED.failure();var found=journal.find(key);
        if(found.isEmpty()||!found.get().actor().equals(actor)||!found.get().feature().equals("FEAT-002")||!found.get().kind().equals("DATA_SOURCE"))return new Result("UNKNOWN",null);
        var e=found.get();String action=jdbc.queryForObject("SELECT ACTION_CD FROM AI_ADMIN_ASSET_COMMAND WHERE COMMAND_KEY=?",String.class,key);
        return new Result("APPLIED",action.equals("CONFIGURATION_CLEANUP")?cleanup.result(e):new ManagedResult(e.commandKey(),e.targetId(),e.revision(),e.historyNo()==null?null:e.historyNo().toString(),e.appliedAt(),action,e.outcome()));
    }
}
