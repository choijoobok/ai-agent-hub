# documents/ — AI 가 만드는 모든 산출물

> **여기 있는 것이 프로젝트의 상태다.** 규칙은 [`aidd-guide/`](../aidd-guide/INDEX.md) 가,
> 코드는 [`sources/`](../sources/) 가, 개발자 안내는 [`developer-guide/`](../developer-guide/INDEX.md) 가 갖는다.
>
> **자리가 정해진 것만 만든다.** 만들 수 있는 자리는
> [산출물 지도](../aidd-guide/artifact/INDEX.md)이 정하고 `guard-write` 훅이 **차단한다**
> (AGENTS.md §4). 이 파일은 지도의 **현재 상태**를 보여줄 뿐이며 자리를 정하지 않는다.

세 고객 제출 사이트의 배치는 [`delivery.yaml`](delivery.yaml)이 정한다. 원문을 옮기지 않고
현재 정본·파생 문서를 `build/deliverables/`로 조립한다.

## 지금 어디인가

**[`project.state.yaml`](project.state.yaml) 하나가 그 답이다.** 단계·게이트·미결 질문·
진행 지표·슬라이스가 전부 거기 있다. `/pm-status` 가 그것을 읽어 보고한다.

> **현황을 적는 다른 문서를 만들지 않는다.** 이 프로젝트가 세 저장소에서 물려받은 가장 큰
> 문제가 그것이었다(AGENTS.md).

## 폴더 여섯

| 폴더                                       | 무엇                                                  | 누가 쓰나               | 손으로 고치나                     |
| ---------------------------------------- | --------------------------------------------------- | ------------------- | --------------------------- |
| [`spec/`](spec/)                         | **SSOT** — 헌장·액터·제약·용어·요구사항·아키텍처·NFR·권한·기능·화면·인터페이스 | AI 가 직접             | **예** (여기가 원본)              |
| [`decisions/`](decisions/ASSUMPTIONS.md) | 이 프로젝트의 **ADR** · **가정 원장** · **변경 대장**            | AI 가 직접             | **예** (누적 원장)               |
| [`design/`](design/)                     | spec YAML 로 접히지 않는 **규범적 산문** — 상태 기계·프로토콜·오케스트레이션  | AI 가 직접             | **예**                       |
| [`research/`](research/)                 | **비규범** — 결정의 근거가 된 외부 사례·기술 평가·PoC 실측                    | AI 가 직접             | **예** (다시 만들 수 없다)        |
| [`screen/`](screen/README.md)            | 화면 문서 셋 — 요건서 · 목업 · 메뉴얼 · 선택 캡처                   | AI 가 직접 (설계자가 승인)   | **예** (목업·제출 HTML은 생성물) |
| `generated/`                             | spec 에서 나온 사람이 읽는 문서                                | **`/doc-gen` 이 생성** | **아니다** — 훅이 막는다            |

- **`spec/` 과 `design/` 을 가르는 기준은 하나다**: *ID 를 가진 항목의 목록으로 표현되면
  spec*, 그렇지 않으면 `design/`. `design/` 의 문서도 상위 spec ID 를 헤더에 명시해
  추적성 체인에 붙는다.
- **`research/` 는 비규범이다** — spec 도 `aidd-guide/` 도 여기를 직접 인용하지 않는다.
  순서는 **조사 → ADR → spec** 이며 조사를 인용하는 것은 `decisions/` 의 ADR 뿐이다
  ([산출물 지도 42행](../aidd-guide/artifact/INDEX.md)).
- **`generated/`는 `/doc-gen`이 현재 spec에서 다시 만든다.** **커밋하지 않는다** —
  `.gitignore` 대상이며 `documents/spec/` 에서 언제든 다시 만든다. **없으면 만들면 되고,
  고칠 것이 있으면 spec 을 고친다.**
  - `10-요구사항정의서/` (9) — **P0~P2 정본 여덟의 읽는 뷰**(지도 43행). 게이트 승인의 판단 대상이다
  - `20-아키텍처.md` · `22-권한매트릭스.md` · `31-기능목록.md` · `60-화면정의서.md` · `65-스케줄배치명세서/` · `80-운영런북.md` · `90-추적성매트릭스.md`
  - **아직 안 나오는 것 넷** — 유즈케이스 · ERD · API · 테스트케이스. **spec 이 비어 있어서다**

