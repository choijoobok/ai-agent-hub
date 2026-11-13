// @feat FEAT-507
package com.aiagent.system.api.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiagent.core.error.BusinessException;
import com.aiagent.core.search.Condition;
import com.aiagent.core.search.Op;
import com.aiagent.core.search.PageSpec;
import com.aiagent.core.search.RangeGuard;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.auth.loginlog.LoginLogErrorCode;
import com.aiagent.domain.auth.loginlog.LoginLogSpec;
import com.aiagent.domain.log.admin.AccessLogErrorCode;
import jakarta.validation.constraints.Max;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class LogMigrationContractTest {

    private static final String LOGIN_LOG = "com.aiagent.system.api.log.LoginLogController";
    private static final String ACCESS_LOG = "com.aiagent.system.api.log.AccessLogController";
    private static final String ACCESS_DETAIL =
            "com.aiagent.system.api.log.request.AccessLogDetailRequest";

    @Test
    @DisplayName("SLICE-509 로그인·사용 이력 구현과 DTO는 system 패키지에만 존재한다")
    void logImplementationLivesOnlyInSystemModule() {
        assertPresent(LOGIN_LOG);
        assertPresent(ACCESS_LOG);
        assertPresent(ACCESS_DETAIL);

        assertMissing("com.aiagent.api.log.LoginLogController");
        assertMissing("com.aiagent.api.log.AccessLogController");
        assertMissing("com.aiagent.api.log.request.AccessLogDetailRequest");
    }

    @Test
    @DisplayName("SLICE-509 로그인·사용 이력 API는 system 정식 경로 하나만 사용한다")
    void logApisUseOnlyTheSystemPath() throws Exception {
        assertPost(LOGIN_LOG, "/api/system/service/loginLog", "getList", "/getList");
        assertPost(ACCESS_LOG, "/api/system/service/accessLog", "getList", "/getList");
        assertPost(ACCESS_LOG, "/api/system/service/accessLog", "getDetail", "/getDetail");
    }

    @Test
    @DisplayName("TC-5072 과도한 로그인·사용 이력 기간은 쿼리 조립 전에 거부한다")
    void excessiveRangeIsRejectedBeforeQueryBuild() throws Exception {
        SearchRequest<Void> request = new SearchRequest<>(
                List.of(new Condition(
                        "creDtm",
                        Op.between,
                        List.of("2026-01-01T00:00:00Z", "2026-09-12T00:00:00Z"))),
                List.of(),
                null,
                null);

        assertRangeRejected(
                request,
                new RangeGuard.Rule(
                        "creDtm",
                        "traceId",
                        92,
                        LoginLogErrorCode.RANGE_REQUIRED,
                        LoginLogErrorCode.RANGE_TOO_WIDE),
                LoginLogErrorCode.RANGE_TOO_WIDE);
        assertRangeRejected(
                request,
                new RangeGuard.Rule(
                        "creDtm",
                        "traceId",
                        31,
                        AccessLogErrorCode.RANGE_REQUIRED,
                        AccessLogErrorCode.RANGE_TOO_WIDE),
                AccessLogErrorCode.RANGE_TOO_WIDE);

        Max pageSizeLimit = PageSpec.class.getDeclaredField("size").getAnnotation(Max.class);
        assertThat(pageSizeLimit).isNotNull();
        assertThat(pageSizeLimit.value()).isEqualTo(PageSpec.MAX_SIZE);
    }

    @Test
    @DisplayName("TC-5073 없는 계정의 로그인 시도는 left join으로 보존한다")
    void missingUserLoginAttemptIsPreserved() {
        assertThat(LoginLogSpec.SPEC.joins()).hasSize(1);
        assertThat(LoginLogSpec.SPEC.joins().getFirst().left()).isTrue();
        assertThat(LoginLogSpec.SPEC.field("userId").nullable()).isTrue();
        assertThat(LoginLogSpec.SPEC.field("userNm").nullable()).isTrue();
    }

    @Test
    @DisplayName("UC-507 이력 관리 API에는 쓰기·삭제 경로가 없다")
    void logAdminApisAreReadOnly() throws Exception {
        assertThat(endpointMethods(LOGIN_LOG)).containsExactly("getList");
        assertThat(endpointMethods(ACCESS_LOG)).containsExactlyInAnyOrder("getList", "getDetail");
    }

    private static void assertRangeRejected(
            SearchRequest<Void> request, RangeGuard.Rule rule, Object expectedCode) {
        assertThatThrownBy(() -> RangeGuard.check(request, rule))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(expectedCode);
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
