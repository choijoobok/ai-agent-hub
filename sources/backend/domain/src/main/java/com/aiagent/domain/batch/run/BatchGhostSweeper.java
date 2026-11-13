package com.aiagent.domain.batch.run;

import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.domain.batch.repository.SysScheduleBatchRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 유령 정리 — screen/admin/system/batch/schedule/requirements.md. <b>죽은 프로세스가 남긴 {@code RUNNING} 행을 종결시킨다.</b>
 *
 * <h2>유령은 보기 싫은 것이 아니라 <b>막는 것</b>이다</h2>
 *
 * <p>{@code RUNNING} 행은 실행 스레드가 갱신한다. 프로세스가 통째로 죽으면 갱신할 주체가
 * 없어 행이 영원히 그대로 남고, {@code BatchRunner#runNow} 가 그것을 <i>이미 실행 중</i>
 * 으로 읽는다 — <b>그 배치는 사람이 눌러도 다시 돌지 않는다.</b> sys/batch.md 이 이력 중 유일하게
 * 갱신되는 테이블을 만들면서 진 빚이며, <b>갚는 주체가 없었다</b>: 락 만료는 락만 회수하고
 * 이력에는 손대지 않는다.
 *
 * <h2>판정은 <b>락이 한다</b> — 시각으로 재지 않는다</h2>
 *
 * <p>처음 화면은 {@code START_DTM + LOCK_EXPIRY_SEC} 을 넘긴 {@code RUNNING} 을 유령 배지로
 * 그렸다. 그 임계값은 <b>오래 도는 정상 배치를 죽었다고 부른다</b> — 300초가 기본값이라
 * 5분을 넘기는 배치는 전부 유령이 된다.
 *
 * <p>{@link BatchHeartbeat} 가 서면서 정확한 증거가 생겼다. 살아 있는 노드는 자기 락의
 * 만료를 계속 밀어 주므로:
 *
 * <table><caption></caption>
 *   <tr><td><b>그 노드의 살아 있는 락이 있다</b></td><td>프로세스가 있다 — 얼마나 오래
 *       돌든 <b>정상</b>이다</td></tr>
 *   <tr><td><b>없다</b></td><td>프로세스가 없다 — 밀어 줄 것이 없어 만료됐다</td></tr>
 * </table>
 *
 * <p><b>{@code NODE_ID}와 정확한 {@code HIST_ID}까지 맞춰 묻는다.</b> 같은 프로세스의 새 실행도 구별한다. 키만 물으면 <i>그 배치가 지금
 * 다시 돌고 있는 경우</i> 새 실행의 락을 보고 옛 유령을 살아 있다고 판정한다. {@code NodeId}
 * 는 기동마다 달라지므로(같은 호스트를 재기동해도 다르다) 그 비교가 <b>프로세스 동일성</b>을
 * 정확히 가른다 — sys/batch.md 가 세 테이블에 같은 값을 쓰기로 한 것이 여기서 값을 한다.
 *
 * <h2>노드마다 돈다 — 락을 잡지 않는다</h2>
 *
 * <p>둘이 같은 행을 동시에 쓸어도 {@code markLost} 가 {@code RUNNING} 일 때만 먹으므로
 * 두 번째는 아무 일도 하지 않는다. <b>이 작업에 락을 씌우면</b> 락을 잃은 노드에서
 * 유령이 안 치워지고, 그 노드가 유일하게 살아 있는 노드일 수도 있다 — 청소가 청소를
 * 필요로 하게 된다.
 *
 * <h2>기동 직후에도 한 번 훑는다</h2>
 *
 * <p>노드가 죽는 가장 흔한 경로가 <b>재배포</b>다. 그때 남은 행들은 <b>이 프로세스의 옛
 * 기동</b>이 남긴 것이고, {@code NodeId} 가 달라졌으므로 여기서 정확히 걸린다.
 */
@Component
public class BatchGhostSweeper {

    private static final Logger log = LoggerFactory.getLogger(BatchGhostSweeper.class);

    /**
     * 훑는 주기.
     *
     * <p>유령이 <b>즉시 실행을 막고</b> 있으므로 늦으면 사람이 기다린다. 반대로 자주
     * 돌아도 비용은 {@code RUNNING} 행 수만큼의 작은 조회뿐이라(보통 0~3건) 1분이 싸다.
     */
    private static final Duration INTERVAL = Duration.ofMinutes(1);

    /** 기동 직후의 첫 훑기는 조금 늦춘다 — 크론 등록과 첫 실행이 자리를 잡고 나서다. */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(20);

    private final BatchHistoryWriter writer;
    private final BatchLeaseStore locks;
    private final SysScheduleBatchRepository batches;
    private final ScheduledExecutorService scheduler;

    public BatchGhostSweeper(BatchHistoryWriter writer, BatchLeaseStore locks, SysScheduleBatchRepository batches) {
        this.writer = writer;
        this.locks = locks;
        this.batches = batches;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "batch-sweeper");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * {@code @PostConstruct} 가 아니다 — {@link BatchScheduler#onReady} 와 같은 이유로
     * Liquibase 와 커넥션 풀이 선 뒤여야 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        scheduler.scheduleWithFixedDelay(
                this::sweepQuietly,
                INITIAL_DELAY.toSeconds(),
                INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * 한 번 훑는다 — <b>바꾼 건수</b>를 돌려준다.
     *
     * <p>공개인 것은 테스트가 부르기 때문이다. 운영에서 부르는 것은 스케줄 하나뿐이다.
     */
    public int sweep() {
        int swept = 0;
        for (BatchHistoryWriter.RunningRow row : writer.runningHistories()) {
            if (!batches.existsById(row.batchId()) || locks.isHeldBy(row.batchId(), row.histId(), row.nodeId())) {
                continue;
            }
            if (writer.markLost(row.histId(), messageFor(row))) {
                swept++;
                log.warn(
                        "유령을 정리했다 — batchId={} histId={} node={} 시작={}",
                        row.batchId(),
                        row.histId(),
                        row.nodeId(),
                        row.startDtm());
            }
        }
        return swept;
    }

    /**
     * <b>무엇을 모르는지</b> 를 적는다.
     *
     * <p>이 문구를 읽는 사람이 다음에 할 일은 <i>스택을 찾는 것</i> 이 아니라 <b>그 노드에
     * 무슨 일이 있었는지</b> 를 보는 것이다 — 그래서 노드 식별자가 문장 안에 있다.
     * 어디까지 처리됐는지는 <b>이 행이 답할 수 없다</b>는 것도 함께 말한다.
     */
    private static String messageFor(BatchHistoryWriter.RunningRow row) {
        return "실행하던 노드가 사라져 결과를 알 수 없다 — " + row.nodeId()
                + " 의 락이 만료됐고 갱신이 끊겼다. 어디까지 처리됐는지는 이 이력이 답하지 못한다";
    }

    private void sweepQuietly() {
        /*
          @swallow scheduleWithFixedDelay 는 작업이 던지면 **다시 부르지 않는다**.

          DB 가 잠깐 흔들린 것 때문에 청소가 영영 멈추면, 그 뒤로 쌓이는 유령을 아무도
          치우지 않는다. 다음 주기에 다시 시도하는 편이 언제나 낫다.

          태그는 검사를 끄는 것이 아니라 **이 판단을 검출기에 알리는 것**이다 — 면제도
          보고에 뜬다(quality-check.mjs 의 SWALLOW_TAG).
        */
        try {
            sweep();
        } catch (RuntimeException e) {
            log.warn("유령 정리가 실패했다 — 다음 주기에 다시 시도한다", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
