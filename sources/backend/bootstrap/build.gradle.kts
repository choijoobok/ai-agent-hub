import java.math.BigDecimal
import java.net.URLClassLoader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.springframework.boot.gradle.tasks.run.BootRun

// bootstrap — 실행 모듈. main · application.yml · Liquibase changelog · 프론트 정적 리소스(backend/packages.md).
plugins {
    alias(libs.plugins.spring.boot)
}

// ── 로컬 기동의 기본값 ────────────────────────────────────────────────
// **기본값을 아는 자리는 `-Pprofile` 하나다**(build-deploy.md). local이면 sample을 포함하고
// 프론트를 생략하며, 그 밖의 프로파일과 프로파일 없는 일반 빌드는 반대로 둔다.
//
// 프로파일을 주지 않은 `build`·`bootJar`·`check`의 기본값은 제품 변형이다. 둘을 local로
// 켠 채 제품 JAR을 만들면 sample이 새고(ADR-009), 프론트 임베드가 조용히 빠진다 — 후자는
// 04-release.md가 **가장 늦게 발견되는 사고**라고 적은 바로 그것이다.
//
// 2026-09-14 실측이 이 자리를 만들었다. local에서 sample을 빼면 application-local.yml의
// changelog도 제품 master로 맞춰야 한다. 그렇지 않으면 명시 옵션이 기본값을 이기지 못한다.
//
// 판정의 원본은 `settings.gradle.kts`다 — 거기가 먼저 평가되고 sample **모듈 포함 여부**를
// 같은 값으로 가르기 때문이다. 여기서 다시 계산하지 않는다.
val isLocalProfile = gradle.extra["isLocalProfile"] as Boolean

/** 실제 통합 제품(hub)인가, 독립 백오피스 템플릿(template)인가(ADR-008). */
val deploymentVariant = gradle.extra["deploymentVariant"] as String
val isTemplateDeployment = deploymentVariant == "template"

/**
 * sample은 local 배포 변형에서만 실제 클래스패스에 넣는다(ADR-009).
 *
 * local 프로파일에서만 기본이 켜진다. 명시한 `-PincludeSample`은 기본값보다 우선한다.
 */
val includeSample = gradle.extra["includeSample"] as Boolean

tasks.processResources {
    inputs.property("includeSample", includeSample)
    filesMatching("application-local.yml") {
        if (!includeSample) {
            filter { line -> line.replace("db.changelog-local.yaml", "db.changelog-master.yaml") }
        }
    }
    // 이전 누적 changeSet은 기준선 생성 입력일 뿐 런타임 산출물에는 싣지 않는다.
    exclude(
        "db/changelog/schema/**",
        "db/changelog/data/**",
        "db/changelog/vendor/**",
        "db/changelog/sample/**",
    )
    if (!includeSample) {
        exclude(
            "db/changelog/db.changelog-local.yaml",
            "db/changelog/modules/sample/**",
        )
    }
}

dependencies {
    testImplementation("org.springframework.data:spring-data-redis")
    testImplementation("org.springframework.data:spring-data-jpa")
    implementation(project(":backend:api"))
    implementation(project(":backend:modules:system"))
    if (includeSample) {
        implementation(project(":backend:modules:sample"))
    }
    implementation("org.springframework.boot:spring-boot-starter")
    // 인터셉터 조립은 실행 모듈이 소유한다. WebMvcConfigurer 는 api의 전이 구현에 기대지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // 스키마의 유일한 출처. ddl-auto 는 전 환경 validate 다(backend/persistence.md).
    //
    // liquibase-core 만 넣으면 안 된다. Spring Boot 4 는 자동설정을 기술별 모듈로 쪼갰고,
    // 라이브러리만 있으면 자동설정이 붙지 않아 마이그레이션이 조용히 돌지 않는다.
    // 그 침묵을 ddl-auto=validate 가 깼다 — "missing table" 로 기동이 실패한다.
    implementation("org.springframework.boot:spring-boot-starter-liquibase")

    // 통합 Hub 는 pgvector 때문에 PostgreSQL 하나만 쓴다. 독립 템플릿만 주요 DBMS 드라이버를
    // 함께 싣는다(ADR-008). 고객사 업무 DB·DW 연결 드라이버의 지원 선언은 CON-023에서 별도로
    // 확정하며, 이 애플리케이션 공유 스키마의 런타임 의존성과 섞지 않는다.
    runtimeOnly(libs.driver.postgresql)
    if (isTemplateDeployment) {
        runtimeOnly(libs.driver.oracle)
        runtimeOnly(libs.driver.mysql)
        runtimeOnly(libs.driver.mariadb)
        runtimeOnly(libs.driver.mssql)
    }

    // 메일 발송 — 분실 비밀번호 재설정(AUTH-07)이 코드를 나르는 유일한 경로다.
    //
    // runtimeOnly 인 것이 요점이다. 부르는 쪽(support 의 Mailer)은 spring-context 의
    // org.springframework.mail.MailSender 만 알고, 구현(jakarta.mail)과 자동설정은 실행
    // 시점에만 있으면 된다 — 컴파일 경로에 두면 support 가 메일 구현에 묶인다.
    //
    // 스타터가 있어도 spring.mail.host 가 없으면 발송 빈이 만들어지지 않는다. 그것이
    // "설정만 하면 돈다" 의 다른 쪽 절반이며, 빈이 없을 때의 동작은 Mailer 가 정한다.
    runtimeOnly("org.springframework.boot:spring-boot-starter-mail")

    // 의존 방향 규칙은 빌드가 강제한다(backend/packages.md).
    testImplementation(libs.archunit.junit5)
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // 시드에 박힌 해시가 문서화된 비밀번호와 맞는지 검사한다 — SeedAdminPasswordTest.
    testImplementation("org.springframework.security:spring-security-crypto")

    // ── 독립 템플릿 이식성 — VendorPortabilityTest(backend/persistence.md · playbook/INDEX.md) ───
    //
    // 버전을 적지 않는다. Spring Boot BOM 이 testcontainers 를 관리하므로(stack.md) 여기에
    // 숫자를 두면 같은 것의 두 원본이 생긴다.
    //
    // 이름이 testcontainers-* 다. Testcontainers 2.0 이 모듈 좌표를 그렇게 바꿨고, 1.x 이름
    // (org.testcontainers:mysql)은 그 버전에 아티팩트가 없다 — 스파이크가 쓰던 이름을 그대로
    // 옮기면 BOM 을 따로 얹어 1.21.3 을 끌어오게 되고, 그러면 코어 2.0.5 위에 모듈 1.21.3 이
    // 얹혀 도는 조합이 된다. 그 어긋남은 컨테이너를 띄우는 순간에야 드러난다.
    //
    // 태그가 붙은 시험 클래스를 컴파일하려면 Testcontainers 핵심은 어느 변형에도 필요하다.
    // 실제 벤더 컨테이너 모듈은 template 변형에만 둔다. Hub 의 보통 test 는 portability 태그를
    // 실행하지 않으며, Hub JAR에는 이 테스트 의존성이 들어가지 않는다.
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    if (isTemplateDeployment) {
        testImplementation("org.testcontainers:testcontainers-jdbc")
        testImplementation("org.testcontainers:testcontainers-postgresql")
        testImplementation("org.testcontainers:testcontainers-mysql")
        testImplementation("org.testcontainers:testcontainers-mariadb")
        testImplementation("org.testcontainers:testcontainers-mssqlserver")
        testImplementation("org.testcontainers:testcontainers-oracle-free")
    }
}

// ── 4벤더 이식성 검사 ─────────────────────────────────────────────────
//
// 평소의 test 에서 빼 둔다. Docker 가 있어야 돌고 벤더당 수십 초가 드는데, `./gradlew test`
// 가 이것을 끌고 가면 개발 머신에서 매번 건너뛰는 검사가 되고 — 건너뛰는 검사는 곧
// 건너뛴 채 초록이 된다.
//
// 소스셋을 새로 만들지 않는다. changelog 와 시드 CSV 가 이 모듈의 리소스이므로 test 셋이
// 이미 그것을 보고 있고, 셋을 가르면 같은 리소스를 두 번 배선하게 된다.
tasks.test {
    useJUnitPlatform { excludeTags("portability", "systemAuthLive", "sourceNotificationLive") }
    systemProperty("includeSample", includeSample)
}

tasks.register<Test>("sourceNotificationTest") {
    group = "verification"
    description = "전용 PostgreSQL에서 역할 수신자·동일 트랜잭션 알림 저장을 검증한다"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("sourceNotificationLive") }
    outputs.upToDateWhen { false }
}

