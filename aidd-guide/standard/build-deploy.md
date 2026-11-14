---
id: LLM-FWK-005
title: "빌드 · 배포 — 단일 JAR · 배포 경계 · 프로파일 · CI"
category: standard
type: operation
class: none
created: 2026-09-04
updated: 2026-10-28
status: active
sources: ["sources/build.gradle.kts", "sources/backend/bootstrap/build.gradle.kts", ".github/workflows/", "sources/backend/bootstrap/src/main/resources/application.yml", "sources/frontend/vite.config.ts", "sources/backend/support/src/main/java/com/aiagent/support/deployment/", "sources/backend/core/src/main/java/com/aiagent/core/deployment/"]
tier: 2
tags: [build, deploy, ci, profile, spa]
---

# 빌드 · 배포 — 단일 JAR · 배포 경계 · 프로파일 · CI

> **`.github/workflows/` 와 `vite.config.ts` 가 이 파일을 단일 원본으로 가리킨다.**
> 파이프라인 단계를 바꾸면 여기도 바꾼다 — 둘이 갈리면 **워크플로가 무엇을 하는지
> 아무 데도 적혀 있지 않게 된다.**

## 1. 단일 실행 JAR — 프론트를 임베드한다

```
npm run build  →  sources/frontend/dist/
      ↓ (Gradle copy)
bootstrap/build/resources/main/static/
      ↓
bootstrap-{version}.jar   (단일 산출물 — 이름은 모듈명이 정한다)
```

| | |
|---|---|
| **얻는 것** | 배포 대상이 하나. 웹서버 별도 구성 불필요. **동일 오리진이라 CORS 가 없고** 세션 쿠키가 자연스럽다 |
| **감수하는 것** | 프론트만 고쳐도 전체 재배포(개발 중에는 Vite dev 서버로 대응) · JAR 크기 · CDN 불가(사내 백오피스라 요구 없음) |

`bootstrap`은 항상 `api`·`modules/system`을 조립한다. `modules/sample`은
`-PincludeSample=true`인 **local 변형에서만** 조립한다. product JAR에는 sample 클래스·라우트·
changelog·API 시드가 들어가지 않는다(ADR-009).

## 2. SPA 라우팅 — `/api/**` 를 반드시 제외한다

React Router 의 클라이언트 라우팅과 정적 리소스 처리가 충돌한다. `/user` 로 **새로고침**하면
404 가 난다. 정적 파일과 `/api/**` 를 뺀 모든 GET 을 `index.html` 로 포워딩한다.

> **`/api/**` 를 빼지 않으면 없는 API 경로가 `index.html` 을 돌려준다** — 그러면 프론트는
> *JSON 인 줄 알았는데 HTML* 을 받고, **원인이 드러나지 않는다.** 404 JSON 을 돌려준다.

정적 리소스 캐시 — 해시 파일명(`assets/*.{hash}.js`)은 `max-age=1y` immutable,
`index.html` 은 `no-cache`.

## 3. 배포 경계와 프로파일은 다른 축이다

`-Pdeployment`은 **무엇을 배포·검증하는가**, `-Pprofile`은 **어느 환경에서 실행하는가**를
가른다. 이 둘을 섞으면 Hub JAR에 템플릿 전용 드라이버가 실리거나, 독립 템플릿의 DBMS
이식성 검사를 Hub 정책으로 잘못 해석한다(ADR-008).

| 배포 변형 | 지정 | 공유 DB 런타임 | 검증 |
|---|---|---|---|
| `hub` | 기본값. 명시하려면 `-Pdeployment=hub` | PostgreSQL만. system·admin·portal·agent가 하나의 스키마와 pgvector를 공유 | 일반 `build`·`check` |
| `template` | `-Pdeployment=template` 필수 | PostgreSQL·Oracle·MySQL·MariaDB·SQL Server | `portabilityTest`와 DBMS 이식성 CI |

고객사 업무 DB·DW는 위 공유 DB의 런타임 의존성이 아니다. 실제 지원할 DBMS·버전과 파서는
고객사 데이터소스별 CON-023에서 정하며, Hub 자체를 그 DBMS로 바꾸는 근거가 되지 않는다.

