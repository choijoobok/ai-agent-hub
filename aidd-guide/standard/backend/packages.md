---
id: LLM-FWK-BE-004
title: "백엔드 구조 — 모듈 · 패키지 · 이름 · 트랜잭션"
category: standard/backend
type: operation
class: policy
created: 2026-05-04
updated: 2026-11-14
status: active
sources: ["sources/backend/", "sources/backend/core/src/main/java/com/aiagent/core/security/", "sources/backend/core/src/main/java/com/aiagent/core/content/", "sources/backend/support/src/main/java/com/aiagent/support/content/"]
tier: 2
tags: [backend, module, package, naming, transaction]
---
# 백엔드 구조 — 모듈 · 패키지 · 이름 · 트랜잭션

> 새 기능의 모듈·패키지·이름을 정하고 의존 방향을 지킨다.
## 1. 모듈과 소유권

```
sources/backend/
├── core/             도메인 비종속 커널 — 응답·에러·검색 규약·감사 엔티티
├── support/          core만 의존하는 기술 어댑터 — 엑셀·락·로깅·시각·설정
├── domain/           이행 전 공통 업무 도메인 — 엔티티·리포지터리·서비스
├── api/              HTTP·Security·필터·예외·세션 경계
├── modules/system/   system 업무 API·DTO·도메인
├── modules/admin/    관리 저장·전체 이력·결과 확인 공통 기반
├── modules/portal/   Portal 서버 인증·세션·보안 사건
├── modules/sample/   통째로 제거 가능한 참조 구현 API·DTO·도메인
└── bootstrap/        조립 루트 — main · application.yml · Liquibase · 프론트 임베드
```

| 모듈 | 의존 |
|---|---|
| `core` | — |
| `support` | `core` |
| `domain` | `core` · `support` |
| `api` | `core` · `support` · `domain` |
| `modules/system` | `api` |
| `modules/admin` | `api` |
| `modules/portal` | `api` |
| `modules/sample` | `api` |
| `bootstrap` | `api` · `modules/system` · `modules/admin` · `modules/portal` (+ **local 변형일 때만** `modules/sample`) |

이 표는 **현재 Gradle 직접 의존**이다. `api`가 이행 전 `domain`을 공개하므로 system·sample도 그 타입을 간접 가시성으로 쓸 수 있다. 새 모듈이 다른 제품 모듈 내부를 참조하는 것은 금지하며,
`LayerDependencyTest`와 `SampleIsolationTest`가 역방향·샘플 누출을 막는다.

`api`에는 `auth`·`authz`·`config`·`error`·`log`·`message`·`progress`·`system`
(`SystemInfoController`)·`trace`만 둔다. 이들은 HTTP·Security·요청 경계 공통물이므로
`core`나 `support`로 옮기지 않는다. 제품 업무는 `modules/{모듈}`로 이행한다.

공유 인증 입력·결과와 순수 Java 포트는 `core/security`, Servlet·Security·JDBC 세션 조립은 `api/auth/boundary`, 업무 계정·권한 판정은 소유 모듈에 둔다. `bootstrap`에서 연결하며 중립 포트에 프레임워크 타입을 노출하지 않는다.

현재 v2 인증 조립은 `bootstrap/config/AuthSessionConfiguration`이 ADMIN/PORTAL 카탈로그와 소유 보호를 하나의 필터에 연결한다. `core/security/portal/PortalAccounts`를 통해 Portal이 admin 원장 공개 서비스를 소비하며 제품 모듈끼리 Gradle 의존을 추가하지 않는다. Portal 자격 변경은 먼저 확정하고 같은 요청의 후속 회전이 실패하면 재로그인을 요구한다. ADMIN의 기존 원자 경계와 혼동하지 않는다.

> 포트/어댑터를 전면 도입하지 않는다. 교체할 락·엑셀·스토리지는 `support`, 공유 인증 계약은 `core`로 분리한다(AGENTS.md §1).

## 2. 패키지 — `com.aiagent`

```
core        domain · error · grid · message · response · search
support     config · excel · lock · logging · time
domain      {애그리거트}/
              entity/  repository/
              {기능}/          ← 화면 하나가 쓰는 것들이 여기 모인다
              service/         ← 공통 계층이 쓰는 서비스만
api         {HTTP·Security·요청 경계}/
              controller · request/ · (필요 시) response/
modules     {모듈}/src/main/java/com/aiagent/{모듈}/
              api/ · domain/ · public/
```

이행 전 `domain` 애그리거트 열일곱: `auth` `authz` `batch` `code` `config` `favorite` `gridpref`
`lock` `log` `menustat` `message` `notification` `progress` `retention` `search` `searchcond` `user`.
`sample`은 여기에 두지 않고 `modules/sample`이 전부 소유한다.
### 레이어가 아니라 기능으로 묶는다

`controller/` · `service/` · `entity/`를 최상위에 두지 않고 업무 화면별 파일을 모은다.

