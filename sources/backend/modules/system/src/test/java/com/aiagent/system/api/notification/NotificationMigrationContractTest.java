// @feat FEAT-509
package com.aiagent.system.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class NotificationMigrationContractTest {

    private static final String ADMIN_CONTROLLER =
            "com.aiagent.system.api.notirequest.NotiRequestController";
    private static final String MY_REQUEST_CONTROLLER =
            "com.aiagent.system.api.notirequest.MyNotiRequestController";
    private static final String INBOX_CONTROLLER =
            "com.aiagent.system.api.notification.NotiInboxController";

    @Test
    @DisplayName("SLICE-514 요청 판정·내 요청·알림함 구현은 system 패키지에만 존재한다")
    void notificationImplementationLivesOnlyInSystemModule() {
        assertPresent(ADMIN_CONTROLLER);
        assertPresent(MY_REQUEST_CONTROLLER);
        assertPresent(INBOX_CONTROLLER);
        assertPresent("com.aiagent.system.api.notirequest.request.NotiRequestHandleRequest");
        assertPresent("com.aiagent.system.api.notirequest.request.MyNotiRequestCreateRequest");
        assertPresent("com.aiagent.system.api.notification.request.NotiReadRequest");

        assertMissing("com.aiagent.api.notirequest.NotiRequestController");
        assertMissing("com.aiagent.api.notirequest.MyNotiRequestController");
        assertMissing("com.aiagent.api.notification.NotiInboxController");
        assertMissing("com.aiagent.api.notirequest.request.NotiRequestHandleRequest");
        assertMissing("com.aiagent.api.notirequest.request.MyNotiRequestCreateRequest");
        assertMissing("com.aiagent.api.notification.request.NotiReadRequest");
    }

    @Test
    @DisplayName("TC-5091·TC-5094·TC-5095·TC-5096·TC-5097 요청과 알림 API는 system 정식 경로만 사용한다")
    void notificationApisUseOnlyTheSystemPath() throws Exception {
        assertPost(ADMIN_CONTROLLER, "/api/system/service/notiRequest", "getList", "/getList");
        assertPost(ADMIN_CONTROLLER, "/api/system/service/notiRequest", "updateHandle", "/updateHandle");
        assertGet(MY_REQUEST_CONTROLLER, "getMyList", "/api/system/common/notiRequest/getMyList");
        assertPost(MY_REQUEST_CONTROLLER, null, "create", "/api/system/common/notiRequest/save");
        assertGet(INBOX_CONTROLLER, "getList", "/api/system/common/noti/getList");
        assertGet(INBOX_CONTROLLER, "getUnreadCount", "/api/system/common/noti/getUnreadCount");
        assertPost(INBOX_CONTROLLER, null, "markRead", "/api/system/common/noti/saveRead");
        assertPost(INBOX_CONTROLLER, null, "markAllRead", "/api/system/common/noti/saveAllRead");
    }

    @Test
    @DisplayName("UC-509 요청 판정과 public 알림 API는 정의된 소유 표면만 선언한다")
    void notificationApiSurfaceStaysWithinOwnership() throws Exception {
        assertThat(postMethods(ADMIN_CONTROLLER)).containsExactlyInAnyOrder("getList", "updateHandle");
        assertThat(getMethods(MY_REQUEST_CONTROLLER)).containsExactly("getMyList");
        assertThat(postMethods(MY_REQUEST_CONTROLLER)).containsExactly("create");
        assertThat(getMethods(INBOX_CONTROLLER)).containsExactlyInAnyOrder("getList", "getUnreadCount");
        assertThat(postMethods(INBOX_CONTROLLER)).containsExactlyInAnyOrder("markRead", "markAllRead");
    }

    private static void assertGet(String className, String methodName, String expectedPath)
            throws Exception {
        Method method = method(Class.forName(className), methodName);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
    }

    private static void assertPost(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        if (expectedBase != null) {
            assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        }
        Method method = method(type, methodName);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedPath);
    }

    private static Method method(Class<?> type, String methodName) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> postMethods(String className) throws Exception {
        return methodsWithAnnotation(className, PostMapping.class);
    }

    private static List<String> getMethods(String className) throws Exception {
        return methodsWithAnnotation(className, GetMapping.class);
    }

    private static List<String> methodsWithAnnotation(
            String className, Class<? extends java.lang.annotation.Annotation> annotation) throws Exception {
        return java.util.Arrays.stream(Class.forName(className).getDeclaredMethods())
                .filter(method -> method.getAnnotation(annotation) != null)
                .map(Method::getName)
                .toList();
    }

    private static void assertPresent(String className) {
        assertThatCode(() -> Class.forName(className)).doesNotThrowAnyException();
    }

    private static void assertMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
