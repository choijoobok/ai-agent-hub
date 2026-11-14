// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.QuotaGradeQueryService;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthPasswordProps;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class PortalUserQueryService {
    private static final PathBuilder<PortalUser> U=new PathBuilder<>(PortalUser.class,"portalUser");
    private static final QuerySpec SPEC=QuerySpec.of("portalUser").from(U)
            .col("loginId",U.getString("loginId")).sortable().col("userNm",U.getString("userNm")).sortable()
            .col("organizationNm",U.getString("organizationNm")).sortable().col("activeYn",U.getString("activeYn"))
            .col("useStartDt",U.getDate("useStartDt",LocalDate.class)).sortable().col("useEndDt",U.getDate("useEndDt",LocalDate.class)).sortable()
            .col("regDtm",U.getDateTime("regDtm",Instant.class)).sortable().col("modDtm",U.getDateTime("modDtm",Instant.class)).sortable()
            .defaultSort("loginId",SortDir.asc).pk(U.getNumber("portalUserId",Long.class)).build();
    public record Row(String portalUserId,String loginId,String userNm,String organizationNm,String authModeCd,String sourceCd,
                      LocalDate useStartDt,LocalDate useEndDt,String activeYn,String revision,String lockedYn,Boolean mustChange,Instant passwordChangedDtm,String regId,Instant regDtm,String modId,Instant modDtm) {}
    public record Detail(Row user,String scope,List<Map<String,Object>> rows,List<Map<String,Object>> choices,
                         String state,String today,PortalQuotaPort.Allocation allocation,List<QuotaGradeQueryService.Grade> gradeChoices,
                         Map<String,Object> credential) {}
    private final JPAQueryFactory query;
    private final QueryDslBuilder builder;
    private final JdbcTemplate jdbc;
    private final PortalPolicy policy;
    private final BusinessCalendar calendar;
    private final PortalDependencies dependencies;
    private final QuotaGradeQueryService definitions;
    private final String mode;
    private final ConfigLookup configs;
    public PortalUserQueryService(JPAQueryFactory query,QueryDslBuilder builder,DataSource source,PortalPolicy policy,BusinessCalendar calendar,PortalDependencies dependencies,QuotaGradeQueryService definitions,ConfigLookup configs,@Value("${app.portal.auth-mode}") String mode) {
        this.query=query;this.builder=builder;jdbc=new JdbcTemplate(source);this.policy=policy;this.calendar=calendar;this.dependencies=dependencies;this.definitions=definitions;this.mode=mode;this.configs=configs;
    }
    /** 페이지의 안전한 자격 상태만 한 번에 읽는다. 해시·임시 암호는 조회 DTO에 포함하지 않는다. */
    private List<Row> rows(List<PortalUser> values) {
        if(values.isEmpty())return List.of();
        var state=new HashMap<Long,Map<String,Object>>();
        new NamedParameterJdbcTemplate(jdbc).queryForList("SELECT PORTAL_USER_ID,LOCKED_YN,MUST_CHANGE_YN,PASSWORD_CHANGED_DTM FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID IN (:ids)",Map.of("ids",values.stream().map(u->u.portalUserId).toList())).forEach(row->state.put(((Number)row.get("PORTAL_USER_ID")).longValue(),row));
        Instant now=policy.now();int days=configs.get(AuthPasswordProps.class).expireDays();
        return values.stream().map(u->{var c=state.get(u.portalUserId);Instant changed=c==null?null:((java.sql.Timestamp)c.get("PASSWORD_CHANGED_DTM")).toInstant();
            Boolean change=c==null?null:"Y".equals(c.get("MUST_CHANGE_YN"))||days>0&&!now.isBefore(changed.plusSeconds(days*86400L));
            return new Row(u.portalUserId.toString(),u.loginId,u.userNm,u.organizationNm,u.authModeCd,u.sourceCd,u.useStartDt,u.useEndDt,u.activeYn,u.revision.toString(),c==null?null:c.get("LOCKED_YN").toString(),change,changed,u.regId,u.regDtm,u.modId,u.modDtm);
        }).toList();
    }
    public PageResponse<Row> getList(SearchRequest<Void> request) {
        policy.protect(false);var built=builder.build(request,SPEC,PageMode.offset);
        var rows=rows(built.apply(query.select(U)).fetch());Long count=built.count(query).fetchOne();return built.respond(rows,count==null?0:count);
    }
    private Row require(String id){var value=query.selectFrom(U).where(U.getNumber("portalUserId",Long.class).eq(ManagedCommand.positive(id))).fetchOne();if(value==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return rows(List.of(value)).getFirst();}
    private static final String AUDIT=",REG_ID AS \"regId\",REG_DTM AS \"regDtm\",MOD_ID AS \"modId\",MOD_DTM AS \"modDtm\"";
    public Detail getOne(String id,String scope,String effectiveMonth) {
        policy.protect(false);String today=policy.now().atZone(calendar.zone()).toLocalDate().toString(),month=effectiveMonth==null?today.substring(0,7):effectiveMonth;
        com.aiagent.admin.quota.QuotaGradeDefinition.month(month);
        if(id==null&&"defaults".equals(scope))return new Detail(null,scope,List.of(),List.of(),"READY",today,null,List.of(),Map.of("authMode",mode));
        var user=require(id);long target=Long.parseLong(id);List<Map<String,Object>> rows=List.of(),choices=List.of();String state="READY";PortalQuotaPort.Allocation allocation=null;List<QuotaGradeQueryService.Grade> gradeChoices=List.of();Map<String,Object> credential=Map.of();
        switch(scope) {
            case "profile" -> {
                var found=jdbc.queryForList("SELECT LOCKED_YN AS \"lockedYn\",FAILED_COUNT AS \"failedCount\",MUST_CHANGE_YN AS \"mustChangeYn\",TEMP_EXPIRES_DTM AS \"tempExpiresDtm\" FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",target);
                if(!found.isEmpty())credential=found.getFirst();
            }
            case "memberships" -> {
                rows=jdbc.queryForList("SELECT CAST(M.GROUP_ID AS varchar) AS \"groupId\",G.GROUP_NM AS \"groupNm\",M.ACTIVE_YN AS \"activeYn\",M.MEMO AS \"memo\",M.REG_ID AS \"regId\",M.REG_DTM AS \"regDtm\",M.MOD_ID AS \"modId\",M.MOD_DTM AS \"modDtm\" FROM AI_ADMIN_USER_MEMBERSHIP M JOIN AI_ADMIN_USER_GROUP G ON G.GROUP_ID=M.GROUP_ID WHERE M.PORTAL_USER_ID=? ORDER BY G.GROUP_NM",target);
                choices=jdbc.queryForList("SELECT CAST(GROUP_ID AS varchar) AS \"groupId\",GROUP_NM AS \"groupNm\" FROM AI_ADMIN_USER_GROUP ORDER BY GROUP_NM");
            }
            case "grades" -> {
                rows=jdbc.queryForList("SELECT EFFECTIVE_MONTH AS \"effectiveMonth\",CAST(GRADE_ID AS varchar) AS \"gradeId\",(SELECT I.GRADE_NM FROM AI_ADMIN_QUOTA_GRADE_ITEM I JOIN AI_ADMIN_QUOTA_GRADE_SET S ON S.GRADE_SET_ID=I.GRADE_SET_ID WHERE I.GRADE_ID=U.GRADE_ID AND S.START_MONTH<=U.EFFECTIVE_MONTH ORDER BY S.START_MONTH DESC LIMIT 1) AS \"gradeNm\",ACTIVE_YN AS \"activeYn\",SOURCE_KIND AS \"sourceKind\",CHANGE_REASON AS \"changeReason\""+AUDIT+" FROM AI_ADMIN_USER_QUOTA_GRADE U WHERE PORTAL_USER_ID=? ORDER BY EFFECTIVE_MONTH DESC",target);
                allocation=dependencies.allocation(id,month);state=allocation.state();var definition=definitions.effective(month);gradeChoices=definition==null?List.of():definition.grades();
            }
            case "extraTokens" -> {allocation=dependencies.allocation(id,month);state=allocation.state();rows=dependencies.extras(id);}
            case "identitySystem","identityDataSource" -> {
                String kind=PortalUserService.identityKind(scope);var targets=dependencies.targets(kind);state=targets.state();
                // 공개 가능한 후보와 현재 매핑을 교집합한다. 과거 매핑의 존재로 기술 대상 권한을 복원하지 않는다.
                var allowed=targets.rows().stream().map(PortalIdentityTargets.Target::targetId).collect(java.util.stream.Collectors.toSet());
                rows=jdbc.queryForList("SELECT CAST(TARGET_ID AS varchar) AS \"targetId\",EXTERNAL_USER_ID AS \"externalUserId\",MAPPING_ACTIVE_YN AS \"mappingActiveYn\",IDENTITY_EVIDENCE AS \"identityEvidence\""+AUDIT+" FROM AI_ADMIN_USER_IDENTITY WHERE PORTAL_USER_ID=? AND TARGET_KIND=?",target,kind).stream().filter(r->allowed.contains(r.get("targetId"))).toList();
                choices=targets.rows().stream().map(t->Map.<String,Object>of("targetId",t.targetId(),"targetNm",t.name(),"available",t.available(),"reason",t.reason()==null?"":t.reason())).toList();
            }
            default -> throw ManagedErrorCode.VALIDATION.failure();
        }
        return new Detail(user,scope,rows,choices,state,today,allocation,gradeChoices,credential);
    }
}