tasks.register<Test>("systemAuthTest") {
    group = "verification"
    description = "전용 PostgreSQL에서 실제 관리자 인증·두 노드 권한·초기 설치를 검증한다"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("systemAuthLive") }
    systemProperty("includeSample", includeSample)
    systemProperty("systemAuth.profile", if (isLocalProfile) "local" else "system-auth-test")
    outputs.upToDateWhen { false }
}

if (isTemplateDeployment) {
    tasks.register<Test>("portabilityTest") {
        group = "verification"
        description = "독립 템플릿의 실제 changelog 를 4벤더에 태운다 — 선다 → 걷어낸다 → 다시 선다(backend/persistence.md)"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { includeTags("portability") }
        /*
          벤더 목록은 워크플로가 넘긴다. **없으면 넘기지 않는다** — 빈 문자열로 덮으면 검사의
          기본값(넷)이 사라져 도는 벤더가 0개가 되고, 그 상태가 정확히 이 태스크가 막으려는
          "확인한 적 없는 초록" 이다.
        */
        providers.systemProperty("portability.vendors").orNull?.let { requested ->
            systemProperty("portability.vendors", requested)
        }
        testLogging { showStandardStreams = true }
        // 컨테이너가 도는 동안 UP-TO-DATE 로 건너뛰면 확인한 적 없는 초록이 된다.
        outputs.upToDateWhen { false }
    }
}

// ── 빌드 정보 ─────────────────────────────────────────────────────────
// 시스템 정보 팝오버(frontend/tabs.md)가 읽는 것 — "지금 도는 서버가 어느 빌드인가" 에 답한다.
//
// 버전 문자열만으로는 답이 되지 않는다. 릴리스 태그를 붙이기 전까지 version 은 계속
// 0.0.1-SNAPSHOT 이고, 그 값으로는 어제 배포와 오늘 배포가 구분되지 않는다. 그래서
// **커밋 해시**를 함께 새긴다.
//
// 게으르게(Provider) 만들지 않고 여기서 바로 읽는다. git 이 없거나 .git 이 없는 곳
// (소스 tarball 배포)에서 `providers.exec` 는 실행 시점에 터지는데, 그 자리가 하필
// 빌드 한복판이다 — 빌드 정보 때문에 빌드가 실패하는 것은 앞뒤가 바뀐 일이다.

/** git 을 물어본다. 실패는 값이 없는 것이며 예외가 아니다. */
fun git(vararg args: String): String =
    runCatching {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(rootProject.layout.projectDirectory.asFile)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) text else ""
    }.getOrDefault("")

