// @feat FEAT-509
package com.aiagent.system.api.notice;

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

class NoticeMigrationContractTest {

    private static final String ADMIN_CONTROLLER = "com.aiagent.system.api.notice.NoticeController";
    private static final String FEED_CONTROLLER = "com.aiagent.system.api.notice.NoticeFeedController";

    @Test
    @DisplayName("SLICE-513 공지 구현과 저장 요청은 system 패키지에만 존재한다")
    void noticeImplementationLivesOnlyInSystemModule() {
        assertPresent(ADMIN_CONTROLLER);
        assertPresent(FEED_CONTROLLER);
        assertPresent("com.aiagent.system.api.notice.request.NoticeSaveRequest");

        assertMissing("com.aiagent.api.notice.NoticeController");
        assertMissing("com.aiagent.api.notice.NoticeFeedController");
        assertMissing("com.aiagent.api.notice.request.NoticeSaveRequest");
    }

    @Test
    @DisplayName("TC-5092·TC-5093 공지 API는 system 정식 경로만 사용한다")
    void noticeApisUseOnlyTheSystemPath() throws Exception {
        assertPost(ADMIN_CONTROLLER, "/api/system/service/notice", "getList", "/getList");
        assertPost(ADMIN_CONTROLLER, "/api/system/service/notice", "save", "/save");
        assertGet(FEED_CONTROLLER, "getList", "/api/system/common/notice/getList");
        assertGet(FEED_CONTROLLER, "getOne", "/api/system/common/notice/getOne");
        assertGet(FEED_CONTROLLER, "getPopup", "/api/system/common/notice/getPopup");
    }

    @Test
    @DisplayName("UC-509 공지 API는 관리와 현재 노출 조회만 선언한다")
    void noticeApiSurfaceStaysWithinNoticeOwnership() throws Exception {
        assertThat(postMethods(ADMIN_CONTROLLER)).containsExactlyInAnyOrder("getList", "save");
        assertThat(getMethods(FEED_CONTROLLER)).containsExactlyInAnyOrder("getList", "getOne", "getPopup");
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
