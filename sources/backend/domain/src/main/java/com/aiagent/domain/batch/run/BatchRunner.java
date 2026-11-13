// @feat FEAT-508
package com.aiagent.domain.batch.run;

import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.batch.BatchExecType;
import com.aiagent.domain.batch.BatchJob;
import com.aiagent.domain.batch.BatchJobContext;
import com.aiagent.domain.batch.BatchJobRegistry;
import com.aiagent.domain.batch.BatchJobResult;
import com.aiagent.domain.batch.BatchResultCode;
import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.schedule.BatchScheduleErrorCode;
import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.support.config.ConfigLookup;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ADMIN 보호 접수에 REQUIRED로 참여한다. 장기 작업은 확정 뒤 서버 소유 실행기로 인계한다. */
@Component
public class BatchRunner {
    public static final String LOCK_TYPE = "BATCH";
    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);
    private static final int STACK_MAX = 1_000_000;
    private final SysBatchHistoryRepository histories;
    private final BatchHistoryWriter writer;
    private final BatchJobRegistry registry;
    private final BatchLeaseStore locks;
    private final BatchHeartbeat heartbeat;
    private final ConfigLookup configs;
    private final Executor executor;
    private final BatchRowLock rows;

    public BatchRunner(SysBatchHistoryRepository histories, BatchHistoryWriter writer,
            BatchJobRegistry registry, BatchLeaseStore locks, BatchHeartbeat heartbeat,
            ConfigLookup configs, @Qualifier(BatchExecutorConfig.EXECUTOR) Executor executor,
            BatchRowLock rows) {
        this.histories = histories;
        this.writer = writer;
        this.registry = registry;
        this.locks = locks;
        this.heartbeat = heartbeat;
        this.configs = configs;
        this.executor = executor;
        this.rows = rows;
    }

    /** 전체 ID를 먼저 검증/정렬 잠금하고 같은 커밋으로 확정한다. 결과는 입력 순서다. */
    @Transactional
    public List<RunOutcome> runNow(List<String> ids) {
        Map<String, SysScheduleBatch> batches = new LinkedHashMap<>();
        ids.stream().distinct().sorted().forEach(id -> batches.put(id, rows.schedule(id)
                .orElseThrow(() -> new BusinessException(BatchScheduleErrorCode.NOT_FOUND, "없는 배치다 — " + id))));
        Map<String, RunOutcome> outcomes = new LinkedHashMap<>();
        batches.forEach((id, batch) -> outcomes.put(id, admit(batch, BatchExecType.MANUAL)));
        return ids.stream().map(outcomes::get).toList();
    }

    @Transactional
    public RunOutcome runNow(SysScheduleBatch batch) {
        return runNow(List.of(batch.getBatchId())).getFirst();
    }

    /** 예약은 관리자 문맥 없이 자기 접수 트랜잭션을 연다. 낡은 스케줄 객체를 실행하지 않는다. */
    @Transactional
    public void runScheduled(SysScheduleBatch snapshot) {
        rows.schedule(snapshot.getBatchId()).filter(batch -> Boolean.TRUE.equals(batch.getUseYn()))
                .ifPresent(batch -> admit(batch, BatchExecType.SCHEDULE));
    }

    private RunOutcome admit(SysScheduleBatch batch, BatchExecType type) {
        Optional<SysBatchHistory> running = runningOf(batch.getBatchId());
        if (type == BatchExecType.SCHEDULE) {
            Instant until = cooldownUntil(batch);
            if (until != null) {
                writer.skip(batch.getBatchId(), type, BatchResultCode.SKIP_COOLDOWN,
                        "쿨다운(" + batch.getLockMinIntervalSec() + "초)에 막혔다 — " + until + " 이후에 다시 시도한다");
                return RunOutcome.ofAlreadyRunning(null);
            }
        }
        if (running.isPresent()) {
            if (type == BatchExecType.SCHEDULE) skipLock(batch);
            return RunOutcome.ofAlreadyRunning(running.get());
        }
        Long id = writer.start(batch.getBatchId(), type);
        Instant now = writer.now();
        Instant cooldown = batch.getLockMinIntervalSec() > 0 ? now.plusSeconds(batch.getLockMinIntervalSec()) : null;
        if (!locks.tryAcquire(batch.getBatchId(), id, now.plusSeconds(batch.getLockExpirySec()), cooldown,
                type == BatchExecType.MANUAL)) {
            writer.discardStart(id);
            if (type == BatchExecType.SCHEDULE) skipLock(batch);
            return RunOutcome.ofAlreadyRunning(null);
        }
        // lease와 RUNNING은 같은 DB 트랜잭션이다. 롤백이면 함께 사라지고 메모리 작업은 없다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { handoff(batch, id); }
        });
        return RunOutcome.ofStarted();
    }

    private void skipLock(SysScheduleBatch batch) {
        writer.skip(batch.getBatchId(), BatchExecType.SCHEDULE, BatchResultCode.SKIP_LOCK,
                "다른 노드가 실행 중이다 — 다중화에서는 정상이다");
    }

    private void handoff(SysScheduleBatch batch, Long id) {
        BatchHeartbeat.Beat beat = null;
        try {
            beat = heartbeat.start(LOCK_TYPE, batch.getBatchId(), id, batch.getLockExpirySec());
            BatchHeartbeat.Beat active = beat;
            executor.execute(() -> execute(batch, id, active));
        } catch (RuntimeException failure) {
            if (beat != null) beat.close();
            // @swallow 확정 접수의 인계 실패는 이력에 남기며 다른 확정 접수의 인계를 막지 않는다.
            try {
                writer.finish(id, BatchResultCode.FAIL, "실행 큐에 넣지 못했다", null, stackOf(failure));
            } catch (RuntimeException recordFailure) {
                log.error("배치 인계 실패를 기록하지 못했다 — histId={}", id, recordFailure);
            } finally {
                releaseQuietly(batch.getBatchId(), id);
            }
        }
    }

    private void execute(SysScheduleBatch batch, Long id, BatchHeartbeat.Beat beat) {
        try {
            configs.inNewSnapshot(() -> runJob(batch, id));
        } catch (RuntimeException failure) {
            log.error("배치 실행 준비가 실패했다 — histId={}", id, failure);
            writer.finish(id, BatchResultCode.FAIL, oneLineOf(failure), null, stackOf(failure));
        } finally {
            beat.close();
            releaseQuietly(batch.getBatchId(), id);
        }
    }

    private void releaseQuietly(String batchId, Long id) {
        // @swallow DB 해제 장애는 만료 안전망으로 복구하며 다른 확정 접수는 계속 인계한다.
        try { locks.release(batchId, id); }
        catch (RuntimeException failure) { log.error("배치 lease를 해제하지 못했다 — histId={}", id, failure); }
    }

    /** 실행 그 자체 — 이력을 적는 것까지가 한 덩어리다. */
    private void runJob(SysScheduleBatch batch, Long histId) {
        try {
            Optional<BatchJob> job = registry.find(batch.getBeanId());
            if (job.isEmpty()) {
                /*
                  저장이 막았으므로 여기 오는 경우는 <i>등록 뒤에 구현이 사라진 배포</i>
                  뿐이다. 실제로 일어난다 — 리팩터링으로 빈 이름이 바뀐다. 조용히
                  건너뛰면 "안 도는데 이유가 없는" 상태가 되므로 FAIL 로 남긴다.
                */
                writer.finish(
                        histId,
                        BatchResultCode.FAIL,
                        "등록된 BatchJob 구현이 없다 — " + batch.getBeanId(),
                        null,
                        null);
                return;
            }

            BatchJobResult result = BatchJobResult.orEmpty(job.get()
                    .run(new BatchJobContext(
                            batch.getBatchId(),
                            batch.getParamJson(),
                            histId,
                            () -> writer.isCancelRequested(histId))));

            writer.finish(
                    histId,
                    result.canceled() ? BatchResultCode.CANCELED : BatchResultCode.SUCCESS,
                    result.summary(),
                    result.detail(),
                    null);

        } catch (Exception e) {
            /*
              Throwable 이 아니라 Exception 이다 — OOM·StackOverflow 는 이 스레드가 이력을
              적을 수 있다는 보장이 없고, 적으려다 두 번째 오류가 나면 첫 번째가 로그에서도
              사라진다. 그런 죽음은 유령 행으로 드러난다(screen/admin/system/batch/schedule/requirements.md).
            */
            log.error("배치 실행이 실패했다 — batchId={} histId={}", batch.getBatchId(), histId, e);
            writer.finish(histId, BatchResultCode.FAIL, oneLineOf(e), null, stackOf(e));
        }
    }

    /**
     * 쿨다운이 남았으면 그 종료 시각 — 아니면 {@code null}.
     *
     * <p><b>락이 아니라 이력을 본다.</b> 락 저장소는 실패 이유를 가르지 않으므로
     * ({@code tryAcquire} 가 {@code false} 하나다) 여기서 미리 갈라야
     * {@code SKIP_COOLDOWN} 과 {@code SKIP_LOCK} 이 서로 다른 코드로 남는다 — 하나는
     * 결함이고 하나는 정상이라 <b>같은 코드에 두면 화면이 색을 줄 수 없다</b>(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p>기준은 <b>마지막으로 실제로 돈 실행의 시작 시각</b>이다. 종료 시각으로 재면 오래
     * 걸린 배치가 자기 소요만큼 쿨다운을 더 갖게 되고, backend/persistence.md 가 정의한 것은 <i>재실행
     * 간격</i>이다.
     *
     * <p><b>건너뜀 행은 기준이 아니다</b>(2026-08-31 에 뒤집었다). 넣으면 건너뜀이 자기
     * 다음 판정의 기준이 되어 쿨다운이 주기마다 앞으로 밀리고, 그 배치는 <b>다시는 돌지
     * 않는다</b> — 근거는 {@code SysBatchHistoryRepository#findLastAttemptStart} 에 있다.
     */
    private Instant cooldownUntil(SysScheduleBatch batch) {
        if (batch.getLockMinIntervalSec() <= 0) {
            return null;
        }
        Duration interval = Duration.ofSeconds(batch.getLockMinIntervalSec());
        Instant threshold = writer.now().minus(interval);
        return histories
                .findLastAttemptStart(batch.getBatchId())
                .filter(startedAt -> startedAt.isAfter(threshold))
                .map(startedAt -> startedAt.plus(interval))
                .orElse(null);
    }

    private Optional<SysBatchHistory> runningOf(String batchId) {
        List<SysBatchHistory> running =
                histories.findByBatchIdAndRsltCd(batchId, BatchResultCode.RUNNING);
        return running.isEmpty() ? Optional.empty() : Optional.of(running.get(0));
    }

    /** {@code RSLT_MSG} 에 실을 한 줄 — 목록 컬럼이라 스택을 넣지 않는다(sys/batch.md). */
    private static String oneLineOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }

    private static String stackOf(Exception e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        String stack = out.toString();
        return stack.length() <= STACK_MAX ? stack : stack.substring(0, STACK_MAX);
    }

    /**
     * 즉시 실행의 결과 — <b>시작했는가, 아니면 왜 안 했는가</b>.
     *
     * <p>예외로 표현하지 않는 것은 screen/admin/system/batch/schedule/requirements.md 의 응답이 <b>건별</b>이기 때문이다. 여러 건을
     * 골라 누르면 어떤 것은 시작하고 어떤 것은 실행 중이며, 예외를 던지면 <b>첫 실패에서
     * 나머지가 멈춘다</b>.
     *
     * @param started 시작했는가
     * @param runningNodeId 이미 도는 노드 — {@code null} 이면 알 수 없다(락만 잡혀 있었다)
     * @param runningStartedAt 그 실행의 시작 시각
     */
    public record RunOutcome(boolean started, String runningNodeId, Instant runningStartedAt) {

        /**
         * <b>이름이 {@code started()} 가 아닌 것은 컴파일러가 정한다.</b> 레코드 컴포넌트와
         * 같은 이름의 메서드는 접근자 자리라 {@code static} 일 수 없다 — 팩토리가 비켜난다.
         */
        static RunOutcome ofStarted() {
            return new RunOutcome(true, null, null);
        }

        static RunOutcome ofAlreadyRunning(SysBatchHistory running) {
            return running == null
                    ? new RunOutcome(false, null, null)
                    : new RunOutcome(false, running.getNodeId(), running.getStartDtm());
        }
    }
}
