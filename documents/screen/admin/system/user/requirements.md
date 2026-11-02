# 요건 정의서 — 사용자 관리

> **CR-028 목표 경계(2026-10-29):** 이 문서의 기존 소스 역작성은 현재 구현 근거다. system 계정·인증·세션·SYS_USER 참조는 관리자 영역에 한정한다. Portal 계정/인증은 ARCH-050의 별도 계약과 SCR-113~115를 사용하며 같은 문자열 ID로 관리자 이름·이력·개인 설정을 연결하지 않는다. 통합 감사의 영역 구분·이행 및 상세 화면 변경은 소유 P3 후속이고 현재 소스에서 구현됐다는 뜻이 아니다.


## 개요

- **무엇을 하는 화면인가**: 시스템을 쓸 계정을 등록하고, 계정을 못 쓰게 하는 넷(사용 여부 · 사용 기간 · 잠금 · 휴면)을 관리하며, 그 계정의 역할 부여 · 접속 허용 IP · 활성 세션을 함께 다룬다. 임시 비밀번호 발급도 여기서 한다.
- **프로세스상 위치**: (선행) `ROLE_MGMT` 에서 역할이 등록되어 있어야 역할을 부여할 수 있다 → **이 화면** → (후행) 역할로 덮지 못하는 권한은 `USER_AUTH_MGMT`(개인 예외)에서 준다.
- **이 데이터를 누가 쓰나**: 로그인 접근 제어 체인(`SYS_USER` 의 넷 + `SYS_USER_IP_ALLOW`), 인가 계산(`SYS_USER_ROLE` → 유효 권한), 로그인·사용 이력의 주인 표시.
- **바꾸면 어디가 흔들리나**: 로그인 체인의 판정 순서(계정 상태 파생과 조회조건이 같은 순서를 본다), 유효 권한 계산, 휴면 배치.

> **이 문서는 구현된 소스에서 역으로 작성했다**(2026-09-03). 신규 화면의 요건서는 구현 **전에** 쓰고 승인을 받는다(AGENTS.md §6.3) — 여기서는 문서 셋의 첫 실물을 세우는 것이 목적이라 순서가 반대다.

---

## 0. 메타

| 항목 | 값 |
|---|---|
| 페이지 경로 | `/admin/system/user` |
| 메뉴코드 | `USER_MGMT` |
| 프론트 폴더 | `sources/frontend/src/modules/system/features/user/` |
| 백엔드 패키지 | `sources/backend/domain/…/domain/user/admin/` · `sources/backend/domain/…/domain/user/detail/` · `sources/backend/modules/system/…/system/api/user/` |
| 화면 유형 | 마스터-디테일(상하) — 디테일은 탭 다섯 |
| 처리 유형 | CRUD + 실행 |
| 쓰는 액션 | `R` `S` `E` `X` — **넷을 다 쓰는 유일한 화면**이다. `EXPORT_YN` = `Y` |
| 페이징 | offset (마스터). 디테일 탭 다섯은 전건 |
| 문서 상태 | 구현됨 — 소스 역작성 2026-09-03 |

---

## 1. 화면 구성

```
[조회조건 — 브레드크럼 헤더에 접기/펼치기]
[그리드 A: 사용자]                     ← 마스터
[━━ 스플리터(상하 · 양쪽 접기 가능) ━━]
[탭: 보유 역할 · 개인 예외 · 접속 IP · 활성 세션 · 유효 권한]   ← 디테일
```

- 진입 시 자동 조회: **안 함.** 사용자가 [조회]를 누른다.
- 축이 **상하**인 이유: 마스터 컬럼이 열아홉이라 좌우로 나누면 가로가 모자란다.
- 모달: **임시 비밀번호 발급 결과** 하나(§5 · §6-4).

### 1-1. 마스터-디테일 연동

- 연동 기준: 마스터의 `USER_ID` 로 디테일 다섯을 조회한다.
- **미저장 신규 마스터 행을 선택한 동안**: 디테일 다섯은 **잠긴 빈 상태**이며 문구는 *저장 후 편집할 수 있습니다*. `SYS_USER_ROLE` 이 `SYS_USER` 를 FK 로 참조하므로 부모가 저장되기 전에는 자식을 만들 수 없다 — **신규 사용자에게 역할을 주는 것은 2단계**다.
- **선택 없음**: 문구는 *사용자를 선택하세요*.
- 디테일에 미저장 변경(보유 역할 · 접속 IP)이 있는 상태에서 다른 마스터 행 선택: **확인 후 폐기**. 거부하면 선택 자체가 일어나지 않는다.
- 마스터 [조회]: 디테일 선택을 **해제**한다. 자동 선택하지 않는다.
- 저장 단위: **마스터와 디테일이 각각 따로.** 디테일 탭도 서로 따로다.
- 디테일 저장 성공 시: **나머지 네 탭의 조회 결과를 버린다**(다음에 그 탭을 열 때 다시 조회한다). 역할 저장이 유효 권한을 바꾸기 때문이며, 접속 IP 는 바꾸지 않지만 같은 규칙을 건다.

---

### 1-9. 영역별 관리 대상과 작업

| 영역 | 종류 | 관리 대상·목적 | 작업 설명 | 버튼 | 조회 방식 | 저장 범위 |
| --- | --- | --- | --- | --- | --- | --- |
| 사용자 | 목록 | 관리자 계정을 관리한다. | 계정을 추가/수정한다. 잠금·휴면 해제와 비밀번호 초기화는 선택 계정에 별도 실행한다. 삭제 기능은 없다. | 조회·추가·저장·잠금 해제·휴면 해제·비밀번호 초기화 | 조회 버튼 | 관리자 계정 변경 행 |
| 보유 역할 | 탭 | 선택 관리자에게 부여한 역할과 유효기간을 관리한다. | 저장된 사용자를 선택한 뒤 역할을 추가/제거하고 기간을 저장한다. | 조회·추가·저장·행 삭제 | 사용자 선택/탭 첫 진입 및 조회 버튼 | 선택 사용자의 역할 변경 행 |
| 개인 예외 | 탭 | 선택 사용자의 직접 부여 예외를 읽는다. | 수정은 개인 권한 관리에서 한다. | 조회 | 사용자 선택/탭 첫 진입 및 조회 버튼 | 조회 전용 |
| 접속 IP | 탭 | 선택 계정의 접속 허용 IP 대역을 관리한다. | 대역을 추가/삭제한다. 첫 대역 등록은 접속을 제한하므로 경고를 확인한다. | 조회·추가·저장·행 삭제 | 사용자 선택/탭 첫 진입 및 조회 버튼 | 선택 사용자의 IP 변경 행 |
| 활성 세션 | 탭 | 현재 살아 있는 선택 계정의 세션을 확인한다. | 선택한 세션을 종료한다. 자기 계정은 종료할 수 없다. | 조회·세션 종료 | 사용자 선택/탭 첫 진입 및 조회 버튼 | 선택 세션 종료; 계정 변경 없음 |
| 유효 권한 | 탭 | 선택 계정의 최종 권한을 진단한다. | 표시 범위를 선택해 메뉴별 유효 액션을 조회한다. | 조회 | 사용자/탭/표시 범위 변경 및 조회 버튼 | 조회 전용 |

- 표의 버튼은 현재 소스의 배치를 따른다. 행 삭제는 그리드 행의 삭제 선택 기능이며 독립 업무 삭제 명령으로 바꾸지 않는다.

## 2. 조회조건

**기본값을 가진 칸이 하나도 없다.** 계정을 끈 뒤 *왜 안 보이나* 를 확인하러 오는 동선이 이 화면의 흔한 사용이고, 기본 필터가 걸려 있으면 **방금 끈 그 계정이 첫 조회에 없다**.

| 조건 | 대상 컬럼 | 입력방식 | 연산 | 필수 | 기본값 | 선택 출처 | 라벨 키 |
|---|---|---|---|---|---|---|---|
| 로그인 ID | `USER_ID` | 텍스트 | `startsWith`(기본) · `eq` · `contains` | 선택 | — | — | `MSG.USER_SEARCH_USER_ID` |
| 이름 | `USER_NM` | 텍스트 | `contains`(기본) · `eq` · `startsWith` · `endsWith` | 선택 | — | — | `MSG.USER_SEARCH_USER_NM` |
| 이메일 | `EMAIL` | 텍스트 | `contains`(기본) · `eq` · `startsWith` · `endsWith` | 선택 | — | — | `MSG.USER_SEARCH_EMAIL` |
| 계정 상태 | **파생** — `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCK_YN` · `DORMANT_YN` | 셀렉트(다중) | `in` | 선택 | **없음** | 상수 다섯(§6-3) | `MSG.USER_SEARCH_STAT` |
| 보유 역할 | **파생** — `SYS_USER_ROLE` 의 `EXISTS` | 셀렉트 | `eq` | 선택 | — | `getRoleCandidates` | `MSG.USER_SEARCH_ROLE` |
| 역할 만료 예정 | **파생** — `SYS_USER_ROLE.VALID_TO_DT` | 셀렉트 | `eq` | 선택 | — | 상수 `7·14·30·60·90` 일 | `MSG.USER_SEARCH_ROLE_EXPIRING` |
| 최종 로그인 | `LAST_LOGIN_DTM` | 날짜(기간) | `between`(기본) · `gte` · `lte` | 선택 | **없음** | — | `MSG.USER_SEARCH_LAST_LOGIN` |
| 등록일 | `REG_DTM` | 날짜(기간) | `between`(기본) · `gte` · `lte` | 선택 | **없음** | — | `MSG.USER_SEARCH_REG_DTM` |

