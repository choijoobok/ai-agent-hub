---
id: LLM-ART-IDX
title: "산출물 지도 — 무엇을 언제 어디에"
category: artifact
type: operation
created: 2026-09-04
updated: 2026-10-29
status: active
sources: []
tier: 1
tags: [artifact, map, index]
---

# 산출물 지도 — 무엇을, 언제, 어디에

> **이 폴더의 다른 문서** — [spec-schema.md](./spec-schema.md)(SSOT `documents/spec/` 의
> 파일과 작성 단계) · [template/](./template/02-requirements.md)(서식) · [screen/INDEX.md](./screen/INDEX.md)(화면 절차).

## 1. 전체 산출물 목록

`S` = SSOT(명세, 직접 작성) · `G` = 생성물(spec에서 파생) · `L` = 원장(누적 기록). spec 경로는 논리 문서의 `_index.yaml` 진입점이며 상세는 [spec-schema.md](./spec-schema.md)를 따른다.

| #   | 산출물                        | 단계    | 종류  | 경로                                                                                                                     | Lite | Std | Full |
| --- | -------------------------- | ----- | --- | ---------------------------------------------------------------------------------------------------------------------- | :--: | :-: | :--: |
| 1   | 프로젝트 헌장 (목적·범위·성공기준)       | P0    | S   | `documents/spec/00-charter/_index.yaml`                                                                                       |  ●   |  ●  |  ●   |
| 2   | 이해관계자 / 액터 정의              | P0    | S   | `documents/spec/01-actors/_index.yaml`                                                                                        |  ●   |  ●  |  ●   |
| 3   | 제약사항 · 전제                  | P0    | S   | `documents/spec/02-constraints/_index.yaml`                                                                                   |  ●   |  ●  |  ●   |
| 4   | 용어사전 (유비쿼터스 언어)            | P1    | S   | `documents/spec/03-glossary/_index.yaml`                                                                                      |  ●   |  ●  |  ●   |
| 5   | 업무 프로세스 정의 (As-Is / To-Be) | P1    | S   | `documents/spec/11-processes/_index.yaml`                                                                                     |  ○   |  ●  |  ●   |
| 6   | 요구사항 목록 (FR/NFR)           | P1    | S   | `documents/spec/10-requirements/_index.yaml`                                                                                  |  ●   |  ●  |  ●   |
| 7   | 아키텍처 결정 기록                 | P2    | L   | `documents/decisions/ADR-{번호}-{제목}.md`                                                                                 |  ●   |  ●  |  ●   |
| 8   | 기술 스택 · 인프라 구성도            | P2    | S+G | `documents/spec/20-architecture/_index.yaml` → `documents/generated/20-아키텍처.md`                                               |  ●   |  ●  |  ●   |
| 9   | 비기능 목표치 (성능·가용성·보안)        | P2    | S   | `documents/spec/21-nfr/_index.yaml`                                                                                           |  ○   |  ●  |  ●   |
| 10  | 유즈케이스 명세 (기본/대안/예외 흐름)     | P3    | S+G | `documents/spec/30-usecases/_index.yaml` → `documents/generated/30-유즈케이스.md`                                                  |  △   |  ●  |  ●   |
| 11  | 기능 목록 · 명세              | P3    | S+G | `documents/spec/31-features/_index.yaml` → `documents/generated/31-기능목록.md`                                                   |  ●   |  ●  |  ●   |
| 12  | 기능별 요건 정의서                 | P3    | G   | `documents/generated/32-요건정의서/FEAT-{###}.md`                                                                           |  ●   |  ●  |  ●   |
| 13  | 도메인/데이터 모델 · ERD           | P3    | S+G | `documents/spec/40-domain/_index.yaml` → `documents/generated/40-ERD.md` (Mermaid)                                            |  ●   |  ●  |  ●   |
| 14  | 물리 스키마 · 테이블 목록/명세서 | P3 | S+G | `documents/spec/40-domain/_index.yaml` ↔ **Liquibase changeset** → `documents/generated/41-테이블명세서/{ENT-ID}.md` · `_index.md`; 모듈 소유, [동기화 서식·절차](data-model.md) | ● | ● | ● |
| 15  | API 명세                     | P3    | S+G | `documents/spec/50-api/_index.yaml` (OpenAPI) → `documents/generated/50-API.md` · `50-openapi.yaml`                                               |  ●   |  ●  |  ●   |
| 16  | 메뉴·화면 목록 · 화면 정의서             | P3    | S+G | `documents/spec/60-screens/_index.yaml` → `documents/generated/60-화면정의서.md`                                                   |  ●   |  ●  |  ●   |
| 17  | 시퀀스 다이어그램                  | P3    | G   | `documents/generated/33-시퀀스/UC-{###}.md` (Mermaid)                                                                     |  △   |  ●  |  ●   |
| 18  | 인터페이스 정의서 (외부 연동)          | P3    | S   | `documents/spec/51-interfaces/_index.yaml`                                                                                    |  ○   |  ●  |  ●   |
| 19  | 권한 매트릭스                    | P3    | S+G | `documents/spec/30-usecases/_index.yaml` → `documents/generated/22-권한매트릭스.md`                                                    |  ○   |  ●  |  ●   |
| 20  | 슬라이스 백로그·현재 후속 검증          | P4~P5 | L   | `documents/project.state.yaml` `slices:` · `pending_rechecks:`                                                         |  ●   |  ●  |  ●   |
| 21  | 소스 코드                      | P4    | -   | `sources/`                                                                                                             |  ●   |  ●  |  ●   |
| 22  | 단위/통합 테스트 코드               | P4    | -   | `sources/backend/*/src/test/` · `sources/frontend/src/**/*.test.ts`                                                    |  ●   |  ●  |  ●   |
| 23  | 테스트 케이스                    | P3~P5 | S+G | `documents/spec/70-testcases/_index.yaml` → `documents/generated/70-테스트케이스.md`                                                |  ●   |  ●  |  ●   |
| 24  | 테스트 수행 결과서                 | P5    | L   | `documents/generated/71-테스트결과서.md`                                                                                     |  ●   |  ●  |  ●   |
| 25  | 결함 관리 대장                   | P5    | L   | `documents/generated/72-결함대장.md`                                                                                       |  ○   |  ●  |  ●   |
| 26  | 추적성 매트릭스                   | P3~P5 | G   | `documents/generated/90-추적성매트릭스.md`                                                                                    |  ○   |  ●  |  ●   |
| 27  | 가정 원장                      | 상시    | L   | `documents/decisions/ASSUMPTIONS.md`                                                                                   |  ●   |  ●  |  ●   |
| 28  | 변경 요청 대장                   | P3~   | L   | `documents/decisions/CHANGES.md`                                                                                       |  ○   |  ●  |  ●   |
| 29  | 배포/운영 런북                   | P2~P6 | S+G | `documents/spec/80-operations/_index.yaml` → `documents/generated/80-운영런북.md`                                                |  ○   |  ●  |  ●   |
| 30  | 사용자 매뉴얼                    | P4~P6 | S+G | `documents/screen/{페이지경로}/manual.md` → 내부 검토 `build/deliverables/user/` · 사용자 독립본 `build/deliverables-user/` |  ●   |  ●  |  ●   |
| 31  | 데이터 이행 계획                  | P6    | S   | `documents/generated/82-데이터이행.md`                                                                                      |  ○   |  ○  |  ●   |
| 32  | 인수 확인서                     | P6    | G   | `documents/generated/99-인수확인서.md`                                                                                      |  ○   |  ●  |  ●   |
| 33  | **화면 요건 정의서 + 테스트케이스**     | P3~P4 | S   | `documents/screen/{페이지경로}/requirements.md`                                                                             |  ●   |  ●  |  ●   |
| 34  | **화면 목업**                  | P3~P4 | G   | `documents/screen/{페이지경로}/mockup.html` — `/mockup` 이 33 에서 만든다                                                         |  ●   |  ●  |  ●   |
| 35  | **화면 사용자 메뉴얼**             | P4    | S   | `documents/screen/{페이지경로}/manual.md`                                                                                   |  ●   |  ●  |  ●   |
| 36  | 〃 실제 화면 그림 · 구현 기록          | P4~P6 | S   | `documents/screen/{페이지경로}/screen.png` · `screen-{그림키}.png` · `impl.md`                                               |  ○   |  ●  |  ●   |
| 37  | 메뉴 없는 공통·독립 화면의 문서               | P3~P4 | S   | `documents/screen/_standalone/{화면이름}/`                                                                                 |  ○   |  ●  |  ●   |
| 38  | 화면 문서 서식                     | 상시    | S   | `aidd-guide/artifact/template/` · `documents/screen/README.md`                                                          |  ●   |  ●  |  ●   |
| 39  | **상세 설계 문서**               | P3    | S   | `documents/design/{주제}.md`                                                                                             |  ○   |  ●  |  ●   |
| 40  | 산출물 대장 (이 지도의 현재 상태)       | 상시    | S   | `documents/INDEX.md`                                                                                                   |  ●   |  ●  |  ●   |
| 41  | **재현 회귀의 근거 기록**           | 상시    | S   | `documents/evals/{차수}/{재현}/SOURCES.md` · `documents/evals/{차수}/{재현}/GAPS.md`                                           |  ○   |  ○  |  ●   |
| 42  | **외부 조사 자료** (비규범)        | P2~   | S   | `documents/research/{주제}.md`                                                                                          |  ○   |  ○  |  ●   |
| 43  | **요구사항 정의서** (읽는 뷰)        | P1~   | G   | `documents/generated/10-요구사항정의서/`                                                                                 |  ●   |  ●  |  ●   |
| 44  | **고객 제출 사이트 구성·제출본**       | P3~P6 | S+G | `documents/delivery.yaml` → `build/deliverables/`                                                                        |  ●   |  ●  |  ●   |
| 45 | **스케줄 배치 목록·명세서** | P3~P6 | S+G | `documents/spec/65-batches/_index.yaml` → `documents/generated/65-스케줄배치명세서/` | ○ | ● | ● |

