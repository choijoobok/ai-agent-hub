# 04. 빌드 · 배포

> **상세와 근거의 정본은 [`aidd-guide/standard/build-deploy.md`](../aidd-guide/standard/build-deploy.md) 다** —
> 파이프라인 단계도 그 파일이 단일 원본이며 `.github/workflows/` 가 그것을 가리킨다.
> 여기는 *사람이 실제로 치는 명령* 과 *배포 전에 확인할 것* 이다.

## 1. 산출물은 하나다 — 단일 JAR

```
sources/frontend/dist/            npm run build
        ↓ (Gradle copy)
bootstrap/build/resources/main/static/
        ↓
bootstrap-{version}.jar           ← 배포 대상은 이 하나
```

| | |
|---|---|
| **얻는 것** | 배포 대상이 하나 · 웹서버 별도 구성 불필요 · **동일 오리진이라 CORS 가 없고** 세션 쿠키가 자연스럽다 |
| **감수하는 것** | 프론트만 고쳐도 전체 재배포 · JAR 크기 · CDN 불가 |

**폐쇄망에 고객사 서버로 직접 구축하는 방식**이므로 이 선택이 맞는다 —
SaaS 가 아니고 고객사 간 버전이 갈릴 수 있는 것을 전제한다(CON-014).

## 2. 빌드

```bash
cd sources && ./gradlew clean :backend:bootstrap:bootJar -Pdeployment=hub
```

**`-PskipFrontend` 를 붙이지 않는다** — 붙이면 프론트가 임베드되지 않고 **배포본이 빈 화면이
된다.** 개발 중 백엔드만 확인할 때만 붙인다.

`bootJar`에는 `-Pprofile`을 주지 않는다. 패키징은 항상 product 변형(sample 제외·프론트
포함)으로 만들고, **무엇을 배포할지**는 `-Pdeployment=hub|template`, **어느 환경에서 실행할지**는
JAR 실행 시 활성 Spring 프로파일로 정한다. 기본값은 `hub`이며, 통합 Hub는 PostgreSQL 전용이다.

임베드가 실제로 들어갔는지 확인한다.

```bash
unzip -l sources/backend/bootstrap/build/libs/bootstrap-*.jar | grep 'BOOT-INF/classes/static/index.html'
```

> **CI 가 같은 검사를 한다.** `package` 잡이 JAR 안의 `static/index.html` 을 확인하고
> 없으면 실패한다 — **임베드가 조용히 빠지는 것이 가장 늦게 발견되는 사고**이기 때문이다.

## 3. 프로파일과 빌드 변형

| 프로파일 | 용도 | 특징 |
|---|---|---|
| `local` | 로컬 개발 | PostgreSQL(ICU) · 세션 스토어 `jdbc` · SQL 로그 |
| 그 외 | 배포 환경 | 현재 저장소에는 설정 파일을 두지 않는다. 배포 환경이 명시 프로파일·데이터소스·비밀값을 제공한다. |

```bash
./gradlew :backend:bootstrap:bootRun -Pprofile=local
```

| 목적 | 명령 | 기본 변형 |
|---|---|---|
| local 개발 서버 | `./gradlew :backend:bootstrap:bootRun -Pprofile=local` | sample 포함 · 프론트 생략 |
| 개발 서버 검증 | `./gradlew :backend:bootstrap:bootRun -Pprofile=dev` | sample 제외 · 프론트 포함 |
| 운영 서버 검증 | `./gradlew :backend:bootstrap:bootRun -Pprofile=prod` | sample 제외 · 프론트 포함 |
| Hub 배포 JAR 생성 | `./gradlew :backend:bootstrap:bootJar -Pdeployment=hub` | sample 제외 · 프론트 포함 · PostgreSQL |
| 독립 템플릿 JAR 생성 | `./gradlew :backend:bootstrap:bootJar -Pdeployment=template` | sample 제외 · 프론트 포함 · 주요 DBMS |
| 배포 JAR 실행 | `java -jar bootstrap-<version>.jar --spring.profiles.active=prod` | 빌드된 product 변형을 `prod` 설정으로 실행 |