val gitCommit: String = git("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }

// 고친 것이 있는 채로 만든 빌드는 **그 커밋이 아니다**. 표시하지 않으면 해시가 거짓말을
// 하게 되고, 그 거짓말은 "그 커밋을 받아 봤는데 증상이 재현되지 않는다" 로 나타난다.
val gitDirty: Boolean = git("status", "--porcelain").isNotEmpty()

springBoot {
    buildInfo {
        properties {
            // 시각은 플러그인이 넣는다(build.time). 여기는 우리 것만 더한다.
            additional.put("commit", if (gitDirty) "$gitCommit-dirty" else gitCommit)
            additional.put("branch", git("rev-parse", "--abbrev-ref", "HEAD").ifEmpty { "unknown" })
        }
    }
}

// ── 프론트엔드 임베드 ──────────────────────────────────────────────────
// npm run build → frontend/dist → 이 모듈의 정적 리소스 → 단일 JAR (build-deploy.md).

val frontendDir = rootProject.layout.projectDirectory.dir("frontend")
val npmCmd = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

/**
 * 백엔드만 만질 때 프론트 빌드를 건너뛴다 — `-PskipFrontend` (build-deploy.md).
 *
 * **local 프로파일은 기본으로 건너뛴다.** 개발 중 화면을 주는 것은 Vite dev 서버이고,
 * 기동할 때마다 도는 `npm run build`의 산출물은 아무도 안 쓴다(02-setup.md §4). 개발·운영
 * 프로파일과 프로파일 없는 일반 빌드는 프론트를 포함한다.
 *
 * **값을 해석한다.** 예전에는 `isPresent` 만 봐서 `-PskipFrontend=false` 도 *건너뛴다* 로
 * 읽혔다. 기본값이 생긴 이상 **끄는 길**이 있어야 한다. 값 없는 `-PskipFrontend` 는 그대로 참이다.
 */
val skipFrontend = providers.gradleProperty("skipFrontend")
    .map { it.isBlank() || it.toBoolean() }
    .getOrElse(isLocalProfile)

val npmInstall by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    commandLine(npmCmd, "ci")
    inputs.files(frontendDir.file("package-lock.json"), frontendDir.file("package.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = frontendDir.asFile
    commandLine(npmCmd, "run", "build")
    environment("VITE_INCLUDE_SAMPLE", includeSample.toString())

    // 입력은 src 만이 아니다. vite.config.ts · tsconfig · index.html · public/ 이 전부
    // 산출물을 바꾸며, 빠뜨리면 고쳐도 UP-TO-DATE 로 통과하고 JAR 에 옛 프론트가 실린다.
    // 아무 경고도 나지 않으므로 배포 후에야 안다.
    inputs.files(
        providers.provider {
            fileTree(frontendDir) {
                exclude("dist/**", "node_modules/**", ".vite/**")
            }
        },
    ).withPropertyName("frontendSources")

    outputs.dir(frontendDir.dir("dist"))
}

tasks.processResources {
    // dependsOn 이 아니라 태스크 출력을 리소스 입력으로 연결한다 — 두 태스크가 같은
    // 디렉터리를 각자 소유하지 않게 하고, clean 후에도 잔여 파일이 남지 않는다.
    if (!skipFrontend) {
        from(npmBuild) { into("static") }
    }
}

// ── 로컬 기동 ──────────────────────────────────────────────────────────
// build-deploy.md. 활성 프로파일은 기동에 **필수**이며, 어디서 띄우든 명시한다.
//
// application.yml에는 spring.profiles.active 기본값이 없다. 데이터소스가 외부에서 우연히
// 주어져도 기본 프로파일로 뜨면 안 되므로 애플리케이션도 별도로 기동을 거부한다.
//
// bootRun은 `-Pprofile`만 받아 애플리케이션 인자로 바꾼다. taskGraph에서 실제 bootRun 태스크를
// 확인하므로 `bootR` 같은 Gradle 축약과 다른 태스크가 bootRun을 의존하는 경우도 빠지지 않는다.
val bootProfile = providers.gradleProperty("profile").map(String::trim)
val bootRunTask = tasks.named<BootRun>("bootRun")
gradle.taskGraph.whenReady {
    if (hasTask(bootRunTask.get())) {
        val activeProfile = bootProfile.orNull?.takeIf(String::isNotEmpty)
            ?: throw GradleException(
                "서버 기동에는 활성 프로파일이 필요하다 — bootRun에 -Pprofile=<local|dev|운영 프로파일>을 지정한다.",
            )
        bootRunTask.get().args("--spring.profiles.active=$activeProfile")
    }
}

// ── 메시지 키 시드 생성 ─────────────────────────────────────────────────
// sys/message.md · seed.md. 키는 코드가 소유하고 값은 사람이 소유한다 — 이 태스크는 키만 쓰며
// SYS_MESSAGE 를 열지 않는다.
//
// 입력이 셋인 것이 generateApiSeed 와 갈리는 자리다(seed.md). 소스에서 사라진 키는 소스
// 스캔으로 알 수 없으므로 직전 시드 파일을 함께 읽어 USE_SCOPE 를 NONE 으로 이어 쓴다.
// 이 입력을 빼면 재생성이 그 행을 떨어뜨리고, loadUpdateData 가 갱신하지 않아 DB 의
// USE_SCOPE 가 옛 값으로 굳는다 — 죽은 키가 영원히 미사용으로 안 보인다.

/** 데이터가 만드는 키의 접두 — 이 태스크가 건드리지 않는다(sys/code.md). */
val dataKeyPrefixes = listOf("menu.", "code.")

abstract class MessageSeedTask : DefaultTask() {

    @get:InputFile abstract val backendKeys: RegularFileProperty

    @get:InputFile abstract val frontendKeys: RegularFileProperty

    @get:Input abstract val dataPrefixes: ListProperty<String>

    /** 생성 대상이자 <b>입력</b>이다 — 그래서 @InputFile 로 선언할 수 없다(seed.md). */
    @get:Internal abstract val seedFile: RegularFileProperty

    /** 참이면 쓰지 않고 커밋본과 비교만 한다 — CI 의 checkSeeds. */
    @get:Input abstract val verifyOnly: Property<Boolean>

    init {
        // 직전 시드가 입력이라 Gradle 의 UP-TO-DATE 판정이 성립하지 않는다. 텍스트 몇 KB 다.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val prefixes = dataPrefixes.get()
        val back = parseJava(backendKeys.get().asFile.readText(Charsets.UTF_8))
        val front = parseTypeScript(frontendKeys.get().asFile.readText(Charsets.UTF_8))

        val file = seedFile.get().asFile
        val previous = if (file.exists()) parseCsv(file.readText(Charsets.UTF_8)) else emptyMap()

        val rows = sortedMapOf<String, Pair<String, String>>()

        // 소스에 있는 키 — 사용처를 매번 다시 계산한다.
        (back.keys + front.keys)
            .filterNot { key -> prefixes.any { key.startsWith(it) } }
            .forEach { key ->
                val scope = when {
                    back.containsKey(key) && front.containsKey(key) -> "BOTH"
                    front.containsKey(key) -> "FRONT"
                    else -> "BACK"
                }
                rows[key] = scope to (back[key] ?: front[key] ?: "")
            }

        // 소스에서 사라진 키 — 행을 지우지 않고 죽었다고 표시만 한다. 번역이 딸려 있다.
        previous.forEach { (key, row) ->
            if (!rows.containsKey(key) && prefixes.none { key.startsWith(it) }) {
                rows[key] = "NONE" to row.second
            }
        }

        val rendered = render(rows)
        if (!verifyOnly.get()) {
            file.parentFile.mkdirs()
            file.writeText(rendered, Charsets.UTF_8)
            logger.lifecycle("메시지 키 ${rows.size}개 — ${file.name}")
            return
        }

        val committed = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        if (committed != rendered) {
            throw GradleException(
                "메시지 키 시드가 소스와 다르다../gradlew generateMessageSeed 를 돌리고 함께 커밋한다 — seed.md.",
            )
        }
        logger.lifecycle("메시지 키 시드 일치 — ${rows.size}개")
    }

    /** 자바 상수 선언과 바로 앞의 Javadoc. */
    private fun parseJava(source: String): Map<String, String> =
        scan(source, Regex("""public\s+static\s+final\s+String\s+\w+\s*=\s*"([^"]+)"\s*;"""))

    /** 타입스크립트 MSG 항목과 바로 앞의 JSDoc. */
    private fun parseTypeScript(source: String): Map<String, String> =
        scan(source, Regex("""^\s*[A-Z][A-Z0-9_]*\s*:\s*'([^']+)'\s*,"""))

    /**
     * 주석은 <b>바로 앞에 붙은 것만</b> 쓴다. 사이에 다른 줄이 있으면 그 주석은 이 키의 것이
     * 아니며, 그것을 설명으로 실으면 관리 화면에서 <b>남의 설명</b>을 보게 된다.
     */
    private fun scan(source: String, declaration: Regex): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val doc = StringBuilder()
        var inDoc = false

        source.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("/**") -> {
                    inDoc = true
                    doc.setLength(0)
                    doc.append(line.removePrefix("/**").removeSuffix("*/"))
                    if (line.endsWith("*/")) inDoc = false
                }
                inDoc -> {
                    doc.append(' ').append(line.removePrefix("*").removeSuffix("*/"))
                    if (line.endsWith("*/")) inDoc = false
                }
                else -> {
                    val match = declaration.find(raw)
                    if (match != null) {
                        result[match.groupValues[1]] = clean(doc.toString())
                        doc.setLength(0)
                    } else if (line.isNotEmpty()) {
                        // 주석과 선언 사이에 뭔가 있었다 — 그 주석은 버린다.
                        doc.setLength(0)
                    }
                }
            }
        }
        return result
    }

    /** Javadoc 표기를 걷어내고 한 줄로 만든다. MSG_DESC 는 500자다. */
    private fun clean(doc: String): String =
        doc.replace(Regex("""\{@\w+\s+([^}]*)}"""), "$1")
            .replace(Regex("""</?[a-zA-Z]+>"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(500)

    private fun parseCsv(text: String): Map<String, Pair<String, String>> {
        val rows = linkedMapOf<String, Pair<String, String>>()
        text.lines().drop(1).filter { it.isNotBlank() }.forEach { line ->
            val cells = splitCsv(line)
            if (cells.size >= 2) rows[cells[0]] = cells[1] to cells.getOrElse(2) { "" }
        }
        return rows
    }

    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                quoted && ch == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    cells.add(cell.toString())
                    cell.setLength(0)
                }
                else -> cell.append(ch)
            }
            index++
        }
        cells.add(cell.toString())
        return cells
    }

    /** 줄바꿈을 LF 로 고정한다 — 그러지 않으면 OS 마다 checkMessageSeed 결과가 갈린다. */
    private fun render(rows: Map<String, Pair<String, String>>): String {
        val out = StringBuilder("MSG_KEY,USE_SCOPE,MSG_DESC\n")
        rows.forEach { (key, row) ->
            out.append(quote(key)).append(',')
                .append(quote(row.first)).append(',')
                .append(quote(row.second)).append('\n')
        }
        return out.toString()
    }

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

fun Project.messageSeedTask(name: String, verify: Boolean) =
    tasks.register<MessageSeedTask>(name) {
        group = "seed"
        description = if (verify) {
            "메시지 키 시드가 소스와 일치하는지 확인한다 — seed.md"
        } else {
            "프론트 MSG 와 백엔드 MsgKey 를 읽어 008-init-message-key.csv 를 만든다 — sys/message.md"
        }
        backendKeys.set(
            rootProject.layout.projectDirectory.file(
                "backend/core/src/main/java/com/aiagent/core/message/MsgKey.java",
            ),
        )
        frontendKeys.set(
            rootProject.layout.projectDirectory.file("frontend/src/shared/i18n/messageKeys.ts"),
        )
        seedFile.set(
            layout.projectDirectory.file(
                "src/main/resources/db/changelog/data/008-init-message-key.csv",
            ),
        )
        dataPrefixes.set(dataKeyPrefixes)
        verifyOnly.set(verify)
    }

val generateMessageSeed = messageSeedTask("generateMessageSeed", verify = false)
val checkMessageSeed = messageSeedTask("checkMessageSeed", verify = true)

// ── 메뉴 시드 생성 — seed.md ───────────────────────────────────
//
//   ./gradlew generateMenuSeed    menu/menu-tree.yaml → 003-init-menu.csv
//   ./gradlew checkMenuSeed       재생성 결과 == 커밋본
//
// 006(API)·008(메시지 키)과 갈리는 자리는 **DB 반영 방식**이지 생성 방식이 아니다. 여기는
// 선언 파일을 읽어 CSV 를 뱉을 뿐이고, upsert 냐 전건 교체냐는 003 의 changeSet 이 정한다.
//
// 선언에 없는 것을 CSV 가 담지 않는다 — SORT_NO · ICON_CD · USE_YN 은 운영이 정하는 값이라
// loadUpdateData 가 건드리면 안 된다(seed.md). MENU_NM 은 메뉴명이 아니라 메시지 키이며
// 코드에서 만든다(sys/menu.md).

/**
 * 메뉴 선언 파일의 파서 — 두 태스크가 나눠 쓴다.
 *
 * generateMenuSeed 는 CSV 를 만들고 generateApiSeed 는 `MENU_CD ↔ MENU_URL` 사슬의 첫
 * 마디로 쓴다. 그 전에는 뒤엣것이 `003-init-menu.yaml` 한 파일을 파싱했고, 메뉴가 파일로
 * 늘어나면 **둘째 파일을 읽지 못해** 그 화면의 API 가 전부 미매핑(=403)이 됐다(seed.md).
 *
 * <b>`object` 인 것이 필요해서다.</b> 스크립트 최상위 함수를 태스크가 부르면 그 태스크
 * 클래스가 <b>스크립트 인스턴스를 잡는 inner class</b> 가 되고, Gradle 이 만들지 못한다.
 */
object MenuDeclaration {

    /** 선언 파일의 메뉴 한 줄. */
    data class Row(val code: String, val parent: String?, val url: String?, val export: Boolean)

    /**
     * `menus:` 목록을 읽는다.
     *
     * 주석은 `#` 앞까지만 본다 — 파일 머리의 트리 그림이 `url` 같은 글자를 담고 있어서다.
     */
    fun parse(text: String): List<Row> {
        val rows = mutableListOf<Row>()
        var code: String? = null
        var parent: String? = null
        var url: String? = null
        var export = false

        fun flush() {
            val current = code ?: return
            rows.add(Row(current, parent, url, export))
            code = null
            parent = null
            url = null
            export = false
        }

        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            when {
                line.startsWith("- code:") -> {
                    flush()
                    code = line.removePrefix("- code:").trim()
                }
                line.startsWith("parent:") -> parent = line.removePrefix("parent:").trim()
                line.startsWith("url:") -> url = line.removePrefix("url:").trim()
                line.startsWith("export:") -> export = line.removePrefix("export:").trim() == "true"
            }
        }
        flush()
        return rows
    }
}

abstract class MenuSeedTask : DefaultTask() {

    @get:InputFile abstract val declaration: RegularFileProperty

    /** 생성 대상. 검증 모드에서는 읽기만 하므로 @OutputFile 로 선언하지 않는다. */
    @get:Internal abstract val seedFile: RegularFileProperty

    @get:Input abstract val verifyOnly: Property<Boolean>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val rows = MenuDeclaration.parse(declaration.get().asFile.readText(Charsets.UTF_8))
        if (rows.isEmpty()) {
            // 파서가 헛돌면 CSV 가 비고, 그러면 전 메뉴가 ORPHAN 이 된다 — 조용히 지나가면
            // 증상이 "로그인했더니 사이더가 비었다" 로 나타난다.
            throw GradleException("메뉴 선언을 하나도 읽지 못했다 — menu/menu-tree.yaml 을 확인한다(seed.md).")
        }

        val codes = rows.map { it.code }
        codes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.let { duplicated ->
            if (duplicated.isNotEmpty()) {
                throw GradleException("메뉴 코드가 겹친다: ${duplicated.joinToString()}")
            }
        }
        val known = codes.toSet()
        rows.filter { it.parent != null && it.parent !in known }.forEach {
            // 두면 배포에서 자기참조 FK 가 거부한다 — 그때는 원인이 시드 파일에 있는 것처럼 보인다.
            throw GradleException("${it.code} 의 상위 ${it.parent} 가 선언에 없다")
        }

        val rendered = render(rows)
        val file = seedFile.get().asFile
        if (!verifyOnly.get()) {
            file.parentFile.mkdirs()
            file.writeText(rendered, Charsets.UTF_8)
            logger.lifecycle("메뉴 ${rows.size}개 — ${file.name}")
            return
        }

        val committed = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        if (committed != rendered) {
            throw GradleException(
                "메뉴 시드가 선언과 다르다../gradlew generateMenuSeed 를 돌리고 함께 커밋한다 — seed.md.",
            )
        }
        logger.lifecycle("메뉴 시드 일치 — ${rows.size}개")
    }

    /**
     * 빈 칸은 `NULL` 로 적는다 — Liquibase 의 `loadUpdateData` 가 그 글자를 널로 읽는다.
     * 빈 문자열로 두면 `UP_MENU_CD=''` 인 행이 생기고 자기참조 FK 가 거부한다.
     */
    private fun render(rows: List<MenuDeclaration.Row>): String {
        val out = StringBuilder("MENU_CD,UP_MENU_CD,MENU_NM,MENU_URL,EXPORT_YN,SRC_CD\n")
        rows.forEach { row ->
            out.append(row.code).append(',')
                .append(row.parent ?: "NULL").append(',')
                // 메뉴명이 아니라 메시지 키다 — 이름의 단일 원본은 SYS_MESSAGE 다(sys/menu.md).
                .append("menu.").append(row.code).append(',')
                .append(row.url ?: "NULL").append(',')
                .append(if (row.export) "Y" else "N").append(',')
                .append("SEED").append('\n')
        }
        return out.toString()
    }
}

fun menuSeedTask(name: String, verify: Boolean) =
    tasks.register<MenuSeedTask>(name) {
        group = "seed"
        description = if (verify) {
            "메뉴 시드가 선언과 일치하는지 확인한다 — seed.md"
        } else {
            "menu/menu-tree.yaml 을 읽어 003-init-menu.csv 를 만든다 — seed.md"
        }
        declaration.set(layout.projectDirectory.file("src/main/resources/menu/menu-tree.yaml"))
        seedFile.set(
            layout.projectDirectory.file("src/main/resources/db/changelog/data/003-init-menu.csv"),
        )
        verifyOnly.set(verify)
    }

val generateMenuSeed = menuSeedTask("generateMenuSeed", verify = false)
val checkMenuSeed = menuSeedTask("checkMenuSeed", verify = true)

// ── 인가 시드 생성 — backend/api.md · seed.md · 33 ───────────────────────────────
//
//   ./gradlew generateApiSeed     컨트롤러 + 프론트 endpoints + 메뉴 시드 → 006·007 CSV
//   ./gradlew checkApiSeed        재생성 결과 == 커밋본 (CI 의 check 에 달려 있다)
//
// 008(메시지)과 방식이 다르다. 여기는 전건 삭제 → 전건 삽입이며(seed.md) 그 행에
// 사람의 것이 딸려 있지 않기 때문이다 — 지운 API 의 행은 그대로 사라져야 한다.
//
// 바이트코드를 읽는다. @Operation 과 @ApiActionType 이 RUNTIME 유지인 것이 그래서이며
// (backend/api.md), Javadoc 을 파싱하는 대안은 SOURCE 유지라 스캔이 보지 못한다.
//
// MENU_CD ↔ API_URL 의 사슬은 셋을 잇는다.
//
// menu/menu-tree.yaml MENU_CD ↔ MENU_URL (seed.md — 시드가 아니라 선언이다)
//   screenRegistry.ts    MENU_URL → @/{features|modules/{module}/features}/{도메인}[/{화면}]
//   등록된 기능 폴더의 api/endpoints.ts  → /service/** URL 들
//
// 마디가 둘일 수 있는 것이 playbook/INDEX.md 다 — 샘플 여섯은 한 애그리거트를 나눠 쓰므로 폴더가
// 인가 경계이고, 한 폴더에 둘을 두면 두 화면의 엔드포인트가 서로에게 매핑된다.
//
// 사슬의 어느 마디도 이 태스크를 위해 새로 만든 선언이 아니다 — 셋 다 화면이 돌기 위해
// 이미 있어야 하는 것이며, 그래서 "선언 = 사용" 이 구조적으로 성립한다(backend/api.md).

abstract class ApiSeedTask : DefaultTask() {

    /** 컨트롤러가 컴파일된 자리. 이 아래의 클래스만 스캔한다. */
    @get:InputFiles abstract val controllerClasses: ConfigurableFileCollection

    /** 애너테이션 클래스를 읽기 위한 클래스패스 — Spring · swagger · 우리 모듈. */
    @get:InputFiles abstract val scanClasspath: ConfigurableFileCollection

    /**
     * 메뉴 **선언** 파일이다 — 시드가 아니다(seed.md).
     *
     * 그 전에는 `003-init-menu.yaml` 한 파일을 파싱했고, 메뉴가 changeset 파일로 늘어나는
     * 모델에서는 **둘째 파일을 읽지 못해** 그 화면의 API 가 전부 미매핑(=403)이 됐다.
     * 지금은 `generateMenuSeed` 와 **같은 입력**을 읽으므로 그 질문 자체가 없다.
     */
    @get:InputFile abstract val menuDeclaration: RegularFileProperty

    @get:InputFile abstract val screenRegistry: RegularFileProperty

    /** local 변형일 때만 합치는 샘플 화면 등록부(ADR-009). */
    @get:InputFile abstract val sampleScreenRegistry: RegularFileProperty

    @get:Input abstract val includeSampleScreens: Property<Boolean>

    /** `features/{도메인}[/{화면}]/api/endpoints.ts` 들이 있는 뿌리. */
    @get:InputDirectory abstract val frontendSrc: DirectoryProperty

    /** 생성 대상. 검증 모드에서는 읽기만 하므로 @OutputFile 로 선언하지 않는다. */
    @get:Internal abstract val apiSeed: RegularFileProperty

    @get:Internal abstract val menuApiSeed: RegularFileProperty

    @get:Input abstract val verifyOnly: Property<Boolean>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val apis = scanControllers()
        if (apis.isEmpty()) {
            // 스캔이 비면 시드가 통째로 비고, 그러면 전 화면이 403 이 된다. 조용히 지나가면
            // 원인이 "권한이 없다" 로 보인다 — 클래스패스가 어긋난 것이다.
            throw GradleException("업무 API 를 하나도 찾지 못했다 — 스캔 대상을 확인한다(backend/api.md).")
        }
        val mappings = scanMappings(apis.keys)

        report(apis, mappings)

        writeOrVerify(apiSeed.get().asFile, renderApis(apis), "006-init-api.csv")
        writeOrVerify(menuApiSeed.get().asFile, renderMappings(mappings), "007-init-menu-api.csv")
    }

    // ── 백엔드 스캔 ──────────────────────────────────────────────────

    private fun scanControllers(): Map<String, ApiRow> {
        val urls = (controllerClasses.files + scanClasspath.files)
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        // 부모를 플랫폼 로더로 두어 Gradle 자신의 클래스와 섞이지 않게 한다.
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            val rows = sortedMapOf<String, ApiRow>()

            controllerClasses.asFileTree.matching { include("**/*.class") }.forEach { file ->
                val root = controllerClasses.files.first { file.startsWith(it) }
                val name = file.relativeTo(root).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
                if (name.contains('$')) return@forEach

                val type = runCatching { Class.forName(name, false, loader) }.getOrNull() ?: return@forEach
                if (annotationOf(type.annotations, "RestController") == null) return@forEach

                val base = pathsOf(annotationOf(type.annotations, "RequestMapping")).firstOrNull() ?: ""

                type.declaredMethods.forEach { method ->
                    method.annotations.forEach { annotation ->
                        val simple = annotation.annotationClass.java.simpleName
                        if (!simple.endsWith("Mapping")) return@forEach
                        pathsOf(annotation).forEach { suffix ->
                            val url = join(base, suffix)
                            if (!SERVICE_PATH.matches(url)) return@forEach
                            val existing = rows.put(url, rowOf(url, method))
                            if (existing != null) {
                                throw GradleException("업무 API 경로가 겹친다 — $url")
                            }
                        }
                    }
                }
            }
            return rows
        }
    }

    private fun rowOf(url: String, method: java.lang.reflect.Method): ApiRow {
        val operation = annotationOf(method.annotations, "Operation")
        val summary = (operation?.let { invoke(it, "summary") } as? String).orEmpty().trim()
        val description = (operation?.let { invoke(it, "description") } as? String).orEmpty().trim()

        if (summary.isEmpty()) {
            throw GradleException(
                "@Operation(summary=…) 이 없다 — $url. API_NM 을 만들 방법이 없다(backend/api.md).",
            )
        }
        val declared = annotationOf(method.annotations, "ApiActionType")
        val action = declared?.let { invoke(it, "value").toString() } ?: actionOfPath(url)

        return ApiRow(summary.take(100), description.take(500), action, declared != null)
    }

    /** 마지막 마디의 접두가 액션을 정한다 — backend/api.md 결정 1. */
    private fun actionOfPath(url: String): String {
        val last = url.substringAfterLast('/')
        return when {
            READ_PREFIXES.any { last.startsWith(it) } -> "READ"
            SAVE_PREFIXES.any { last.startsWith(it) } -> "SAVE"
            last.startsWith("export") -> "EXPORT"
            else -> "EXECUTE"
        }
    }

    private fun annotationOf(annotations: Array<Annotation>, simpleName: String): Annotation? =
        annotations.firstOrNull { it.annotationClass.java.simpleName == simpleName }

    private fun pathsOf(annotation: Annotation?): List<String> {
        if (annotation == null) return emptyList()
        val value = runCatching { invoke(annotation, "value") }.getOrNull()
        val paths = (value as? Array<*>)?.filterIsInstance<String>().orEmpty()
        return paths.filter { it.isNotBlank() }
    }

    private fun invoke(annotation: Annotation, member: String): Any? =
        annotation.annotationClass.java.getMethod(member).invoke(annotation)

    private fun join(base: String, suffix: String): String =
        (base.removeSuffix("/") + "/" + suffix.removePrefix("/")).replace("//", "/")

    // ── 프론트 · 메뉴 스캔 ───────────────────────────────────────────

    private fun scanMappings(known: Set<String>): List<Pair<String, String>> {
        val menuUrls = MenuDeclaration.parse(menuDeclaration.get().asFile.readText(Charsets.UTF_8))
            .filter { it.url != null }
            .map { it.code to it.url!! }
        val screens = linkedMapOf<String, String>()
        screens.putAll(parseScreenRegistry(screenRegistry.get().asFile.readText(Charsets.UTF_8)))
        if (includeSampleScreens.get()) {
            screens.putAll(parseScreenRegistry(sampleScreenRegistry.get().asFile.readText(Charsets.UTF_8)))
        }

        /*
          <b>화면은 등록됐는데 메뉴가 없다</b> — 언제나 실수다(seed.md). 그 화면은 메뉴
          트리에 없어 열 수가 없고, 매핑도 안 만들어져 부를 수 있어도 403 이다.

          반대 방향(선언에 있는데 SCREENS 에 없음)은 정상이다 — screenRegistry 주석이
          "데이터가 앞서간 것이 아니라 아직 만들지 않은 화면" 이라고 이미 적었다.
        */
        val declaredUrls = menuUrls.map { it.second }.toSet()
        (screens.keys - declaredUrls).sorted().let { missing ->
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "screenRegistry 에 있는데 메뉴 선언에 없다 — 열 수 없는 화면이다: " +
                        missing.joinToString() + " (menu/menu-tree.yaml · seed.md)",
                )
            }
        }

        val rows = sortedSetOf<Pair<String, String>>(compareBy({ it.first }, { it.second }))

        menuUrls.forEach { (menuCd, menuUrl) ->
            val sourceDir = screens[menuUrl] ?: return@forEach
            val file = frontendSrc.get().asFile.resolve("$sourceDir/api/endpoints.ts")
            if (!file.exists()) return@forEach

            ENDPOINT_URL.findAll(file.readText(Charsets.UTF_8)).forEach { match ->
                val apiUrl = "/api" + match.groupValues[1]
                if (apiUrl !in known) {
                    // 선언과 구현이 어긋났다. 이대로 시드를 만들면 FK 위반으로 배포에서
                    // 터지고, 그때는 원인이 시드 파일에 있는 것처럼 보인다.
                    throw GradleException(
                        "endpoints 에 선언된 $apiUrl 을 컨트롤러에서 찾지 못했다 — ${file.name}",
                    )
                }
                rows.add(menuCd to apiUrl)
            }
        }
        return rows.toList()
    }

    private fun parseScreenRegistry(text: String): Map<String, String> {
        val screens = linkedMapOf<String, String>()
        SCREEN_ENTRY.findAll(text).forEach { match ->
            screens[match.groupValues[1]] = match.groupValues[2]
        }
        return screens
    }

    // ── 출력 ─────────────────────────────────────────────────────────

    private fun report(apis: Map<String, ApiRow>, mappings: List<Pair<String, String>>) {
        val mapped = mappings.map { it.second }.toSet()
        val unmapped = apis.keys - mapped
        if (unmapped.isNotEmpty()) {
            // sys/menu.md 의 미매핑이다. 잘못은 아니지만 아무도 권한을 가질 수 없으므로 곧 403 이다.
            logger.warn("메뉴 매핑이 0건인 API ${unmapped.size}개 — 전부 403 이다: ${unmapped.joinToString()}")
        }
        val execute = apis.filterValues { it.action == "EXECUTE" && !it.declared }.keys
        if (execute.isNotEmpty()) {
            // 접두 오타(svae)가 조용히 EXECUTE 로 떨어지는 자리다 — backend/api.md 결과.
            logger.lifecycle("접두로 EXECUTE 가 된 API ${execute.size}개: ${execute.joinToString()}")
        }
    }

    private fun renderApis(apis: Map<String, ApiRow>): String {
        val out = StringBuilder("API_URL,API_NM,API_DESC,ACTION_CD\n")
        apis.forEach { (url, row) ->
            out.append(quote(url)).append(',')
                .append(quote(row.name)).append(',')
                .append(quote(row.description)).append(',')
                .append(quote(row.action)).append('\n')
        }
        return out.toString()
    }

    private fun renderMappings(mappings: List<Pair<String, String>>): String {
        val out = StringBuilder("MENU_CD,API_URL\n")
        mappings.forEach { (menuCd, apiUrl) ->
            out.append(quote(menuCd)).append(',').append(quote(apiUrl)).append('\n')
        }
        return out.toString()
    }

    private fun writeOrVerify(file: File, rendered: String, label: String) {
        if (!verifyOnly.get()) {
            file.parentFile.mkdirs()
            file.writeText(rendered, Charsets.UTF_8)
            logger.lifecycle("$label — ${rendered.lines().size - 2}행")
            return
        }
        val committed = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        if (committed != rendered) {
            throw GradleException(
                "$label 이 소스와 다르다../gradlew generateApiSeed 를 돌리고 함께 커밋한다 — seed.md.",
            )
        }
    }

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    data class ApiRow(
        val name: String,
        val description: String,
        val action: String,
        /** 접두가 아니라 @ApiActionType 이 정했다 — EXECUTE 보고에서 뺀다. */
        val declared: Boolean,
    )

    private companion object {
        val SERVICE_PATH = Regex("""/api/[A-Za-z0-9_-]+/service/.+""")
        val READ_PREFIXES = listOf("get", "search", "find", "retrieve")
        val SAVE_PREFIXES = listOf("save", "insert", "update", "delete")
        val ENDPOINT_URL = Regex("""'(/[A-Za-z0-9_-]+/service/[^']+)'""")
        /*
          마디를 하나로 못 박지 않는다. playbook/INDEX.md 가 화면마다 폴더를 요구하므로 샘플의
          여섯은 `modules/sample/features/mdvertical`처럼 모듈과 기능 마디를 모두 가지며,
          기능 마디를 못 잡으면 `modules/sample/api/endpoints.ts`를 찾다가 조용히 건너뛴다 —
          그 화면의 API 가 전부 미매핑(=403)이고, 실패가 아니라 침묵으로 나온다.

          `/pages/` 앞까지를 잡는 것은 등록 줄의 모양이 언제나
          `@/{features|modules/{module}/features}/{…}/pages/{Component}` 이기 때문이다.
        */
        val SCREEN_ENTRY =
            Regex("""'(/[^']*)':\s*lazy\([\s\S]{0,400}?@/((?:features|modules/[A-Za-z0-9_-]+/features)/[A-Za-z0-9_/-]+)/pages/""")
    }
}

