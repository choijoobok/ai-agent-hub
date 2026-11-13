// @feat FEAT-501
package com.aiagent.system.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiagent.api.auth.AuthErrorCode;
import com.aiagent.api.auth.v2.SessionRuntime;
import com.aiagent.api.auth.v2.SessionV2Filter;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionAdmissionsV2;
import com.aiagent.core.security.v2.SessionExchange;
import com.aiagent.support.config.props.ConcurrentSessionProps;
import org.springframework.mock.env.MockEnvironment;
import org.mockito.ArgumentCaptor;
import com.aiagent.api.authz.AuthzSession;
import com.aiagent.api.authz.AuthzVersionHeader;
import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.auth.LoginResultCode;
import com.aiagent.domain.auth.service.LoginHistoryService;
import com.aiagent.domain.auth.service.LoginHistoryService.LoginContext;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.EffectiveAuthority;
import com.aiagent.domain.authz.MenuAction;
import com.aiagent.domain.authz.service.EffectiveAuthorityService;
import com.aiagent.domain.authz.service.MenuTreeService;
import com.aiagent.domain.message.service.CacheVersionService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

class LoginSlice501Test {

    private static final String USER_ID = "kim";
    private static final Instant NOW = Instant.parse("2026-09-12T01:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        AdminRequestContext.clear();
    }

    @Test
    @DisplayName("TC-5011 허용 계정은 세션·권한 버전·성공 이력을 함께 발급한다")
    void allowedAccountCreatesSessionAndLoginHistory() {
        Fixture fixture = new Fixture(List.of());
        fixture.givenAuthenticatedUser();

        MockHttpServletRequest request = fixture.request("10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        LoginResponse result = fixture.service.login(
                new LoginRequest(USER_ID, "valid-password"), request, response);

        assertThat(result.user().userId()).isEqualTo(USER_ID);
        assertThat(request.getSession(false)).isNull();
        var proof = ArgumentCaptor.forClass(SessionAdmissionsV2.Proof.class);
        verify(fixture.scope).publish(proof.capture(), any());
        assertThat(proof.getValue().identity().principal().subjectId()).isEqualTo(USER_ID);
        assertThat(proof.getValue().identity().authLevel()).isEqualTo(AuthLevel.AUTHENTICATED);
        assertThat(response.getHeader(AuthzVersionHeader.NAME)).isEqualTo(fixture.authority.version());
        assertThat(result.expiresInSeconds())
                .isEqualTo(1800);
        assertThat(result.dateFormat())
                .isEqualTo(new LoginResponse.DateFormatResponse(
                        "YYYY-MM", "YYYY-MM-DD", "YYYY-MM-DD HH:mm:ss"));
        verify(fixture.userAccountService).recordLoginSuccess(USER_ID);
        verify(fixture.loginHistoryService)
                .record(eq(USER_ID), eq(LoginResultCode.SUCCESS), any(LoginContext.class));
    }

