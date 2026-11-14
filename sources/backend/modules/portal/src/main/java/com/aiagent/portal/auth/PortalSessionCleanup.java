// @feat FEAT-301
package com.aiagent.portal.auth;

import javax.sql.DataSource;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 만료 판정과 독립된 내부 청소. 사건 원장과 상대 영역은 삭제하지 않는다. */
@Component
@ConditionalOnProperty(name="app.auth.session.domains.portal.enabled",havingValue="true")
public final class PortalSessionCleanup implements AutoCloseable {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"portal-session-cleanup");thread.setDaemon(true);return thread;});
    public PortalSessionCleanup(DataSource source,PlatformTransactionManager manager){jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(manager);}
    @EventListener(ApplicationReadyEvent.class) public void ready(){scheduler.scheduleWithFixedDelay(this::quiet,60,60,TimeUnit.SECONDS);}
    private void quiet(){try{sweep();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Portal 만료 정리를 완료하지 못했다 type={}",failure.getClass().getSimpleName());}}
    public int sweep() {
        int count=remove("AI_PORTAL_SESSION","EXPIRY_TIME <= floor(extract(epoch FROM clock_timestamp())*1000)");
        count+=remove("AI_PORTAL_LOGIN_CHALLENGE","EXPIRES_DTM <= clock_timestamp()");
        count+=remove("AI_PORTAL_AUTH_EVIDENCE","EXPIRES_DTM <= clock_timestamp()");return count;
    }
    private int remove(String table,String expired){return tx.execute(status->jdbc.update("DELETE FROM "+table+" WHERE ctid IN (SELECT ctid FROM "+table+" WHERE "+expired+" LIMIT 1000 FOR UPDATE SKIP LOCKED)"));}
    @Override public void close(){scheduler.shutdownNow();}
}
