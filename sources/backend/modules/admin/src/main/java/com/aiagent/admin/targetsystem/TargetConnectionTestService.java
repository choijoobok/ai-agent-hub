// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.core.deployment.*;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.support.deployment.TargetConnections;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기술 입력은 서버 설정만 사용하며 응답 본문·예외 원문은 밖으로 내보내지 않는다. */
@Service
public class TargetConnectionTestService {
    public record Request(String systemId,String connectionId,String appliedSettingsId) {}
    public record Result(Instant checkedAt,String appliedSettingsId,String status,long elapsedMs,String reasonCode) {}
    private final JdbcTemplate jdbc;
    private final TargetConnections loaded;
    private final ConfigurationDeploymentService deployment;
    private final ManagedLocks locks;
    private final TargetSystemAccess access;
    private final Clock clock;
    public TargetConnectionTestService(DataSource source,TargetConnections loaded,ConfigurationDeploymentService deployment,ManagedLocks locks,TargetSystemAccess access,Clock clock){
        jdbc=new JdbcTemplate(source);this.loaded=loaded;this.deployment=deployment;this.locks=locks;this.access=access;this.clock=clock;
    }
    @Transactional
    public Result test(Request request){
        access.require(ActionType.READ,ActionType.EXECUTE);
        if(request==null||request.connectionId()==null||request.appliedSettingsId()==null)throw ManagedErrorCode.VALIDATION.failure();
        long id=ManagedCommand.positive(request.systemId());
        // 운영 허용 전환과 같은 경계: 진행 중인 시험을 지나 새 세대가 확정된다.
        locks.ordered(List.of(ConfigurationDeploymentService.boundary(loaded.environment()),new ManagedLocks.Key(3,"TARGET_SYSTEM",request.systemId())));
        deployment.requireReady(loaded);
        if(!loaded.settingsId().equals(request.appliedSettingsId()))throw ManagedErrorCode.REVISION_CONFLICT.failure();
        var keys=jdbc.queryForList("SELECT SYSTEM_KEY FROM AI_ADMIN_TARGET_SYSTEM S JOIN AI_ADMIN_TARGET_SYSTEM_CONN C ON C.TARGET_SYSTEM_ID=S.TARGET_SYSTEM_ID WHERE S.TARGET_SYSTEM_ID=? AND C.CONNECTION_ID=?",String.class,id,request.connectionId());
        if(keys.size()!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        var selected=loaded.require(keys.getFirst(),request.connectionId());
        var test=selected.test();
        if(test==null)return new Result(clock.instant(),loaded.settingsId(),"UNAVAILABLE",0,"NOT_CONFIGURED");
        var options=new BusinessApiInvoker.Options(Math.min(selected.options().connectTimeoutMs(),test.timeoutMs()),test.timeoutMs(),selected.options().maxResponseBytes(),selected.options().apiKeyHeader());
        long start=System.nanoTime();
        try {
            var response=loaded.invoke("target-system-test",keys.getFirst(),request.connectionId(),options,new BusinessApiInvoker.Request(test.method(),test.path(),Map.of(),Map.of(),null));
            boolean success=test.successStatuses().contains(response.status());
            return result(start,success?"SUCCESS":"FAILURE",success?"EXPECTED_STATUS":"HTTP_STATUS");
        } catch(BusinessApiInvoker.Failure failure){
            return result(start,"TIMEOUT".equals(failure.code())?"TIMEOUT":"FAILURE",failure.code());
        } catch(ConnectionRegistry.Rejected failure){return result(start,"UNAVAILABLE","CONNECTION_UNAVAILABLE");}
    }
    private Result result(long start,String status,String reason){return new Result(clock.instant(),loaded.settingsId(),status,(System.nanoTime()-start)/1_000_000,reason);}
}
