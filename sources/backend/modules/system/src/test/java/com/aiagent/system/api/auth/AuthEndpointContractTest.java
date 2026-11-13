// @feat FEAT-501
package com.aiagent.system.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class AuthEndpointContractTest {

    @Test
    @DisplayName("TC-5017·TC-50117 정확 관리자 CSRF 준비와 모듈 우선 경로만 등록한다")
    void administratorEntryUsesExplicitV2Routes() {
        var gets = java.util.Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .flatMap(m -> java.util.Arrays.stream(m.getAnnotation(GetMapping.class).value())).toList();
        assertThat(gets).contains("/api/system/public/auth/getCsrf");
        assertThat(gets).allMatch(p -> p.startsWith("/api/system/"));
    }

    @Test
    @DisplayName("TC-5011 로그인 공개 계약은 system 정식 경로 하나만 사용한다")
    void loginUsesOnlyTheSystemCanonicalPath() throws Exception {
        assertPost("login", "/api/system/public/auth/login");
    }

    @Test
    @DisplayName("TC-5013 인증 경로는 public·common system 경계 안에만 등록된다")
    void authEndpointsUseTheSystemBoundary() throws Exception {
        assertGet("getPasswordPolicy", "/api/system/public/auth/getPasswordPolicy");
        assertPost("sendResetCode", "/api/system/public/auth/sendResetCode");
        assertPost("verifyResetCode", "/api/system/public/auth/verifyResetCode");
        assertPost("saveResetPassword", "/api/system/public/auth/saveResetPassword");
        assertGet("me", "/api/system/common/auth/getMe");
        assertPost("heartbeat", "/api/system/common/auth/heartbeat");
        assertPost("changePassword", "/api/system/common/auth/changePassword");
        assertPost("logout", "/api/system/common/auth/logout");
    }

    private static void assertPost(String methodName, String expected) throws Exception {
        Method method = find(methodName);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expected);
    }

    private static void assertGet(String methodName, String expected) throws Exception {
        Method method = find(methodName);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expected);
    }

    private static Method find(String methodName) {
        return java.util.Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