## spec/ 카탈로그

`S` = 직접 작성 · `단계` = 그 산출물이 완성되어야 하는 게이트

| 파일 | 단계 | 담는 것 | 상태 |
|---|---|---|---|
| [`00-charter/_index.yaml`](spec/00-charter/_index.yaml) | P0 | 문제 5 · 경제성 5 · **목표 5**(GOAL-) · **비목표 10**(NGOAL-) · 성공 기준 7 · 로드맵 4 | 이관 |
| [`01-actors/_index.yaml`](spec/01-actors/_index.yaml) | P0 | **액터 11**(ACT-) — 사람 5 · 외부 시스템 3 · 외부 조직 3 + 모듈 3 | 이관 |
| [`02-constraints/_index.yaml`](spec/02-constraints/_index.yaml) | P0 | **설계 원칙 10**(PRN-) · 기술 제약 11 · 배포 3 · **착수 시 확정 18**(CON-) · 개발 제약 2 | 이관 |
| [`03-glossary/_index.yaml`](spec/03-glossary/_index.yaml) | P1 | **용어 54**(TRM- · 대화·실행·정책·실행기반·처리중입력) · **프레임워크 어휘 24** · **헷갈리는 짝 10** · 턴 상태 6 · 대기 유형 3 · 회차 규칙 5 | 이관 |
| [`10-requirements/_index.yaml`](spec/10-requirements/_index.yaml) | P1 | **요구사항 84**(REQ-) — 질의 처리·턴 수명주기 38 · 보안·거버넌스 19 · 저장된 조회 7 · 메타데이터·지식 검토 8 · **유도 2** · system 기반 10 | 이관 |
| [`11-processes/_index.yaml`](spec/11-processes/_index.yaml) | P1 | 업무 프로세스 As-Is / To-Be | **비어 있다** |
| [`20-architecture/_index.yaml`](spec/20-architecture/_index.yaml) | P2 | 구성 · **논리 구성도**(노드 14 · 간선 16 · 불변식 4) · 모듈 경계 6 · 딥링크 · 데이터소스 · LLM 연동 · 신원 매핑 · 지식 검색 · **실행 점유**(ARCH-) | 이관 |
| [`21-nfr/_index.yaml`](spec/21-nfr/_index.yaml) | P2 | 비기능 (NFR-) — 성능·응답성·비용·안정성·보안·사용성/호환성과 측정·검증 기준 | 항목별 정본 참조 |
| [`22-authz/_index.yaml`](spec/22-authz/_index.yaml) | P2 | 그룹 판정 · 매핑 6 · 행 스코프 · **SQL 통제 4단계** · 주입 통제 · 기본 필터 · 책임 층(AUTHZ-) | 이관 |
| [`30-usecases/_index.yaml`](spec/30-usecases/_index.yaml) | P3 | 유즈케이스 (UC-) — 요구사항 연결·기본·대안·예외 흐름 | 작업흐름별 정본 참조 |
| [`31-features/_index.yaml`](spec/31-features/_index.yaml) | P3 | 기능 (FEAT-) — 모듈별 업무·공통 처리·배치/이벤트와 사용 관계 | 작업흐름별 정본 참조 |
| [`40-domain/_index.yaml`](spec/40-domain/_index.yaml) | P3 | 엔티티·속성·상태 전이 (네이밍 규약만 확정) | **비어 있다** |
| [`50-api/_index.yaml`](spec/50-api/_index.yaml) | P3 | Hub 내부 API (경로 규약만 확정) | **비어 있다** |
| [`51-interfaces/_index.yaml`](spec/51-interfaces/_index.yaml) | P3 | **외부 인터페이스 8**(IF-) — 나가는 5 · 들어오는 3 | 이관 |
| [`60-screens/_index.yaml`](spec/60-screens/_index.yaml) | P3 | 메뉴·화면 (SCR-) — 진입 유형·소유/참여 기능·공통 화면 호출·차수/범위 | 작업흐름별 정본 참조 |
| [`70-testcases/_index.yaml`](spec/70-testcases/_index.yaml) | P3~ | 테스트 케이스 | **비어 있다** |
| [`80-operations/_index.yaml`](spec/80-operations/_index.yaml) | P2~P6 | **운영 절차**(RUN-) — 구성·일상 점검·장애·백업/복구·모니터링·정기 작업·에스컬레이션 | 작성 중 |