fun Project.apiSeedTask(name: String, verify: Boolean) =
    tasks.register<ApiSeedTask>(name) {
        val controllerProjects = buildList {
            add(":backend:api")
            add(":backend:modules:system")
            if (includeSample) {
                add(":backend:modules:sample")
            }
        }
        group = if (verify) "verification" else "seed"
        description = if (verify) {
            "인가 시드가 컨트롤러·화면 선언과 일치하는지 확인한다 — seed.md"
        } else {
            "컨트롤러와 프론트 endpoints 를 읽어 006·007 CSV 를 만든다 — backend/api.md"
        }
        dependsOn(controllerProjects.map { "$it:classes" })
        controllerClasses.from(
            controllerProjects.map {
                rootProject.project(it).the<SourceSetContainer>()["main"].output.classesDirs
            },
        )
        // bootstrap 의 런타임 클래스패스가 api 와 그 의존을 전부 담는다 — 남의 설정을
        // 해석하지 않는다.
        scanClasspath.from(configurations.named("runtimeClasspath"))
        menuDeclaration.set(
            layout.projectDirectory.file("src/main/resources/menu/menu-tree.yaml"),
        )
        screenRegistry.set(
            rootProject.layout.projectDirectory.file(
                "frontend/src/shared/routes/screenRegistry.ts",
            ),
        )
        sampleScreenRegistry.set(
            rootProject.layout.projectDirectory.file(
                "frontend/src/shared/routes/sampleScreens.local.ts",
            ),
        )
        includeSampleScreens.set(includeSample)
        frontendSrc.set(rootProject.layout.projectDirectory.dir("frontend/src"))
        apiSeed.set(
            layout.projectDirectory.file("src/main/resources/db/changelog/data/006-init-api.csv"),
        )
        menuApiSeed.set(
            layout.projectDirectory.file("src/main/resources/db/changelog/data/007-init-menu-api.csv"),
        )
        verifyOnly.set(verify)
    }