`bootRun`은 `-Pprofile`이 없으면 Gradle 단계에서 실패한다. 반면 `java -jar`와 IDE 직접 실행은
Gradle 옵션을 읽지 않으므로 `--spring.profiles.active=<프로파일>` 또는 동등한 외부 설정이
필수다. 둘 다 빠지면 애플리케이션이 데이터소스를 만들기 전에 기동을 거부한다.

기본값과 다른 조합도 명시할 수 있다. 예를 들어 local에서 샘플 없이 프론트를 포함해 점검하려면
다음처럼 실행한다.

```bash
./gradlew :backend:bootstrap:bootRun -Pprofile=local -PincludeSample=false -PskipFrontend=false
```

- 현재 설정의 **비밀값은 환경 변수로 주입한다** (`DB_PASSWORD` · `REDIS_PASSWORD` · `MAIL_PASSWORD`).
  `local`의 합성 개발 기본값은 운영 인증정보로 재사용하지 않는다. 암호화 키를 도입할 때는
  운영 키 파일 기본·로컬/개발 환경 변수 규약을 따른다. 현재 YAML 설정 복호화 연동·운영 도구 및
  개인정보 암호화 키 처리는 구현되지 않았다. **Hub는 Spring Security Crypto 기반 공통 설정 복호화를 직접 구현**하며,
  암호화 yml + 환경별 외부 활성 마스터 키 하나의 운영 방식을 유지한다.
  개별 비밀의 환경 변수 주입을 기본 운영 방식으로 요구하지 않는다. 관리 대상·키/교체·비노출 경계는
  [REQ-403](../documents/spec/10-requirements/008-derived.yaml), 선택 이유와 설정 초기화 검증은
  [ADR-022](../documents/decisions/ADR-022-Spring-Security-Crypto-설정암복호화.md)을 따른다.
- **Hub 를 얹으면 프로파일이 하나 더 갈린다** — LLM Provider 선택(개발기 CLI ↔ 운영 API)이
  프로파일 설정으로 전환된다(CON-033). **개발 환경의 CLI 로는 토큰 집계·스트리밍·동시성
  세 항목이 검증되지 않으므로**, API 구독 확보 직후 실환경에서 그 셋을 최우선 검증한다.
- **DB·API·LLM 연결 정의는 배포 yml에서 관리한다.** 비밀 설정값은 REQ-403의 공통 도구로 암호화하고
  설정 마스터 키만 외부에서 관리한다. 화면의 관리 저장값은 매핑과 참조 키다. 대상 시스템 화면은
  [REQ-604의 connection_display](../documents/spec/10-requirements/011-reference-integration.yaml)에 따라 현재 로드된
  API 연결의 비밀 없는 Base URL·인증 방식과 관측 근거를 읽기 전용으로 표시한다. 비밀 원문·암호문과
  DB 접속 정보의 비노출 경계(ARCH-030)는 유지한다.

`sample`은 local 변형에서만 포함된다. product JAR은 `./gradlew :backend:bootstrap:bootJar`로
만들며 sample 코드·화면·DB 기준선을 넣지 않는다.

## 4. 배포 전에 확인할 것

### 게이트 넷이 초록인가

```bash
node aidd-guide/meta/_meta/lint.mjs && node .claude/hooks/trace-check.mjs --all
```

```bash
cd sources && ./gradlew check -PskipFrontend && cd frontend && npm run lint && npm run typecheck && npm run test
```

백엔드 검사는 프론트 빌드를 생략하고, 프론트 검사는 뒤의 별도 명령으로 실행한다. 배포 JAR을
만들 때만 `-PskipFrontend`를 빼서 프론트를 임베드한다.

### 시드가 소스와 일치하나

```bash
cd sources && ./gradlew :backend:bootstrap:checkSeeds -PincludeSample=true
```

**`checkSeeds`는 local 전용 생성물 검사다.** product `check`는 sample을 포함하지 않으므로 이
검사를 건너뛴다.

