// @feat FEAT-508
package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.run.BatchHeartbeat;
import com.aiagent.domain.batch.run.BatchHistoryWriter;
import com.aiagent.domain.batch.run.BatchRunner;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.domain.batch.run.BatchRowLock;
import static org.mockito.ArgumentMatchers.anyBoolean;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class BatchRunnerTest {

    private static final String BATCH_ID = "SETTLE_DAILY";
    private static final Instant STARTED_AT = Instant.parse("2026-09-12T00:00:00Z");

    private SysBatchHistoryRepository histories;
    private BatchHistoryWriter writer;
    private BatchLeaseStore locks;
    private BatchHeartbeat heartbeat;
    private BatchHeartbeat.Beat beat;
    private ConfigLookup configs;
    private BatchRunner runner;
    private BatchRowLock rows;

    @BeforeEach
    void setUp() {
        rows = mock(BatchRowLock.class);
        histories = mock(SysBatchHistoryRepository.class);
        writer = mock(BatchHistoryWriter.class);
        locks = mock(BatchLeaseStore.class);
        heartbeat = mock(BatchHeartbeat.class);
        beat = mock(BatchHeartbeat.Beat.class);
        configs = mock(ConfigLookup.class);

        when(writer.now()).thenReturn(STARTED_AT);
        when(writer.start(BATCH_ID, BatchExecType.MANUAL)).thenReturn(42L);
        when(writer.start(BATCH_ID, BatchExecType.SCHEDULE)).thenReturn(43L);
        when(heartbeat.start(anyString(), anyString(), anyLong(), anyInt())).thenReturn(beat);
        when(locks.tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean())).thenReturn(true);
        when(histories.findByBatchIdAndRsltCd(BATCH_ID, BatchResultCode.RUNNING)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer((Answer<Void>) invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(configs).inNewSnapshot(any(Runnable.class));

        BatchJob job = context -> BatchJobResult.of("정산 완료", "처리 대상 1건");
        runner = runnerFor(job);
    }

    private BatchRunner runnerFor(BatchJob job) {
        return runnerFor(job, (Executor) Runnable::run);
    }

    private BatchRunner runnerFor(BatchJob job, Executor executor) {
        return new BatchRunner(
                histories,
                writer,
                new BatchJobRegistry(Map.of("settleDailyJob", job)),
                locks,
                heartbeat,
                configs,
                executor, rows);
    }

    private static SysScheduleBatch batch() {
        return SysScheduleBatch.created(
                BATCH_ID, "야간 정산", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);
    }

    @Test
    @DisplayName("TC-5081 즉시 실행은 RUNNING을 먼저 남기고 성공 결과와 종료 처리를 확정한다")
    void manualRunStartsAndFinishesSuccessfully() {
        BatchRunner.RunOutcome outcome = manual(batch());

        assertThat(outcome.started()).isTrue();
        verify(writer).start(BATCH_ID, BatchExecType.MANUAL);
        verify(writer).finish(42L, BatchResultCode.SUCCESS, "정산 완료", "처리 대상 1건", null);
        verify(beat).close();
        verify(locks).release(BATCH_ID, 42L);
    }

    @Test
    @DisplayName("UC-508 같은 배치가 RUNNING이면 즉시 실행을 새로 시작하지 않고 현재 실행을 돌려준다")
    void manualRunRejectsAlreadyRunningBatch() {
        SysBatchHistory running = SysBatchHistory.started(
                BATCH_ID, BatchExecType.MANUAL, "app-02", STARTED_AT);
        when(histories.findByBatchIdAndRsltCd(BATCH_ID, BatchResultCode.RUNNING)).thenReturn(List.of(running));

        BatchRunner.RunOutcome outcome = manual(batch());

        assertThat(outcome).isEqualTo(new BatchRunner.RunOutcome(false, "app-02", STARTED_AT));
        verify(writer, never()).start(anyString(), any());
        verify(locks, never()).tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean());
        verify(writer, never()).finish(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-5083 중단을 읽고 끝난 실행만 CANCELED로 확정한다")
    void manualRunFinishesCanceledOnlyWhenJobReportsCancellation() {
        runner = runnerFor(context -> BatchJobResult.canceled("300건 처리 후 중단"));

        manual(batch());

        verify(writer).finish(42L, BatchResultCode.CANCELED, "300건 처리 후 중단", null, null);
    }

    @Test
    @DisplayName("TC-5083 중단 요청이 있어도 작업이 정상 완료하면 SUCCESS를 보존한다")
    void cancelRequestDoesNotRelabelNormalCompletion() {
        when(writer.isCancelRequested(42L)).thenReturn(true);
        runner = runnerFor(context -> {
            assertThat(context.isCancelRequested()).isTrue();
            return BatchJobResult.of("이미 완료", "중단 요청 전에 모든 처리가 끝남");
        });

        manual(batch());

        verify(writer).finish(42L, BatchResultCode.SUCCESS, "이미 완료", "중단 요청 전에 모든 처리가 끝남", null);
    }

    @Test
    @DisplayName("TC-5081 큐가 거부하면 RUNNING을 FAIL로 끝내고 heartbeat와 락을 함께 정리한다")
    void queueRejectionFinishesTheStartedHistoryAndReleasesTheLock() {
        RuntimeException queueFull = new IllegalStateException("실행 풀이 가득 찼다");
        runner = runnerFor(context -> BatchJobResult.of("사용되지 않음", null), runnable -> {
            throw queueFull;
        });

        assertThat(manual(batch()).started()).isTrue();

        verify(beat).close();
        verify(writer)
                .finish(
                        eq(42L),
                        eq(BatchResultCode.FAIL),
                        eq("실행 큐에 넣지 못했다"),
                        eq(null),
                        contains("IllegalStateException: 실행 풀이 가득 찼다"));
        verify(locks).release(BATCH_ID, 42L);
    }

    @Test
    @DisplayName("UC-508 수동 실행은 등록 잠금 뒤 RUNNING 부재에서만 쿨다운을 우회한다")
    void manualRunReacquiresOnlyTheCooldownLock() {
        assertThat(manual(batch()).started()).isTrue();
        verify(locks).tryAcquire(eq(BATCH_ID), eq(42L), any(), any(), eq(true));
    }

    @Test
    @DisplayName("UC-508 최신 RUNNING이 있으면 새 실행 lease와 이력을 만들지 않는다")
    void manualRunDoesNotReleaseALockWhenAnotherNodeStartedFirst() {
        SysBatchHistory raced = SysBatchHistory.started(BATCH_ID, BatchExecType.SCHEDULE, "app-03", STARTED_AT);
        when(histories.findByBatchIdAndRsltCd(BATCH_ID, BatchResultCode.RUNNING)).thenReturn(List.of(raced));
        assertThat(manual(batch())).isEqualTo(new BatchRunner.RunOutcome(false, "app-03", STARTED_AT));
        verify(locks, never()).tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean());
        verify(writer, never()).start(anyString(), any());
    }

    @Test
    @DisplayName("TC-5083 스케줄 쿨다운은 락 충돌이 아니라 SKIP_COOLDOWN으로 기록한다")
    void scheduledRunRecordsCooldownBeforeTryingTheLock() {
        SysScheduleBatch cooldownBatch = batch(60);
        when(histories.findLastAttemptStart(BATCH_ID)).thenReturn(Optional.of(STARTED_AT.minusSeconds(1)));

        scheduled(cooldownBatch);

        verify(writer)
                .skip(
                        eq(BATCH_ID),
                        eq(BatchExecType.SCHEDULE),
                        eq(BatchResultCode.SKIP_COOLDOWN),
                        contains("쿨다운(60초)"));
        verify(locks, never()).tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("TC-5083 쿨다운이 아니고 다른 노드가 선점했으면 SKIP_LOCK으로 기록한다")
    void scheduledRunRecordsLockContentionSeparatelyFromCooldown() {
        when(locks.tryAcquire(anyString(), anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        scheduled(batch());

        verify(writer)
                .skip(
                        eq(BATCH_ID),
                        eq(BatchExecType.SCHEDULE),
                        eq(BatchResultCode.SKIP_LOCK),
                        contains("다른 노드"));
        verify(writer).discardStart(43L);
    }

    @Test
    @DisplayName("TC-5081 스케줄 실행도 시작 이력부터 남기고 성공과 락 해제로 끝낸다")
    void scheduledRunStartsAndFinishesSuccessfully() {
        scheduled(batch());

        verify(writer).start(BATCH_ID, BatchExecType.SCHEDULE);
        verify(writer).finish(43L, BatchResultCode.SUCCESS, "정산 완료", "처리 대상 1건", null);
        verify(locks).release(BATCH_ID, 43L);
    }

    @Test
    @DisplayName("TC-5083 등록 뒤 구현이 사라지면 SUCCESS 대신 FAIL로 남긴다")
    void missingRegisteredJobFinishesAsFail() {
        SysScheduleBatch missingJob = SysScheduleBatch.created(
                BATCH_ID, "야간 정산", "설명", "removedJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);

        manual(missingJob);

        verify(writer)
                .finish(
                        eq(42L),
                        eq(BatchResultCode.FAIL),
                        contains("등록된 BatchJob 구현이 없다"),
                        eq(null),
                        eq(null));
    }

    @Test
    @DisplayName("TC-5083 실행 예외는 FAIL 이력과 오류 전문을 남기고 락을 해제한다")
    void jobExceptionFinishesAsFailAndReleasesTheLock() {
        runner = runnerFor(context -> {
            throw new IllegalStateException("정산 원장 연결 실패");
        });

        manual(batch());

        verify(writer)
                .finish(
                        eq(42L),
                        eq(BatchResultCode.FAIL),
                        eq("IllegalStateException: 정산 원장 연결 실패"),
                        eq(null),
                        contains("IllegalStateException: 정산 원장 연결 실패"));
        verify(beat).close();
        verify(locks).release(BATCH_ID, 42L);
    }

    private static SysScheduleBatch batch(int lockMinIntervalSec) {
        return SysScheduleBatch.created(
                BATCH_ID,
                "야간 정산",
                "설명",
                "settleDailyJob",
                "0 0 2 * * *",
                "Asia/Seoul",
                null,
                300,
                lockMinIntervalSec,
                true,
                0);
    }
    @Test
    @DisplayName("TC-5088 수동 접수는 커밋 전에 heartbeat나 큐를 시작하지 않는다")
    void handoffWaitsForAdmissionCommit() {
        Executor queue = mock(Executor.class);
        runner = runnerFor(context -> BatchJobResult.of("완료", null), queue);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(rows.schedule(BATCH_ID)).thenReturn(Optional.of(batch()));
            assertThat(runner.runNow(batch()).started()).isTrue();
            verify(queue, never()).execute(any());
            verify(heartbeat, never()).start(anyString(), anyString(), anyLong(), anyInt());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(queue).execute(any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    private BatchRunner.RunOutcome manual(SysScheduleBatch batch) {
        when(rows.schedule(BATCH_ID)).thenReturn(Optional.of(batch));
        return committed(() -> runner.runNow(batch));
    }

    private void scheduled(SysScheduleBatch batch) {
        when(rows.schedule(BATCH_ID)).thenReturn(Optional.of(batch));
        committed(() -> { runner.runScheduled(batch); return null; });
    }

    private <T> T committed(java.util.function.Supplier<T> action) {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            T value = action.get();
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            return value;
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
    }

}