val generateApiSeed = apiSeedTask("generateApiSeed", verify = false)
val checkApiSeed = apiSeedTask("checkApiSeed", verify = true)

// ── 샘플 대량 시드 — playbook/INDEX.md 의 데이터 건수 ────────────────────────────
//
//   ./gradlew generateSampleBulkSeed    902-sample-bulk-{order,item}.csv 를 만든다
//   ./gradlew checkSampleBulkSeed       재생성 결과 == 커밋본
//
// 다른 생성기 셋과 갈리는 자리는 **입력**이다. 저것들은 선언 파일이나 소스를 읽어 시드를
// 뱉지만 이 데이터는 원본이 없다 — 원본은 아래 상수 셋(건수 · 시작 ID · 기준일)이며,
// 그래서 "몇 건인가" 의 답이 한 곳에만 있다.
//
// ── 다시 만들면 남의 로컬이 죽는다 ─────────────────────────────────────
//
// loadData 는 CSV 내용을 체크섬에 넣는다(backend/persistence.md). 건수를 바꾸는 것은 이 CSV 를 다시 만드는
// 일이 아니라 **새 changeset 을 더하는** 일이다 — 이미 902 가 돈 DB 에서는 기동이 죽는다.
// checkSampleBulkSeed 가 CI 에 붙어 있는 것은 그래서다: 손으로 고치는 것도, 무심코 다시
// 만드는 것도 여기서 걸린다.
//
// Random 을 쓰지 않는다. 순수 인덱스 산술이라 JDK 가 바뀌어도 같은 파일이 나오고, 생성물을
// 열어 보면 규칙이 그대로 읽힌다 — 대량 시드에서 "이 행은 왜 이런가" 에 답이 있어야 한다.
//
// ── 건수를 바꾸는 사람에게 ─────────────────────────────────────────────
//
// 새 changeset 에는 **RMK 를 함께 실어야 한다.** 여기 컬럼 목록에 그것이 없어서 902 는
// 할인율만 넣고 비고를 비운 행 1996건을 만들었고, ①은 **할인이 있으면 비고가 필수**라
// (playbook/INDEX.md) 그 행들이 화면에서 저장되지 않았다 — 한 칸을 고치고 저장을 누르면 손대지 않은
// 97행이 함께 걸려 "99건의 오류" 가 뜬다. 903 이 그 행들을 뒤에서 채웠지만, 이 목록은
// 그대로다(CSV 를 다시 만들 수 없다 — 위의 이유).
abstract class SampleBulkSeedTask : DefaultTask() {