- **계정 상태는 값과 SQL 컬럼이 1:1 이 아니다.** 화면이 보내는 것은 다섯 중 몇 개이고 서버가 그것을 네 컬럼의 조건으로 번역한다(§6-3). `in` 과 `eq` 만 받는다.
- **보유 역할은 기간을 보지 않는다.** 묻는 것이 *이 역할을 가진 사람* 이고 만료된 부여도 그 사실이다.
- **역할 만료 예정은 숫자 칸이 아니라 후보 콤보다.** 파생 조건은 부등호를 받지 못하며(파생 필드의 자바 타입이 문자열이다) *N일 이내* 라는 뜻은 서버의 번역 함수가 갖는다. 범위는 **오늘부터**이며 이미 만료된 부여는 걸리지 않는다.
- 기간 둘에 `gte`·`lte` 를 연 이유: 여기서 실제로 묻는 것이 *이 날짜 이후로 한 번도 안 들어온 계정* 이고, 범위로만 두면 반대쪽 끝에 사람이 아무 값이나 넣어야 한다.

---

## 3. 조회 — 테이블과 범위

### 3-1. 사용 테이블 · 결합

| 테이블 | 역할 | 결합 기준 |
|---|---|---|
| `SYS_USER` | 메인 | — |
| `SYS_USER_ROLE` | 조회조건 둘의 `EXISTS` 전용 | `USER_ID` |

- **조인이 없다.** 목록에 역할명을 이어 붙이지 않기 때문이다 — 문자열 집계 함수가 벤더마다 다르다(`LISTAGG` · `GROUP_CONCAT` · `string_agg`). 그 질문은 조회조건 `보유 역할` 과 디테일 `보유 역할` 탭이 나눠 받는다.
- **`PWD` 를 내리지 않는다.** 해시조차 응답에 담지 않으며 화이트리스트에도 없다.

### 3-2. 고정 조건 · 기본 정렬

- 항상 적용되는 필터: **없음.** 물리 삭제가 없으므로 지워진 행이라는 개념이 없다.
- 기본 정렬: `USER_NM` 오름차 → `USER_ID` 오름차. **tie-breaker 가 필수**다 — 동명이인에서 순서가 비결정적이면 페이징이 같은 행을 두 장에 낸다.
- 조회 상한: 없음(기간이 필수가 아니다). 페이징은 offset.

---

## 4. 컬럼

- **행 식별키**: `USER_ID`.
- **감사 4컬럼은 표에 적지 않는다** — 표준이다. **예외 하나**: 이 화면은 마스터 · 보유 역할 · 개인 예외 · 유효 권한에서 감사 넷의 **정렬을 켠다**.

### 4-1. 마스터 그리드 — 사용자

**표시 여덟 · 기본 숨김 일곱.** 숨김도 응답에는 들어 있고 그리드 설정에서 켜면 보인다 — **목록이 곧 상세이며 `getDetail` 이 없다.**

| 컬럼 | 라벨 키 | DB 컬럼 | **필드명** | 입력방식 | 폭/정렬 | 필수 | 편집 | 선택 출처 | 검증 | 설명 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 로그인 ID | `MSG.USER_COL_USER_ID` | `USER_ID` | `userId` | 텍스트 | 160/좌고정 | 필수 | **신규행만**(PK) | — | 그리드 내 중복 금지 | 로그인 ID 항목. 값/선택 출처: —. | string | text | text | 없음 | 없음 | 저장 시 | 가능 | 입력 대상 아님 | 불가(신규만 입력) | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | —; sources/frontend/src/modules/system/features/user/pages/UserMgmtPage.tsx의 userId 컬럼 선언 | 소유 구성에 저장 | {"pk":true,"pinned":"left","sortable":true} |
| 이름 | `MSG.USER_COL_USER_NM` | `USER_NM` | `userNm` | 텍스트 | 140 | 필수 | 가능 | — | — | 이름 항목. 값/선택 출처: —. | string | text | text | 없음 | 없음 | 저장 시 | 가능 | 저장 시 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | —; sources/frontend/src/modules/system/features/user/pages/UserMgmtPage.tsx의 userNm 컬럼 선언 | 소유 구성에 저장 | {"sortable":true} |
| 이메일 | `MSG.USER_COL_EMAIL` | `EMAIL` | `email` | 텍스트 | 220 | **필수** | 가능 | — | — | 이메일 항목. 값/선택 출처: —. | string | text | text | 없음 | 없음 | 저장 시 | 가능 | 저장 시 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | —; sources/frontend/src/modules/system/features/user/pages/UserMgmtPage.tsx의 email 컬럼 선언 | 소유 구성에 저장 | {"sortable":true} |
| 상태 | `MSG.USER_COL_STAT` | **파생**(§6-3) | `accountStat` | 텍스트·배지 | 100 | — | 읽기전용 | — | — | 상태 항목. 값/선택 출처: —. | string | text | 없음 | badge | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | 자동 계산/집계 | 대상/조회 조건·소유 원천 변경 후 조회 시 | 파생(§6-3); 상태 항목. 값/선택 출처: —. | 비저장 투영(원천의 저장 결과/집계 참조) | — |
| 사용 여부 | `MSG.USER_COL_USE_YN` | `USE_YN` | `useYn` | 스위치 | 90 | — | **조건부**(§4-2) | — | — | 사용 여부 항목. 값/선택 출처: —. | boolean | ynFlag | 없음 | 없음 | 없음 | 선택 | 조건부 — §4-2의 권한·업무 상태 및 검증 조건 | 선택 | 조건부 — §4-2의 권한·업무 상태 및 검증 조건 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | — | 소유 구성에 저장 | {"sortable":true} |
| 사용 시작일 | `MSG.USER_COL_VALID_FROM` | `VALID_FROM_DT` | `validFromDt` | 날짜 | 120 | — | 가능 | — | 종료일보다 늦으면 거부 | 사용 시작일 항목. 값/선택 출처: —. | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | — | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//pages/UserMgmtPage.tsx#validFromDt.validate"} |
| 사용 종료일 | `MSG.USER_COL_VALID_TO` | `VALID_TO_DT` | `validToDt` | 날짜 | 120 | — | 가능 | — | 〃 | 사용 종료일 항목. 값/선택 출처: —. | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | — | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//pages/UserMgmtPage.tsx#validToDt.validate"} |
| 최종 로그인 | `MSG.USER_COL_LAST_LOGIN` | `LAST_LOGIN_DTM` | `lastLoginDtm` | 일시 | 160 | — | 읽기전용 | — | — | 최종 로그인 항목. 값/선택 출처: —. | string | dateTime | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | LAST_LOGIN_DTM | 소유 정본의 저장값 | {"sortable":true} |
| 언어 ○ | `MSG.USER_COL_LOCALE` | `LOCALE_CD` | `localeCd` | 셀렉트 | 100 | — | 가능 | **상수** `ko`·`en` | — | 언어 ○ 항목. 값/선택 출처: 상수 ko·en. | string | code | select | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 상수 ko·en; sources/frontend/src/modules/system/features/user/pages/UserMgmtPage.tsx의 localeCd 컬럼 선언 | 소유 구성에 저장 | {"visible":false,"options":"sources/frontend/src/modules/system/features/user//pages/UserMgmtPage.tsx#localeCd.options"} |
| 잠금 ○ | `MSG.USER_COL_LOCK_YN` | `LOCK_YN` | `lockYn` | 스위치 | 90 | — | **읽기전용** | — | — | 잠금 ○ 항목. 값/선택 출처: —. | boolean | ynFlag | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | LOCK_YN | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 잠금 시각 ○ | `MSG.USER_COL_LOCK_DTM` | `LOCK_DTM` | `lockDtm` | 일시 | 160 | — | 읽기전용 | — | — | 잠금 시각 ○ 항목. 값/선택 출처: —. | string | dateTime | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | LOCK_DTM | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 실패 횟수 ○ | `MSG.USER_COL_FAIL_CNT` | `LOGIN_FAIL_CNT` | `loginFailCnt` | 숫자 | 100 | — | **읽기전용** | — | — | 실패 횟수 ○ 항목. 값/선택 출처: —. | number | number | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | LOGIN_FAIL_CNT | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 휴면 ○ | `MSG.USER_COL_DORMANT_YN` | `DORMANT_YN` | `dormantYn` | 스위치 | 80 | — | **읽기전용** | — | — | 휴면 ○ 항목. 값/선택 출처: —. | boolean | ynFlag | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | DORMANT_YN | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 임시 비밀번호 ○ | `MSG.USER_COL_PWD_INIT` | `PWD_INIT_YN` | `pwdInitYn` | 스위치 | 110 | — | 읽기전용 | — | — | 임시 비밀번호 ○ 항목. 값/선택 출처: —. | boolean | ynFlag | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | PWD_INIT_YN | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 비밀번호 변경일 ○ | `MSG.USER_COL_PWD_CHG` | `PWD_CHG_DTM` | `pwdChgDtm` | 일시 | 160 | — | 읽기전용 | — | — | 비밀번호 변경일 ○ 항목. 값/선택 출처: —. | string | dateTime | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | PWD_CHG_DTM | 소유 정본의 저장값 | {"visible":false,"sortable":true} |

`○` = 기본 숨김. **감춘 컬럼(`hidden`)은 없다** — 운영자가 켜서 보면 안 되는 컬럼이 이 화면에는 없다.

