// @feat FEAT-503
/**
 * 샘플 마스터-디테일(복수 탭) API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). **이 선언이 `SYS_MENU_API` 시드의 입력**이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/md-tabs` → `@/modules/sample/features/mdtabs` 를 따라와 여기를 읽는다.
 *
 * <p>**새 경로가 둘뿐이다.** 마스터와 품목 탭은 ③의 것을 그대로 부른다 — ③이 싣는 아홉이
 * 그대로 이 화면의 마스터이고(playbook/INDEX.md 가 *"§5.2 의 아홉"* 이라고 적었다) 품목 탭도 같은
 * 표다. 같은 표에 경로를 새로 열면 **어느 쪽이 진짜인지** 를 다음 사람이 물어야 한다.
 *
 * <p>**같은 URL 을 두 화면이 선언하는 것이 정상이다**(④가 먼저 그렇게 했다). 007 시드가
 * 그 URL 을 두 메뉴에 매핑하며, 적지 않으면 이 화면에서만 403 이 난다.
 *
 * <p>**태그 둘만 새것이다.** `SMPL_ORDER_TAG` 를 ①은 `list` 컬럼으로 나르므로 주문 응답에
 * 실려 오지만(그 화면은 태그를 따로 묻지 않는다), 여기서는 **탭이 스스로 조회하고 스스로
 * 저장한다** — 그것이 지연 로딩과 탭별 독립 편집 상태의 전제다(playbook/INDEX.md).
 *
 * <p>**고객 팝업이 없다.** ④는 선언했지만 이 화면의 마스터는 ③의 아홉이고 그 컬럼에서
 * 고객명은 평범한 텍스트다 — 부르지 않는 경로를 적으면 *선언 = 사용* 이 깨지고, 그
 * 화면에 없는 권한이 역할에 붙는다(sys/menu.md).
 */
export const SAMPLE_TABS_ENDPOINTS = {
  getOrderList: '/sample/service/getOrderList',
  getItemList: '/sample/service/getItemList',
  saveOrders: '/sample/service/saveOrders',
  saveItems: '/sample/service/saveItems',
  /** ⑤가 여는 첫 경로 — `SMPL_ORDER_TAG` 를 행으로 읽는다. */
  getTagList: '/sample/service/getTagList',
  /** ①의 전량 교체와 **갈래가 다른** 저장이다 — 행마다 `rowState` 를 싣는다. */
  saveTags: '/sample/service/saveTags',
} as const;
