---
name: doc-gen
description: documents/spec/ 의 논리 YAML 문서 세트에서 사람이 읽는 산출물 문서를 생성한다. 요건정의서, 유즈케이스 명세, ERD, API 문서, 화면정의서, 권한 매트릭스, 테스트 케이스, 사용자 매뉴얼 등을 documents/generated/ 아래에 만든다.
---

<!-- generated_from: aidd-guide/harness/skill-src/doc-gen.md; do not edit -->

# 문서 생성 (spec → docs)

인자: 없음(전체) / `아키텍처` `기능목록` `화면` `권한` `요건정의서` `유즈케이스` `ERD` `테이블명세서` `DDL` `API`
`시퀀스` `테스트` `매뉴얼` `추적성`

**목록의 정본은 아래 「생성 대상」 표다.** 이 줄은 그 표의 첫 칸을 줄인 것이며,
**표에 행을 더하면 이 줄도 함께 고친다** — 갈리면 표가 이긴다.

## 먼저 스크립트를 돌린다 — 손으로 쓰지 않는다

```bash
node .claude/skills/doc-gen/gen.mjs                    # 스크립트가 아는 대상 전부
node .claude/skills/doc-gen/gen.mjs 요구사항정의서 화면정의서
node .claude/skills/doc-gen/gen.mjs --check            # 생성물이 spec 을 다 담았나
```

**왜 스크립트인가** — 파생물의 값은 *정본에서 언제든 **같은 것**이 다시 나온다* 는 데
있다. 산문 지시로만 두면 부를 때마다 결과가 흔들리고 그 값이 사라진다.

- **`gen.mjs` 가 만드는 대상**은 아래 표에서 **`gen`** 으로 표시된 것이다. 그 대상은
  **손으로 쓰지 않는다** — 서식을 바꿀 일이 있으면 `gen.mjs` 를 고친다.
- **`prose` 로 표시된 것만 이 문서의 지시를 읽고 AI 가 쓴다.** 쓰고 나면
  **`gen.mjs` 에 대상으로 옮긴다** — 두 번째 생성부터 흔들리지 않게 하기 위함이다.
- **의존성을 갖지 않는다.** YAML 은 공통 `spec-set` 로더와 `hooks/lib.mjs`의 `parseYaml`로 읽는다. 새 런타임을
  들이지 않는 것이 폐쇄망 전제다 — 이 저장소의 도구는 전부 Node 다.
- 호출자는 `31-features` 같은 **논리 이름**만 사용한다. 로더는 이관 중의 단일
  `31-features.yaml`과 정식 폴더 `31-features/_index.yaml` 중 하나를 읽는다.
- **`--check` 가 완전성을 센다** — spec ID · 스칼라 값 · 상대 링크 셋을 대조해
  *생성물에서 조용히 사라진 것* 을 찾는다. **P3 spec 이 채워지면 빨개지는 것이 정상이다**
  (새 대상이 필요하다는 신호이며, 초록으로 만들려고 검사를 좁히지 않는다).

## 원칙
- 임시 스크립트·로그·캡처·비교 데이터는 [빌드 안내](../../../aidd-guide/artifact/site-kit/BUILD-README.txt)의 작업별 경로에 둔다. `build/` 최상위에 쌓지 않는다.
- 데이터 모델·Liquibase·테이블 명세서는 [동기화 규약](../../../aidd-guide/artifact/data-model.md)을 따른다. 생성 전 `domain-schema.mjs`가 활성 스키마와 설계의 일치를 검사하며 불일치면 쓰기 전에 실패한다. 테이블 명세서는 독립 생성물이며 `--check`로 내용의 노후화도 검사한다.
- API 목록·상세 계약은 독립 산출물이며 API ID와 소유 기능·구현 메서드를 출력한다. Swagger UI는 필요하지 않다.
- 메뉴·공통/독립 화면을 구별하고 단/복수 기능·화면 연결을 합쳐 읽는다. 기능별 문서를 수기로 복제하지 않는다.
- **`documents/generated/` 는 파생물이다.** 항상 `documents/spec/` 에서 다시 만든다. 손으로 고친 내용은 덮어써진다.
- 생성 전 `documents/spec/` 의 스키마 유효성과 ID 참조 무결성을 먼저 확인한다.
- 모든 생성 문서에 프런트매터를 넣는다:
  ```yaml
  ---
  id: FEAT-012
  traces_to: [UC-004, REQ-011]
  status: baselined
  generated_from: documents/spec/31-features/
  last_sync: 2026-09-04
  ---
  <!-- 이 문서는 생성물입니다. 직접 수정하지 마세요. documents/spec/ 을 수정 후 /doc-gen 하세요. -->
  ```
