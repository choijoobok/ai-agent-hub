---
id: LLM-ART-DATA
title: "데이터 모델 · Liquibase · 테이블 명세서 동기화"
category: artifact
type: operation
created: 2026-10-28
updated: 2026-10-28
status: active
sources: []
tier: 2
tags: [data, schema, liquibase, trace]
---

# 데이터 모델 · Liquibase · 테이블 명세서

**물리 구조를 바꾸는 작업은 모델과 Liquibase를 같은 변경에서 현행화하고 문서를 재생성한다.**
테이블 명세서를 별도 수기 정본으로 만들지 않는다. 소유자는 해당 ENT의 모듈이다.

## 역할과 정본

| 대상 | 역할 | 수정 위치 |
|---|---|---|
| 도메인·데이터 모델 | 개념·소유·업무 규칙·상태·논리 관계·검토한 물리 계약 | `documents/spec/40-domain/` |
| Liquibase | 실행 가능한 스키마 변경 이력, 구현된 물리 구조의 근거 | `sources/backend/bootstrap/src/main/resources/db/changelog/` |
| ERD | 데이터 모델의 관계 개요 | `documents/generated/40-ERD.md` — 생성 |
| 테이블 목록·명세서 | 테이블별 컬럼·자료형/길이·NULL·기본값·키·제약·인덱스·변경 근거 | `documents/generated/41-테이블명세서/` — 생성 |
| HTML | 위 산출물의 종류 → 모듈별 열람 | `build/deliverables/design/index.html` — 생성 |

`physical`은 검토한 설계 계약, Liquibase는 구현이다. 서로 다른 역할의 계약을 검사하며 한쪽으로
다른 쪽을 자동 덮어쓰지 않는다. 구현 후 차이가 생기면 소스를 현재 사실로 보고하되 의도한 변경인지
결함인지 판정한다. 승인된 의미를 바꾸면 `change-request` 절차를 따른다.

## ENT 기록 형식

- `id`·`name`·`module`·`features`: 안정된 식별자, 한글 논리명, 쓰기 소유 모듈, 소비 기능.
- `design_stage`: `logical`은 물리명 없이 논리 모델을 검토하는 단계, `physical`은 물리 계약 설계 단계다.
  기존 구현은 생략 시 physical로 읽는다. 신규 logical은 `table`·`physical`을 비워 두고 임시 물리명을 만들지 않는다.
- `term`·`domain`: 기존 용어사전의 TRM(용어 식별자) 연결과 도출한 업무 영역. 업무 영역과 소유 모듈은 다른 축이다.
- `table`: 물리 설계에서 정하는 대문자 테이블명. ENT(엔티티 식별자)는 논리→물리 전환에도 유지한다.
- `implementation`: `planned`는 미구현 설계, `implemented`는 활성 Liquibase에 존재하는 구조.
- `attributes`·`relations`·`rules`·`state_machine`: 업무 속성·논리 관계·불변식. 논리 관계가
  있다고 DB 외래키가 존재하는 것은 아니다. 기존 `columns` 이름 목록은 호환하며 신규 문서에는 중복하지 않는다.
  논리 속성의 `name`은 한글로 유지하고 물리 설계에서 `physical_name`을 연결한다. 기존 영문 name 입력은 호환한다.
- `revision_column`: 관리 마스터의 업무 버전 열. 생략하면 `REVISION`이며 기존 `MANAGEMENT_VERSION`처럼 이름이 다르면 명시한다. 선언한 열의 존재와 이력 동형 복사를 검사하며 자식의 독립 버전을 허용하는 설정은 아니다.
- `physical.attributes`: 전체 컬럼의 순서·`name`·정확한 `type`·`nullable`·`default`(없으면 null).
  실제 DB 주석은 선택 `comment`, 업무 설명은 논리 속성의 `description`에 둔다.
- `physical.constraints`: 이름과 전체 `definition`. 기본키·유일성·외래키·CHECK를 포함하고
  복합 키 순서·참조 동작·지연 조건을 생략하지 않는다.
- `physical.indexes`: 이름·`unique`·`method`·전체 `definition`. 제약이 생성하는 인덱스와 구분한다.
- `sources`: 테이블 생성/변경 파일과 활성 include 경로. 변경 파일 → ENT → FEAT → 관련 문서를 추적한다.
- `planned_changes`: 기존 구현의 다음 변경을 설계할 때 CR/요구·예정 계약·적용 조건을 기록한다.
  구현 전의 예정 컬럼을 현재 `physical`에 섞거나 `implemented` 전체를 `planned`로 낮추지 않는다.

신규 `planned` 테이블의 `physical`이 미완성이면 생성물에도 미완성으로 표시한다. G3에서는
해당 범위의 필수 물리 설계와 제약을 완성해야 한다. G3 전에 제품 changeset을 만들어 소스 게이트를
우회하지 않는다. 물리 이름과 키는 기존 명명·식별자 표준을 따른다.

