// @feat FEAT-506
package com.aiagent.system.api.config;

import com.aiagent.system.api.config.request.ConfigSaveRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.config.admin.ConfigQueryService;
import com.aiagent.domain.config.admin.ConfigService;
import com.aiagent.domain.config.admin.ConfigRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code CONFIG_MGMT} — screen/config/requirements.md. <b>단일 그리드이고 액션은 {@code R} {@code S} 둘이다.</b>
 *
 * <p><b>{@code EXPORT} 가 없다</b>(sys/menu.md) — 키 목록이 {@code yml} 에 있고 바뀐 값은
 * <i>변경 여부</i> 조회로 화면에서 끝난다. 수십 건짜리 목록이다.
 *
 * <p><b>행 추가도 없다.</b> 아우터 조인의 왼쪽에 없는 키를 넣으면 아무 데도 읽히지 않는
 * 행이 생기고, 그것은 <b>저장은 되고 동작하지 않는</b> 가장 나쁜 실패다(frontend/grid.md).
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md) — {@code get*} 는 {@code READ},
 * {@code save} 는 {@code SAVE}. <b>되돌리기도 {@code save} 다</b>: 일괄 저장이라 수정과
 * 삭제가 한 요청이며, 쪼개면 화면의 저장 버튼 하나가 두 번 나간다.
 */
@RestController
@RequestMapping("/api/system/service/config")
public class ConfigController {

    private final ConfigQueryService queries;
    private final ConfigService admin;

    public ConfigController(ConfigQueryService queries, ConfigService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "시스템 환경설정 목록 조회",
            description = "yml 이 정한 설정 키 전부를 SYS_CONFIG 오버라이드와 아우터 조인해 반환한다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<ConfigRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * 조회조건의 <b>접두사 콤보</b>가 쓴다 — screen/config/requirements.md.
     *
     * <p><b>목록 응답에 실어 보내지 않는다.</b> 접두사는 <i>지금 걸린 조건의 결과</i> 가
     * 아니라 <b>전 키에서 도출한 고정 집합</b>이라, 페이지에 실으면 <i>거른 뒤의 목록</i> 을
     * 후보로 내게 된다 — 한 번 좁히면 넓힐 수 없는 콤보가 된다.
     */
    @Operation(
            summary = "시스템 환경설정 접두사 목록 조회",
            description = "조회조건의 접두사 후보를 설정 키 전부에서 도출해 반환한다.")
    @PostMapping("/getPrefixes")
    public ApiResponse<List<String>> getPrefixes() {
        return ApiResponse.success(queries.prefixes(), TraceId.current());
    }

    /**
     * <b>응답 본문이 없다.</b> 저장 결과로 화면이 할 일은 재조회이고(frontend/grid.md) 그것은 별도
     * 호출이다 — 여기서 갱신된 행을 돌려주면 재조회와 두 갈래가 된다.
     */
    @Operation(
            summary = "시스템 환경설정 저장",
            description = "적용값 수정과 되돌리기(오버라이드 삭제)를 한 트랜잭션으로 반영하고 설정 캐시 버전을 올린다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid ConfigSaveRequest request) {
        admin.save(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
