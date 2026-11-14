// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import com.aiagent.support.deployment.TargetConnections;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 조회 요청과 분리된 신뢰 동기화 작업. 운영 허용을 관찰한 기동만 자기 정의를 확정한다. */
@Component
public final class TargetSystemLifecycle implements DisposableBean {
    private static final Logger log=LoggerFactory.getLogger(TargetSystemLifecycle.class);
    private final TargetConnections loaded;private final TargetDeploymentService deployment;private final TargetSystemSyncService sync;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"target-configuration");t.setDaemon(true);return t;});
    private boolean reported;
    public TargetSystemLifecycle(TargetConnections loaded,TargetDeploymentService deployment,TargetSystemSyncService sync){this.loaded=loaded;this.deployment=deployment;this.sync=sync;}
    @EventListener(ApplicationReadyEvent.class) public void start(){deployment.stage(loaded);scheduler.scheduleWithFixedDelay(this::check,1,1,TimeUnit.SECONDS);}
    private void check(){
        try {if(deployment.allowed(loaded)&&!deployment.ready(loaded))sync.synchronize(loaded);reported=false;}
        catch(RuntimeException failure){if(!reported){log.error("대상 연결 동기화에 실패하여 이 기동의 준비를 열지 않았다. 배포 허용과 저장 상태를 점검해야 한다");reported=true;}}
    }
    public void destroy(){scheduler.shutdownNow();}
}
