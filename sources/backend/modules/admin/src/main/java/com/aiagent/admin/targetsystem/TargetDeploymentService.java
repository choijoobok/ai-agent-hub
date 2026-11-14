// @feat FEAT-001 FEAT-302
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
import com.aiagent.support.deployment.TargetConnections;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보호된 운영 호출만 허용 상태를 바꾼다. 기동 등록이나 같은 지문만으로 재가입하지 않는다. */
@Service
public class TargetDeploymentService {
    private final JdbcTemplate jdbc;private final ManagedLocks locks;private final Clock clock;
    public TargetDeploymentService(DataSource source,ManagedLocks locks,Clock clock){jdbc=new JdbcTemplate(source);this.locks=locks;this.clock=clock;}
    static ManagedLocks.Key boundary(String environment){return new ManagedLocks.Key(1,"TARGET_CONFIGURATION",environment);}
    @Transactional public void stage(TargetConnections loaded) {
        locks.ordered(List.of(boundary(loaded.environment())));
        jdbc.update("INSERT INTO AI_ADMIN_TARGET_DEPLOYMENT(ENVIRONMENT_ID,CONTROL_VERSION,MOD_DTM) VALUES (?,0,?) ON CONFLICT DO NOTHING",loaded.environment(),Timestamp.from(clock.instant()));
        jdbc.update("INSERT INTO AI_ADMIN_TARGET_BOOT(BOOT_ID,ENVIRONMENT_ID,CONFIG_ID,ALLOWED_YN,READY_YN,REG_DTM) VALUES (?,?,?,'N','N',?) ON CONFLICT DO NOTHING",loaded.bootId().toString(),loaded.environment(),loaded.settingsId(),Timestamp.from(clock.instant()));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT WHERE BOOT_ID=? AND ENVIRONMENT_ID=? AND CONFIG_ID=?",Long.class,loaded.bootId().toString(),loaded.environment(),loaded.settingsId())!=1)throw ManagedErrorCode.UNAVAILABLE.failure();
    }
    /** HTTP 메뉴에 노출하지 않는 운영 진입점. 호출자는 보호된 OS/배포 도구에서 검증된다. */
    @Transactional public long allow(String environment,UUID boot,long expectedVersion,UUID command,String operator,boolean retainExisting) {
        if(environment==null||!environment.matches("[A-Za-z0-9_-]{1,100}")||boot==null||command==null||operator==null||operator.isBlank()||operator.length()>100)throw ManagedErrorCode.VALIDATION.failure();
        locks.command(command.toString());
        locks.ordered(List.of(boundary(environment)));
        var control=jdbc.queryForMap("SELECT * FROM AI_ADMIN_TARGET_DEPLOYMENT WHERE ENVIRONMENT_ID=? FOR UPDATE",environment);
        String digest=ManagedDigest.hash(environment+"/"+boot+"/"+expectedVersion+"/"+operator+"/"+retainExisting);
        var previous=jdbc.queryForList("SELECT REQUEST_DIGEST,RESULT_VERSION FROM AI_ADMIN_TARGET_DEPLOYMENT_COMMAND WHERE COMMAND_KEY=?",command.toString());
        if(!previous.isEmpty()){if(!digest.equals(previous.getFirst().get("REQUEST_DIGEST")))throw ManagedErrorCode.COMMAND_CONFLICT.failure();return ((Number)previous.getFirst().get("RESULT_VERSION")).longValue();}
        if(((Number)control.get("CONTROL_VERSION")).longValue()!=expectedVersion)throw ManagedErrorCode.REVISION_CONFLICT.failure();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT WHERE BOOT_ID=? AND ENVIRONMENT_ID=?",Long.class,boot.toString(),environment)!=1)throw ManagedErrorCode.VALIDATION.failure();
        long next=Math.addExact(expectedVersion,1);
        if(!retainExisting)jdbc.update("UPDATE AI_ADMIN_TARGET_BOOT SET ALLOWED_YN='N' WHERE ENVIRONMENT_ID=?",environment);
        jdbc.update("UPDATE AI_ADMIN_TARGET_BOOT SET ALLOWED_YN='Y' WHERE BOOT_ID=? AND ENVIRONMENT_ID=?",boot.toString(),environment);
        jdbc.update("UPDATE AI_ADMIN_TARGET_DEPLOYMENT SET CONTROL_VERSION=?,WRITER_BOOT_ID=?,LAST_COMMAND_KEY=?,LAST_COMMAND_DIGEST=?,MOD_ID=?,MOD_DTM=? WHERE ENVIRONMENT_ID=?",next,boot.toString(),command.toString(),digest,operator,Timestamp.from(clock.instant()),environment);
        jdbc.update("INSERT INTO AI_ADMIN_TARGET_DEPLOYMENT_COMMAND(COMMAND_KEY,ENVIRONMENT_ID,REQUEST_DIGEST,RESULT_VERSION,OPERATOR_ID,RECORDED_DTM) VALUES (?,?,?,?,?,?)",command.toString(),environment,digest,next,operator,Timestamp.from(clock.instant()));
        return next;
    }
    public boolean allowed(TargetConnections loaded) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT WHERE BOOT_ID=? AND ENVIRONMENT_ID=? AND CONFIG_ID=? AND ALLOWED_YN='Y'",Long.class,loaded.bootId().toString(),loaded.environment(),loaded.settingsId())==1;
    }
    public boolean ready(TargetConnections loaded) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT WHERE BOOT_ID=? AND ENVIRONMENT_ID=? AND CONFIG_ID=? AND ALLOWED_YN='Y' AND READY_YN='Y'",Long.class,loaded.bootId().toString(),loaded.environment(),loaded.settingsId())==1;
    }
    void requireWriter(TargetConnections loaded) {
        locks.ordered(List.of(boundary(loaded.environment())));
        if(!allowed(loaded)||jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_DEPLOYMENT WHERE ENVIRONMENT_ID=? AND WRITER_BOOT_ID=?",Long.class,loaded.environment(),loaded.bootId().toString())!=1)throw ManagedErrorCode.UNAVAILABLE.failure();
    }
    public void requireReady(TargetConnections loaded){if(!ready(loaded))throw ManagedErrorCode.UNAVAILABLE.failure();}
    boolean synchronizedBefore(TargetConnections loaded){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_BOOT WHERE ENVIRONMENT_ID=? AND CONFIG_ID=? AND READY_YN='Y'",Long.class,loaded.environment(),loaded.settingsId())>0;}
    void markReady(TargetConnections loaded){requireWriter(loaded);jdbc.update("UPDATE AI_ADMIN_TARGET_BOOT SET READY_YN='Y' WHERE BOOT_ID=?",loaded.bootId().toString());}
    @Transactional(readOnly=true) public List<Map<String,Object>> inspect(String environment){return jdbc.queryForList("SELECT B.BOOT_ID,B.CONFIG_ID,B.ALLOWED_YN,B.READY_YN,B.REG_DTM,D.CONTROL_VERSION,D.WRITER_BOOT_ID FROM AI_ADMIN_TARGET_BOOT B JOIN AI_ADMIN_TARGET_DEPLOYMENT D ON D.ENVIRONMENT_ID=B.ENVIRONMENT_ID WHERE B.ENVIRONMENT_ID=? ORDER BY B.REG_DTM",environment);}
}
