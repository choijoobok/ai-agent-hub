// @feat FEAT-505
package com.aiagent.system.api.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class MessageMigrationContractTest {

    private static final String MESSAGE_ADMIN =
            "com.aiagent.system.api.message.MessageAdminController";
    private static final String MESSAGE_PUBLIC = "com.aiagent.system.api.message.MessageController";

    @Test
    @DisplayName("TC-5055 메시지 API는 system 정식 경로 하나만 사용한다")
    void messageApisUseOnlyTheSystemPath() throws Exception {
        assertPost(MESSAGE_ADMIN, "/api/system/service/message", "getList", "/getList");
        assertPost(MESSAGE_ADMIN, "/api/system/service/message", "save", "/save");
        assertGet(MESSAGE_PUBLIC, "getBundle", "/api/system/public/message/getBundle");
        assertClassMissing("com.aiagent.api.message.MessageAdminController");
        assertClassMissing("com.aiagent.api.message.MessageController");
    }

    private static void assertPost(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        Method method = method(type, methodName);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedPath);
    }

    private static void assertGet(String className, String methodName, String expectedPath)
            throws Exception {
        Method method = method(Class.forName(className), methodName);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
    }

    private static Method method(Class<?> type, String methodName) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static void assertClassMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
