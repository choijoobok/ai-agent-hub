// @feat FEAT-508
package com.aiagent.system.api.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiagent.core.grid.RowState;
import com.aiagent.domain.batch.schedule.BatchScheduleSaveRow;
import com.aiagent.system.api.batch.request.BatchScheduleSaveRequest;
import java.lang.reflect.Method;
import java.util.List;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class BatchScheduleMigrationContractTest {

    private static final String CONTROLLER = "com.aiagent.system.api.batch.BatchScheduleController";

    @Test
    @DisplayName("SLICE-511 배치 일정 구현과 DTO는 system 패키지에만 존재한다")
    void batchScheduleImplementationLivesOnlyInSystemModule() {
        assertPresent(CONTROLLER);
        assertPresent("com.aiagent.system.api.batch.request.BatchRunRequest");
        assertPresent("com.aiagent.system.api.batch.request.BatchScheduleSaveRequest");
        assertPresent("com.aiagent.system.api.batch.response.BatchRunResponse");

        assertMissing("com.aiagent.api.batch.BatchScheduleController");
        assertMissing("com.aiagent.api.batch.request.BatchRunRequest");
        assertMissing("com.aiagent.api.batch.request.BatchScheduleSaveRequest");
        assertMissing("com.aiagent.api.batch.response.BatchRunResponse");
    }

    @Test
    @DisplayName("TC-5081·TC-5082 배치 일정 API는 system 정식 경로 하나만 사용한다")
    void batchScheduleApisUseOnlyTheSystemPath() throws Exception {
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "getList", "/getList");
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "getBeanList", "/getBeanList");
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "getTimezoneList", "/getTimezoneList");
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "getRecentHistory", "/getRecentHistory");
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "save", "/save");
        assertPost(CONTROLLER, "/api/system/service/batchSchedule", "runNow", "/runNow");
    }

    @Test
    @DisplayName("UC-508 일정 화면은 조회·저장·실행만 선언하고 이력 중단은 섞지 않는다")
    void batchScheduleApiSurfaceStaysWithinScheduleOwnership() throws Exception {
        assertThat(endpointMethods(CONTROLLER))
                .containsExactlyInAnyOrder(
                        "getList", "getBeanList", "getTimezoneList", "getRecentHistory", "save", "runNow");
    }

    @Test
    @DisplayName("TC-5082 잠금 범위는 system 저장 요청의 중첩 Bean Validation에서 거부된다")
    void scheduleSaveRequestRejectsInvalidLockBounds() {
        BatchScheduleSaveRow invalidRow = new BatchScheduleSaveRow(
                RowState.C,
                "daily-close",
                "일마감",
                "일마감 배치",
                "dailyCloseJob",
                "0 0 1 * * *",
                "Asia/Seoul",
                null,
                0,
                -1,
                true,
                0);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new BatchScheduleSaveRequest(List.of(invalidRow))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("rows[0].lockExpirySec", "rows[0].lockMinIntervalSec");
        }
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
