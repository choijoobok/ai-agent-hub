// @feat FEAT-507
package com.aiagent.system.api.log;

import com.aiagent.system.api.log.request.ErrorLogDetailRequest;
import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.log.errorlog.ErrorLogDetail;
import com.aiagent.domain.log.errorlog.ErrorLogQueryService;
import com.aiagent.domain.log.errorlog.ErrorLogRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code ERROR_LOG} — screen/admin/system/log/error/requirements.md. <b>{@code get*} 둘뿐이다.</b>
 *
 * <p>{@code save*} 가 없는 것이 <i>이 화면에 쓰기가 없다</i> 는 사실의 집행이다 — 시드
 * 생성기가 {@code SAVE} 액션을 만들 수 없고(backend/api.md), 만들 수 없으므로 <b>어느 역할에도
 * 줄 수 없다</b>. {@code AccessLogController} 와 같은 모양이다.
 *
 * <p><b>경로가 목업에 이미 있었다</b> — {@code api-catalog.html} 이
 * {@code /api/system/service/errorLog/getList} · {@code /getDetail} 을 적어 두었고 그대로 맞는다.
 * 다만 그 목업의 설명이 <i>레벨로 조회</i> 라고 한 것은 <b>상태</b>다(screen/admin/system/log/error/requirements.md) — 로그 레벨은
 * 애플리케이션 로그 파일의 어휘이고(28), 이 테이블에는 그 컬럼이 없다.
 *
 * <p><b>이 컨트롤러가 자기 테이블에 행을 만들 수 있다.</b> 기간을 비우고 조회하면 400 이
 * 나가고 그 400 이 {@code SYS_LO_EXCEPTION} 한 행이 된다 — 사용 이력의 자기 참조(screen/admin/system/log/error/requirements.md)와
 * 같은 종류이며 고장이 아니다.
 */
@RestController
@RequestMapping("/api/system/service/errorLog")
public class ErrorLogController {

    private final ErrorLogQueryService queries;
    private final RequestLocale locale;

    public ErrorLogController(ErrorLogQueryService queries, RequestLocale locale) {
        this.queries = queries;
        this.locale = locale;
    }

    /**
     * <b>커서형이다</b>({@code mode: 'cursor'} — backend/api.md). 총 건수를 세지 않으므로 화면의
     * 푸터가 {@code ‹ 이전 · 다음 ›} 을 그린다.
     *
     * <p><b>세션 언어를 받는다</b> — 메뉴명이 {@code SYS_MESSAGE} 에서 오기 때문이다(sys/code.md).
     */
    @Operation(
            summary = "에러 로그 목록 조회",
            description = "예외가 난 요청을 최근 순으로 반환한다. 오류 코드와 완성된 문구가 함께 실리며 스택은 실리지 않는다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<ErrorLogRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request, locale.current()), TraceId.current());
    }

    /**
     * <b>탭 둘을 한 번에</b> 준다 — 스택(자기 테이블)과 요청 전문(사용 이력에서
     * {@code TRACE_ID} 로). 왕복을 나누면 두 번째가 실패했을 때 절반만 열린 모달이 된다(screen/admin/system/log/error/requirements.md).
     */
    @Operation(
            summary = "에러 로그 상세 조회",
            description = "한 행의 스택트레이스와, 같은 추적 ID 의 요청 전문을 함께 가져온다. 4xx 는 스택이 없고 90일이 지난 요청은 전문이 없다.")
    @PostMapping("/getDetail")
    public ApiResponse<ErrorLogDetail> getDetail(@RequestBody @Valid ErrorLogDetailRequest request) {
        return ApiResponse.success(queries.getDetail(request.exceptionLogSeq()), TraceId.current());
    }
}
