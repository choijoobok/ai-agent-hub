// support — 기술 어댑터(캐시/파일/암호화/알림/락/엑셀). 의존은 core 뿐이다(backend/packages.md).
dependencies {
    api(project(":backend:core"))
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework:spring-context")

    // 설정 조회 유틸이 yml 을 직접 바인딩한다 — props 를 빈으로 등록하지 않는다(sys/code.md).
    implementation("org.springframework.boot:spring-boot")
    api("jakarta.validation:jakarta.validation-api")

    // 메일 발송 — backend/packages.md 이 알림을 이 모듈에 준 책임이다(분실 비밀번호 재설정, AUTH-07).
    //
    // 스타터가 아니라 org.springframework.mail 한 패키지만 가져온다. Mailer 가 쓰는 것은
    // MailSender 인터페이스와 SimpleMailMessage 둘이고, 그 둘은 jakarta.mail 을 요구하지
    // 않는다 — 구현과 자동설정은 bootstrap 이 runtimeOnly 로 갖는다.
    //
    // 그래서 이 모듈은 **메일 구현에 묶이지 않는다**: 발송 빈이 없는 배포에서도 컴파일도
    // 기동도 되며, 그때의 동작(보내지 못했다고 답한다)을 Mailer 가 정한다.
    implementation("org.springframework:spring-context-support")

    // 락 추상화 — backend/packages.md 이 이 모듈에 준 책임이다.
    //
    // 스타터가 아니라 필요한 셋만 가져온다. spring-aop 는 @Aspect 를, aspectjweaver 는
    // 포인트컷 파서를, spring-tx 는 WAIT 의 트랜잭션 경계를 준다 — WAIT 의 정의 자체가
    // *해제가 곧 커밋* 이라(backend/persistence.md) 트랜잭션 없이는 그 정의를 표현할 수 없다.
    //
    // spring-boot-starter-aop 를 쓰지 않는 것은 이 모듈이 스타터를 하나도 안 쓰기 때문이며,
    // 넣으면 spring-boot-starter 가 딸려 와 core 의 "프레임워크 의존 최소" 가 여기서 깨진다.
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-tx")
    implementation("org.aspectj:aspectjweaver")

    // 시스템 환경설정 화면의 **설명 컬럼**이 여기서 온다 — screen/admin/system/code/requirements.md.
    //
    // 프로세서가 컴파일 시점에 필드 Javadoc 을 META-INF/spring-configuration-metadata.json
    // 으로 뽑아 둔다. 설명을 담을 새 자리(CFG_DESC)를 만들지 않는 것이 요점이며,
    // backend/api.md 가 @Operation.summary 를 재사용한 것과 같은 모양이다.
    //
    // **프로필로 가르지 않는다.** 빼면 설명이 조용히 빈다(20).
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // 파일 맨 아래의 compileJava doFirst 도 이 프로세서 때문에 있다.
    //
    // **읽는 쪽도 전용 라이브러리를 쓴다.** 처음에는 org.springframework.boot.json 의
    // JsonParserFactory 로 읽었는데, 그것은 클래스패스에 있는 것을 고르므로 **Jackson 이
    // 있는 곳에서만 맞았다** — 없으면 BasicJsonParser 로 떨어지고 이 파일을 잘못 읽는다
    // (2026-08-30 실측: 프로퍼티 28개짜리가 2개로 읽혔다). 배포에는 Jackson 이 있어
    // **테스트에서만 틀렸고**, 그것이 이 종류 실패의 최악이다.
    implementation("org.springframework.boot:spring-boot-configuration-metadata")

    // 서버 생성 엑셀 — playbook/excel-download.md 의 세 번째 갈래. backend/packages.md 이 엑셀을 이 모듈에 준 책임이다.
    //
    // 프론트 생성(exceljs)이 기본이고 이쪽은 화면 구조와 엑셀 구조가 다를 때만 쓴다.
    // 여기 있는 것은 시트를 쓰는 도구 하나뿐이며, 무엇을 쓸지는 도메인이 정한다 —
    // support 가 업무 컬럼을 알면 그 순간 기술 어댑터가 아니게 된다.
    implementation(libs.poi.ooxml)

    // 파라미터가 바인딩된 SQL — backend/logging.md. JDBC 계층에서 가로채므로
    // JPA · QueryDSL · MyBatis 셋이 한 벌로 해결된다.
    //
    // 운영 JAR 에서도 빼지 않는다. backend/logging.md 이 운영을 "OFF, 진단 시 일시 ON" 으로 정했는데,
    // developmentOnly 로 두면 그 "일시 ON" 이 재빌드가 된다.
    implementation(libs.p6spy)

    // ConfigValidator 는 **실제로 바인딩해 보는 것**이 하는 일의 전부라(screen/admin/system/code/requirements.md), 구현체가
    // 없으면 그 검사가 검사하지 못한다. 런타임에는 웹 스타터가 끌고 오므로 여기서는
    // 테스트에만 둔다 — 이 모듈이 검증 구현을 골라 다른 모듈에 강요하지 않는다.
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
}

