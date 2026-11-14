// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.QuotaGradeQueryService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** 사용자별 등급의 효력 예약. 토큰 한도/사용량을 직접 쓰지 않는다. */
public final class PortalGrades {
    private final JdbcTemplate jdbc;
    private final QuotaGradeQueryService definitions;
    private final PortalDependencies dependencies;
    public PortalGrades(DataSource source,QuotaGradeQueryService definitions,PortalDependencies dependencies){jdbc=new JdbcTemplate(source);this.definitions=definitions;this.dependencies=dependencies;}
    String initialGrade(String month){var definition=definitions.effective(month);if(definition==null||definition.grades().isEmpty())throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return definition.grades().getFirst().gradeId();}
    public Set<String> permitted(String month) {
        var definition=definitions.effective(month);if(definition==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        var result=new HashSet<String>();definition.grades().forEach(row->result.add(row.gradeId()));return result;
    }
    Map<String,Object> at(long user,String month) {
        var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_USER_QUOTA_GRADE WHERE PORTAL_USER_ID=? AND EFFECTIVE_MONTH<=? AND ACTIVE_YN='Y' ORDER BY EFFECTIVE_MONTH DESC LIMIT 1",user,month);return rows.isEmpty()?null:rows.getFirst();
    }
    Map<String,Object> exact(long user,String month) {
        var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_USER_QUOTA_GRADE WHERE PORTAL_USER_ID=? AND EFFECTIVE_MONTH=?",user,month);return rows.isEmpty()?null:rows.getFirst();
    }
    void requireEffective(long user,String month){var grade=at(user,month);if(grade==null||!permitted(month).contains(grade.get("GRADE_ID").toString()))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();}
    void initial(long user,String month,ManagedAssetAdapter.Stamp stamp){
        String selected=initialGrade(month);write(user,month,selected,"Y","INITIAL",stamp,null);
        for(String future:jdbc.queryForList("SELECT START_MONTH FROM AI_ADMIN_QUOTA_GRADE_SET WHERE START_MONTH>? ORDER BY START_MONTH",String.class,month)) {
            if(permitted(future).contains(selected))continue;
            dependencies.mutable(Long.toString(user),future);selected=initialGrade(future);write(user,future,selected,"Y","INITIAL",stamp,null);
        }
    }
    public void validate(long user,List<Map<String,Object>> rows,LocalDate today) {
        String current=YearMonth.from(today).toString();
        for(var row:rows) {
            String month=(String)row.get("effectiveMonth"),original=(String)row.getOrDefault("originalMonth",month);var before=exact(user,original);
            if(month.compareTo(current)<0||original.compareTo(current)<0||before==null&&month.compareTo(current)<=0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            if(!original.equals(month)&&(before==null||original.compareTo(current)<=0||month.compareTo(current)<=0||exact(user,month)!=null))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            if("N".equals(row.get("activeYn"))&&(before==null||month.compareTo(current)<=0))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            if(!permitted(month).contains(row.get("gradeId")))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            dependencies.mutable(Long.toString(user),month);
            if(!original.equals(month))dependencies.mutable(Long.toString(user),original);
        }
    }
    public boolean changed(long user,List<Map<String,Object>> rows) {
        return rows.stream().anyMatch(row->{String month=(String)row.get("effectiveMonth");var old=exact(user,month);return old==null||!month.equals(row.getOrDefault("originalMonth",month))||!PortalUserService.same(old,"GRADE_ID",row,"gradeId")||!PortalUserService.same(old,"ACTIVE_YN",row,"activeYn");});
    }
    public void apply(long user,List<Map<String,Object>> rows,ManagedAssetAdapter.Stamp stamp) {
        for(var row:rows) {
            String month=(String)row.get("effectiveMonth"),original=(String)row.getOrDefault("originalMonth",month);var before=exact(user,original);
            if(!original.equals(month))jdbc.update("DELETE FROM AI_ADMIN_USER_QUOTA_GRADE WHERE PORTAL_USER_ID=? AND EFFECTIVE_MONTH=?",user,original);
            write(user,month,(String)row.get("gradeId"),(String)row.get("activeYn"),"ADMIN",stamp,before);
        }
    }
    /** 예약 취소/이동 뒤 상속되는 등급도 모든 이후 기준 경계에서 유효해야 한다. */
    void requireValidSchedule(long user,String currentMonth) {
        var months=new TreeSet<String>();months.add(currentMonth);
        months.addAll(jdbc.queryForList("SELECT EFFECTIVE_MONTH FROM AI_ADMIN_USER_QUOTA_GRADE WHERE PORTAL_USER_ID=? AND ACTIVE_YN='Y' AND EFFECTIVE_MONTH>=?",String.class,user,currentMonth));
        months.addAll(jdbc.queryForList("SELECT START_MONTH FROM AI_ADMIN_QUOTA_GRADE_SET WHERE START_MONTH>=?",String.class,currentMonth));
        for(String month:months)requireEffective(user,month);
    }
    void write(long user,String month,String grade,String active,String source,ManagedAssetAdapter.Stamp stamp,Map<String,Object> original) {
        var old=exact(user,month);var at=Timestamp.from(stamp.at());
        if(old==null)jdbc.update("INSERT INTO AI_ADMIN_USER_QUOTA_GRADE(PORTAL_USER_ID,EFFECTIVE_MONTH,GRADE_ID,ACTIVE_YN,SOURCE_KIND,SOURCE_KEY,ACCEPTED_DTM,CHANGE_REASON,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                user,month,Long.parseLong(grade),active,source,stamp.commandKey(),at,stamp.reason(),original==null?stamp.actor():original.get("REG_ID"),original==null?at:original.get("REG_DTM"),stamp.actor(),at);
        else if(!grade.equals(old.get("GRADE_ID").toString())||!active.equals(old.get("ACTIVE_YN")))jdbc.update("UPDATE AI_ADMIN_USER_QUOTA_GRADE SET GRADE_ID=?,ACTIVE_YN=?,SOURCE_KIND=?,SOURCE_KEY=?,ACCEPTED_DTM=?,CHANGE_REASON=?,MOD_ID=?,MOD_DTM=? WHERE PORTAL_USER_ID=? AND EFFECTIVE_MONTH=?",
                Long.parseLong(grade),active,source,stamp.commandKey(),at,stamp.reason(),stamp.actor(),at,user,month);
    }
}
