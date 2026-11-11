---
id: LLM-HRN-008
title: "훅 계약 — 배선 · lib.mjs · 새 훅을 더할 때"
category: harness
type: operation
created: "2026-09-06"
updated: 2026-10-28
status: active
sources: ["aidd-guide/harness/capabilities.yaml", ".claude/hooks/", ".claude/settings.json", ".codex/hooks.json"]
tier: 2
tags: [harness, hooks, contract, wiring]
---

# 훅 계약 — 배선 · `lib.mjs` · 새 훅을 더할 때

> **훅을 만들거나 고칠 때** 읽는다. *어떤 훅이 있고 각각 왜 그런가*는 [hooks.md](./hooks.md)가 갖는다 — 갈린 축은 **읽는 이유**다: 여기는 만드는 사람, 저기는 막힌 사람이 온다.

## 정본과 구현

| 층 | 정본 | 책임 |
|---|---|---|
| 공통 의미 | 이 문서 · [`capabilities.yaml`](./capabilities.yaml) | 목적·강도·실패 정책·동등성 |
| 공통 스킬 | `skill-src/*.md` | 절차와 trigger |
| Claude adapter | `.claude/settings.json` | Claude 이벤트·입력·컨텍스트 게이지 |
| Codex adapter | `.codex/hooks.json` · `.codex/hooks/` | `apply_patch` 정규화·압축 뒤 재주입 |
| 정책 엔진 | `.claude/hooks/*.mjs` | 역사적 경로를 유지한 공용 Node 로직. 화면 역인덱스는 `screen-registry.mjs` 하나를 훅·스킬이 함께 쓴다 |
| 공통 chain | `runtime/chains.mjs` | 실행 순서의 유일한 원본 — manifest·adapter·생성 문서가 함께 읽는다 |
| 추적 관계 | `runtime/trace-model.mjs` | 소유·사용 관계, 단/복수 호환, 소스 경로와 공통 소비자 해석 |
| spec-set 로더 | `runtime/spec-set.mjs` | 논리 spec 이름을 기존 단일 YAML 또는 분할 폴더로 읽는 유일한 원본 |
| 사전 진입 | `runtime/pretool-bootstrap.mjs` | 파일 chain이 import 전부터 깨져도 차단하고 훅 파일 수리만 연다 |
| 셸 파싱 | `runtime/shell-parse.mjs` | **따옴표 안이 데이터인가 명령인가** — `guard-bash`·`guard-commit`이 함께 읽는 유일한 원본 |

`spec-set`은 `이름.yaml`과 `이름/_index.yaml` 중 하나만 허용한다. 폴더형은 명시적 `parts` 순서로 병합하며 미등재 YAML·중복·폴더 이탈·값 덮어쓰기를 실패시킨다. 새 공용 로직부터 `runtime/`에 둔다.

## 배선

| 이벤트 | Claude | Codex | 공통 결과 |
|---|---|---|---|
| `SessionStart` | `session-start` chain | `session-start`(`startup`) · `session-brief`(`resume\|clear\|compact`) | `git-sync` → `session-brief` |
| `PreToolUse` 파일 | `pre-write` chain | `file-chain` | `guard-write`(기능 소유 작업흐름 G3) → `guard-generated` |
| `PreToolUse` shell | `pre-shell` chain | `pre-shell` chain | 파괴 명령·커밋 사고 차단 → **셸이 쓰는 경로를 파일 chain 에 태운다** |
| `PostToolUse` 파일 | `post-write` chain | `file-chain` | 인코딩 → 추적성 → 문서 현행화 |
| `UserPromptSubmit` | `log-raw` + `context-gauge` | `log-raw` | 사용자 대화 기록 |
| `Stop` | `log-raw` | `log-raw` | AI 대화 기록 |

