---
id: LLM-KNW-SYS-003
title: "공통코드 — 그룹 · 코드 · 참조 코드"
category: knowledge/sys
type: operation
created: 2026-05-04
updated: 2026-09-03
status: active
sources: ["sources/backend/domain/src/main/java/com/aiagent/domain/code/entity/", "sources/frontend/src/shared/code/"]
tier: 3
tags: [knowledge, code, master-data]
---

# 공통코드 — 그룹 · 코드 · 참조 코드

> 표 넷: `SYS_CODE_GROUP` · `SYS_CODE` · `SYS_CODE_GRP_REF` · `SYS_CODE_REF`.
>
> **단계는 셋인데 셋째가 정의와 값 둘로 갈렸다.** 그 갈림이 이 문서의 절반이다.

## 1. 네 표의 관계 — 뒤의 둘이 **매트릭스**다

```
SYS_CODE_GROUP   그룹            ORD_STAT
  └ SYS_CODE     상세 코드        RCV · CFM · SHP · DON        ← 행
SYS_CODE_GRP_REF 참조 코드 정의   COLOR · SORT_HINT            ← 열
  └ SYS_CODE_REF 참조 값          (RCV, COLOR) = 'blue'        ← 셀
```

**부모가 둘이라 정의 없는 참조값이 구조적으로 생길 수 없다.**

## 2. `SYS_CODE_GROUP`

| 컬럼 | |
|---|---|
| `GRP_CD` | PK |
| `GRP_NM` · `GRP_DESC` | **메시지 키가 아니라 컬럼이다** — 관리 화면 전용 명칭 |
| `SYS_YN` | *소스가 이 그룹을 문자열로 참조하는가* — 삭제를 거부하는 근거 |
| `SORT_NO` | |

- **`GRP_NM` 은 예외다.** 표시 명칭의 단일 원본은 `SYS_MESSAGE` 인데, 이 값은 코드 관리
  화면 안에서만 보인다 — **운영자 화면까지 다국어로 열면 메시지 행이 관리 대상 데이터보다
  많아진다.** `SYS_CODE.DTL_NM` 이 그 반대편이다.
- **`SYS_YN` 에 변경 메서드가 없다.** 시드가 **선언**하고 저장 서비스가 **읽는다**(삭제
  거부). 화면만 막으면 요청 본문으로 뚫린다.

### `USE_YN` 이 여기에는 없다 — **이름이 같다고 같은 것이 아니다**

| | 읽는 쪽 |
|---|---|
| `SYS_CODE.USE_YN` | **있다** — 콤보 후보에서 뺀다. 이미 저장된 데이터는 그 값을 가지므로 **이름 조회는 계속 된다**: *새로 고르지는 못하지만 옛 행은 여전히 읽힌다* |
| `SYS_CODE_GROUP.USE_YN` | **없었다** — 그룹은 콤보의 후보가 아니라 **소스가 이름으로 지목하는 것**이라, 끄면 무슨 일이 일어나야 하는지 답이 없다 |

**그래서 지우는 쪽을 골랐다.** 안 쓰는 그룹은 지우거나 그냥 두면 된다 — 코드가 남아 있으면
삭제 게이트가 막는데 그것은 *정말 안 쓰면 코드도 지워라* 로 읽혀 자연스럽다.

## 3. `SYS_CODE`

| 컬럼 | |
|---|---|
| `GRP_CD` + `DTL_CD` | PK |
| **`DTL_NM`** | **코드명이 아니라 메시지 키다** — `code.{GRP_CD}.{DTL_CD}` |
| `DTL_DESC` · `SYS_YN` · `USE_YN` · `SORT_NO` | |

- **`DTL_NM` 은 사람이 정하지 않는다.** 값이 PK 에서 도출되는데도 컬럼으로 두는 이유는
  조회 쪽이 문자열을 조립하지 않고 **컬럼을 그대로 조인 키로 쓰기** 위해서다 — 조립이
  저장 시점 한 곳에만 있으면 **규칙이 바뀌어도 고칠 곳이 하나**다.
- **값(문구)은 `SYS_MESSAGE` 에 넣어야 한다.** 넣지 않으면 화면에 `code.ORD_STAT.RCV` 가
  그대로 보인다 — 그것이 **시끄럽게 드러나는** 쪽이라 의도된 모양이다.
- **부가 속성 컬럼(`ETC1`·`ETC2` …)이 없다.** 부가 속성은 `SYS_CODE_REF` 로 확정됐으므로
  함께 두면 **같은 목적의 메커니즘이 둘**이 되고 *어디에 넣어야 하나* 가 매번 판단 대상이
  된다.

