---
id: LLM-PHS-004
title: "P4 구현 — 수직 슬라이스 반복"
category: phase
type: operation
created: 2026-09-04
updated: 2026-09-11
status: active
sources: []
tier: 1
tags: [build, slice]
---

# P4 구현 — 수직 슬라이스 반복

> 목표: 슬라이스 단위로 동작하는 것을 쌓는다. 계층별로 만들지 않는다.
> 상세 규약: `aidd-guide/process/07-slice.md`
> 소요: 프로젝트의 35%

## 1. 슬라이스 시작 전 (매번)

`project.state.yaml`에서 슬라이스의 `workstream`을 확인하고 **그 작업흐름 G3 승인**을 먼저
검사한다. 전역 G3가 아직 열리지 않았어도 해당 작업흐름 G3가 승인되면 그 기능은 구현할 수
있다. 기능 소유권과 공통 기반 예외는 [`process/13-workstreams.md`](../process/13-workstreams.md)가 갖는다.

```
1. documents/project.state.yaml 에서 다음 슬라이스 확인
2. 해당 FEAT / UC / ENT / API / SCR / TC spec 을 실제로 읽는다  ← 기억에 의존 금지
3. 의존 슬라이스가 done 인지 확인
4. 사용자에게 "SLICE-### 시작합니다. 범위: ..." 한 줄 통지
5. 같은 슬라이스의 pending_rechecks가 있으면 새 구현 전에 함께 실행할지 확인
```

## 2. 슬라이스 내부 순서 (고정)

| 순서 | 작업 | 완료 조건 |
|---|---|---|
| 1 | TC를 테스트 코드로 옮긴다 | 테스트가 **실패**하는 것을 확인 |
| 2 | 마이그레이션 작성 | 적용 + 롤백 스크립트 존재 |
| 3 | 도메인/서비스 구현 | 업무 규칙(UC의 business_rules)이 코드에 있음 |
| 4 | API 구현 | spec의 요청/응답/에러코드와 일치 |
| 5 | 화면 구현 | error_display/empty_state/loading 포함 |
| 6 | 영향·중요 위험에 맞는 테스트 실행 | 선정한 필수 시험 통과, 실패를 skip하지 않음. 비영향 근거 재사용 |
| 7 | 미니게이트 G4 | 체크리스트 + 사용자 확인 |

## 3. 첫 슬라이스는 특별하다 — 골격(Walking Skeleton)

`SLICE-001` 은 기능이 아니라 **관통 골격**이다.

```
로그인 → 권한 확인 → 하나의 목록 화면 → DB 조회 → 화면 표시 → 로그 기록 → 테스트 → 배포
```

여기서 프로젝트의 모든 기술 리스크가 한 번에 드러난다. 기능 슬라이스보다 먼저 한다.
골격에 포함할 것:
- 인증/인가 실제 동작 (목업 아님)
- DB 연결 + 마이그레이션 도구
- 에러 처리·로깅 공통 처리
- 테스트 실행 환경
- 빌드/배포 스크립트
- 화면 레이아웃 공통 프레임 (메뉴, 헤더, 권한별 메뉴 노출)

## 4. 코드 규칙 (모든 슬라이스 공통)

```
sources/
  <도메인영역>/            # 조회, 카탈로그, 실행, 운영, 시스템
    <feature>/
      *.controller.*       # @feat FEAT-###
      *.service.*
      *.repository.*
tests/
  unit/                    # 서비스·도메인 규칙
  integration/             # API 계약 (documents/spec/50-api/_index.yaml 대조)
  e2e/                     # UC 시나리오 (TC-### 매핑)
db/migrations/             # 순번_설명.sql + 순번_설명_rollback.sql
```

- 모든 소스 파일 상단에 `@feat FEAT-###` 태그 (→ [05-traceability.md](../process/05-traceability.md) §4)
- 업무 규칙은 컨트롤러가 아니라 서비스/도메인에 둔다 (테스트 가능하게)
- 권한 검사는 API 레벨. 화면 숨김만으로는 미충족
- 에러는 spec의 에러코드로 던진다. 임의 메시지 금지