- 다이어그램은 전부 **Mermaid**. 이미지 파일을 만들지 않는다.
  - **레이블의 괄호는 `#40;`·`#41;` 로 escape 한다.** 원통 노드 `[("…")]` 안의 `)` 가
    셰이프 종료로 읽힐 수 있고, 인용만으로 안전한지는 셰이프마다 다르다. spec 에는
    괄호가 든 레이블이 흔하다(`AI 전용 DB (PostgreSQL)`) — 여기서 escape 하지 않으면
    **그림이 통째로 렌더되지 않고, 생성물이라 아무도 고치지 않는다.**
  - 아키텍처 그림의 노드·간선 원본은 논리 문서 `20-architecture`의 `topology.nodes`·`edges`다.
    **`edge_invariants` 를 함께 싣는다** — 그림만으로는 낡음을 판정할 수 없다.
- **기능별로 파일을 쪼갠다.** 큰 단일 문서는 다음 세션에 AI가 통째로 못 읽는다.
- 각 디렉터리에 `_index.md` (ID + 한 줄 요약)를 만든다. AI는 인덱스를 먼저 읽는다.

### 고객 제출물의 `meta` 경계

- 생성기는 `...d.meta`처럼 문서 `meta` 전체를 고객 문서에 펼쳐 쓰지 않는다. 이관 메모·편집
  흔적·내부 운영 정보가 그대로 제출물에 실릴 수 있다.
- 공통 메타를 출력해야 하면 `gen.mjs`의 `publicMeta(논리이름, d.meta)`를 사용한다. 새 논리
  문서는 `META_PUBLIC` 또는 `META_INTERNAL`에 **모든 meta 키의 공개·내부 분류를 먼저** 등록한다.
  분류되지 않은 키는 생성 실패가 정상이므로, 임의로 숨기거나 통과시키지 않는다.
- 새 생성기를 추가하거나 메타 출력을 바꾸면, 미분류 임시 키를 정본에 넣어 생성이 실패하는지
  확인한 뒤 즉시 제거하고 전체 재생성·`--check`를 실행한다. 고객용 값의 허용은
  `aidd-guide/artifact/spec-schema.md` §5를 따른다.

## 생성 대상

`gen` = `gen.mjs` 가 만든다(손대지 않는다) · `prose` = 아직 이 문서를 읽고 AI 가 쓴다

| 대상 | 누가 | 출력 | 원본 |
|---|---|---|---|
| **요구사항정의서** | `gen` | `documents/generated/10-요구사항정의서/{장}.md` + `_index.md` | 00-charter · 01-actors · 02-constraints · 03-glossary · 10-requirements · 11-processes · 21-nfr · 51-interfaces |
| 아키텍처 | `gen` | `documents/generated/20-아키텍처.md` (Mermaid) | 20-architecture + ADR |
| 기능목록 | `gen` | `documents/generated/31-기능목록.md` | 31-features |
| 메뉴·화면정의서 | `gen` | `documents/generated/60-화면정의서.md` | 60-screens |
| 권한매트릭스 | `gen` | `documents/generated/22-권한매트릭스.md` | 22-authz |
| 추적성 | `gen` | `documents/generated/90-추적성매트릭스.md` | `trace-check --report` |
| 요건정의서 | `gen` | `documents/generated/32-요건정의서/FEAT-###.md` + `_index.md` | 31-features + 50-api + 60-screens (REQ/UC 참조) |
| 유즈케이스 | `gen` | `documents/generated/30-유즈케이스.md` | 30-usecases |
| ERD | `gen` | `documents/generated/40-ERD.md` (Mermaid erDiagram) | 40-domain |
| 테이블명세서 | `gen` | `documents/generated/41-테이블명세서/{ENT-ID}.md` + `_index.md` (기술 기반은 테이블명) | 40-domain + 활성 Liquibase |
| DDL | `prose` | 승인된 **새 Liquibase changeset** `sources/backend/bootstrap/src/main/resources/db/changelog/` | 40-domain + conventions |
| API | `gen` | `documents/generated/50-API.md` + `documents/generated/50-openapi.yaml` | 50-api |
| 스케줄배치명세서 | `gen` | `documents/generated/65-스케줄배치명세서/` | 65-batches |
| 테스트케이스 | `gen` | `documents/generated/70-테스트케이스.md` | 70-testcases |
| 운영런북 | `gen` | `documents/generated/80-운영런북.md` | 80-operations |
| 시퀀스 | `gen` | `documents/generated/33-시퀀스/UC-###.md` (Mermaid sequenceDiagram) + `_index.md` | 30-usecases.sequence_design (50-api 등 상세 계약 참조) |

