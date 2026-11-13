// @feat FEAT-508
package com.aiagent.system.api.batch;

import com.aiagent.system.api.batch.request.BatchCancelRequest;
import com.aiagent.system.api.batch.request.BatchHistoryDetailRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.batch.history.BatchCancelService;
import com.aiagent.domain.batch.history.BatchHistoryDetail;
import com.aiagent.domain.batch.history.BatchHistoryQueryService;
import com.aiagent.domain.batch.history.BatchHistoryRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code BATCH_HISTORY} — screen/admin/system/batch/history/requirements.md. <b>조회 전용인데 개입한다.</b>
 *
 * <p>sys/menu.md 의 액션 열이 {@code R E X} 다 — {@code S} 가 없는데 {@code X} 가 있다.
 * screen/menu/requirements.md 의 조회 전용 모양에서 <b>{@code X} 하나가 더 있는</b> 형태이며, 그 하나가
 * <b>중단</b>이다(BATCH-05).
 *
 * <p><b>{@code save*} 가 없는 것이 이 화면에 쓰기가 없다는 사실의 집행</b>이다 — 시드
 * 생성기가 {@code SAVE} 액션을 만들 수 없고(backend/api.md), 만들 수 없으므로 <b>어느 역할에도
 * 줄 수 없다</b>. {@code ErrorLogController} 와 같은 장치다.
 *
 * <h2>{@code cancel} 이 {@code SAVE} 가 아닌 이유</h2>
 *
 * <p>screen/admin/system/batch/history/requirements.md — {@code CANCEL_REQ_DTM} 을 쓰지만 <b>컬럼 쓰기가 결과 그 자체가 아니라</b>
 * 실행 중인 절차에 요청을 전달하는 <b>통로</b>다. 아무도 읽지 않으면 아무 일도 일어나지
 * 않으며, screen/admin/system/user/requirements.md 의 잠금 해제(컬럼 값을 바꾸는 것이 목적)와 갈리는 지점이 그것이다.
 * 경로 접두({@code cancel} → <i>그 외</i> → {@code EXECUTE})와 판단이 <b>일치</b>하므로
 * {@code @ApiActionType} 을 쓰지 않는다.
 *
 * <h2>{@code EXPORT} 에는 경로가 없다 — 그런데 버튼은 있다</h2>
 *
 * <p>프론트 생성 엑셀이라 서버 호출이 없다(frontend/grid.md · playbook/excel-download.md). sys/menu.md 의 액션 열에
 * {@code E} 가 있고 그것은 {@code SYS_MENU.EXPORT_YN} 이 선언한다(sys/menu.md) — 056 시드가
 * 이 메뉴를 이미 켜 두었다. <b>여기 경로가 없는 것이 정상</b>이며, 내려받은 사실은
 * {@code /api/system/common/excel/saveLog} 가 남긴다(2026-08-30).
 */
@RestController
@RequestMapping("/api/system/service/batchHistory")
public class BatchHistoryController {

    private final BatchHistoryQueryService queries;
    private final BatchCancelService cancels;

    public BatchHistoryController(
            BatchHistoryQueryService queries, BatchCancelService cancels) {
        this.queries = queries;
        this.cancels = cancels;
    }

    /**
     * 목록 — <b>번호 페이징이고 총 건수를 센다</b>(screen/admin/system/batch/history/requirements.md).
     *
     * <p>이력 넷 중 유일하다. 커서형으로 두면 임의 컬럼 정렬이 성립하지 않고, 정렬이
     * 없으면 <i>"뭐가 오래 걸리나"</i> 에 답할 수 없다 — 그 질문이 sys/batch.md 이
     * {@code ELAPSED_MS} 를 저장하기로 한 이유 전부다.
     *
     * <p><b>기간이 필수가 아니다.</b> screen/admin/system/log/access/requirements.md 의 상한 규약을 상속하지 않으며(등록 수십 건 ×
     * 하루 수십 회다) 기본값만 화면이 채운다 — 비우고 조회하면 전량이고 느리지만 막지 않는다.
     */
    @Operation(
            summary = "배치 실행 이력 조회",
            description = "실행 이력을 조건에 맞춰 반환한다. 소요 시간 정렬이 열려 있고 총 건수를 센다. 상세와 오류는 실리지 않는다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<BatchHistoryRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * <b>탭 둘을 한 번에</b> 준다 — 결과 상세와 오류(screen/admin/system/batch/history/requirements.md).
     *
     * <p>왕복을 나누면 <b>두 번째가 실패했을 때 절반만 열린 모달</b>이 된다(screen/admin/system/log/access/requirements.md).
     * 여기는 둘이 같은 행의 두 컬럼이라 조인조차 없다.
     */
    @Operation(
            summary = "배치 실행 이력 상세 조회",
            description = "한 실행의 결과 상세와 예외 스택을 함께 가져온다. 남기지 않은 쪽은 비어 있고 화면이 그 탭을 비활성으로 둔다.")
    @PostMapping("/getDetail")
    public ApiResponse<BatchHistoryDetail> getDetail(
            @RequestBody @Valid BatchHistoryDetailRequest request) {
        return ApiResponse.success(queries.getDetail(request.histId()), TraceId.current());
    }

    /**
     * 중단 요청 — <b>요청까지가 화면의 책임이다</b>(screen/admin/system/batch/history/requirements.md).
     *
     * <p>확인 문구가 <i>"중단합니다"</i> 가 아니라 <i>"중단을 요청합니다 —
     * <b>즉시 멈추지 않을 수 있습니다</b>"</i> 인 것이 의도다. 협조적 중단이므로 안 멈췄을
     * 때 화면이 거짓말한 것이 되지 않아야 한다.
     *
     * <p><b>대상이 아닌 것이 오면 거절한다.</b> 화면이 {@code eligible} 로 걸렀지만 그것은
     * 고른 시점의 사실이고, 그 사이 배치가 끝났으면 <b>조용히 건너뛰는 것보다 실패가
     * 낫다</b> — 건너뛰면 운영자는 중단이 먹었다고 믿는다.
     */
    @Operation(
            summary = "배치 실행 중단 요청",
            description = "실행 중인 이력에 중단을 요청한다. 협조적 중단이라 즉시 멈추지 않을 수 있으며, 이미 요청된 건은 요청 시각을 덮지 않는다.")
    @PostMapping("/cancel")
    public ApiResponse<BatchCancelService.CancelResult> cancel(
            @RequestBody @Valid BatchCancelRequest request) {
        return ApiResponse.success(cancels.cancel(request.targets()), TraceId.current());
    }
}