- **배선은 도구 이름으로 선다 — 그래서 무엇을 덮는지 세지 않으면 도구를 바꾸는 것만으로 게이트가 사라진다.** `harness-check`가 provider별 대표 도구 이름(Claude의 `Write`·`Edit`·`NotebookEdit`, Codex의 `apply_patch`, 양쪽의 shell·MCP 쓰기)을 각 matcher 정규식에 실제로 먹여 본다. 새 쓰기·셸 도구가 생기면 그 목록에 먼저 더한다. **파일 쪽만 MCP를 덮고 셸 쪽을 두면 같은 구멍이 남는다** — 2026-09-14 실측에서 `Bash`로 막힌 명령이 MCP `start_process`로 그대로 돌았다.
- **셸 chain이 보는 것은 `tool_input.command` 하나다.** 셸을 *새로 여는* MCP 도구는 덮지만 이미 열린 REPL에 이어 넣는 입력(`interact_with_process`의 `input`)은 못 본다 — 그 자리는 사후 chain과 사람이 본다. 같은 matcher의 command hook은 동시 실행될 수 있으므로, 순서가 필요한 정책은 handler를 여러 개 배선하지 않고 **chain 하나**가 직렬 실행한다.
- **같은 chain에는 같은 timeout을 준다.** 예산이 갈리면 좁은 쪽의 뒤쪽 훅이 잘리므로 `harness-check`가 파일 chain 둘을 비교한다.
- 실행은 `node` 하나다. `package.json`도 `node_modules`도 보지 않는다(AGENTS.md §1).
- 사전 보호 chain에서 내부 오류가 나면 차단한다. 사후 chain 오류는 되돌릴 수 없으므로 경고한다.
- **정책의 원본은 훅 코드가 아니다.** 차단·교정·경고의 근거는 먼저 `AGENTS.md` 또는 `aidd-guide/`에 있어야 하고, 훅은 그 규칙을 기계적으로 집행한다. matcher와 입력 정규화 같은 구현 상세만 훅이 단독으로 가질 수 있다.
- `trace-check` 강도는 `process/05-traceability.md`가 정한다. T2·T5·T7의 P3 설계 결손은 뒤 차수나 소유 작업흐름 G3 전에는 경고이고, `--workstream <키>` G3 검사에서 그 소유 범위를 오류로 올린다. T2는 FEAT 미연결 `REQ.workstream`도 엄격 검사한다. **T13은 예외다** — G3 승인 뒤에도 `planned` 슬라이스의 TC는 경고이고, `slices[].testcases`로 매핑된 소유 슬라이스가 `in_progress` 이상이 된 뒤부터 코드 부재를 오류로 올린다. 구형 상태처럼 TC 매핑이 없으면 그 기능의 슬라이스가 모두 `done`이거나 P4가 승인된 뒤 엄격하게 검사한다. spec에 없는 TC를 코드가 부르는 역방향은 언제나 오류다. **T14(요구사항 정련 카드)** 의 완전성은 평소 경고이고 `--g1`(G1 청구)에서 소유 작업흐름 G1 승인 전인 REQ만 오류로 올린다. 카드끼리의 연계·실현 정합 위반은 언제나 오류다. 명시 비저장 ENT의 허용/금지 필드는 `artifact/spec-schema.md`를 따르고 `domain-schema.self-test.mjs`에서 정상·물리/HIST 혼입 거부·일반 logical 오류 유지 반례를 검사한다.
- chain 순서는 코드에 다시 쓰지 않는다. `runtime/chains.mjs`를 adapter와 문서 생성기가 직접 읽고, `harness-check`가 `capabilities.yaml`과 대조한다.

### 추적·현행화 사후 검사 계약

- 입력: spec 변경은 T1~T14, 소스 변경은 경로·선언된 sources/source_contract·헤더 태그·화면 등록정보.
- 판정: API/SCR 소유와 사용 관계는 분리하고 공통 소비자는 전부 찾는다. 화면 없는 기능의 빈 화면/API는 오류가 아니다.
- 강도: 기존 단계별 강도를 유지한다. 잘못된 관계·기능 태그·명시 화면 사례 연결은 오류, 문서 대조는 경고다.
- 출력: 정본 경로·영향 식별자·재생성 지시. 조회 결과가 수정·게이트 승인을 대신하지 않는다.
- 한계: API 전수 인벤토리·DTO 필드·동적 호출은 소스와 별도 대조한다. 미등록 연결을 영향 없음으로 판정하지 않는다.
- fixture: `runtime/trace-model.self-test.mjs` — chain 실행·다대다/공통 소비자·순환·삭제 경로·배치·복수 메뉴·잘못된 참조. 양쪽 adapter는 같은 정책 엔진을 실행한다.

