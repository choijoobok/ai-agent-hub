// @feat FEAT-509
package com.aiagent.system.api.notirequest;

import com.aiagent.system.api.notirequest.request.NotiRequestHandleRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.notification.request.NotiRequestService;
import com.aiagent.domain.notification.request.NotiRequestQueryService;
import com.aiagent.domain.notification.request.NotiRequestRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code NOTI_REQUEST} — screen/admin/system/noti-request/requirements.md. <b>경로가 둘이고 액션이 둘이다.</b>
 *
 * <table><caption></caption>
 *   <tr><td>{@code getList}</td><td>{@code READ}</td><td>대기열 — §5.2</td></tr>
 *   <tr><td>{@code updateHandle}</td><td><b>{@code SAVE}</b></td><td>처리 · 반려 — §5.3</td></tr>
 * </table>
 *
 * <h2>{@code updateHandle} 이 {@code SAVE} 인 것에 예외가 없다</h2>
 *
 * <p>접두 {@code update} 가 {@code SAVE} 다(backend/api.md). screen/admin/system/user/requirements.md 의 기준(<i>"우리 테이블의
 * 컬럼을 쓰는가"</i>)으로 봐도 그렇다 — 처리는 {@code SYS_NOTI_REQUEST} 의 컬럼 넷을 쓴다.
 *
 * <p><b>{@code BATCH_HISTORY} 의 {@code cancel} 과 갈리는 자리</b>다(screen/admin/system/batch/schedule/requirements.md). 그쪽은
 * 요청만 남기고 실제 종결은 배치가 했으므로 {@code EXECUTE} 였지만, 여기는 <b>이 호출이
 * 끝나면 상태가 끝난 상태</b>다.
 *
 * <h2>처리와 반려가 한 경로다</h2>
 *
 * <p>쓰는 컬럼이 같고 트랜잭션이 같다(screen/admin/system/noti-request/requirements.md). 둘로 가르면 <b>알림 INSERT 코드가 두
 * 벌</b>이 되고 한쪽만 고치는 날이 온다 — {@code reqStatCd} 가 가른다.
 *
 * <h2>사용자 측은 여기 없다</h2>
 *
 * <p>자기 요청의 상태 확인은 알림 모달의 <i>내 요청</i> 탭이며 {@code /api/common/**}
 * 이다(sys/menu.md — <i>"관리자 측은 처리 권한이 필요하므로 메뉴여야 한다. 모달에는 권한을
 * 걸 자리가 없다"</i>).
 */
@RestController
@RequestMapping("/api/system/service/notiRequest")
public class NotiRequestController {

    private final NotiRequestQueryService queries;
    private final NotiRequestService admin;

    public NotiRequestController(
            NotiRequestQueryService queries, NotiRequestService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "요청함 목록 조회",
            description = "요청을 오래된 순으로 반환한다. 대기열이므로 오래 기다린 것이 먼저이며 기본 조건은 접수 상태다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<NotiRequestRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * 처리 · 반려 — <b>섞이면 전부 막는다</b>(screen/admin/system/noti-request/requirements.md).
     *
     * <p>선택에 이미 처리된 것이 하나라도 있으면 아무것도 처리하지 않는다. 일부만
     * 처리하면 <b>알림이 일부에게만 나가고</b> 사용자는 무엇이 처리됐는지 모른다.
     *
     * <p><b>커밋되면 요청자의 알림함에 행이 하나 생긴다</b> — 같은 트랜잭션이다.
     */
    @Operation(
            summary = "요청 처리·반려",
            description = "선택한 요청을 처리 완료 또는 반려로 바꾸고 요청자에게 알림을 넣는다. 반려는 사유가 필수이며 이미 처리된 요청이 섞이면 전부 거부한다.")
    @PostMapping("/updateHandle")
    public ApiResponse<Void> updateHandle(@RequestBody @Valid NotiRequestHandleRequest request) {
        admin.handle(request.targets(), request.reqStatCd(), request.handleMemo());
        return ApiResponse.success(null, TraceId.current());
    }
}