### DB 기준선이 올바른 master에 걸려 있나

product는 `db.changelog-master.yaml`, local은 `db.changelog-local.yaml`을 쓴다. 모듈 master에
새 파일을 include하지 않으면 기동해도 적재되지 않는다. `SeedChangelogIncludeTest`가 두 루트를
함께 검사한다.

### 이식성 (DB 를 바꿀 계획이 있을 때만)

```bash
cd sources && ./gradlew :backend:bootstrap:portabilityTest -Pdeployment=template
```

독립 템플릿에서만 Docker 로 4벤더를 띄워 스키마·정렬을 대조한다. **20분이 걸려 매 푸시에 태우지 않는다** —
자체 워크플로(`db-portability.yml`)가 changelog·엔티티 변경과 야간 스케줄로 돌린다.

## 5. CI — 무엇이 언제 도나

| 잡 | 언제 | 무엇을 |
|---|---|---|
| **backend** | push(main) · PR · 수동 | 프로파일·배포 경계 계약 → `./gradlew build -PskipFrontend -Pdeployment=hub` — 컴파일 · 단위테스트 · ArchUnit |
| **frontend** | 〃 | `npm ci` → lint → test → build |
| **package** | **수동만** | 단일 JAR + **프론트 임베드 검증** |
| **db-portability** | changelog·엔티티 변경 · 야간 | 4벤더 스키마·정렬 대조 |

- **`documents/**` · `aidd-guide/**` · `developer-guide/**` · `**/*.md` 만 바뀐 푸시는 CI 를 돌리지
  않는다** — 문서 트랙의 커밋이 다수가 되면 빌드 큐가 그것으로 찬다. PR 에서는 항상 돈다.
- **`package` 가 수동인 이유는 배포 대상이 아직 없어서다.** 배포가 생기면 그 조건을 지운다.
- **빌드 루트가 `sources/` 이므로 워크플로의 모든 잡이 `working-directory: sources` 를 갖는다.**
  경로를 고칠 때 이것을 함께 본다.

## 6. 운영 이관

**고객사 서버에 직접 구축하고 이관 후에는 고객사별로 개별 유지보수한다.**

| 확인할 것 | 어디서 |
|---|---|
| 아웃바운드 허용 (**도메인 기준**) · 프록시 정보 · 인바운드 미허용 | CON-013 — **고객사 인프라 영역이며 제품 기능이 아니다** |
| 업무 DB 읽기 전용 계정 + **메타데이터 조회 권한** | CON-018 — 없으면 카탈로그 자동 수집이 불가하다 |
| API 서비스 계정과 인증 방식 · 호출 허용 대역 | CON-019 |
| 백업·복구 RPO·RTO — **설정·정책과 이력은 주기를 분리한다** | CON-026 |
| 모니터링 — **화면은 제품 기능이 아니다.** 고객사 도구가 긁어 갈 지점만 노출한다 | CON-027 |
| 이력 용량 산정 — **재개 상태가 스텝마다 메시지 전량을 저장하므로 별도 항목으로 넣는다** | CON-025 |
| 대화 이력 보관 기간·파기 방식의 **법무·보안 검토** | CON-032 |

> **착수 시 확정 항목 18개가 전부 `documents/spec/02-constraints/_index.yaml` 에 있다.**
> 시스템 기능이 아니므로 값을 우리가 정하지 않는다 — 대신 **확정되지 않은 상태로 구축에
> 들어가지 않는다**는 것을 요건으로 둔다.

## 설정 비밀과 고정 연결

SLICE-302는 환경/프로파일/외부 파일 우선순위를 유지하는 공통 설정 복호화와 운영 도구를 제공한다.
암호 형식은 ADR-022의 `ENC(v1.A256GCM.<base64url-no-padding>)`이며 Spring Security Crypto를 사용한다.
고객사·환경·정규 설정키를 암호문 내부에 결합하므로 다른 속성으로 복사하면 소비가 실패한다.
`app.secrets.tenant`, `app.secrets.environment`는 암호화할 때와 기동할 때 같아야 한다.

