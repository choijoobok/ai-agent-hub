// @feat FEAT-503
package com.aiagent.sample.api.singleoffset;

import com.aiagent.sample.api.singleoffset.request.SampleConfirmRequest;
import com.aiagent.sample.api.singleoffset.request.SampleOffsetSaveRequest;
import com.aiagent.sample.api.singleoffset.request.SampleRecalcRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.RowReport;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.progress.ProgressHelper;
import com.aiagent.sample.domain.SampleOrderService;
import com.aiagent.sample.domain.SampleQueryService;
import com.aiagent.sample.domain.SmplCustomerRow;
import com.aiagent.sample.domain.SmplOrderFullRow;
import com.aiagent.sample.domain.SmplOrderTagRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SMPL_SINGLE_OFFSET} — playbook/INDEX.md. <b>어휘의 본진이다.</b>
 *
 * <p>playbook/INDEX.md 의 커버리지 표에서 굵은 점이 열둘인 화면이며, 지우면 템플릿에서 그 어휘의 예가
 * <b>통째로</b> 사라진다 — 에디터 셋 · {@code viewer} · 숨김 두 층 · {@code params} 모달 ·
 * 진행률 · {@code bigint} 가 전부 여기에만 있다.
 *
 * <h2>실행 둘이 갈리는 것이 이 화면의 핵심 시연이다</h2>
 *
 * <p>{@code confirmOrders} 와 {@code recalculateOrders} 는 <b>둘 다 {@code X} 액션</b>인데
 * 사용자 경험이 다르다. backend/crosscutting.md 이 <i>"오래 걸리는 작업은 jobId 를 발급하고 요청 스레드를
 * 붙잡지 않는다"</i> 고 정했고, <b>그 갈림이 화면에서 어떻게 보이는지</b> 는 지금까지
 * 어디에도 실물이 없었다.
 *
 * <table border="1">
 * <caption>playbook/INDEX.md 의 표</caption>
 *   <tr><td></td><td>확정</td><td>일괄 재계산</td></tr>
 *   <tr><td>대상</td><td>{@code selected}</td><td>{@code all}</td></tr>
 *   <tr><td>인자</td><td>없다</td><td>{@code params} 모달 — 기준일·세율</td></tr>
 *   <tr><td>응답</td><td>결과 요약(토스트)</td><td>{@code jobId}</td></tr>
 *   <tr><td>진행률</td><td>없다 — 응답이 곧 끝이다</td><td>폴링</td></tr>
 * </table>
 *
 * <h2>엑셀 선언이 없다</h2>
 *
 * <p>③과 갈리는 자리다(playbook/INDEX.md 의 <i>엑셀 ▾ — 선언 없음</i>). 이 화면이 그리는 표가 곧
 * 내려받을 표이므로 <b>프론트가 만든다</b> — 왕복이 0 인 것을 왕복 하나로 만들 이유가 없고,
 * {@code EXPORT} 권한은 메뉴가 선언한다(sys/menu.md).
 *
 * <h2>{@code /api/sample/service} 를 ③과 나눠 쓴다</h2>
 *
 * <p>도메인 마디는 <b>애그리거트 이름</b>이고(backend/api.md) 여섯 화면의 애그리거트는 하나다.
 * 갈리는 것은 <b>패키지와 메서드명</b>이며, 인가는 화면 폴더의 {@code endpoints.ts} 가
 * 가른다(playbook/INDEX.md · backend/api.md).
 */
@RestController
@RequestMapping("/api/sample/service")
public class SampleSingleOffsetController {

    private final SampleQueryService queries;
    private final SampleOrderService orderService;
    private final ProgressHelper progress;

    public SampleSingleOffsetController(
            SampleQueryService queries, SampleOrderService orderService, ProgressHelper progress) {
        this.queries = queries;
        this.orderService = orderService;
        this.progress = progress;
    }

