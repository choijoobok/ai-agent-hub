---
id: LLM-FWK-FE-005
title: "HTTP — endpoints.ts · 배럴 하나 · 응답 처리"
category: standard/frontend
type: operation
class: policy
created: 2026-05-04
updated: 2026-09-12
status: active
sources: ["sources/frontend/src/shared/api/", "sources/frontend/eslint.config.js", "aidd-guide/standard/frontend/packages.md"]
tier: 2
tags: [frontend, http, axios, endpoints, request-id]
---

# HTTP — `endpoints.ts` · 배럴 하나 · 응답 처리

> **호출 URL 은 기능의 `api/endpoints.ts` 에만 둔다** — 이행 전에는
> `features/{도메인}`, 모듈 안에서는 `modules/{모듈}/{features|public}/{도메인}`이다.
> sample 화면도 `modules/sample/features/{화면}/api/endpoints.ts`를 쓴다.
> 그 선언이 **인가 시드의 입력**이다 — **선언하지 않고 부르면 403, 선언만 하고 안 부르면
> 접근 범위가 조용히 넓어진다.**

## 1. URL 선언

```ts
// features/user/api/endpoints.ts 또는 modules/system/public/auth/api/endpoints.ts
export const USER_ENDPOINTS = {
  getList: '/service/user/getList',
  save:    '/service/user/save',
} as const;
```

- **`baseURL` 이 `/api` 라 선언은 계층 마디부터 시작한다** — `/service/user/getList`.
- **URL 문자열 리터럴은 ESLint 가 금지한다.** 금지 규칙이 `/api/` 와
  `/public|/common|/service` 를 **둘 다** 잡아야 한다 — **앞의 것만 두면 게이트가 조용히
  빈다.**
- 화면은 **그 상수만** 쓴다.

## 2. 입구는 배럴 하나

```ts
import { get, post, request, ApiError } from '@/shared/api';   // ✅
import { post } from '@/shared/api/request';                    // ❌ ESLint
```

**`@/shared/api/*` 딥 임포트는 금지다**(배럴 안쪽은 예외 — 순환).

> **같은 헬퍼가 두 경로로 들어오면 새 화면이 무엇을 따라야 할지 알 수 없다.**
> 실측에서 **딥 14곳 · 배럴 10곳**이었다. 배럴에 없는 것은 바깥에서 쓸 것이
> 아니며, 필요하면 `index.ts` 를 고친다.

## 3. 호출을 감싸는 자리

기능의 HTTP 호출은 **그 기능의 `api/{도메인}Api.ts` 함수**로 감싼다 —
**화면에 `post<T>(URL, {…})` 가 남지 않는다.**

파일 이름은 **폴더 이름 그대로**(`features/code/api/codeApi.ts`)이며, 계층이 다르면
같은 이름이 둘일 수 있다(`shared/code/api/codeApi.ts` — 공통 코드 조회).

> **예외는 그리드 선언이다.** `read` · `save` · `actions` 는 **URL 을 그대로 받는 것이
> `<DataGrid>` 의 계약**이고, 그 자리를 함수로 감싸면 컨트롤러가 인자를 다시 나열하게 된다.

**열한 기능이 화면에서 직접 부르고 있었다** — 같은 질의의 **페이지 크기·기본
조건이 화면마다 달라지는** 자리다.

### 감쌀 것이 없으면 파일을 두지 않는다

호출이 **그리드 선언뿐인 화면에는 `{도메인}Api.ts` 가 없다** — `loginlog` · `notice` ·
`message` · `role` · `apicatalog` 가 그렇다. 빈 파일을 두면 다음 사람이 **거기에 무엇이
들어가야 하는지** 를 묻는다.

### 함수 이름

- **동사는 엔드포인트 키의 동사를 그대로 쓴다** — `get*` · `save*` · `delete*`.
  그 밖의 행위는 행위를 적는다(`login` · `heartbeat` · `markNotiRead` · `verifyResetCode`).
- **목적어에는 도메인을 붙인다.** 엔드포인트 키는 기능 안에서만 읽히지만
  함수는 화면이 import 해 쓰므로 **혼자 읽혀야 한다** —
  `getMenuTree`(키) → **`getRoleAuthTree`**(함수) · `saveRoleAuth` · `getUserAuthTree`.
  이미 혼자 읽히는 것은 그대로 쓴다(`getRefDefList`).

## 4. 매 요청 붙는 것

| | 처리 |
|---|---|
| baseURL | `/api` (동일 오리진) |
| 인증 | 세션 쿠키 자동 — `withCredentials: true` |
| CSRF | `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더 (axios 기본) |
| **request-id** | UUID 를 **헤더 `X-Request-Id` + 쿼리 `_rid` 양쪽**에 |
| **menu-code** | 활성 탭의 메뉴 코드를 `X-Menu-Code` 로. **메뉴 문맥 없는 호출은 생략** |
| `Accept-Language` | 프론트가 고른 언어 |

> **`crypto.randomUUID` 폴백이 필수다** — **secure context 전용**이라 사내 HTTP 환경에서
> `undefined` 가 된다. 없으면 request-id 가 통째로 비고, **중복 방어와 추적이 함께 죽는다.**

**양쪽에 붙이는 이유(이중 채널)**: 헤더는 프록시·게이트웨이가 지우는 일이 있고,
쿼리는 로그에 남는다. 둘 중 하나만 살아도 요청을 특정할 수 있다.

## 5. 응답 처리

| 상황 | 처리 |
|---|---|
| `code === 'err.duplicate-request'` | **일반 실패 처리보다 먼저 무음 통과** |
| **401** | **재인증 모달** — 탭·입력값을 그대로 두고 셸 위에 띄운다. **곧바로 로그아웃하지 않는다** |
| 403 | 권한 없음 화면 또는 토스트 |
| 5xx | 공통 에러 토스트 + **`traceId` 표시** (운영 문의 시 쓴다) |
| 로딩 | react-query 상태를 쓴다. **전역 스피너는 최소화** |

- **에러 문구를 프론트가 조립하지 않는다.** 서버가 번역을 완성해 `message` 에 담는다 —
  받은 값을 **그대로 보여준다.** **프론트에 에러 메시지 카탈로그를 두지 않는다.**
- **분기는 `code` 로, 표시는 `message` 로.**
- **401 에서 로그아웃하지 않는 것이 규칙이다.** 재인증이 불가능하거나 사용자가 포기하면
  그때 전역 로그아웃한다 — 작성 중이던 입력이 사라지면 같은 일을 두 번 하게 된다.

## 6. 새 API 를 부를 때

1. 컨트롤러 경로를 `/api/service/{도메인}/{동작}`으로 짓는다. 모듈 제품은
   `/api/{모듈}/service/{도메인}/{동작}`, sample은 `/api/sample/service/{동작}`의 도메인 생략 예외를 쓴다. 기존 소스의 계층 우선 경로는 CR-038 이행 대상이다.
2. `endpoints.ts` 에 상수를 더한다.
3. `{도메인}Api.ts` 에 함수를 더한다 (그리드 선언은 예외).
4. **`./gradlew generateApiSeed`** — 잊으면 **403** 이다.
5. 소스·상수·시드를 **한 커밋**에 담는다.

## 관련

- [../seed.md](../seed.md) — 선언이 시드의 입력인 이유
- [../backend/api.md](../backend/api.md) — 경로 세 마디 · 봉투
- [packages.md](./packages.md) — 폴더 구조 · ESLint 게이트
- [AGENTS.md §8](../../../AGENTS.md)
