package com.aiagent.domain.batch.run;

import com.aiagent.domain.batch.BatchExecType;
import com.aiagent.domain.batch.BatchResultCode;
import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.notification.producer.BatchFailNotifier;
import com.aiagent.support.lock.NodeId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력 행을 <b>접수 확정과 함께 보이게</b> 쓰는 자리 — sys/batch.md.
 *
 * <p>{@link BatchRunner} 에서 떼어 낸 이유는 <b>트랜잭션 경계가 실행과 반대</b>이기
 * 때문이다. 배치 본체는 트랜잭션 밖에서 오래 돌고(그 안에서 자기 트랜잭션을 연다),
 * 이력 행은 <b>그 전후에 각각 커밋</b>돼야 한다.
 *
 * <table><caption></caption>
 *   <tr><td>{@link #start}</td><td><b>접수 REQUIRED에 참여</b></td>
 *       <td>커밋 안 하면 도는 동안 화면에 아무것도 안 보인다 — {@code RUNNING} 행을
 *           미리 넣는 이유 전부가 무효가 된다</td></tr>
 *   <tr><td>{@link #finish}</td><td><b>실행 후에 새 트랜잭션</b></td>
 *       <td>배치가 자기 업무 트랜잭션을 롤백해도 <b>실패했다는 기록은 남아야</b> 한다</td></tr>
 * </table>
 *
 * <p>시작과 배치 lease는 같은 접수 트랜잭션에 참여한다. 종결은 {@code REQUIRES_NEW}로
 * 독립하여 작업 본체의 롤백과 구분한다.
 *
 * <p><b>{@code NODE_ID} 를 여기서 채운다.</b> sys/batch.md 가 세 테이블이 같은 규칙으로 이 값을
 * 만든다고 했고 그 하나가 {@link NodeId} 다 — 러너가 문자열을 직접 만들면 락 테이블과
 * 대조가 깨진다.
 */
@Service
public class BatchHistoryWriter {

    private final SysBatchHistoryRepository histories;
    private final BatchFailNotifier failNotifier;
    private final NodeId nodeId;
    private final Clock clock;
    private final BatchRowLock rows;

    public BatchHistoryWriter(
            SysBatchHistoryRepository histories,
            BatchFailNotifier failNotifier,
            NodeId nodeId,
            Clock clock, BatchRowLock rows) {
        this.histories = histories;
        this.failNotifier = failNotifier;
        this.nodeId = nodeId;
        this.clock = clock;
        this.rows = rows;
    }

    /**
     * 시작을 적고 <b>번호를 돌려준다</b> — 그 번호가 {@code BatchJobContext.histId} 다.
     *
     * @return {@code HIST_ID}
     */
    @Transactional
    public Long start(String batchId, BatchExecType execType) {
        SysBatchHistory started =
                SysBatchHistory.started(batchId, execType, nodeId.value(), clock.instant());
        return histories.save(started).getHistId();
    }

    @Transactional
    public void discardStart(Long histId) { histories.deleteById(histId); }

    /**
     * 건너뜀 — {@code RUNNING} 을 거치지 않는 두 갈래다(sys/batch.md).
     *
     * <p>사유를 {@code RSLT_MSG} 에 적되 <b>판정은 코드가 갖는다</b> — 화면이 문자열을
     * 읽어 색을 정하면 문구를 고치는 순간 화면이 틀린다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skip(
            String batchId, BatchExecType execType, BatchResultCode rsltCd, String rsltMsg) {
        histories.save(SysBatchHistory.skipped(
                batchId, execType, nodeId.value(), clock.instant(), rsltCd, rsltMsg));
    }

    /**
     * 종료 — {@code RUNNING} 행을 갱신한다.
     *
     * <p><b>행이 없으면 조용히 지나간다.</b> 파기 배치가 그 사이 지나갔다는 뜻인데
     * (보존 1년이므로 실제로는 없다), 여기서 던지면 <b>정상 종료한 배치가 예외로 끝난
     * 것처럼</b> 로그에 남는다 — 이력을 못 적은 것과 배치가 실패한 것은 다른 사건이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(
            Long histId,
            BatchResultCode rsltCd,
            String rsltMsg,
            String rsltDtlMsg,
            String errMsg) {
        rows.history(histId).ifPresent(history -> {
            if (history.getRsltCd() != BatchResultCode.RUNNING && history.getRsltCd() != BatchResultCode.LOST) return;
            history.finish(rsltCd, clock.instant(), rsltMsg, rsltDtlMsg, errMsg);
            /*
              <b>BATCH-06 이 여기 걸린다</b>(screen/admin/system/batch/history/requirements.md · screen/admin/system/notice/requirements.md). 러너에는 FAIL 을 적는
              자리가 넷인데(큐 적재 실패 · 구현 없음 · 예외 · 유령), 거기에 각각 붙이면
              <b>하나를 빠뜨린 것을 아무도 모른다</b> — 드물게 타는 경로라 운영에서
              "알림이 안 왔다" 로만 드러난다.

              이력을 적는 자리에 걸면 <b>실패 기록과 알림이 같은 사건의 두 면</b>이 되고,
              새 실패 경로가 생겨도 이력을 적는 한 알림이 따라온다.
            */
            if (BatchFailNotifier.isFailure(rsltCd)) {
                failNotifier.notifyFailure(histId, history.getBatchId(), rsltCd, rsltMsg);
            }
        });
    }

    /**
     * 취소가 요청됐는가 — <b>배치가 체크포인트마다 부른다</b>(sys/batch.md).
     *
     * <p>매번 DB 를 읽는다. 요청은 <b>다른 노드의 화면</b>이 세우므로 메모리 플래그로는
     * 보이지 않는다 — 캐시하면 <i>중단이 안 먹는다</i> 가 되고, 그것이 운영자가 버튼을
     * 다시 누르게 만드는 상황이다(screen/admin/system/batch/history/requirements.md).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isCancelRequested(Long histId) {
        return histories
                .findById(histId)
                .map(history -> history.getCancelReqDtm() != null)
                .orElse(false);
    }

    /**
     * 지금 {@code RUNNING} 인 행들 — <b>유령 청소가 훑을 목록</b>이다(screen/admin/system/batch/history/requirements.md).
     *
     * <p>엔티티가 아니라 {@link RunningRow} 를 돌려준다. 청소는 <b>트랜잭션 밖에서</b>
     * 락을 하나씩 물어보므로, 엔티티를 넘기면 <i>준영속 객체를 들고 판정하다가 나중에
     * 다른 트랜잭션에서 저장</i> 하는 모양이 된다 — 판정에 필요한 것은 세 값뿐이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<RunningRow> runningHistories() {
        return histories.findByRsltCd(BatchResultCode.RUNNING).stream()
                .map(h -> new RunningRow(
                        h.getHistId(), h.getBatchId(), h.getNodeId(), h.getStartDtm()))
                .toList();
    }

    /**
     * 유령으로 종결한다 — <b>다시 읽고 나서</b> 바꾼다(screen/admin/system/batch/history/requirements.md).
     *
     * <p>{@link #runningHistories} 가 준 목록은 <b>읽은 시점의 사실</b>이다. 락을 물어보는
     * 사이에 배치가 스스로 끝냈을 수 있고, 그때 덮어쓰면 <b>성공한 실행이 유실로</b> 기록된다
     * — 그래서 {@code markLost} 가 {@code RUNNING} 일 때만 먹는다(엔티티가 그 조건을 갖는다).
     *
     * @return 이번 호출이 실제로 바꿨는가
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markLost(Long histId, String rsltMsg) {
        return rows
                .history(histId)
                .map(history -> {
                    /*
                      <b>실제로 바꿨을 때만 알린다.</b> markLost 는 RUNNING 이 아니면
                      false 를 돌려주는데(엔티티가 그 조건을 갖는다), 그 경우는 배치가
                      스스로 끝낸 뒤라 <b>이미 알림이 나갔거나 성공한 것</b>이다 —
                      여기서 또 보내면 성공한 실행에 유실 알림이 붙는다.
                    */
                    boolean changed = history.markLost(clock.instant(), rsltMsg);
                    if (changed) {
                        failNotifier.notifyFailure(
                                histId, history.getBatchId(), BatchResultCode.LOST, rsltMsg);
                    }
                    return changed;
                })
                .orElse(false);
    }

    /** 시작 시각 — 즉시 실행 거부 문구가 <i>"09:14 시작"</i> 을 여기서 얻는다(screen/admin/system/batch/history/requirements.md). */
    public Instant now() {
        return clock.instant();
    }

    /** 유령 판정에 필요한 것 전부 — 번호 · 어느 배치 · <b>어느 프로세스</b> · 언제부터. */
    public record RunningRow(Long histId, String batchId, String nodeId, Instant startDtm) {}
}
