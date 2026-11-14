// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

/** 등급 기준 변경이 사용자 효력에 미치는 범위를 소유한다. 원 예약은 전체 HIST에 남긴다. */
public class PortalGradeUsage implements QuotaGradeUsage {
    private final JdbcTemplate jdbc;
    private final PortalDependencies dependencies;
    private final PortalGrades grades;
    private final PortalUserService users;
    private final ManagedIds ids;
    public PortalGradeUsage(DataSource source,PortalDependencies dependencies,PortalGrades grades,PortalUserService users,ManagedIds ids){jdbc=new JdbcTemplate(source);this.dependencies=dependencies;this.grades=grades;this.users=users;this.ids=ids;}
    private List<String> userIds(){return jdbc.queryForList("SELECT PORTAL_USER_ID FROM AI_ADMIN_PORTAL_USER ORDER BY PORTAL_USER_ID",String.class);}
    public List<ManagedLocks.Key> locks(Change change) {
        var result=new ArrayList<ManagedLocks.Key>();result.add(PortalPolicy.KEY);result.add(new ManagedLocks.Key(1,"QUOTA_MONTH",change.startMonth()));
        for(String id:userIds()){result.add(PortalPolicy.user(id));result.add(new ManagedLocks.Key(3,"PORTAL_USER",id));}
        return result;
    }
    private Set<String> allowed(Change change) {
        if(!change.deletion())return change.after().grades().stream().map(g->g.gradeId()==null?"new:"+g.clientKey():g.gradeId()).collect(java.util.stream.Collectors.toSet());
        return Set.copyOf(jdbc.queryForList("SELECT GRADE_ID FROM AI_ADMIN_QUOTA_GRADE_ITEM WHERE GRADE_SET_ID=(SELECT GRADE_SET_ID FROM AI_ADMIN_QUOTA_GRADE_SET WHERE START_MONTH<? ORDER BY START_MONTH DESC LIMIT 1)",String.class,change.startMonth()));
    }
    private record Move(String user,String month,String grade) {}
    private List<Move> moves(Change change) {
        var result=new ArrayList<Move>();var permitted=allowed(change);
        for(String user:userIds()) {
            long id=Long.parseLong(user);var points=new TreeMap<String,String>();var inherited=grades.at(id,change.startMonth());
            if(inherited!=null)points.put(change.startMonth(),inherited.get("GRADE_ID").toString());
            jdbc.query("SELECT EFFECTIVE_MONTH,GRADE_ID FROM AI_ADMIN_USER_QUOTA_GRADE WHERE PORTAL_USER_ID=? AND ACTIVE_YN='Y' AND EFFECTIVE_MONTH>? AND (CAST(? AS varchar) IS NULL OR EFFECTIVE_MONTH<?) ORDER BY EFFECTIVE_MONTH",r->{points.put(r.getString(1),r.getString(2));},id,change.startMonth(),change.nextMonth(),change.nextMonth());
            for(var point:points.entrySet())if(!permitted.contains(point.getValue())) {
                String to=change.deletion()?null:change.after().transitions().get(point.getValue());
                if(to==null||!permitted.contains(to))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                result.add(new Move(user,point.getKey(),to));
            }
            if(change.nextMonth()!=null&&!result.isEmpty()) {
                var next=grades.at(id,change.nextMonth());
                if(next!=null) {
                    String effective=next.get("EFFECTIVE_MONTH").toString(),value=next.get("GRADE_ID").toString();
                    for(var move:result)if(move.user().equals(user)&&move.month().compareTo(effective)>=0)value=move.grade();
                    var nextAllowed=jdbc.queryForList("SELECT GRADE_ID FROM AI_ADMIN_QUOTA_GRADE_ITEM WHERE GRADE_SET_ID=(SELECT GRADE_SET_ID FROM AI_ADMIN_QUOTA_GRADE_SET WHERE START_MONTH=?)",String.class,change.nextMonth());
                    if(!nextAllowed.contains(value))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                }
            }
        }
        return result;
    }
    public void validate(Change change) {
        dependencies.requireQuota();
        for(var move:moves(change))dependencies.mutable(move.user(),move.month());
        // 가중치·명칭 변경도 이미 확정된 해당 효력 월의 정책을 덮을 수 없다.
        for(String user:userIds())dependencies.mutable(user,change.startMonth());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void apply(Change change,ManagedAssetAdapter.Stamp stamp) {
        var grouped=new LinkedHashMap<String,List<Move>>();for(var move:moves(change))grouped.computeIfAbsent(move.user(),key->new ArrayList<>()).add(move);
        for(var entry:grouped.entrySet()) {
            long user=Long.parseLong(entry.getKey());var before=users.root(user);String revision=Long.toString(Math.addExact(((Number)before.get("REVISION")).longValue(),1));
            var userStamp=new ManagedAssetAdapter.Stamp(stamp.commandKey(),stamp.actor(),revision,ids.next("AI_ADMIN_HISTORY_NO"),stamp.at(),"CHANGE",stamp.reason());
            for(var move:entry.getValue())grades.write(user,move.month(),move.grade(),"Y","GRADE_TRANSFER",userStamp,grades.exact(user,move.month()));
            users.touch(user,userStamp);dependencies.changed(entry.getKey(),userStamp);users.history().capture(entry.getKey(),userStamp);
        }
    }
    public References references(Collection<String> gradeIds) {
        dependencies.requireQuota();var counts=new LinkedHashMap<String,Long>();
        for(String grade:gradeIds)counts.put(grade,jdbc.queryForObject("SELECT COUNT(DISTINCT PORTAL_USER_ID) FROM AI_ADMIN_USER_QUOTA_GRADE WHERE GRADE_ID=?",Long.class,Long.parseLong(grade)));
        return new References("READY",Map.copyOf(counts));
    }
}
