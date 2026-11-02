---
id: LLM-ART-001
title: "spec 스키마 — 파일과 작성 단계"
category: artifact
type: operation
created: 2026-09-04
updated: 2026-10-29
status: active
sources: []
tier: 1
tags: [spec, ssot, yaml]
---

# documents/spec/ 스키마 안내

`documents/spec/` 은 이 프로젝트의 **유일한 진실(SSOT)** 이다. `documents/generated/` 는 여기서 생성된 뷰다.

## 논리 문서와 작성 단계

각 행은 파일 하나가 아니라 **논리 spec 문서**다. 정식 저장 형태는
`documents/spec/{이름}/_index.yaml`과 번호 조각(`NNN-이름.yaml`)으로 구성한 spec-set이며,
호출자·링크·생성기는 `_index.yaml` 또는 논리 폴더를 가리킨다. 이관 중에만 같은 이름의
단일 YAML 하나를 읽을 수 있고, 단일 YAML과 spec-set은 함께 두지 않는다.

| 논리 문서 (`_index.yaml`) | 단계 | 내용 | 형식 참조 |
|---|---|---|---|
| `00-charter/_index.yaml` | P0 | 목적·문제·성공기준·범위·트랙 | `aidd-guide/phase/P0-inception.md` §3 |
| `01-actors/_index.yaml` | P0 | 액터(사용자/외부시스템) | 〃 |
| `02-constraints/_index.yaml` | P0 | 제약사항 | 〃 |
| `03-glossary/_index.yaml` | P1 | 용어사전 (유비쿼터스 언어) | `aidd-guide/phase/P1-discovery.md` §2 |
| `10-requirements/_index.yaml` | P1 | 요구사항 FR/NFR + 정련 카드 | 〃 §4 · §5 |
| `11-processes/_index.yaml` | P1 | 업무 프로세스 To-Be | 〃 §3 |
| `20-architecture/_index.yaml` | P2 | 기술스택·인프라·사이징 | `aidd-guide/phase/P2-architecture.md` §4 |
| `21-nfr/_index.yaml` | P2 | 비기능 목표치·측정방법 | 〃 §3 |
| `22-authz/_index.yaml` | P2 | 권한 모델·역할 | 〃 §5 |
| `30-usecases/_index.yaml` | P3 | 유즈케이스 (기본/대안/예외 흐름) | `aidd-guide/phase/P3-design.md` §2 |
| `31-features/_index.yaml` | P3 | 화면·공유 업무·배치·이벤트 기능의 목록과 처리 계약 | 〃 §3 |
| `40-domain/_index.yaml` | P3 | 엔티티·속성·인덱스·상태전이 | 〃 §4 |
| `50-api/_index.yaml` | P3 | 독립 API 목록·호출 계약·소스 메서드 연결 | 〃 §5 |
| `51-interfaces/_index.yaml` | P3 | 외부 시스템 연동 | 〃 |
| `60-screens/_index.yaml` | P3 | 메뉴·화면 목록, 공통 화면과 호출 관계 | 〃 §6 |
| `65-batches/_index.yaml` | P3~P6 | 관리형 배치별 실행 명세·구현/운영 연결 | 이 문서의 스케줄 배치 명세 절 |
| `70-testcases/_index.yaml` | P3~ | 테스트 케이스 | `aidd-guide/quality/test-design.md` §1 |
| `80-operations/_index.yaml` | P2~P6 | 운영 절차·검증·복구·에스컬레이션 | `aidd-guide/artifact/template/05-runbook.md` |

## spec-set 공통 규칙

1. `_index.yaml`은 `meta`와 `spec_set: { version: 1, parts: [...] }`만 가진다. 본문 값은
   번호 조각에 둔다.
2. `parts`는 실행·문서 생성·검사가 읽는 유일한 순서다. 모든 조각은 목록에 정확히 한 번만
   나오며, 목록 밖 YAML은 허용하지 않는다.
3. 조각은 최상위 키를 중복 정의하지 않는다. 배열은 선언된 순서로 이어 붙이고, 객체는
   충돌 없이 병합한다.
4. 설명에 의미가 있던 YAML 주석은 삭제하지 않고 **그 설명이 걸리는 항목의** 명시적
   `note`·`rationale`로 승격한다. 주석은 검증·생성의 입력이 아니다.