● 필수 · △ 축약형 · ○ 선택

문서별 책임·연결·현행화는 [spec-schema.md](./spec-schema.md)의 「설계 문서의 책임과 연결」을 따른다. 기능별 명세는 31-features에서 생성하고 공통 화면도 같은 SCR 체계로 관리한다. API 목록은 독립 추적 기준이며 Swagger UI는 필수가 아니다.

### 33~38 — 화면 문서는 왜 `generated/` 가 아닌가

**spec 에서 생성되지 않는다.** `60-screens/_index.yaml` 은 화면의 *목록과 소속* 을 갖고,
요건서는 그 화면의 *컬럼·검증·영향 테이블·테스트 케이스* 를 갖는다 — 후자는 spec 에 담기지 않는
분량이며 **설계자가 목업과 함께 보고 승인하는 대상**이다(AGENTS.md §6.3 ③). 그래서 직접
작성(`S`)이고, `60-screens/_index.yaml` 의 `SCR-###` 과 폴더가 1:1 로 대응하는 것으로 추적성을 유지한다.

**34 목업만 `G` 다.** 요건서의 §1 배치와 §테스트 케이스에서 [`/mockup`](../../.claude/skills/mockup/SKILL.md) 이
만든다 — 골격·표·확인 목록은 기계가 옮기고 **그림만 AI 가 그린다.** 목업이 정본이던 시절에는
*근거는 목업 §N 에 있다* 는 케이스가 쌓였고, 그 절이 사라지면 근거도 사라졌다. 지금은
**결정과 그 검증이 같은 문서(33)에 있어** 어느 한쪽만 낡는 일이 생기지 않는다.

