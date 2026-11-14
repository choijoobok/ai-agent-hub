---
id: LLM-PLY-003
title: "하단 디테일 탭 — 자식 표가 둘 이상일 때"
category: playbook
type: operation
created: 2026-05-04
updated: 2026-11-14
status: active
sources: ["sources/frontend/src/modules/sample/features/mdtabs/", "sources/frontend/src/shared/grid/DetailTabs.tsx", "aidd-guide/playbook/INDEX.md"]
tier: 2
tags: [playbook, master-detail, tabs]
---

# 하단 디테일 탭 — 자식 표가 둘 이상일 때

> **실물**: `modules/sample/features/mdtabs/`(⑤) — 페이지 하나와 `components/SampleDetailTabs.tsx`.
>
> **③에 탭을 얹은 것이지 ③을 베낀 것이 아니다.** 마스터·조회조건·스플리터는 그대로이며
> 갈리는 것은 **하단**뿐이다. [master-detail.md](./master-detail.md) 를 먼저 읽는다.

## 1. 파일을 나눈다

```
pages/SampleMdTabsPage.tsx        마스터 + SplitPane
components/SampleDetailTabs.tsx   탭 둘 = 컨트롤러 둘
```

**한 파일에 컨트롤러 셋을 두지 않는다** — *어느 `dirty` 가 누구의 것인가* 를 읽는 사람이
세게 된다.

## 2. 훅은 탭 **밖**에 산다

`useGridController` 둘이 `SampleDetailTabs` 에 있다. 탭 컴포넌트가 활성 판만 그리더라도
훅은 부모에 있으므로 **품목을 고치고 태그 탭으로 가도 편집이 살아 있다.**

**탭 안에서 훅을 부르면 언마운트와 함께 미저장 편집이 사라진다.**

## 3. 핸들 둘 — 마스터가 하단에게 묻는 전부

```ts
export interface SampleDetailTabsHandle {
  select: (ordId: string | null) => Promise<void>;
  dirty: () => boolean;
}
```

- **`dirty()` 는 탭 둘을 `||` 로 묶는다.** 마스터가 선택을 바꾸기 전에 **한 번만** 묻는다 —
  탭마다 물으면 사용자는 같은 물음에 두 번 답하면서도 **무엇을 잃는지는 여전히 모른다.**
- **핸들은 `useEffect` 에서 채운다.** 렌더 중에 ref 를 쓰지 않는다(`react-hooks/refs`) —
  마스터가 이것을 부르는 것은 사람이 행을 클릭한 뒤라 효과가 이미 돌아 있다.

## 4. 전체 디테일 확인이 기본, 지연 조회는 명시한 예외

