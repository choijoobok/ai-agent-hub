---
name: slice
description: 수직 슬라이스 하나를 구현한다. 소유 작업흐름과 스펙 확인 → 테스트 먼저 → 필요한 층 구현 → 계약·테스트 통과 → 미니게이트 순으로 진행한다. P4 구현 단계에서 기능 하나를 만들 때 사용.
---

# 슬라이스 구현

인자: `SLICE-###` 또는 생략(백로그의 다음 todo)

## 전제 조건 확인
- `slices[]`의 `workstream`과 `features`가 있어야 하고 기능의 `workstream:` 또는 `module:`과
  일치해야 한다. 공통 기능은 `integration`이 소유한다.
- `workstreams.<키>.gates.P3_design.status == approved` 여야 한다. 작업흐름 구조가 없는
  기존 프로젝트만 전역 `gates.P3_design`을 쓴다. 아니면 **거부하고 이유를 보고**한다.
- 이 슬라이스의 `pending_rechecks`가 있으면 새 구현과 함께 실행할지 먼저 확인한다.
- 의존 슬라이스가 `done` 인지 확인.

## 절차 — `aidd-guide/process/07-slice.md` §3

### ① 스펙 재확인 (기억에 의존 금지)
해당 `FEAT-###`의 spec을 **실제로 읽는다**: UC / ENT / API / SCR / TC 전부.
변경 전 `trace-check --impact`와 `--source`로 공통 소비자·정본을 찾고, 완료 전 API 실제 목록/계약과 변경 후 영향을 대조한다. 연결이 없다는 결과를 영향 없음으로 간주하지 않는다.

### ② 설계 확인
구현 방식이 spec과 어긋나면 코드가 아니라 **CR을 먼저** 낸다.

### ③ 필요한 동작의 TEST-FIRST
`aidd-guide/quality/test-design.md` §0으로 변경·중요 위험에 맞는 최소 시험을 고른다.
논리 문서 `documents/spec/70-testcases`의 해당 TC를 실행 코드로 옮기고
**실패하는 것을 확인**한 뒤 구현한다. 단순 문서/표시 변경에 새 시험을 억지로 만들지 않는다.
> 코드를 먼저 쓰면 AI는 그 코드에 맞는 테스트를 쓴다. 그건 검증이 아니라 자기 확인이다.

### ④ 필요한 층
DB → 서비스/API → UI 중 기능에 존재하는 층만 구현한다. 마이그레이션이 있으면 롤백을,
API·화면이 있으면 각 spec 계약과 권한·예외를 확인한다. 없는 UI를 agent 기능에 만들지 않는다.
테이블 변경은 40-domain 물리 계약·새 Liquibase changeset·활성 include·ENT 소스 연결을 같은 변경에 반영한다.
`node aidd-guide/harness/runtime/domain-schema.mjs`와 doc-gen/HTML 재생성을 수행한다. 적재된 changeset은 수정하지 않는다.

### ⑤ 계약·통합
외부에서 관찰 가능한 API·이벤트·응답 블록을 확인한다. 모듈 간 계약 변경은 `integration`
계약 슬라이스를 먼저 닫고 제공자·소비자 슬라이스가 그 버전을 따른다.

### ⑥ 통과 확인
선정한 필수 시험 통과. **실패를 skip / xfail로 우회하지 않는다.** 수정 중에는 관련 시험만,
통합 영향이 있을 때 최종 회귀 한 번만 실행한다. 코드·설정·의존성이 같은 비영향 통과 결과는 재사용한다.

### ⑦-1 중요한 위험의 반례 확인
변경된 인증·권한·정합성·비가역 동작의 반례가 차단되고 업무 효과가 남지 않는지 확인한다.
검증 공백이 의심될 때만 해당 조건을 뒤집어 관련 시험의 실패를 확인하고 원문을 복구한다.
자동 변이·전 규칙 수동 반전·고정 점수는 의무가 아니다. 실환경 시험을 변이마다 반복하지 않는다.
필수 반례 누락과 현재 수정에 맞지 않는 과거 결과는 숨기지 않는다.
→ `aidd-guide/quality/test-design.md` §0 · `aidd-guide/quality/redteam.md` §4

### ⑦-2 기계 품질 검사 (필수)
```bash
node .claude/hooks/quality-check.mjs
```
중복률 ≤5% · 예외 삼킴 0건 · `@feat` 미태깅 0개.
중복이 걸리면 **추출한다.** 임계값을 올리지 않는다.
→ `aidd-guide/quality/INDEX.md` §5

### ⑧ 미니게이트 G4
`aidd-guide/process/04-gates.md` G4 체크리스트 + `aidd-guide/quality/INDEX.md` §6 코드 품질 기준.
사용자에게 실행 결과(스크린샷/실행 방법)를 제시한다.

### ⑨ 상태 갱신
`documents/project.state.yaml`: slice status = done, 다음 슬라이스 제시. 완료 슬라이스가 통합
영향을 받아도 status를 되돌리지 않고 `pending_rechecks`만 등록·해제한다. 다른 팀원이 넘긴
재시험도 실행 명령과 결과를 남기면 누구나 닫을 수 있다.
완료 보고는 `aidd-guide/process/07-slice.md` §6 포맷.

## 첫 슬라이스(SLICE-001)는 골격이다
기능이 아니라 **관통 골격(Walking Skeleton)**: 로그인 → 권한 → 화면 → DB → 로그 → 테스트 → 배포.
→ `aidd-guide/phase/P4-build.md` §3

## 구현 중 spec 오류 발견 시
- 오타/누락(Low) → spec 수정 + 통지 후 계속
- 설계 결함(Medium) → 영향분석 + CR 등록 + 통지 후 계속
- 요구사항 모순(High) → **구현 중단**, 사용자 결정 요청, 다른 슬라이스로 이동

**절대 금지**: spec과 다르게 구현하고 나중에 spec을 맞추기.