## 4. `SYS_CODE_GRP_REF` — 행 하나가 **그리드 컬럼 하나**다

| 컬럼 | |
|---|---|
| `GRP_CD` + `REF_CD` | PK |
| `REF_NM` | **필수이고 폴백이 없다** |
| `REF_DESC` | |

- **정의 표가 없던 시절에는** 목록이 `SELECT DISTINCT REF_CD` 라 **상세 코드가 0건인
  그룹은 속성을 정의할 수 없었고, 마지막 코드를 지우면 정의가 조용히 사라졌다.**
- **`USE_YN` 이 없다.** 쓰지 않을 속성은 정의를 지운다 — **컬럼이 나오면 채우라는 뜻**이라
  *컬럼은 있는데 꺼져 있다* 를 만들 자리를 두지 않는다.
- **`REF_NM` 에 폴백을 두지 않는다.** 같은 `REF_CD` 를 그룹마다 다르게 부르는 것이 이 컬럼의
  존재 이유이므로 전역 코드명이 대신할 수 없고, *없으면 코드명* 으로 두면 **명칭의 원천이
  둘**이 된다.
- **`REF_CD` 로 쓸 수 있는 값은 화이트리스트다** — 공통코드 그룹 `CODE_REF_KEY` 가 그 목록이며
  `SYS_YN='Y'` 다. **지우면 참조 코드를 정의할 수단 자체가 사라진다.**

## 5. `SYS_CODE_REF` — **없는 값은 행이 없는 것이다**

| 컬럼 | |
|---|---|
| `GRP_CD` + `DTL_CD` + `REF_CD` | PK |
| `REF_VAL` | |

- **`USE_YN` 도 `NULL` 도 빈 문자열도 두지 않는다.** 화면이 셀을 비우고 저장하면 서비스가
  그 행을 **지운다** — 남기면 *없음* 의 표현이 둘이 되고 읽는 쪽이 매번 두 가지를 함께
  판정해야 한다.
- **대가는 끈 값을 기억하지 못하는 것**이고 감사 넷도 행과 함께 사라진다. 참조값은 코드값
  하나이므로 다시 넣는 비용이 낮고, **코드 자체를 끄는 길(`USE_YN='N'`)은 그대로 있다.**

## 6. 값을 넣었으면 캐시 버전을 올린다

코드 행을 바꾸는 changeset 은 `SYS_CACHE_VERSION` 의 `code` 를 함께 올린다 —
[message.md §4](./message.md) · [playbook/changeset.md](../../playbook/changeset.md).

## 7. 참조 조건으로 콤보 후보를 좁히기

`POST /api/system/common/code/getListByRef`는 `grpCd`, `refCd`, `refVal`을 받아 현재 참조 정의가
있는 활성 상세 코드를 정렬해 반환한다. 참조값은 공백·대소문자를 바꾸지 않고 그대로 비교한다.
응답은 조회 전에 읽은 CODE 버전과 코드 배열이며 표시명은 기존 메시지 번들을 사용한다.
일반 유효 관리자 로그인으로 소비할 수 있지만 공통코드 관리 저장 권한은 주지 않는다.

직접 폼에는 `<CodeSelect grpCd="DBMS_DATA_TYPE" refFilter={{ refCd: 'PARENT', refVal: dbms ?? '' }} />`,
다른 공통 컨트롤에는 `useCodeOptionsByRef`의 `options/status/retry`를 사용한다.
조건 미완성은 요청0·선행 선택 안내이고 첫 후보를 자동 선택하지 않는다. 실패는 재조회,
빈 성공은 후보 없음으로 표시하며 기존 미지원 선택값은 경고와 함께 보존한다.
실제 카탈로그의 DBMS별 자료형 연결은 해당 화면 구현 범위다. DBMS 버전은 이 조건이 아니다.

`refFilter`가 없으면 기존 그룹 전체 조회다. 참조 후보는 구조화한 세 값의 튜플 키로 별도 메모리에
보관하며 `groups[grpCd]`에 넣지 않는다. 새 CODE 버전은 전체/참조 캐시를 함께 비우고,
관측한 버전보다 오래된 응답을 수락하지 않는다. 조건을 바꾼 뒤 늦게 도착한 이전 후보도 현재 콤보에 섞지 않는다.

## 관련

- [message.md](./message.md) — `DTL_NM` 이 가리키는 곳
- [config.md](./config.md) — 설정과 코드의 경계
- [playbook/changeset.md](../../playbook/changeset.md) — 코드 시드를 넣는 법
