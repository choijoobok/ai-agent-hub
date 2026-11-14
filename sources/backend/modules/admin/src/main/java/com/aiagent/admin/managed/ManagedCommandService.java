// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.support.time.BusinessCalendar;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.*;

public class ManagedCommandService {
    private final ManagedRegistry registry;
    private final ManagedAdmission admission;
    private final ManagedAuthorization authorization;
    private final ManagedLocks locks;
    private final ManagedCommandGuard guard;
    private final ManagedIds ids;
    private final BusinessCalendar calendar;
    private final ManagedHistoryService history;
    public ManagedCommandService(ManagedRegistry registry,ManagedAdmission admission,ManagedAuthorization authorization,
            ManagedLocks locks,ManagedCommandGuard guard,ManagedIds ids,BusinessCalendar calendar,ManagedHistoryService history) {
        this.registry=registry;this.admission=admission;this.authorization=authorization;this.locks=locks;this.guard=guard;this.ids=ids;this.calendar=calendar;this.history=history;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public ManagedResult save(String operationId,ManagedCommand raw) {
        try { return apply(operationId,raw); }
        catch(DataAccessException failure) { throw ManagedErrorCode.UNAVAILABLE.failure(); }
    }
    private ManagedResult apply(String operationId,ManagedCommand raw) {
        var operation=registry.operation(operationId); var owner=operation.owner();
        var input=normalize(owner,raw); String actor=admission.saveActor(operationId,input);
        authorization.require(actor,owner,false);
        if(guard.expired(input.commandKey()))throw ManagedErrorCode.COMMAND_EXPIRED.failure();
        String digest=ManagedDigest.of(actor,owner.feature(),operation.id()+"/"+owner.kind()+"/"+(operation.delete()?"DELETE":"SAVE"),input);
        var previous=registry.find(input.commandKey());
        if(previous.isPresent()) {
            var old=previous.get();
            if(!old.actor().equals(actor)||!old.feature().equals(owner.feature())||!old.kind().equals(owner.kind())||!old.digestVersion().equals("managed/v1")||!old.digest().equals(digest))
                throw ManagedErrorCode.COMMAND_CONFLICT.failure();
            return result(old,owner.history());
        }
        var keys=new ArrayList<>(owner.locks(input));
        if(input.targetId()!=null)keys.add(new ManagedLocks.Key(3,owner.kind(),input.targetId()));
        locks.ordered(keys);
        var before=owner.current(input);
        if(input.targetId()==null?before!=null:before==null||!input.targetId().equals(before.targetId())||!input.expectedRevision().equals(before.revision()))
            throw ManagedErrorCode.REVISION_CONFLICT.failure();
        owner.validate(input,before);
        if(operation.delete()&&before==null)throw ManagedErrorCode.VALIDATION.failure();
        if(before!=null&&!operation.delete()&&!owner.changed(input,before)) {
            var unchanged=new ManagedJournal.Entry(input.commandKey(),owner.feature(),actor,digest,"managed/v1",owner.kind(),before.targetId(),before.revision(),null,"NO_CHANGE",null,calendar.now().truncatedTo(ChronoUnit.MICROS));
            operation.journal().insert(unchanged); return result(unchanged,owner.history());
        }
        long revision;
        try { revision=before==null?1:Math.addExact(ManagedCommand.positive(before.revision()),1); }
        catch(ArithmeticException failure) { throw ManagedErrorCode.REVISION_CONFLICT.failure(); }
        var stamp=new ManagedAssetAdapter.Stamp(input.commandKey(),actor,Long.toString(revision),ids.next("AI_ADMIN_HISTORY_NO"),
                owner.acceptedAt(calendar.now()).truncatedTo(ChronoUnit.MICROS),operation.delete()?"DELETE":before==null?"CREATE":"CHANGE",input.reason());
        String target;
        if(operation.delete()) {
            target=before.targetId(); owner.history().capture(target,stamp); owner.delete(input,before);
            if(owner.current(input)!=null)throw new IllegalStateException("삭제 뒤 현재 구성이 남았다");
        } else {
            target=owner.apply(input,before,stamp); owner.targetKeys().bind(target);
            if(before!=null&&!before.targetId().equals(target))throw new IllegalStateException("기존 안정 ID를 변경할 수 없다");
            var written=owner.current(new ManagedCommand(input.commandKey(),target,stamp.revision(),input.scope(),input.values(),input.reason()));
            if(written==null||!written.targetId().equals(target)||!written.revision().equals(stamp.revision()))throw new IllegalStateException("소유 저장의 결과 판이 다르다");
            owner.history().capture(target,stamp);
        }
        history.protectManagedRecord(owner.history().event(stamp.historyNo()));
        var entry=new ManagedJournal.Entry(input.commandKey(),owner.feature(),actor,digest,"managed/v1",owner.kind(),target,stamp.revision(),stamp.historyNo(),"APPLIED",stamp.at(),stamp.at());
        operation.journal().insert(entry); return result(entry,owner.history());
    }
    public static ManagedCommand normalize(ManagedAssetAdapter owner,ManagedCommand input) {
        if(input.targetId()!=null)owner.targetKeys().bind(input.targetId());
        if(!owner.scopes().contains(input.scope()))throw ManagedErrorCode.VALIDATION.failure();
        return new ManagedCommand(input.commandKey(),input.targetId(),input.expectedRevision(),input.scope(),owner.normalize(input),input.reason());
    }
    static ManagedResult result(ManagedJournal.Entry entry,ManagedHistoryStore history) {
        String action=entry.historyNo()==null?"CHANGE":history.event(entry.historyNo()).action();
        return new ManagedResult(entry.commandKey(),entry.targetId(),entry.revision(),entry.historyNo()==null?null:entry.historyNo().toString(),entry.appliedAt(),action,entry.outcome());
    }
}
