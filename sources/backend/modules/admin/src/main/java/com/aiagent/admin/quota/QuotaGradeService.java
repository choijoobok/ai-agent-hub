// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

/** 시작월 부모와 전체 등급 자식을 한 관리 명령으로 저장한다. 안정 등급 식별은 영구 보존한다. */
public class QuotaGradeService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/quotaGrade";
    static final String ROOT="AI_ADMIN_QUOTA_GRADE_SET",ITEM="AI_ADMIN_QUOTA_GRADE_ITEM";
    private final JdbcTemplate jdbc;
    private final ManagedIds ids;
    private final QuotaGradeGuard guard;
    private final ManagedLocks locks;
    private final ManagedHistoryStore history;
    public QuotaGradeService(DataSource source,ManagedIds ids,QuotaGradeGuard guard,ManagedLocks locks) {
        jdbc=new JdbcTemplate(source);this.ids=ids;this.guard=guard;this.locks=locks;
        history=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(
                new TypedHistoryStore.Table(ROOT,"GRADE_SET_ID",List.of("GRADE_SET_ID","START_MONTH","MEMO","REVISION","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
                new TypedHistoryStore.Table(ITEM,"GRADE_SET_ID",List.of("GRADE_SET_ID","GRADE_ID","GRADE_NM","RANK_NO","WEIGHT","TRANSFER_GRADE_ID","DESCRIPTION"))),rows->!rows.get(ITEM).isEmpty(),rows->List.of());
    }
    public DataSource dataSource(){return jdbc.getDataSource();}
    public String feature(){return "FEAT-042";}
    public String kind(){return "QUOTA_GRADE_SET";}
    public String readPath(){return BASE+"/getList";}
    public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("definition");}
    public Map<String,Object> normalize(ManagedCommand command){return command.targetId()!=null&&command.values().isEmpty()?Map.of():QuotaGradeDefinition.parse(command.values()).values();}
    public List<ManagedLocks.Key> locks(ManagedCommand command){
        locks.ordered(List.of(QuotaGradeGuard.boundary()));
        var before=current(command);
        return command.targetId()!=null&&before==null?List.of():guard.locks(change(command,before));
    }
    public Current current(ManagedCommand command) {
        if(command.targetId()==null)return null;
        var rows=jdbc.query("SELECT GRADE_SET_ID,REVISION FROM "+ROOT+" WHERE GRADE_SET_ID=?",(r,n)->new Current(r.getString(1),r.getString(2)),Long.parseLong(command.targetId()));
        return rows.isEmpty()?null:rows.getFirst();
    }
    private String month(String id){return jdbc.queryForObject("SELECT START_MONTH FROM "+ROOT+" WHERE GRADE_SET_ID=?",String.class,Long.parseLong(id));}
    private Set<String> gradeIds(String id){return id==null?Set.of():Set.copyOf(jdbc.queryForList("SELECT GRADE_ID FROM "+ITEM+" WHERE GRADE_SET_ID=?",String.class,Long.parseLong(id)));}
    private QuotaGradeUsage.Change change(ManagedCommand command,Current before) {
        boolean remove=command.values().isEmpty();var value=remove?null:QuotaGradeDefinition.parse(command.values());
        String start=before==null?value.startMonth():month(before.targetId());
        if(!remove&&!value.startMonth().equals(start))throw ManagedErrorCode.VALIDATION.failure();
        var prior=jdbc.queryForList("SELECT GRADE_SET_ID FROM "+ROOT+" WHERE START_MONTH<? ORDER BY START_MONTH DESC LIMIT 1",String.class,start);
        Set<String> previous=gradeIds(before!=null?before.targetId():prior.isEmpty()?null:prior.getFirst());
        if(!remove) {
            for(var grade:value.grades())if(grade.gradeId()!=null&&!previous.contains(grade.gradeId()))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            if(!previous.containsAll(value.transitions().keySet()))throw ManagedErrorCode.VALIDATION.failure();
        }
        var next=jdbc.queryForList("SELECT START_MONTH FROM "+ROOT+" WHERE START_MONTH>? ORDER BY START_MONTH LIMIT 1",String.class,start);
        return new QuotaGradeUsage.Change(before==null?null:before.targetId(),start,next.isEmpty()?null:next.getFirst(),remove,previous,value);
    }
    public void validate(ManagedCommand command,Current before) {
        var change=change(command,before);guard.requireFuture(change.startMonth(),guard.now());
        if(before==null&&jdbc.queryForObject("SELECT COUNT(*) FROM "+ROOT+" WHERE START_MONTH=?",Integer.class,change.startMonth())!=0)throw ManagedErrorCode.REVISION_CONFLICT.failure();
        guard.validate(change);
    }
    public Instant acceptedAt(Instant fallback){return guard.now();}
    public boolean changed(ManagedCommand command,Current before) {
        var value=QuotaGradeDefinition.parse(command.values());
        if(!value.transitions().isEmpty())return true;
        String memo=jdbc.queryForObject("SELECT COALESCE(MEMO,'') FROM "+ROOT+" WHERE GRADE_SET_ID=?",String.class,Long.parseLong(before.targetId()));
        var rows=jdbc.query("SELECT GRADE_ID,GRADE_NM,RANK_NO,WEIGHT,COALESCE(DESCRIPTION,'') FROM "+ITEM+" WHERE GRADE_SET_ID=? ORDER BY RANK_NO",(r,n)->new QuotaGradeDefinition.Grade(r.getString(1),null,r.getString(2),r.getInt(3),r.getBigDecimal(4),r.getString(5)),Long.parseLong(before.targetId()));
        return !Objects.equals(memo,value.memo())||!rows.equals(value.grades());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        var change=change(command,before);var value=change.after();guard.requireFuture(change.startMonth(),stamp.at());
        long target=before==null?ids.next(ROOT):Long.parseLong(before.targetId());var at=Timestamp.from(stamp.at());
        if(before==null)jdbc.update("INSERT INTO "+ROOT+" (GRADE_SET_ID,START_MONTH,MEMO,REVISION,ACCEPTED_DTM,CHANGE_REASON,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?)",target,value.startMonth(),value.memo(),Long.parseLong(stamp.revision()),at,stamp.reason(),stamp.actor(),at,stamp.actor(),at);
        else jdbc.update("UPDATE "+ROOT+" SET MEMO=?,REVISION=?,ACCEPTED_DTM=?,CHANGE_REASON=?,MOD_ID=?,MOD_DTM=? WHERE GRADE_SET_ID=?",value.memo(),Long.parseLong(stamp.revision()),at,stamp.reason(),stamp.actor(),at,target);
        jdbc.update("DELETE FROM "+ITEM+" WHERE GRADE_SET_ID=?",target);
        var resolved=new ArrayList<QuotaGradeDefinition.Grade>();
        for(var grade:value.grades()) {
            long id=grade.gradeId()==null?ids.next("AI_ADMIN_QUOTA_GRADE"):Long.parseLong(grade.gradeId());
            if(grade.gradeId()==null)jdbc.update("INSERT INTO AI_ADMIN_QUOTA_GRADE(GRADE_ID,CREATED_DTM) VALUES (?,?)",id,at);
            jdbc.update("INSERT INTO "+ITEM+"(GRADE_SET_ID,GRADE_ID,GRADE_NM,RANK_NO,WEIGHT,DESCRIPTION) VALUES (?,?,?,?,?,?)",target,id,grade.gradeNm(),grade.rankNo(),grade.weight(),grade.description());
            resolved.add(new QuotaGradeDefinition.Grade(Long.toString(id),null,grade.gradeNm(),grade.rankNo(),grade.weight(),grade.description()));
        }
        var after=new QuotaGradeDefinition(value.startMonth(),value.memo(),List.copyOf(resolved),value.transitions());
        guard.apply(new QuotaGradeUsage.Change(Long.toString(target),change.startMonth(),change.nextMonth(),false,change.beforeIds(),after),stamp);return Long.toString(target);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void delete(ManagedCommand command,Current before) {
        if(!command.values().isEmpty())throw ManagedErrorCode.VALIDATION.failure();
        var change=change(command,before);guard.requireFuture(change.startMonth(),guard.now());
        // 삭제의 이전/취소도 동일 명령의 수락 정보로 기록한다.
        var event=history.event(jdbc.queryForObject("SELECT MAX(HISTORY_NO) FROM "+ROOT+"_HIST WHERE GRADE_SET_ID=?",Long.class,Long.parseLong(before.targetId())));
        var row=event.rows().get(ROOT).getFirst();
        guard.apply(change,new Stamp(command.commandKey(),row.get("HIST_ACTOR_ID").toString(),event.ref().revision(),event.historyNo(),event.at(),"DELETE",command.reason()));
        jdbc.update("DELETE FROM "+ITEM+" WHERE GRADE_SET_ID=?",Long.parseLong(before.targetId()));
        jdbc.update("DELETE FROM "+ROOT+" WHERE GRADE_SET_ID=?",Long.parseLong(before.targetId()));
    }
    public ManagedHistoryStore history(){return history;}
}
