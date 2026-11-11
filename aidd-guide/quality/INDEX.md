---
id: LLM-QLT-IDX
title: "quality — 테스트 설계 · 레드팀 · 코드 품질 · 골든셋 · 관측"
category: quality
type: operation
created: 2026-09-04
updated: 2026-09-06
status: active
sources: []
tier: 2
tags: [index, quality, test, redteam, evals]
---

# quality — 품질 규약

> **품질은 게이트에서만 확인되는 것이 아니다.** 여기의 넷은 서로 다른 시점에 서로 다른 것을
> 막는다 — TC 설계는 *구현 전에*, 레드팀은 *게이트 직전에*, 코드 품질은 *슬라이스 완료 시에*,
> 골든셋은 *프롬프트·지식이 바뀔 때마다*.

## 결정 트리

```
무엇을 확인하려는가?
  ├─ "이 기능의 테스트를 무엇으로 짜나"           → test-design.md
  ├─ "비기능 목표를 어떻게 측정하나"              → test-design.md §2
  ├─ "게이트 전에 내 산출물을 어떻게 공격하나"    → redteam.md      · /redteam
  ├─ "테스트가 진짜 검증하는지 어떻게 아나"       → redteam.md §4   (규칙 뒤집기 · 뮤테이션)
  ├─ "슬라이스를 done 으로 표시해도 되나"         → code-quality.md · quality-check.mjs
  ├─ "결함을 어디에 남기나"                       → code-quality.md §7
  ├─ "프롬프트를 고쳤는데 나빠졌는지 어떻게 아나" → evals/INDEX.md
  └─ "토큰 비용·라우팅 정확도·문맥 부패"          → observability/INDEX.md
```

## 카탈로그

| 파일 | 무엇 | 언제 |
|---|---|---|
| [test-design.md](test-design.md) | 최소 세트(정상·경계·거부·예외) · TC 작성 형식 · 비기능 TC | **P3** — 구현 전에 존재해야 한다 |
| [redteam.md](redteam.md) | 자기반박 · 중요 반례 · 선택적 규칙 반전/자동 변이 | **레드팀 필수 · 변이 선택** |
| [code-quality.md](code-quality.md) | 중복률 · 예외 삼킴 · `@feat` 태깅 · 미니게이트 · 결함 관리 | **슬라이스 완료 시** |
| [evals/INDEX.md](evals/INDEX.md) | 골든셋 · 회귀 · A/B · 화면 재현 | 프롬프트·지식·카탈로그를 고칠 때 |
| [observability/INDEX.md](observability/INDEX.md) | 라우팅 정확도 · 토큰 경제 · Context Rot 감시 | 상시 |

## 기계가 세는 것 — 세 게이트

| 명령 | 무엇을 막나 | 임계 |
|---|---|---|
| `node .claude/hooks/quality-check.mjs` | 중복 · 예외 삼킴 · 태깅 누락 | 중복률 ≤5% · 삼킴 0 · 미태깅 0 |
| `node .claude/hooks/trace-check.mjs --all` | 추적성 T1~T14(G1 청구는 `--g1`) — **규칙의 정의는 [process/05-traceability.md](../process/05-traceability.md) 가 갖는다** | 오류 0 |
| `node aidd-guide/meta/_meta/lint.mjs` | 문서 메타블록·200줄·깨진 링크 | 위반 0 |

- **임계값의 근거는 [code-quality.md](code-quality.md) 가 갖는다** — 2026 GitClear·CodeRabbit 실증.
  숫자를 여기서 다시 적지 않는다.
- **임계값을 올려서 통과시키지 않는다**(AGENTS.md §5-10). 복사한 것은 추출한다.

## 무엇을 고쳤나 → 무엇을 더 돌리나

**위 셋과 소스 빌드가 기본이고**(AGENTS.md §7), 아래는 **고친 것이 생성물의 입력일 때** 더 돈다.
빠뜨리면 남의 트리에서 깨진다 — 생성물이 소스와 갈리기 때문이다.

| 무엇을 고쳤나 | 추가로 돌릴 것 |
|---|---|
| `documents/spec/**` | `node .claude/hooks/trace-check.mjs --all` (훅이 자동으로도 돈다) |
| `40-domain/**` · 제품 Liquibase changelog | `node aidd-guide/harness/runtime/domain-schema.mjs` + doc-gen 생성/`--check` + HTML 재생성. logical 초안은 경고, 소유 G3는 물리 계약/매핑 미완성 오류. [후속 조치](../artifact/data-model.md); 실제 DB 시험 별도 |
| `aidd-guide/**` · `documents/screen/**` | `node aidd-guide/meta/_meta/lint.mjs` — **위반 0 이어야 한다** |
| `.claude/hooks/**` · `.claude/skills/**` · `settings.json` | `node .claude/hooks/harness-doc.mjs --check` — 훅이 자동으로 다시 만들지만, **커밋에 담겼는지**는 이것이 본다 |
| `shared/**` · `core/**` · `support/**` 에 **폴더를 더하거나 지웠다** · 가이드의 `sources:` 를 고쳤다 | `node .claude/hooks/commons-doc.mjs --check` — 공통물 목록이 실물과 어긋났는지. 위와 같은 이유로 **커밋 누락**을 본다 |
| `menu-tree.yaml` | `cd sources && ./gradlew generateMenuSeed` |
| 컨트롤러 · `endpoints.ts` · `screenRegistry.ts` | `cd sources && ./gradlew generateApiSeed` |
| `messageKeys.ts` · `MsgKey.java` | `cd sources && ./gradlew generateMessageSeed` (값은 **새 changeset** 으로) |
| **changelog 파일을 더했다** | **`master` 에 include** — 안 걸면 파일만 있고 적재되지 않는다([artifact/screen/screen.md](../artifact/screen/screen.md) ④-b) |
| 슬라이스를 마쳤다 | `node .claude/hooks/quality-check.mjs` — 중복률 ≤5% · 예외 삼킴 0 · `@feat` 미태깅 0 |
| 전부 | `cd sources && ./gradlew generateSeeds && ./gradlew checkSeeds` |

## 왜 넷으로 나눠 두었나

**한 문서에 담았더니 209줄이 되었고, 그 안에서 서로 다른 시점의 규칙이 섞였다.** 테스트 설계를
찾는 사람과 슬라이스를 닫으려는 사람이 같은 파일을 통째로 읽어야 했다. 200줄 규칙이 잡아낸 것은
분량이지만 **진짜 문제는 주제가 넷이었다는 것**이다 — 예외를 두는 대신 나눴다.
