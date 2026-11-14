// @feat FEAT-001 FEAT-040
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
import com.aiagent.domain.authz.ActionType;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public class TargetCommandQueryService {
    public record Key(String commandKey) {}
    public record Result(String status,Object result) {}
    private final JdbcTemplate jdbc;private final ManagedAdmission admission;private final TargetSystemAccess access;
    private final TargetAssetJournal journal;private final TargetCleanupService cleanup;private final ManagedCommandGuard guard;
    public TargetCommandQueryService(DataSource source,ManagedAdmission admission,TargetSystemAccess access,TargetAssetJournal journal,TargetCleanupService cleanup,ManagedCommandGuard guard){jdbc=new JdbcTemplate(source);this.admission=admission;this.access=access;this.journal=journal;this.cleanup=cleanup;this.guard=guard;}
    public Result getResult(Key request){
        String key=ManagedCommand.requireKey(request.commandKey());String actor=admission.queryActor("API-009",key,"FEAT-001");
        access.require(ActionType.READ,ActionType.SAVE);
        if(guard.expiredOwner(key).filter("FEAT-001"::equals).isPresent())throw ManagedErrorCode.COMMAND_EXPIRED.failure();
        var found=journal.find(key);
        if(found.isEmpty()||!found.get().actor().equals(actor)||!found.get().feature().equals("FEAT-001")||!found.get().kind().equals("TARGET_SYSTEM"))return new Result("UNKNOWN",null);
        var e=found.get();String action=jdbc.queryForObject("SELECT ACTION_CD FROM AI_ADMIN_ASSET_COMMAND WHERE COMMAND_KEY=?",String.class,key);
        Object result=action.equals("CONFIGURATION_CLEANUP")?cleanup.result(e):new ManagedResult(e.commandKey(),e.targetId(),e.revision(),e.historyNo()==null?null:e.historyNo().toString(),e.appliedAt(),action,e.outcome());
        return new Result("APPLIED",result);
    }
}