5. `meta`는 **문서 수준의 사실만** 갖는다 — 상태·베이스라인·채번 규칙과 문서 전체에 걸리는
   업무 규약이다. 이관 메모·편집 이력·줄번호 같은 작업 흔적은 두지 않는다. `meta`의 값은
   고객 제출 문서로 나가며, 나가는 키는 `doc-gen`의 `META_PUBLIC`이 허용목록으로 정한다.
6. **조각은 업무 영역 하나이고, 항목 12건 이하 또는 약 800줄 이하로 둔다.** 넘으면 영역을 둘로
   나눈다 — 요구사항 정련 카드처럼 항목마다 칸이 많은 문서는 금방 수천 줄이 되어 읽히지 않는다.
   조각 첫 줄에 `# 영역: <이름>` 을 두면 생성기가 그 이름으로 생성 파일을 하나씩 만든다(`10-requirements`).
   ID 범위나 영역 이름을 생성기에 다시 적지 않는다. 나눌 때는 병합 결과가 나누기 전과 같은지 대조한다.

## 공통 규칙

1. 모든 항목에 `id:` 가 있다. ID 체계 → [05-traceability.md](../process/05-traceability.md) §2
2. ID는 재사용하지 않는다. 삭제해도 번호를 비워 둔다.
3. 상위 산출물을 참조하는 필드가 반드시 있다 (`reqs:`, `usecases:`, `feature:` 등).
4. G3 승인 후 베이스라인 동결. 각 문서의 `meta.baselined`에 날짜를 적는다.
5. 베이스라인 이후 변경은 `/change-request`.
6. **요구사항 사실(업무 규칙·산식·판정 조건)의 정본은 REQ 의 정련 카드다.** `FEAT`·`SCR` 은 기능 범위와 표시를 적고 규칙은 그 REQ 를 가리킨다 — 두 곳에 적으면 한쪽만 고쳐진다.

## 설계 문서의 책임과 연결

**기능은 메뉴의 하위 항목이 아니다.** 요구사항을 실현하는 기능은 화면·스케줄·이벤트·내부
호출로 실행될 수 있다. 화면이나 HTTP API가 없는 기능에 가짜 연결을 만들지 않는다.

| 문서 | 정본이 정의하는 사실 | 연결·파생 |
|---|---|---|
| 요구사항 | 업무 규칙·판정·제약·수용 조건 | UC·FEAT는 REQ를 참조 |
| 유즈케이스 | 주체·전제·정상/대안/예외 업무 흐름 | 여러 기능·화면에 걸칠 수 있음 |
| 메뉴 목록 | 계층·진입 화면·접근 권한 | 60-screens의 메뉴 항목으로 조회; 구현 후 실제 메뉴 트리와 대조 |
| 기능 목록·명세 | 입력·처리·출력·실패·사용 기능·데이터·실행 계기 | 31-features에서 기능별 읽는 문서를 생성 |
| 화면 목록·명세 | 구성·표시·입력·조작·호출 계약 | 목록/연결은 60-screens, 상세는 screen의 requirements.md |
| 공통 화면 명세 | 여러 화면이 공유하는 모달·선택창의 공통 계약 | 같은 SCR 체계; 호출 화면은 전달값·반환값 매핑만 정의 |
| API 목록·명세 | 메서드+경로·입출력·오류·인가·소유 기능·구현 메서드 | 50-api가 독립 정본; 화면 문서는 API 식별자를 참조 |
| 데이터 모델·테이블 목록/명세서 | 개념·소유·상태·물리 계약, Liquibase 구현 대조 | ENT와 소스 연결로 테이블별 명세 생성; [동기화 서식](data-model.md) |
| 외부 연동 | 외부 송수신 계약 | IF를 사용 기능과 연결 |
| 스케줄 배치 명세 | JOB별 실행 대상·설정/파라미터·기동·중복·결과·재처리 경계 | 65-batches에서 목록/개별 명세 생성. 업무 판정은 REQ, 기능 처리는 FEAT, 운영 절차는 RUN 참조 |
| 테스트 명세 | 검증 조건·기대 결과·검증 수단 | TC와 테스트 코드·화면 사례 연결 |
| 추적성·변경 영향 | 위 연결에서 계산한 검토 범위 | 생성/조회만 하며 별도 현황 원장을 만들지 않음 |

사용자 매뉴얼과 운영 런북도 현행화 대상이다.
배치의 실행·실패·재처리·복구 절차는 80-operations에 두고 기능 계약을 참조한다.
API 목록의 목적은 변경 추적이며 Swagger UI는 필수가 아니다.

### 스케줄 배치 명세

