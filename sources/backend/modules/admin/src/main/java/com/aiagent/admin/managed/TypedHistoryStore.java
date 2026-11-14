// @feat FEAT-040
package com.aiagent.admin.managed;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** 직접 소유 부모/자식의 전체 동형 복사. 복잡한 소유 그래프는 같은 포트의 소유 구현으로 연결한다. */
public final class TypedHistoryStore implements ManagedHistoryStore {
    public record Table(String name,String ownerKey,List<String> columns) {
        public Table {
            identifier(name); identifier(ownerKey); columns=List.copyOf(columns);
            columns.forEach(TypedHistoryStore::identifier);
            if(new HashSet<>(columns).size()!=columns.size()||!columns.contains(ownerKey))throw new IllegalArgumentException("이력 소유 키/열이 올바르지 않다");
        }
    }
    private static final List<String> ROOT_META=List.of("HIST_ID","HISTORY_NO","HIST_DTM","HIST_ACTION","HIST_ACTOR_ID","HIST_SOURCE_CD","HISTORY_COMMAND_KEY","RESULT_REVISION","BEFORE_HISTORY_NO","HIST_REASON","HIST_SUMMARY","SCHEMA_VERSION");
    private final String kind;
    private final ManagedTargetKeys targetKeys;
    private final String revision;
    private final List<Table> tables;
    private final JdbcTemplate jdbc;
    private final ManagedIds ids;
    private final Predicate<Map<String,List<Map<String,Object>>>> complete;
    private final Function<Map<String,List<Map<String,Object>>>,List<Ref>> references;
    public TypedHistoryStore(DataSource source,ManagedIds ids,String kind,String revision,List<Table> tables,
            Predicate<Map<String,List<Map<String,Object>>>> complete,Function<Map<String,List<Map<String,Object>>>,List<Ref>> references) {
        this(source,ids,kind,revision,tables,complete,references,ManagedTargetKeys.DECIMAL);
    }
    public TypedHistoryStore(DataSource source,ManagedIds ids,String kind,String revision,List<Table> tables,
            Predicate<Map<String,List<Map<String,Object>>>> complete,Function<Map<String,List<Map<String,Object>>>,List<Ref>> references,
            ManagedTargetKeys targetKeys) {
        this.targetKeys=Objects.requireNonNull(targetKeys);
        this.jdbc=new JdbcTemplate(source); this.ids=ids; this.kind=kind; this.revision=identifier(revision);
        this.tables=List.copyOf(tables); this.complete=Objects.requireNonNull(complete); this.references=Objects.requireNonNull(references);
        if(tables.isEmpty()||!tables.getFirst().columns().contains(revision))throw new IllegalArgumentException("이력 부모와 리비전 열이 필요하다");
        validateShape();
    }
    private static String identifier(String value) {
        if(value==null||!value.matches("[A-Z][A-Z0-9_]{0,62}"))throw new IllegalArgumentException("등록된 물리 식별자만 사용할 수 있다");
        return value;
    }
    public String kind() { return kind; }
    public DataSource dataSource() { return jdbc.getDataSource(); }
    public List<ManagedLocks.Key> locks(Ref ref) { target(ref); return List.of(new ManagedLocks.Key(3,kind,ref.targetId())); }
    private Object target(Ref ref) {
        if(!kind.equals(ref.kind()))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        return targetKeys.bind(ref.targetId());
    }
    private Map<String,List<Object>> columns(String table) {
        var result=new LinkedHashMap<String,List<Object>>();
        jdbc.query("SELECT column_name,udt_name,is_nullable,character_maximum_length,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?",r->{
            var type=Arrays.asList(r.getObject(2),r.getObject(3),r.getObject(4),r.getObject(5),r.getObject(6));
            result.put(r.getString(1).toUpperCase(Locale.ROOT),type);
        },table.toLowerCase(Locale.ROOT));
        return result;
    }
    private void validateShape() {
        for(int index=0;index<tables.size();index++) {
            var table=tables.get(index); var source=columns(table.name()); var history=columns(table.name()+"_HIST");
            if(!source.containsKey(table.ownerKey())||!targetKeys.acceptsSqlType(source.get(table.ownerKey()).getFirst().toString()))
                throw new IllegalStateException("이력 소유 키 정책과 물리 타입이 다르다: "+table.name());
            var expected=new HashSet<>(table.columns()); expected.addAll(index==0?ROOT_META:List.of("HIST_ID","HISTORY_NO"));
            if(!history.keySet().equals(expected))throw new IllegalStateException("전체 이력 열과 등록된 소유 열이 다르다: "+table.name());
            for(String column:table.columns()) if(!source.containsKey(column)||!Objects.equals(source.get(column),history.get(column)))
                throw new IllegalStateException("이력 원값 타입/필수 조건이 다르다: "+table.name()+"."+column);
        }
    }
    public void capture(String targetId,ManagedAssetAdapter.Stamp stamp) {
        captureOrigin(targetId,stamp,"ADMIN");
    }
    public void captureExternal(String targetId,ManagedAssetAdapter.Stamp stamp) {
        if(!kind.equals("PORTAL_USER")||stamp.actor()!=null||!stamp.action().equals("CHANGE"))throw ManagedErrorCode.VALIDATION.failure();
        captureOrigin(targetId,stamp,"EXTERNAL");
    }
    private void captureOrigin(String targetId,ManagedAssetAdapter.Stamp stamp,String origin) {
        Object target=targetKeys.bind(targetId);
        var selected=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var table:tables)selected.put(table.name(),jdbc.queryForList("SELECT "+String.join(",",table.columns())+" FROM "+table.name()+" WHERE "+table.ownerKey()+"=?",target));
        if(selected.get(tables.getFirst().name()).size()!=1||!complete.test(selected))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var root=tables.getFirst();
        var previous=jdbc.queryForList("SELECT HISTORY_NO FROM "+root.name()+"_HIST WHERE "+root.ownerKey()+"=? AND HIST_ACTION<>'DELETE' ORDER BY RESULT_REVISION DESC LIMIT 1",Long.class,target);
        for(int i=0;i<tables.size();i++) {
            var table=tables.get(i); var rows=selected.get(table.name());
            if(rows.isEmpty())continue;
            long next=ids.reserve("AI_ADMIN_HIST_ID",rows.size());
            for(var row:rows) {
                var values=new LinkedHashMap<String,Object>();
                for(String column:table.columns())values.put(column,row.get(column));
                values.put("HIST_ID",next++); values.put("HISTORY_NO",stamp.historyNo());
                if(i==0) {
                    values.put("HIST_DTM",Timestamp.from(stamp.at())); values.put("HIST_ACTION",stamp.action());
                    values.put("HIST_ACTOR_ID",stamp.actor()); values.put("HIST_SOURCE_CD",origin);
                    values.put("HISTORY_COMMAND_KEY",stamp.commandKey()); values.put("RESULT_REVISION",Long.parseLong(stamp.revision()));
                    values.put("BEFORE_HISTORY_NO",previous.isEmpty()?null:previous.getFirst()); values.put("HIST_REASON",stamp.reason());
                    values.put("HIST_SUMMARY",stamp.action()); values.put("SCHEMA_VERSION",1);
                }
                jdbc.update("INSERT INTO "+table.name()+"_HIST ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray());
            }
        }
    }
    public Event exact(Ref ref) {
        if(!kind.equals(ref.kind()))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var root=tables.getFirst();
        var found=jdbc.queryForList("SELECT HISTORY_NO FROM "+root.name()+"_HIST WHERE "+root.ownerKey()+"=? AND RESULT_REVISION=? AND HIST_ACTION<>'DELETE'",Long.class,target(ref),Long.parseLong(ref.revision()));
        if(found.size()!=1)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        return event(found.getFirst());
    }
    public Event event(long historyNo) {
        var data=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var table:tables)data.put(table.name(),jdbc.queryForList("SELECT * FROM "+table.name()+"_HIST WHERE HISTORY_NO=? ORDER BY HIST_ID",historyNo));
        var roots=data.get(tables.getFirst().name());
        if(roots.size()!=1||!complete.test(data))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var root=roots.getFirst();
        if(((Number)root.get("SCHEMA_VERSION")).intValue()!=1)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var ref=new Ref(kind,root.get(tables.getFirst().ownerKey()).toString(),root.get("RESULT_REVISION").toString());
        target(ref);
        return new Event(ref,historyNo,((Timestamp)root.get("HIST_DTM")).toInstant(),root.get("HIST_ACTION").toString(),data,references.apply(data));
    }
    public boolean current(Event event) {
        if(event.action().equals("DELETE"))return false;
        var root=tables.getFirst();
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+root.name()+" WHERE "+root.ownerKey()+"=? AND "+revision+"=?",Long.class,target(event.ref()),Long.parseLong(event.ref().revision()))!=0;
    }
    public void purge(Event event) {
        for(var table:tables.reversed())jdbc.update("DELETE FROM "+table.name()+"_HIST WHERE HISTORY_NO=?",event.historyNo());
    }
}