    @Test
    @DisplayName("TC-5013 잘못된 비밀번호는 실패 횟수와 원인 이력만 남긴다")
    void badPasswordRecordsFailureWithoutSession() {
        Fixture fixture = new Fixture(List.of());
        when(fixture.authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("잘못된 비밀번호"));
        MockHttpServletRequest request = fixture.request("10.0.0.8");

        assertThatThrownBy(() -> fixture.service.login(
                        new LoginRequest(USER_ID, "wrong-password"),
                        request,
                        new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(AuthErrorCode.LOGIN_FAILED));

        assertThat(request.getSession(false)).isNull();
        verify(fixture.userAccountService).recordLoginFailure(USER_ID);
        verify(fixture.loginHistoryService)
                .record(eq(USER_ID), eq(LoginResultCode.FAIL_PWD), any(LoginContext.class));
    }

    @Test
    @DisplayName("TC-5011 비밀번호 변경 필수 계정은 제한 세션으로 유도한다")
    void expiredPasswordCreatesRestrictedSession() {
        Fixture fixture = new Fixture(List.of());
        fixture.givenAuthenticatedUser();
        when(fixture.authenticationManager.authenticate(any()))
                .thenThrow(new CredentialsExpiredException("비밀번호 만료"));
        MockHttpServletRequest request = fixture.request("10.0.0.8");

        LoginResponse result = fixture.service.login(
                new LoginRequest(USER_ID, "expired-password"),
                request,
                new MockHttpServletResponse());

        assertThat(result.mustChangePassword()).isTrue();
        var proof = ArgumentCaptor.forClass(SessionAdmissionsV2.Proof.class);
        verify(fixture.scope).publish(proof.capture(), any());
        assertThat(proof.getValue().identity().authLevel()).isEqualTo(AuthLevel.PASSWORD_CHANGE_REQUIRED);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("TC-5011 로그아웃은 세션·보안 문맥을 닫고 이력을 남긴다")
    void logoutInvalidatesSessionAndRecordsHistory() {
        Fixture fixture = new Fixture(List.of());
        when(fixture.authentication.getName()).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(fixture.authentication);
        MockHttpServletRequest request = fixture.request("10.0.0.8");

        fixture.service.logout(request, LoginResultCode.LOGOUT);

        verify(fixture.scope).logout();
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(fixture.loginHistoryService)
                .record(eq(USER_ID), eq(LoginResultCode.LOGOUT), any(LoginContext.class));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BlockedCondition.class)
    @DisplayName("TC-5013 로그인 조건 하나만 실패해도 세션 없이 원인별 이력을 남긴다")
    void oneFailedConditionRejectsSession(BlockedCondition condition) {
        LoginPostCheck postCheck = candidate -> condition.postCheckResult();
        Fixture fixture = new Fixture(condition.isPostCheck() ? List.of(postCheck) : List.of());

        if (condition.isPostCheck()) {
            fixture.givenAuthenticatedUser();
        } else {
            when(fixture.authenticationManager.authenticate(any()))
                    .thenThrow(condition.authenticationFailure());
        }

        MockHttpServletRequest request = fixture.request("10.0.1.8");

        assertThatThrownBy(() -> fixture.service.login(
                        new LoginRequest(USER_ID, "valid-password"),
                        request,
                        new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(condition.errorCode));

        assertThat(request.getSession(false)).isNull();
        verify(fixture.loginHistoryService)
                .record(eq(USER_ID), eq(condition.resultCode), any(LoginContext.class));
        verify(fixture.scope, org.mockito.Mockito.never()).publish(any(), any());
    }

    private enum BlockedCondition {
        LOCKED(LoginResultCode.FAIL_LOCKED, AuthErrorCode.LOCKED),
        EXPIRED(LoginResultCode.FAIL_EXPIRED, AuthErrorCode.EXPIRED),
        DORMANT(LoginResultCode.FAIL_DORMANT, AuthErrorCode.DORMANT),
        IP_DENIED(LoginResultCode.FAIL_IP, AuthErrorCode.IP_DENIED);

        private final LoginResultCode resultCode;
        private final AuthErrorCode errorCode;

        BlockedCondition(LoginResultCode resultCode, AuthErrorCode errorCode) {
            this.resultCode = resultCode;
            this.errorCode = errorCode;
        }

        boolean isPostCheck() {
            return this == DORMANT || this == IP_DENIED;
        }

        Optional<LoginResultCode> postCheckResult() {
            return isPostCheck() ? Optional.of(resultCode) : Optional.empty();
        }

        RuntimeException authenticationFailure() {
            return switch (this) {
                case LOCKED -> new LockedException("잠긴 계정");
                case EXPIRED -> new AccountExpiredException("사용 기간 만료");
                default -> throw new IllegalStateException("후처리 조건에는 인증 예외가 없다");
            };
        }
    }

    private static final class Fixture {

        private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        private final UserAccountService userAccountService = mock(UserAccountService.class);
        private final EffectiveAuthorityService effectiveAuthorityService =
                mock(EffectiveAuthorityService.class);
        private final MenuTreeService menuTreeService = mock(MenuTreeService.class);
        private final CacheVersionService cacheVersionService = mock(CacheVersionService.class);
        private final LoginHistoryService loginHistoryService = mock(LoginHistoryService.class);
        private final SessionRuntime.Scope scope = mock(SessionRuntime.Scope.class);
        private final SessionAdmissionsV2.Producer producer = SessionAdmissionsV2.producer(AuthDomain.ADMIN, "system-test");
        private final ConfigLookup configs = mock(ConfigLookup.class);
        private final BusinessCalendar calendar = mock(BusinessCalendar.class);
        private final SysUser user = mock(SysUser.class);
        private final Authentication authentication = mock(Authentication.class);
        private final EffectiveAuthority authority = new EffectiveAuthority(
                Set.of(new MenuAction("HOME", ActionType.READ)), LocalDate.of(2026, 9, 12));
        private final LoginService service;

        private Fixture(List<LoginPostCheck> postChecks) {
            service = new LoginService(
                    authenticationManager,
                    userAccountService,
                    effectiveAuthorityService,
                    menuTreeService,
                    cacheVersionService,
                    loginHistoryService,
                    postChecks,
                    producer,
                    new MockEnvironment(),
                    configs,
                    calendar);
            var exchange = new SessionExchange(AuthDomain.ADMIN, "test", Capability.LOGIN, producer);
            exchange.beginAttempt(new Object());
            when(scope.exchange()).thenReturn(exchange);
            when(scope.current()).thenReturn(Optional.empty());
            when(configs.get(ConcurrentSessionProps.class)).thenReturn(new ConcurrentSessionProps(false, 1, false));
            when(calendar.now()).thenReturn(NOW);
            when(configs.get(AuthPasswordProps.class)).thenReturn(new AuthPasswordProps(
                    8, 3, 5, 90, 7, 24, true, new AuthPasswordProps.Reset(10, 5, 60)));
            when(configs.get(UiExcelProps.class)).thenReturn(new UiExcelProps(50_000, 5_000));
            when(configs.get(SearchRangeProps.class)).thenReturn(new SearchRangeProps(30, 366));
            when(configs.get(UiDateFormatProps.class))
                    .thenReturn(new UiDateFormatProps("YYYY-MM", "YYYY-MM-DD", "YYYY-MM-DD HH:mm:ss"));
            when(cacheVersionService.all()).thenReturn(Map.of());
            when(menuTreeService.build(authority)).thenReturn(List.of());
        }

        void givenAuthenticatedUser() {
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(userAccountService.find(USER_ID)).thenReturn(Optional.of(user));
            when(user.getUserId()).thenReturn(USER_ID);
            when(user.getUserNm()).thenReturn("김사용자");
            when(user.getLocaleCd()).thenReturn("ko");
            when(user.isTemporaryPasswordExpired(any(), eq(24))).thenReturn(false);
            when(effectiveAuthorityService.calculate(USER_ID)).thenReturn(authority);
        }

        MockHttpServletRequest request(String remoteAddress) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/system/public/auth/login");
            request.setRemoteAddr(remoteAddress);
            request.setAttribute(SessionV2Filter.SCOPE, scope);
            AdminRequestContext.bind(request);
            return request;
        }
    }
}