### 깨진 보호에서 빠져나오는 길

사전 chain이 죽으면 지키던 도구와 고칠 도구가 함께 막힌다. 다음 장치로 복구한다.

| | 무엇 |
|---|---|
| 애초에 안 잠기게 | `hook-syntax` 가 사후 chain에서 **방금 쓴 훅**에 `node --check` 를 돌린다. 실행 권한·timeout 실패는 구문 오류라고 하지 않고 **검사 불가**로 알린다 |
| 길을 알린다 | 차단 사유에 `RECOVERY` 를 붙인다 — 쓰기가 막혔으면 *Bash로 고친다*, 셸이 막혔으면 *편집 도구로 고친다* |
| import 전부터 닫는다 | `pretool-bootstrap`이 파일 사전 chain의 입력을 먼저 보관하고 adapter import 실패를 구조화된 deny로 바꾼다. shell 진입 실패도 provider command wrapper가 명시적 deny로 바꾼다 |
| 수리 자리만 연다 | **죽어서 막힌 경우에만** 훅 adapter(`.claude/hooks/`·`.codex/hooks/`)와 **그들이 함께 쓰는 실행 원본**(`aidd-guide/harness/runtime/`)의 `.mjs` 쓰기를 통과시킨다 — `chains.mjs` 가 깨지면 모든 chain 이 서지 못하는데 열지 않으면 **잠긴 사람이 잠근 파일을 못 고친다**. 정책 차단과 다른 경로는 절대 열지 않는다 |
| 남의 트리로 안 퍼지게 | CI가 훅 전부에 `node --check` 를 돌린다 — 구문이 깨진 훅이 `main`에 들어가면 **clone하는 사람이 즉시 잠긴다** |

fixture는 `.codex/hooks/self-test.mjs`가 갖고 여는 쪽과 **닫히는 쪽을 함께** 센다.

## 배치 명세 추적성 확장

기존 spec 쓰기 후 trace-check 공통 엔진은 `65-batches`의 JOB 참조·필수 계약·소유 FEAT/UC/REQ/TC 및 실제 BatchJob 구현 누락을 검사한다. Claude/Codex의 기존 post-write adapter와 경고 강도·실패 정책을 유지하며 새 이벤트나 승인 흐름을 추가하지 않는다. 문서 생성은 같은 검사에서 오류가 있으면 쓰기 전에 실패한다. `batch-contract.self-test.mjs`는 정상과 누락/오참조/구현체 미등재 반례를 검증한다.

## `git-sync` — 세션 시작의 원격 동기화

| 칸 | 값 |
|---|---|
| 이벤트 | `SessionStart`, **세션 시작(`startup`)에서만** |
| 입력 | 훅 입력의 `source`(Claude) · `startup` 전용 matcher 그룹(Codex) |
| 판정 | 원격 없음 → 조용히 통과 / 추적 브랜치 없음·fetch 실패 → 알림 / **작업 트리가 더러우면 당기지 않는다** / 깨끗하고 뒤처졌으면 `git pull --ff-only` / 이력이 갈렸으면 `integrate-main` 안내 |
| 강도 | 교정(당길 수 있을 때만) · 문맥에 넣는다 |
| 출력 | `[원격 동기화]` 로 시작하는 **한 덩이** — 무엇을 했나 · 못 했으면 왜 · 다음에 무엇을 하나 |
| 실패 정책 | **던지지 않는다.** git 이 없거나 네트워크가 죽어도 세션은 열린다. 네트워크 15초 · 로컬 5초에서 끊는다 |
| fixture | `.codex/hooks/self-test.mjs` — `source: 'compact'` 에서 아무것도 하지 않는다 |

**세 가지를 하지 않는 것이 이 훅의 절반이다.** ① 미커밋 변경 위로 남의 커밋을 얹지 않는다 — 충돌 해소는 사람의 판단이다(AGENTS.md §5-2). ② `--ff-only`가 안 되면 자동 merge하지 않고 `integrate-main`으로 넘긴다. ③ **푸시하지 않는다**(AGENTS.md §9).

