// @feat FEAT-502
package com.aiagent.system.api.userauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class UserAuthorizationMigrationContractTest {

    private static final String USER_AUTH = "com.aiagent.system.api.userauth.UserAuthController";

    @Test
    @DisplayName("TC-5025 개인 예외 권한 API는 system 정식 경로 하나만 사용한다")
    void personalAuthorizationUsesOnlyTheSystemPath() throws Exception {
        assertPost(USER_AUTH, "/api/system/service/userAuth", "getList", "/getList");
        assertPost(USER_AUTH, "/api/system/service/userAuth", "getMenuTree", "/getMenuTree");
        assertPost(USER_AUTH, "/api/system/service/userAuth", "save", "/save");
        assertClassMissing("com.aiagent.api.userauth.UserAuthController");
    }

    private static void assertPost(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedPath);
    }

    private static void assertClassMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
