// @feat FEAT-504
package com.aiagent.system.api.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class CodeMigrationContractTest {

    private static final String CODE_ADMIN = "com.aiagent.system.api.code.CodeAdminController";
    private static final String CODE_COMMON = "com.aiagent.system.api.code.CodeController";

    @Test
    @DisplayName("TC-5045 공통코드 API는 system 정식 경로 하나만 사용한다")
    void codeApisUseOnlyTheSystemPath() throws Exception {
        assertPost(CODE_ADMIN, "/api/system/service/code", "getGroupList", "/getGroupList");
        assertPost(CODE_ADMIN, "/api/system/service/code", "saveGroup", "/saveGroup");
        assertPost(CODE_ADMIN, "/api/system/service/code", "getList", "/getList");
        assertPost(CODE_ADMIN, "/api/system/service/code", "save", "/save");
        assertPost(CODE_ADMIN, "/api/system/service/code", "getRefDefList", "/getRefDefList");
        assertPost(CODE_ADMIN, "/api/system/service/code", "saveRefDef", "/saveRefDef");
        assertPost(CODE_COMMON, null, "getList", "/api/system/common/code/getList");
        assertClassMissing("com.aiagent.api.code.CodeAdminController");
        assertClassMissing("com.aiagent.api.code.CodeController");
    }

    private static void assertPost(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        if (expectedBase != null) {
            assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        } else {
            assertThat(type.getAnnotation(RequestMapping.class)).isNull();
        }
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
