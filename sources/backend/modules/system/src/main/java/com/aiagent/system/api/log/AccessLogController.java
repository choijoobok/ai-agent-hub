// @feat FEAT-507
package com.aiagent.system.api.log;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.log.admin.AccessLogDetail;
import com.aiagent.domain.log.admin.AccessLogQueryService;
import com.aiagent.domain.log.admin.AccessLogRow;
import com.aiagent.system.api.log.request.AccessLogDetailRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code ACCESS_LOG} — screen/admin/system/log/access/requirements.md. <b>{@code get*} 둘뿐이다.</b>
 *
 * <p>{@code save*} 를 두지 않은 것이 <i>이 화면에 쓰기가 없다</i> 는 사실의 집행이다 —
 * 시드 생성기가 {@code SAVE} 액션을 만들 수 없고(backend/api.md — 접두가 액션을 정한다), 만들 수
 * 없으므로 <b>어느 역할에도 줄 수 없다</b>. 화면에서 버튼을 감추는 것과 층이 다르다.
 *
 * <p><b>{@code getDetail} 도 {@code READ} 다</b> — 접두 규칙에 예외가 없다(backend/api.md · backend/packages.md).
 *
 * <p><b>{@code EXPORT} 에도 경로가 없다.</b> 엑셀은 있지만(sys/menu.md 의 액션 열이 {@code R E} 다)
 * 프론트 생성이며(playbook/excel-download.md 첫째) 서버가 낼 것이 없다. 다운로드 이력은
 * 구 API 모듈의 엑셀 기록기가 받는 {@code POST /api/system/common/excel/saveLog} 로 남으며, 그 행은
 * <b>이 화면이 조회하는 바로 그 테이블</b>에 들어간다 — LOG-08 이 그것으로 충족된다.
 *
 * <p><b>이 컨트롤러의 호출이 자기 테이블에 남는다.</b> 조회하면 그 조회가
 * {@code SYS_LO_ACCESS} 에 기록되고 다음 조회에서 자기 행이 맨 위에 보인다 — 고장이 아니라
 * 이 템플릿에서 <b>열람 감사에 가장 가까운 것</b>이다(screen/admin/system/log/access/requirements.md · sys/logs.md).
 */
@RestController
@RequestMapping("/api/system/service/accessLog")
public class AccessLogController {

    private final AccessLogQueryService queries;
    private final RequestLocale locale;

    public AccessLogController(AccessLogQueryService queries, RequestLocale locale) {
        this.queries = queries;
        this.locale = locale;
    }

    /**
     * <b>커서형이다</b>({@code mode: 'cursor'} — backend/api.md). 총 건수를 세지 않으므로 응답에
     * {@code totalElements} 가 없고, 화면의 푸터가 그것을 보고 {@code ‹ 이전 · 다음 ›} 을 그린다.
     *
     * <p><b>세션 언어를 받는다</b> — 메뉴명이 {@code SYS_MESSAGE} 에서 오기 때문이며(sys/code.md),
     * 요청 본문에 언어를 두지 않는 것은 <i>지금 이 사람이 보고 있는 것</i> 을 세션이 갖기
     * 때문이다.
     */
    @Operation(
            summary = "사용 이력 목록 조회",
            description = "일어난 요청 전부를 최근 순으로 반환한다. 총 건수를 세지 않는 커서형이며 요청 전문은 실리지 않는다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<AccessLogRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request, locale.current()), TraceId.current());
    }

    @Operation(
            summary = "사용 이력 요청 전문 조회",
            description = "한 행의 요청 전문만 가져온다. 목록에 실을 수 없는 nclob 컬럼이며 상한에서 잘렸는지가 함께 실린다.")
    @PostMapping("/getDetail")
    public ApiResponse<AccessLogDetail> getDetail(
            @RequestBody @Valid AccessLogDetailRequest request) {
        return ApiResponse.success(queries.getDetail(request.accessLogSeq()), TraceId.current());
    }
}
