// @feat FEAT-040
package com.aiagent.admin.managed;

import java.time.Duration;
import com.aiagent.support.time.BusinessCalendar;
import org.springframework.transaction.annotation.Transactional;

/** 고객사 기간 미설정이면 비활성. 작업 스케줄/공개 파기 API는 이 슬라이스에서 만들지 않는다. */
public class ManagedRetentionService {
    private final ManagedRegistry registry;
    private final ManagedLocks locks;
    private final ManagedHistoryService history;
    private final ManagedCommandGuard guard;
    private final BusinessCalendar calendar;
    private final Duration retention;
    public ManagedRetentionService(ManagedRegistry registry,ManagedLocks locks,ManagedHistoryService history,ManagedCommandGuard guard,BusinessCalendar calendar,Duration retention) {
        if(retention!=null&&(retention.isNegative()||retention.isZero()))throw new IllegalArgumentException("양의 보존 기간이 필요하다");
        this.registry=registry;this.locks=locks;this.history=history;this.guard=guard;this.calendar=calendar;this.retention=retention;
    }
    @Transactional
    public boolean forgetResult(String key) {
        locks.command(key);
        if(retention==null||guard.expired(key))return false;
        var found=registry.find(key);
        if(found.isEmpty())return false;
        var entry=found.get();
        if(entry.recordedAt()==null||!entry.recordedAt().isBefore(calendar.now().minus(retention)))return false;
        if(entry.historyNo()!=null) {
            var store=registry.owner(entry.kind()).history();
            var event=store.event(entry.historyNo()); locks.ordered(store.locks(event.ref())); locks.history(event.historyNo());
            if(store.current(event)||history.protectedHistory(event.historyNo()))return false;
        }
        guard.expire(entry);
        for(var journal:registry.journals())if(journal.find(key).isPresent())journal.delete(key);
        return true;
    }
    @Transactional
    public boolean purge(ManagedHistoryStore.Ref ref) {
        if(retention==null)return false;
        var store=registry.owner(ref.kind()).history(); locks.ordered(store.locks(ref));
        return purgeLocked(store,store.exact(ref));
    }
    @Transactional
    public boolean purgeEvent(String kind,long historyNo) {
        if(retention==null)return false;
        var store=registry.owner(kind).history(); var event=store.event(historyNo);
        locks.ordered(store.locks(event.ref())); return purgeLocked(store,event);
    }
    private boolean purgeLocked(ManagedHistoryStore store,ManagedHistoryStore.Event original) {
        history.lockManagedPurge(original.historyNo());
        var event=store.event(original.historyNo());
        if(!event.at().isBefore(calendar.now().minus(retention))||store.current(event)||history.protectedHistory(event.historyNo()))return false;
        for(var journal:registry.journals())if(journal.references(event.historyNo()))return false;
        store.purge(event); history.releaseManagedRecord(event.historyNo()); return true;
    }
}