### 상태가 뜻하는 것

- **`이관`** (`status: migrated`) — 재편성해 들어왔고 **사용자 승인을 받지 않았다.**
  각 파일의 `meta.status` 가 그 표시이며 `meta.baselined` 가 `null` 이다.
  **승인 전에는 어떤 항목도 "합의된 것" 으로 인용하지 않는다.**
- **`비어 있다`** (`status: empty`) — **지어내지 않았다.** 만들 단계에 도달하지 않았다. 파일 머리에 *왜 비어 있고 무엇에 막혀 있는지* 와 **채울 때의 형식 예시**가 있다.

## ID 체계 — 프로젝트의 것

| Prefix                              | 대상                       | 정의 위치                               |
| ----------------------------------- | ------------------------ | ----------------------------------- |
| `GOAL-` `NGOAL-`                    | 최종 목표 · 비목표              | `00-charter/_index.yaml`                   |
| `ACT-`                              | 액터                       | `01-actors/_index.yaml`                    |
| `PRN-`                              | 설계 원칙                    | `02-constraints/_index.yaml`               |
| `CON-`                              | 제약·착수 확정 항목              | 〃                                   |
| `TRM-`                              | 용어                       | `03-glossary/_index.yaml`                  |
| `REQ-`                              | 요구사항                     | `10-requirements/_index.yaml`              |
| `PRC-`                              | 업무 프로세스                  | `11-processes/_index.yaml`                 |
| `ARCH-`                             | 아키텍처 결정 사항               | `20-architecture/_index.yaml`              |
| `NFR-`                              | 비기능 목표치                  | `21-nfr/_index.yaml`                       |
| `AUTHZ-`                            | 권한 모델                    | `22-authz/_index.yaml`                     |
| `UC-`                               | 유즈케이스                    | `30-usecases/_index.yaml`                  |
| `FEAT-`                             | 기능                       | `31-features/_index.yaml`                  |
| `ENT-`                              | 엔티티                      | `40-domain/_index.yaml`                    |
| `API-`                              | Hub 내부 API               | `50-api/_index.yaml`                       |
| `IF-`                               | 외부 인터페이스                 | `51-interfaces/_index.yaml`                |
| `SCR-`                              | 화면                       | `60-screens/_index.yaml`                   |
| `TC-`                               | 테스트 케이스                  | `70-testcases/_index.yaml`                 |
| `RUN-`                              | 운영 런북 절차                 | `80-operations/_index.yaml`                |
| `DLV-` `SITE-`                      | 제출 구성 · 제출 사이트          | `delivery.yaml`                     |
| `DES-`                              | 상세 설계 문서                 | `design/`                           |
| `ADR-` `ASM-` `CR-` `DEF-` `SLICE-` | 결정 · 가정 · 변경 · 결함 · 슬라이스 | `decisions/` · `project.state.yaml` |

- 체인과 검사 규칙(T1~T13)은 [`aidd-guide/process/05-traceability.md`](../aidd-guide/process/05-traceability.md).
- **`LLM-` 접두는 여기 것이 아니다** — `aidd-guide/` 의 문서 ID 다. 두 체계는 섞이지 않는다.

## 검증

```bash
node .claude/hooks/trace-check.mjs --all
```

**지금 이 명령은 오류를 낸다** — FEAT 85건에 TC 가 없고(T5) NFR 19건에 TC 가 없다(T7).
**그것이 사실이며 G3 를 통과할 수 없다는 뜻이다.** `project.state.yaml` 의 `counter_note`
가 같은 말을 한다.
