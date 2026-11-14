// @feat FEAT-040
package com.aiagent.admin.managed;

import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import com.aiagent.support.time.BusinessCalendar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static com.aiagent.admin.managed.ManagedHistoryStore.*;

public class ManagedHistoryService {
    /** 소비 모듈은 자기 원천의 현재 권한/상태를 같은 트랜잭션에서 검사한다. 예외는 성공이 아니다. */
    public interface Consumer {
        String module(); String kind();
        void requireActive(String key,String purpose);
        void requireReadable(String key,String purpose);
        void requireEnded(String key,String purpose);
    }
    public record Use(String module,String kind,String key,String purpose) {
        public Use {
            if(!valid(module,20)||!valid(kind,40)||!valid(key,128)||!valid(purpose,30))throw ManagedErrorCode.VALIDATION.failure();
        }
        private static boolean valid(String s,int max) { return s!=null&&!s.isBlank()&&s.length()<=max; }
        String lockKey() { return module.length()+":"+module+kind.length()+":"+kind+key.length()+":"+key+purpose.length()+":"+purpose; }
        Object[] params() { return new Object[]{module,kind,key,purpose}; }
    }
    private static final String USE_WHERE="CONSUMER_MODULE=? AND CONSUMER_KIND=? AND CONSUMER_KEY=? AND PURPOSE_CD=?";
    private final ManagedRegistry registry;
    private final ManagedLocks locks;
    private final ManagedIds ids;
    private final JdbcTemplate jdbc;
    private final BusinessCalendar calendar;
    private final Map<String,Consumer> consumers;
    public ManagedHistoryService(ManagedRegistry registry,ManagedLocks locks,ManagedIds ids,DataSource source,BusinessCalendar calendar,List<Consumer> installed) {
        this.registry=registry;this.locks=locks;this.ids=ids;this.jdbc=new JdbcTemplate(source);this.calendar=calendar;
        var values=new HashMap<String,Consumer>();
        for(var consumer:installed)if(values.put(consumer.module()+"/"+consumer.kind(),consumer)!=null)throw new IllegalStateException("이력 소비 소유자가 중복됐다");
        consumers=Map.copyOf(values);
    }
    private Consumer consumer(Use use) {
        var consumer=consumers.get(use.module()+"/"+use.kind());
        if(consumer==null)throw ManagedErrorCode.FORBIDDEN.failure();
        locks.consumer(use.lockKey()); return consumer;
    }
    private void collect(Ref ref,Ref parent,Map<Ref,Event> events,Map<Ref,Ref> parents,Set<Ref> stack) {
        if(stack.contains(ref))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        if(events.containsKey(ref))return;
        if(events.size()>=10_000||stack.size()>=256)throw ManagedErrorCode.VALIDATION.failure();
        stack.add(ref); var event=registry.owner(ref.kind()).history().exact(ref); events.put(ref,event); parents.put(ref,parent);
        for(var child:event.references())collect(child,ref,events,parents,stack);
        stack.remove(ref);
    }
    @Transactional
    public void protect(Use use,List<Ref> roots) {
        consumer(use).requireActive(use.key(),use.purpose());
        register(use,roots);
    }
    public void protectManagedRecord(Event event) {
        if(event.references().isEmpty())return;
        var use=managedUse(event.historyNo()); locks.consumer(use.lockKey()); register(use,event.references());
    }
    private static Use managedUse(long historyNo) { return new Use("admin","managed-history",Long.toString(historyNo),"DEPENDENCY"); }
    void lockManagedPurge(long historyNo) {
        var use=managedUse(historyNo); locks.consumer(use.lockKey());
        var numbers=new TreeSet<>(jdbc.queryForList("SELECT HISTORY_NO FROM AI_ADMIN_HISTORY_REF WHERE "+USE_WHERE,Long.class,use.params()));
        numbers.add(historyNo); numbers.forEach(locks::history);
    }
    void releaseManagedRecord(long historyNo) { erase(managedUse(historyNo)); }
    private void register(Use use,List<Ref> roots) {
        if(roots.isEmpty()||roots.size()>10_000)throw ManagedErrorCode.VALIDATION.failure();
        var events=new LinkedHashMap<Ref,Event>(); var parents=new HashMap<Ref,Ref>();
        for(var ref:roots)collect(ref,null,events,parents,new HashSet<>());
        events.values().stream().map(Event::historyNo).distinct().sorted().forEach(locks::history);
        // 잠금 전 열람한 전이 집합이 전부 아직 존재하는지 확인한 뒤 하나의 성공 단위로 기록한다.
        for(var event:events.values()) {
            var fresh=registry.owner(event.ref().kind()).history().exact(event.ref());
            if(fresh.historyNo()!=event.historyNo()||!fresh.references().equals(event.references()))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        }
        var numbers=new HashMap<Ref,Long>();
        for(var entry:events.entrySet()) {
            var ref=entry.getKey();var event=entry.getValue();
            var existing=jdbc.queryForList("SELECT REFERENCE_ID FROM AI_ADMIN_HISTORY_REF WHERE "+USE_WHERE+" AND ASSET_KIND=? AND ASSET_KEY=? AND APPLIED_REVISION=?",Long.class,
                    use.module(),use.kind(),use.key(),use.purpose(),ref.kind(),ref.targetId(),Long.parseLong(ref.revision()));
            long id=existing.isEmpty()?ids.next("AI_ADMIN_HISTORY_REF"):existing.getFirst(); numbers.put(ref,id);
            if(existing.isEmpty())jdbc.update("INSERT INTO AI_ADMIN_HISTORY_REF(REFERENCE_ID,CONSUMER_MODULE,CONSUMER_KIND,CONSUMER_KEY,ASSET_KIND,ASSET_KEY,APPLIED_REVISION,HISTORY_NO,PURPOSE_CD,PARENT_REFERENCE_ID,REGISTERED_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    id,use.module(),use.kind(),use.key(),ref.kind(),ref.targetId(),Long.parseLong(ref.revision()),event.historyNo(),use.purpose(),numbers.get(parents.get(ref)),Timestamp.from(calendar.now()));
        }
    }
    @Transactional
    public Event readExact(Use use,Ref ref) {
        consumer(use).requireReadable(use.key(),use.purpose());
        var found=jdbc.queryForList("SELECT HISTORY_NO FROM AI_ADMIN_HISTORY_REF WHERE "+USE_WHERE+" AND ASSET_KIND=? AND ASSET_KEY=? AND APPLIED_REVISION=?",Long.class,
                use.module(),use.kind(),use.key(),use.purpose(),ref.kind(),ref.targetId(),Long.parseLong(ref.revision()));
        if(found.size()!=1)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        locks.history(found.getFirst());
        var event=registry.owner(ref.kind()).history().exact(ref);
        if(event.historyNo()!=found.getFirst())throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        return event;
    }
    @Transactional
    public void release(Use use) {
        consumer(use).requireEnded(use.key(),use.purpose());
        jdbc.queryForList("SELECT DISTINCT HISTORY_NO FROM AI_ADMIN_HISTORY_REF WHERE "+USE_WHERE+" ORDER BY HISTORY_NO",Long.class,use.params()).forEach(locks::history);
        erase(use);
    }
    private void erase(Use use) {
        jdbc.update("UPDATE AI_ADMIN_HISTORY_REF SET PARENT_REFERENCE_ID=NULL WHERE "+USE_WHERE,use.params());
        jdbc.update("DELETE FROM AI_ADMIN_HISTORY_REF WHERE "+USE_WHERE,use.params());
    }
    public boolean protectedHistory(long historyNo) { return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_HISTORY_REF WHERE HISTORY_NO=?",Long.class,historyNo)!=0; }
}
