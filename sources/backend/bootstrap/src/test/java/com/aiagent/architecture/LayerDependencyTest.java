package com.aiagent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 의존 방향은 규약이 아니라 빌드가 강제한다 — aidd-guide/standard/backend/packages.md.
 *
 * <p>Gradle 모듈 경계가 이미 상당 부분을 막지만, 막지 못하는 것이 둘이다 — 한 모듈 안의
 * 패키지 간 순환과, {@code api()} 로 전이 노출된 상위 모듈의 역참조다.
 */
class LayerDependencyTest {

    private static final String BASE = "com.aiagent";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    /**
     * <b>이 테스트가 나머지 넷을 지킨다.</b> ArchUnit 은 읽지 못한 클래스를 조용히 건너뛰므로,
     * 임포트가 비면 모든 규칙이 "위반 0건" 으로 통과한다 — 영원히 초록인 게이트가 된다.
     *
     * <p>가상의 위험이 아니다. ArchUnit 1.4.0 은 JDK 25 바이트코드(class file v69)를
     * 한 개도 읽지 못했고, 예외도 경고도 없었다. 1.5.0 에서 해소됐다.
     *
     * <p>아래 규칙들은 {@code allowEmptyShould(true)} 로 열어 두었다 — support·domain·api 가
     * 아직 비어 있기 때문이다. 그 완화의 대가를 여기서 치른다.
     */
    @Test
    @DisplayName("임포트가 비어 있지 않다 — 나머지 규칙이 공허하게 통과하는 것을 막는다")
    void importIsNotEmpty() {
        assertThat(classes).isNotEmpty();
        assertThat(classes.stream().map(c -> c.getName()))
                .contains(
                        BASE + ".core.domain.BaseAuditEntity",
                        BASE + ".portal.api.auth.PortalAuthController",
                        BASE + ".system.api.auth.AuthController");
    }

    @Test
    @DisplayName("core 는 다른 모듈을 참조하지 않는다")
    void coreDependsOnNothing() {
        noClasses()
                .that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + ".support..", BASE + ".domain..", BASE + ".api..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("support 는 domain·api 를 참조하지 않는다")
    void supportDependsOnCoreOnly() {
        noClasses()
                .that().resideInAPackage(BASE + ".support..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + ".domain..", BASE + ".api..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("domain 은 api 를 참조하지 않는다")
    void domainDoesNotDependOnApi() {
        noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".api..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("system 은 다른 제품 모듈을 참조하지 않는다")
    void systemDoesNotDependOnOtherProductModules() {
        noClasses()
                .that().resideInAPackage(BASE + ".system..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE + ".admin..",
                        BASE + ".portal..",
                        BASE + ".agent..",
                        BASE + ".extensions..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("순환 의존이 없다")
    void noCycles() {
        slices().matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Portal 은 다른 제품 모듈을 참조하지 않는다")
    void portalDoesNotDependOnOtherProductModules() {
        noClasses().that().resideInAPackage(BASE + ".portal..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + ".admin..",BASE + ".system..",BASE + ".agent..",BASE + ".bootstrap..")
                .check(classes);
    }
}