// 설정 메타데이터를 **매번 처음부터 다시 만든다** — screen/admin/system/code/requirements.md 의 설명 컬럼이 여기 달렸다.
//
// spring-boot-configuration-processor 는 출력 디렉터리에 이미 있는
// META-INF/spring-configuration-metadata.json 을 **직전 결과로 읽어 합치는데**, 그
// 합치기가 description 을 잃는다. 한 번 잃으면 다음 빌드가 그 파일을 다시 읽으므로
// **영영 빈 채로 굳는다**(2026-08-30 실측: props 파일 하나를 건드리자 스물둘 전부가 빈
// 설명이 됐다).
//
// **둘이 함께여야 한다.**
//
//   지우기만  : 소스가 안 바뀌면 증분 컴파일러가 javac 를 아예 돌리지 않아
//               파일이 **지워진 채로 남는다** — 설명이 비는 것보다 나쁘다(실측)
//   끄기만    : 직전 파일이 그대로라 프로세서가 그것을 합치고 **설명을 다시 잃는다**
//
// 그래서 전체 컴파일로 고정하고(=javac 가 언제나 전 소스를 돈다) 그 직전에 파일을
// 지운다. 순서가 뒤바뀌면 어느 쪽도 성립하지 않는다.
//
// 실패가 **조용한 것**이 이 자리에 주석이 긴 이유다. 빌드도 기동도 성공하고 시스템
// 환경설정 화면의 설명 컬럼만 빈다 — 붙잡는 것은 `ConfigCatalogTest` 의
// *모든 키에 설명이 있다* 한 줄이다.
//
// 이 모듈은 파일 스물 남짓이라 전체 재컴파일이 1초 안쪽이다.
tasks.named<JavaCompile>("compileJava") {
    options.isIncremental = false
    doFirst {
        destinationDirectory
            .file("META-INF/spring-configuration-metadata.json")
            .get().asFile.delete()
    }
}

// @feat FEAT-302 — 인자에는 동작/용도/파일 경로/환경변수 이름만 받는다.
tasks.register<JavaExec>("configSecret") {
    group = "application"
    description = "설정 비밀 encrypt/verify/reencrypt (평문 인자 금지)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.aiagent.support.deployment.SecretTool")
    standardInput = System.`in`
    doFirst {
        val options = mutableListOf(providers.gradleProperty("secretAction").getOrElse(""))
        mapOf("secretTenant" to "tenant", "secretEnvironment" to "environment", "secretProperty" to "property", "secretKeyFile" to "key-file", "secretKeyEnv" to "key-env", "secretNewKeyFile" to "new-key-file", "secretNewKeyEnv" to "new-key-env", "secretInputFile" to "input-file", "secretFile" to "secret-file").forEach { (property, option) ->
            providers.gradleProperty(property).orNull?.let { options.addAll(listOf("--$option", it)) }
        }
        if (providers.gradleProperty("secretStdin").orNull == "true") options.add("--secret-stdin")
        setArgs(options)
    }
}
