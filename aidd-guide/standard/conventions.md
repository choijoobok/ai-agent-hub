---
id: LLM-FWK-002
title: "공통 규약 — 이름 · 인코딩 · 주석 · 게이트"
category: standard
type: operation
class: none
created: 2026-05-04
updated: 2026-09-12
status: active
sources: ["sources/backend/", "sources/frontend/", "AGENTS.md"]
tier: 2
tags: [convention, naming, encoding, comment]
---

# 공통 규약 — 이름 · 인코딩 · 주석 · 게이트

> **양쪽에 걸리는 것만 여기 있다.** 백엔드만의 것은
> [sources/backend/](./backend/INDEX.md), 프론트만의 것은 [sources/frontend/](./frontend/INDEX.md) 가 갖는다.

## 1. 인코딩과 줄끝

**UTF-8(BOM 없음) · LF.** `.gitattributes` 가 `eol=lf` 를 건다.

훅 `normalize-encoding` 이 **쓰기 직후 고친다** — 판단의 여지가 없는 것이라 묻지 않는다.
다만 **훅은 `Write`·`Edit` 도구에만 걸린다** — 스크립트로 파일을 만들었으면 직접 확인한다.

## 2. 이름

| 무엇 | 규칙 |
|---|---|
| 테이블 | **30자 이내**, 공통은 `SYS_` 접두, 샘플은 `SMPL_`. 예약어 회피 |
| 컬럼 | `_DT`(날짜) · `_DTM`(일시) · `_YN`(플래그) · `_CD`(코드) · `_NM`(이름) |
| API 경로 | 기본 `/api/{계층}/{도메인}/{동작}` · 모듈러 제품 `/api/{모듈}/{계층}/{도메인}/{동작}` — **모듈 뒤에 보안 계층**, camelCase 단수 |
| 자바 서비스 | 조회 `<기능>QueryService` · 명령 `<기능>Service` · 충돌 시만 `Admin` |
| 자바 레코드 | 행 `*Row` · 저장 입력 `*SaveRow` · 단건 키 `*Params` · 화이트리스트 `*Spec` |
| 프론트 폴더 | 소문자 붙여쓰기 — `batchschedule` · `notirequest`(백엔드 애그리거트와 대체로 대응) |
| 프론트 API 파일 | **폴더 이름 그대로** — `features/code/api/codeApi.ts` |
| 메시지 키 | 점 표기 — `err.duplicate-code` · `col.product.id` · `code.LANG_CD.ko` |

**이름은 패키지·경로가 못 하는 말만 해야 한다.** 같은 말을 두 번 하면 그 자리에서
규칙이 갈린다([standard/backend/packages.md §3](./backend/packages.md) 의 `Admin` 사례).

## 3. 문구 — 소스에 박지 않는다

| 어디 | 무엇 |
|---|---|
| 화면 문구 | **`MSG` 키 + `label()`** |
| 서버 문구 | **`MsgKey` + `SYS_MESSAGE`** |
| **개발자용 로그·예외 메시지** | **영어** |

**문자열·JSX 텍스트·템플릿 문자열 안의 한글을 ESLint 가 막는다** — 막는 것은 **한글**이지 그 문법이 아니다. `` `${a} · ${b}` `` 같은 조립은 정당하다.

## 4. 주석

- **한국어로 쓴다.** 읽는 사람이 한국어 사용자이고, AI 도 같은 문서를 읽는다.
- **무엇을 하는지가 아니라 왜 그런지를 적는다.** 코드가 이미 *무엇* 을 말한다.
- **되돌리기 쉬운 것에는 주석이 필요 없다.** 다음 사람이 *"이거 왜 이렇게 했지"* 하고
  고칠 만한 자리에 적는다 — 그 자리가 곧 실패가 재발하는 자리다.
- **순서·자리가 규약인 곳에는 반드시 적는다.** 주석만으로는 못 막으니 **테스트를 함께
  둔다**([standard/backend/crosscutting.md §2](./backend/crosscutting.md)).
- 설계 문서를 가리킬 때는 **절 번호까지** 적는다.

## 5. 게이트 — 고쳤으면 돌린다

> **아래 명령은 전부 `sources/` 에서 돈다**(AGENTS.md §1 — 빌드 루트가 거기다).
> 저장소 루트에서 치면 `./gradlew` 가 없다.

```bash
./gradlew check -PskipFrontend
```

```bash
cd frontend && npm run lint && npx tsc -b --noEmit && npm run test
```

| 무엇을 고쳤나 | 추가로 |
|---|---|
| `menu-tree.yaml` | `./gradlew generateMenuSeed` |
| 컨트롤러 · `endpoints.ts` · `screenRegistry.ts` | `./gradlew generateApiSeed` |
| `messageKeys.ts` · `MsgKey.java` | `./gradlew generateMessageSeed` |
| 전부 | `./gradlew generateSeeds` → `checkSeeds` |

**초록을 확인하지 않은 변경은 완료가 아니다.**

## 6. 커밋

- **경로를 지정해 커밋한다** — `git commit -F - -- <경로…>`. 한 트리에서 세션이 둘일 수
  있고, **낡은 인덱스가 남의 커밋을 되돌린다.**
- **메시지는 stdin(`-F -`)으로 넘긴다** — POSIX heredoc 또는 PowerShell here-string pipe.
- **생성물은 소스와 같은 커밋에** 담는다.
- **AI 커밋은 마지막 trailer 묶음에 provider의 `Agent:` 표식**을 둔다.
- 훅 `guard-commit` 이 위 규칙을 **차단**한다.

## 7. 샘플은 통째로 지울 수 있어야 한다

- 공통 코드가 **샘플을 참조하지 않는다** — `SampleIsolationTest` 가 막는다.
- 샘플은 **`SMPL_` 테이블만** 쓴다.
- 고객사에 넘길 때 **지우는 것이 `sample` 하나이도록** 한다.

## 관련

- [AGENTS.md §5 · §7 · §8 · §9](../../AGENTS.md) — 요지
- [standard/backend/packages.md](./backend/packages.md) · [standard/frontend/packages.md](./frontend/packages.md)
- [seed.md](./seed.md) — 생성물과 손으로 쓰는 것
