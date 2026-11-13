// @feat FEAT-501
package com.aiagent.system.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class UserEndpointMigrationContractTest {

    private static final String USER = "com.aiagent.system.api.user.UserController";
    private static final String DETAIL = "com.aiagent.system.api.user.UserDetailController";
    private static final String PROFILE = "com.aiagent.system.api.user.UserProfileController";
    private static final String PREFERENCE = "com.aiagent.system.api.user.UserPreferenceController";

    @Test
    @DisplayName("TC-5015 사용자 관리 API는 system 정식 경로 하나만 사용한다")
    void userManagementUsesOnlyTheSystemPath() throws Exception {
        assertPost(USER, "/api/system/service/user", "getList", "/getList");
        assertPost(USER, "/api/system/service/user", "save", "/save");
        assertPost(DETAIL, "/api/system/service/user", "getIpList", "/getIpList");
        assertPost(DETAIL, "/api/system/service/user", "saveIp", "/saveIp");
        assertPost(DETAIL, "/api/system/service/user", "getSessionList", "/getSessionList");
        assertPost(DETAIL, "/api/system/service/user", "killSession", "/killSession");
        assertClassMissing("com.aiagent.api.user.UserController");
        assertClassMissing("com.aiagent.api.user.UserDetailController");
    }

    @Test
    @DisplayName("TC-5015 내 프로필 API와 DTO는 system public 경로에만 존재한다")
    void profileUsesOnlyTheSystemPublicPath() throws Exception {
        assertGet(PROFILE, "/api/system/common/user", "getMyProfile", "/getMyProfile");
        assertPost(PROFILE, "/api/system/common/user", "saveMyProfile", "/saveMyProfile");
        assertGet(PROFILE, "/api/system/common/user", "getMyLoginHistory", "/getMyLoginHistory");
        assertGet(PROFILE, "/api/system/common/user", "getMyRoles", "/getMyRoles");
        assertPost(PREFERENCE, "/api/system/common/user", "saveLocale", "/saveLocale");

        assertClassPresent("com.aiagent.system.api.user.request.ProfileRequest");
        assertClassPresent("com.aiagent.system.api.user.request.LocaleRequest");
        assertClassMissing("com.aiagent.api.user.UserProfileController");
        assertClassMissing("com.aiagent.api.user.UserPreferenceController");
        assertClassMissing("com.aiagent.api.user.request.ProfileRequest");
        assertClassMissing("com.aiagent.api.user.request.LocaleRequest");
    }

    private static void assertGet(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
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

    private static void assertClassPresent(String className) throws Exception {
        assertThat(Class.forName(className)).isNotNull();
    }
}
