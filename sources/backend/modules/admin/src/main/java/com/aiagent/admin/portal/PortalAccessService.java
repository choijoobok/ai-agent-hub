// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.core.security.*;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthPasswordProps;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재값 판정과 내부 실행 수락을 같은 공유 정책 보호 아래 수행한다. */
@Service
public class PortalAccessService {
    public record Access(String outcome,PrincipalRef principal,String revision,Instant checkedAt,Set<String> groups,
                         PortalQuotaPort.Allocation allocation,PortalAssetPolicy.Decision asset,String externalUserId,String reason) {
        public Access {groups=Set.copyOf(groups);}
    }
    private final JdbcTemplate jdbc;
    private final PortalPolicy policy;
    private final BusinessCalendar calendar;
    private final PortalDependencies dependencies;
    private final PortalGrades grades;
    private final ObjectProvider<PortalAssetPolicy> assets;
    private final ConfigLookup configs;
    private final String mode;
    public PortalAccessService(DataSource source,PortalPolicy policy,BusinessCalendar calendar,PortalDependencies dependencies,PortalGrades grades,ObjectProvider<PortalAssetPolicy> assets,ConfigLookup configs,@Value("${app.portal.auth-mode}") String mode){jdbc=new JdbcTemplate(source);this.policy=policy;this.calendar=calendar;this.dependencies=dependencies;this.grades=grades;this.assets=assets;this.configs=configs;this.mode=mode;}
    static long portal(PrincipalRef principal){if(principal==null||principal.domain()!=AuthDomain.PORTAL)throw ManagedErrorCode.FORBIDDEN.failure();return ManagedCommand.positive(principal.subjectId());}
    @Transactional
    public Access resolveCurrentPortalAccess(PrincipalRef principal,PortalAssetPolicy.Request operation){return withinCurrentAccess(principal,operation,Function.identity());}
    /** FEAT-303 조립용 현재 계정/소속 읽기. 허용/거부를 모두 같은 보호 구간의 콜백으로 전달한다. */
    @Transactional
    public <T> T inspectCurrentAccount(PrincipalRef principal,Function<Access,T> inspection){return withinCurrentAccess(principal,null,inspection);}
    /** 허용된 경우에만 실행 수락 callback을 호출한다. 반환 뒤 이 결과를 권한 토큰으로 재사용하지 않는다. */
    @Transactional
    public <T> T acceptCurrentAccess(PrincipalRef principal,PortalAssetPolicy.Request operation,Function<Access,T> acceptance) {
        return withinCurrentAccess(principal,operation,access->{
            if(!access.outcome().equals("ALLOW"))throw (access.outcome().equals("UNAVAILABLE")?ManagedErrorCode.UNAVAILABLE:ManagedErrorCode.FORBIDDEN).failure();
            return acceptance.apply(access);
        });
    }
    private <T> T withinCurrentAccess(PrincipalRef principal,PortalAssetPolicy.Request operation,Function<Access,T> acceptance) {
        long id=portal(principal);policy.protect(false);Instant now=policy.now();LocalDate today=now.atZone(calendar.zone()).toLocalDate();String month=YearMonth.from(today).toString();
        var found=jdbc.queryForList("SELECT REVISION,ACTIVE_YN,USE_START_DT,USE_END_DT,AUTH_MODE_CD FROM AI_ADMIN_PORTAL_USER WHERE PORTAL_USER_ID=?",id);
        if(found.isEmpty())return acceptance.apply(denied(principal,null,now,"ACCOUNT_UNAVAILABLE"));var user=found.getFirst();String revision=user.get("REVISION").toString();
        if(!mode.equals(user.get("AUTH_MODE_CD"))||!"Y".equals(user.get("ACTIVE_YN"))||today.isBefore(((java.sql.Date)user.get("USE_START_DT")).toLocalDate())||user.get("USE_END_DT")!=null&&today.isAfter(((java.sql.Date)user.get("USE_END_DT")).toLocalDate()))return acceptance.apply(denied(principal,revision,now,"ACCOUNT_UNAVAILABLE"));
        if("LOCAL".equals(user.get("AUTH_MODE_CD"))) {
            var credentials=jdbc.queryForList("SELECT LOCKED_YN,MUST_CHANGE_YN,PASSWORD_CHANGED_DTM FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",id);
            if(credentials.size()!=1||"Y".equals(credentials.getFirst().get("LOCKED_YN"))||"Y".equals(credentials.getFirst().get("MUST_CHANGE_YN")))return acceptance.apply(denied(principal,revision,now,"CREDENTIAL_RESTRICTED"));
            int days=configs.get(AuthPasswordProps.class).expireDays();
            if(days>0&&!now.isBefore(((java.sql.Timestamp)credentials.getFirst().get("PASSWORD_CHANGED_DTM")).toInstant().plusSeconds(days*86400L)))return acceptance.apply(denied(principal,revision,now,"CREDENTIAL_RESTRICTED"));
        }
        var groups=Set.copyOf(jdbc.queryForList("SELECT GROUP_ID FROM AI_ADMIN_USER_MEMBERSHIP WHERE PORTAL_USER_ID=? AND ACTIVE_YN='Y'",String.class,id));
        if(groups.isEmpty())return acceptance.apply(denied(principal,revision,now,"NO_ACTIVE_GROUP"));
        var grade=grades.at(id,month);
        if(grade==null||!grades.permitted(month).contains(grade.get("GRADE_ID").toString()))return acceptance.apply(denied(principal,revision,now,"GRADE_UNAVAILABLE"));
        var allocation=dependencies.allocation(principal.subjectId(),month);PortalAssetPolicy.Decision asset=null;String external=null;
        if(operation!=null) {
            var owners=assets.orderedStream().filter(p->p.ownerFeature().equals(operation.ownerFeature())).toList();
            if(owners.size()!=1)return acceptance.apply(new Access("UNAVAILABLE",principal,revision,now,Set.of(),allocation,null,null,"ASSET_OWNER_UNAVAILABLE"));
            asset=owners.getFirst().evaluate(principal,groups,operation);
            if(asset==null||!"ALLOW".equals(asset.outcome())||Collections.disjoint(groups,asset.allowedGroups()))return acceptance.apply(denied(principal,revision,now,"ASSET_DENIED"));
            if(operation.quotaRequired()&&!"READY".equals(allocation.state()))return acceptance.apply(new Access("UNAVAILABLE",principal,revision,now,Set.of(),allocation,null,null,"QUOTA_NOT_READY"));
            if(operation.identityKind()!=null) {
                if(!Set.of("SYSTEM","DATA_SOURCE").contains(operation.identityKind())||operation.identityTarget()==null)throw ManagedErrorCode.VALIDATION.failure();
                var identities=jdbc.queryForList("SELECT EXTERNAL_USER_ID FROM AI_ADMIN_USER_IDENTITY WHERE PORTAL_USER_ID=? AND TARGET_KIND=? AND TARGET_ID=? AND MAPPING_ACTIVE_YN='Y'",String.class,id,operation.identityKind(),ManagedCommand.positive(operation.identityTarget()));
                if(identities.size()!=1)return acceptance.apply(denied(principal,revision,now,"IDENTITY_UNAVAILABLE"));external=identities.getFirst();
            }
        }
        return acceptance.apply(new Access("ALLOW",principal,revision,now,groups,allocation,asset,external,"CURRENT"));
    }
    private Access denied(PrincipalRef principal,String revision,Instant now,String reason){return new Access("DENY",principal,revision,now,Set.of(),null,null,null,reason);}
}