- **비밀번호 컬럼을 두지 않는다.** 관리자는 임시 비밀번호를 **발급**할 뿐 값을 정하지 않는다 — 편집 가능한 자리를 두면 *관리자가 아는 비밀번호* 가 생긴다.
- **상태 컬럼은 정렬을 켜지 않는다.** SQL 이 모르는 값이라 화이트리스트에 없고, 켜면 400 이다.
- **삭제 컬럼이 없다**(§5-1).

### 4-1a. 디테일 탭 1 — 보유 역할 (편집 가능)

| 컬럼 | 라벨 키 | DB 컬럼 | **필드명** | 입력방식 | 폭 | 필수 | 편집 | 설명 | 검증 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 역할 | `MSG.USER_ROLE_COL_ROLE` | `ROLE_ID` | `roleId` | 셀렉트 | 220 | 필수 | **신규행만**(PK 절반) | 역할 항목. 저장 또는 조회 근거: ROLE_ID. | 10  ·  역할 저장  ·  화면  ·  같은 그리드 안에 `ROLE_ID` 중복 금지  ·  `MSG.USER_ROLE_DUPLICATE` | string | code | select | 없음 | 없음 | 필수 | 가능 | 입력 대상 아님 | 불가(신규만 입력) | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력 | 소유 구성에 저장 | — |
| 역할명 ○ | `MSG.USER_ROLE_COL_ROLE_NM` | `SYS_ROLE.ROLE_NM` | `roleNm` | 텍스트 | 160 | — | 읽기전용 | 역할명 ○ 항목. 저장 또는 조회 근거: SYS_ROLE.ROLE_NM. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | SYS_ROLE.ROLE_NM | 소유 정본의 저장값 | {"visible":false,"sortable":true} |
| 설명 ○ | `MSG.USER_ROLE_COL_ROLE_DESC` | `SYS_ROLE.ROLE_DESC` | `roleDesc` | 텍스트 | 240 | — | 읽기전용 | 설명 ○ 항목. 저장 또는 조회 근거: SYS_ROLE.ROLE_DESC. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | SYS_ROLE.ROLE_DESC | 소유 정본의 저장값 | {"visible":false} |
| 역할 사용 | `MSG.USER_ROLE_COL_USE_YN` | `SYS_ROLE.USE_YN` | `roleUseYn` | 스위치 | 110 | — | **읽기전용** — 이 부여의 값이 아니라 역할의 값이다 | 역할 사용 항목. 저장 또는 조회 근거: SYS_ROLE.USE_YN. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | boolean | ynFlag | 없음 | 없음 | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | SYS_ROLE.USE_YN | 소유 정본의 저장값 | {"sortable":true} |
| 시작일 | `MSG.USER_ROLE_COL_FROM` | `VALID_FROM_DT` | `validFromDt` | 날짜 | 130 | — | 가능 | 시작일 항목. 저장 또는 조회 근거: VALID_FROM_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력 | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#validFromDt.validate"} |
| 종료일 | `MSG.USER_ROLE_COL_TO` | `VALID_TO_DT` | `validToDt` | 날짜 | 130 | — | 가능 | 종료일 항목. 저장 또는 조회 근거: VALID_TO_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력 | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#validToDt.validate"} |
| 부여 상태 | `MSG.USER_ROLE_COL_STATE` | **파생**(§6-3) | `grantState` | 배지 | 90 | — | 읽기전용 | 부여 상태 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | badge | 없음 | 입력 대상 아님 | 불가 | 입력 대상 아님 | 불가 | 자동 계산/집계 | 조회 시 현재 원천과 같은 기준 시각으로 계산 | 오늘<validFromDt → SCHEDULED; 오늘>validToDt → EXPIRED; 그 외 ACTIVE; sources/backend/domain/src/main/java/com/aiagent/domain/user/detail/UserDetailQueryService.java#기간 기반 부여 상태 | 비저장 조회 파생. 계산에 쓰인 원천/관계 저장값과 구분 | {"badge":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#grantState.badge"} |

- 후보 콤보의 글자는 `역할명 (역할ID)` 이며 **꺼진 역할도 후보다** — 켜기 전에 부여를 구성해 두는 동선이 정상이다. 꺼져 있다는 사실은 `역할 사용` 컬럼과 상태 배지 옆 경고 배지가 말한다.
- 행 삭제 **허용**. 삭제는 *잘못 등록한 행 정리* 이고 **회수가 아니다** — 회수는 종료일을 어제로 두는 것이며 그때 행은 남는다(변경 이력 테이블이 없으므로 그 컬럼이 유일한 기록이다).

### 4-1b. 디테일 탭 2 — 개인 예외 (**조회 전용**)

