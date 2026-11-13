package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.history.BatchCancelService;
import com.aiagent.domain.batch.run.BatchRowLock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 중단 요청 — BATCH-05 · screen/admin/system/batch/schedule/requirements.md. <b>요청까지가 화면의 책임이다.</b>
 *
 * <p>협조적 중단이라 이 서비스가 하는 일은 {@code CANCEL_REQ_DTM} 을 세우는 것 하나다.
 * 그래서 검사할 것도 <b>세울 수 있는가</b>와 <b>이미 세워져 있는가</b> 둘뿐이며, 그 둘을
 * 가르는 것이 <i>버튼은 한 번만 먹는다</i> 를 화면에서 성립시킨다.
 */
class BatchCancelServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T14:00:00Z");

    private BatchRowLock histories;
    private BatchCancelService service;

    @BeforeEach
    void setUp() {
        histories = mock(BatchRowLock.class);
        service = new BatchCancelService(histories, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** {@code HIST_ID} 는 시퀀스가 채우므로 테스트에서는 심어 준다. */
    private static SysBatchHistory running(long histId) {
        SysBatchHistory history = SysBatchHistory.started(
                "SETTLE_DAILY", BatchExecType.SCHEDULE, "app-01#a1b2", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(history, "histId", histId);
        return history;
    }

    private static SysBatchHistory finished(long histId) {
        SysBatchHistory history = running(histId);
        history.finish(BatchResultCode.SUCCESS, NOW, "끝났다", null, null);
        return history;
    }

    @Test
    @DisplayName("실행 중인 행에 요청을 세운다")
    void requestsCancelOnRunningRows() {
        SysBatchHistory first = running(1L);
        SysBatchHistory second = running(2L);
        found(List.of(first, second));

        BatchCancelService.CancelResult result = service.cancel(List.of(1L, 2L));

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.alreadyRequested()).isZero();
        assertThat(first.getCancelReqDtm()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 요청된 것은 시각을 덮지 않고 따로 센다 — 두 번 누른 사람이 두 번 다 먹었다고 믿는다")
    void countsAlreadyRequestedSeparately() {
        SysBatchHistory already = running(1L);
        Instant first = NOW.minusSeconds(30);
        already.requestCancel(first);
        found(List.of(already));

        BatchCancelService.CancelResult result = service.cancel(List.of(1L));

        assertThat(result.requested()).isZero();
        assertThat(result.alreadyRequested()).isEqualTo(1);
        /*
          <b>시각이 뒤로 밀리면 안 된다.</b> 화면이 그 값으로 *"중단 요청됨 (09:14)"* 를
          만들고, 밀리면 <i>언제 눌렀나</i> 가 두 번째 클릭의 시각이 된다.
        */
        assertThat(already.getCancelReqDtm()).isEqualTo(first);
    }

    @Test
    @DisplayName("실행 중이 아니면 거절한다 — 조용히 건너뛰면 중단이 먹었다고 믿는다")
    void rejectsRowsThatAreNotRunning() {
        found(List.of(finished(1L)));

        assertThatThrownBy(() -> service.cancel(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().code())
                        .isEqualTo("err.batch-hist.not-cancelable"));
    }

    @Test
    @DisplayName("없는 번호가 섞이면 거절한다 — 그 사이 파기됐다는 뜻이다")
    void rejectsMissingRows() {
        found(List.of(running(1L)));

        assertThatThrownBy(() -> service.cancel(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().code())
                        .isEqualTo("err.batch-hist.purged"));
    }

    @Test
    @DisplayName("대상이 비면 거절한다 — 버튼이 비활성이어야 할 상태다")
    void rejectsEmptyTargets() {
        assertThatThrownBy(() -> service.cancel(List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("끝난 실행은 요청을 받지 않는다 — 영원히 응답하지 않을 요청이 이력에 남는다")
    void finishedRunCannotBeAskedToStop() {
        SysBatchHistory history = finished(1L);
        assertThat(history.getRsltCd().isRunning()).isFalse();
        assertThat(history.getRsltCd().isFinished()).isTrue();
    }

    @Test
    @DisplayName("건너뜀도 끝난 것이다 — 시작조차 안 했지만 그 시도는 종결됐다")
    void skippedIsFinishedToo() {
        SysBatchHistory skipped = SysBatchHistory.skipped(
                "SETTLE_DAILY", BatchExecType.SCHEDULE, "app-02#c3d4", NOW,
                BatchResultCode.SKIP_LOCK, "다른 노드가 실행 중이다");

        assertThat(skipped.getRsltCd().isFinished()).isTrue();
        /*
          <b>소요가 0 이지 null 이 아니다</b> — null 로 두면 소요 정렬에서 <b>유령과 같은
          자리</b>에 선다. 건너뜀은 끝난 사건이고 유령은 끝나지 않은 사건이다(screen/admin/system/batch/schedule/requirements.md).
        */
        assertThat(skipped.getElapsedMs()).isZero();
        assertThat(skipped.getEndDtm()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("종료가 중단 요청 시각을 지우지 않는다 — 요청했는데 그냥 끝난 경우가 있다")
    void finishingKeepsTheCancelRequest() {
        SysBatchHistory history = running(1L);
        history.requestCancel(NOW.minusSeconds(10));

        // 배치가 취소를 확인하지 않고 끝까지 돌았다 — sys/batch.md 표의 넷째 줄이다.
        history.finish(BatchResultCode.SUCCESS, NOW, "다 처리했다", null, null);

        assertThat(history.getRsltCd()).isEqualTo(BatchResultCode.SUCCESS);
        // <b>화면은 그것을 거짓말하지 않는다</b> — 요청한 사실이 남아야 그 상태를 그릴 수 있다.
        assertThat(history.getCancelReqDtm()).isEqualTo(NOW.minusSeconds(10));
    }
    private void found(List<SysBatchHistory> found) {
        found.forEach(row -> when(histories.history(row.getHistId())).thenReturn(java.util.Optional.of(row)));
    }

    @Test
    @DisplayName("TC-5087 중복·역순 입력도 이력 번호순으로 한 번만 잠근다")
    void cancellationLocksInStableDistinctOrder() {
        found(List.of(running(1L), running(2L)));
        assertThat(service.cancel(List.of(2L, 1L, 2L)).requested()).isEqualTo(2);
        var order = org.mockito.Mockito.inOrder(histories);
        order.verify(histories).history(1L);
        order.verify(histories).history(2L);
        order.verifyNoMoreInteractions();
    }

}
