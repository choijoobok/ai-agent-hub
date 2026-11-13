// @feat FEAT-506
package com.aiagent.system.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ConfigMigrationContractTest {

    private static final String CONFIG_ADMIN =
            "com.aiagent.system.api.config.ConfigController";

    @Test
    @DisplayName("TC-5065 시스템 설정 API는 system 정식 경로 하나만 사용한다")
    void configApisUseOnlyTheSystemPath() throws Exception {
        assertPost(CONFIG_ADMIN, "/api/system/service/config", "getList", "/getList");
        assertPost(CONFIG_ADMIN, "/api/system/service/config", "getPrefixes", "/getPrefixes");
        assertPost(CONFIG_ADMIN, "/api/system/service/config", "save", "/save");
        assertClassMissing("com.aiagent.api.config.ConfigController");
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
