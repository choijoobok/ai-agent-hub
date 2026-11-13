package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.run.BatchGhostSweeper;
import com.aiagent.domain.batch.run.BatchHistoryWriter;
import com.aiagent.domain.batch.run.BatchRunner;
import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.domain.batch.repository.SysScheduleBatchRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 유령 정리 — screen/admin/system/batch/schedule/requirements.md. <b>판정의 증거가 시각이 아니라 락이다.</b>
 *
 * <p>이 테스트가 지키는 것은 하나다: <b>오래 도는 배치를 죽었다고 부르지 않는다.</b>
 * 그것이 예전 화면 배지({@code START_DTM + LOCK_EXPIRY_SEC})가 틀린 이유이며,
 * 하트비트가 생기고 나서야 <i>락이 있다 = 프로세스가 있다</i> 가 참이 됐다.
 */
class BatchGhostSweeperTest {

    private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");
    private static final String LIVE_NODE = "app-01#a1b2c3d4";
    private static final String DEAD_NODE = "app-03#c9d0e1f2";

    private BatchHistoryWriter writer;
    private BatchLeaseStore locks;
    private BatchGhostSweeper sweeper;

    @BeforeEach
    void setUp() {
        writer = mock(BatchHistoryWriter.class);
        locks = mock(BatchLeaseStore.class);
        var batches = mock(SysScheduleBatchRepository.class);
        when(batches.existsById(anyString())).thenReturn(true);
        sweeper = new BatchGhostSweeper(writer, locks, batches);
        when(writer.markLost(anyLong(), anyString())).thenReturn(true);
    }

    private static BatchHistoryWriter.RunningRow row(long histId, String nodeId, Instant startedAt) {
        return new BatchHistoryWriter.RunningRow(histId, "RETENTION_PURGE", nodeId, startedAt);
    }

    @Test
    @DisplayName("락이 살아 있으면 건드리지 않는다 — 사흘째 도는 배치도 정상이다")
    void aLongRunningBatchIsNotAGhost() {
        // 사흘 전에 시작했다. 옛 배지 규칙(시작 + 락 만료)이었다면 유령이라고 불렀을 행이다.
        when(writer.runningHistories())
                .thenReturn(List.of(row(1L, LIVE_NODE, NOW.minusSeconds(259_200))));
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(LIVE_NODE))).thenReturn(true);

        assertThat(sweeper.sweep()).isZero();
        verify(writer, never()).markLost(anyLong(), anyString());
    }

    @Test
    @DisplayName("락이 없으면 유실로 종결한다 — 30초 전에 시작했어도 그렇다")
    void aRunWhoseNodeVanishedIsSwept() {
        /*
          <b>시간이 판정에 들어가지 않는다.</b> 방금 시작한 실행이라도 그 프로세스가 없으면
          이력을 갱신할 주체가 없다 — 기다려 봐야 아무 일도 일어나지 않는다.
        */
        when(writer.runningHistories())
                .thenReturn(List.of(row(2L, DEAD_NODE, NOW.minusSeconds(30))));
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(DEAD_NODE))).thenReturn(false);

        assertThat(sweeper.sweep()).isEqualTo(1);
        verify(writer).markLost(anyLong(), anyString());
    }

    @Test
    @DisplayName("문구에 노드가 들어간다 — 다음에 볼 곳이 스택이 아니라 그 서버다")
    void theMessageNamesTheNode() {
        when(writer.runningHistories())
                .thenReturn(List.of(row(3L, DEAD_NODE, NOW.minusSeconds(600))));
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(DEAD_NODE))).thenReturn(false);

        sweeper.sweep();

        verify(writer).markLost(eq(3L), contains(DEAD_NODE));
    }

    @Test
    @DisplayName("그 사이 스스로 끝냈으면 세지 않는다 — 실제로 돈 결과가 판정보다 정확하다")
    void aRunThatFinishedMeanwhileIsNotCounted() {
        when(writer.runningHistories())
                .thenReturn(List.of(row(4L, DEAD_NODE, NOW.minusSeconds(600))));
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(DEAD_NODE))).thenReturn(false);
        // 목록을 읽고 락을 묻는 사이에 배치가 SUCCESS 로 끝냈다 — markLost 가 안 먹는다.
        when(writer.markLost(anyLong(), anyString())).thenReturn(false);

        assertThat(sweeper.sweep()).isZero();
    }

    @Test
    @DisplayName("여러 노드가 섞여 있으면 죽은 것만 고른다")
    void picksOnlyTheDeadOnes() {
        when(writer.runningHistories())
                .thenReturn(List.of(
                        row(5L, LIVE_NODE, NOW.minusSeconds(60)),
                        row(6L, DEAD_NODE, NOW.minusSeconds(60))));
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(LIVE_NODE))).thenReturn(true);
        when(locks.isHeldBy(eq("RETENTION_PURGE"), anyLong(), eq(DEAD_NODE))).thenReturn(false);

        assertThat(sweeper.sweep()).isEqualTo(1);
        verify(writer).markLost(anyLong(), anyString());
    }

    @Test
    @DisplayName("유실은 소요를 남기지 않는다 — 언제 죽었는지 모르기 때문이다")
    void lostRunsHaveNoElapsed() {
        SysBatchHistory history = SysBatchHistory.started(
                "RETENTION_PURGE", BatchExecType.SCHEDULE, DEAD_NODE, NOW.minusSeconds(259_200));

        assertThat(history.markLost(NOW, "노드가 사라졌다")).isTrue();

        assertThat(history.getRsltCd()).isEqualTo(BatchResultCode.LOST);
        assertThat(history.getEndDtm()).isEqualTo(NOW);
        /*
          판정 시각까지를 소요로 적으면 <b>사흘 걸린 배치</b>가 되어 소요 정렬을 통째로
          망친다 — 모르는 것을 0 이나 큰 수로 적지 않는다(screen/admin/system/batch/schedule/requirements.md).
        */
        assertThat(history.getElapsedMs()).isNull();
        assertThat(history.getRsltCd().isFinished()).isTrue();
    }

    @Test
    @DisplayName("이미 끝난 행에는 먹지 않는다 — 성공한 실행을 유실로 덮으면 안 된다")
    void markLostOnlyAppliesToRunningRows() {
        SysBatchHistory history = SysBatchHistory.started(
                "RETENTION_PURGE", BatchExecType.SCHEDULE, LIVE_NODE, NOW.minusSeconds(60));
        history.finish(BatchResultCode.SUCCESS, NOW, "다 지웠다", null, null);

        assertThat(history.markLost(NOW, "노드가 사라졌다")).isFalse();
        assertThat(history.getRsltCd()).isEqualTo(BatchResultCode.SUCCESS);
    }
}
