// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.core.security.*;
import com.aiagent.domain.user.service.PasswordRules;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.*;
import com.aiagent.support.time.BusinessCalendar;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Portal 인증 소유 공개 포트. 입력 비밀은 반환·예외·관리 이력에 포함하지 않는다. */
@Service
public class PortalAccountService {
    public record Verification(String outcome,PrincipalRef principal,String credentialVersion,String userNm) {}
    public record PasswordChange(String outcome,String credentialVersion,Instant changedAt) {}
    public record Account(PrincipalRef principal,String userNm,String authMode,String credentialVersion,boolean restricted) {}
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final PortalCredentials credentials;
    private final PortalPolicy policy;
    private final BusinessCalendar calendar;
    private final ConfigLookup configs;
    private final ObjectProvider<PortalSecurityEvents> events;
    private final PortalDependencies dependencies;
    private final String mode,dummyHash;
    public PortalAccountService(DataSource source,PortalCredentials credentials,PortalPolicy policy,BusinessCalendar calendar,ConfigLookup configs,ObjectProvider<PortalSecurityEvents> events,PortalDependencies dependencies,@Value("${app.portal.auth-mode}") String mode,ObjectProvider<PortalExternalDirectory> external) {
        this.source=source;jdbc=new JdbcTemplate(source);this.credentials=credentials;this.policy=policy;this.calendar=calendar;this.configs=configs;this.events=events;this.dependencies=dependencies;this.mode=mode;dummyHash=credentials.encoder.encode(UUID.randomUUID().toString());
        if(!Set.of("LOCAL","EXTERNAL").contains(mode)||mode.equals("EXTERNAL")&&external.getIfUnique()==null)throw new IllegalStateException("Portal 인증 모드와 검증 어댑터 구성이 필요하다");
    }
    private void audit(PortalSecurityEvents.Kind kind,PrincipalRef principal,String result,Instant now) {
        var sink=events.getIfUnique();if(sink==null||sink.dataSource()!=source)throw ManagedErrorCode.UNAVAILABLE.failure();
        sink.record(new PortalSecurityEvents.Event(kind,principal,result,now));
    }
    private static boolean bounded(String value){return value!=null&&!value.isEmpty()&&value.getBytes(StandardCharsets.UTF_8).length<=72;}
    private boolean eligible(Map<String,Object> user,Instant now) {
        LocalDate today=now.atZone(calendar.zone()).toLocalDate();
        return "Y".equals(user.get("ACTIVE_YN"))&&!today.isBefore(((java.sql.Date)user.get("USE_START_DT")).toLocalDate())
                &&(user.get("USE_END_DT")==null||!today.isAfter(((java.sql.Date)user.get("USE_END_DT")).toLocalDate()));
    }
    private boolean restricted(PortalCredentials.Credential credential,Instant now){int days=configs.get(AuthPasswordProps.class).expireDays();return credential.mustChange()||days>0&&!now.isBefore(credential.changed().plusSeconds(days*86400L));}
    private Map<String,Object> root(long id){var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_PORTAL_USER WHERE PORTAL_USER_ID=? FOR UPDATE",id);return rows.isEmpty()?null:rows.getFirst();}
    /** 실패도 정상 결과로 반환해 호출자 REQUIRED 트랜잭션이 실패횟수와 감사 사건을 확정할 수 있다. */
    @Transactional
    public Verification verifyLocalCredentials(String loginId,String password) {
        policy.protect(true);Instant now=policy.now();PrincipalRef principal=null;String reason="REJECTED";
        if(!mode.equals("LOCAL"))return rejected(null,"MODE_UNAVAILABLE",now);
        var ids=loginId==null?List.<Long>of():jdbc.queryForList("SELECT PORTAL_USER_ID FROM AI_ADMIN_PORTAL_USER WHERE LOGIN_ID=?",Long.class,loginId.strip());
        if(ids.size()!=1){if(bounded(password))credentials.encoder.matches(password,dummyHash);return rejected(null,reason,now);}
        long id=ids.getFirst();principal=new PrincipalRef(AuthDomain.PORTAL,Long.toString(id));var user=root(id);
        if(!"LOCAL".equals(user.get("AUTH_MODE_CD"))||!eligible(user,now))return rejected(principal,"ACCOUNT_UNAVAILABLE",now);
        var credential=credentials.require(id);var login=configs.get(AuthLoginProps.class);
        if(credential.locked()&&login.lockReleaseMinutes()>0&&credential.lockedAt()!=null&&!now.isBefore(credential.lockedAt().plusSeconds(login.lockReleaseMinutes()*60L))) {credentials.unlock(id);credential=credentials.require(id);}
        if(credential.locked())return rejected(principal,"LOCKED",now);
        if(!bounded(password)||!credentials.encoder.matches(password,credential.hash())) {
            int failures=Math.addExact(credential.failures(),1);boolean locked=login.failLimit()>0&&failures>=login.failLimit();
            jdbc.update("UPDATE AI_ADMIN_PORTAL_CREDENTIAL SET FAILED_COUNT=?,LOCKED_YN=?,LOCKED_DTM=? WHERE PORTAL_USER_ID=?",failures,locked?"Y":"N",locked?Timestamp.from(now):null,id);
            return rejected(principal,"REJECTED",now);
        }
        if(credential.mustChange()&&(credential.expires()==null||!now.isBefore(credential.expires())))return rejected(principal,"TEMP_EXPIRED",now);
        credentials.unlock(id);String outcome=restricted(credential,now)?"PASSWORD_CHANGE_REQUIRED":"AUTHENTICATED";
        audit(PortalSecurityEvents.Kind.LOGIN_ACCEPTED,principal,outcome,now);
        return new Verification(outcome,principal,Long.toString(credential.version()),user.get("USER_NM").toString());
    }
    private Verification rejected(PrincipalRef principal,String reason,Instant now){audit(PortalSecurityEvents.Kind.LOGIN_REJECTED,principal,reason,now);return new Verification(reason,null,null,null);}
    @Transactional
    public PasswordChange changeOwnLocalPassword(PrincipalRef principal,String expectedVersion,String oldPassword,String nextPassword) {
        long id=PortalAccessService.portal(principal);policy.protect(true);Instant now=policy.now();var user=root(id);
        if(!mode.equals("LOCAL")||user==null||!"LOCAL".equals(user.get("AUTH_MODE_CD"))||!eligible(user,now))throw ManagedErrorCode.FORBIDDEN.failure();
        var before=credentials.require(id);if(!Long.toString(before.version()).equals(expectedVersion))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        if(before.locked()||before.mustChange()&&(before.expires()==null||!now.isBefore(before.expires()))||!bounded(oldPassword)||!credentials.encoder.matches(oldPassword,before.hash()))return new PasswordChange("REJECTED",null,null);
        var settings=configs.get(AuthPasswordProps.class);
        if(!bounded(nextPassword)||!PasswordRules.satisfies(nextPassword,settings.minLength(),settings.requireCharsetKinds())||credentials.encoder.matches(nextPassword,before.hash()))return new PasswordChange("POLICY_REJECTED",null,null);
        var hashes=jdbc.queryForList("SELECT PASSWORD_HASH FROM AI_ADMIN_PORTAL_PASSWORD_HISTORY WHERE PORTAL_USER_ID=? ORDER BY PASSWORD_VERSION DESC LIMIT ?",String.class,id,settings.reuseBlockCount());
        if(hashes.stream().anyMatch(hash->credentials.encoder.matches(nextPassword,hash)))return new PasswordChange("POLICY_REJECTED",null,null);
        credentials.replace(id,before,nextPassword,now,false,null);credentials.unlock(id);audit(PortalSecurityEvents.Kind.PASSWORD_CHANGED,principal,"CHANGED",now);
        return new PasswordChange("CHANGED",Long.toString(before.version()+1),now);
    }
    /** 검증된 외부 디렉터리 계약을 다시 대조한다. 원 SSO 증거 소비는 검증 어댑터 소유다. */
    @Transactional
    public Account resolveVerifiedExternalIdentity(PortalExternalDirectory.Identity verified) {
        if(!mode.equals("EXTERNAL")||verified==null)throw ManagedErrorCode.FORBIDDEN.failure();policy.protect(false);
        if(!verified.equals(dependencies.external(verified.sourceKey(),verified.subject())))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        var ids=jdbc.queryForList("SELECT PORTAL_USER_ID FROM AI_ADMIN_PORTAL_EXTERNAL_ID WHERE SOURCE_KEY=? AND SUBJECT_ID=?",Long.class,verified.sourceKey(),verified.subject());
        if(ids.size()!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();long id=ids.getFirst();var user=root(id);
        if(user==null||!"EXTERNAL".equals(user.get("AUTH_MODE_CD"))||!eligible(user,policy.now()))throw ManagedErrorCode.FORBIDDEN.failure();
        return new Account(new PrincipalRef(AuthDomain.PORTAL,Long.toString(id)),user.get("USER_NM").toString(),"EXTERNAL",null,false);
    }
    @Transactional
    public Account currentAccount(PrincipalRef principal,String expectedVersion) {
        long id=PortalAccessService.portal(principal);policy.protect(false);var user=root(id);Instant now=policy.now();
        if(user==null||!mode.equals(user.get("AUTH_MODE_CD"))||!eligible(user,now))throw ManagedErrorCode.FORBIDDEN.failure();
        var credential=mode.equals("LOCAL")?credentials.require(id):null;
        if(credential!=null&&(credential.locked()||!Long.toString(credential.version()).equals(expectedVersion)||credential.mustChange()&&(credential.expires()==null||!now.isBefore(credential.expires()))))throw ManagedErrorCode.FORBIDDEN.failure();
        return new Account(principal,user.get("USER_NM").toString(),mode,credential==null?null:Long.toString(credential.version()),credential!=null&&restricted(credential,now));
    }
}
