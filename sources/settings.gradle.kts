import org.gradle.kotlin.dsl.extra

rootProject.name = "ai-agent"

// 모듈 구성과 의존 방향은 aidd-guide/standard/backend/packages.md §1 이 단일 원본이다.

/**
 * local 프로파일인가 — sample 모듈 포함 여부를 가르는 유일한 기본 판정이다(ARCH-027).
 *
 * settings 가 project 보다 먼저 평가되므로 여기가 원본이고, `bootstrap/build.gradle.kts` 는
 * `gradle.extra` 로 **같은 값을 읽는다.** 태스크 이름으로 추정하지 않는다 — `bootRun` 은
 * local뿐 아니라 개발·운영 프로파일도 실행할 수 있고, 축약 태스크명도 같은 태스크가 된다.
 *
 * 프로파일을 주지 않는 일반 build·check·bootJar는 제품 변형이다. 서버를 실제로 띄우는
 * bootRun은 bootstrap 태스크가 `-Pprofile` 누락을 태스크 실행 전에 거부한다.
 */
val isLocalProfile = providers.gradleProperty("profile")
    .map(String::trim)
    .map { it == "local" }
    .getOrElse(false)
gradle.extra["isLocalProfile"] = isLocalProfile

/**
 * 배포 경계 — 통합 Hub 는 PostgreSQL 전용이고, 독립 템플릿만 다중 DBMS 를 지원한다(ADR-008).
 *
 * 프로파일은 어느 환경에서 애플리케이션을 실행하는지, 이 값은 어느 제품을 패키징·검증하는지
 * 가른다. 둘을 같은 옵션으로 합치면 local 개발 중에 템플릿 드라이버를 실은 Hub JAR 이 나오거나,
 * 반대로 템플릿 이식성 시험이 Hub 정책에 묶인다. 기본값은 실제 통합 제품인 hub 이며,
 * 독립 템플릿의 빌드·시험은 항상 `-Pdeployment=template` 으로 명시한다.
 */
val deploymentVariant = providers.gradleProperty("deployment")
    .map(String::trim)
    .getOrElse("hub")
require(deploymentVariant in setOf("hub", "template")) {
    "-Pdeployment 는 hub 또는 template 이어야 합니다: $deploymentVariant"
}
gradle.extra["deploymentVariant"] = deploymentVariant

/** local 프로파일만 sample 포함이 기본이다. 명시한 옵션은 이 기본값보다 우선한다. */
val includeSample = providers.gradleProperty("includeSample")
    .map(String::toBoolean)
    .getOrElse(isLocalProfile)
gradle.extra["includeSample"] = includeSample

include(
    "backend:core",
    "backend:support",
    "backend:domain",
    "backend:api",
    "backend:modules:system",
    "backend:modules:admin",
    "backend:modules:portal",
    "backend:bootstrap",
)

if (includeSample) {
    include("backend:modules:sample")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
