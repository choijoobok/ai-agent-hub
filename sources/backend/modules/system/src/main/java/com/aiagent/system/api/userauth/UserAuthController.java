// @feat FEAT-502
package com.aiagent.system.api.userauth;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.system.api.userauth.request.UserAuthSaveRequest;
import com.aiagent.system.api.userauth.request.UserAuthTreeRequest;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.authz.userauth.UserAuthQueryService;
import com.aiagent.domain.authz.userauth.UserAuthService;
import com.aiagent.domain.authz.userauth.UserAuthTreeRow;
import com.aiagent.domain.authz.userauth.UserPickRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code USER_AUTH_MGMT} — screen/admin/system/user-auth/requirements.md. <b>경로가 셋이다.</b>
 *
 * <table>
 *   <tr><td>{@code getList}</td><td>좌 pane — 사용자 목록. <b>읽기 전용</b>(계정 생성은 {@code USER_MGMT})</td></tr>
 *   <tr><td>{@code getMenuTree}</td><td>우 + 하 — <b>한 응답이 트리와 예외 목록을 함께 먹인다</b></td></tr>
 *   <tr><td>{@code save}</td><td>저장 — 트리와 목록이 <b>같은 데이터의 두 표시</b>라 API 도 하나다</td></tr>
 * </table>
 *
 * <h2>왜 예외 목록에 경로를 따로 두지 않았나</h2>
 *
 * <p>screen/admin/system/user-auth/requirements.md 가 트리와 목록을 <i>"같은 데이터의 두 표시"</i> 로 정했다. 표시가 둘이라고 원본이
 * 둘이 되면 <b>같은 질문에 답이 둘</b>이 되고, 그 둘이 갈리는 순간 <i>"트리에는 있는데 목록에
 * 없다"</i> 가 된다 — {@code ROLE_MGMT} 의 하단이 {@code roleAuth/getAffectedUsers} 를 그대로
 * 부르는 것과 <b>같은 규칙</b>이다(screen/menu/requirements.md).
 *
 * <p>그래서 {@link UserAuthTreeRow} 가 그 메뉴의 개인 예외를 <b>기간과 사유까지</b> 담아 오고,
 * 화면이 그것을 펴서 하단 그리드를 만든다.
 *
 * <h2>{@code USER_MGMT} 의 탭 2 와는 경로를 나눈다</h2>
 *
 * <p>screen/admin/system/user-auth/requirements.md 가 <i>"조회 API 를 공유하지 않는다 — 반환 형태가 다르다"</i> 고 정했다. 거기는
 * <b>목록</b>이고 여기는 <b>트리 체크 상태를 함께 낸다</b>. backend/security.md 가 <i>"화면마다 허용 범위가
 * 달라야 한다면 그것은 분리해야 할 두 API"</i> 라고 한 것과 방향이 같다 — <b>편집을 탭 2 에
 * 두지 않는 것</b>이 그 분리의 이유다: 사용자 관리 권한이 있다고 <b>예외 권한 부여 권한이
 * 따라오면 안 된다</b>.
 *
 * <h2>{@code exportExcel} 이 없다</h2>
 *
 * <p>액션 열이 {@code R S E} 인데(sys/menu.md) {@code E} 에 대응하는 경로가 없다 — 엑셀은 프론트가
 * 만들고(playbook/excel-download.md) {@code EXPORT} 는 {@code SYS_MENU.EXPORT_YN} 이 선언한다(sys/menu.md).
 */
@RestController
@RequestMapping("/api/system/service/userAuth")
public class UserAuthController {

    private final UserAuthQueryService queries;
    private final UserAuthService admin;
    private final RequestLocale locale;

    public UserAuthController(
            UserAuthQueryService queries,
            UserAuthService admin,
            RequestLocale locale) {
        this.queries = queries;
        this.admin = admin;
        this.locale = locale;
    }

    /**
     * 좌 pane — <b>못 쓰는 계정도 나온다</b>(screen/admin/system/user-auth/requirements.md · {@code UserAuthSpec}).
     *
     * <p>screen/menu/requirements.md 가 비활성 역할을 목록에 둔 것과 같은 논리다 — 못 쓰는 계정의 권한을 고치는
     * 것은 아무에게도 영향이 없어 <b>가장 안전한 편집</b>이고, 감추면 <i>계정을 살리기 전에
     * 권한을 구성해 두는</i> 동선이 막힌다. 대신 <b>상태를 컬럼으로</b> 낸다.
     */
    @Operation(
            summary = "사용자 목록 조회",
            description = "개인 예외를 부여할 사용자를 고르는 목록이다. 못 쓰는 계정도 함께 반환하며 계정 상태가 실린다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<UserPickRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    @Operation(
            summary = "개인 예외 권한 트리 조회",
            description = "메뉴 계층 전건과 이 사용자의 개인 예외를 기간·사유까지 반환한다. 역할로 이미 갖고 있는 액션도 함께 실린다.")
    @PostMapping("/getMenuTree")
    public ApiResponse<PageResponse<UserAuthTreeRow>> getMenuTree(
            @RequestBody @Valid UserAuthTreeRequest request) {
        return ApiResponse.success(
                queries.getMenuTree(request.userId(), locale.current()), TraceId.current());
    }

    /**
     * 저장 — <b>세션 무효화가 뒤따르되 대상이 한 사람</b>이다.
     *
     * <p>역할 권한을 고치면 <b>보유자 전원</b>의 유효 권한이 바뀌지만(screen/menu/requirements.md), 개인 예외는
     * 정의상 <b>그 사람 하나</b>다. 그래서 여기서 세우는 표시도 하나이며, 그것이 개인 예외를
     * <i>예외</i> 라 부르는 이유의 데이터 쪽 모습이다.
     *
     * <p><b>순서가 규약이다.</b> 저장이 커밋된 <i>뒤에</i> 표시를 세운다 — 먼저 세우면 롤백된
     * 변경에 대해 재계산이 돌고, 그 계산은 옛 값을 다시 읽어 <b>이유 없는 부하</b>가 된다
     * ({@code RoleController} 와 같은 규약).
     */
    @Operation(
            summary = "개인 예외 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영하고, 그 사용자의 세션에 권한 재계산을 예약한다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(
            @RequestBody @Valid UserAuthSaveRequest request,
            Principal principal) {

        admin.save(request.userId(), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