## 통합 상태 — 현재 경고와 과거 이력을 섞지 않는다

`integration-state`는 `pending_rechecks`와 최근 `[충돌 통합]` 3건을 브리핑하고 CI에서 형식을 검사한다. `guard-commit`은 `pull --ff-only`·`merge --no-commit`, merge 상태의 경로 없는 커밋 예외, 충돌 커밋의 `영향`·`확인`·`검증`을 집행한다. 상세 정책은 [14-collaboration.md](../process/14-collaboration.md)가 갖는다.

`resume`·`clear`·`compact` 에서 돌지 않는다 — 작업 중간에 파일이 바뀌면 AI가 읽은 것과 디스크가 어긋난다.

startup 훅도 진입 문서와 hook 설정이 **로드된 뒤** 돈다. pull로 바뀐 것을 현재 실행에 적용됐다고 가장하지 않는다. Claude Code는 진입 문서·하네스 변경이면 새 세션을 연다. Codex는 문서 규칙만 바뀌면 현재 작업에서 다시 읽어 적용하되, CLI에서 훅을 새로 승인했거나 훅 배선이 바뀌면 Windows 앱의 기존 세션에는 활성화되지 않으므로 새 세션 창을 연다.

## `skill-sync` — 공통 스킬 원본 → 두 AI 의 `SKILL.md`

| 칸 | 값 |
|---|---|
| 이벤트 | `PostToolUse` 파일 chain, `guide-drift` 다음 · `harness-doc` **앞**(생성 결과를 `harness-doc` 이 센다) |
| 입력 | `aidd-guide/harness/skill-src/**` · `runtime/sync-skills.mjs` 쓰기 |
| 판정 | `skillDiffs()` 가 비면 통과 / 다르면 **양쪽 `SKILL.md` 를 다시 만든다** / `name` 불일치면 만들지 않고 알린다 |
| 강도 | 교정 · 경고 |
| 출력 | 다시 만든 스킬 이름과 *같은 커밋에 담으라* 는 말 |
| 실패 정책 | 생성기가 던져도 훅은 통과시키고 경고만 낸다 — 쓰기를 되돌릴 수는 없다 |

**왜 즉시 만드나.** `SKILL.md` 는 생성물이고 손편집은 `guard-generated` 가 막는다. 그러면 원본을 고친 사람은 고쳤다고 믿는데 **AI 가 부르는 것은 옛 파일**이다 — 다음 세션의 `harness-check` 가 잡아 주지만 **그 세션 안에서는 고친 스킬을 쓸 수 없다.** 일부 provider 쓰기가 실패하면 성공이라고 말하지 않고 실패 목록과 재실행 명령만 알린다.

## `guard-shell-write` — 셸이 쓰는 경로도 파일 게이트에 태운다

| 칸 | 값 |
|---|---|
| 이벤트 · 입력 | `PreToolUse` shell chain 의 끝 · `tool_input.command` |
| 판정 · 강도 | 명령에서 쓰기 대상을 뽑아 **`pre_file` chain 을 그대로** 돌린다 · 차단. 복사·이동은 원본이 아니라 목적지를 보고 `>|`·`<>`·`-t` 목적지도 포함한다. 저장소 밖·`/dev/null`·glob·인용부 안은 통과 |
| 출력 · 실패 정책 | 막힌 경로마다 원래 훅의 사유 · 사전 chain 이라 던지면 차단한다 |
| 못 보는 것 | 스크립트가 자기 안에서 여는 파일(`node -e` · gradle), 인용부 안의 리다이렉션, 변수·glob 경로, 삭제(`rm` — `@defer ?`) |
| fixture | `.codex/hooks/self-test.mjs` — 잡는 형태와 **통과해야 하는 형태**를 함께 센다 |

**왜.** 2026-09-14 실측에서 `Write` 로 막히는 지도 밖 경로가 `printf > …` 로는 조용히 만들어졌다. 게이트가 도구 이름으로 서면 **도구를 바꾸는 것만으로 게이트가 사라진다.** `guard-commit` 은 [hooks.md](./hooks.md)가 갖는다.