    @get:Internal abstract val orderFile: RegularFileProperty

    @get:Internal abstract val itemFile: RegularFileProperty

    /** 품목이 수천 건인 주문 넷 — 904. 진행률의 행×디테일이 시연되는 밀도다. */
    @get:Internal abstract val denseItemFile: RegularFileProperty

    @get:Input abstract val verifyOnly: Property<Boolean>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val orders = renderOrders()
        val items = renderItems()
        val dense = renderDenseItems()

        if (verifyOnly.get()) {
            verify(orderFile.get().asFile, orders)
            verify(itemFile.get().asFile, items)
            verify(denseItemFile.get().asFile, dense)
            logger.lifecycle("샘플 대량 시드 일치 — 주문 $COUNT 건")
            return
        }
        write(orderFile.get().asFile, orders)
        write(itemFile.get().asFile, items)
        write(denseItemFile.get().asFile, dense)
        logger.lifecycle(
            "샘플 대량 시드 — 주문 $COUNT 건 · 품목 ${itemCount()} 건 · 밀집 품목 ${denseCount()} 건",
        )
    }

    private fun verify(file: File, rendered: String) {
        val committed = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        if (committed != rendered) {
            throw GradleException(
                "샘플 대량 시드가 생성기와 다르다(${file.name}). 손으로 고치지 않는다 — seed.md. " +
                    "건수를 바꾸려면 새 changeset 이 필요하다(playbook/INDEX.md).",
            )
        }
    }

    private fun write(file: File, rendered: String) {
        file.parentFile.mkdirs()
        file.writeText(rendered, Charsets.UTF_8)
    }

    /**
     * 주문 [COUNT] 건.
     *
     * 날짜는 기준일에서 하루씩 거슬러 **2년**을 덮는다 — 하루에 두세 건이 앉으므로 최근
     * 30일 기본 조건에도 행이 있고 2년 범위 조건에도 있다. 정렬 기본값이 `ORD_DTM desc` 라
     * (SampleOrderSpec) 시각을 겹치지 않게 흩는다: 같은 값 위에서 도는 커서를 시연하면
     * 그것은 tie-breaker 시연이지 커서 시연이 아니다.
     */
    private fun renderOrders(): String {
        val head = "ORD_ID,ORD_NO,ORD_NM,CUST_ID,CUST_NM,CUST_GRD_CD,ORD_STAT_CD,CHNL_CD," +
            "PRIORITY_CD,ORD_DT,DLVR_YM,ORD_DTM,ORD_QTY,UNIT_AMT,ORD_AMT,VAT_AMT,DC_RT," +
            "PROC_MS,URGENT_YN,USE_YN,LOCK_YN,SRC_SYS_CD,ERR_MSG,REG_ID"
        val rows = (0 until COUNT).map { i ->
            val id = FIRST_ID + i
            val day = dayOf(i)
            val qty = 1 + (i * 7) % 200
            val unit = UNIT_AMOUNTS[i % UNIT_AMOUNTS.size]
            val amount = unit.multiply(BigDecimal(qty))
            listOf(
                id.toString(),
                "SO-2026-%04d".format(id),
                "정기 주문 %04d".format(id),
                "C%03d".format(1 + i % 40),
                CUSTOMERS[i % CUSTOMERS.size],
                GRADES[i % GRADES.size],
                STATUSES[i % STATUSES.size],
                CHANNELS[i % CHANNELS.size],
                PRIORITIES[i % PRIORITIES.size],
                day.toString(),
                day.plusMonths(1 + (i % 4).toLong()).format(MONTH),
                dtmOf(i),
                qty.toString(),
                unit.toPlainString(),
                amount.toPlainString(),
                amount.multiply(VAT).setScale(2).toPlainString(),
                "%d.%02d".format(i % 6, (i * 13) % 100),
                /*
                  다섯 중 하나는 **비운다** — `duration` 렌더러가 빈 값과 0 을 가르는 자리다.
                  빈 칸이 아니라 NULL 이라고 적는 것은 loadData 가 빈 문자열을 빈 문자열로
                  실을 수 있기 때문이다: 그러면 `bigint` 칸이 죽고, 문자 칸에서는 *값 있음*
                  조회가 **전건을 잡는다**(오류 메시지가 그 자리다).
                */
                if (i % 5 == 0) "NULL" else (30 + (i * 37) % 90_000).toString(),
                if (i % 23 == 0) "Y" else "N",
                if (i % 97 == 0) "N" else "Y",
                // 잠긴 행 — USE_YN 이 닫히는 것이 그 행에서만 보인다(playbook/INDEX.md).
                if (i % 61 == 0) "Y" else "N",
                SOURCES[i % SOURCES.size],
                // 오류 메시지가 있는 행 — 조회조건 `값 있음` 이 잡을 것이 있어야 한다.
                if (i % 137 == 0) "외부 시스템 응답이 없어 보류했습니다." else "NULL",
                "admin",
            ).joinToString(",")
        }
        return (listOf(head) + rows).joinToString("\n", postfix = "\n")
    }

    /**
     * 품목 — 주문마다 **하나에서 셋**.
     *
     * 비우지 않는 것이 요점이다: 대량 주문의 디테일이 전부 비어 있으면 ③④⑤가 **고장난
     * 것처럼** 보이고 ⑥의 평면 그리드는 손으로 만든 일곱 줄만 갖는다. 그렇다고 개수를
     * 하나로 고정하지 않는다 — 하나뿐인 주문과 셋인 주문이 같은 화면에 있어야 디테일의
     * 합계와 행 추가가 무엇을 하는지 보인다.
     */
    private fun renderItems(): String {
        val head = ITEM_HEAD
        val rows = mutableListOf<String>()
        for (i in 0 until COUNT) {
            val id = FIRST_ID + i
            val day = dayOf(i)
            for (seq in 1..(1 + i % 3)) {
                val qty = 1 + (i * 3 + seq) % 50
                val unit = UNIT_AMOUNTS[(i + seq) % UNIT_AMOUNTS.size]
                val amount = unit.multiply(BigDecimal(qty))
                rows += listOf(
                    id.toString(),
                    seq.toString(),
                    "SO-2026-%04d".format(id),
                    "P-%04d-%d".format(id, seq),
                    "품목 %04d-%d".format(id, seq),
                    UNITS[(i + seq) % UNITS.size],
                    "%d.000".format(qty),
                    unit.toPlainString(),
                    amount.toPlainString(),
                    day.plusDays(7 + seq.toLong()).toString(),
                    ITEM_STATUSES[(i + seq) % ITEM_STATUSES.size],
                    "Y",
                    "admin",
                ).joinToString(",")
            }
        }
        return (listOf(head) + rows).joinToString("\n", postfix = "\n")
    }

    private fun itemCount(): Int = (0 until COUNT).sumOf { 1 + it % 3 }

    /**
     * **품목이 수천 건인 주문 넷** — 904. `SMPL_MD_VERT` 의 품목 재계산(행×디테일 진행률)이
     * 시연되는 밀도다.
     *
     * 902 는 주문마다 품목을 **하나에서 셋**만 둔다. 그 밀도에서 행×디테일 진행률을 걸면
     * 구간마다 막대가 `1/1` 에서 `3/3` 으로 끝나 **소제목만 깜빡이고 막대는 움직이지
     * 않는다** — 그 화면으로는 "행이 바뀌면 그 행 기준으로 다시 센다" 를 볼 수 없다.
     * 진행률의 밀도 문제는 커서 페이징에서 이미 한 번 겪었다(902 의 주석 — 건수만 늘리는
     * 것으로는 안 되고 밀도가 맞아야 한다).
     *
     * **건수를 넷으로 다르게 둔 것이 요점이다**(2,000 · 1,200 · 800 · 400). 같으면 구간이
     * 바뀔 때 **전체가 갈아 끼워졌다는 사실**이 화면에 나타나지 않는다 — 막대가 0 으로
     * 돌아가는 것만 보이고, 그것은 `reset` 의 절반뿐이다.
     *
     * **주문은 접수(RCV) · 잠기지 않은 · 최근 여드레 안**이어야 한다. 재계산의 게이트가
     * 금액과 같은 자리이고(`amountOpen` — 확정부터는 금액이 잠긴다) 화면의 기본 조회 창이
     * 그 여드레이기 때문이다. `i % 4 == 0` 이 RCV 이고(STATUSES) `i % 61`·`i % 97` 이
     * 잠금·미사용이라, `i = 0` 은 **둘 다 걸려서** 뺐다.
     *
     * **셋 중 하나는 금액이 0 이다.** 재계산이 고칠 것이 없으면 그 실행은 **아무 일도 하지
     * 않은 것과 구별되지 않는다** — 진행률만 돌고 화면의 값은 그대로다. 외부 연계가
     * 금액을 빼먹고 넣은 품목이 그 실물이며, 액션이 지나간 뒤 디테일 그리드의 그 칸들이
     * 수량×단가로 채워지는 것이 **이 샘플의 증거**다.
     *
     * `ITEM_SEQ` 는 101 부터다 — 902 가 1~3 을 쓰므로 겹치지 않고, 번호만 보고 어느 시드의
     * 행인지 알 수 있다.
     */
    private fun renderDenseItems(): String {
        val rows = mutableListOf<String>()
        DENSE_ITEM_ORDERS.forEach { (index, count) ->
            val id = FIRST_ID + index
            val day = dayOf(index)
            for (n in 1..count) {
                val seq = DENSE_SEQ_FROM + n - 1
                val qty = 1 + (index * 3 + n) % 50
                val unit = UNIT_AMOUNTS[(index + n) % UNIT_AMOUNTS.size]
                val amount = unit.multiply(BigDecimal(qty))
                rows += listOf(
                    id.toString(),
                    seq.toString(),
                    "SO-2026-%04d".format(id),
                    "P-%04d-%d".format(id, seq),
                    "품목 %04d-%d".format(id, seq),
                    UNITS[(index + n) % UNITS.size],
                    "%d.000".format(qty),
                    unit.toPlainString(),
                    // 셋 중 하나는 0 — 재계산이 고칠 것이 있어야 한다(위).
                    if (n % 3 == 0) "0.00" else amount.toPlainString(),
                    day.plusDays(7 + (n % 5).toLong()).toString(),
                    ITEM_STATUSES[(index + n) % ITEM_STATUSES.size],
                    "Y",
                    "admin",
                ).joinToString(",")
            }
        }
        return (listOf(ITEM_HEAD) + rows).joinToString("\n", postfix = "\n")
    }

    private fun denseCount(): Int = DENSE_ITEM_ORDERS.sumOf { it.second }

    /**
     * **다섯 중 넷은 최근 [DENSE_DAYS]일 안이다** (2026-09-02 — 실측에서 고쳤다).
     *
     * 처음에는 2년에 고르게 흩었는데, ②는 `ordDtm` 범위를 **31일까지만** 받고 기본이
     * **최근 1일**이다(playbook/INDEX.md · `SampleQueryService.checkRange`) — 하루 두세 건이면 그 창에
     * 99건만 들어와 100/쪽에서 한 쪽이고, 그러면 다음 쪽 버튼이 눌리지 않아 **커서가
     * 시연되지 않는다.** 건수만 늘리는 것으로는 안 되고 **밀도**가 맞아야 하는 자리다.
     *
     * **시간축이 고정이라는 한계는 남는다.** [BASE_DATE] 가 상수이므로(그러지 않으면 만든
     * 날에 따라 생성물이 달라진다) 시간이 지나면 *최근 N일* 조건이 이 데이터를 벗어난다 —
     * 그때는 기간을 데이터가 있는 쪽으로 옮겨 조회한다. 901 의 여섯도 같은 성질이다.
     *
     * 나머지 다섯 중 하나는 이틀 간격으로 2년 남짓 거슬러 둔다 — ①의 주문일 조건은 366일까지
     * 열려 있고(frontend/search-form.md), 오래된 행이 없으면 그 조건은 **넓혀도 결과가 같다.**
     */
    private fun dayOf(i: Int): LocalDate =
        if (i < DENSE_COUNT) {
            BASE_DATE.minusDays((i % DENSE_DAYS).toLong())
        } else {
            BASE_DATE.minusDays((DENSE_DAYS + (i - DENSE_COUNT) * 2).toLong())
        }

    /**
     * 같은 날에 앉은 행끼리 **시각이 겹치지 않게** 흩는다.
     *
     * 정렬 기본값이 `ORD_DTM desc` 이고 커서가 그 값을 담으므로(backend/api.md), 같은 값이 쌓이면
     * 시연되는 것이 **커서가 아니라 tie-breaker** 다. 하루에 [DENSE_DAYS] 칸씩 앉으므로
     * 8시부터 분 단위로 배치하면 하루 안에서 유일하다.
     */
    private fun dtmOf(i: Int): String {
        val slot = if (i < DENSE_COUNT) i / DENSE_DAYS else 0
        return "%sT%02d:%02d:00".format(dayOf(i), 8 + slot / 60, slot % 60)
    }

    companion object {
        /**
         * **2,000 건** — playbook/INDEX.md 이 초안으로 적어 둔 값이다.
         *
         * ②의 쪽수 후보가 100 · 500 · 1000 이라(SampleSingleCursorPage) 가장 큰 후보에서도
         * **두 쪽**이다 — 한 쪽이면 다음 쪽 버튼이 눌리지 않아 커서가 시연되지 않는다.
         */
        const val COUNT = 2_000

        /**
         * 손으로 만든 여섯(`ORD_ID` 1~6)과 **눈에 보이게 떨어뜨린다**.
         *
         * 붙여 두면 어느 행이 시연용 여섯인지 ID 로 구별되지 않는다 — 그 여섯은 상태 넷과
         * 잠금·빈 디테일을 일부러 담은 행이라 찾을 수 있어야 한다(901).
         */
        const val FIRST_ID = 1_001

        /**
         * 밀집 구간의 폭 — **여드레**.
         *
         * ②의 기간 조건은 기본이 **최근 1일**이고 상한이 31일이다(playbook/INDEX.md). 상한에 맞춰
         * 31일에 흩으면 하루에 52건이 앉아 **기본값으로 조회했을 때 한 쪽**이고, 그러면
         * 커서를 보려면 사용자가 먼저 기간을 넓혀야 한다 — 참조 구현이 **첫 조회에서**
         * 자기 어휘를 보여야 하므로 하루에 200건이 앉게 좁혔다.
         *
         * 상한(31일)으로 넓혀도 창 안에 [DENSE_COUNT] 건이 다 들어온다 — 여드레가 31일
         * 안이다.
         */
        const val DENSE_DAYS = 8

        /** 그 창 안에 앉는 건수. 가장 큰 쪽수 후보(1000)에서도 **두 쪽**이어야 한다. */
        const val DENSE_COUNT = 1_600

        /** 시드의 시간축 원점. 오늘로 두면 **만든 날에 따라 파일이 달라진다**. */
        val BASE_DATE: LocalDate = LocalDate.of(2026, 9, 2)

        val ITEM_HEAD = "ORD_ID,ITEM_SEQ,ORD_NO,ITEM_CD,ITEM_NM,UOM_CD,QTY,UNIT_AMT,AMT," +
            "DLVR_DT,ITEM_STAT_CD,USE_YN,REG_ID"

        /**
         * 품목을 몰아 줄 주문 — `(생성 인덱스, 품목 수)`. 합이 [denseCount] 다.
         *
         * `ORD_ID` 는 [FIRST_ID] + 인덱스라 1005 · 1009 · 1013 · 1017 이다. 넷 다
         * `i % 4 == 0`(RCV) 이고 `i % 61 != 0`(잠금 아님) · `i % 97 != 0`(사용) 이며 밀집
         * 구간 안에 있다 — 그 셋이 재계산 액션의 게이트와 화면의 기본 조회 창이다.
         */
        val DENSE_ITEM_ORDERS: List<Pair<Int, Int>> =
            listOf(4 to 2_000, 8 to 1_200, 12 to 800, 16 to 400)

        /** 902 의 1~3 과 겹치지 않게 — 번호만 보고 어느 시드의 행인지 안다. */
        const val DENSE_SEQ_FROM = 101

        val MONTH: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMM")

        val VAT: BigDecimal = BigDecimal("0.10")

        val UNIT_AMOUNTS: List<BigDecimal> = listOf(
            "320.00", "1500.00", "2400.00", "5000.00", "9800.00", "12000.00", "45000.00",
        ).map { BigDecimal(it) }

        // 코드값은 901 이 넣은 공통코드 그대로다 — 없는 값을 쓰면 셀이 코드값을 그대로 그린다.
        val STATUSES = listOf("RCV", "CFM", "SHP", "DON")
        val GRADES = listOf("VIP", "GOLD", "SILVER", "BRONZE")
        val PRIORITIES = listOf("LOW", "NORMAL", "HIGH", "URGENT")
        val SOURCES = listOf("ERP", "WMS", "MANUAL")
        val UNITS = listOf("EA", "BOX", "KG")
        val ITEM_STATUSES = listOf("WAIT", "PICK", "SENT")
        val CHANNELS = listOf(
            "WEB", "EDI", "APP", "CALL", "FAX", "EMAIL", "B2B", "MARKET", "KIOSK", "AGENT",
            "DIRECT", "EXPORT", "IMPORT", "RETURN", "SAMPLE", "EVENT", "SUBSCR", "CONSIGN",
            "WHOLE", "RETAIL", "INTERNAL", "ETC",
        )
        val CUSTOMERS = listOf(
            "가나상사", "다라물산", "마바유통", "사아상회", "자차코리아", "카타무역",
            "파하산업", "한빛물류",
        )
    }
}

