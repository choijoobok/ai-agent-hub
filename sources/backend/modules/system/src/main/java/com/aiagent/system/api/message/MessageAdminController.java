// @feat FEAT-505
package com.aiagent.system.api.message;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.system.api.message.request.MessageSaveRequest;
import com.aiagent.domain.message.admin.MessageQueryService;
import com.aiagent.domain.message.admin.MessageAdminService;
import com.aiagent.domain.message.admin.MessageKeyRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code MESSAGE_MGMT} — screen/admin/system/message/requirements.md.
 *
 * <p>경로가 {@code /api/service/**} 이므로 {@code SYS_API}·{@code SYS_MENU_API} 판정을 받는다
 * (backend/security.md). 시드가 없으면 <b>전부 403</b> 이며 그것이 fail-closed 의 정상 동작이다 —
 * {@code./gradlew generateApiSeed} 가 이 클래스를 스캔해 그 두 시드를 만든다(backend/api.md).
 *
 * <p><b>{@code @Operation(summary=…)} 은 필수다.</b> {@code SYS_API.API_NM} 의 원천이며
 * {@code ApiOperationSummaryTest} 가 빠뜨림을 빌드에서 막는다 — 시드를 재생성하지 않아도
 * 어겨질 수 있으므로 {@code checkApiSeed} 가 아니라 거기가 맞는 자리다.
 *
 * <p>액션은 <b>경로 접두가 정한다</b> — {@code getList} 는 {@code READ}, {@code save} 는
 * {@code SAVE}. 선언에 또 적지 않는다: 같은 사실이 두 곳에 표현되면 시드와 어긋난다.
 */
@RestController
@RequestMapping("/api/system/service/message")
public class MessageAdminController {

    private final MessageQueryService queries;
    private final MessageAdminService admin;

    public MessageAdminController(MessageQueryService queries, MessageAdminService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "다국어 메시지 목록 조회",
            description = "메시지 키를 페이지 단위로 반환한다. 키마다 지원 언어 전부의 값이 함께 실린다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<MessageKeyRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * <b>응답 본문이 없다.</b> 저장 결과로 화면이 할 일은 재조회이고(frontend/grid.md) 그것은 별도
     * 호출이다 — 여기서 갱신된 행을 돌려주면 <b>재조회와 두 갈래</b>가 되고, 둘이 어긋날 때
     * 화면이 어느 쪽을 믿을지 정해야 한다.
     */
    @Operation(
            summary = "다국어 메시지 저장",
            description = "생성된 키의 수정·삭제 행을 한 트랜잭션으로 반영하고 메시지 캐시 버전을 올린다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid MessageSaveRequest request) {
        admin.save(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