## 공통 계약 — `lib.mjs`

| 함수 | 무엇 |
|---|---|
| `readHookInput()` | 표준 입력 또는 bootstrap이 보관한 JSON 한 덩이. **못 읽으면 빈 객체** — 훅이 조용히 통과한다 |
| `REPO` | 저장소 뿌리. **작업 디렉터리를 믿지 않고** 훅 파일 위치에서 `AGENTS.md`를 찾아 올라간다 |
| `targetPath(input)` · `featureWorkstream()` · `workstreamGateStatus()` | 대상 경로를 읽고, FEAT의 명시 `workstream` 또는 `module`을 소유 작업흐름 G3에 연결한다 |
| `repoRelative(abs)` | 저장소 기준 상대 경로(슬래시 통일). **바깥이면 빈 문자열** |
| `pass()` · `deny(reason)` · `warn(message)` | 통과 · 차단 · 알림. chain 안에서는 결과 객체로 회수한다 |
| `runEmbeddedHook()` | 자식 process 없이 기존 정책 엔진을 한 번 실행한다 |
| `provider()` · `PROVIDER_NAME` | 지금 부른 것이 `claude`인가 `codex`인가 — bootstrap 표식 또는 **진입 경로가 정한다**. 모르면 `unknown` |
| `skillRef(name)` | 그 runtime에서 **실행되는 형태**의 스킬 표기 — `/이름` · `$이름` · 모르면 `이름 스킬` |

**두 가지가 설계 결정이다** — 고칠 때 되돌리지 않는다:

- **`REPO` 를 `process.cwd()` 로 바꾸지 않는다.** 훅이 어느 경로에서 불릴지는 하네스가 정하고, 그것이 바뀌면 **훅이 조용히 아무것도 안 하게** 된다.
- **provider 판정을 훅 입력의 필드로 하지 않는다.** 안정 계약에 없는 필드로 분기하면 조용히 틀린다. 판정의 근거는 **우리가 만든 배선**이고, `harness-check`가 그 전제(Codex는 어댑터를 거친다)를 검사한다.
- **`repoRelative` 는 저장소 밖을 빈 문자열로 돌려준다.** `normalize-encoding` 은 파일을 *고치므로* 밖을 상대 경로로 오인하면 남의 파일을 손댄다. **다른 드라이브**가 그 자리다 — 윈도우에서 `relative('D:\repo', 'C:\tmp\x')` 는 `..` 로 시작하지 않고 **절대 경로 그대로**를 돌려준다.

`warn()` 은 `systemMessage`(사람)와 `additionalContext`(모델)를 **둘 다** 보낸다 — *사람이 봤는데 AI 는 모르는* 상태를 만들지 않는다.

**Codex가 읽는 출력은 JSON 하나다.** `SessionStart`는 사용자에게 보이는 `systemMessage`와 모델이
읽는 `additionalContext`를 함께 내고, `PreToolUse` 허용은 결정 없는 중립 `hookSpecificOutput`을 낸다. 종료 코드 0의 빈 출력도 계약상 허용되지만 일부
실행면이 침묵을 `Failed`로 표시한 이력이 있어 호환 형태를 명시한다. Claude `SessionStart`는
날것이며, `emitHookResults` 한 곳에서 가르고 어댑터가 stdout을 섞지 않는다 — 사례는 [providers/codex.md](./providers/codex.md).

## Codex 세션 주의 — 안내만 하고 작업을 제어하지 않는다

