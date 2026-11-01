# 07. 훅과 스킬 — 지금 무엇이 있나

> **이 파일은 생성물이다. 손으로 고치지 않는다** — 다음 생성에서 사라지고, 훅이 막는다.
> 정본은 `aidd-guide/harness/`의 명세와 provider 배선이며
> `node .claude/hooks/harness-doc.mjs --write` 가 만든다.
>
> **여기는 *지금 무엇이 있나* 다.** *왜 그렇게 만들었나* 는
> [`aidd-guide/harness/hooks.md`](../aidd-guide/harness/hooks.md) 와
> [`skills.md`](../aidd-guide/harness/skills.md) 가 갖는다 — 같은 사실을 두 번 적지 않는다.

---

## 훅

같은 의미의 정책 엔진을 공유하고 입력 adapter와 컨텍스트 전략만 다르게 둔다.

### Claude Code — 17개

| 이벤트 | 언제 | 훅 | 하는 일 | 강도 | 제한 |
|---|---|---|---|---|---|
| `SessionStart` | — | `git-sync` | 세션이 열릴 때 원격을 먼저 당긴다 | 문맥에 넣는다 | 60s |
| `SessionStart` | — | `session-brief` | 세션이 열릴 때 지금 어디인가를 문맥에 넣는다 | 문맥에 넣는다 | 60s |
| `PreToolUse` | `Write\|Edit\|NotebookEdit\|^mcp__.*__(?:write\|edit\|move\|create)` | `guard-write` | 쓰기의 다섯 사고를 막는다 | **차단** · 경고 | 45s |
| `PreToolUse` | `Write\|Edit\|NotebookEdit\|^mcp__.*__(?:write\|edit\|move\|create)` | `guard-generated` | 생성물을 손으로 고치는 것을 막는다 | **차단** | 45s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-bash` | 되돌릴 수 없는 명령을 막는다 | **차단** | 20s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-commit` | 커밋의 네 사고를 막는다 | **차단** | 20s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-shell-write` | 셸로 파일을 쓰는 것도 파일 게이트에 태운다 | **차단** | 20s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `normalize-encoding` | BOM 과 CRLF 를 방금 쓴 파일에서 걷어낸다 | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `hook-syntax` | 방금 고친 훅이 구문부터 깨졌는지 그 자리에서 본다 | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `trace-check` | 추적성 검사 — `PostToolUse`(`documents/spec/**` 쓰기) 훅 겸 `/trace` 스킬의 실행기. | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `doc-drift` |  | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `guide-drift` | `sources:` 에 이 파일을 적은 가이드 문서를 대조하라고 알린다 | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `skill-sync` | 공통 스킬 원본이 바뀌면 두 AI 의 `SKILL.md` 를 다시 만든다 | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `harness-doc` | 훅·스킬 목록을 `developer-guide/07-harness.md` 로 만든다 | 경고 | 90s |
| `PostToolUse` | `Write\|Edit\|^mcp__.*__(?:write\|edit\|move\|create)` | `commons-doc` | 공통물 목록을 `aidd-guide/standard/registry.md` 로 만든다 | 경고 | 90s |
| `UserPromptSubmit` | — | `log-raw` | 대화 원문을 `chat-history/` 에 append 한다 | 조용히 한다 | 10s |
| `UserPromptSubmit` | — | `context-gauge` | 컨텍스트 게이지 — 문맥이 차오르면 압축할 여지가 남아 있을 때 알린다. | 문맥에 넣는다 | 10s |
| `Stop` | — | `log-raw` | 대화 원문을 `chat-history/` 에 append 한다 | 조용히 한다 | 15s |

### Codex — 16개

| 이벤트 | 언제 | 훅 | 하는 일 | 강도 | 제한 |
|---|---|---|---|---|---|
| `SessionStart` | `startup` | `git-sync` | 세션이 열릴 때 원격을 먼저 당긴다 | 문맥에 넣는다 | 15s |
| `SessionStart` | `startup` | `session-brief` | 세션이 열릴 때 지금 어디인가를 문맥에 넣는다 | 문맥에 넣는다 | 15s |
| `SessionStart` | `resume\|clear\|compact` | `session-brief` | 세션이 열릴 때 지금 어디인가를 문맥에 넣는다 | 문맥에 넣는다 | 15s |
| `PreToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `guard-write` | 쓰기의 다섯 사고를 막는다 | **차단** · 경고 | 45s |
| `PreToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `guard-generated` | 생성물을 손으로 고치는 것을 막는다 | **차단** | 45s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-bash` | 되돌릴 수 없는 명령을 막는다 | **차단** | 20s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-commit` | 커밋의 네 사고를 막는다 | **차단** | 20s |
| `PreToolUse` | `Bash\|PowerShell\|^mcp__.*__(?:start_process\|execute_command\|run_command\|run_shell_command\|shell_exec\|bash\|terminal\|exec)` | `guard-shell-write` | 셸로 파일을 쓰는 것도 파일 게이트에 태운다 | **차단** | 20s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `normalize-encoding` | BOM 과 CRLF 를 방금 쓴 파일에서 걷어낸다 | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `hook-syntax` | 방금 고친 훅이 구문부터 깨졌는지 그 자리에서 본다 | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `trace-check` | 추적성 검사 — `PostToolUse`(`documents/spec/**` 쓰기) 훅 겸 `/trace` 스킬의 실행기. | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `doc-drift` |  | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `guide-drift` | `sources:` 에 이 파일을 적은 가이드 문서를 대조하라고 알린다 | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `skill-sync` | 공통 스킬 원본이 바뀌면 두 AI 의 `SKILL.md` 를 다시 만든다 | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `harness-doc` | 훅·스킬 목록을 `developer-guide/07-harness.md` 로 만든다 | 경고 | 90s |
| `PostToolUse` | `apply_patch\|Edit\|Write\|^mcp__.*__(?:write\|edit\|move\|create)` | `commons-doc` | 공통물 목록을 `aidd-guide/standard/registry.md` 로 만든다 | 경고 | 90s |
| `UserPromptSubmit` | — | `log-raw` | 대화 원문을 `chat-history/` 에 append 한다 | 조용히 한다 | 10s |
| `Stop` | — | `log-raw` | 대화 원문을 `chat-history/` 에 append 한다 | 조용히 한다 | 15s |
- 사전 보호 chain 내부 오류는 해당 쓰기를 차단한다. 사후 검사 오류는 명시 경고한다.
- **`node` 하나로 돌며 의존성이 없다.**
- 컨텍스트는 Claude만 사용량 경고를 내고, Codex는 자동 압축 뒤 SessionStart로 재주입한다.

### 걸리지 않은 것 — 손으로 부른다

| 파일 | 하는 일 | 언제 |
|---|---|---|
| `quality-check` | 코드 품질 기계 검사 — 슬라이스 미니게이트(G4)의 기계가 세는 부분. | 필요할 때 `node .claude/hooks/quality-check.mjs` |
| `screen-registry` |  | 필요할 때 `node .claude/hooks/screen-registry.mjs` |

---

## 스킬 — 18 개

공통 원본에서 두 발견 경로로 생성한다. **AI가 정해진 절차를 따르게 하는 지시서**다.

| Claude | Codex | 무엇을 | 언제 쓰나 |
|---|---|---|---|
| `/adr` | `$adr` | 아키텍처 의사결정 기록(ADR)을 작성한다. | 되돌리기 비용이 큰 기술·설계 선택을 대안·근거·포기한 것과 함께 남긴다. 기술 스택, DB, 인증, 아키텍처 스타일 등을 정할 때 사용. |
| `/change-request` | `$change-request` | 베이스라인 이후의 변경 요청(CR)을 처리한다. | 영향 분석, 3옵션 제시, 사용자 판정, spec 반영, 재작업 범위 산정. G3 승인 후 설계나 요구사항을 바꿔야 할 때 사용. |
| `/deliverable-site` | `$deliverable-site` | 설계 산출물·사용자 매뉴얼·운영 런북/개발자 가이드를 좌측 트리형 오프라인 HTML 사이트와 선택 PDF로 조립한다. | "고객 제출본", "산출물 사이트", "모든 문서를 HTML", "오프라인 매뉴얼", "PDF 제출본" 요청에 사용한다. |
| `/doc-consistency` | `$doc-consistency` | 프로젝트 문서의 정합성·일관성·모순을 횡단 점검한다. | 여러 문서 동시 변경 뒤, 통합 전후, P1~P6 게이트 전 또는 정기 품질 점검 때 사용한다. |
| `/doc-gen` | `$doc-gen` | documents/spec/ 의 논리 YAML 문서 세트에서 사람이 읽는 산출물 문서를 생성한다. | 요건정의서, 유즈케이스 명세, ERD, API 문서, 화면정의서, 권한 매트릭스, 테스트 케이스, 사용자 매뉴얼 등을 documents/generated/ 아래에 만든다. |
| `/harness-sync` | `$harness-sync` | Claude Code와 Codex가 함께 쓰는 스킬·훅을 추가하거나 수정하고 양쪽 구현의 누락·불일치를 복구한다. | "새 스킬", "새 훅", "Claude와 Codex에 같이 추가", "하네스 동기화", "하네스 불일치" 요청이나 SessionStart parity 경고가 있을 때 쓴다. |
| `/integrate-main` | `$integrate-main` | 같은 main에서 병행한 변경을 pull·merge하거나 충돌을 해결할 때 쓴다. | fast-forward, 충돌 통합 기록, 오프라인 확인, 테스트·문서 재검토 위임을 한 절차로 처리한다. |
| `/interview` | `$interview` | 현재 단계에서 사용자에게 물어야 할 것을 질문 배치로 만들어 제시한다. | 각 질문에 권장안·영향범위·되돌리기비용을 붙인다. 정보가 부족해 진행이 막혔을 때 사용. |
| `/kickoff` | `$kickoff` | 새 시스템 구축 프로젝트를 시작한다. | 사용자가 "○○ 시스템을 만들고 싶다"고 할 때 AI-SDLC 방법론에 따라 P0 착수를 개시하고, 프로젝트 뼈대와 첫 질문 배치를 만든다. |
| `/manual-site` | `$manual-site` | 화면별 manual.md를 사용자 매뉴얼 오프라인 사이트로 조립하고 목업·실제 캡처 치환 상태를 검사한다. | 메뉴얼을 고친 뒤, 사용자 메뉴얼 HTML을 만들 때, 실제 화면을 캡처해 넣을 때 쓴다. "메뉴얼 HTML" · "메뉴얼 사이트" · "화면 캡처" 요청에 쓴다. |
| `/menu-doc` | `$menu-doc` | 메뉴 화면의 문서(requirements.md · manual.md)를 시작하거나, 소스와 대조해 현행화하거나, 전체 화면의 결손 현황을 센다. | 새 화면 착수 전 요건서를 쓸 때, 화면 소스를 고친 뒤 문서를 맞출 때, "화면 문서" · "요건서" · "메뉴얼" 을 만들거나 점검하라는 요청에 쓴다. 목업은 /mockup 이 요건서에서 생성한다. |
| `/mockup` | `$mockup` | 화면 목업(mockup.html)을 요건서에서 생성하거나, 있는 목업이 요건서·메뉴얼과 어긋나지 않는지 검사한다. | 요건서를 고친 뒤 목업을 맞출 때, 새 화면의 목업을 처음 만들 때, "목업을 만들어" · "목업 다시 그려" · "목업 검사" 요청에 쓴다. 신설·수정은 build/의 최종 HTML 산출물 생성·검사까지 한 번에 수행한다. |
| `/phase-gate` | `$phase-gate` | 단계 게이트를 점검하고 통과를 요청한다. | Exit Criteria 자체검증 → 레드팀 자기반박 → 사용자 승인 요청의 4단계를 실행한다. 한 단계를 마치고 다음으로 넘어갈 때 반드시 사용. |
| `/pm-status` | `$pm-status` | 프로젝트 현재 상태를 보고한다. | 지금 어느 단계인지, 게이트 상태, 미결 질문, 진행 중 슬라이스, 다음 할 일을 한 화면에 정리한다. 세션 시작, 프로젝트 "브리핑", 또는 "지금 어디까지 됐어?"처럼 현황을 물을 때 사용. |
| `/redteam` | `$redteam` | 현재 산출물을 적대적으로 검토한다. | AI가 자기가 만든 설계·요구사항·코드를 인수 심사자 입장에서 공격해 실패 시나리오, 누락, 사용자가 반대할 지점을 찾아낸다. 게이트 전이나 중요 산출물 완료 후 사용. |
| `/runbook` | `$runbook` | 운영 런북 정본을 P2~P6 근거로 작성·검증하고 RUN ID, 장애 대응, 백업/복구, 모니터링, 정기 작업, 에스컬레이션의 누락을 점검한다. | "운영 런북", "장애 대응 절차", "백업 복구 절차", "운영 인수" 요청에 사용한다. |
| `/slice` | `$slice` | 수직 슬라이스 하나를 구현한다. | 소유 작업흐름과 스펙 확인 → 테스트 먼저 → 필요한 층 구현 → 계약·테스트 통과 → 미니게이트 순으로 진행한다. P4 구현 단계에서 기능 하나를 만들 때 사용. |
| `/trace` | `$trace` | 추적성 체인을 검사한다. | REQ→UC→FEAT→ENT/API/SCR→TC 연결이 끊긴 곳, 고아 산출물, 미실현 요구사항, dangling ID를 찾아 보고하고 추적성 매트릭스를 생성한다. |

### 스크립트를 가진 스킬

| 스킬 | 스크립트 | 직접 돌리려면 |
|---|---|---|
| `/deliverable-site` | `browser-check.mjs` | `node .claude/skills/deliverable-site/browser-check.mjs` |
| `/deliverable-site` | `build.mjs` | `node .claude/skills/deliverable-site/build.mjs` |
| `/deliverable-site` | `check.mjs` | `node .claude/skills/deliverable-site/check.mjs` |
| `/deliverable-site` | `flow-view-check.mjs` | `node .claude/skills/deliverable-site/flow-view-check.mjs` |
| `/doc-gen` | `approval-contract.mjs` | `node .claude/skills/doc-gen/approval-contract.mjs` |
| `/doc-gen` | `approval-contract.test.mjs` | `node .claude/skills/doc-gen/approval-contract.test.mjs` |
| `/doc-gen` | `gen.mjs` | `node .claude/skills/doc-gen/gen.mjs` |
| `/doc-gen` | `history-contract.mjs` | `node .claude/skills/doc-gen/history-contract.mjs` |
| `/doc-gen` | `history-contract.test.mjs` | `node .claude/skills/doc-gen/history-contract.test.mjs` |
| `/doc-gen` | `menu-contract.mjs` | `node .claude/skills/doc-gen/menu-contract.mjs` |
| `/doc-gen` | `menu-contract.test.mjs` | `node .claude/skills/doc-gen/menu-contract.test.mjs` |
| `/doc-gen` | `sequences.test.mjs` | `node .claude/skills/doc-gen/sequences.test.mjs` |
| `/manual-site` | `build.mjs` | `node .claude/skills/manual-site/build.mjs` |
| `/manual-site` | `capture.mjs` | `node .claude/skills/manual-site/capture.mjs` |
| `/menu-doc` | `requirements-trace.mjs` | `node .claude/skills/menu-doc/requirements-trace.mjs` |
| `/menu-doc` | `requirements-trace.test.mjs` | `node .claude/skills/menu-doc/requirements-trace.test.mjs` |
| `/menu-doc` | `screens.mjs` | `node .claude/skills/menu-doc/screens.mjs` |
| `/mockup` | `add-choice.mjs` | `node .claude/skills/mockup/add-choice.mjs` |
| `/mockup` | `add-choice.test.mjs` | `node .claude/skills/mockup/add-choice.test.mjs` |
| `/mockup` | `admin-direct-management.test.mjs` | `node .claude/skills/mockup/admin-direct-management.test.mjs` |
| `/mockup` | `column-contract.mjs` | `node .claude/skills/mockup/column-contract.mjs` |
| `/mockup` | `column-contract.test.mjs` | `node .claude/skills/mockup/column-contract.test.mjs` |
| `/mockup` | `declared-layout.mjs` | `node .claude/skills/mockup/declared-layout.mjs` |
| `/mockup` | `declared-layout.test.mjs` | `node .claude/skills/mockup/declared-layout.test.mjs` |
| `/mockup` | `generic-layout.mjs` | `node .claude/skills/mockup/generic-layout.mjs` |
| `/mockup` | `generic-layout.test.mjs` | `node .claude/skills/mockup/generic-layout.test.mjs` |
| `/mockup` | `grid-chrome.mjs` | `node .claude/skills/mockup/grid-chrome.mjs` |
| `/mockup` | `grid-chrome.test.mjs` | `node .claude/skills/mockup/grid-chrome.test.mjs` |
| `/mockup` | `grid-math.test.mjs` | `node .claude/skills/mockup/grid-math.test.mjs` |
| `/mockup` | `presentation.mjs` | `node .claude/skills/mockup/presentation.mjs` |
| `/mockup` | `presentation.test.mjs` | `node .claude/skills/mockup/presentation.test.mjs` |
| `/mockup` | `request-layout.mjs` | `node .claude/skills/mockup/request-layout.mjs` |
| `/mockup` | `request-layout.test.mjs` | `node .claude/skills/mockup/request-layout.test.mjs` |
| `/mockup` | `row-actions.mjs` | `node .claude/skills/mockup/row-actions.mjs` |
| `/mockup` | `row-actions.test.mjs` | `node .claude/skills/mockup/row-actions.test.mjs` |
| `/mockup` | `scaffold.mjs` | `node .claude/skills/mockup/scaffold.mjs` |
| `/mockup` | `snapshot-view.mjs` | `node .claude/skills/mockup/snapshot-view.mjs` |
| `/mockup` | `snapshot-view.test.mjs` | `node .claude/skills/mockup/snapshot-view.test.mjs` |
| `/pm-status` | `deferred.mjs` | `node .claude/skills/pm-status/deferred.mjs` |
| `/runbook` | `check.mjs` | `node .claude/skills/runbook/check.mjs` |

