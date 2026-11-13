// @feat FEAT-501
package com.aiagent.system.api.user;

import com.aiagent.api.auth.SessionPrincipal;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.user.service.UserPreferenceService;
import com.aiagent.system.api.user.request.LocaleRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 자신의 설정 — frontend/tabs.md.
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). <i>"특정 메뉴에 소속시킬 수 없는 것"</i> 이며,
 * 매핑하려 들면 <b>메뉴를 전부 나열하게 된다</b> — 그것이 업무 API 가 아니라는 판별 신호다.
 * 그래서 {@code SYS_API} 시드 대상이 아니고 {@code @Operation} 도 요구되지 않는다.
 *
 * <p><b>남의 설정을 바꿀 수 없다.</b> 대상은 언제나 <b>세션의 주인</b>이며 요청 본문에
 * {@code userId} 를 두지 않는다 — 두는 순간 <i>"누구 것을 바꾸는가"</i> 가 클라이언트가
 * 보낸 값이 되고, 그것을 막는 판정을 이 자리가 또 해야 한다(backend/api.md 이 고정형을 기본으로
 * 둔 것과 같은 이유다).
 */
@RestController
@RequestMapping("/api/system/common/user")
public class UserPreferenceController {

    private final UserPreferenceService preferences;
    private final SessionPrincipal principals;

    public UserPreferenceController(
            UserPreferenceService preferences, SessionPrincipal principals) {
        this.preferences = preferences;
        this.principals = principals;
    }

    /**
     * 언어 변경 — 의 ②.
     *
     * <p><b>서버가 먼저다.</b> 실패하면 프론트가 {@code localStorage} 를 건드리지 않고 멈춘다 —
     * 둘이 갈리면 다음 로그인의 1순위가 어긋난다.
     *
     * <p>저장 뒤 <b>세션 주체의 언어도 함께 갈아 끼운다.</b> 그러지 않으면 재로드 후에도
     * 서버가 만드는 실패 문구만 옛 언어로 나간다(backend/packages.md).
     */
    @PostMapping("/saveLocale")
    public ApiResponse<Void> saveLocale(
            @Valid @RequestBody LocaleRequest request,
            Principal principal,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        preferences.changeLocale(principal.getName(), request.localeCd());
        principals.changeLocale(request.localeCd(), servletRequest, servletResponse);

        return ApiResponse.success(null, TraceId.current());
    }
}
