// @feat FEAT-003 FEAT-040
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.*;
import com.aiagent.domain.authz.ActionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public class CatalogCommandQueryService {
 public record Result(String status,Object result){}
 private final ManagedAdmission admission;private final CatalogAccess access;private final JdbcManagedJournal journal;private final ManagedCommandGuard guard;private final CatalogService owner;private final CatalogQueryService queries;
 public CatalogCommandQueryService(ManagedAdmission admission,CatalogAccess access,JdbcManagedJournal journal,ManagedCommandGuard guard,CatalogService owner,CatalogQueryService queries){this.admission=admission;this.access=access;this.journal=journal;this.guard=guard;this.owner=owner;this.queries=queries;}
 public Result getResult(String commandKey){String key=ManagedCommand.requireKey(commandKey),actor=admission.queryActor("API-034",key,"FEAT-003");access.require(ActionType.READ,ActionType.SAVE);if(guard.expiredOwner(key).filter("FEAT-003"::equals).isPresent())throw ManagedErrorCode.COMMAND_EXPIRED.failure();var found=journal.find(key);if(found.isEmpty()||!found.get().actor().equals(actor)||!found.get().feature().equals("FEAT-003"))return new Result("UNKNOWN",null);var e=found.get();return new Result("APPLIED",CatalogResults.management(new ManagedResult(e.commandKey(),e.targetId(),e.revision(),e.historyNo()==null?null:e.historyNo().toString(),e.appliedAt(),e.historyNo()==null?"CHANGE":owner.history().event(e.historyNo()).action(),e.outcome()),e.historyNo()==null?e.revision():owner.history().event(e.historyNo()).action().equals("CREATE")?"0":Long.toString(Long.parseLong(e.revision())-1),queries.mappings(key)));}
}
