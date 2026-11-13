// @feat FEAT-503
package com.aiagent.sample.api.singlecursor;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.sample.domain.SampleQueryService;
import com.aiagent.sample.domain.SmplOrderCursorRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SMPL_SINGLE_CURSOR} — playbook/INDEX.md. <b>커서형 페이징의 유일한 실물이다.</b>
 *
 * <h2>엔드포인트가 하나뿐이다</h2>
 *
 * <p>이 화면의 액션은 <b>{@code R} 과 {@code E}</b> 다 — 저장이 없다. playbook/INDEX.md 의 커버리지
 * 표에서 이 화면이 유일하게 갖는 것은 <b>페이징 {@code cursor}</b> 하나이며, 그것을 보이는
 * 데 필요한 것은 조회 하나다.
 *
 * <p>엑셀 둘은 <b>프론트가 만든다</b> — 화면이 그리는 표가 곧 내려받을 표다. {@code EXPORT}
 * 권한은 메뉴가 선언한다(sys/menu.md).
 *
 * <h2>같은 테이블에 모드가 다른 엔드포인트 둘</h2>
 *
 * <p>①({@code getOrderOffsetList})과 <b>같은 {@code SMPL_ORDER}</b> 를 읽는다. 테이블을 하나
 * 더 만들지 않은 것이 playbook/INDEX.md 의 결정이며, 그렇게 하면 <b>모드가 데이터의 성질처럼</b>
 * 읽힌다 — 모드는 <b>엔드포인트가 정한다</b>(backend/api.md).
 *
 * <h2>커서형이 갈리는 것 셋</h2>
 *
 * <table border="1">
 * <caption>playbook/INDEX.md</caption>
 *   <tr><td>총 건수</td><td><b>없다</b> — {@code COUNT} 를 돌지 않는 것이 이 모드의 값이다</td></tr>
 * <tr><td>정렬</td><td><b>한 컬럼만</b>. 둘 이상이면 400 이다(backend/api.md)</td></tr>
 * <tr><td>기간</td><td><b>필수 + 상한</b>. screen/admin/system/log/access/requirements.md 의 규칙을 업무 테이블에서 되풀이한다</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/sample/service")
public class SampleSingleCursorController {

    private final SampleQueryService queries;

    public SampleSingleCursorController(SampleQueryService queries) {
        this.queries = queries;
    }

    /**
     * 목록 — 커서형이다.
     *
     * <p><b>기간 검증이 서버에도 있다.</b> 화면의 {@code required} 와 {@code rangeDays} 가
     * 먼저 막지만 <b>도구·스크립트는 그 폼을 지나지 않는다</b> — 화면만 막으면 그 상한은
     * 규약이 아니라 권고다(screen/admin/system/log/access/requirements.md).
     */
    @Operation(
            summary = "샘플 주문 목록 조회 — 커서형",
            description = "주문일시 기준 커서 페이징으로 반환한다. 총 건수를 세지 않으며 기간이 필수다.")
    @PostMapping("/getOrderCursorList")
    public ApiResponse<PageResponse<SmplOrderCursorRow>> getOrderCursorList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getOrderCursorList(request), TraceId.current());
    }
}