| 칸 | 값 |
|---|---|
| 출력 | 시작·재개 훅은 Windows 앱의 상태 영역에 `Codex 훅 승인 상태 확인`을 표시한다. `SessionStart` adapter는 `[Codex 주의]`를 `systemMessage`로 내고, 같은 문구를 첫 사용자 요청의 최종 답변(`final_answer`) 첫 줄에 세션당 한 번만 표시하라는 지시와 브리핑을 `additionalContext`로 모델에 주입한다. `commentary` 표시는 이 계약을 충족하지 않으며, 이전 assistant `final_answer`에 이미 표시됐다면 이후 응답에서는 반복하지 않는다. `session-brief` adapter는 재개 문맥만 주입한다 |
| 주의 내용 | 훅은 CLI에서 승인해야 작동한다. 사용자는 `/hooks`에서 승인 여부를 반드시 확인하고, 새로 승인했다면 Windows 앱에서 새 세션 창을 연다 |
| 강도 | 안내. 승인 상태·revision·세션별 상태를 저장하거나 확인하지 않으며, 이를 이유로 모델 작업이나 `PreToolUse`를 막지 않는다 |
| 배선 | `startup`은 `git-sync` → `session-brief`, `resume|clear|compact`는 `session-brief`를 직접 실행한다 |
| fixture | `.codex/hooks/self-test.mjs`가 시작·재개 adapter 배선, 승인 차단 부재, 사용자용 `systemMessage`, `final_answer` 표시와 `commentary` 비충족 지시, 모델용 `additionalContext`를 각각 확인한다 |

Codex CLI는 `systemMessage`를 경고로 표시하지만 현재 Windows 앱은 같은 출력을 대화 화면에 표시하지
않는다. 공식 계약도 UI **또는** 이벤트 스트림 노출까지만 보장한다. 그래서 새 세션의 첫 사용자 요청에
대한 `final_answer`는 같은 문구를 첫 줄에 표시한다. 접히는 `commentary`에 먼저 표시했더라도 첫 최종
응답에서 다시 표시한다. 이전 assistant `final_answer`에 이미 표시됐다면 이후 응답에서는 반복하지 않으며,
`resume`·`clear`·`compact`에서도 대화 중간에 반복하지 않는다.

훅 설정이 작업 중 바뀌면 현재 실행이 새 설정을 자동으로 싣는다고 가정하지 않는다. CLI에서 새로 승인한 훅은 Windows 앱의 기존 세션에 활성화되지 않으므로 새 세션 창에서 확인한다.

## 새 훅을 더할 때

1. `harness-sync` 스킬로 이 문서에 이벤트·입력·판정·강도·출력·실패 정책·fixture를 먼저 적는다. 판정의 `AGENTS.md`·`aidd-guide/` 근거가 없으면 훅보다 규칙을 먼저 쓴다.
2. Claude와 Codex 입력이 같으면 정책 엔진을 공유하고 adapter만 둔다. 다르면 결과 동등성을 적는다.
3. `lib.mjs`만 import하고 런타임 의존성을 더하지 않는다.
   **`pass`·`warn`·`deny`를 `try` 안에 두지 않는다** — chain에서 이 셋은 예외로 빠져나오므로
   옆의 `catch`가 삼키면 훅이 *일은 하고 아무 말도 안 하는* 상태가 된다.
4. `deny`에는 *고칠 곳*과 *다음에 돌릴 것*을 함께 적는다.
5. 두 provider 배선과 self-test를 같은 변경에서 완성한다. 한쪽 실측을 못 하면 미검증으로 보고한다.
6. `harness-check`, `harness-doc --write`, 문서 lint를 실행한다.

## 훅이 의심스러울 때

```bash
echo '{"tool_input":{"file_path":"sources/backend/bootstrap/src/main/resources/db/changelog/data/003-init-menu.csv"}}' | node .claude/hooks/guard-generated.mjs
```

- **출력이 비면 통과한 것이다**(`pass()` 는 아무 말도 하지 않는다).
- 아무 훅도 안 도는 것 같으면 `REPO` 탐색과 provider 신뢰 승인을 먼저 본다.
- Codex 주의 문구가 보이지 않으면 `SessionStart` 배선과 adapter 출력을 확인한다.
- `doc-drift` 가 한 번 말하고 조용하면 정상이다. 다시 보려면 tmp 의 세션 파일을 지운다.

## 관련

- [hooks.md](./hooks.md) — 훅 하나하나가 무엇을 보고 무엇을 말하나
- [automation-matrix.md](./automation-matrix.md) — 무엇을 언제 · 강도가 왜 넷인가
- [skills.md](./skills.md) — 스킬과의 경계