`65-batches`의 `common`은 공통 실행 규약의 연결과 현행 지원 한계를, `jobs[]`는 `JOB-###` 단위의 관리형 BatchJob 실행 계약을 갖는다. JOB는 설계 식별자이고 실제 스케줄 등록의 BATCH_ID·구현 beanId와 구분한다. 내부 하트비트/청소 타이머는 관리형 JOB로 오등록하지 않는다.

- 필수: `id`·`title`·`module`·`implementation`(implemented/planned), 소유 `feature`·`usecase`·`reqs`, `scope`·`schedule`·`parameters`·`permissions`·`processing`·`concurrency`·`failure`·`cancellation`·`retry`·`outputs`·`observability`·`testcases`·`sources`.
- `schedule`은 시드 기본값과 운영 현재값을 구분한다. 주기·등록 키/빈이 미정인 계획 배치는 null과 `pending` 근거를 둔다. 실제 설정을 읽지 않고 활성/성공을 선언하지 않는다.
- `sources`는 구현/시험 경로, `screens`는 설정·결과 화면, `runbooks`는 운영 절차를 연결한다. 구현된 BatchJob은 반드시 명세가 있어야 한다. 예정 배치의 시드를 임의로 만들지 않는다.
- 새 배치는 소유자가 해당 조각에 등록하고 JOB 연결·필수 항목·실구현 누락 검사를 통과시킨 뒤 문서/HTML을 재생성한다. 업무 규칙·파기/복구 절차를 복사하지 않고 REQ·FEAT·RUN 정본을 참조한다.

### 핵심 처리 시퀀스

`30-usecases`의 해당 UC에 `sequence_design`을 두고 `doc-gen 시퀀스`로
`documents/generated/33-시퀀스/UC-###.md`를 만든다. 파생 Mermaid나 설명을 손으로 고치지 않는다.
기존 산문 상세 설계 문서를 같은 사실의 정본으로 복제하지 않는다.

- 공통 메타: `contracts_from`(상세 계약 근거)·`scope`·선택 `shared_sequences`·`pending`.
- `diagrams[]`: UC 내부에서 유일한 `key`(영문 소문자/숫자/하이픈)·`title`·`mermaid`·
  `transaction_boundary`·`reading_order`·관련 `apis`/`testcases`와 선택 `counterexamples`/`pending`.
- 그림은 `sequenceDiagram`·`alt` 예외 분기·트랜잭션 경계를 갖는다. 읽기만 하는 흐름은
  쓰기 트랜잭션이 없음을 명시한다. 공통 흐름은 다른 UC의 그림을 링크하고 승인/구현 상태를 올리지 않는다.
- 생성기는 선언된 메타·설명을 모두 출력하고 키 중복·필수 구조 누락을 거부한다.
  `doc-gen --check`는 그림·설명 변경, 누락, 폐기된 시퀀스 잔존을 원본과 대조한다.
- Mermaid `Note` 설명의 세미콜론은 구문 구분자로 해석되므로 문장 구분 기호로 바꾸거나 `#59;`로 이스케이프한다.