    /**
     * 목록 — 컬럼 서른(playbook/INDEX.md).
     *
     * <p>③의 {@code getOrderList} 와 <b>같은 테이블 · 같은 페이징 모드</b>인데 엔드포인트가
     * 갈린다. 무엇을 실을지는 테이블이 아니라 <b>부르는 화면</b>이 정하기 때문이며, playbook/INDEX.md 가
     * <i>"모드는 엔드포인트가 정한다"</i> 고 적은 것과 같은 규칙이다.
     */
    @Operation(
            summary = "샘플 주문 목록 조회 — 전 컬럼",
            description = "조회조건 열둘로 걸러 오프셋 페이징으로 반환한다. 태그는 자식 테이블에서 한 번에 읽어 붙인다.")
    @PostMapping("/getOrderOffsetList")
    public ApiResponse<PageResponse<SmplOrderFullRow>> getOrderOffsetList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getOrderOffsetList(request), TraceId.current());
    }

    /**
     * 참조 팝업의 후보 — playbook/INDEX.md.
     *
     * <p><b>이 화면의 엔드포인트다.</b> 팝업이 자기 메뉴를 갖는 것처럼 굴면 어느 메뉴로도
     * 인가되지 않는 요청이 되어 403 이 난다({@code OpenPopup} 의 {@code menuCd}) — 팝업의
     * 조회는 <b>부르는 화면의 것</b>이다.
     *
     * <p><b>봉투에 조회조건이 없다.</b> 후보가 다섯이라 검색 폼을 붙이면 <i>고르러 왔는데
     * 먼저 찾아야</i> 하는 창이 된다.
     */
    @Operation(
            summary = "샘플 고객 목록 조회",
            description = "참조 팝업의 후보를 주문에서 중복 없이 뽑는다. 고객 마스터가 없는 샘플의 대역이다.")
    @PostMapping("/getCustomerList")
    public ApiResponse<PageResponse<SmplCustomerRow>> getCustomerList() {
        return ApiResponse.success(queries.getCustomerList(), TraceId.current());
    }

    /**
     * {@code listModal} 이 항목을 고르는 팝업의 후보 &mdash; 54 &sect;5.6 &middot; 68 &sect;9.6.
     *
     * <p><b>고객 팝업과 같은 자리</b>이며 갈리는 것은 이름 하나다: {@code /getTagList} 는
     * &#9316;의 것이라(한 주문의 태그 <b>행들</b>) 같은 {@code /api/sample/service} 아래에서
     * 쓸 수 없다.
     *
     * <p><b>이것이 없으면 태그를 더할 수 없다.</b> 레지스트리에 없는 키로 열면 팝업 호스트가
     * <i>취소</i> 로 돌려주므로({@code PopupHost}) 창의 <b>추가 버튼이 아무 일도 하지
     * 않는다</b> &mdash; playbook/INDEX.md 이 <i>섰다</i> 로 적어 둔 동안 그 자리가 비어 있었다.
     */
    @Operation(
            summary = "샘플 태그 후보 조회",
            description = "listModal 이 고를 태그 후보를 자식 테이블에서 중복 없이 뽑는다. 태그 마스터가 없는 샘플의 대역이다.")
    @PostMapping("/getTagCandidateList")
    public ApiResponse<PageResponse<SmplOrderTagRow>> getTagCandidateList() {
        return ApiResponse.success(queries.getTagCandidateList(), TraceId.current());
    }

    /**
     * 저장 — <b>행마다 커밋한다</b>(backend/row-transaction.md §4).
     *
     * <p><b>부분 실패도 200 이다.</b> 요청은 받아들여졌고 열 건을 시도했으며 그중 셋이
     * 안 됐다 — {@code 400} 은 <i>요청이 틀려 아무것도 시도하지 못했다</i> 는 뜻이라
     * 사실과 다르다. 전건이 실패해도 같다: 사용자가 할 일이 같기 때문이다(표를 보고
     * 고쳐 다시 낸다).
     */
    @Operation(
            summary = "샘플 주문 저장 — 전 컬럼",
            description = "신규·수정·삭제를 행마다 커밋한다. 실패한 행만 되돌아가고 결과를 건별로 돌려준다.")
    @PostMapping("/saveOffsetOrders")
    public ApiResponse<RowReport> saveOffsetOrders(
            @RequestBody @Valid SampleOffsetSaveRequest request) {
        return ApiResponse.success(orderService.saveFullOrders(request.rows()), TraceId.current());
    }

    /**
     * 확정 — <b>프로그레스가 없다</b>(playbook/INDEX.md).
     *
     * <p>고른 행 몇의 상태 하나를 바꾸는 일이라 응답이 곧 끝이다. 여기에 진행률을 붙이면
     * 창이 떴다가 바로 닫히고, 그 깜빡임은 <i>무언가 잘못됐나</i> 로 읽힌다(backend/crosscutting.md).
     *
     * <p><b>건수를 돌려주지 않는다.</b> {@code {count: 16}} 이었는데, 그 수로는 <i>어느 넷이
     * 왜</i> 안 됐는지 말할 수 없다 — 그리고 그때는 하나라도 걸리면 <b>스물 전부를 거절</b>했다.
     */
    @Operation(
            summary = "샘플 주문 확정",
            description = "접수 상태의 주문을 확정으로 옮긴다. 행마다 커밋하고 안 된 행의 사유를 돌려준다.")
    @PostMapping("/confirmOrders")
    public ApiResponse<RowReport> confirmOrders(@RequestBody @Valid SampleConfirmRequest request) {
        RowReport report =
                orderService.confirm(request.targets().stream().map(Long::valueOf).toList());
        return ApiResponse.success(report, TraceId.current());
    }

    /**
     * 일괄 재계산 — <b>{@code jobId} 를 즉시 돌려준다</b>(backend/crosscutting.md · playbook/INDEX.md).
     *
     * <p>요청 스레드를 붙잡지 않는 것이 전부다. 붙잡으면 그 요청이 타임아웃까지 열려 있고,
     * 사용자는 <i>화면이 멈췄다</i> 를 겪으며 새로고침한다 — 그러면 <b>같은 작업이 또</b>
     * 시작된다.
     *
     * <p><b>{@code totCnt} 를 {@code null} 로 준다.</b> 대상 건수를 미리 세려면 같은 조건으로
     * {@code COUNT} 를 한 번 더 돌아야 하는데, 그 사이에 늘어난 행은 어차피 세어지지 않는다 —
     * 화면은 불확정 막대를 그리고(sys/batch.md) {@code STEP_MSG} 가 어디까지 갔는지 말한다.
     */
    @Operation(
            summary = "샘플 주문 일괄 재계산",
            description = "기준일 이전 주문의 금액을 준 세율로 다시 센다. jobId 를 즉시 돌려주고 작업은 별도 풀에서 돈다.")
    @PostMapping("/recalculateOrders")
    public ApiResponse<Map<String, String>> recalculateOrders(
            @RequestBody @Valid SampleRecalcRequest request) {
        SampleRecalcRequest.Params params = request.params();
        String jobId = progress.start(
                null,
                job -> orderService.recalculate(params.baseDt(), params.vatRate(), job));
        return ApiResponse.success(Map.of("jobId", jobId), TraceId.current());
    }
}