### 프로파일

| 프로파일 | 용도 | 특징 |
|---|---|---|
| `local` | 로컬 개발 | PostgreSQL(**ICU**) · **세션 스토어 `jdbc`** · SQL 로그 |
| 그 외 | 배포 환경 | 현재 저장소에는 설정 파일을 두지 않는다. 배포 환경이 명시 프로파일·데이터소스·비밀값을 제공한다. |

**제품 연결 비밀은 `ENC(v1.A256GCM...)`로 암호화한 YAML에 둔다.** 공통 `support/deployment`가
원천 우선순위를 유지해 소비 시 해석한다. 환경별 활성 키 하나는 `AIHUB_CONFIG_MASTER_KEY` 또는
`AIHUB_CONFIG_MASTER_KEY_FILE` 중 하나로 주입하고 암호문과 별도로 보호한다.
로컬 합성 자격의 평문 허용은 `app.secrets.allow-plaintext=true`이며 운영 기본값은 false다.
설정 선택·키/용도 실패에 하위 값/평문 폴백은 없다. 임의 비밀 설정키는 canonical 이름으로 등록한다.
JDBC 세션 배포는 Redis 구성을 만들지 않으며 선택한 경우에만 자격을 소비한다.
운영 명령·별도 복구 세트·고정 연결 세대는 [배포 가이드](../../developer-guide/04-release.md)의 설정 비밀 절을 따른다.
`ConnectionRegistry`의 서버 소비자 bean은 현재 READ/실행 권한을 검사하고, 해석된 연결/비밀은 콜백 밖으로 보관하지 않는다.

> **서버 기동에는 활성 프로파일을 반드시 명시한다.** `application.yml`에 기본 프로파일을
> 두지 않았고, 애플리케이션도 누락을 데이터소스 구성 전에 거부한다. `bootRun`은
> `-Pprofile=<프로파일>`, `java -jar`·IDE 직접 실행은 `--spring.profiles.active=<프로파일>`
> 또는 동등한 외부 설정을 사용한다.
>
> **`includeSample`과 `skipFrontend`의 기본값은 프로파일이 정한다.** `local`은 둘 다 true,
> 개발·운영 프로파일과 프로파일 없는 일반 빌드는 둘 다 false다. 명시한
> `-PincludeSample=<true|false>`·`-PskipFrontend=<true|false>`가 기본값보다 우선한다.
>
> 판정의 원본은 `settings.gradle.kts`이며 sample **모듈 포함 여부**를 같은 값으로 가른다.
> `bootstrap/build.gradle.kts`는 그 값을 읽는다. 일반 `build`·`bootJar`·`check`는 프로파일을
> 주지 않으므로 제품 변형이고, sample 제외·프론트 포함이 기본이다. 배포 변형도 주지 않으면
> `hub`이므로 PostgreSQL 드라이버만 실린다.
>
> `local`에서 `includeSample=false`를 명시하면 처리된 `application-local.yml`도 제품 master
> changelog를 가리킨다. 그래야 명시 옵션이 클래스·화면뿐 아니라 DB 시드까지 함께 제외한다.

| 목적 | 명령 |
|---|---|
| local 실행 | `./gradlew :backend:bootstrap:bootRun -Pprofile=local` |
| 개발·운영 서버 검증 | `./gradlew :backend:bootstrap:bootRun -Pprofile=<dev|prod>` |
| local에서 sample 제외·프론트 포함 | `./gradlew :backend:bootstrap:bootRun -Pprofile=local -PincludeSample=false -PskipFrontend=false` |
| Hub JAR | `./gradlew :backend:bootstrap:bootJar -Pdeployment=hub` |
| Hub JAR 실행 | `java -jar bootstrap-<version>.jar --spring.profiles.active=<dev|prod>` |
| Hub 백엔드 검증 | `./gradlew check -PskipFrontend -Pdeployment=hub` |
| 독립 템플릿 JAR | `./gradlew :backend:bootstrap:bootJar -Pdeployment=template` |
| 독립 템플릿 DBMS 이식성 | `./gradlew :backend:bootstrap:portabilityTest -Pdeployment=template` |
| local 생성 시드 검증 | `./gradlew :backend:bootstrap:checkSeeds -PincludeSample=true` |

