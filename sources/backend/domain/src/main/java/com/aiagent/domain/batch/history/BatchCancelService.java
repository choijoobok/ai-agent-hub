package com.aiagent.domain.batch.history;

import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.run.BatchRowLock;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중단 요청 — BATCH-05 · screen/admin/system/batch/schedule/requirements.md. <b>요청까지가 화면의 책임이다.</b>
 *
 * <p>배치는 <b>다른 노드에서 돌 수 있으므로</b> 스레드를 직접 끊을 수 없다 — 협조적
 * 중단이며 배치가 체크포인트에서 플래그를 읽고 스스로 멈춘다. 그래서 이 서비스가 하는
 * 일은 <b>{@code CANCEL_REQ_DTM} 을 세우는 것</b> 하나다.
 *
 * <h2>{@code SAVE} 가 아니라 {@code EXECUTE} 다</h2>
 *
 * <p>screen/admin/system/batch/schedule/requirements.md — {@code SYS_BATCH_HISTORY} 의 컬럼을 쓰지만 <b>컬럼 쓰기가 결과 그 자체가
 * 아니라</b> 실행 중인 절차에 요청을 전달하는 <b>통로</b>다. 아무도 읽지 않으면
 * <b>아무 일도 일어나지 않는다</b> — screen/admin/system/user/requirements.md 의 잠금 해제와 갈리는 지점이 그것이다.
 * 경로 접두({@code cancel} → <i>그 외</i> → {@code EXECUTE})와 판단이 일치하므로
 * {@code @ApiActionType} 을 쓰지 않는다.
 *
 * <h2>대상이 아닌 것을 조용히 건너뛰지 않는다</h2>
 *
 * <p>화면이 {@code eligible} 로 이미 걸렀지만 그것은 <b>고른 시점의 사실</b>이다(frontend/grid.md).
 * 그 사이 배치가 끝났을 수 있고, 그때 건너뛰면 운영자는 <i>중단이 먹었다</i> 고 믿는다.
 * 원칙 P1 의 <b>실패가 시끄러운 쪽</b>이다.
 *
 * <p><b>이미 요청된 것은 다르다.</b> 그것은 <i>대상이 아니다</i> 가 아니라
 * <i>이미 됐다</i> 이므로 던지지 않고 세어서 돌려준다 — {@code CANCEL_REQ_DTM} 을 다시
 * 쓰면 <b>언제 눌렀나</b> 가 뒤로 밀리고, 화면이 그 값으로 문구를 만든다(screen/admin/system/batch/schedule/requirements.md).
 */
@Service
public class BatchCancelService {

    private final BatchRowLock rows;
    private final Clock clock;

    public BatchCancelService(BatchRowLock rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    /**
     * 선택한 이력에 중단을 요청한다 — <b>한 트랜잭션</b>이다.
     *
     * @param histIds 화면이 고른 것. <b>비면 거부</b>한다 — 버튼이 비활성이어야 할 상태다
     * @return 이번에 세운 건수와 이미 요청돼 있던 건수
     */
    @Transactional
    public CancelResult cancel(List<Long> histIds) {
        if (histIds == null || histIds.isEmpty()) {
            throw new BusinessException(
                    BatchHistoryErrorCode.NOT_CANCELABLE, "중단 대상이 비었다");
        }
        List<SysBatchHistory> found = new ArrayList<>();
        for (Long id : histIds.stream().distinct().sorted().toList()) {
            found.add(rows.history(id).orElseThrow(() -> new BusinessException(
                    BatchHistoryErrorCode.PURGED, "없는 이력 번호다 — " + id)));
        }

        Instant now = clock.instant();
        int requested = 0;
        int already = 0;
        for (SysBatchHistory history : found) {
            if (!history.getRsltCd().isRunning()) {
                throw new BusinessException(
                        BatchHistoryErrorCode.NOT_CANCELABLE,
                        "실행 중이 아니다 — histId=" + history.getHistId() + " rsltCd="
                                + history.getRsltCd());
            }
            if (history.requestCancel(now)) {
                requested++;
            } else {
                already++;
            }
        }
        return new CancelResult(requested, already);
    }

    /**
     * 화면이 모달에 그리는 것 — frontend/grid.md.
     *
     * <p><b>둘을 가르는 이유.</b> <i>"N건에 중단을 요청했습니다"</i> 만 내면 이미 요청돼
     * 있던 것이 그 수에 섞여, 두 번 누른 사람이 <b>두 번 다 먹었다</b> 고 믿는다. screen/admin/system/batch/schedule/requirements.md 가
     * <i>"버튼은 한 번만 먹는다"</i> 로 요청의 유일성을 요구했으므로 그 사실이 응답에
     * 보여야 한다.
     *
     * @param requested 이번에 세운 것
     * @param alreadyRequested 이미 요청돼 있던 것 — <b>오류가 아니다</b>
     */
    public record CancelResult(int requested, int alreadyRequested) {}
}
