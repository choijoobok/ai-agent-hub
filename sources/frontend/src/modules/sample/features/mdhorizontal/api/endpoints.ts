// @feat FEAT-503
/**
 * 샘플 마스터-디테일(좌우) API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). **이 선언이 `SYS_MENU_API` 시드의 입력**이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/md-horizontal` → `@/modules/sample/features/mdhorizontal` 을 따라와
 * 여기를 읽는다.
 *
 * <p>**전부 ③의 엔드포인트다.** ①②가 새 경로를 연 것과 갈리는 자리이며, 근거는 투영이다 —
 * ①은 서른을 그려 아홉짜리 응답으로는 스물하나가 빈 칸이 되고 ②는 모드가 달라 봉투가
 * 다르지만, ④는 **③이 싣는 아홉 중 셋만 그린다**. 부분집합에는 새 엔드포인트가 필요 없다:
 * 만들면 같은 표를 두 경로가 내려 **어느 쪽이 진짜인지** 를 다음 사람이 물어야 한다.
 *
 * <p>**같은 URL 을 두 화면이 선언하는 것이 정상이다.** 007 시드가 그 URL 을 두 메뉴에
 * 매핑하며, 적지 않으면 이 화면에서만 403 이 난다.
 *
 * <p>**팝업의 조회도 여기 적는다.** 등록은 `modules/sample/popups.ts` 에 있지만 인가는
 * **부르는 화면의 것**이다(playbook/INDEX.md).
 */
export const SAMPLE_HORZ_ENDPOINTS = {
  getOrderList: '/sample/service/getOrderList',
  getItemList: '/sample/service/getItemList',
  saveOrders: '/sample/service/saveOrders',
  saveItems: '/sample/service/saveItems',
  /** 참조 팝업의 후보 — 왼쪽 마스터의 고객 칸이 연다. */
  getCustomerList: '/sample/service/getCustomerList',
} as const;
