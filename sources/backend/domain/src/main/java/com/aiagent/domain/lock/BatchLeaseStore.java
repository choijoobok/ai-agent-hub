// @feat FEAT-508
package com.aiagent.domain.lock;

import com.aiagent.support.lock.NodeId;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 배치 전용 lease. 일반 CustomLock의 전파/해제 계약과 분리하고 HIST_ID 동등성만 비교한다. */
@Component
public class BatchLeaseStore {
    private final JdbcTemplate jdbc;
    private final NodeId node;
    private final Clock clock;

    public BatchLeaseStore(JdbcTemplate jdbc, NodeId node, Clock clock) {
        this.jdbc = jdbc;
        this.node = node;
        this.clock = clock;
    }

    /** 호출자는 등록 행 잠금 뒤 최신 RUNNING 부재를 확인한다. 접수 롤백은 lease도 롤백한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(String key, Long histId, Instant expiresAt, Instant releasableAt, boolean manual) {
        Instant now = clock.instant();
        return jdbc.update("""
                insert into SYS_DATA_LOCK (LOCK_TYPE,LOCK_KEY,EXPIRE_DTM,RELEASE_DTM,NODE_ID,CRE_DTM,BATCH_HIST_ID)
                values ('BATCH',?,?,?,?,?,?)
                on conflict (LOCK_TYPE,LOCK_KEY) do update set
                    EXPIRE_DTM=excluded.EXPIRE_DTM, RELEASE_DTM=excluded.RELEASE_DTM,
                    NODE_ID=excluded.NODE_ID, CRE_DTM=excluded.CRE_DTM,
                    USER_ID=null, BATCH_HIST_ID=excluded.BATCH_HIST_ID
                where ? or SYS_DATA_LOCK.EXPIRE_DTM <= ?
                """, key, utc(expiresAt), utc(releasableAt), node.value(), utc(now), histId, manual, utc(now)) == 1;
    }

    /** 쿨다운만 남기면 소유 token을 비워 늦은 heartbeat/release를 무효화한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key, Long histId) {
        int held = jdbc.update("""
                update SYS_DATA_LOCK set EXPIRE_DTM=RELEASE_DTM, BATCH_HIST_ID=null
                where LOCK_TYPE='BATCH' and LOCK_KEY=? and BATCH_HIST_ID=? and RELEASE_DTM>?
                """, key, histId, utc(clock.instant()));
        if (held == 0) jdbc.update("delete from SYS_DATA_LOCK where LOCK_TYPE='BATCH' and LOCK_KEY=? and BATCH_HIST_ID=?", key, histId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean heartbeat(String key, Long histId, Instant expiresAt) {
        return jdbc.update("""
                update SYS_DATA_LOCK set EXPIRE_DTM=?
                where LOCK_TYPE='BATCH' and LOCK_KEY=? and BATCH_HIST_ID=? and NODE_ID=?
                  and EXPIRE_DTM>?
                """, utc(expiresAt), key, histId, node.value(), utc(clock.instant())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isHeldBy(String key, Long histId, String owner) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from SYS_DATA_LOCK where LOCK_TYPE='BATCH' and LOCK_KEY=?
                    and BATCH_HIST_ID=? and NODE_ID=? and EXPIRE_DTM>?)
                """, Boolean.class, key, histId, owner, utc(clock.instant())));
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