`dto/`는 두지 않는다. 애그리거트 안은 엔티티·리포지터리와 기능 서브패키지로 갈리고,
`*Service`·`*QueryService`·`*Row`·`*SaveRow`·`*Spec`·`*Params`·`*ErrorCode`를 기능에 모은다.

## 3. 이름

| 무엇 | 이름 |
|---|---|
| **조회 서비스** | **`<기능>QueryService`** — 예외 없다. `Admin` 을 넣지 않는다(패키지가 이미 말한다) |
| **명령 서비스** | **`<기능>Service`** — 기본이다 |
| 〃 이름이 부딪힐 때 | **`<기능>AdminService`** — 같은 도메인에 공통 계층 서비스가 있어 이름을 쓸 수 없는 경우**뿐**이다. 실제로 둘이다: `CodeAdminService`(↔ `CodeService`) · `MessageAdminService`(↔ `MessageService`) |
| 컨트롤러 | 같은 규칙이다. `CodeAdminController` · `MessageAdminController` 만 `Admin` 을 단다 |
| 목록 행 | **`<도메인><무엇>Row`** |
| 저장 입력 | **`*SaveRow`** |
| 단건 키 | **`*Params`** |
| 화이트리스트 | **`*Spec`** |
| api 층 요청 | **`*Request`** (`api/{도메인}/request/`) |
| api 층 응답 | **`*Response`** — API 전용 응답이 필요할 때만. **도메인 레코드를 그대로 내보내는 것이 기본** |
### 메서드 이름 — **URL 마지막 마디와 같다**

```
POST /api/system/service/code/saveGroup
                       ↑
   CodeAdminController.saveGroup()  →  CodeAdminService.saveGroup()
                       ↑
   endpoints.ts        saveGroup: '/service/system/code/saveGroup'
```

**넷이 한 낱말이다** — URL 마디 · 컨트롤러 메서드 · 서비스 메서드 · `endpoints.ts` 키.
[api.md](./api.md) 가 앞의 셋을 이미 묶어 두었고, **서비스까지 같게** 하는 것이 이 줄이다.

- **행을 여럿 받아도 단수다.** `saveGroups` 가 아니라 `saveGroup` 이다 — 일괄 저장은 이
  저장소의 기본이라 이름에 적을 것이 없고, 적으면 **URL 과 갈린다**. *무엇을 몇 개 받나*
  는 시그니처가 말한다.
- **`/service/`와 `/public/`에서만 그렇다.** `/common/`은 접두가 인가 액션을 정하므로
  `favorite/save` → `add()`처럼 갈릴 수 있다([api.md](./api.md)).
- 이 규칙은 복수 입력 때문에 `saveGroups`처럼 URL과 서비스 이름이 갈리는 일을 막는다.

> **`Admin`을 이름의 장식으로 쓰지 않는다.** `<도메인>/admin/` 패키지가 이미 말하는 역할을
> 이름에 반복하면 다른 조회 서비스와 갈린다. 이름은 패키지가 못 하는 말만 해야 한다.
>
> **그래서 `<기능>` 은 마디 둘을 가른다.** `detail`·`menu`·`schedule` 은 **기능**이라 이름에
> 들어가고(`UserDetailService`·`BatchScheduleService`), `admin`·`service` 는 **역할**이라
> 들어가지 않는다(`user/admin/UserService`·`code/service/CodeService`). **역할 마디는 이름이
> 부딪힐 때만 드러나며, 그때도 명령만 그렇다** — `code/admin` 이 `CodeAdminService`(↔공통
> `CodeService`)이면서 조회는 `CodeQueryService` 다.
>
> **위 표만으로는 읽히지 않는 자리다** — `user/detail` 이 마디를 붙이니 `user/admin` 도
> 붙일 것처럼 보이고, 그러면 `UserAdminService` 가 나온다.
### `ErrorCode` 상수 — 술어 어휘를 고정한다

**같은 뜻이면 같은 이름을 쓴다.** 파일마다 새로 지으면 문구 시드의 키(`err.{도메인}.{상수}`)가
갈리고, 같은 상황을 두 이름으로 부르게 된다.

| 뜻 | 상수 |
|---|---|
| 그 행이 없다 | `NOT_FOUND` |
| 이미 있다 | `DUPLICATE` — 대상이 여럿인 파일은 **`{대상}_DUPLICATE`**(`CODE_DUPLICATE` · `IP_DUPLICATE`) |
| 값이 비었다 | `REQUIRED` — 〃 **`{대상}_REQUIRED`**(`NAME_REQUIRED` · `PERIOD_REQUIRED`) |
| 지울 수 없다 | `NOT_DELETABLE` |
| 기간이 뒤집혔다 | `PERIOD_REVERSED` · 값 자체가 틀렸으면 `{대상}_INVALID` |
| 모르는 값 | `{대상}_UNKNOWN` |
| 이력 조회 기간 | `RANGE_REQUIRED` · `RANGE_TOO_WIDE` · `PURGED` |

**`DUPLICATED` 는 쓰지 않는다** — `SampleErrorCode` 하나가 그렇게 쓰고 나머지 열여덟이
`DUPLICATE` 다.

