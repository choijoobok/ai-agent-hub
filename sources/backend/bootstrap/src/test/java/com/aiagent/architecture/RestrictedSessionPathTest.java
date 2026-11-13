package com.aiagent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiagent.system.api.authz.AuthorizationInterceptor;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 제한 세션 화이트리스트의 경로는 <b>실제 핸들러를 가리킨다</b> — backend/security.md 체인 #5.
 *
 * <p>체인 #5 는 <b>차단이 아니라 유도</b>다. 비밀번호가 만료된 사용자의 세션은 살아 있되
 * 이 목록 밖의 모든 {@code /api/**} 가 403 이 되며, <b>목록의 경로에 핸들러가 없으면
 * 404</b> 다 — 유도의 끝이 막다른 곳이 되고 그 사람은 로그아웃도 못 한다.
 *
 * <p><b>실제로 그런 상태로 한동안 있었다.</b> {@code /api/system/common/auth/changePassword} 는
 * 화면이 오기 전부터 목록에 적혀 있었고(<i>"없는 경로가 여기 있어도 무해하다 — 핸들러가
 * 없으면 404 다"</i>), 프로필 모달(frontend/tabs.md)이 그 핸들러를 붙이면서 비로소 참이 됐다. 이제
 * <b>반대 방향의 오타</b>도 막는다: 경로를 고치면서 한쪽만 고치는 것이 이 게이트가 잡는 것이다.
 *
 * <p>매칭은 <b>완전 일치</b>다. 인터셉터가 {@code Set.contains} 로 비교하므로 여기서도
 * 그래야 한다 — 접두 비교로 검사하면 게이트가 통과하는데 실행은 403 이 된다.
 */
class RestrictedSessionPathTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aiagent.api", "com.aiagent.system");

    @Test
    @DisplayName("화이트리스트의 경로마다 핸들러가 있다 — 없으면 404 라 막다른 세션이 된다")
    void everyAllowedPathHasAHandler() {
        Set<String> allowed = restrictedSessionAllowed();
        Set<String> handlers = handlerPaths();

        assertThat(allowed).as("화이트리스트가 비면 제한 세션이 로그아웃조차 못 한다").isNotEmpty();
        assertThat(handlers).as("핸들러를 하나도 찾지 못했다면 스캔이 틀린 것이다").isNotEmpty();

        assertThat(handlers).containsAll(allowed);
    }

    @Test
    @DisplayName("비밀번호 변경이 그 안에 있다 — 유도의 끝에는 문이 열려야 한다")
    void passwordChangeIsReachable() {
        // 이 경로가 목록에서 빠지면 임시 비밀번호로 들어온 사람이 바꿀 방법이 없다.
        assertThat(restrictedSessionAllowed()).contains("/api/system/common/auth/changePassword");
    }

    /** 인터셉터의 private 상수를 읽는다 — 목록을 여기 다시 적으면 두 곳이 어긋난다. */
    private static Set<String> restrictedSessionAllowed() {
        try {
            var environment = new org.springframework.mock.env.MockEnvironment();
            environment.setActiveProfiles("local");
            var catalog = new com.aiagent.system.api.auth.AdminApiCatalog(environment,
                    org.mockito.Mockito.mock(com.aiagent.domain.progress.JobProgressStore.class));
            return catalog.catalog().entries().stream()
                    .filter(operation -> !operation.path().contains("/public/")
                            && operation.levels().contains(com.aiagent.core.security.SessionPersistencePort.AuthLevel.PASSWORD_CHANGE_REQUIRED))
                    .map(operation -> operation.path()).collect(java.util.stream.Collectors.toSet());
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private Set<String> handlerPaths() {
        Set<String> found = new LinkedHashSet<>();
        for (JavaClass type : classes) {
            if (annotationOf(type, "RestController").isEmpty()) {
                continue;
            }
            String base = firstPath(annotationOf(type, "RequestMapping"));
            for (JavaMethod method : type.getMethods()) {
                for (JavaAnnotation<?> annotation : method.getAnnotations()) {
                    if (annotation.getRawType().getSimpleName().endsWith("Mapping")) {
                        found.add(join(base, firstPath(Optional.of(annotation))));
                    }
                }
            }
        }
        return found;
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
}