| 컬럼 | 라벨 키 | DB 컬럼 | **필드명** | 입력방식 | 폭 | 편집 | 설명 | 검증 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 메뉴 | `MSG.USER_GRANT_COL_MENU` | **파생** — 메뉴 경로 | `menuPath` | 텍스트 | 280 | 읽기전용 | 메뉴 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 대상/조회 조건·소유 원천 변경 후 조회 시 | 파생 — 메뉴 경로; 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다.; 메뉴 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 비저장 투영(원천의 저장 결과/집계 참조) | {"pinned":"left","sortable":true} |
| 액션 | `MSG.USER_GRANT_COL_ACTION` | `ACTION_CD` | `actionCd` | **코드** | 100 | 읽기전용 | 액션 항목. 저장 또는 조회 근거: ACTION_CD. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | code | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | ACTION_CD | 소유 정본의 저장값 | {"sortable":true,"options":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#actionCd.options"} |
| 부여 상태 | `MSG.USER_AUTH_COL_STATE` | **파생**(§6-3) | `grantState` | 배지 | 90 | 읽기전용 | 부여 상태 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | badge | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 조회 시 현재 원천과 같은 기준 시각으로 계산 | 오늘<validFromDt → SCHEDULED; 오늘>validToDt → EXPIRED; 그 외 ACTIVE; sources/backend/domain/src/main/java/com/aiagent/domain/user/detail/UserDetailQueryService.java#기간 기반 부여 상태 | 비저장 조회 파생. 계산에 쓰인 원천/관계 저장값과 구분 | — |
| 시작일 | `MSG.USER_AUTH_COL_FROM` | `VALID_FROM_DT` | `validFromDt` | 날짜 | 130 | 읽기전용 | 시작일 항목. 저장 또는 조회 근거: VALID_FROM_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | VALID_FROM_DT | 소유 정본의 저장값 | {"sortable":true} |
| 종료일 | `MSG.USER_AUTH_COL_TO` | `VALID_TO_DT` | `validToDt` | 날짜 | 130 | 읽기전용 | 종료일 항목. 저장 또는 조회 근거: VALID_TO_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | VALID_TO_DT | 소유 정본의 저장값 | {"sortable":true} |
| 대상 유효 | `MSG.USER_GRANT_COL_MENU_OK` | **파생** | `menuAuthorizable` | 스위치 | 100 | 읽기전용 | 대상 유효 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | ynFlag | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 조회 시 현재 원천과 같은 기준 시각으로 계산 | grantState=ACTIVE 이고 menuAuthorizable=true; sources/backend/domain/src/main/java/com/aiagent/domain/user/detail/UserDetailQueryService.java#개인 예외 유효성 | 비저장 조회 파생. 계산에 쓰인 원천/관계 저장값과 구분 | {"sortable":true} |
| 부여 사유 | `MSG.USER_GRANT_COL_REASON` | `GRANT_RSN` | `grantRsn` | 텍스트 | 240 | 읽기전용 | 부여 사유 항목. 저장 또는 조회 근거: GRANT_RSN. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | GRANT_RSN | 소유 정본의 저장값 | {"fill":true} |
| (숨김) 메뉴코드 | `MSG.USER_AUTH_COL_MENU` | `MENU_CD` | `menuCd` | 텍스트 | 160 | 읽기전용 | (숨김) 메뉴코드 항목. 저장 또는 조회 근거: MENU_CD. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | MENU_CD | 소유 정본의 저장값 | {"visible":false} |

- **`액션` 은 `code` 다** — 라벨은 `MSG.AUTHZ_ACTION_*` 로 만든 `options` 에서 온다. **`ACTION_CD` 공통코드 그룹은 없다**(§8) — `grpCd` 로 쓰면 후보가 비어 코드값이 그대로 보인다.
- 감사 넷은 `auditColumns()` 가 붙인다.

- **저장 경로가 없다.** 사용자 관리 권한이 있다고 예외 권한 부여 권한이 따라오면 안 되며, 시드 생성기가 `SAVE` 를 만들 수 없으므로 **어느 역할에도 줄 수 없다.** 부여는 `/user-auth` 에서 한다.
- `대상 유효`(`menuAuthorizable`)가 거짓이면 **부여는 살아 있는데 대상이 죽은 것**이다.

### 4-1c. 디테일 탭 3 — 접속 IP (편집 가능)

| 컬럼 | 라벨 키 | DB 컬럼 | **필드명** | 입력방식 | 폭 | 필수 | 편집 | 설명 | 검증 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| IP · CIDR | `MSG.USER_IP_COL_CIDR` | `IP_CIDR` | `ipCidr` | 텍스트 | 220 | 필수 | **신규행만**(PK 절반) | IP · CIDR 항목. 저장 또는 조회 근거: IP_CIDR. | 11  ·  IP 저장  ·  화면  ·  `IP_CIDR` 이 IP 또는 CIDR 형식(빈 값은 통과 — 아직 안 쓴 칸이다)  ·  `MSG.USER_IP_MALFORMED` / 12  ·  IP 저장  ·  화면  ·  같은 그리드 안에 `IP_CIDR` 중복 금지  ·  `MSG.USER_IP_DUPLICATE` | string | text | text | 없음 | 없음 | 저장 시 | 가능 | 입력 대상 아님 | 불가(신규만 입력) | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력; sources/frontend/src/modules/system/features/user/components/UserDetailTabs.tsx의 ipCidr 컬럼 선언 | 소유 구성에 저장 | {"pk":true,"pinned":"left","sortable":true,"validate":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#ipCidr.validate"} |
| 사용 | `MSG.USER_IP_COL_USE_YN` | `USE_YN` | `useYn` | 스위치 | 90 | — | 가능 | 사용 항목. 저장 또는 조회 근거: USE_YN. | 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / **세션 · 캐시 부수효과**: 마스터 저장은 세션을 건드리지 않는다 — `USE_YN` 은 유효 권한의 입력이 아니라 **로그인 체인**의 것이고, 이미 로그인한 세션은 다음 로그인부터 막힌다. **역할 저장은** 그 사람의 세션에 권한 재계산을 예약한다. **IP 저장은** 세션을 건드리지 않는다(인가가 아니라 인증의 검사다). | boolean | ynFlag | 없음 | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력; 저장(수정)  ·  SYS_USER  ·  UPDATE  ·  USER_NM · EMAIL · USE_YN · VALID_FROM_DT · VALID_TO_DT · LOCALE_CD 만 / 세션 · 캐시 부수효과: 마스터 저장은 세션을 건드리지 않는다 — USE_YN 은 유효 권한의 입력이 아니라 로그인 체인의 것이고, 이미 로그인한 세션은 다음 로그인부터 막힌다. 역할 저장은 그 사람의 세션에 권한 재계산을 예약한다. IP 저장은 세션을 건드리지 않는다(인가가 아니라 인증의 검사다). | 소유 구성에 저장 | {"sortable":true} |
| 허용 시작 | `MSG.USER_IP_COL_FROM` | `VALID_FROM_DT` | `validFromDt` | 날짜 | 130 | — | 가능 | 허용 시작 항목. 저장 또는 조회 근거: VALID_FROM_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력 | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#validFromDt.validate"} |
| 허용 종료 | `MSG.USER_IP_COL_TO` | `VALID_TO_DT` | `validToDt` | 날짜 | 130 | — | 가능 | 허용 종료 항목. 저장 또는 조회 근거: VALID_TO_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 자동(type) | 없음 | 없음 | 선택 | 가능 | 선택 | 가능 | 사용자 입력 | 로컬 입력 후 전체 저장 성공 시 | 직접 입력 | 소유 구성에 저장 | {"sortable":true,"validate":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#validToDt.validate"} |

- **행이 없으면 제한 없음이다.** 빈 판의 문구가 그 사실을 말한다 — *허용 대역이 없습니다 — 이 계정은 어느 IP 에서도 접속할 수 있습니다.*
- **꺼진 행도 조회된다** — 다시 켜는 곳이 이 화면이다.
- 행 삭제 **허용**.
- 판정 시각은 **서버 기준**이며 양끝 포함이다.

### 4-1d. 디테일 탭 4 — 활성 세션 (조회 + 실행)

**DB 컬럼 칸이 없는 유일한 표다** — 원천이 세션 스토어이고 IP·브라우저만 로그인 이력에서 붙는다.

| 컬럼 | 라벨 키 | **필드명** | 입력방식 | 폭 | 편집 | 설명 | 검증 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 세션 | `MSG.USER_SESSION_COL_ID` | `sessionId` | 텍스트(앞 8자) | 140 | 읽기전용 | 세션 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/파생 | 참조 행 값 변경/재조회 시 | (row) => row.sessionId.slice(0, 8); sources/frontend/src/modules/system/features/user/components/UserDetailTabs.tsx#sessionId.derive | 비저장 화면 파생값 | {"pinned":"left","derive":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#sessionId.derive"} |
| 현재 세션 | `MSG.USER_SESSION_COL_CURRENT` | `current` | 스위치 | 110 | 읽기전용 | 현재 세션 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | boolean | ynFlag | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 조회 시 현재 원천과 같은 기준 시각으로 계산 | 대상 sessionId와 요청자의 현재 sessionId 동일 여부; UserDetailQueryService의 활성 세션 조회 | 비저장 조회 파생. 계산에 쓰인 원천/관계 저장값과 구분 | — |
| 생성 시각 | `MSG.USER_SESSION_COL_CREATED` | `createdDtm` | 일시 | 170 | 읽기전용 | 생성 시각 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | dateTime | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 참조 조회 | 참조 원천 변경/선택 대상 변경 후 재조회 시 | 세션 스토어에서 생성 시각 값을 조회. SYS_USER의 별도 컬럼으로 복사 저장하지 않음 | 화면 비저장 참조. 안정 식별자/매핑은 해당 소유 정본에 저장하며 표시명을 복사 저장하지 않음 | {"sortable":true} |
| 최근 접근 | `MSG.USER_SESSION_COL_ACCESS` | `lastAccessedDtm` | 일시 | 170 | 읽기전용 | 최근 접근 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | dateTime | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 참조 조회 | 참조 원천 변경/선택 대상 변경 후 재조회 시 | 세션 스토어에서 최근 접근 값을 조회. SYS_USER의 별도 컬럼으로 복사 저장하지 않음 | 화면 비저장 참조. 안정 식별자/매핑은 해당 소유 정본에 저장하며 표시명을 복사 저장하지 않음 | {"sortable":true} |
| 만료 예정 | `MSG.USER_SESSION_COL_EXPIRY` | `expiryDtm` | 일시 | 170 | 읽기전용 | 만료 예정 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | dateTime | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 참조 조회 | 참조 원천 변경/선택 대상 변경 후 재조회 시 | 세션 스토어에서 만료 예정 값을 조회. SYS_USER의 별도 컬럼으로 복사 저장하지 않음 | 화면 비저장 참조. 안정 식별자/매핑은 해당 소유 정본에 저장하며 표시명을 복사 저장하지 않음 | {"sortable":true} |
| 접속 IP | `MSG.USER_SESSION_COL_IP` | `clientIp` | 텍스트 | 140 | 읽기전용 | 접속 IP 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 참조 조회 | 참조 원천 변경/선택 대상 변경 후 재조회 시 | 세션 식별자/사용자 문맥에 연결된 로그인 이력의 접속 IP 조회. 세션 스토어 정보와 결합 | 화면 비저장 참조. 안정 식별자/매핑은 해당 소유 정본에 저장하며 표시명을 복사 저장하지 않음 | {"sortable":true} |
| 브라우저 | `MSG.USER_SESSION_COL_AGENT` | `userAgent` | 텍스트 | 240 | 읽기전용 | 브라우저 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 참조 조회 | 참조 원천 변경/선택 대상 변경 후 재조회 시 | 세션 식별자/사용자 문맥에 연결된 로그인 이력의 브라우저 조회. 세션 스토어 정보와 결합 | 화면 비저장 참조. 안정 식별자/매핑은 해당 소유 정본에 저장하며 표시명을 복사 저장하지 않음 | {"sortable":true,"fill":true} |

**기본 정렬은 `lastAccessedDtm` 내림차순이다.**

- 원천이 DB 가 아니라 **세션 스토어**다.
- **접속 IP · 브라우저는 로그인 이력에서 붙인다.** 못 찾으면 **빈 칸**이며 그것이 정직하다 — 다른 로그인의 IP 를 끌어다 채우면 *어디서 들어왔나* 에 틀린 답을 하게 된다.
- 세션 ID 는 표시만 8자로 자르고 **실행이 보내는 값은 온전한 값**이다.

### 4-1e. 디테일 탭 5 — 유효 권한 (**조회 전용** · 진단)

| 컬럼 | 라벨 키 | DB 컬럼 | **필드명** | 입력방식 | 폭 | 편집 | 설명 | 검증 | 값 자료형 | 그리드 타입 | 에디터 | 렌더러 | 뷰어 | 신규 필수 | 신규 편집 | 조회 필수 | 조회 편집 | 값 원천 | 생성·갱신 | 참조·계산식 | 저장 방식 | 추가 속성 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 액션 4열 | `ACTION_LABEL[액션]` | **파생** | **`act.{액션}`** — 액션마다 한 열 | 배지 | 76 | 읽기전용 | 액션 4열 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | badge | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 대상/조회 조건·소유 원천 변경 후 조회 시 | 파생; 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다.; 액션 4열 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 비저장 투영(원천의 저장 결과/집계 참조) | — |
| 메뉴 | `MSG.USER_EFF_COL_MENU` | **파생** — 메뉴 경로 | `menuPath` | 텍스트 | 300 | 읽기전용 | 메뉴 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 대상/조회 조건·소유 원천 변경 후 조회 시 | 파생 — 메뉴 경로; 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다.; 메뉴 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 비저장 투영(원천의 저장 결과/집계 참조) | {"pinned":"left","sortable":true,"emphasis":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#menuPath.emphasis","badge":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#menuPath.badge"} |
| 출처 | `MSG.USER_EFF_COL_VIA` | **파생** | `via` | 텍스트 | 180 | 읽기전용 | 출처 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/파생 | 참조 행 값 변경/재조회 시 | (row) => row.via === VIA_PERSONAL ? label(MSG.USER_EFF_VIA_PERSONAL) : row.via !== null && row.via !== '' ? row.via : label(MSG.USER_EFF_VIA_NONE); sources/frontend/src/modules/system/features/user/components/UserDetailTabs.tsx#via.derive | 비저장 화면 파생값 | {"sortable":true,"derive":"sources/frontend/src/modules/system/features/user//components/UserDetailTabs.tsx#via.derive"} |
| 상태 | `MSG.USER_EFF_COL_STATE` | **파생**(§6-3) | `grantState` | 배지 | 90 | 읽기전용 | 상태 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | badge | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 자동 계산/집계 | 대상/조회 조건·소유 원천 변경 후 조회 시 | 파생(§6-3); 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다.; 상태 항목. 표시/편집 동작은 이 영역의 작업 설명과 아래 편집 조건을 따른다. | 비저장 투영(원천의 저장 결과/집계 참조) | — |
| 시작일 | `MSG.USER_AUTH_COL_FROM` | `VALID_FROM_DT` | `validFromDt` | 날짜 | 130 | 읽기전용 | 시작일 항목. 저장 또는 조회 근거: VALID_FROM_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | VALID_FROM_DT | 소유 정본의 저장값 | {"sortable":true} |
| 종료일 | `MSG.USER_AUTH_COL_TO` | `VALID_TO_DT` | `validToDt` | 날짜 | 130 | 읽기전용 | 종료일 항목. 저장 또는 조회 근거: VALID_TO_DT. | 5  ·  저장  ·  화면+서버  ·  `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과  ·  `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` / 저장(수정)  ·  `SYS_USER`  ·  UPDATE  ·  `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** / `EXPIRED` 기간 만료  ·  사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖**  ·  warn | string | date | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | VALID_TO_DT | 소유 정본의 저장값 | {"sortable":true} |
| 부여 사유 | `MSG.USER_EFF_COL_REASON` | `GRANT_RSN` | `grantRsn` | 텍스트 | 240 | 읽기전용 | 부여 사유 항목. 저장 또는 조회 근거: GRANT_RSN. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | GRANT_RSN | 소유 정본의 저장값 | {"fill":true} |
| (숨김) 메뉴코드 | `MSG.USER_AUTH_COL_MENU` | `MENU_CD` | `menuCd` | 텍스트 | 160 | 읽기전용 | (숨김) 메뉴코드 항목. 저장 또는 조회 근거: MENU_CD. | 직접 입력하지 않는다. 조회 범위·권한을 적용한 값만 표시한다. | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | DB 저장값 조회 | 조회/대상 변경 시 원천에서 갱신 | MENU_CD | 소유 정본의 저장값 | {"visible":false} |
| (숨김) 역할ID | `MSG.USER_ROLE_COL_ROLE` | `ROLE_ID` | `roleId` | 텍스트 | 140 | 읽기전용 | (숨김) 역할ID 항목. 저장 또는 조회 근거: ROLE_ID. | 10  ·  역할 저장  ·  화면  ·  같은 그리드 안에 `ROLE_ID` 중복 금지  ·  `MSG.USER_ROLE_DUPLICATE` | string | text | 없음 | 없음 | 없음 | 해당 없음(신규 미제공) | 해당 없음(신규 미제공) | 입력 대상 아님 | 불가 | 서버 생성/저장 | 소유 명령 저장 성공/해당 상태 전이 시 | ROLE_ID; 소유 정본 | 소유 원천에 저장, 화면 직접 입력 불가 | — |

- **액션마다 한 열이다** — 필드가 `act.READ` · `act.SAVE` · `act.EXPORT` · `act.EXECUTE` 이고
  **액션이 늘면 열이 는다.** **`dynamic` 이 아니다** — 열 집합이 데이터에서 오지 않고 상수라서다.
  `derive` 로 세 값을 만들고 `display` 로 글자를 고른다 — 어휘는
  [standard/frontend/declarations.md](../../../aidd-guide/standard/frontend/declarations.md) 가 갖는다.
  **폭을 정하지 않으면 재현이 갈린다** — 76 이다.
- 순서는 **액션 4열이 먼저**이고 그다음이 메뉴다.

- **메뉴 하나가 여러 행일 수 있다** — 경유가 둘이면 행이 둘이다. 합쳐서 한 행으로 내면 액션 합집합은 맞아도 *어느 부여를 건드려야 하는지* 를 잃는다.
- 액션 4열은 세 값이다: `●` 준다 · `·` 안 준다 · `—` **정의되지 않았다**(부여할 칸 자체가 없다).
- `effective` 는 **서버가 낸다.** 화면 전용 계산을 두면 실제 판정과 갈리고, 갈리는 순간 이 탭은 진단이 아니라 또 하나의 거짓말이 된다.
- 그리드 헤더에 `표시 범위` 셀렉트 — `가진 것만` / `전체 메뉴`. 조회조건 칸이 아닌 이유는 여기서 고르는 것이 *거르기* 가 아니라 **무엇을 행으로 낼지**(부여를 세나 메뉴를 세나)이기 때문이다.
- 조회 결과에 `effective` 인 행이 하나도 없으면 배너: *유효 권한이 공집합입니다 — 이 계정은 다음 로그인부터 차단됩니다.* **조회 전에는 내지 않는다.**

### 4-2. 편집 가능 판단 — 조건부

| 범위 | 대상 | 조건 → 편집 가능 |
|---|---|---|
| 셀 | 마스터 `USE_YN` | **자기 계정(`USER_ID` = 로그인한 사람)이면 불가.** 감추지 않고 보이되 막고 사유를 붙인다 — `MSG.USER_SELF_DISABLE` |
| 셀 | 마스터 `USER_ID` | 신규 행에서만 |
| 셀 | 보유 역할 `ROLE_ID` · 접속 IP `IP_CIDR` | 신규 행에서만 |
| 그리드 | 디테일 다섯 전부 | 마스터에 **저장된** 행이 선택되어 있을 때만. 미저장 신규 행이면 잠긴다 |

**컬럼을 누가 거는가로 가른 것이 규칙의 집행 지점이다.** 관리자 축 둘(`USE_YN` · 사용 기간)만 셀이 열리고, 시스템 축 셋(`LOCK_YN` · `DORMANT_YN` · `LOGIN_FAIL_CNT`)은 읽기 전용이며 §5-1 의 버튼이 **자기 컬럼만** 쓴다. 셀 편집을 막으면 *셀 하나씩 고치다 넷이 뒤섞이는* 경로가 아예 열리지 않는다.

### 4-3. 값 변경 후 연쇄 · 재계산

- 없음. 상태는 서버가 계산해 내려주는 값이고 화면이 다시 세지 않는다.

### 4-4. 팝업 · 모달 선택 결과 매핑

- 없음. 역할 선택은 팝업이 아니라 셀 셀렉트다.

### 4-5. 행 추가 기본값

- **마스터**: `USE_YN='Y'` · `LOCALE_CD='ko'` · `LOCK_YN='N'` · `DORMANT_YN='N'` · `LOGIN_FAIL_CNT=0` · `PWD_INIT_YN='N'` · 상태 `NORMAL`. 기간 둘은 비운다. **로그인 ID · 이름 · 이메일은 사람이 채운다.**
- **보유 역할**: 기간 둘 비움(= 무기한 유효) · 상태 `ACTIVE`.
- **접속 IP**: `USE_YN='Y'` · 기간 둘 비움(= 제한 없음).

### 4-6. 비노출 저장 컬럼

- **없음.** 오히려 반대다 — **시스템 축 셋(`LOCK_YN` · `DORMANT_YN` · `LOGIN_FAIL_CNT`)은 저장 요청에 싣지 않는다.** 서버의 저장 DTO 에도 그 필드가 없어 보내도 받을 자리가 없다.
- 삭제 행은 **키만** 보낸다(보유 역할 `ROLE_ID` · 접속 IP `IP_CIDR`).

---

## 5. 버튼 · 액션

### 5-1. 마스터

| 버튼 | 라벨 키 | 액션 | 활성 조건 | 동작 | 확인 문구 |
|---|---|---|---|---|---|
| 저장 | (공통) | `S` | 변경 있음 | 추가 · 수정 일괄 저장. **신규 행은 임시 비밀번호를 발급받아 모달로 보여준다** | 공통 + §5-3 경고 |
| 잠금 해제 | `MSG.USER_ACTION_UNLOCK` | **`S`** | 행 선택 | `LOCK_YN` · `LOCK_DTM` · `LOGIN_FAIL_CNT` 만 쓴다 | 공통 |
| 휴면 해제 | `MSG.USER_ACTION_DORMANT` | **`S`** | 행 선택 | `DORMANT_YN` 만 쓴다. **잠금은 같이 풀리지 않는다** | 공통 |
| 비밀번호 초기화 | `MSG.USER_ACTION_RESET_PWD` | **`S`** | 행 선택 | `PWD` · `PWD_CHG_DTM` · `PWD_INIT_YN`. **잠금을 풀지 않는다.** 발급값을 모달로 보여준다 | 공통 |

- **셋 다 액션이 `SAVE` 다.** 기준은 *버튼이 위험해 보이는가* 가 아니라 **우리 테이블의 컬럼을 쓰는가**이고, 셋 다 `SYS_USER` 를 쓴다. 그래서 경로도 `save*` 다.
- **셋 다 대상을 좁히지 않는다.** 잠기지 않은 계정에 잠금 해제를 눌러도 실패가 아니라 *이미 이뤄진 상태* 이며, 대상을 좁히면 고른 행과 실행되는 행이 갈린다.
- 잠금 해제가 **비밀번호를 바꾸지 않고**, 비밀번호 초기화가 **잠금을 풀지 않는다** — 초기화가 잠금을 풀면 *실패 5회 → 초기화 → 다시 5회* 가 무한 반복된다.
- 엑셀 다운로드: **사용** — `EXPORT_YN='Y'`. 부를 API 가 없다(프론트가 만든다).
- 엑셀 업로드: 미사용.
- 행 삭제: **비허용.** 삭제 컬럼 자체가 없고 서버도 `D` 를 거부한다.
- 진행률 창: 없음.

### 5-2. 디테일

| 탭 | 버튼 | 액션 | 활성 조건 | 확인 문구 키 |
|---|---|---|---|---|
| 보유 역할 | 저장 · 행 추가 · 행 삭제 | `S` | 마스터의 저장된 행 선택 | 공통 + §5-3 |
| 개인 예외 | (조회 · 엑셀만) | `R` `E` | — | — |
| 접속 IP | 저장 · 행 추가 · 행 삭제 | `S` | 〃 | 공통 + §5-3 |
| 활성 세션 | **강제 종료** | **`X`** | 행 선택 **AND 자기 계정이 아님** | `MSG.USER_SESSION_KILL_CONFIRM` |
| 유효 권한 | (조회 · 엑셀만) + `표시 범위` 셀렉트 | `R` `E` | — | — |

- **강제 종료가 이 화면의 유일한 `EXECUTE` 다.** 세션 스토어를 비울 뿐 `SYS_USER` 를 쓰지 않는다.
- 자기 계정을 보고 있으면 버튼이 **비활성**이고 사유를 말한다 — `MSG.USER_SESSION_SELF`. *선택 0건* 과 구별되어야 하므로 힌트가 필요하다.
- 확인 문구가 **계정은 바뀌지 않는다**는 것을 말한다 — 그러지 않으면 이 버튼이 계정을 잠그는 것으로 읽힌다.
- 고른 수와 끊은 수가 **다를 수 있고 다른 것이 사실이다** — 이미 만료된 세션은 조용히 넘어간다. 결과 문구가 실제 건수를 말한다.

### 5-3. 저장 확인 **앞**에 서는 경고

건수는 *얼마나* 를 말할 뿐이라 **무엇이 일어나는지**를 말하지 못한다.

| 트리거 | 문구 키 | 무엇을 말하나 |
|---|---|---|
| 마스터 — 신규 행 있음 | `MSG.USER_WARN_NEW_PASSWORD` | 임시 비밀번호가 발급되며 **다시 볼 수 없다** |
| 마스터 — `USE_YN` 을 `Y→N` 으로 바꾼 행 있음 | `MSG.USER_WARN_DISABLE` | **다음 로그인부터** 막힌다. 지금 세션은 살아 있다 |
| 보유 역할 — 삭제했거나 **기간을 줄인** 행 있음 | `MSG.USER_ROLE_WARN_REVOKE` | 회수는 다음 로그인부터 반영된다 |
| 접속 IP — **첫 대역**을 등록함 | `MSG.USER_IP_WARN_FIRST` | 그 순간부터 **나머지 모든 IP 가 막힌다** |

- 역할 회수 판정은 **오늘이 언제인지 묻지 않는다.** *새 종료일이 오늘보다 이른가* 로 판정하면 브라우저 시계에 의존하게 되므로(업무 날짜는 서버가 정한다), 묻는 것을 **기간을 줄였는가**(편집 전후 두 값의 비교)로 바꿨다. 이미 만료된 부여는 세지 않는다.
- IP 첫 대역 판정은 **삭제 행을 뺀** 기존 건수로 센다.

---

## 6. 규칙 — 저장 · 실행

### 6-1. 검증

| # | 시점 | 위치 | 검사 (컬럼 · 값 기준) | 위반 시 메시지 |
|---|---|---|---|---|
| 1 | 저장 | 화면 | 같은 요청 안에 `USER_ID` 중복 금지 | `MSG.USER_DUPLICATE` |
| 2 | 저장(신규) | 서버 | 같은 요청 안 중복 + `SYS_USER` 에 이미 있는 `USER_ID` 금지 | `MsgKey.ERR_USER_ADMIN_DUPLICATE` |
| 3 | 저장 | 화면+서버 | `USER_NM` 필수 | `MsgKey.ERR_USER_ADMIN_NAME_REQUIRED` |
| 4 | 저장 | 화면+서버 | **`EMAIL` 필수** — 임시 비밀번호의 발송처다. 컬럼 정의는 `NULL` 을 허용하지만 이 화면이 만드는 계정에는 필수다 | `MsgKey.ERR_USER_ADMIN_EMAIL_REQUIRED` |
| 5 | 저장 | 화면+서버 | `VALID_FROM_DT` ≤ `VALID_TO_DT`. **양끝 포함**이라 같은 날은 통과 | `MSG.USER_PERIOD_REVERSED` / `MsgKey.ERR_USER_ADMIN_PERIOD` |
| 6 | 저장 | 화면+서버 | **자기 계정을 `USE_YN='N'` 으로 바꿀 수 없다** | `MSG.USER_SELF_DISABLE` / `MsgKey.ERR_USER_ADMIN_SELF_DISABLE` |
| 7 | 저장 | 서버 | **행 삭제(`D`)를 받지 않는다** — 이력 테이블이 `USER_ID` 를 값으로 갖고 있어 행을 지우면 지난 이력의 주인이 사라진다 | `MsgKey.ERR_USER_ADMIN_NO_DELETE` |
| 8 | 실행 셋 | 서버 | 대상 계정이 없으면 거부 | `MsgKey.ERR_USER_ADMIN_NOT_FOUND` |
| 9 | 비밀번호 초기화 | 서버 | `adminResetEnabled` 가 꺼져 있으면 거부 | `MsgKey.ERR_USER_ADMIN_RESET_DISABLED` |
| 10 | 역할 저장 | 화면 | 같은 그리드 안에 `ROLE_ID` 중복 금지 | `MSG.USER_ROLE_DUPLICATE` |
| 11 | IP 저장 | 화면 | `IP_CIDR` 이 IP 또는 CIDR 형식(빈 값은 통과 — 아직 안 쓴 칸이다) | `MSG.USER_IP_MALFORMED` |
| 12 | IP 저장 | 화면 | 같은 그리드 안에 `IP_CIDR` 중복 금지 | `MSG.USER_IP_DUPLICATE` |

**화면이 막는 것은 UX 이지 방어가 아니다.** 요청 본문은 조작할 수 있고 화면 없이 부르는 경로가 늘 있다 — 그래서 서버가 같은 것을 다시 본다. IPv6 는 화면이 정규식으로 온전히 맞추지 않으며 최종 판정은 서버의 리터럴 파서가 한다.

### 6-2. 영향 테이블

| 트리거 | 테이블 | 작업 | 조건 · 내용 |
|---|---|---|---|
| 저장(신규) | `SYS_USER` | INSERT | 임시 비밀번호 해시 + `PWD_INIT_YN='Y'` · `PWD_CHG_DTM` |
| 저장(수정) | `SYS_USER` | UPDATE | `USER_NM` · `EMAIL` · `USE_YN` · `VALID_FROM_DT` · `VALID_TO_DT` · `LOCALE_CD` **만** |
| 잠금 해제 | `SYS_USER` | UPDATE | `LOCK_YN='N'` · `LOCK_DTM=NULL` · `LOGIN_FAIL_CNT=0` |
| 휴면 해제 | `SYS_USER` | UPDATE | `DORMANT_YN='N'` |
| 비밀번호 초기화 | `SYS_USER` | UPDATE | `PWD` · `PWD_CHG_DTM` · `PWD_INIT_YN='Y'` |
| 비밀번호 초기화 | `SYS_USER_PWD_HIST` | INSERT | **바꾸기 전에** 지금 값을 옮긴다 — 사용자가 옛 비밀번호를 그대로 다시 쓸 수 없게 한다 |
| 역할 저장 | `SYS_USER_ROLE` | INSERT · UPDATE · DELETE | 부여 추가 · 기간 변경 · 행 삭제 |
| IP 저장 | `SYS_USER_IP_ALLOW` | INSERT · UPDATE · DELETE | 〃 |
| 강제 종료 | **세션 스토어** | DELETE | `SYS_USER` 를 쓰지 않는다 |

- 저장은 **한 트랜잭션**이다.
- **신규 계정은 비밀번호 이력을 남기지 않는다.** 옮길 *지금 값* 이 없으며, 방금 발급한 임시 비밀번호를 이력에 넣으면 사용자가 첫 변경에서 재사용 금지에 걸린다.
- **세션 · 캐시 부수효과**: 마스터 저장은 세션을 건드리지 않는다 — `USE_YN` 은 유효 권한의 입력이 아니라 **로그인 체인**의 것이고, 이미 로그인한 세션은 다음 로그인부터 막힌다. **역할 저장은** 그 사람의 세션에 권한 재계산을 예약한다. **IP 저장은** 세션을 건드리지 않는다(인가가 아니라 인증의 검사다).

### 6-3. 상태 — 파생 둘

**둘 다 컬럼이 아니다.** 조회 시 계산하므로 배치가 실패해도 화면과 판정이 갈리지 않는다.

**계정 상태** — 접근 제어 체인 순서로 **먼저 걸린 것 하나**만 낸다. 넷이 동시에 걸릴 수 있으며 원본 넷은 각각 컬럼으로 남아 있다(기본 숨김).

| 값 | 조건 (앞의 것을 통과했다는 전제가 조건 안에 들어 있다) | 배지 |
|---|---|---|
| `DISABLED` 미사용 | `USE_YN='N'` | muted |
| `EXPIRED` 기간 만료 | 사용 중 AND 오늘이 `VALID_FROM_DT` ~ `VALID_TO_DT` **밖** | warn |
| `LOCKED` 잠김 | 사용 중 AND 기간 안 AND `LOCK_YN='Y'` | err |
| `DORMANT` 휴면 | 사용 중 AND 기간 안 AND 잠기지 않음 AND `DORMANT_YN='Y'` | warn |
| `NORMAL` 정상 | 넷 다 통과 | ok |

- **표시와 조건이 같은 순서를 본다.** 조건에서 앞의 것을 빼지 않으면 *미사용이면서 잠긴* 계정이 두 값에 다 걸려, 목록에는 `미사용` 인데 `잠김` 으로 조회하면 나온다.
- **다섯을 다 고른 것과 조건이 없는 것이 같다.** 특수 분기를 두지 않는다.
- **시간 경과 자동 잠금 해제를 보지 않는다.** 그것은 로그인 시점의 판정이고, 목록이 그것까지 계산하면 조회조건의 SQL 과 표시가 갈린다. 확실히 정리하는 수단은 [잠금 해제] 버튼이다.
- 오늘은 **서버의 업무 시간대**로 정한다(`app.business-timezone`). `CURRENT_DATE` 를 쓰지 않는 이유는 그것이 DB 서버의 시간대이기 때문이다.

**부여 상태**(보유 역할 · 개인 예외 · 유효 권한 공통) — `SCHEDULED` 예정 / `ACTIVE` 유효 / `EXPIRED` 만료. **기간에서만** 파생하며 역할 자체의 `USE_YN` 은 보지 않는다. 그래서 *유효인데 아무 권한도 주지 않는* 행이 있을 수 있고, 그 사실은 `역할 사용` 컬럼과 상태 옆 경고 배지가 말한다. 기간이 비면(`null`) 무기한이다.

### 6-4. 임시 비밀번호 모달

- **저장 · 비밀번호 초기화의 응답에만 있는 값**이다. DB 에는 해시가 들어가므로 재조회로 다시 얻을 수 없다.
- 수정만 한 저장에서는 빈 배열이고 **모달이 뜨지 않는다.**
- 창은 바깥을 눌러 닫히지 않는다. 취소 버튼이 없다 — 둘을 두면 *취소하면 발급이 취소되나* 가 된다.
- **엑셀 · 전체 복사를 두지 않는다** — 파일로 떨어지면 그 파일이 값의 수명을 넘겨 살아남는다. 칸 하나 복사는 있다.
- 값은 고정폭 글꼴로 그린다 — 사람이 옮겨 적는 값이다.
- 방어는 **값의 수명**이다: `PWD_INIT_YN='Y'` 라 다음 로그인이 곧바로 강제 변경으로 가고, `tempValidHours` 가 지나면 그 값으로는 로그인할 수 없다.
- 여러 건을 한 번에 발급할 수 있어 **누구의 것인지**를 표가 말한다(로그인 ID · 이름 · 값).

---

## 7. API

| 용도 | `endpoints.ts` 키 | 경로 | 메서드 | 액션 | 페이징 |
|---|---|---|---|---|---|
| 목록 | `getList` | `/service/system/user/getList` | POST | R | offset |
| 저장 | `save` | `/service/system/user/save` | POST | S | — |
| 잠금 해제 | `saveUnlock` | `/service/system/user/saveUnlock` | POST | **S** | — |
| 휴면 해제 | `saveDormantRelease` | `/service/system/user/saveDormantRelease` | POST | **S** | — |
| 비밀번호 초기화 | `savePasswordReset` | `/service/system/user/savePasswordReset` | POST | **S** | — |
| 탭1 목록 | `getRoleList` | `/service/system/user/getRoleList` | POST | R | 전건 |
| 탭1 저장 | `saveRole` | `/service/system/user/saveRole` | POST | S | — |
| 역할 후보 | `getRoleCandidates` | `/service/system/user/getRoleCandidates` | POST | R | 전건 |
| 탭2 목록 | `getUserMenuAuth` | `/service/system/user/getUserMenuAuth` | POST | R | 전건 |
| 탭3 목록 | `getIpList` | `/service/system/user/getIpList` | POST | R | 전건 |
| 탭3 저장 | `saveIp` | `/service/system/user/saveIp` | POST | S | — |
| 탭4 목록 | `getSessionList` | `/service/system/user/getSessionList` | POST | R | 전건 |
| 탭4 강제 종료 | `killSession` | `/service/system/user/killSession` | POST | **X** | — |
| 탭5 목록 | `getEffectiveAuthList` | `/service/system/user/getEffectiveAuthList` | POST | R | 전건 |

- **`exportExcel` 이 없다.** 액션 열은 `R S E X` 인데 `E` 에 대응하는 경로가 없다 — 엑셀은 프론트가 만들고 `EXPORT` 는 `SYS_MENU.EXPORT_YN` 이 선언한다.
- **역할 후보를 `ROLE_MGMT` 의 경로로 부르지 않는다.** 그 요청은 **역할 관리의 `READ`** 를 요구하게 되고, 그러면 사용자 관리만 가진 사람이 역할을 부여할 수 없다. 조회조건의 `보유 역할` 과 보유 역할 탭이 **같은 목록**을 본다.
- **개인 예외는 `USER_AUTH_MGMT` 와 경로를 공유하지 않는다.** 반환 형태가 다르다 — 저쪽은 트리 체크 상태를 내야 해 메뉴 전건이 실리고, 여기는 부여된 행만 나오는 납작한 목록이다.

---

## 8. 시드 · 데이터 전제

- 코드그룹: **없음.** 계정 상태와 언어 둘 다 공통코드가 아니다 — 상태는 서버가 계산하는 파생값이고, 언어는 값에 따라 프로세스가 갈리므로 소스가 값을 가져야 한다(번들 · 메시지 · `src/locales` 를 함께 건드리는 일이라 코드 행 하나로 늘지 않는다).
- 메시지 키: 화면 `MSG.USER_*` · 서버 `MsgKey.ERR_USER_ADMIN_*` 여덟.
- 메뉴 시드: `menu-tree.yaml` — `code: USER_MGMT` · `parent: USER` · `url: /admin/system/user` · `export: true`.
- 초기 데이터 전제: 역할을 부여하려면 `SYS_ROLE` 에 역할이 먼저 있어야 한다.
- 설정(`AuthPasswordProps`): `minLength` · `requireCharsetKinds` · `reuseBlockCount` · **`adminResetEnabled`**(꺼지면 비밀번호 초기화가 거부된다) · `tempValidHours`.

---

## 9. 테스트 케이스

> **요건이 지켜졌는지 묻는 표다.** 위의 절들이 *무엇을* 정했다면 여기는 *그것이 지켜졌는가* 다.
> **되면 안 되는 것**을 함께 담는다 — 그것이 빠지면 케이스가 그림 검사로 줄어든다.
>
> **목업(`mockup.html`)은 이 표와 위의 절들로부터 생성되는 파생물이다** — 케이스를 목업에서
> 고치지 않는다. 고칠 곳은 여기고, 고친 뒤 `/mockup` 이 목업을 다시 만든다.

### 조회

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-01` | 메뉴로 들어온다 | 목록이 비어 있다 — **자동 조회가 돌지 않는다** |
| `U-02` | 조건을 하나도 넣지 않고 조회 | 전체가 나온다. 끈 계정·휴면 계정도 함께 나온다 |
| `U-03` | `계정 상태` 에 다섯을 전부 고르고 조회 | 조건이 없는 것과 같은 결과다 |
| `U-04` | `USE_YN='N'` 이면서 `LOCK_YN='Y'` 인 계정을 *잠김* 으로 조회 | **나오지 않는다.** 목록의 상태가 *미사용* 이므로 조회도 그것과 같아야 한다 **(되면 안 되는 것)** |
| `U-05` | `상태` 컬럼 헤더를 누른다 | 정렬되지 않는다 **(되면 안 되는 것)** |
| `U-06` | `보유 역할` 에 역할을 고르고 조회 | 기간이 **만료된 부여를 가진 사람도** 나온다 |
| `U-07` | `역할 만료 예정` 을 *30일 이내* 로 조회 | 어제 만료된 부여만 가진 사람은 **나오지 않는다** — 범위는 오늘부터다 |
| `U-08` | `[⚙]` 로 `잠금`·`실패 횟수` 를 켠다 | 재조회 없이 값이 보인다 — 응답에 이미 있다 |
| `U-09` | 동명이인이 페이지 경계에 걸리게 조회하고 2페이지로 넘긴다 | 같은 행이 두 장에 나오지 않는다 |
| `U-10` | 응답 본문을 확인한다 | `PWD` 가 어떤 형태로도 실려 있지 않다 **(되면 안 되는 것)** |

### 편집과 저장

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-20` | 기존 행의 `로그인 ID` 셀을 두 번 누른다 | 편집 모드로 들어가지 않는다 **(되면 안 되는 것)** |
| `U-21` | `잠금`·`휴면`·`실패 횟수` 셀을 두 번 누른다 | 셋 다 열리지 않는다 **(되면 안 되는 것)** |
| `U-22` | 자기 계정 행의 `사용 여부` 를 끈다 | 셀이 열리지 않고 *자기 계정은 사용 중지할 수 없습니다.* **(되면 안 되는 것)** |
| `U-23` | U-22 를 화면 없이 API 로 직접 호출한다 | 서버가 거부한다 — `err.user-admin.self-disable` **(되면 안 되는 것)** |
| `U-24` | 신규 행 둘에 같은 로그인 ID 를 넣고 저장 | 저장 전에 화면이 막는다 — *같은 로그인 ID 가 두 번 있습니다.* |
| `U-25` | 이미 있는(끈) 계정의 ID 로 신규 행을 만들고 저장 | 서버가 거부한다 — *이미 있는 로그인 ID 입니다.* |
| `U-26` | 이메일을 비우고 저장 | 거부된다 — **DB 는 NULL 을 허용하지만 이 화면은 필수다** |
| `U-27` | 경계 — 시작일과 종료일을 **같은 날**로 넣는다 | 통과한다(양끝 포함) |
| `U-28` | 시작일을 종료일보다 하루 뒤로 넣는다 | 거부된다 **(되면 안 되는 것)** |
| `U-29` | 마스터에 삭제 컬럼을 찾는다 | 없다. API 로 `D` 를 보내면 서버가 거부한다 **(되면 안 되는 것)** |
| `U-30` | 신규 1건 + 수정 1건을 함께 저장 | 한 트랜잭션이며, 모달에는 **신규 1건만** 나온다 |
| `U-31` | 수정만 하고 저장 | 모달이 뜨지 않는다 |
| `U-32` | 저장 요청 본문을 확인한다 | `LOCK_YN`·`DORMANT_YN`·`LOGIN_FAIL_CNT` 가 실리지 않는다 |

### 실행 버튼 셋

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-40` | 잠기지 **않은** 계정에 `[잠금 해제]` | 성공한다 — 실패 모달이 뜨지 않는다 |
| `U-41` | 잠긴 계정에 `[비밀번호 초기화]` 후 다시 조회 | `잠금` 이 여전히 `Y` 다 — **초기화는 잠금을 풀지 않는다** |
| `U-42` | 휴면+잠김 계정에 `[휴면 해제]` | 휴면만 풀리고 잠금은 남는다. 상태는 *잠김* 이 된다 |
| `U-43` | `[비밀번호 초기화]` 를 두 번 하고 첫 값으로 로그인 | 실패한다 **(되면 안 되는 것)** |
| `U-44` | 초기화된 계정으로 로그인 | 곧바로 비밀번호 변경 화면으로 간다(`PWD_INIT_YN='Y'`) |
| `U-45` | 초기화 후 사용자가 **직전 비밀번호**로 변경을 시도 | 재사용 금지에 걸린다 — 이력이 옮겨졌기 때문이다 |
| `U-46` | 새로 만든 계정이 **첫 변경**에서 임의의 값으로 변경 | 통과한다 — 신규는 이력을 남기지 않는다 |
| `U-47` | `adminResetEnabled` 를 끄고 `[비밀번호 초기화]` | 거부된다 — *임시 비밀번호 발급이 꺼져 있습니다.* |
| `U-48` | 사용 여부를 끄고 저장한 직후, 그 사람이 **이미 접속 중** | 지금 세션은 살아 있다. 막으려면 `[강제 종료]` 가 따로 필요하다 |
| `U-49` | 사용 이력에서 초기화 요청을 찾는다 | 발급된 **값이 남아 있지 않다** — 이력은 요청 본문을 기록하고 값은 응답에 있다 |

### 마스터-디테일 연동

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-60` | 진입 직후 탭을 본다 | *사용자를 선택하세요* |
| `U-61` | `[행 추가]` 로 만든 **미저장** 행을 고른다 | *저장 후 편집할 수 있습니다* — 조회도 편집도 되지 않는다 **(되면 안 되는 것)** |
| `U-62` | 보유 역할을 고치던 중 다른 사용자 행을 누른다 | 폐기 확인이 뜬다. **취소하면 선택이 일어나지 않는다** |
| `U-63` | U-62 에서 취소한 뒤 화면을 본다 | 마스터 선택과 탭의 주인이 **같다** — 갈리지 않는다 |
| `U-64` | 사용자를 고른 상태에서 마스터 `[조회]` | 탭이 *사용자를 선택하세요* 로 돌아간다. **자동 선택하지 않는다** |
| `U-65` | 보유 역할을 저장한 뒤 [유효 권한] 탭으로 간다 | 다시 조회한다 — 저장 전의 답을 보여주지 않는다 **(되면 안 되는 것)** |
| `U-66` | 탭을 처음 연다 | 그때 조회한다(미리 다섯을 부르지 않는다) |

### 디테일 탭 다섯

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-70` | 역할 콤보를 연다 | **꺼진 역할도** 후보에 있다. 글자는 `역할명 (역할ID)` |
| `U-71` | 꺼진 역할을 부여하고 저장 | 저장된다. 상태는 *유효* 이고 **경고 배지가 붙는다** |
| `U-72` | 기존 부여의 `역할` 셀을 두 번 누른다 | 열리지 않는다 — PK 의 절반이다 **(되면 안 되는 것)** |
| `U-73` | 무기한 부여에 **종료일을 넣고** 저장 | 회수 경고가 뜬다 — 오늘보다 뒤의 날짜여도 뜬다 |
| `U-74` | **이미 만료된** 부여를 삭제하고 저장 | 회수 경고가 뜨지 않는다 |
| `U-75` | KST 자정 직후에 U-73 을 한다 | 결과가 같다 — **브라우저 시계를 보지 않는다** |
| `U-76` | [개인 예외] 탭에서 저장 버튼을 찾는다 | 없다. 부여는 `/user-auth` 에서 한다 **(되면 안 되는 것)** |
| `U-77` | IP 가 하나도 없는 계정에 첫 대역을 넣고 저장 | *첫 대역을 등록하면 그 순간부터 나머지 모든 IP 가 막힙니다.* |
| `U-78` | 경계 — 기존 1건을 지우고 새 1건을 넣어 저장 | 첫 대역 경고가 **뜬다** — 삭제 행을 뺀 결과로 센다 |
| `U-79` | `1.2.3.4/64` 를 넣는다 | 거부된다 — 상한이 계열에서 나온다 **(되면 안 되는 것)** |
| `U-80` | 새 IP 행을 만들고 아직 아무것도 입력하지 않는다 | 붉은 칸이 뜨지 않는다 — 빈 값은 통과이고 필수 표시가 그 일을 한다 |
| `U-81` | 자기 계정의 [활성 세션] 을 연다 | `[강제 종료]` 가 비활성이고 **사유가 표시된다** **(되면 안 되는 것)** |
| `U-82` | 남의 세션 2건을 고르고 종료. 그중 1건은 이미 만료 | 오류가 아니라 **1건 종료**로 끝난다 |
| `U-83` | 로그인 이력이 파기된 오래된 세션을 본다 | `접속 IP`·`브라우저` 가 **비어 있다** — 다른 로그인의 값을 끌어오지 않는다 **(되면 안 되는 것)** |
| `U-84` | [유효 권한] 을 열고 `표시 범위` 를 *전체 메뉴* 로 바꾼다 | 다시 조회하고 **못 가진 메뉴까지** 나온다 |
| `U-85` | 같은 메뉴를 역할과 개인 예외 둘로 가진 사람을 본다 | **행이 둘**이다. 합쳐지지 않는다 **(되면 안 되는 것)** |
| `U-86` | 액션이 `R S` 만 정의된 메뉴를 본다 | `E`·`X` 칸이 `—` 다 — *안 줬다* 와 구별된다 |
| `U-87` | 탭을 열자마자(조회 전) 배너를 본다 | 공집합 배너가 **번쩍이지 않는다** **(되면 안 되는 것)** |
| `U-88` | 조회가 **실패**했다 | 공집합 배너가 뜨지 않는다 — 못 물어본 것을 두고 *권한이 없습니다* 라고 하지 않는다 |

### 권한과 엑셀

| # | 조작 · 상황 | 기대 |
|---|---|---|
| `U-90` | `R` 만 가진 사용자로 들어온다 | 저장·행 추가·실행 버튼 셋이 **없다** |
| `U-91` | `R S` 는 있고 `X` 가 없는 사용자로 [활성 세션] 을 연다 | `[강제 종료]` 가 없다 — 잠금 해제 셋은 보인다 |
| `U-92` | 사용자 관리 권한만 가진 사람이 역할 콤보를 연다 | 후보가 **정상적으로 나온다** — 역할 관리의 `READ` 를 요구하지 않는다 |
| `U-93` | `[⤓]` 로 전체 다운로드 | **지금 보이는 컬럼만** 나간다. 상태는 **코드가 아니라 표시 글자**로 나간다 |
| `U-94` | 임시 비밀번호 창에서 엑셀 버튼을 찾는다 | 없다 **(되면 안 되는 것)** |

---

## 10. 미결 · 확인 필요

- **계정 상태 컬럼의 정렬** — 지금은 켤 수 없다(SQL 이 모르는 값이라 화이트리스트에 없고 켜면 400). 필요해지면 정렬 키를 따로 정의해야 한다. *현재 답은 "정렬 없음" 이며 이것이 결정이다.*
- **임시 비밀번호를 화면에 표시하는 것** — 알림(NOTI)이 서면 메일이 전달을 맡으므로 그때 뒤집는다. *지금은 표시가 결정이다.*

---

## 11. 변경 이력

| 일자 | 변경 | 커밋 |
|---|---|---|
| 2026-09-03 | 최초 작성 — 구현된 소스에서 역작성 | — |
| 2026-09-05 | 테스트 케이스 64건을 목업에서 이 문서로 옮겼다(§9) — 목업은 파생물이 된다 | — |