## 논리 설계에서 물리명으로 연결하는 순서

1. 업무 기능에서 개념·책임·생명주기를 도출한다. 논리 엔티티명·속성명·관계·식별 기준·불변식을 먼저 기록한다.
2. `03-glossary.terms`의 `term`·`definition`·`key`를 대조한다. 기존 용어를 우선 연결하고 없는 개념/동음이의만 보완한다.
3. 엔티티 묶음의 업무 영역을 정한다. 사전의 `group`은 용어를 읽는 분류이며 도메인 경계나 물리 접두어를 자동 확정하지 않는다.
   프로젝트의 실제 영역 목록은 `40-domain.conventions.business_domains`에 이름·책임·관련 용어로 한 번 정의한다.
4. 물리 설계에서 용어 토큰·분류어를 조합해 이름, 타입/길이, NULL·키·제약·인덱스를 정하고 충돌·길이를 대조한다.
5. G3 전에 물리 계약을 검토·확정한다. 구현 단계에서 Liquibase와 소스를 작성하고 같은 모델에서 명세/HTML을 재생성한다.

**기본 틀은 지금, 실제 영역·약어·물리명은 도메인 도출 뒤에 확정한다.** 구현 직전까지 명명 규칙 전체를 미루지는 않는다.

### 명명 구조

| 층 | 원천과 규칙 |
|---|---|
| 논리 용어 | `03-glossary.terms`: 한글명·뜻·영문 key. 프레임워크 해설 용어는 명명의 원천으로 쓰지 않는다 |
| 업무 영역 | `conventions.business_domains`: 도출된 책임의 분류. 모듈 및 화면 메뉴와 동일하다고 가정하지 않는다 |
| 영문 토큰 | 기본은 key를 대문자 스네이크로 변환한다. `dataSource`→`DATA_SOURCE`, `knowledgeRevision`→`KNOWLEDGE_REVISION` |
| 승인한 약어 | 길이/충돌 때문에 필요할 때만 해당 TRM에 `db_token`·`db_token_reason`을 추가한다. 기본 key·정의는 바꾸지 않는다 |
| 테이블 | Hub 기존 규칙 `AI_<MODULE>_<DETAIL>`·원본 30자 제한. 관리 변경 이력만 원본명 전체 뒤 `_HIST`를 붙이는 예외(최대 35자)이며 원본명을 줄여 다시 명명하지 않는다 |
| 컬럼 | 필요한 대상/역할 + 속성·분류어. 한글 논리 속성과 `physical_name`으로 연결한다. 기존 SYS 컬럼을 일괄 개명하지 않는다 |
| 참조 | 같은 개념의 참조는 원래 식별 용어를 유지하고 다중 역할이면 역할 수식어를 붙인다. 이름이 아니라 안정된 키로 참조한다 |

업무 영역마다 임의의 세 글자 코드를 만들거나 테이블명에 영역 접두어를 일괄 추가하지 않는다.
같은 모듈 안에서 의미 충돌이 있을 때 영역 수식어의 필요성을 먼저 판단하고 사전에 등록한다.
일반 원본이 30자를 넘으면 자동 절단/숫자를 붙이지 않는다. `_HIST` 예외 외에는 약어의 의미와 충돌을 검토한다. 이력의 `HIST_ID` PK·`HISTORY_NO` 연결·세트 공통 `HIST_DTM`은 [공통 패턴](screen/overlay/history-pattern.md)을 따른다.
스키마 표기는 대문자 스네이크이며 PostgreSQL의 비인용 소문자 저장과 구별한다. 대소문자 보존용 따옴표를 새로 도입하지 않는다.

기존 공통 컬럼에서 쓰는 분류어는 의미가 같을 때 재사용한다: `ID` 식별자, `CD` 코드, `NM` 이름,
`YN` Y/N 여부, `CNT` 건수, `SEQ` 순번, `DT` 날짜, `DTM` 일시. `YN`을 임의의 boolean 저장 규칙으로
해석하지 않으며 타입과 허용 값은 물리 계약에서 정한다. 새 분류어·단위·약어는 실제 속성을 도출할 때 정의한다.
모든 컬럼에 분류어를 기계적으로 덧붙이지 않는다. 감사 컬럼·식별자 생성·시각 타입은 기존 표준을 우선한다.

## 변경 절차

1. ENT/FEAT와 `trace-check --source <경로>`로 요구사항·유즈케이스·API·화면·테스트 영향을 확인한다.
2. 설계 단계는 `planned` 또는 `planned_changes`를 작성한다. 구현 단계에서는 승인된 계약으로
   `physical`을 갱신하고 **새 Liquibase changeset**을 추가한다. 적재된 변경 파일을 수정하지 않는다.