DB/Liquibase·Redis·메일·TLS 저장소의 표준 password 속성은 등록돼 있다. 추가 문자열 비밀은
`app.secrets.properties` 목록에 `app.providers.primary.api-key`처럼 canonical 이름으로 등록한다.
배열은 `app.integrations[0].access-token`처럼 정확 인덱스를 포함한다. 환경 재정의는 이 정규 이름의
표준 환경변수 표기(`SPRING_DATASOURCE_PASSWORD`)로 한다. 임의 별칭을 거쳐 암호문을 복사하지 않는다.
선택된 비밀만 변환하며 잘못된 상위 값은 하위 설정으로 넘어가지 않는다. 필수 Hub DB 비밀은
DB 풀/Liquibase 전에 검사한다. 미사용 메일·JDBC 배포의 Redis·선택 연결 비밀은 실제 소비까지 지연한다.

환경별 활성 키는 CSPRNG 32바이트의 base64url-no-padding 값이다. 고객 운영 체계가 발급·접근 권한을 관리한다.
`AIHUB_CONFIG_MASTER_KEY` 또는 `AIHUB_CONFIG_MASTER_KEY_FILE` 하나만 제공한다. 키 파일은 일반 파일이어야 하며,
POSIX는 소유자 전용, Windows는 소유자·SYSTEM·Administrators 이외 읽기·쓰기·권한 변경 허용 ACL이 없어야 한다.
키와 암호문은 같은 파일·배포 묶음·백업에 넣지 않는다. 실제 키는 채팅·명령 인자·셸 이력에 입력하지 않는다.

운영 도구는 `sources/`에서 다음과 같이 호출한다. 예시 파일은 사전에 권한을 제한한 별도 입력이며
키/비밀 내용은 명령 인자로 받지 않는다. `-q`는 Gradle 진행 출력을 줄인다.

```powershell
./gradlew.bat -q :backend:support:configSecret '-PsecretAction=encrypt' '-PsecretTenant=company' '-PsecretEnvironment=prod' '-PsecretProperty=spring.datasource.password' '-PsecretKeyFile=D:/secure/config-master' '-PsecretFile=D:/secure/db-input'
./gradlew.bat -q :backend:support:configSecret '-PsecretAction=verify' '-PsecretTenant=company' '-PsecretEnvironment=prod' '-PsecretProperty=spring.datasource.password' '-PsecretKeyFile=D:/secure/config-master' '-PsecretInputFile=D:/deploy/db-ciphertext'
./gradlew.bat -q :backend:support:configSecret '-PsecretAction=reencrypt' '-PsecretTenant=company' '-PsecretEnvironment=prod' '-PsecretProperty=spring.datasource.password' '-PsecretKeyFile=D:/secure/config-master-old' '-PsecretNewKeyFile=D:/secure/config-master-new' '-PsecretInputFile=D:/deploy/db-ciphertext'
```

`encrypt`/`reencrypt`는 암호문만, `verify`는 `OK` 또는 안전한 오류만 출력한다. `secretKeyEnv`와
`secretNewKeyEnv`는 비밀이 아닌 **환경변수 이름**이다. 보호된 파이프를 직접 연결할 때만 `secretStdin=true`를 사용한다.
입력의 줄바꿈도 비밀번호에 포함되므로 줄바꿈을 덧붙이는 셸 출력을 비밀 입력으로 사용하지 않는다.
도구는 기존 YAML/입력 파일을 수정하지 않는다. 임시 평문 파일을 새로 만들도록 요구하지 않으며,
기존 운영 비밀 파일이나 보호된 파이프를 사용하고 고객사의 보관/파기 규칙을 따른다.

새 키/암호문과 실제 자격을 격리 환경에서 검수한 후 계획 배출·재기동한다. 부분 교체는 기동을 막는다.
이전 키·이전 암호문은 서로 분리한 논리 복구 세트로 보존하고, 복구 때도 두 짝과 실제 접속을 함께 검수한다.
자동 키 혼용·무중단 교체·일반 오류 후 자동 롤백은 제공하지 않는다.
`management.endpoint.env.show-values`와 `configprops.show-values`는 `never`만 허용한다.
로컬 합성 계정의 `app.secrets.allow-plaintext=true`를 운영에 적용하지 않는다.

