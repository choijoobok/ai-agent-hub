// @feat FEAT-508
package com.aiagent.system.api.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class BatchHistoryMigrationContractTest {

    private static final String CONTROLLER = "com.aiagent.system.api.batch.BatchHistoryController";

    @Test
    @DisplayName("SLICE-512 배치 실행 이력 구현과 요청 DTO는 system 패키지에만 존재한다")
    void batchHistoryImplementationLivesOnlyInSystemModule() {
        assertPresent(CONTROLLER);
        assertPresent("com.aiagent.system.api.batch.request.BatchCancelRequest");
        assertPresent("com.aiagent.system.api.batch.request.BatchHistoryDetailRequest");

        assertMissing("com.aiagent.api.batch.BatchHistoryController");
        assertMissing("com.aiagent.api.batch.request.BatchCancelRequest");
        assertMissing("com.aiagent.api.batch.request.BatchHistoryDetailRequest");
    }

    @Test
    @DisplayName("TC-5085 배치 실행 이력 API는 system 정식 경로 하나만 사용한다")
    void batchHistoryApisUseOnlyTheSystemPath() throws Exception {
        assertPost(CONTROLLER, "/api/system/service/batchHistory", "getList", "/getList");
        assertPost(CONTROLLER, "/api/system/service/batchHistory", "getDetail", "/getDetail");
        assertPost(CONTROLLER, "/api/system/service/batchHistory", "cancel", "/cancel");
    }

    @Test
    @DisplayName("UC-508 이력 API는 조회와 협조적 중단 요청만 선언한다")
    void batchHistoryApiSurfaceStaysWithinHistoryOwnership() throws Exception {
        assertThat(endpointMethods(CONTROLLER)).containsExactlyInAnyOrder("getList", "getDetail", "cancel");
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

    private static List<String> endpointMethods(String className) throws Exception {
        return java.util.Arrays.stream(Class.forName(className).getDeclaredMethods())
                .filter(method -> method.getAnnotation(PostMapping.class) != null)
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