### 41 — 재현 회귀의 근거는 왜 자리를 갖나

**샌드박스가 사라지면 다시 만들 수 없다.** 재현 회귀([quality/evals/screen-reproduction.md](../quality/evals/screen-reproduction.md))는
독립한 재현 둘을 스크래치 worktree 에서 돌리고, 끝나면 그 트리를 버린다. 그런데 그
문서가 *"`SOURCES.md`·`GAPS.md` 가 이 실험의 산출물이다 — 소스는 대조 대상일 뿐"* 이라고
선언한다. **버리면 남는 것은 집계 비율뿐이고, 그러면 잰 값을 아무도 되짚을 수 없다.**

- **목적** — *이 비율이 어디서 나왔나* 와 *독립한 둘이 정말 같은 자리를 짚었나* 를 답한다.
  뒤엣것이 미룬 것의 근거이므로(§2-0 의 `A ≒ B ≠ 실물` 판독) **되읽을 수 없으면 그
  판정도 근거가 없다.** 기존 셋으로 답할 수 없다 — 커밋 메시지는 결론만, ADR 은 결정만,
  `project.state.yaml` 은 **값만** 갖는다.
- **포맷** — [screen-reproduction.md](../quality/evals/screen-reproduction.md) §2-2 의 4·5 가
  서식이다(분류 넷 · 최소 25행 · GAPS 항목 다섯). **`artifact/template/` 에 따로 두지
  않는다** — 프롬프트가 곧 서식이고, 갈라 두면 프롬프트를 고칠 때마다 둘이 갈린다.
