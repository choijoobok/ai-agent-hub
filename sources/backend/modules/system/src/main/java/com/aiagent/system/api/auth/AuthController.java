// @feat FEAT-501
package com.aiagent.system.api.auth;

import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.auth.LoginResultCode;
import com.aiagent.domain.authz.EffectiveAuthority;
import com.aiagent.domain.authz.service.MenuTreeService;
import com.aiagent.domain.message.service.CacheVersionService;
import com.aiagent.domain.user.entity.SysUser;
import com.aiagent.domain.user.service.PasswordChangeService;
import com.aiagent.domain.user.service.PasswordResetService;
import com.aiagent.domain.user.service.UserAccountService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.mail.Mailer;
import com.aiagent.support.config.props.SearchRangeProps;
import com.aiagent.support.config.props.UiDateFormatProps;
import com.aiagent.support.config.props.UiExcelProps;
import com.aiagent.system.api.auth.request.LoginRequest;
import com.aiagent.system.api.auth.request.PasswordChangeRequest;
import com.aiagent.system.api.auth.request.ResetCodeRequest;
import com.aiagent.system.api.auth.request.ResetPasswordRequest;
import com.aiagent.system.api.auth.request.ResetVerifyRequest;
import com.aiagent.system.api.auth.response.HeartbeatResponse;
import com.aiagent.system.api.auth.response.LoginResponse;
import com.aiagent.system.api.auth.response.LoginUserResponse;
import com.aiagent.system.api.auth.response.PasswordPolicyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AuthController {

    private final com.aiagent.api.error.ApiErrorWriter errors;
    private final LoginService loginService;
    private final UserAccountService userAccountService;
    private final MenuTreeService menuTreeService;
    private final CacheVersionService cacheVersionService;
    private final PasswordChangeService passwordChangeService;
    private final PasswordResetService passwordResetService;
    private final PasswordPolicies passwordPolicies;
    private final Mailer mailer;
    private final ConfigLookup configs;

    public AuthController(
            LoginService loginService,
            UserAccountService userAccountService,
            MenuTreeService menuTreeService,
            CacheVersionService cacheVersionService,
            PasswordChangeService passwordChangeService,
            PasswordResetService passwordResetService,
            PasswordPolicies passwordPolicies,
            Mailer mailer,
            ConfigLookup configs, com.aiagent.api.error.ApiErrorWriter errors) {
        this.errors = errors;
        this.loginService = loginService;
        this.userAccountService = userAccountService;
        this.menuTreeService = menuTreeService;
        this.cacheVersionService = cacheVersionService;
        this.passwordChangeService = passwordChangeService;
        this.passwordResetService = passwordResetService;
        this.passwordPolicies = passwordPolicies;
        this.mailer = mailer;
        this.configs = configs;
    }

    /** 실제 CSRF 발급은 공통 필터가 같은 정확 경로에서 처리한다. */
    @GetMapping("/api/system/public/auth/getCsrf")
    public ApiResponse<Void> getCsrf() {
        throw new IllegalStateException("공통 인증 필터를 거쳐야 한다");
    }

    @PostMapping("/api/system/public/auth/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) throws java.io.IOException {
        var result = loginService.attempt(request, servletRequest, servletResponse);
        if (result.rejection() != null) { errors.write(servletResponse, result.rejection()); return null; }
        return ApiResponse.success(result.response(), TraceId.current());
    }


    @GetMapping("/api/system/public/auth/getPasswordPolicy")
    public ApiResponse<PasswordPolicyResponse> getPasswordPolicy() {
        return ApiResponse.success(passwordPolicies.current(), TraceId.current());
    }


    @PostMapping("/api/system/public/auth/sendResetCode")
    public ApiResponse<Void> sendResetCode(@Valid @RequestBody ResetCodeRequest request) {
        passwordResetService
                .issue(request.loginId())
                .ifPresent(mail -> AdminRequestContext.afterCommit(() -> mailer.send(mail.to(), mail.subject(), mail.body())));
        return ApiResponse.success(null, TraceId.current());
    }


    @PostMapping("/api/system/public/auth/verifyResetCode")
    public ApiResponse<Void> verifyResetCode(@Valid @RequestBody ResetVerifyRequest request) {
        passwordResetService.verify(request.loginId(), request.resetCode());
        return ApiResponse.success(null, TraceId.current());
    }


    @PostMapping("/api/system/public/auth/saveResetPassword")
    public ApiResponse<Void> saveResetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.complete(
                request.loginId(), request.resetCode(), request.newPwd());
        loginService.credentialsChanged(java.util.List.of(request.loginId()));
        return ApiResponse.success(null, TraceId.current());
    }


    @GetMapping("/api/system/common/auth/getMe")
    public ApiResponse<LoginResponse> me(Principal principal, HttpServletRequest request) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        SysUser user = userAccountService
                .find(principal.getName())
                // 세션은 살아 있는데 계정이 사라졌다 — 관리자가 지운 경우다. 세션을 믿지 않는다.
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));

        EffectiveAuthority authority = currentAuthority(request);
        return ApiResponse.success(
                LoginResponse.of(
                        new LoginUserResponse(user.getUserId(), user.getUserNm(), user.getLocaleCd()),
                        passwordChangeRequired(request),
                        authority,
                        menuTreeService.build(authority),
                        cacheVersionService.all(),
                        loginService.timeoutSeconds(),
                        configs.get(UiExcelProps.class).maxRows(),
                        configs.get(UiExcelProps.class).maxUploadRows(),
                        configs.get(SearchRangeProps.class).defaultDays(),
                        configs.get(SearchRangeProps.class).maxDays(),
                        dateFormat()),
                TraceId.current());
    }


    private LoginResponse.DateFormatResponse dateFormat() {
        UiDateFormatProps format = configs.get(UiDateFormatProps.class);
        return new LoginResponse.DateFormatResponse(
                format.month(), format.day(), format.second());
    }


    @PostMapping("/api/system/common/auth/heartbeat")
    public ApiResponse<HeartbeatResponse> heartbeat(HttpServletRequest request) {
        return ApiResponse.success(
                new HeartbeatResponse(loginService.timeoutSeconds()), TraceId.current());
    }


    @PostMapping("/api/system/common/auth/changePassword")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            Principal principal,
            HttpServletRequest servletRequest) {

        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        passwordChangeService.changePassword(
                principal.getName(), request.currentPwd(), request.newPwd());

        loginService.rotatePassword(userAccountService.find(principal.getName())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED)));
        return ApiResponse.success(null, TraceId.current());
    }

    @PostMapping("/api/system/common/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        // traceId 를 먼저 읽는다 — 세션 무효화 뒤에도 응답 봉투는 채워져야 한다.
        String traceId = TraceId.current();
        loginService.logout(request, LoginResultCode.LOGOUT);
        return ApiResponse.success(null, traceId);
    }


    private static EffectiveAuthority currentAuthority(HttpServletRequest request) {
        return AdminRequestContext.authority();
    }

    private static boolean passwordChangeRequired(HttpServletRequest request) {
        return AdminRequestContext.scope(request).current().orElseThrow().identity().authLevel()
                == AuthLevel.PASSWORD_CHANGE_REQUIRED;
    }
}
