// @feat FEAT-507
package com.aiagent.system.api.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiagent.domain.log.errorlog.ErrorLogSpec;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ErrorLogMigrationContractTest {

    private static final String ERROR_LOG = "com.aiagent.system.api.log.ErrorLogController";
    private static final String ERROR_DETAIL =
            "com.aiagent.system.api.log.request.ErrorLogDetailRequest";
    private static final String EXCEL_LOG = "com.aiagent.system.api.log.ExcelLogController";
    private static final String EXCEL_REQUEST =
            "com.aiagent.system.api.log.request.ExcelLogRequest";
    private static final String EXCEL_SCOPE = "com.aiagent.system.api.log.request.ExcelScope";

    @Test
    @DisplayName("SLICE-510 오류·다운로드 이력 구현과 DTO는 system 패키지에만 존재한다")
    void errorAndDownloadHistoryLivesOnlyInSystemModule() {
        assertPresent(ERROR_LOG);
        assertPresent(ERROR_DETAIL);
        assertPresent(EXCEL_LOG);
        assertPresent(EXCEL_REQUEST);
        assertPresent(EXCEL_SCOPE);

        assertMissing("com.aiagent.api.log.ErrorLogController");
        assertMissing("com.aiagent.api.log.request.ErrorLogDetailRequest");
        assertMissing("com.aiagent.api.log.ExcelLogController");
        assertMissing("com.aiagent.api.log.request.ExcelLogRequest");
        assertMissing("com.aiagent.api.log.request.ExcelScope");
    }

    @Test
    @DisplayName("TC-5075 오류·다운로드 이력 API는 system 정식 경로 하나만 사용한다")
    void errorAndDownloadHistoryUseOnlyTheSystemPath() throws Exception {
        assertPost(ERROR_LOG, "/api/system/service/errorLog", "getList", "/getList");
        assertPost(ERROR_LOG, "/api/system/service/errorLog", "getDetail", "/getDetail");
        assertPost(EXCEL_LOG, null, "log", "/api/system/common/excel/saveLog");
    }

    @Test
    @DisplayName("TC-5071 오류 이력은 없는 계정도 left join으로 보존하고 CLOB 전문을 목록 조건에 싣지 않는다")
    void errorHistoryKeepsTraceableRowsWithoutListingClobs() {
        assertThat(ErrorLogSpec.SPEC.joins()).hasSize(1);
        assertThat(ErrorLogSpec.SPEC.joins().getFirst().left()).isTrue();
        assertThat(ErrorLogSpec.SPEC.field("userId").nullable()).isTrue();
        assertThat(ErrorLogSpec.SPEC.field("userNm").nullable()).isTrue();
        assertThat(ErrorLogSpec.SPEC.field("rootCauseStack")).isNull();
    }

    @Test
    @DisplayName("UC-507 오류 이력 관리 API에는 쓰기·삭제 경로가 없다")
    void errorLogAdminApiIsReadOnly() throws Exception {
        assertThat(endpointMethods(ERROR_LOG)).containsExactlyInAnyOrder("getList", "getDetail");
    }

    private static void assertPost(
            String className, String expectedBase, String methodName, String expectedPath)
            throws Exception {
        Class<?> type = Class.forName(className);
        if (expectedBase == null) {
            assertThat(type.getAnnotation(RequestMapping.class)).isNull();
        } else {
            assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly(expectedBase);
        }
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
                .sorted()
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