- **누가 소유하나** — **정본(`S`)이다.** 생성물이 아니라 재현이 쓴 것이며 다시 만들 수
  없으므로 **커밋한다.** 잰 값 자체(차수 · 비율 · 축 일치)는 여기 두지 않는다 —
  `project.state.yaml` 의 `reproduction_baseline` 이 갖고, 여기는 **그 값의 근거**다.

### 42 — 외부 조사 자료는 왜 자리를 갖나

**ADR 로 답할 수 없는 것이 남는다.** ADR 은 결정 **하나**를 담는데 조사는 그보다 넓다 —
사례·프레임워크 지형·PoC 결과는 **여러 결정에 걸쳐** 근거가 되고, 어느 결정에도 속하지 않는 관측(*가지 않은 길*)도 있다.

- **목적** — *근거가 무엇이었나* 와 *가지 않은 길은 무엇이었나*. 읽는 사람은 **같은 결정을
  다시 마주한 다음 사람**이고, 판단하는 것은 *그 근거가 아직 유효한가* 다.
- **포맷** — [`template/00-research.md`](./template/00-research.md). **ADR 앞 번호가 순서다.**
- **소유** — **정본(`S`)이며 커밋한다.** 다시 만들 수 없다 — PoC 코드가 사라져 `ASM-003` 이
  *"결과를 재현할 수 없다"* 고 적었고, **근거가 없으면 결정이 미확인 가정으로 강등된다.**
- **비규범이다.** 순서는 **조사 → ADR → spec** 이고 spec·`aidd-guide/` 는 **ADR 을** 인용한다.

### 39 — 상세 설계 문서는 무엇을 담나

**spec YAML 로 접히지 않는 설계**만 담는다 — 상태 기계의 전이 규약, 오케스트레이션 흐름,
프로토콜처럼 *구조화된 행* 이 아니라 *규범적 산문* 인 것. 담을지 판단하는 기준은 하나다:
**ID 를 가진 항목의 목록으로 표현되면 spec 이고, 그렇지 않으면 여기다.**
여기에 적은 것도 상위 spec 의 ID 를 헤더에 명시해 추적성 체인에 붙인다.

### 44 — 고객 제출 사이트 구성은 왜 자리를 갖나

- **목적** — 세 사이트의 경계와 입력만 결정한다. 내용은 원래 정본에 둔다.
- **포맷** — [`template/06-delivery.yaml`](./template/06-delivery.yaml): SITE ID·입력·오프라인·PDF·캡처 정책.
- **소유** — `delivery.yaml`은 정본, `build/deliverables/`는 파생이며 UI 자산은 한 벌이다.

## 2. 이 지도가 곧 문서 예산이다

> 문서를 정리하는 문서와 그 근거가 다시 생기면 정본이 갈린다.
> **그래서 문서는 예산 안에서만 만든다**(AGENTS.md §4).

**`documents/` 에는 위 표에 있는 자리만 만든다.** `guard-write` 훅이 이 파일의 경로 칸을
읽어 **그 밖의 새 문서를 차단한다** — AGENTS.md §4 의 집행 지점이 여기다.

- 새 종류가 정말 필요하면 **이 표에 행을 먼저 더하고** 사용자에게 왜 필요한지 보고한다.
  번호는 마지막 행 다음으로 이어 붙이며, 지운 행의 번호는 재사용하지 않는다.
- **새 유형은 규격화해야 자리가 열린다** — 셋을 함께 낸다:
  1. **목적** — 누가 읽고 무엇을 판단하는가. 기존 셋(커밋·ADR·상태)으로 답하면 만들지 않는다.
  2. **포맷** — `artifact/template/`에 서식을 만든다. 없으면 유형이 아니라 낱개다.
  3. **소유** — 정본/파생과 생성 원본 또는 현행화 기준을 밝힌다.
