// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.domain.user.service.TemporaryPasswords;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthPasswordProps;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** LOCAL 보안 저장만 소유한다. 관리 전체 HIST에 해시를 포함하지 않는다. */
public final class PortalCredentials {
    final JdbcTemplate jdbc;
    final PasswordEncoder encoder;
    final ConfigLookup configs;
    private final PortalSecretDelivery secrets;
    public PortalCredentials(DataSource source,PasswordEncoder encoder,ConfigLookup configs,PortalSecretDelivery secrets) {
        jdbc=new JdbcTemplate(source);this.encoder=encoder;this.configs=configs;this.secrets=secrets;
    }
    public record Credential(String hash,boolean locked,int failures,boolean mustChange,Instant changed,long version,Instant lockedAt,Instant expires) {
        @Override public String toString(){return "PortalCredential[비밀 제외]";}
    }
    public Credential get(long user) {
        var found=jdbc.query("SELECT PASSWORD_HASH,LOCKED_YN,FAILED_COUNT,MUST_CHANGE_YN,PASSWORD_CHANGED_DTM,CREDENTIAL_VERSION,LOCKED_DTM,TEMP_EXPIRES_DTM FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",
                (r,n)->new Credential(r.getString(1),"Y".equals(r.getString(2)),r.getInt(3),"Y".equals(r.getString(4)),r.getTimestamp(5).toInstant(),r.getLong(6),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant(),r.getTimestamp(8)==null?null:r.getTimestamp(8).toInstant()),user);
        return found.isEmpty()?null:found.getFirst();
    }
    public void create(long user,Instant now) {
        // 등록은 발급이 아니다. 전달되지 않은 난수는 즉시 만료시키고 명시 초기화를 기다린다.
        jdbc.update("INSERT INTO AI_ADMIN_PORTAL_CREDENTIAL(PORTAL_USER_ID,PASSWORD_HASH,LOCKED_YN,FAILED_COUNT,MUST_CHANGE_YN,PASSWORD_CHANGED_DTM,CREDENTIAL_VERSION,TEMP_EXPIRES_DTM) VALUES (?,?,'N',0,'Y',?,1,?)",
                user,encoder.encode(UUID.randomUUID().toString()),Timestamp.from(now),Timestamp.from(now));
    }
    public void reset(long user,Instant now) {
        var policy=configs.get(AuthPasswordProps.class);if(!policy.adminResetEnabled())throw ManagedErrorCode.FORBIDDEN.failure();
        var before=require(user);String raw=TemporaryPasswords.generate(policy.minLength(),policy.requireCharsetKinds());
        replace(user,before,raw,now,true,now.plusSeconds(policy.tempValidHours()*3600L));secrets.issue(raw);
    }
    public void unlock(long user){require(user);jdbc.update("UPDATE AI_ADMIN_PORTAL_CREDENTIAL SET LOCKED_YN='N',LOCKED_DTM=NULL,FAILED_COUNT=0 WHERE PORTAL_USER_ID=?",user);}
    Credential require(long user){var value=get(user);if(value==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return value;}
    void replace(long user,Credential before,String password,Instant now,boolean temporary,Instant expires) {
        long next=Math.addExact(before.version(),1);
        jdbc.update("INSERT INTO AI_ADMIN_PORTAL_PASSWORD_HISTORY(PORTAL_USER_ID,PASSWORD_VERSION,PASSWORD_HASH,CHANGED_DTM) VALUES (?,?,?,?)",user,before.version(),before.hash(),Timestamp.from(before.changed()));
        jdbc.update("UPDATE AI_ADMIN_PORTAL_CREDENTIAL SET PASSWORD_HASH=?,MUST_CHANGE_YN=?,PASSWORD_CHANGED_DTM=?,CREDENTIAL_VERSION=?,TEMP_EXPIRES_DTM=? WHERE PORTAL_USER_ID=?",
                encoder.encode(password),temporary?"Y":"N",Timestamp.from(now),next,expires==null?null:Timestamp.from(expires),user);
        int count=configs.get(AuthPasswordProps.class).reuseBlockCount();
        if(count>0)jdbc.update("DELETE FROM AI_ADMIN_PORTAL_PASSWORD_HISTORY WHERE PORTAL_USER_ID=? AND PASSWORD_VERSION<=?",user,next-count-1);
    }
}
