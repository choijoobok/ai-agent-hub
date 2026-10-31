---
id: LLM-PLY-002
title: "마스터-디테일 화면 — 상하 · 좌우"
category: playbook
type: operation
created: 2026-05-04
updated: 2026-09-03
status: active
sources: ["sources/frontend/src/modules/sample/features/mdvertical/", "sources/frontend/src/modules/sample/features/mdhorizontal/", "aidd-guide/playbook/INDEX.md"]
tier: 2
tags: [playbook, master-detail, splitpane]
---

# 마스터-디테일 화면 — 상하 · 좌우

> **실물 둘**: `modules/sample/features/mdvertical/`(③ 상하) · `modules/sample/features/mdhorizontal/`(④ 좌우).
> 상하는 기본 배치, 좌우는 예외 구현을 참고하는 실물이다.
>
> 단일 그리드의 모든 규칙이 그대로 적용된다 — [crud-screen.md](./crud-screen.md) 를 먼저 읽는다.

## 1. 기본 UI — 상단 그리드와 하단 상세

- **기본 배치는 상단 마스터 그리드·하단 상세와 가운데 스플리터**다. 하단 그리드가 하나면 탭 없이 바로 표시하고, 독립 상세 영역이 둘 이상일 때 탭을 둔다. 마스터 컬럼 수가 적어도 좌우 배치로 자동 분기하지 않는다.
- **1:1 현재값은 가능한 한 마스터 그리드의 컬럼**으로 조회·편집·비교·엑셀 다운로드한다. 별도 기본정보 편집 폼으로 나누지 않는다.
- 1:N 집합·관련 자산·사용처는 하단 상세에 둔다. 관리 변경 이력은 [공통 이력 패턴](../artifact/screen/overlay/history-pattern.md)으로 저장하고 별도 메뉴에서 같은 화면 구조로 조회한다. 장문 현재값은 그리드 컬럼·셀 뷰어를 사용한다.
- 가로 스크롤을 줄이도록 전체 폭을 쓰고, 중요한 열을 먼저 표시한다. 드문 열은 그리드 설정에서 켤 수 있게 하며 감춰진 열도 자동 내려간다고 설명하지 않는다.
- **그리드 엑셀 다운로드는 기본 제공**이다. 메뉴 EXPORT 선언·현재 권한과 그리드별 반출 정책을 적용하고, 미제공은 요건서에 사유를 남긴다. 범위·표시 열·현재 페이지/전체의 뜻은 [엑셀 표준](./excel-download.md)을 따른다.
- 좌우 배치가 필요한 트리 탐색 등은 업무상 이유를 요건서에 적는 예외다. **축 전환 토글은 기본으로 두지 않는다.**

## 2. 독립 저장 대상이 둘일 때 컨트롤러도 둘이다

```ts
const master = useGridController<SampleOrder>({ menuCd, columns: masterColumns, api: masterApi,
                                                params: collectConditions, addRow, toSaveRow });
const detail = useGridController<SampleOrderItem>({ menuCd, columns: detailColumns, api: detailApi,
                                                    searchParams: detailSearchParams, addRow, toSaveRow,
                                                    saveEnvelope: (rows) => ({ ordId, rows }) });
```

- **독립된 저장 계약은 그리드마다 저장한다.** 한 자산의 전체 구성으로 정의된 계약이면 그 단위를 유지한다. 독립 계약을 묶으면 *품목만 고쳤는데 주문도 갱신됨* 이 되어 **감사
  컬럼이 거짓말을 한다.**
- **디테일의 대상은 `searchParams` 다 — `conditions` 가 아니다.** 부모 키는 *구조* 이지
  *사용자가 좁힌 것* 이 아니다.
- **부모 키는 `useRef` 로 든다.** 선택이 바뀌는 것과 조회가 나가는 것 사이에 렌더가 끼면
  `useState` 값은 아직 옛것이다.

## 3. 선택 — 거부할 수 있어야 한다

```ts
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';

const confirmDiscard = useConfirmDiscard();   // ← 공통물이다. 손으로 쓰지 않는다

const selectOrder = async (row) => {
  if (detail.dirty && !(await confirmDiscard())) return false;   // ← 판의 선택이 되돌아간다
  setSelected(row);
  if (!row || row.state === 'C') { selectedOrdId.current = null; detail.setRows([]); return true; }
  selectedOrdId.current = row.data.ordId;
  await detail.read(true);
  return true;
};
```

| 규칙 | 없으면 |
|---|---|
| **화면이 묻는다** — 그리드가 아니다 | 물음의 결과를 화면이 알 수 없어 **선택을 받아들일지 정하지 못한다** |
| **물음은 `useConfirmDiscard()` 가 갖는다** | 이 줄이 없던 동안 화면 여섯이 같은 열세 줄을 손으로 다시 썼다 — **문구 하나를 고치는 데 일곱 곳** |
| **거부권이 있다**(`false` 를 돌려준다) | *보고 있는 것* 과 *고른 것* 이 갈리고, 그 상태로 저장하면 **A 의 품목이 B 로 나간다** |
| **신규 행(`C`)은 빈 상태다** | 부모 키가 아직 DB 에 없다 — 붙이려 하면 **FK 위반** |
| **마스터 조회는 아래를 리셋한다** | 이전에 보던 행이 결과에 없을 수 있다 — **다른 주문의 품목을 그 주문 것으로 본다** |

