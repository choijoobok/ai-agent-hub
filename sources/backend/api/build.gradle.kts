// api — REST 컨트롤러 · DTO · 보안 설정 · 필터/인터셉터 · 예외 핸들러(backend/packages.md).
dependencies {
    api(project(":backend:core"))
    api(project(":backend:support"))
    api(project(":backend:domain"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework:spring-orm")
    runtimeOnly("org.postgresql:postgresql")

    // @Operation — 시드의 API_NM · API_DESC 원천(backend/api.md). 프로필로 가르지 않는다:
    // 빼면 생성기가 이름을 못 만들고 그 사실이 조용하다.
    api(libs.swagger.annotations)

    // 세션 스토어는 설정으로 고른다 — 둘 다 클래스패스에 두고 코드 분기를 만들지 않는다(backend/security.md).
    //
    // 스타터를 쓴다. Spring Boot 4 는 자동설정을 기술별 모듈로 쪼갰으므로 라이브러리만
    // 넣으면 store-type 프로퍼티를 읽는 자동설정이 없다 — 세션이 조용히 메모리에 남는다.
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.postgresql:postgresql")
    testImplementation("org.liquibase:liquibase-core")
}

tasks.test { useJUnitPlatform { excludeTags("authV2Live") } }
tasks.register<Test>("authV2LiveTest") {
    group = "verification"
    description = "전용 PostgreSQL/Redis의 v2 실제 저장소·HTTP·JPA 계약 시험"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("authV2Live") }
    shouldRunAfter(tasks.test)
}
tasks.register<JavaExec>("authSessionStore") {
    group = "application"
    description = "인증 저장소 설정판의 명시 init/drain/expected-epoch switch(외부 수락 배출 후 실행)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.aiagent.api.auth.v2.SessionProviderTool")
    doFirst { args(providers.gradleProperty("authStoreArgs").get().split(" ").filter { it.isNotBlank() }) }
}
