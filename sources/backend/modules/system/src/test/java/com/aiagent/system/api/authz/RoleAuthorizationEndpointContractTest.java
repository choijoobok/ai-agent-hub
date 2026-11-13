// @feat FEAT-502
package com.aiagent.system.api.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class RoleAuthorizationEndpointContractTest {

    private static final String ROLE = "com.aiagent.system.api.role.RoleController";
    private static final String ROLE_AUTH = "com.aiagent.system.api.role.RoleAuthController";
    private static final String USER_AUTHZ = "com.aiagent.system.api.user.UserAuthorizationController";

    @Test
    @DisplayName("TC-5021 유효 권한 조회는 system 정식 경로 하나만 사용한다")
    void effectiveAuthorityUsesTheSystemPath() throws Exception {
        assertPost(USER_AUTHZ, "/api/system/service/user", "getEffectiveAuthList", "/getEffectiveAuthList");
        assertClassMissing("com.aiagent.api.user.UserDetailController");
    }

    @Test
    @DisplayName("TC-5023 역할 권한 회수 API는 system 경계에서 세션 무효화를 시작한다")
    void roleAuthoritySaveUsesTheSystemPath() throws Exception {
        assertPost(ROLE_AUTH, "/api/system/service/roleAuth", "save", "/save");
        assertClassMissing("com.aiagent.api.role.RoleAuthController");
    }

    @Test
    @DisplayName("TC-5024 사용자 역할 API는 역할명과 무관한 system 계약을 사용한다")
    void userRoleEndpointsUseTheSystemPath() throws Exception {
        assertPost(USER_AUTHZ, "/api/system/service/user", "getRoleList", "/getRoleList");
        assertPost(USER_AUTHZ, "/api/system/service/user", "saveRole", "/saveRole");
        assertPost(USER_AUTHZ, "/api/system/service/user", "getRoleCandidates", "/getRoleCandidates");
        assertClassMissing("com.aiagent.api.user.UserDetailController");
    }

    @Test
    @DisplayName("TC-5026 역할과 역할 권한 API는 system 경계에만 등록된다")
    void roleEndpointsUseTheSystemBoundary() throws Exception {
        assertPost(ROLE, "/api/system/service/role", "getList", "/getList");
        assertPost(ROLE, "/api/system/service/role", "save", "/save");
        assertPost(ROLE_AUTH, "/api/system/service/roleAuth", "getList", "/getList");
        assertPost(ROLE_AUTH, "/api/system/service/roleAuth", "getMenuTree", "/getMenuTree");
        assertPost(ROLE_AUTH, "/api/system/service/roleAuth", "getAffectedUsers", "/getAffectedUsers");
        assertClassMissing("com.aiagent.api.role.RoleController");
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