fun sampleBulkSeedTask(name: String, verify: Boolean) =
    tasks.register<SampleBulkSeedTask>(name) {
        group = if (verify) "verification" else "seed"
        description = if (verify) {
            "샘플 대량 시드가 생성기와 일치하는지 확인한다 — playbook/INDEX.md"
        } else {
            "샘플 대량 시드 CSV 를 만든다 — playbook/INDEX.md"
        }
        orderFile.set(
            layout.projectDirectory.file(
                "src/main/resources/db/changelog/sample/902-sample-bulk-order.csv",
            ),
        )
        itemFile.set(
            layout.projectDirectory.file(
                "src/main/resources/db/changelog/sample/902-sample-bulk-item.csv",
            ),
        )
        denseItemFile.set(
            layout.projectDirectory.file(
                "src/main/resources/db/changelog/sample/904-sample-bulk-item-dense.csv",
            ),
        )
        verifyOnly.set(verify)
    }

val generateSampleBulkSeed = sampleBulkSeedTask("generateSampleBulkSeed", verify = false)
val checkSampleBulkSeed = sampleBulkSeedTask("checkSampleBulkSeed", verify = true)

// 태스크를 따로 기억하게 두지 않는다 — 하나만 돌린 커밋이 생기고, 그때 막는 것은 CI 뿐이다.
tasks.register("generateSeeds") {
    group = "seed"
    description = "생성 시드를 전부 다시 만든다 — seed.md"
    dependsOn(generateMenuSeed, generateApiSeed, generateMessageSeed, generateSampleBulkSeed)
}

tasks.register("checkSeeds") {
    group = "verification"
    if (includeSample) {
        description = "local 생성 시드가 소스와 일치하는지 확인한다 — seed.md"
        dependsOn(checkMenuSeed, checkApiSeed, checkMessageSeed, checkSampleBulkSeed)
    } else {
        /*
          이 생성물은 local 기준선의 입력이다. product 변형은 sample 클래스·화면과 함께
          해당 CSV를 산출물에서 제외하므로, 여기서 product 소스와 비교하면 정상적인
          local 전용 행을 오류로 오인한다(ADR-009). product 기준선은 별도 master 기동
          검증과 SeedChangelogIncludeTest가 책임진다.
         */
        description = "local 전용 생성 시드 검사를 건너뛴다 — product 기준선은 기동 검사로 확인"
        onlyIf { false }
    }
}

tasks.named("check") { dependsOn("checkSeeds") }
