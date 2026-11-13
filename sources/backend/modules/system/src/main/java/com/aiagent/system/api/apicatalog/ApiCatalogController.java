// @feat FEAT-503
package com.aiagent.system.api.apicatalog;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.system.domain.apicatalog.ApiCatalogQueryService;
import com.aiagent.system.domain.apicatalog.ApiCatalogRow;
import com.aiagent.system.domain.apicatalog.ApiMenuRow;
import com.aiagent.system.domain.apicatalog.ApiUrlParams;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code API_CATALOG} — screen/api-catalog/requirements.md. <b>{@code get*} 둘뿐이다.</b>
 *
 * <p>이 컨트롤러가 <b>이 화면이 조회 전용이라는 사실의 집행</b>이다(screen/api-catalog/requirements.md). {@code save*} 를
 * 두지 않았으므로 시드 생성기가 {@code SAVE} 액션을 만들 수 없고(backend/api.md — 접두가 액션을
 * 정한다), 만들 수 없으므로 <b>어느 역할에도 줄 수 없다</b>. 화면에서 버튼을 감추는 것과
 * 층이 다르다.
 *
 * <p><b>{@code EXPORT} 에도 경로가 없다.</b> 엑셀은 있지만(sys/menu.md 의 액션 열이 {@code R E} 다)
 * 그것은 <b>프론트 생성</b>이다(frontend/grid.md · playbook/excel-download.md 첫째) — 서버가 낼 것이 없다. 권한은 시드에
 * 있고 버튼은 {@code <DataGrid>} 가 그린다.
 *
 * <p><b>이 화면 자신이 카탈로그에 실린다.</b> 아래 둘은 {@code /api/service/**} 이므로
 * {@code generateApiSeed} 가 스캔해 {@code SYS_API} 행을 만들고, {@code endpoints.ts} 선언이
 * {@code SYS_MENU_API} 를 만든다 — <b>목록의 첫 화면에서 자기 이름을 볼 수 있다</b>.
 */
@RestController
@RequestMapping("/api/system/service/apiCatalog")
public class ApiCatalogController {

    private final ApiCatalogQueryService queries;
    private final RequestLocale locale;

    public ApiCatalogController(ApiCatalogQueryService queries, RequestLocale locale) {
        this.queries = queries;
        this.locale = locale;
    }

    @Operation(
            summary = "API 카탈로그 목록 조회",
            description = "등록된 업무 API 를 페이지 단위로 반환한다. API 마다 그것을 쓰는 메뉴 수가 함께 실리며 0 이면 미매핑이다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<ApiCatalogRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * <b>페이징하지 않는다</b>({@code mode: 'all'}).
     *
     * <p>한 API 를 쓰는 화면은 많아야 몇이고, screen/api-catalog/requirements.md 가 <i>"0 건이면 §5.4 의 진단을 낸다"</i> 고
     * 적은 것은 <b>이 목록의 크기가 곧 답</b>이라는 뜻이다.
     *
     * <p><b>세션 언어를 받는다.</b> 메뉴 경로의 이름이 {@code SYS_MESSAGE} 에서 오기 때문이며
     * (sys/code.md), 요청 본문에 언어를 두지 않는 것은 screen/admin/system/code/requirements.md 의 코드 저장과 같은 이유다 — 언어는
     * <i>지금 이 사람이 보고 있는 것</i> 이고 그것은 세션이 갖는다.
     */
    @Operation(
            summary = "API 매핑 메뉴 조회",
            description = "선택한 API 를 쓰는 메뉴를 전량 반환한다. 메뉴 전체 경로와 사용 여부, 접근 가능 인원이 함께 실린다.")
    @PostMapping("/getMenuList")
    public ApiResponse<PageResponse<ApiMenuRow>> getMenuList(
            @RequestBody @Valid SearchRequest<ApiUrlParams> request) {
        return ApiResponse.success(
                queries.getMenuList(request, locale.current()), TraceId.current());
    }
}