배포 연결은 다음처럼 비밀 **설정키 참조**를 갖는다. 공개 조회에는 URL·헤더·사용자명·암호문이 없다.

```yaml
app:
  secrets:
    tenant: company
    environment: prod
    properties: [app.providers.primary.api-key]
  providers:
    primary:
      api-key: ENC(운영_도구가_출력한_전체_값)
  deployment:
    id: release-20261114
    connections:
      primary:
        kind: MODEL
        display-name: 사내 모델 연결
        endpoint: https://model.example.invalid/v1
        authentication: BEARER
        secret-property: app.providers.primary.api-key
        required: false
```

종류는 DATASOURCE/BUSINESS_API/MODEL/EXTERNAL_IDENTITY, 인증 방식은 NONE/BASIC/BEARER/API_KEY/EXTERNAL이다.
NONE은 명시 검수한 무인증 연결만 사용한다. 비밀/인증 헤더를 URL이나 일반 headers에 넣지 않는다.
선택 비밀을 아직 소비하지 않은 공개 상태는 `SECRET_NOT_VERIFIED`이며 연결 성공을 뜻하지 않는다.
필수(`required=true`) 연결은 기동 시 비밀을 검증한다. 실제 공급자 기능·현재 권한·연결 성공은 소유 어댑터가 별도로 검증한다.
설정 세대는 기동별 UUID·배포 ID·계약 버전·로드 시각으로 고정한다. 파일 수정은 기존 호출 목적지를 바꾸지 않는다.

서버 소비자는 core `ConnectionRegistry.Consumer` bean으로 허용 종류와 현재 권한 검사 함수를 등록한다.
미등록 소비자는 거부된다. public 투영의 READ와 실제 연결 CONNECT를 매번 구분해 검사한다.
해석된 연결은 `withResolvedConnection` 내부에서만, 비밀 char 배열은 그 안의 `withSecret`에서만 소비한다.
콜백이 끝나면 접근을 차단하고 전달 배열을 지운다. JVM의 모든 String 복사까지 완전 소거된다고 보장하지 않는다.
새 설정 바인딩은 공통 `SecretEnvironment.binder` 또는 보호 advisor가 설치된 `@ConfigurationProperties`를 사용한다.
기본 `Binder.get`만으로 비밀을 바인딩하지 않는다. 설정 객체를 API DTO/로그/모델 입력으로 넘기지 않는다.

## 관련

- 파이프라인의 정본 — [`aidd-guide/standard/build-deploy.md`](../aidd-guide/standard/build-deploy.md)
- 배포 후 무엇을 보나 — [05-operate.md](05-operate.md)
- 인도 단계 절차 — [`aidd-guide/phase/P6-deliver.md`](../aidd-guide/phase/P6-deliver.md)
- 사용자 메뉴얼 확인·캡처 치환 — `/manual-site` 스킬
- 설계·사용자·운영 오프라인 제출본 만들기 — `/deliverable-site` 스킬


### 대상 시스템과 연결별 호출 유틸

CR-050 B·[ADR-051](../documents/decisions/ADR-051-연결별-호출유틸과-API-공통자산-확장.md)을 따른다. 공통 연결 정의와 시스템 소속/호출 옵션을 `registry-key`로 연결한다. 아래는 스키마 예시이며 `.invalid` 주소에 접속 성공을 기대하지 않는다. 실제 업무 실행/고객 공동 검수는 후속 소유 기능에서 수행한다.

