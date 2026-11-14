// @feat FEAT-001 FEAT-002
package com.aiagent.admin.shared.configuration;
import com.aiagent.core.deployment.ConfigurationSnapshot;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** 조회 요청과 분리된 신뢰 동기화 작업. 운영 허용을 관찰한 기동만 자기 정의를 확정한다. */
public final class ConfigurationLifecycle implements DisposableBean {
    private static final Logger log=LoggerFactory.getLogger(ConfigurationLifecycle.class);
    private final ConfigurationSnapshot loaded;private final ConfigurationDeploymentService deployment;private final Runnable sync;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"target-configuration");t.setDaemon(true);return t;});
    private boolean reported;
    public ConfigurationLifecycle(ConfigurationSnapshot loaded,ConfigurationDeploymentService deployment,Runnable sync){this.loaded=loaded;this.deployment=deployment;this.sync=sync;}
    @EventListener(ApplicationReadyEvent.class) public void start(){deployment.stage(loaded);scheduler.scheduleWithFixedDelay(this::check,1,1,TimeUnit.SECONDS);}
    private void check(){
        try {if(deployment.allowed(loaded)&&!deployment.ready(loaded))sync.run();reported=false;}
        catch(RuntimeException failure){if(!reported){log.error("자산 설정 동기화에 실패하여 이 기동의 준비를 열지 않았다. 배포 허용과 저장 상태를 점검해야 한다");reported=true;}}
    }
    public void destroy(){scheduler.shutdownNow();}
}
