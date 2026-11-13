// @feat FEAT-507
package com.aiagent.system.api.log;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.auth.loginlog.LoginLogQueryService;
import com.aiagent.domain.auth.loginlog.LoginLogRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code LOGIN_LOG} — screen/admin/system/log/login/requirements.md. <b>경로가 하나다.</b>
 *
 * <p>이 템플릿에서 가장 작은 컨트롤러이며 그것이 설계의 결과다. {@code save*} 가 없는 것은
 * 이력 행이 불변이기 때문이고(screen/admin/system/log/login/requirements.md), {@code getDetail} 마저 없는 것은 <b>{@code nclob}
 * 컬럼이 하나도 없어</b> 목록이 곧 전부이기 때문이다. 선언이 없으므로 시드 생성기가
 * {@code SAVE} 액션을 만들 수 없고, 만들 수 없으므로 <b>어느 역할에도 줄 수 없다</b>
 * (backend/api.md — 접두가 액션을 정한다).
 *
 * <p><b>{@code EXPORT} 에도 경로가 없다.</b> 엑셀은 있지만(sys/menu.md 의 액션 열이 {@code R E}
 * 다) 프론트 생성이며(playbook/excel-download.md 첫째) 다운로드 이력은 {@code POST /api/system/common/excel/saveLog} 가
 * 남긴다.
 *
 * <p><b>세션 언어를 받지 않는다</b> — 사용 이력과 갈리는 자리다. 저쪽은 메뉴명이
 * {@code SYS_MENU} 에서 와 서버가 번역해야 했고, 여기 이름이 붙는 유일한 컬럼은 결과
 * 코드인데 그것은 <b>공통코드라 프론트 캐시가 붙인다</b>(sys/code.md).
 */
@RestController
@RequestMapping("/api/system/service/loginLog")
public class LoginLogController {

    private final LoginLogQueryService queries;

    public LoginLogController(LoginLogQueryService queries) {
        this.queries = queries;
    }

    /**
     * <b>커서형이다</b>({@code mode: 'cursor'} — backend/api.md). 총 건수가 없으므로 화면의 푸터가
     * {@code ‹ 이전 · 다음 ›} 을 그린다.
     */
    @Operation(
            summary = "로그인 이력 목록 조회",
            description = "로그인 성공·실패와 세션 종료를 최근 순으로 반환한다. 총 건수를 세지 않는 커서형이다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<LoginLogRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }
}