3. 새 파일을 실제 실행하는 master/모듈 changelog에 include하고 ENT `sources`도 갱신한다.
   파일만 추가하고 include하지 않은 경우는 구현된 것으로 판정하지 않는다.
4. 마이그레이션 적용·기존 데이터 전환·롤백/복구 시험을 수행한다. 신규 테이블은 구현 시
   `implemented`로 바꾸고 완료한 `planned_changes`를 현재 계약에 반영한다.
5. 아래 검사와 문서 생성을 수행한다. 설계·Liquibase·관련 코드/테스트를 같은 변경에 담는다.
   파생 문서의 커밋 여부는 저장소 생성물 규약을 따른다.

```bash
node aidd-guide/harness/runtime/domain-schema.mjs
node .claude/skills/doc-gen/gen.mjs
node .claude/skills/doc-gen/gen.mjs --check
node .claude/skills/deliverable-site/build.mjs all
node .claude/skills/deliverable-site/check.mjs
```

## 생성·검사 경계

활성 루트와 resources 경로는 `conventions.liquibase`가 정의한다. 폴더 전체를 검색하여 과거
이관용 파일을 합치지 않는다. 기술 기반 테이블은 같은 설정의 `technical_tables`에 모듈·이유를
명시하며 가짜 업무 ENT를 만들지 않는다. 미분류 신규 테이블은 오류다.

생성 전 전체 대조에서 테이블/컬럼 추가·누락, 타입·NULL·기본값, 키·제약·인덱스, 소스 연결의
차이를 찾는다. 불일치나 미지원 구문이 있으면 문서를 쓰기 전에 실패한다. `gen --check`는 명세서의
실제 내용도 다시 계산해 낡은 파일을 찾는다. 차이를 검사에서 제외해 초록으로 만들지 않는다.

현재 판독기는 제품 PostgreSQL의 무조건 적용 경로와 SQL CREATE TABLE·ADD CONSTRAINT·CREATE INDEX·
ADD COLUMN·일부 ALTER COLUMN·COMMENT, YAML의 알려진 데이터 변경 및 `sql`을 지원한다.
DROP/RENAME·기타 스키마 구문·조건부 context/dbms/precondition·includeAll 등은 조용히 무시하지 않고
실패한다. 해당 변경을 처음 도입할 때 판독기와 회귀 사례를 함께 확장한다. rollback은 순방향에 합산하지 않는다.

이 검사는 **소스 기준 예상 스키마**를 비교한다. 운영 DB의 적용 여부·Liquibase checksum·수동 변경을
검증했다고 보고하지 않는다. 실제 마이그레이션 시험은 구현/통합 검증에서 별도로 수행한다.

## 생성이 보류됐을 때의 후속 작업

**보류는 수정할 책임을 사용자에게 넘기거나 작업을 종료한다는 뜻이 아니다.** 잘못된 최신본을 쓰지 않고
원본을 보완하는 분기다. AI는 오류 항목·보완 파일·영향·담당·재실행 순서를 설명하고 가능한 작업을 이어간다.

| 원인 | 먼저 보완할 것 | 담당/사용자 행동 |
|---|---|---|
| 원본은 일치하지만 명세서만 낡음 | doc-gen 재생성 → 검사 → HTML 조립 | AI가 실행, 사용자 판단 불필요 |
| 컬럼·키·제약 불일치 | 해당 ENT 조각과 오류의 Liquibase 파일에서 의도/현재 사실 대조 | 합의된 누락은 AI 보완. 업무 계약 변경이면 선택지·영향을 제시 |
| include·소스 연결 누락 | 모듈/master changelog와 ENT sources | 구현 승인 범위에서 AI 보완 후 재검사 |
| 미지원 SQL/조건부 구조 | 판독기와 회귀 검증 | AI 보완. 실제 SQL을 검사 통과 목적으로 변형하거나 건너뛰지 않음 |
| 명명 충돌·의미 변경·파괴적 변경 | 관련 용어/모델과 결정 근거 | AI가 구체적 권장안·영향·되돌리기 비용을 제시하고 사용자 판정 요청 |
| 논리 설계의 물리명 미정 | 보완 오류가 아님. logical 상태로 논리 명세 생성 | 사용자에게 물리명 입력을 강요하지 않음. G3 전 물리 설계로 진행 |

오류 출력은 대상 식별자, 모델 조각, 연결된 소스와 후속 명령을 포함한다. 별도 오류 현황 문서를 만들지 않는다.
사용자는 필요하면 “표시한 불일치를 보완하고 HTML을 다시 생성해줘”라고 요청하면 된다. 생성 작업을 맡은 AI는
그 요청을 다시 기다리지 않고 보완·재생성·검증까지 수행한다. 사용자 판정이 필요한 부분만 분리해 질문한다.
기존 HTML은 보존하되 현행 원본을 반영한 검증본으로 제출하지 않는다. 완료 보고에는 재생성 여부와 남은 판단을 적는다.
