plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.pitest) apply false
}

// 모듈 공통 규약. 모듈별 책임과 의존 방향은
// aidd-guide/standard/backend/packages.md §1 이 단일 원본이다.
subprojects {
    apply(plugin = "java-library")

    group = "com.aiagent"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        // 20-tech-stack §1.1 — JDK 25 (LTS)
        toolchain { languageVersion = JavaLanguageVersion.of(25) }
    }

    dependencies {
        // Spring Boot BOM. 관리 대상 아티팩트는 버전을 명시하지 않는다 — stack.md
        add("implementation", platform(rootProject.libs.spring.boot.bom))
        add("annotationProcessor", platform(rootProject.libs.spring.boot.bom))
        add("testImplementation", platform(rootProject.libs.spring.boot.bom))
        add("testAnnotationProcessor", platform(rootProject.libs.spring.boot.bom))

        add("compileOnly", "org.projectlombok:lombok")
        add("annotationProcessor", "org.projectlombok:lombok")
        add("testCompileOnly", "org.projectlombok:lombok")
        add("testAnnotationProcessor", "org.projectlombok:lombok")

        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}

// Full 트랙의 슬라이스 미니게이트 — 업무 규칙이 있는 클래스만 -PmutationTargets 로 좁혀 잰다.
val pitestCoreVersion = libs.versions.pitestCore.get()
val pitestJunit5Version = libs.versions.pitestJunit5.get()
configure(listOf(
    ":backend:api",
    ":backend:core",
    ":backend:domain",
    ":backend:modules:system",
    ":backend:modules:sample",
).mapNotNull(::findProject)) {
    apply(plugin = "info.solidsoft.pitest")
    extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
        providers.gradleProperty("mutationTargets").orNull?.let { patterns ->
            targetClasses.set(patterns.split(',').map(String::trim).filter(String::isNotEmpty).toSet())
        }
        providers.gradleProperty("mutationTests").orNull?.let { patterns ->
            targetTests.set(patterns.split(',').map(String::trim).filter(String::isNotEmpty).toSet())
        }
        pitestVersion.set(pitestCoreVersion)
        junit5PluginVersion.set(pitestJunit5Version)
        mutationThreshold.set(75)
        threads.set(providers.gradleProperty("mutationThreads").map(String::toInt).getOrElse(Runtime.getRuntime().availableProcessors().coerceAtMost(4)))
        outputFormats.set(setOf("XML", "HTML"))
        timestampedReports.set(false)
    }
}
