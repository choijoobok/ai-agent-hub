// domain — 백오피스 공통 도메인. api 를 참조하지 않는다(backend/packages.md).
dependencies {
    api(project(":backend:core"))
    api(project(":backend:support"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // PARAM_JSON 의 구문 검증 — screen/admin/system/batch/schedule/requirements.md · frontend/grid.md.
    //
    // 프론트가 JSON.parse 로 먼저 보지만 그것은 UX 이지 방어가 아니다(backend/security.md) — 화면 밖
    // 경로는 그 검사를 지나지 않는다. sys/batch.md 가 "저장 시 JSON 구문 검증으로 완화한다" 고
    // 적은 대가가 여기서 값을 하려면 서버에도 파서가 있어야 한다.
    //
    // core 는 jackson-annotations 만 갖는다(직렬화 힌트용). 읽는 것은 databind 라 여기서
    // 따로 가져오며, 런타임에는 웹 스타터가 이미 끌고 와 새로 실리는 것이 없다.
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // 비밀번호 해시. 인증 흐름이 아니라 해시 알고리즘만 쓴다 — 필터 체인은 api 의 것이다(backend/packages.md).
    implementation("org.springframework.security:spring-security-crypto")

    // QueryDSL — 동적 조건 목록 조회의 표준(backend/packages.md).
    // 원본 QueryDSL 은 유지보수 중단, OpenFeign 포크를 쓴다(stack.md).
    //
    // 좌표가 원본과 다르다. 포크의 querydsl-jpa 는 jakarta 네이티브라 분류자가 없고,
    // 애너테이션 프로세서만 jakarta 분류자를 갖는다. 원본 시절의
    // `querydsl-jpa:...:jakarta` 를 그대로 쓰면 Maven Central 에 없는 아티팩트가 된다.
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jakarta") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // 유효 권한 계산은 도메인의 판정 로직이다 — 저장소를 mock 하면 DB 없이 검사할 수 있고,
    // 그래야 PR 마다 도는 값싼 게이트가 된다(backend/persistence.md 의 Testcontainers 는 그 위층이다).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 메시지 버전의 실제 JPA 원자 증가·롤백·동시 저장 회귀 시험.
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly(libs.driver.postgresql)

    testImplementation(libs.querydsl.jpa)
    testAnnotationProcessor(variantOf(libs.querydsl.apt) { classifier("jakarta") })
    testAnnotationProcessor("jakarta.persistence:jakarta.persistence-api")
    testAnnotationProcessor("jakarta.annotation:jakarta.annotation-api")
}

// 실제 PostgreSQL 배치 경합은 명시한 독립 테스트 DB에서만 실행한다. 일반 단위 게이트와 분리한다.
tasks.named<Test>("test") { exclude("**/BatchPostgresTest*") }
tasks.register<Test>("batchPostgresTest") {
    description = "독립 PostgreSQL에서 배치 접수·행 잠금·lease 경합을 검증한다"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*BatchPostgresTest") }
    systemProperty("batch.pg.url", providers.gradleProperty("batchPgUrl").orElse("").get())
    maxHeapSize = "384m"
}