HTML 목차는 문서 종류 → 모듈 순으로 자동 구성한다. 단일 모듈의 설계 Markdown은 앞부분
메타에 `module`을 기록하고, 모듈별 파생 문서는 생성기가 정본에서 이 값을 출력한다. 집계
문서는 전체·통합으로 둔다. 분류의 정본과 확장 절차는 [사이트 목차 규약](site-kit/README.md#설계-문서-목차)을 따른다.

### 관계 필드 — 소유와 사용을 구분한다

| 항목 | 필드와 의미 |
|---|---|
| FEAT | `reqs`·`usecases`·`nfrs`: 근거, `uses_features`: 호출/사용하는 공통 업무 기능 ID 배열 |
| FEAT | `apis`·`entities`·`interfaces`: 사용하는 API·ENT·IF ID 배열; 배치도 동일 구조 |
| FEAT | `trigger`: 화면/스케줄/이벤트/내부 호출의 계기; 배치는 중복 실행·재시도·재처리 계약 명시 |
| SCR | `feature`: **소유 기능 하나**, `features`: 화면에서 수행하는 다른 기능 ID 배열 |
| SCR | `uses_screens`: 호출하는 공통/내부 SCR ID 배열; 역방향 사용처는 계산 |
| SCR | `entry`: `menu`/`standalone`/`embedded`; 메뉴는 `menu_code`·`menu_parent`·`route`를 설계하고 구현 트리와 대조 |
| SCR | `current_doc`·`target_doc`: 실제/목표 화면 요건서; 전용 탭·모달은 부모 문서의 절로 관리 |
| API | `feature`: 소유 기능, `method`+`path`: 호출 식별, `source_contract`: `sources/.../Controller.java#method` |
| API | `implementation`: `planned`/`implemented`; 요청·응답·오류·권한 계약과 연결 기능을 유지 |
| FEAT/SCR/API | `sources`: 구현 파일 또는 끝이 `/`인 폴더, 필요하면 `#메서드`; `docs`: 추가로 대조할 정본 경로 |
| ENT | `name`·`term`·`domain`·`design_stage`: 논리명, TRM 연결, 도출한 업무 영역, logical/physical. logical 초안은 물리명 없이 생성하며 G3 전 물리 설계를 완료 |
| ENT | `module`·`implementation`·`table`·`physical`·`sources`: 소유 모듈, planned/implemented, 물리명/계약, Liquibase 연결. 논리 속성의 `physical_name`으로 컬럼 연결 |
| TC | `feature`: 소유 기능, 필요하면 `features`·`apis`·`screens`; `screen_cases`: 기존 화면 사례의 `documents/screen/.../requirements.md#사례번호` 배열 |

신규 화면 연결은 SCR의 `feature`/`features`에 정의한다. 기존 FEAT의 `screen`/`screens`는
호환 입력으로 합집합을 읽으며 조용히 버리지 않는다. 이를 새 정본으로 중복 작성하지 않는다.
`feature`는 소유자이고 `features`는 사용 관계다. 사용처가 늘어도 작업흐름·승인 소유권을 옮기지 않는다.
새 관계는 식별자 배열로 기록한다. 산문 속 이름만으로 공통 소비자를 추정하지 않는다.

공통 화면은 `_standalone/{소유모듈}/{화면이름}/requirements.md`에 한 번 정의하고 목업을 생성한다.
입력 조건·선택 결과를 어느 칸에 반영하는지는 호출 화면이 정의한다. 공유가 무권한 접근을 뜻하지
않으며 호출 메뉴 권한·사용자/데이터 범위는 API 계약에서 검사한다. 단순 확인창·모달 틀은
기술 공통물(CMP) 규약으로 관리하고 업무용 공통 화면·기능과 구분한다.

### 소스·테스트·현행화

1. 구현 전에는 목표 계약을 명세한다. 구현 후 소스는 현재 동작의 근거이며, 승인 명세와 다른
   부분은 의도한 변경인지 결함인지 판정한다. 차이를 숨기려고 어느 한쪽을 자동 덮어쓰지 않는다.
2. API 변경은 **최종 메서드+경로**를 컨트롤러와 대조한다. 클래스 경로, 메서드 경로,
   public/common/service 및 요청·응답 타입을 포함한다. 현재 추적 훅의 태그 검사는 API 전수
   일치 검사가 아니므로 소스에만 있는 API·사라진 메서드·planned 계약을 구분하는 대조를 생략하지 않는다.
3. 화면 없는 기능도 `sources`와 테스트를 연결한다. 공통 기술 계층에 가짜 FEAT를 붙이지 않고
   실제 소비 기능의 `sources`에서 사용 공통 파일/폴더를 연결하거나 가이드 `sources`로 대조한다.
4. 신규 화면 테스트는 전역 TC를 참조한다. 기존 지역 사례번호는 보존하고 TC의 `screen_cases`로
   연결한다. 화면은 조작·표시 사례를, TC는 검증 대상·수단을 정의하며 같은 기대값을 복제하지 않는다.
5. 변경 전과 변경 후 식별자/소스의 영향을 조회한다. 삭제·이름 변경은 이전 연결도 조회한다.
   공통 소비자·관련 정본·테스트·매뉴얼·운영 절차를 대조하고 파생물을 재생성한 뒤 같은 변경으로 검증한다.
6. 영향 조회는 **선언된 관계의 검토 범위**다. 연결이 없으면 영향 없음이 아니라 확인 필요다.
   동적 호출·미등록 소비자·업무 의미는 사람/AI가 확인한다. G3 이후 의미 변경은 change-request를 따른다.

기존 산출물의 일괄 재채번·소유권 변경은 하지 않는다. 해당 영역 상세 설계/변경에서 연결을 보완하고
G3 검토 시 그 범위의 소스·API 전수 대조와 테스트 연결을 확인한다.

## 검증

```bash
node .claude/hooks/trace-check.mjs --all
```
또는 `/trace`.
