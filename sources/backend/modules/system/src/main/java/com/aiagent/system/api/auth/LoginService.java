// @feat FEAT-501
package com.aiagent.system.api.auth;

import com.aiagent.api.auth.AuthErrorCode;
import com.aiagent.api.auth.LoginContexts;
import com.aiagent.api.authz.AuthzVersionHeader;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.ErrorCode;
import com.aiagent.domain.auth.LoginResultCode;
import com.aiagent.domain.authz.EffectiveAuthority;
import com.aiagent.domain.authz.service.EffectiveAuthorityService;
import com.aiagent.domain.authz.service.MenuTreeService;
import com.aiagent.domain.message.service.CacheVersionService;
import com.aiagent.domain.auth.service.LoginHistoryService;
import com.aiagent.domain.auth.service.LoginHistoryService.LoginContext;
import com.aiagent.domain.user.entity.SysUser;
import com.aiagent.domain.user.service.UserAccountService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthPasswordProps;
import com.aiagent.support.config.props.SearchRangeProps;
import com.aiagent.support.config.props.UiDateFormatProps;
import com.aiagent.support.config.props.UiExcelProps;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.system.api.auth.check.LoginCandidate;
import com.aiagent.system.api.auth.check.LoginPostCheck;
import com.aiagent.system.api.auth.request.LoginRequest;
import com.aiagent.system.api.auth.response.LoginResponse;
import com.aiagent.system.api.auth.response.LoginUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.time.Instant;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionAdmissionsV2;
import com.aiagent.core.security.v2.SessionV2;
import com.aiagent.api.auth.v2.SessionRuntime;
import com.aiagent.support.config.props.ConcurrentSessionProps;
import org.springframework.core.env.Environment;
import java.util.Map;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class LoginService {


    private static final Map<Class<? extends RuntimeException>, LoginResultCode> EXCEPTION_TO_RESULT =
            Map.of(
                    UsernameNotFoundException.class, LoginResultCode.FAIL_NO_USER,
                    BadCredentialsException.class, LoginResultCode.FAIL_PWD,
                    DisabledException.class, LoginResultCode.FAIL_DISABLED,
                    AccountExpiredException.class, LoginResultCode.FAIL_EXPIRED,
                    LockedException.class, LoginResultCode.FAIL_LOCKED);


    private static final Map<LoginResultCode, AuthErrorCode> RESULT_TO_ERROR = Map.of(
            LoginResultCode.FAIL_NO_USER, AuthErrorCode.LOGIN_FAILED,
            LoginResultCode.FAIL_PWD, AuthErrorCode.LOGIN_FAILED,
            LoginResultCode.FAIL_DISABLED, AuthErrorCode.DISABLED,
            LoginResultCode.FAIL_EXPIRED, AuthErrorCode.EXPIRED,
            LoginResultCode.FAIL_LOCKED, AuthErrorCode.LOCKED,
            LoginResultCode.FAIL_DORMANT, AuthErrorCode.DORMANT,
            LoginResultCode.FAIL_IP, AuthErrorCode.IP_DENIED,
            LoginResultCode.FAIL_NO_AUTHORITY, AuthErrorCode.NO_AUTHORITY,
            LoginResultCode.FAIL_TEMP_EXPIRED, AuthErrorCode.TEMP_EXPIRED,
            LoginResultCode.FAIL_CONCURRENT, AuthErrorCode.CONCURRENT_LOGIN);

    private final AuthenticationManager authenticationManager;
    private final UserAccountService userAccountService;
    private final EffectiveAuthorityService effectiveAuthorityService;
    private final MenuTreeService menuTreeService;
    private final CacheVersionService cacheVersionService;
    private final LoginHistoryService loginHistoryService;
    private final List<LoginPostCheck> postChecks;
    private final SessionAdmissionsV2.Producer producer;
    private final int timeoutSeconds;
    private final ConfigLookup configs;
    private final BusinessCalendar calendar;

    public LoginService(
            AuthenticationManager authenticationManager,
            UserAccountService userAccountService,
            EffectiveAuthorityService effectiveAuthorityService,
            MenuTreeService menuTreeService,
            CacheVersionService cacheVersionService,
            LoginHistoryService loginHistoryService,
            List<LoginPostCheck> postChecks,
            SessionAdmissionsV2.Producer producer,
            Environment environment,
            ConfigLookup configs,
            BusinessCalendar calendar) {
        this.authenticationManager = authenticationManager;
        this.userAccountService = userAccountService;
        this.effectiveAuthorityService = effectiveAuthorityService;
        this.menuTreeService = menuTreeService;
        this.cacheVersionService = cacheVersionService;
        this.loginHistoryService = loginHistoryService;
        this.postChecks = postChecks;
        this.producer = producer;
        this.timeoutSeconds = environment.getProperty("app.auth.session.timeout-minutes", Integer.class, 30) * 60;
        this.configs = configs;
        this.calendar = calendar;
    }

    public record Attempt(LoginResponse response, ErrorCode rejection) {}
    /** 거부도 정상 결과로 반환한다. 필터는 횟수/잠금 커밋 뒤 버퍼의 HTTP401을 내보낸다. */
    public Attempt attempt(LoginRequest form, HttpServletRequest request, HttpServletResponse response) {
        try { return new Attempt(login(form, request, response), null); }
        catch (BusinessException rejected) { return new Attempt(null, rejected.errorCode()); }
    }

    public LoginResponse login(
            LoginRequest form, HttpServletRequest request, HttpServletResponse response) {

        LoginContext context = LoginContexts.of(request);
        Authentication authentication;
        boolean mustChangePassword = false;

        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            form.loginId(), form.password()));
        } catch (CredentialsExpiredException e) {
            // 체인 #5. 비밀번호는 맞았으므로 제한 세션으로 들여보낸다(backend/security.md).
            mustChangePassword = true;
            authentication = restrictedAuthentication(form.loginId());
        } catch (BadCredentialsException e) {
            userAccountService.recordLoginFailure(form.loginId());
            throw fail(form.loginId(), LoginResultCode.FAIL_PWD, context);
        } catch (RuntimeException e) {
            LoginResultCode result = EXCEPTION_TO_RESULT.get(e.getClass());
            if (result == null) {
                throw e;
            }
            throw fail(form.loginId(), result, context);
        }

        SysUser user = userAccountService
                .find(form.loginId())
                .orElseThrow(() -> fail(form.loginId(), LoginResultCode.FAIL_NO_USER, context));

        // 임시 비밀번호 방치 — 발급받고 temp-valid-hours 를 넘긴 계정은 들여보내지 않는다(backend/security.md).
        if (user.isTemporaryPasswordExpired(
                calendar.now(), configs.get(AuthPasswordProps.class).tempValidHours())) {
            throw fail(form.loginId(), LoginResultCode.FAIL_TEMP_EXPIRED, context);
        }

        // AUTH-14 후처리 체인 — 플래그로 커버되지 않는 검사들(sys/user-role.md).
        //
        // 유효 권한은 후보 객체가 지연 계산한다. 체인의 마지막 검사(공집합)가 부르거나,
        // 그것이 꺼져 있으면 아래 세션 적재가 부른다 — 어느 쪽이든 계산은 한 번이다.
        LoginCandidate candidate = new LoginCandidate(
                user,
                () -> effectiveAuthorityService.calculate(form.loginId()),
                context.clientIp());

        for (LoginPostCheck check : postChecks) {
            LoginResultCode blocked = check.check(candidate).orElse(null);
            if (blocked != null) {
                throw fail(form.loginId(), blocked, context);
            }
        }

        EffectiveAuthority authority = candidate.authority();
        try {
            establishSession(user, mustChangePassword, authority, response);
        } catch (SessionV2.Failure e) {
            if (e.outcome() != SessionV2.Outcome.INVALID_SESSION) throw e;
            throw fail(form.loginId(), LoginResultCode.FAIL_CONCURRENT, context);
        }

        userAccountService.recordLoginSuccess(form.loginId());
        loginHistoryService.record(form.loginId(), LoginResultCode.SUCCESS, context);


        return LoginResponse.of(
                new LoginUserResponse(user.getUserId(), user.getUserNm(), user.getLocaleCd()),
                mustChangePassword,
                authority,
                menuTreeService.build(authority),
                cacheVersionService.all(),
                timeoutSeconds,
                configs.get(UiExcelProps.class).maxRows(),
                configs.get(UiExcelProps.class).maxUploadRows(),
                configs.get(SearchRangeProps.class).defaultDays(),
                configs.get(SearchRangeProps.class).maxDays(),
                dateFormat());
    }


    private LoginResponse.DateFormatResponse dateFormat() {
        UiDateFormatProps format = configs.get(UiDateFormatProps.class);
        return new LoginResponse.DateFormatResponse(
                format.month(), format.day(), format.second());
    }

    public void logout(HttpServletRequest request, LoginResultCode reason) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication == null ? null : authentication.getName();

        AdminRequestContext.scope().logout();
        SecurityContextHolder.clearContext();

        if (userId != null) {
            loginHistoryService.record(userId, reason, LoginContexts.of(request));
        }
    }

    // ── 내부 ────────────────────────────────────────────────────────────


    private void establishSession(SysUser user, boolean mustChangePassword,
            EffectiveAuthority authority, HttpServletResponse response) {
        var scope = AdminRequestContext.scope();
        var identity = identity(user, mustChangePassword);
        var proof = producer.login(scope.exchange(), scope.current().map(SessionV2.Snapshot::ref).orElse(null),
                identity, calendar.now().plusSeconds(60));
        scope.publish(proof, slotPolicy());
        AuthzVersionHeader.stamp(response, authority);
    }

    public void rotatePassword(SysUser user) {
        var scope = AdminRequestContext.scope();
        var current = scope.current().orElseThrow(() -> new SessionV2.Failure(SessionV2.Outcome.INVALID_SESSION));
        var proof = producer.rotation(scope.exchange(), current.ref(), identity(user, false), calendar.now().plusSeconds(60));
        scope.publish(proof, slotPolicy());
    }

    public void credentialsChanged(List<String> userIds) {
        if (userIds.isEmpty()) return;
        var scope = AdminRequestContext.scope();
        scope.credentialsChanged(producer.credentialsChanged(scope.exchange(), userIds.stream()
                .map(id -> new PrincipalRef(AuthDomain.ADMIN, id)).toList(), calendar.now().plusSeconds(60)));
    }

    public int timeoutSeconds() { return timeoutSeconds; }

    private SessionRuntime.SlotPolicy slotPolicy() {
        var props = configs.get(ConcurrentSessionProps.class);
        return new SessionRuntime.SlotPolicy(props.enabled(), props.maxSessions(), props.preventLogin());
    }

    private Identity identity(SysUser user, boolean restricted) {
        // 원장 시각은 microseconds이다. 같은 clock tick의 암호 변경 시각을 잘라 과거로 만들지 않는다.
        Instant authenticatedAt = calendar.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (user.getPwdChgDtm() != null && authenticatedAt.isBefore(user.getPwdChgDtm())) {
            authenticatedAt = user.getPwdChgDtm().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (authenticatedAt.isBefore(user.getPwdChgDtm())) authenticatedAt = authenticatedAt.plusNanos(1_000);
        }
        return new Identity(new PrincipalRef(AuthDomain.ADMIN, user.getUserId()),
                restricted ? AuthLevel.PASSWORD_CHANGE_REQUIRED : AuthLevel.AUTHENTICATED,
                "LOCAL", null, authenticatedAt, user.getUserNm(), user.getLocaleCd());
    }

    private Authentication restrictedAuthentication(String userId) {
        return UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of());
    }

    private BusinessException fail(String userId, LoginResultCode result, LoginContext context) {
        loginHistoryService.record(userId, result, context);
        ErrorCode code = RESULT_TO_ERROR.getOrDefault(result, AuthErrorCode.LOGIN_FAILED);
        return new BusinessException(code, code.defaultMessage());
    }

}
