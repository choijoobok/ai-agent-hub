// @feat FEAT-040
package com.aiagent.admin.managed;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.*;

@Transactional(readOnly=true,propagation=Propagation.MANDATORY)
public class ManagedCommandQueryService {
    public record Query(String commandKey,String ownerFeature) {}
    public record Result(String status,ManagedResult result) {}
    private final ManagedRegistry registry;
    private final ManagedAdmission admission;
    private final ManagedAuthorization authorization;
    private final ManagedCommandGuard guard;
    public ManagedCommandQueryService(ManagedRegistry registry,ManagedAdmission admission,ManagedAuthorization authorization,ManagedCommandGuard guard) {
        this.registry=registry;this.admission=admission;this.authorization=authorization;this.guard=guard;
    }
    public Result getResult(Query query) {
        try {
            ManagedCommand.requireKey(query.commandKey());
            var actor=admission.queryActor(query.commandKey(),query.ownerFeature());
            var owners=registry.commonOwners(query.ownerFeature());
            if(owners.isEmpty())return new Result("UNKNOWN",null);
            var entry=registry.find(query.commandKey());
            var owner=entry.filter(e->e.actor().equals(actor)&&e.feature().equals(query.ownerFeature()))
                    .flatMap(e->owners.stream().filter(o->o.kind().equals(e.kind())).findFirst()).orElse(owners.getFirst());
            authorization.require(actor,owner,true);
            if(guard.expiredOwner(query.commandKey()).filter(query.ownerFeature()::equals).isPresent())throw ManagedErrorCode.COMMAND_EXPIRED.failure();
            if(entry.isEmpty()||!entry.get().actor().equals(actor)||!entry.get().feature().equals(query.ownerFeature()))return new Result("UNKNOWN",null);
            if(!owner.kind().equals(entry.get().kind()))return new Result("UNKNOWN",null);
            return new Result("APPLIED",ManagedCommandService.result(entry.get(),owner.history()));
        } catch(DataAccessException failure) { throw ManagedErrorCode.UNAVAILABLE.failure(); }
    }
}
