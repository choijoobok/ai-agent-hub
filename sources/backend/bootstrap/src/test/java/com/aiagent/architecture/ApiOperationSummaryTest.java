package com.aiagent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/service/**} 핸들러는 {@code @Operation(summary=…)} 을 갖는다 — backend/api.md 결정 2.
 *
 * <p><b>{@code checkApiSeed} 가 아니라 여기인 이유</b> — 시드 어긋남이 아니라 <b>코드 규약</b>
 * 이다. 시드를 재생성하지 않아도 어겨질 수 있고, 그때 {@code checkApiSeed} 는
 * <i>"재생성 결과 == 커밋본"</i> 을 볼 뿐이라 <b>둘 다 이름이 빈 채로 일치</b>한다.
 *
 * <p>{@code description} 은 필수가 아니다. 이름이 비면 카탈로그의 컬럼이 쓸모없어지지만
 * 설명이 비어도 화면은 제 일을 한다 — <b>필수 항목을 늘리면 채우려고 쓴 문장이 들어온다</b>.
 */
class ApiOperationSummaryTest {

    private static final String SERVICE_PREFIX = "/api/service/";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aiagent.api", "com.aiagent.system", "com.aiagent.sample");

    @Test
    @DisplayName("업무 API 핸들러 전부가 @Operation.summary 를 갖는다")
    void everyServiceHandlerHasSummary() {
        List<String> handlers = serviceHandlers();

        // 임포트가 비면 규칙이 공허하게 통과한다 — LayerDependencyTest 가 같은 대가를 치른다.
        assertThat(handlers)
                .as("업무 API 핸들러를 하나도 찾지 못했다면 스캔이 틀린 것이다")
                .isNotEmpty();

        assertThat(missingSummary())
.as("@Operation(summary=…) 이 없으면 SYS_API.API_NM 을 만들 수 없다 — backend/api.md")
                .isEmpty();
    }

    private List<String> serviceHandlers() {
        List<String> found = new ArrayList<>();
        forEachServiceHandler((method, url) -> found.add(url));
        return found;
    }

    private List<String> missingSummary() {
        List<String> broken = new ArrayList<>();
        forEachServiceHandler((method, url) -> {
            if (summaryOf(method).isBlank()) {
                broken.add(url + " (" + method.getFullName() + ")");
            }
        });
        return broken;
    }

    private void forEachServiceHandler(HandlerVisitor visitor) {
        for (JavaClass type : classes) {
            if (!hasAnnotation(type, "RestController")) {
                continue;
            }
            String base = firstPath(annotationOf(type, "RequestMapping"));
            for (JavaMethod method : type.getMethods()) {
                for (JavaAnnotation<?> annotation : method.getAnnotations()) {
                    if (!annotation.getRawType().getSimpleName().endsWith("Mapping")) {
                        continue;
                    }
                    String url = join(base, firstPath(Optional.of(annotation)));
                    if (url.matches("/api/[A-Za-z0-9_-]+/service/.+")) {
                        visitor.visit(method, url);
                    }
                }
            }
        }
    }

    private static String summaryOf(JavaMethod method) {
        return method.getAnnotations().stream()
                .filter(annotation -> annotation.getRawType().getSimpleName().equals("Operation"))
                .findFirst()
                .map(annotation -> annotation.get("summary").orElse("").toString())
                .orElse("");
    }

    private static boolean hasAnnotation(JavaClass type, String simpleName) {
        return annotationOf(type, simpleName).isPresent();
    }

    private static Optional<JavaAnnotation<?>> annotationOf(JavaClass type, String simpleName) {
        for (JavaAnnotation<?> annotation : type.getAnnotations()) {
            if (annotation.getRawType().getSimpleName().equals(simpleName)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    private static String firstPath(Optional<? extends JavaAnnotation<?>> annotation) {
        return annotation
                .flatMap(a -> a.get("value"))
                .filter(String[].class::isInstance)
                .map(String[].class::cast)
                .filter(paths -> paths.length > 0)
                .map(paths -> paths[0])
                .orElse("");
    }

    private static String join(String base, String suffix) {
        return (base + "/" + suffix).replace("//", "/");
    }

    @FunctionalInterface
    private interface HandlerVisitor {
        void visit(JavaMethod method, String url);
    }
}