- **훅의 차단은 유지한다.** 경고로 낮추면 절차가 번거로울 때 그냥 지나가게 되고,
  그 순간 예산은 없는 것이 된다.
- **경로 칸의 `{…}`는 자리표시자다** — 가변 부분은 반드시 그렇게 적어야 훅이 자리를 연다.

### 자리가 있다는 것과 커밋한다는 것은 다르다

**`G` 로 표시된 자리는 파생물이라 형상 관리에 넣지 않는다**(AGENTS.md §4-5). 지도에 자리가
있는 이유는 *만들 때 어디에 두나* 를 정하기 위해서이고, 커밋하지 않는 이유는 **정본에서 언제든
다시 만들 수 있어서**다. 둘을 헷갈리면 정본을 한 줄 고칠 때마다 파생물이 diff 를 덮는다.

| | 어디 | 커밋 |
|---|---|---|
| **정본** | `documents/spec/` · `screen/*/requirements.md` · `screen/*/manual.md` · `decisions/` · `design/` | **한다** |
| **파생** | `documents/generated/` · `screen/*/mockup.html` · `build/deliverables/` | 하지 않는다 |
| **파생이지만 빌드가 읽는다** | 생성 시드 CSV | **한다** — 빼면 새로 클론한 사람이 앱을 띄울 수 없다 |

**목업은 세 번째 칸에 넣지 않는다.** 시드 CSV와 달리 `/manual-site` 때 `/mockup`으로 다시 만들 수 있다. 앱이 뜨는 일과 문서를 만드는 일은 급함이 다르다.
문서 작업의 임시 파일은 [빌드 안내 정본](site-kit/BUILD-README.txt)의 작업별 `build/tmp/` 하위에 둔다. 반복 실행할 생성기/검증기는 해당 도구 폴더에서 형상 관리한다.

## 3. 산출물 생성 원칙

### 원칙 A — SSOT를 먼저, 문서는 나중에
`S` 표시 산출물만 손으로(=AI가 직접) 만든다. `G` 는 `/doc-gen`, `/mockup`,
`/deliverable-site` 같은 지정 생성기가 정본에서 만든다.
사람이 `documents/generated/` 를 고치면 다음 생성 때 덮어써진다. 훅이 경고한다.

### 원칙 B — 문서는 "AI가 다시 읽을 것"을 전제로 쓴다
- 파일 하나가 너무 크면 AI가 다음 세션에 통째로 못 읽는다. **기능별로 파일을 쪼갠다** (`documents/generated/32-요건정의서/FEAT-012.md`).
- 각 디렉터리에 `_index.md` 를 두고 ID와 한 줄 요약만 나열한다. AI는 인덱스를 먼저 읽고 필요한 것만 연다.

### 원칙 C — 다이어그램은 Mermaid로
ERD·시퀀스·상태도·흐름은 검색·부분 수정 가능한 Mermaid 코드블록으로 작성한다.

### 원칙 D — 산출물마다 헤더 블록 필수
```markdown
---
id: FEAT-012
title: 스키마 카탈로그 등록
traces_to: [UC-004, REQ-011]
status: baselined
generated_from: documents/spec/31-features/
last_sync: 2026-10-28
---
```
`generated_from` 이 있으면 생성물이다. 직접 수정 금지 표시이기도 하다.

## 4. 산출물 생성 시점 (게이트 기준)

```
P0 게이트 통과 조건 → 1, 2, 3 완료
P1 게이트 통과 조건 → 4, 5, 6 완료 + 요구사항이 액터/프로세스에 모두 연결됨
P2 게이트 통과 조건 → 7, 8, 9 완료 + 모든 NFR에 측정 가능한 수치
P3 게이트 통과 조건 → 10~19, 39 완료 + 추적성 체인 무결 + ★spec 베이스라인 동결★
P4 (슬라이스별) → 21, 22, 33~36 + 해당 TC 통과. 슬라이스 미니게이트.
P5 게이트 통과 조건 → 23, 24, 25, 26 완료 + 잔여 결함이 합의된 수준 이하
P6 게이트 통과 조건 → 29~32 완료 + 사용자 인수
```
