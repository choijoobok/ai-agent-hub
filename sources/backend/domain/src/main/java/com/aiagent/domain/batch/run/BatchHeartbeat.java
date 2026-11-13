package com.aiagent.domain.batch.run;

import com.aiagent.domain.lock.BatchLeaseStore;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 도는 동안 락의 만료를 <b>계속 뒤로 민다</b> — backend/persistence.md.
 *
 * <h2>없으면 오래 도는 배치가 자기 락을 잃는다</h2>
 *
 * <p>{@code EXPIRE_DTM} 은 선점할 때 <b>한 번</b> 정해진다({@code now + LOCK_EXPIRY_SEC}).
 * 배치가 그보다 오래 걸리면 <b>살아 있는데 락이 만료</b>되고, 다음 주기에 깨어난 다른 노드의
 * {@code tryAcquire} 가 <b>성공</b>한다 — 같은 배치가 두 노드에서 동시에 돈다. backend/persistence.md 이
 * 락으로 막으려던 사고가 <b>정확히 그 자리에서</b> 일어난다.
 *
 * <p>기본값이 300초라 흔한 일이다. 이 템플릿의 첫 구현인 보존 파기 배치가
 * ({@code RetentionPurgeJob}) 수백만 행을 청크로 지우므로 <b>가장 먼저 여기 걸릴 배치</b>다.
 *
 * <h2>기간을 늘리는 것으로는 풀리지 않는다</h2>
 *
 * <table><caption></caption>
 *   <tr><td>짧게</td><td>도는 배치의 락이 회수된다 — <b>중복 실행</b></td></tr>
 *   <tr><td>길게</td><td>진짜로 죽었을 때 그만큼 <b>아무도 못 돈다</b></td></tr>
 * </table>
 *
 * <p><b>둘을 함께 만족시키는 상수는 없다.</b> 미는 쪽을 택하면 {@code LOCK_EXPIRY_SEC} 의
 * 뜻이 <i>이만큼 걸릴 것이다</i>(예측 — 맞출 수 없다)에서 <b>이만큼 소식이 없으면 죽은
 * 것이다</b>(관측 — 맞출 수 있다)로 바뀐다. 그래서 화면의 설명도 바뀌지 않는다: 운영자가
 * 고르는 값은 여전히 <i>얼마나 기다렸다 죽었다고 볼 것인가</i> 하나다.
 *
 * <h2>주기는 만료의 3분의 1</h2>
 *
 * <p><b>한 번 놓쳐도 만료되지 않아야</b> 한다 — GC 정지나 순간적인 DB 지연으로 한 박자를
 * 거르는 일은 정상 운영에서 일어난다. 2분의 1이면 한 번 놓치는 순간 만료이므로 여유가
 * 없고, 더 잘게 쪼개면 <b>이득 없이 UPDATE 만 늘어난다</b>.
 *
 * <h2>뺏겼으면 <b>멈춘다</b></h2>
 *
 * <p>{@link BatchLeaseStore#heartbeat} 는 {@code NODE_ID} 와 HIST_ID가 맞을 때만 민다. {@code false} 가
 * 돌아왔다는 것은 <b>이미 남이 잡았거나 누가 강제 해제했다</b>는 뜻이고, 그때 계속 두드리면
 * 로그만 채운다. 배치를 죽이지는 <b>않는다</b> — 중단은 협조적이어야 하고(sys/batch.md), 여기서
 * 스레드를 끊으면 배치가 체크포인트 밖에서 죽어 <b>정확히 유령을 만든다</b>. 대신 크게
 * 남긴다: 그 시점부터 <b>중복 실행이 일어났을 수 있다</b>는 것이 이 로그의 내용이다.
 */
@Component
public class BatchHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(BatchHeartbeat.class);

    /** 만료의 3분의 1마다 민다 — 한 박자를 걸러도 살아남는다. */
    private static final int DIVISOR = 3;

    /** 아무리 짧아도 이보다 자주 두드리지 않는다 — 만료를 15초 미만으로 두는 설정 대비. */
    private static final long MIN_PERIOD_SEC = 5L;

    private final BatchLeaseStore locks;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    public BatchHeartbeat(BatchLeaseStore locks, Clock clock) {
        this.locks = locks;
        this.clock = clock;
        /*
          데몬 스레드다. 배치 실행 풀(BatchExecutorConfig)과 나누는 것은 **밀기가 도는 것에
          막히면 안 되기** 때문이다 — 풀이 가득 차 있을 때 하트비트가 큐에서 기다리면
          정확히 밀어야 할 순간에 밀리지 않는다.
        */
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "batch-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 밀기를 시작한다 — 돌려받은 것을 <b>반드시 닫는다</b>.
     *
     * @param expirySec 등록의 {@code LOCK_EXPIRY_SEC} — 밀 때마다 이만큼 뒤로 잡는다
     */
    public Beat start(String lockType, String key, Long histId, int expirySec) {
        long period = Math.max(MIN_PERIOD_SEC, expirySec / DIVISOR);
        Beat beat = new Beat(lockType, key, histId, expirySec);
        beat.future.set(scheduler.scheduleWithFixedDelay(beat, period, period, TimeUnit.SECONDS));
        return beat;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /** 한 실행의 밀기 — {@link #close()} 가 멈춘다. */
    public final class Beat implements AutoCloseable, Runnable {

        private final String lockType;
        private final String key;
        private final int expirySec;
        private final Long histId;
        private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();

        private Beat(String lockType, String key, Long histId, int expirySec) {
            this.lockType = lockType;
            this.key = key;
            this.expirySec = expirySec;
            this.histId = histId;
        }

        @Override
        public void run() {
            /*
              예외를 삼킨다 — scheduleWithFixedDelay 는 작업이 던지면 **다시 부르지
              않는다**. DB 가 잠깐 흔들린 것 때문에 밀기가 영영 멈추면, 그 배치는 다음
              만료에서 락을 잃는다. 여기서는 조용히 넘기고 다음 박자에 다시 시도한다.
            */
            try {
                if (locks.heartbeat(key, histId, clock.instant().plusSeconds(expirySec))) {
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("락 만료를 밀지 못했다 — {}:{} (다음 주기에 다시 시도한다)", lockType, key, e);
                return;
            }
            log.error(
                    "락을 이미 잃었다 — {}:{}. 같은 배치가 다른 노드에서 함께 돌고 있을 수 있다",
                    lockType,
                    key);
            close();
        }

        @Override
        public void close() {
            ScheduledFuture<?> scheduled = future.getAndSet(null);
            if (scheduled != null) {
                // false 다 — 밀기가 UPDATE 중이면 끝내게 둔다. 끊어야 할 이유가 없다.
                scheduled.cancel(false);
            }
        }
    }

    /** 밀기 주기 — 테스트가 계산을 확인한다. */
    public static Duration periodOf(int expirySec) {
        return Duration.ofSeconds(Math.max(MIN_PERIOD_SEC, expirySec / DIVISOR));
    }
}