기본은 마스터 선택 시 모든 디테일을 조회하고, 모두 비었음을 확인한 뒤 해당 기존 행의 삭제를 여는 것이다. 조회 결과는 탭별로 보존해 탭 전환만으로 다시 읽지 않는다. 미저장 신규 마스터 행의 로컬 제거는 즉시 허용한다. 상세한 판정·서버 보호는 [삭제 표준](../standard/frontend/grid-validation.md#4-마스터-디테일)이 정본이다.

모든 탭 조회의 지연이 사용자 경험에 부적절하면 화면 요건서에 이유를 적고 **활성 탭만 조회**한다. 이때 기존 행 삭제를 처음부터 열어 두는 예외도 요건서에 명시하며, 최종 서버 검증은 조회하지 않은 탭의 참조까지 포함한다. 미조회는 빈 결과가 아니고 조회 실패는 이 예외로 허용하지 않는다.

- 선택한 마스터가 바뀌면 탭별 조회 완료 표시와 삭제 판정을 초기화한다. 이전 대상 응답은 무시한다.
- 성공한 완전 조회만 완료로 표시한다. 실패한 조회는 미완료로 남겨 재시도할 수 있어야 한다.
- 같은 대상의 탭 전환에서는 조회 결과와 미저장 편집을 유지한다. 새로 조회할 때는 해당 삭제 판정을 먼저 무효화한다.
- 저장이 다른 탭의 내용을 바꾸면 그 탭의 조회 완료 표시도 비운다. 어떤 탭이 영향을 받는지는 화면 요건서에서 정한다.

## 5. 대상이 없을 때 — 탭을 잠그지 않는다

```ts
const idle = picked === null;
// <DataGrid disabled={idle} emptyText={idle ? MSG.SAMPLE_ITEM_NO_MASTER : MSG.SAMPLE_ITEM_EMPTY} />
```

**탭은 살아 있고 버튼이 잠긴다.** 탭을 잠그면 *"이 주문에 태그 탭이 없나"* 로 읽힌다 —
없는 것은 **고를 주문**이지 탭이 아니다.

**빈 문구가 갈린다**: *주문을 고르세요* 와 *품목이 없습니다* 는 다른 사실이다.

## 6. 탭이 나눠 갖는 것과 공유하는 것

| | 탭마다 | 공유 |
|---|---|---|
| `gridId` | **다르다**(`detailItem` · `detailTag`) — 같으면 설정이 서로를 덮는다 | |
| 컨트롤러 · API · 저장 | 다르다 — **저장 단위가 셋**이다(마스터·품목·태그) | |
| `titleSuffix` | | 부모에서 온다 — 탭이 제 이름을 짓지 않는다 |
| 조회조건 폼 | **없다** | 마스터의 것 하나 |
| `disabled` | | 선택 상태 하나 |

**디테일 탭에는 조회조건 폼이 없다.** 대상이 마스터의 선택이며 그것은 `searchParams`(구조)
이지 `conditions`(사용자가 좁힌 것)가 아니다. `params` 는 대상이 없을 때 **`null` 을
돌려주어 조회 자체를 막는다.**

## 7. 판 전체를 봐야 아는 검증은 `save.validate.grid` 다

```ts
validate: { grid: (rows) => [...tooManyTagRows(rows), ...duplicatedSortNo(rows)] }
```

- **여러 규칙을 이어 붙인다.** 앞의 것만 내면 사용자가 고친 뒤에 나머지를 처음 듣는다.
- **순서에 뜻이 있다.** 개수 초과가 앞이다 — 넘은 판에서 할 일은 행을 빼는 것이고, 뺄 행의
  순서를 먼저 고치라고 하면 **두 번 고치게 된다.**
- **서버가 같은 것을 다시 본다.** 화면 검증은 왕복을 아끼는 것이지 판정이 아니다.

## 8. 탭 전환에는 묻지 않는다

**하단 디테일 탭과 화면 안 최상단 탭의 규칙이 다르다.**

| | 하단 디테일 탭(⑤) | 화면 안 최상단 탭(⑥) |
|---|---|---|
| 전환할 때 | **묻지 않는다** — 표시가 바뀔 뿐 데이터를 버리지 않는다 | **묻는다** — [excel-upload.md §7](./excel-upload.md) |
| 갈리는 것 | 한 화면 안의 **부분** | **화면 전체** |

## 9. 커밋 전 체크리스트

- [ ] 훅이 탭 **밖**에 있는가
- [ ] `dirty()` 가 탭 전부를 묶는가 — 마스터가 **한 번만** 묻는가
- [ ] 주문이 바뀔 때 `loaded` 를 비우고 **모든 탭의 행**을 비우는가
- [ ] `gridId` 가 탭마다 다른가
- [ ] [master-detail.md §8](./master-detail.md) 의 체크리스트

## 관련

- [master-detail.md](./master-detail.md) — 마스터와 스플리터
- [excel-upload.md](./excel-upload.md) — 화면 안 최상단 탭은 규칙이 다르다
- [standard/frontend/grid-validation.md](../standard/frontend/grid-validation.md) §3 — 검증 세 층
