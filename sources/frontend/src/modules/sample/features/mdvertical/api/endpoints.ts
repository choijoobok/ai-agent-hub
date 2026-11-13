// @feat FEAT-503
/**
 * 샘플 마스터-디테일(상하) API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/md-vertical` → `@/modules/sample/features/mdvertical` 을 따라와 여기를 읽는다.
 *
 * <p><b>액션은 접두가 정한다</b>(backend/api.md): `get*` 은 `READ`, `save*` 는 `SAVE`,
 * `export*` 는 `EXPORT`, 나머지는 `EXECUTE`.
 *
 * <p><b>`export*` 셋이 ②의 세 항목이다</b>(playbook/excel-download.md 의 임의 API). 화면의 아홉 컬럼이
 * 아니라 <b>주문 × 품목 합본</b>을 내리므로 프론트가 만들 수 없다 — 그 표는 화면에 없다.
 */
export const SAMPLE_VERT_ENDPOINTS = {
  getOrderList: '/sample/service/getOrderList',
  getItemList: '/sample/service/getItemList',
  saveOrders: '/sample/service/saveOrders',
  saveItems: '/sample/service/saveItems',
  /** 출고 지시 — <b>프로그레스가 없다</b>(playbook/INDEX.md). 상태 하나를 바꾸는 일이라 즉시 끝난다. */
  shipOrders: '/sample/service/shipOrders',
  /**
   * 품목 금액 재계산 — <b>프로그레스가 있다</b>(backend/crosscutting.md · playbook/INDEX.md). <b>행×디테일</b>이다.
   *
   * <p>바로 위와 나란히 놓인 것이 요점이다: 같은 `EXECUTE`, 같은 `target:'selected'` 인데
   * 하나는 즉시 끝나고 하나는 <b>고른 주문마다 딸린 품목 전건</b>을 돈다 — 주문 하나가
   * 수천 건일 수 있어 창이 소제목으로 <i>지금 어느 주문</i>을 말한다.
   */
  recalcItems: '/sample/service/recalcItems',
  /** ②의 <i>현재 페이지</i> — 이름은 그대로 두고 경로만 서버다(playbook/excel-download.md). */
  exportOrderPage: '/sample/service/exportOrderPage',
  /** ②의 <i>전체</i> — 상한 판정도 서버가 한다. */
  exportOrderAll: '/sample/service/exportOrderAll',
  /** ②의 세 번째 항목 — 조회로는 낼 수 없는 표다. */
  exportOrderNoItem: '/sample/service/exportOrderNoItem',
} as const;