`-Pprofile`은 Gradle의 `bootRun` 변형을 고르는 옵션이다. `bootJar`에는 주지 않는다. 패키지는
항상 sample 제외·프론트 포함 product 조합으로 만들되, 배포 대상은 `-Pdeployment`으로 정한다.
JAR·IDE 직접 실행은 Spring 활성 프로파일을 외부에서 명시한다.

> **`application-local.yml` 은 저장소에 넣는다.** `.gitignore` 대상으로 두면
> **템플릿을 받은 사람이 "로컬을 어떻게 띄우나" 의 답을 갖지 못한다.**
> 비밀값 규칙은 그대로이며 값은 `${DB_PASSWORD:...}` 형태로 **환경변수가 이긴다.**

> **로컬 세션 스토어는 `none` 이 아니라 `jdbc` 다.** `none` 이면
> 세션 스토어가 **로컬에서 한 번도 돌지 않아** `SPRING_SESSION` DDL 과 직렬화 경로가
> **배포 뒤에야 시험된다.** *조용히 통과하는 게이트를 만들지 않는다* 는 원칙 그대로다.

## 4. 독립 템플릿에서만 DB 전환 — 소스를 고치지 않는다

`spring.datasource` 의 URL · 드라이버 · 계정만 바꾼다.

- **JPA 방언을 명시하지 않는다.** Hibernate 가 드라이버로 판별한다 — 명시하면 **DB 전환 시
  고칠 자리가 하나 더 생긴다.**
- Liquibase 가 기동 시 그 벤더용 DDL 을 만들어 적용한다.
- MyBatis `databaseIdProvider` 가 벤더를 감지해 분기 쿼리를 고른다.
- template JAR에는 PostgreSQL·Oracle·MySQL·MariaDB·SQL Server 드라이버가 들어 있다.
- **Hub JAR은 PostgreSQL 전용이다.** pgvector와 네 제품 모듈의 공유 스키마를 다른 DBMS로
  전환하지 않는다.

## 5. CI 파이프라인

```
1. 체크아웃
2. JDK 25 / Node 셋업
3. node gradle/build-variant-contract.test.mjs   프로파일·배포 경계·드라이버 조합 + 누락 거부
4. ./gradlew build -PskipFrontend -Pdeployment=hub   Hub 컴파일 + 단위테스트 + ArchUnit (프론트는 다음 단계에서 별도 검사)
5. npm ci && npm run lint && npm run test && npm run build
6. 독립 템플릿 통합테스트 (`-Pdeployment=template`, Testcontainers)
     PR   : PostgreSQL · MySQL
     야간 : + MariaDB · MSSQL · Oracle
7. 의존성 취약점 스캔
8. 수동 package: Hub 또는 template를 명시한 bootJar → JAR 안 `static/index.html` 확인 → 아티팩트 업로드
```

**시드 태스크의 절차는 [seed.md](./seed.md) 가 갖는다** — 여기는 파이프라인의 자리만 말한다.
local 시드 변경이 있는 CI는 `checkSeeds -PincludeSample=true`를 별도 실행한다. product `check`는
local 전용 생성물을 의도적으로 비교하지 않는다. **시드를 다시 만들지 않은 local 커밋은 CI에서
막힌다.**

## 6. 개발 중에는 두 서버를 프록시로 잇는다

`vite.config.ts` 가 `/api` 를 백엔드로 넘긴다. **단일 JAR 로 배포하면 같은 오리진이라
프록시가 필요 없다** — 개발에서만 쓰는 장치다.

## 관련

- [stack.md](./stack.md) — 무엇으로 만드나
- [seed.md](./seed.md) — 시드 생성 절차와 검증
- [standard/backend/persistence.md](./backend/persistence.md) — DB 중립을 지키는 규칙
- — 단일 저장소 + 임베드 빌드
- [`backend/persistence.md`](./backend/persistence.md) · [`backend/session.md`](./backend/session.md)