**디테일의 행 추가에도 게이트가 있다** — `addRow.canAdd` 가 부모 선택을 요구한다.

## 4. 마스터 삭제 게이트 — 근거는 마스터 응답에 있어야 한다

```ts
const masterDeletable = useMemo<DeleteColumnSpec<SampleOrder>>(() => ({
  enabled: (row) => row.data.itemCnt === 0 && !row.data.lockYn,
  reason: (row) => row.data.lockYn ? label(MSG.SAMPLE_LOCKED)
                                   : label(MSG.SAMPLE_DELETE_BLOCKED, { count: row.data.itemCnt }),
}), []);
```

**게이트가 건수 컬럼에서 나온다** — 마스터 응답이 자식 건수를 실으므로 **행마다 답할 수
있다.** 아래를 조회해야만 아는 것이면 게이트가 아니다.

> **건수를 싣는 것이 늘 옳지는 않다.** ①은 싣지 않고 **서버가 사유를 담아 거절한다** —
> 컬럼 서른짜리 조회에 `group by` 가 한 벌 더 붙기 때문이다. **화면의 주제가 무엇이냐**로
> 갈린다: 레이아웃이면 건수를 싣고, 컬럼이면 서버에 맡긴다.

## 5. `SplitPane` — 선언 넷

```tsx
<SplitPane
  storageKey="smplMdVert"                          {/* 개인화의 키 */}
  first={(collapsed, expand, axis) => (
    <DataGrid gridId="master" controller={master} deletable={masterDeletable}
              onSelect={selectOrder} collapsed={collapsed} collapsedAxis={axis} onExpand={expand} />
  )}
  second={(collapsed, expand, axis) => (
    <DataGrid gridId="detail" controller={detail} titleSuffix={detailSuffix}
              disabled={selectedOrdId.current === null} ... />
  )}
/>
```

| prop | 왜 |
|---|---|
| **`gridId`** | 개인화의 키다. **같으면 위아래 설정이 서로를 덮는다** |
| **`titleSuffix`** | 디테일은 **자기가 누구의 것인지 말하지 못한다** — 부모 컨트롤러에서 온다 |
| **`disabled`** | 대상이 없으면 버튼이 통째로 잠긴다. 탭이 아니라 **버튼**이다 |
| **`collapsed`·`collapsedAxis`·`onExpand`** | 접힘은 `SplitPane` 이 갖고 그리드는 **전달만** 한다 |

**현재 구현은 `SplitPane`이 축을 판단한다.** 세로 제목·접힘 띠는 좌우 예외의 회귀 실물이다.
@defer P4 — 공통 축 결정의 실제 구현을 §1의 상하 기본·좌우 예외에 맞추고 승인된 소유 슬라이스에서 검증한다. 현재 자동 축을 새 표준의 구현 완료로 표시하지 않는다.

## 6. 디테일에만 조회 버튼을 붙이는 자리 — ④

```tsx
onRead={() => void detail.read(true)}
```

④ 실물은 마스터 재조회 없이 품목만 갱신하는 버튼이 있다. 디테일의 수동 조회는 축이 아니라 업무로 판정한다.
선택/탭 진입으로 자동 조회하는 이력 등은 요건서대로 표시하며, 배치만으로 조회조건·버튼을 추가하지 않는다.

## 7. 갈리지 않아야 하는 것

- **디테일의 컬럼 선언을 공유한다** — ③④가 `modules/sample/itemColumns.ts` 하나를 쓴다.
  갈려야 하는 것은 **마스터와 축**이지 자식의 정의가 아니다.
- **부분집합이면 엔드포인트를 새로 열지 않는다**(④는 ③의 아홉 중 셋만 그린다).
- **조회조건도 같다** — ③④가 같은 넷이다.

## 8. 커밋 전 체크리스트

- [ ] `gridId` 가 둘 다 있고 서로 다른가
- [ ] 선택 핸들러가 **`Promise<boolean>`** 을 돌려주는가(거부권)
- [ ] 마스터 조회가 디테일을 리셋하는가
- [ ] 신규 마스터 행에서 디테일이 **빈 상태**가 되는가
- [ ] [crud-screen.md §7](./crud-screen.md) 의 체크리스트

## 관련

- [master-tabs.md](./master-tabs.md) — 자식 표가 둘 이상이면
- [standard/frontend/grid-validation.md](../standard/frontend/grid-validation.md) §4
- [excel-download.md](./excel-download.md) — ③이 서버 생성 엑셀의 실물이다