## 5. 진행 보고 리듬

| 시점 | 보고 |
|---|---|
| 슬라이스 시작 | 한 줄 (범위) |
| 슬라이스 완료 | 완료 보고 포맷 ([`process/07-slice.md`](../process/07-slice.md) §6) |
| 5개 슬라이스마다 | 누적 진척 + CR + 남은 슬라이스 + 범위 증가율 + **검증 부담 추이** |
| 막힘 발생 즉시 | 무엇이 막혔는지 + 우회안 2개 |

**"진행 중"만 반복 보고하지 않는다.** 사용자가 볼 것이 없는 보고는 소음이다.

### 검증 부담 기록 (슬라이스마다 필수)

**셀 수 있는 것**을 기본으로 기록하고, 실행 로그의 실제 시간만 병목 분석에 쓴다. 추정 시간·절감률을 만들지 않는다.
슬라이스 완료 시 `documents/project.state.yaml` 의 `verification_load` 에 기록한다.

| 항목 | 세는 방법 |
|---|---|
| `fix_rounds` | 테스트 실패 → 수정 사이클을 몇 번 돌았나 |
| `unverified_rules` | 변경된 중요한 규칙 중 유효한 반례/검증 근거가 없는 수. 선택한 반전이 실패를 못 잡는 경우 포함 |
| `quality_fails` | `quality-check.mjs` 가 미충족을 낸 횟수 |
| `spec_defects` | 구현 중 발견한 spec 오류 (CR 발생 수) |
| `redteam_fixes` | 레드팀 지적 중 실제 수정으로 이어진 수 |

**5개 슬라이스마다 추세를 보고한다.** 지표가 내려가면 J-커브를 빠져나오는 중이고,
평평하거나 올라가면 원인이 있다 — 해석표는 `aidd-guide/process/11-verification-cost.md` §3.

특히 `spec_defects` 나 `unverified_rules` 가 줄지 않으면 **P3 산출물이 부실했다는 신호**다.
남은 슬라이스의 해당 spec을 선제 점검할지 사용자에게 제안한다.

### 사용자가 "너무 느리다, 절차를 줄이자"고 할 때

즉흥적으로 판단하지 않는다. `aidd-guide/process/11-verification-cost.md` §4 의 3분류
(**줄일 수 있는 것 / 절대 안 되는 것 / 지금은 빠르지만 나중에 비싼 것**)로 즉답한다.
줄이기로 한 것은 `charter.expectations.reduced_scope` 에 근거와 함께 기록한다.

## 6. 구현 중 자주 생기는 상황과 처리

| 상황 | 처리 |
|---|---|
| spec에 없는 필드가 필요 | 왜 필요한지 판단 → 설계 누락이면 CR, 구현 편의면 안 만든다 |
| API 스펙대로 하니 화면이 어색 | spec을 고친다(CR). 코드로 우회하지 않는다 |
| 성능이 안 나옴 | NFR 목표치 확인 → 목표 내면 그냥 진행, 초과면 ADR로 최적화 결정 |
| 라이브러리가 예상과 다름 | Low 결정이면 바꾸고 진행. 아키텍처 영향이면 ADR |
| 테스트가 실패하는데 원인 불명 | **skip 하지 않는다.** 슬라이스를 blocked로 두고 보고 |
| 다른 팀원이 통합 후 시험을 넘김 | `pending_rechecks`의 TC를 실행하고 근거를 남긴 뒤 항목 제거 |
| 사용자가 중간에 새 기능 요청 | CR 절차. 현재 슬라이스는 끝내고 처리 |

## 7. G4 미니게이트 (슬라이스마다)
→ [04-gates.md](../process/04-gates.md) 의 G4 체크리스트

전 슬라이스 done → `/phase-gate P4` → P5로.
