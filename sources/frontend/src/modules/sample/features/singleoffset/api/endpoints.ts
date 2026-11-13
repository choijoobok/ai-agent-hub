// @feat FEAT-503
/**
 * 샘플 단일 그리드·오프셋 API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). **이 선언이 `SYS_MENU_API` 시드의 입력**이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/single-offset` → `@/modules/sample/features/singleoffset` 을 따라와
 * 여기를 읽는다.
 *
 * <p>**액션은 접두가 정한다**(backend/api.md): `get*` 은 `READ`, `save*` 는 `SAVE`,
 * `export*` 는 `EXPORT`, 나머지는 `EXECUTE`.
 *
 * <p>**`export*` 가 하나도 없다** — ③과 갈리는 자리다. 이 화면이 그리는 표가 곧 내려받을
 * 표이므로 엑셀은 프론트가 만들고(playbook/excel-download.md 의 앞 두 갈래), `EXPORT` 권한은 메뉴가
 * 선언한다(sys/menu.md · `menu-tree.yaml` 의 `export: true`).
 *
 * <p>**③의 `getOrderList` 를 재사용하지 않는다.** 같은 테이블·같은 페이징 모드지만 투영이
 * 다르다 — 여기를 ③에 맞추면 서른 중 스물하나가 빈 칸이 되고, ③을 여기에 맞추면 그
 * 화면이 그리지도 않는 `nclob` 셋을 페이지마다 나른다.
 */
export const SAMPLE_OFFSET_ENDPOINTS = {
  getOrderOffsetList: '/sample/service/getOrderOffsetList',
  /** 참조 팝업의 후보 — 팝업의 조회는 **부르는 화면의 것**이다(playbook/INDEX.md). */
  getCustomerList: '/sample/service/getCustomerList',
  /**
   * `listModal` 이 항목을 고르는 팝업의 후보 — frontend/grid.md · playbook/INDEX.md.
   *
   * <p>**이름이 긴 이유가 있다.** `/getTagList` 는 ⑤의 것이고(한 주문의 태그 **행들**)
   * 같은 `/api/service/sample` 를 나눠 쓰므로, 짧은 이름을 쓰면 기동이 매핑 충돌로 죽는다.
   */
  getTagCandidateList: '/sample/service/getTagCandidateList',
  saveOffsetOrders: '/sample/service/saveOffsetOrders',
  /** 확정 — `target:'selected'`, **프로그레스 없음**. 응답이 곧 끝이다. */
  confirmOrders: '/sample/service/confirmOrders',
  /** 일괄 재계산 — `target:'all'`, **`params` 모달 + 진행률**. 응답은 `jobId` 다. */
  recalculateOrders: '/sample/service/recalculateOrders',
} as const;
