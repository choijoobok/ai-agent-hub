// @feat FEAT-501
package com.aiagent.system.api.user;

import com.aiagent.api.trace.TraceId;
import com.aiagent.system.api.user.request.UserIpSaveRequest;
import com.aiagent.system.api.user.request.UserSessionKillRequest;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.user.detail.UserDetailQueryService;
import com.aiagent.domain.user.detail.UserDetailService;
import com.aiagent.domain.user.detail.UserIdParams;
import com.aiagent.domain.user.detail.UserIpRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code USER_MGMT} 디테일 중 접속 IP·활성 세션 — screen/admin/system/user/requirements.md. <b>경로가 넷이다.</b>
 *
 * <table>
 *   <tr><th>탭</th><th>경로</th><th>액션</th></tr>
 *   <tr><td>3 접속 IP</td><td>{@code getIpList} · {@code saveIp}</td><td>{@code R} {@code S}</td></tr>
 *   <tr><td>4 활성 세션</td><td>{@code getSessionList} · {@code killSession}</td><td>{@code R} {@code X}</td></tr>
 * </table>
 *
 * <h2>이 화면의 {@code EXECUTE} 가 여기서 처음 실물이 된다</h2>
 *
 * <p>{@code UserController} 가 <i>"{@code X} 에 대응하는 경로도 아직 없다 — 활성 세션
 * 탭이 서는 슬라이스가 가져온다"</i> 고 적어 둔 자리다. {@code killSession} 은 접두 규칙의
 * <i>그 외</i> 에 걸려 {@code EXECUTE} 가 되고(backend/api.md), 그 판정이 screen/admin/system/user/requirements.md 의 기준과
 * 일치한다 — <b>우리 테이블의 컬럼을 쓰지 않는다.</b> 세션 스토어를 비울 뿐이며
 * {@code SYS_USER} 는 그대로다.
 *
 * <p>그래서 {@code saveUnlock} 과 갈린다. 저것은 {@code LOCK_YN} 을 쓰므로 이름이
 * {@code unlock} 이면 안 됐고, 이것은 쓰지 않으므로 {@code killSession} 이 맞다 —
 * <b>규칙을 넓히지 않고 경로를 규칙에 맞춘다.</b>
 *
 * <h2>권한 탭과 나눈 과도기 컨트롤러다</h2>
 *
 * <p>보유 역할·개인 예외 조회·유효 권한은 system 경계의 {@code UserAuthorizationController}가
 * 맡는다(FEAT-502). 이 컨트롤러는 접속 IP·활성 세션을 같은 system 정식 경로에서 맡는다.
 *
 * <p><b>활성 세션만 도메인 서비스가 아니다</b>({@link UserSessionService}) — 원천이 DB 가
 * 아니라 세션 스토어라 {@code api} 의 것이며, 도메인이 그것을 알면 backend/packages.md 이 갈린다.
 */
@RestController
@RequestMapping("/api/system/service/user")
public class UserDetailController {

    private final UserDetailQueryService queries;
    private final UserDetailService detail;
    private final UserSessionService sessions;

    public UserDetailController(
            UserDetailQueryService queries,
            UserDetailService detail,
            UserSessionService sessions) {
        this.queries = queries;
        this.detail = detail;
        this.sessions = sessions;
    }

    // ── 탭 3 — 접속 IP (AUTH-08) ────────────────────────────────────

    @Operation(
            summary = "접속 허용 IP 조회",
            description = "한 사용자의 허용 대역을 반환한다. 꺼진 행도 포함하며, 행이 하나도 없으면 그 계정에는 제한이 없다.")
    @PostMapping("/getIpList")
    public ApiResponse<PageResponse<UserIpRow>> getIpList(
            @RequestBody @Valid SearchRequest<UserIdParams> request) {
        return ApiResponse.success(queries.getIpList(request), TraceId.current());
    }

    /**
     * 허용 대역 저장 — <b>세션을 건드리지 않는다</b>.
     *
     * <p>{@code saveRole} 과 갈리는 자리다. 허용 IP 는 <b>인가가 아니라 인증의 검사</b>이며
     * (sys/user-role.md — 로그인 후처리 핸들러 체인) 세션에 캐시되는 유효 권한과 아무 관계가 없다 —
     * 다음 <b>로그인</b>에서 읽히지 다음 <b>요청</b>에서 읽히지 않는다.
     */
    @Operation(
            summary = "접속 허용 IP 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영한다. 형식이 IP 또는 CIDR 이어야 하며 전부 지우면 제한 없음이 된다.")
    @PostMapping("/saveIp")
    public ApiResponse<Void> saveIp(@RequestBody @Valid UserIpSaveRequest request) {
        detail.saveIps(request.userId(), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    // ── 탭 4 — 활성 세션 (AUTH-10) ──────────────────────────────────

    @Operation(
            summary = "활성 세션 조회",
            description = "한 사용자의 살아 있는 세션을 최근 접근이 늦은 것부터 반환한다. 이 요청의 세션에는 표시가 붙는다.")
    @PostMapping("/getSessionList")
    public ApiResponse<PageResponse<UserSessionRow>> getSessionList(
            @RequestBody @Valid SearchRequest<UserIdParams> request) {

        return ApiResponse.success(
                PageResponse.all(sessions.list(request.params().userId())),
                TraceId.current());
    }

    /**
     * 강제 로그아웃 — <b>이 화면의 유일한 {@code EXECUTE}</b>(sys/menu.md 의 {@code X}).
     *
     * <p><b>자기 계정은 막는다</b>(screen/admin/system/user/requirements.md). 그 자리에서 화면 밖으로 밀려나기 때문이며,
     * 되돌릴 사람이 그 사람 자신이다 — 화면도 버튼을 비활성으로 두고 사유를 붙이지만,
     * 요청 본문은 조작할 수 있으므로 서버가 다시 본다(sys/menu.md 원칙 P1).
     *
     * <p><b>끊어도 {@code SYS_USER} 는 바뀌지 않는다</b> — 다시 로그인할 수 있다. 막으려면
     * {@code USE_YN='N'} 이고 그것은 <b>다른 축</b>이다(sys/user-role.md).
     */
    @Operation(
            summary = "세션 강제 종료",
            description = "고른 세션을 스토어에서 지운다. 자기 계정의 세션은 거부하며 이미 끊긴 세션은 건너뛴다. 계정 자체는 바뀌지 않는다.")
    @PostMapping("/killSession")
    public ApiResponse<Integer> killSession(
            @RequestBody @Valid UserSessionKillRequest request, Principal principal) {

        if (request.userId().equals(userId(principal))) {
            throw new BusinessException(
                    UserSessionErrorCode.SELF, "자기 세션은 끊을 수 없다 — " + request.userId());
        }
        return ApiResponse.success(
                sessions.kill(request.userId(), request.targets()), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
