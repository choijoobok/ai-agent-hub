// @feat FEAT-501
package com.aiagent.system.api.user;

import com.aiagent.api.auth.SessionPrincipal;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.auth.MyLoginRecord;
import com.aiagent.domain.auth.service.LoginHistoryService;
import com.aiagent.domain.authz.MyAuthority;
import com.aiagent.domain.authz.service.MyAuthorityService;
import com.aiagent.domain.user.MyProfile;
import com.aiagent.domain.user.service.UserProfileService;
import com.aiagent.system.api.user.request.ProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필 모달의 네 탭 — USER-05 · AUTH-05 · LOG-02 · AUTHZ-04(frontend/tabs.md).
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). 전부 <i>"나에 대한 것"</i> 이라 특정 메뉴에
 * 소속시킬 수 없고, 매핑하려 들면 <b>메뉴를 전부 나열하게 된다</b> — 그것이 업무 API 가
 * 아니라는 판별 신호다. 그래서 {@code SYS_API} 시드 대상이 아니다.
 *
 * <p><b>비밀번호 변경만 여기 없다.</b> 그것은 {@code /api/system/common/auth/changePassword} 이며
 * <b>제한 세션에서도 열려 있어야 하는 유일한 업무 경로</b>다(체인 #5 는 차단이 아니라
 * 유도다 — backend/security.md). 그 화이트리스트는 인가 인터셉터가 갖고 있고 경로 문자열로 비교하므로,
 * 편의로 이 컨트롤러에 옮겨 두면 <b>비밀번호가 만료된 사용자가 막다른 세션에 갇힌다</b>.
 *
 * <p><b>탭마다 호출이 하나다.</b> 넷을 한 번에 내리지 않는 이유는 열자마자 로그인 이력과
 * 권한까지 읽게 되기 때문이다 — 대부분은 첫 탭만 보고 닫는다. 정책({@code PasswordRule})
 * 만 예외로 첫 응답에 실린다: <b>규칙을 모른 채 거절당하면 맞출 수 없다.</b>
 */
@RestController
@RequestMapping("/api/system/common/user")
public class UserProfileController {

    private final UserProfileService profiles;
    private final LoginHistoryService loginHistory;
    private final MyAuthorityService authorities;
    private final SessionPrincipal principals;

    public UserProfileController(
            UserProfileService profiles,
            LoginHistoryService loginHistory,
            MyAuthorityService authorities,
            SessionPrincipal principals) {
        this.profiles = profiles;
        this.loginHistory = loginHistory;
        this.authorities = authorities;
        this.principals = principals;
    }

    /** 내 정보 탭 — 조회와 수정이 한 화면이라 이 응답이 편집 폼의 초기값이기도 하다(frontend/tabs.md). */
    @GetMapping("/getMyProfile")
    public ApiResponse<MyProfile> getMyProfile(Principal principal) {
        return ApiResponse.success(profiles.getMyProfile(userId(principal)), TraceId.current());
    }

    /**
     * <b>세션 주체의 이름도 함께 갈아 끼운다.</b> DB 만 고치면 헤더가 세션이 끝날 때까지
     * 옛 이름을 보인다 — {@code auth/me} 가 읽는 것은 DB 가 아니라 주체다(frontend/tabs.md).
     *
     * <p>바뀐 프로필을 그대로 돌려준다. 화면이 저장 뒤 다시 조회하지 않아도 되고, 서버가
     * 다듬은 값(공백 제거 · 빈 메일의 {@code null} 화)이 무엇인지 그 자리에서 보인다.
     */
    @PostMapping("/saveMyProfile")
    public ApiResponse<MyProfile> saveMyProfile(
            @Valid @RequestBody ProfileRequest request,
            Principal principal,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String userId = userId(principal);
        profiles.saveMyProfile(userId, request.userNm(), request.email());

        MyProfile saved = profiles.getMyProfile(userId);
        principals.changeUserNm(saved.userNm(), servletRequest, servletResponse);

        return ApiResponse.success(saved, TraceId.current());
    }

    /**
     * 내 로그인 이력 탭 — LOG-02. <b>보안 기능이다</b>(frontend/tabs.md).
     *
     * <p>실패 기록을 포함한다 — 누군가 내 계정으로 시도했다는 사실이 그 자체로 신호이며,
     * 성공만 보여주면 이 탭이 있을 이유가 없다.
     */
    @GetMapping("/getMyLoginHistory")
    public ApiResponse<List<MyLoginRecord>> getMyLoginHistory(Principal principal) {
        return ApiResponse.success(loginHistory.findMine(userId(principal)), TraceId.current());
    }

    /** 내 역할 탭 — AUTHZ-04. 읽기 전용이며, 권한을 늘리는 경로는 신청(NOTI-06)이다. */
    @GetMapping("/getMyRoles")
    public ApiResponse<MyAuthority> getMyRoles(Principal principal) {
        return ApiResponse.success(
                authorities.getMyAuthority(userId(principal)), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
