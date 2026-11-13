// @feat FEAT-501
package com.aiagent.system.api.user;

import com.aiagent.api.trace.TraceId;
import com.aiagent.system.api.auth.LoginService;
import com.aiagent.system.api.user.request.UserSaveRequest;
import com.aiagent.system.api.user.request.UserTargetRequest;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.user.admin.IssuedPassword;
import com.aiagent.domain.user.admin.UserQueryService;
import com.aiagent.domain.user.admin.UserService;
import com.aiagent.domain.user.admin.UserRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code USER_MGMT} 의 마스터 — screen/admin/system/user/requirements.md. <b>경로가 다섯이고 넷이 {@code save*} 다.</b>
 *
 * <table>
 *   <tr><td>{@code getList}</td><td>목록 — <b>곧 상세다</b>({@code getDetail} 이 없는 이유)</td></tr>
 *   <tr><td>{@code save}</td><td>셀 편집 일괄 저장 — <b>관리자 축 둘</b>과 이름·이메일·언어</td></tr>
 *   <tr><td>{@code saveUnlock}</td><td>잠금 해제 — AUTH-04</td></tr>
 * <tr><td>{@code saveDormantRelease}</td><td>휴면 해제 — sys/user-role.md</td></tr>
 *   <tr><td>{@code savePasswordReset}</td><td>임시 비밀번호 발급 — AUTH-07</td></tr>
 * </table>
 *
 * <h2>{@code unlock} 이 아니라 {@code saveUnlock} 이다</h2>
 *
 * <p>screen/admin/system/user/requirements.md — <b>규칙을 넓히지 않고 경로를 규칙에 맞춘다</b>(backend/api.md). 순진하게
 * {@code unlock} 으로 쓰면 접두가 {@code EXECUTE} 로 떨어지는데, 이 API 는
 * {@code LOCK_YN}·{@code LOGIN_FAIL_CNT} 두 컬럼을 <b>쓴다</b> — 저장이다.
 *
 * <p>기준은 <i>버튼이 위험해 보이는가</i> 가 아니라 <b>우리 테이블의 컬럼을 쓰는가</b>이며,
 * 그래서 이 화면의 {@code EXECUTE} 는 <b>강제 로그아웃 하나</b>다(sys/menu.md 의 {@code X}) —
 * 그것은 세션 스토어를 비우지 우리 테이블을 쓰지 않고, 활성 세션 탭에 붙는다(screen/admin/system/user/requirements.md).
 *
 * <h2>{@code exportExcel} 이 없다</h2>
 *
 * <p>이 화면의 액션 열은 {@code R S E X} 인데(sys/menu.md) <b>{@code E} 에 대응하는 경로가
 * 없다</b> — 엑셀 두 갈래는 프론트가 만들고(playbook/excel-download.md) {@code EXPORT} 는
 * {@code SYS_MENU.EXPORT_YN} 이 선언한다(sys/menu.md). 056 이 이 메뉴에 이미 {@code 'Y'} 를
 * 넣어 두었으므로 <b>스위치는 화면이 서기 전부터 서 있었다</b>.
 *
 * <h2>저장이 값을 돌려준다</h2>
 *
 * <p>다른 화면의 {@code save} 는 {@code Void} 다. 여기만 다른 것은 <b>신규 계정의 임시
 * 비밀번호</b> 때문이며({@link IssuedPassword}), 그 값은 <b>이 응답에만</b> 있다 — DB 에는
 * 해시가 들어가 재조회로 다시 얻을 수 없다.
 */
@RestController
@RequestMapping("/api/system/service/user")
public class UserController {
    private final LoginService login;

    private final UserQueryService queries;
    private final UserService admin;

    public UserController(LoginService login, UserQueryService queries, UserService admin) {
        this.login = login;
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "사용자 목록 조회",
            description = "계정을 페이지 단위로 반환한다. 접근 제어 체인으로 계산한 계정 상태가 함께 실리며 원본 네 컬럼도 같이 온다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<UserRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * 셀 편집 저장 — <b>신규 행은 임시 비밀번호를 함께 받는다</b>(screen/admin/system/user/requirements.md).
     *
     * <p>{@code principal} 을 넘기는 것은 <b>자기 비활성화를 막기 위해서</b>다(screen/admin/system/user/requirements.md).
     * 도메인이 <i>지금 누가 요청했는가</i> 를 스스로 알면 계층이 뒤집힌다(backend/packages.md) — 감사 4컬럼을
     * 채우는 {@code AuditorAware} 와 <b>다른 자리</b>인 것은, 저것이 <i>기록</i> 이고 이것은
     * <b>판정</b>이기 때문이다.
     */
    @Operation(
            summary = "사용자 저장",
            description = "추가·수정 행을 한 트랜잭션으로 반영한다. 삭제는 받지 않으며 신규 계정의 임시 비밀번호를 돌려준다.")
    @PostMapping("/save")
    public ApiResponse<List<IssuedPassword>> save(
            @RequestBody @Valid UserSaveRequest request, Principal principal) {
        return ApiResponse.success(admin.save(request.rows(), userId(principal)), TraceId.current());
    }

    @Operation(
            summary = "계정 잠금 해제",
            description = "연속 로그인 실패로 잠긴 계정을 푼다. 잠금 플래그와 실패 횟수만 쓰고 사용 여부·휴면은 건드리지 않는다.")
    @PostMapping("/saveUnlock")
    public ApiResponse<Void> saveUnlock(@RequestBody @Valid UserTargetRequest request) {
        admin.unlock(request.targets());
        return ApiResponse.success(null, TraceId.current());
    }

    @Operation(
            summary = "휴면 해제",
            description = "배치가 건 휴면 플래그를 관리자가 내린다. 잠금은 함께 풀리지 않는다.")
    @PostMapping("/saveDormantRelease")
    public ApiResponse<Void> saveDormantRelease(@RequestBody @Valid UserTargetRequest request) {
        admin.releaseDormant(request.targets());
        return ApiResponse.success(null, TraceId.current());
    }

    /**
     * 임시 비밀번호 발급 — <b>값을 응답에 담는다</b>.
     *
     * <p>screen/admin/system/user/requirements.md 이 <i>"메일 발송이 없는 배포에서 화면에 표시할지"</i> 를 미결로 남겼고
     * <b>표시하는 쪽으로 닫았다</b>({@link IssuedPassword}) — sys/code.md 이 이 기능을 <i>"잠긴
     * 계정을 푸는 유일한 경로"</i> 라 부른 이상 전달 수단이 없으면 경로가 아니다.
     *
     * <p><b>사용 이력에 값이 남지 않는다.</b> {@code UserActionLogInterceptor} 가 기록하는
     * 것은 <b>요청 본문</b>이고(sys/logs.md) 여기서 값이 실리는 곳은 <b>응답</b>이다 — 우연이
     * 아니라 이 경계가 그렇게 생겼다: 비밀번호를 <i>받는</i> API 는 마스킹이 필요하고,
     * <i>주는</i> API 는 그 문제가 애초에 없다.
     */
    @Operation(
            summary = "임시 비밀번호 발급",
            description = "정책을 충족하는 임시 비밀번호를 만들어 계정에 넣고 강제 변경 상태로 표시한다. 잠금은 풀리지 않는다.")
    @PostMapping("/savePasswordReset")
    public ApiResponse<List<IssuedPassword>> savePasswordReset(
            @RequestBody @Valid UserTargetRequest request) {
        var issued = admin.resetPassword(request.targets());
        login.credentialsChanged(request.targets());
        return ApiResponse.success(issued, TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