```yaml
app:
  deployment:
    id: example-release
    connections:
      order-http:
        kind: BUSINESS_API
        display-name: 주문 API
        endpoint: https://orders.invalid/api
        authentication: NONE
    target-systems:
      orders:
        name: 주문 시스템
        description: 일반 REST JSON 연결 예시
        connections:
          main:
            registry-key: order-http
            name: 기본 연결
            invoker-bean: restJsonInvoker
            public-path-segments: [api]
            options:
              connect-timeout-ms: 1000
              request-timeout-ms: 10000
              max-response-bytes: 1048576
            test:
              method: HEAD
              path: /health
              timeout-ms: 2000
              success-statuses: [200, 204]
```

인증이 필요한 연결은 앞 절의 보호된 `secret-property`를 사용한다. BASIC은 `principal`, API_KEY는 호출 옵션 `api-key-header`도 지정한다. Authorization·Cookie·토큰은 일반 `headers`에 넣지 않는다. 테스트 미제공 서비스는 `test` 블록을 생략한다. 등록되지 않은 빈·알 수 없는 옵션·잘못된 URL/소속·비밀 오류를 기본 구현이나 빈 정상 목록으로 대체하지 않는다.

`restJsonInvoker`는 JDK HTTP 클라이언트를 사용하는 공통 인터페이스 구현이다. 후속 유틸은 같은 인터페이스를 구현해 WebClient나 전용 SDK를 내부에서 사용할 수 있다. 현재 OData 처리 빈은 없으며 빈 이름만 SAP용으로 적어 지원을 활성화할 수 없다. 응답 상한·시간 제한·리다이렉트 미추적을 유지하며 SAP 전용 확장 조건은 ADR-051에서 확인한다.


#### 대상 시스템 기동 허용 절차

Liquibase가 서버 기동 때 `018-target-system.sql`부터 `024-target-system-messages.sql`까지의 미적용 변경을 반영한다. 적용된 파일은 고치지 않고 후속 체인지셋을 추가한다. DB 테이블 생성과 YAML 메타 동기화는 별개이며, 메타 동기화는 아래의 정확 기동 허용 뒤 수행한다.

1. 보호된 운영 실행 환경에 `TARGET_DEPLOYMENT_JDBC`(실제 스키마 포함), `TARGET_DEPLOYMENT_USER`, `TARGET_DEPLOYMENT_PASSWORD`를 설정한다. 비밀번호는 명령 인자나 로그에 넣지 않는다.
2. 서버를 기동하고 `sources/`에서 아래 조회를 실행한다. 환경 값은 `app.secrets.environment`와 같아야 한다.
3. 이번 배포의 정확 BOOT_ID·CONFIG_ID와 CONTROL_VERSION을 대조한 뒤 새 UUID 명령키로 허용한다. 같은 설정 지문인 재기동도 새 BOOT_ID이며 자동 승계하지 않는다.
4. 다시 조회해 허용된 기동의 READY_YN=Y를 확인한다. 준비 전에는 시험·표시 저장·정리를 열지 않는다. 허용 명령만 성공하고 동기화가 실패하면 로그를 조사하며 다른 기동의 준비 값을 대신 쓰지 않는다.

```powershell
.\gradlew.bat :backend:bootstrap:targetDeployment '--args=inspect customer-prod' -PskipFrontend
.\gradlew.bat :backend:bootstrap:targetDeployment '--args=allow customer-prod <boot-uuid> <control-version> <command-uuid> <operator-id> false' -PskipFrontend
```

마지막 `false`는 기존 기동의 허용을 끝내고 이번 기동을 메타 작성자로 지정한다. 구·신 기동 공존을 운영 검수한 경우에만 `true`를 사용한다. 응답 유실은 같은 명령키·같은 인자로 확인하며, 과거 명령 재수신이 최신 허용 상태를 되돌리지 않는다. 일반 관리자 화면에는 이 운영 권한을 노출하지 않는다.

이 경계는 대상 연결의 시험·동기화·참조 쓰기·정리를 직렬화한다. 전역 agent/큐의 작업 배출 완료를 대신하지 않으며 해당 실행 기능은 후속 통합 때 같은 배포 경계와 진행 참조 제공자를 연결해야 한다. 미설치 소비자의 영향 건수는 0이 아니라 미확인으로 표시한다.
