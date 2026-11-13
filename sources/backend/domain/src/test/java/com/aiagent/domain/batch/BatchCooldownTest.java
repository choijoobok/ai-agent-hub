package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.run.BatchHeartbeat;
import com.aiagent.domain.batch.run.BatchHistoryWriter;
import com.aiagent.domain.batch.run.BatchRunner;
import com.aiagent.domain.batch.schedule.CronRules;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.domain.batch.run.BatchRowLock;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 쿨다운의 기준 — backend/persistence.md · screen/admin/system/batch/schedule/requirements.md. <b>건너뜀은 기준이 아니다.</b>
 *
 * <p>이 파일이 지키는 것은 한 문장이다: <b>쿨다운이 자기 자신을 밀어서는 안 된다.</b>
 * 건너뜀 행을 기준으로 삼으면 {@code SKIP_COOLDOWN} 이 <i>자기 다음 판정의 기준</i>이
 * 되어 쿨다운이 주기마다 앞으로 밀리고, 그 배치는 <b>다시는 돌지 않는다</b> — 화면에는
 * 매 주기 건너뜀만 쌓여 <b>쿨다운이 일하고 있는 것처럼</b> 보인다.
 *
 * <p>기준을 고르는 것은 {@code SysBatchHistoryRepository#findLastAttemptStart} 의 조건절이며,
 * 그 조건절 자체는 DB 없이 확인할 수 없다. 여기서 확인하는 것은 <b>러너가 그 메서드를
 * 쓰는가</b>와 <b>그 답을 어떻게 읽는가</b>다.
 */
class BatchCooldownTest {

    private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");
    private static final String BATCH_ID = "SETTLE_DAILY";

    private SysBatchHistoryRepository histories;
    private BatchHistoryWriter writer;
    private BatchLeaseStore locks;
    private BatchRunner runner;
    private BatchRowLock rows;

    @BeforeEach
    void setUp() {
        rows = mock(BatchRowLock.class);
        histories = mock(SysBatchHistoryRepository.class);
        writer = mock(BatchHistoryWriter.class);
        locks = mock(BatchLeaseStore.class);
        when(writer.now()).thenReturn(NOW);

        runner = new BatchRunner(
                histories,
                writer,
                new BatchJobRegistry(Map.of()),
                locks,
                mock(BatchHeartbeat.class),
                mock(ConfigLookup.class),
                mock(Executor.class), rows);
    }

    /** 5분 주기에 쿨다운 300초 — 저장이 막는 조합이지만 시드·직접 UPDATE 는 지나온다. */
    private static SysScheduleBatch batch(int cooldownSec) {
        return SysScheduleBatch.created(
                BATCH_ID, "야간 정산", "설명", "settleDailyJob",
                "0 */5 * * * *", "Asia/Seoul", null, 300, cooldownSec, true, 0);
    }

    @Test
    @DisplayName("마지막 실행이 쿨다운 안에 있으면 건너뛴다 — 락은 건드리지도 않는다")
    void aRecentRunBlocksTheNextCycle() {
        when(histories.findLastAttemptStart(BATCH_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(100)));

        scheduled(batch(300));

        verify(writer).skip(eq(BATCH_ID), any(), eq(BatchResultCode.SKIP_COOLDOWN), anyString());
        /*
          <b>순서가 판정이다</b>(screen/admin/system/batch/schedule/requirements.md). 락을 먼저 잡아 보면 실패한 이유를 저장소가
          가르지 못해 쿨다운에 막힌 것이 전부 {@code SKIP_LOCK} 으로 적힌다 — 결함이
          정상으로 기록된다.
        */
        verify(locks, never()).tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("기준이 없으면 쿨다운도 없다 — 건너뜀만 쌓인 배치가 여기 온다")
    void noBaselineMeansNoCooldown() {
        /*
          <b>이 빈 답이 톱니바퀴를 푼다.</b> 건너뜀 행을 포함했다면 방금 만든
          {@code SKIP_COOLDOWN} 이 기준이 되어 다음 주기도, 그다음 주기도 막혔다.
          제외하면 <i>실제로 돈 적이 없다</i> 가 되어 <b>돈다</b>.
        */
        when(histories.findLastAttemptStart(BATCH_ID)).thenReturn(Optional.empty());
        when(locks.tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        scheduled(batch(300));

        verify(writer, never())
                .skip(anyString(), any(), eq(BatchResultCode.SKIP_COOLDOWN), anyString());
        verify(writer).skip(eq(BATCH_ID), any(), eq(BatchResultCode.SKIP_LOCK), anyString());
    }

    @Test
    @DisplayName("쿨다운이 지났으면 돈다 — 경계에서 정확히 간격만큼 벌어진다")
    void anOldEnoughRunDoesNotBlock() {
        when(histories.findLastAttemptStart(BATCH_ID))
                .thenReturn(Optional.of(NOW.minusSeconds(300)));
        when(locks.tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        scheduled(batch(300));

        // 정확히 300초 전은 아직 지난 것이다 — 아니면 매 경계에서 한 주기가 새어 나간다.
        verify(writer, never())
                .skip(anyString(), any(), eq(BatchResultCode.SKIP_COOLDOWN), anyString());
    }

    @Test
    @DisplayName("쿨다운이 0이면 이력을 읽지도 않는다 — 대부분의 등록이 이쪽이다")
    void noCooldownSkipsTheQuery() {
        when(locks.tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        scheduled(batch(0));

        verify(histories, never()).findLastAttemptStart(anyString());
    }

    @Test
    @DisplayName("쿨다운이 크론 주기 이상이면 어긋난 것이다 — 저장과 기동이 같은 판정을 쓴다")
    void theSamePredicateGuardsSaveAndStartup() {
        // 5분 주기(300초)다.
        assertThat(CronRules.cooldownSwallowsCycles("0 */5 * * * *", 600)).isTrue();
        // 경계도 어긋난 것이다 — 같으면 한 주기가 통째로 누락된다.
        assertThat(CronRules.cooldownSwallowsCycles("0 */5 * * * *", 300)).isTrue();
        assertThat(CronRules.cooldownSwallowsCycles("0 */5 * * * *", 299)).isFalse();
        // 0 은 쿨다운을 쓰지 않는다는 뜻이다.
        assertThat(CronRules.cooldownSwallowsCycles("0 */5 * * * *", 0)).isFalse();
        /*
          <b>모르는 것과 어긋난 것은 다르다.</b> 못 읽는 크론을 어긋났다고 읽으면 저장이
          정상 크론을 거부하고, 기동이 멀쩡한 등록에 경고를 낸다.
        */
        assertThat(CronRules.cooldownSwallowsCycles("매일 두시", 600)).isFalse();
    }
    private void scheduled(SysScheduleBatch batch) {
        when(rows.schedule(BATCH_ID)).thenReturn(Optional.of(batch));
        runner.runScheduled(batch);
    }

}