### 두 화면이 한 패키지를 쓸 때 — 조회는 모으고 명령은 가른다

`/role`(역할 관리)와 `/role-auth`(역할 권한)가 `domain/authz/role/` 하나를 쓴다.

- **조회는 `RoleQueryService` 하나**다 — 두 화면이 같은 표를 같은 조인으로 읽으므로,
  가르면 `holderCount` 를 세는 질의가 두 벌이 되고 한쪽만 고쳐진다.
- **명령은 `RoleService`(역할의 생애)와 `RoleAuthService`(권한 부여)로 가른다** —
  트랜잭션 경계도 인가 액션도 다르다([knowledge/sys/menu.md](../../knowledge/sys/menu.md)).
- **`{화면}QueryService` 를 새로 만들지 않는다.** 이름이 화면을 가리키면 두 번째 화면이
  왔을 때 갈 곳이 없다 — 이름은 **패키지가 담는 것**을 가리킨다.

## 4. 트랜잭션 — 모양이 셋이다

| 서비스 | 클래스 레벨 | 메서드 레벨 |
|---|---|---|
| **순수 조회** (`*QueryService`) | `@Transactional(readOnly = true)` | **없음** |
| **순수 명령** (관리 화면 저장 — `*Service`) | **없음** | 쓰기 메서드마다 `@Transactional` |
| **혼합** (공통 계층 — 읽기와 쓰기를 함께) | `readOnly = true` | **+** 쓰기 메서드마다 `@Transactional` |

- **경계는 `Service` 메서드다.** 컨트롤러에 트랜잭션을 두지 않는다.
- **트랜잭션 안에서 HTTP·메일을 호출하지 않는다.** 커밋 후
  `@TransactionalEventListener(AFTER_COMMIT)` 로 뺀다.
- **트랜잭션·`EntityManager` 를 다른 스레드로 전파하지 않는다.** 비동기 작업은 자기
  트랜잭션을 시작한다.
- 이력 기록처럼 본 트랜잭션의 롤백과 갈라야 하는 것은 `REQUIRES_NEW`.
- **목록을 처리할 때는 경계가 선택이다** — 전체에 하나냐 행마다 하나냐. **저장이냐 실행이냐가
  가르지 않는다**: 실패했을 때 무엇이 남아야 하는가가 가른다 →
  [row-transaction.md](./row-transaction.md).

> **①만 기계가 강제한다**(`ServiceConventionTest`). ②는 **붙이지 않는 것이 맞는 경우**가
> 있어 — `MenuService` 는 이미 락의 트랜잭션 안이다 — 기계로 셀 수 없다.

## 5. 기계가 지키는 것

| 테스트 | 무엇 |
|---|---|
| `LayerDependencyTest` | 기반 모듈 의존 방향 (`core ← support ← domain ← api`) |
| `ServiceConventionTest` | 조회 서비스는 클래스 레벨 `readOnly` · 메서드 레벨 없음 · 이름에 `Admin` 없음 / 컨트롤러에 트랜잭션 없음 |
| `SampleIsolationTest` | 샘플 전용 모듈·구 패키지 부재 · 밖에서 참조 금지 · `SMPL_` 테이블만 |
| `RestrictedSessionPathTest` | 세션 제한 경로 |
| `ApiOperationSummaryTest` | API 요약 |

`sources/backend/bootstrap/src/test/java/com/aiagent/architecture/`에서 변경 관련 검사를 선택한다.

**샘플은 local 전용으로 통째로 분리한다** — **local 변형**(`-Pprofile=local`, 또는 명시한
`-PincludeSample=true`)일 때만 `modules/sample`과 프런트 라우트·시드·sample 기준선을 조립한다. product에는 외부 주소
`/sample/**`·`/api/service/sample/**`도 없다. 공통·제품 코드는 샘플을 참조하지 않는다.

## 관련
- [persistence.md](./persistence.md) — JPA / QueryDSL / MyBatis 경계
- [api.md](./api.md) — 경로·응답·액션 판정
- [INDEX.md](./INDEX.md) — 백엔드 카테고리 진입
- [AGENTS.md §8](../../../AGENTS.md) — 기계가 막는 것 / 사람이 지키는 것

### 정적 콘텐츠 표시 포트와 기술 렌더러
소유 관리 모듈의 원문 저장과 소비 모듈을 직접 의존시키지 않는다. `core/content`는 판·형식·정책 버전을 묶는 소비 계약을, `support/content`는 네트워크 없이 허용 표현만 만드는 표시 기술을 둔다. 실제 소유 데이터·권한·소비 참조 보호 구현은 관리 모듈에 남긴다. 원문을 정제 결과로 덮어쓰지 않으며 표시 정책을 바꾸면 보존 판의 재현과 현재 소비자를 함께 검토한다. 프로젝트 연결/시험 절차는 [개발 가이드](../../../developer-guide/03-develop.md)의 채팅 안내 절을 따른다.