**`prose`는 DDL 하나다.** 승인된 물리 변경이 생길 때 생성기로 옮긴다.
기능별 요건정의서는 정본의 모든 필드와 정규화한 화면/API 연결을 생성한다.
시퀀스는 [정본 서식](../../../aidd-guide/artifact/spec-schema.md#핵심-처리-시퀀스)의 UC `sequence_design`에서
Mermaid·읽는 순서·API/시험·후속 계약을 함께 생성한다. 작성하지 않은 UC의 완료를 추정하지 않으며
`--check`로 그림/설명 누락·노후화·폐기 문서 잔존을 거부한다.
유즈케이스·ERD·API·테스트는 `gen.mjs`가 모든 필드를 빠뜨리지 않고 재현한다.
사용자 매뉴얼은 전역 문서로 중복 생성하지 않고 화면별 `manual.md`를
`/deliverable-site`가 한 사이트로 조립한다. `gen.mjs`의 `NOT_YET`은 남은 DDL을 부르면
*왜 아직 아닌지*를 말한다.

## 요구사항 정의서 — 지도 43행 (`요구사항정의서`)

**요건정의서(32)와 다른 것이다.** 32 는 *기능 하나* 를 구현하려는 AI 가 읽고, 이것은
**P0~P2 정본 여덟을 사용자(고객·의사결정자)가 순서대로** 읽는다 — 판단하는 것은
*이대로 만들어도 되는가* 이고, 그 판단이 곧 게이트 승인이다.

- **왜 자리를 갖나** — spec 여덟이 `S` 라 파생 뷰가 없었고, **게이트 승인의 대상을
  YAML 로 내밀고 있었다.** AGENTS.md §5-12(이해하지 못한 승인을 받아내지 않는다)가
  요구하는 것을 이 문서가 뒷받침한다.
- **소유** — **파생(`G`)이다.** `.gitignore` 대상이며 커밋하지 않는다. 고칠 것은 spec 이다.
- **장 나눔** (원칙 B — 한 장이 한 파일이다)

| 파일 | 원본 | 담는 것 |
|---|---|---|
| `_index.md` | — | 장 목록 + 한 줄 요약 + 승인 상태 |
| `00-개요.md` | 00-charter | 문제 · 경제성 · 목적 · 목표 · 비목표 · 성공 기준 · 로드맵 |
| `01-이해관계자.md` | 01-actors | 액터 · 모듈 |
| `02-제약과-원칙.md` | 02-constraints | 설계 원칙 · 기술 제약 · 배포 전제 · 착수 시 확정 항목 |
| `03-용어.md` | 03-glossary | 용어 · 턴 상태 · 프레임워크 어휘 · **헷갈리는 짝** |
| `04-요구사항/` (`00-목록.md` + 정본 조각별 파일) | 10-requirements | 기능 요구사항 (FR) — 조각 첫 줄 `# 영역: …` 이 파일 이름·제목 |
| `05-업무프로세스.md` | 11-processes | As-Is / To-Be |
| `06-비기능요구.md` | 21-nfr | 성능 · 비용 · 안정성 |
| `07-외부인터페이스.md` | 51-interfaces | 나가는 · 들어오는 연동 |

- **비어 있는 spec 은 빈 장으로 낸다** — *왜 비어 있고 무엇에 막혀 있는지* 를 그 장에
  적는다. 장을 빼면 읽는 사람이 **그 주제를 다룬 줄 알게 된다.**
- **`meta.status` 와 `baselined` 를 각 장 머리에 싣는다.** `migrated` 는 승인 전이라는
  뜻이며, 그 표시가 없으면 파생 뷰가 합의된 것처럼 읽힌다.

## 요건정의서 한 장 포맷 (핵심 산출물)
```markdown
# FEAT-012 입고 예정 등록

| 항목 | 내용 |
|---|---|
| 기능ID | FEAT-012 |
| 메뉴 | 입고관리 > 입고예정등록 (MNU-01-01) |
| 유형 | 트랜잭션 |
| 관련 요구사항 | REQ-011, REQ-012 |
| 유즈케이스 | UC-004 |
| 사용 권한 | 협력사담당자, 창고관리자 |

## 1. 기능 개요
## 2. 처리 흐름  (UC main_flow)
## 3. 업무 규칙  (UC business_rules)
## 4. 예외 처리  (UC exception_flows → API 에러코드 매핑 표)
## 5. 화면       (SCR-008 필드·액션·유효성)
## 6. 데이터     (사용 엔티티, 생성/변경되는 항목)
## 7. API        (API-031, API-032 요약)
## 8. 테스트 케이스 (TC 목록)
```

## 사용자 매뉴얼 생성 규칙
기능 나열이 아니라 **업무 상황 기준 목차**로 만든다. 역할별로 섹션을 나눈다.
각 절차에 "이럴 때는" (오류 상황 대응) 을 붙인다. → `aidd-guide/phase/P6-deliver.md` §3

## 완료 후
생성 실패를 종료로 보고하지 않는다. 오류·보완 원본·다음 조치를 설명하고 합의된 범위의 누락은 AI가 고쳐 재생성한다.
업무 의미/명명 충돌/파괴적 변경에 사용자 판정이 필요한 경우에만 근거·권장안·영향을 질문한다. 논리 설계의 물리명 미정은 오류가 아니다.
생성한 파일 목록과 건수를 보고한다. spec 대비 누락이 있으면 함께 보고한다.
