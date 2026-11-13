// @feat FEAT-503
/**
 * API 카탈로그 API — screen/admin/system/api-catalog/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/api-catalog` → `@/modules/system/features/apicatalog` 를 따라와 여기를 읽는다.
 *
 * <p><b>이 파일이 이 화면의 소재이기도 하다</b>. screen/admin/system/api-catalog/requirements.md 가 *미매핑* 을 두고 *"고치는 곳은
 * 프론트 `endpoints.ts` + 시드 재생성"* 이라고 적은 그 파일이 이것이다 — 화면이 진단하는
 * 결함의 원인이 화면 자신의 이웃 파일에 있다.
 *
 * <p><b>둘뿐이고 둘 다 `get*` 이다.</b> 저장 경로가 없는 것이 이 화면이 조회 전용이라는
 * 사실의 집행이며(screen/admin/system/api-catalog/requirements.md), 그래서 시드가 `SAVE` 를 만들지 못하고 어느 역할에도 줄 수 없다.
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀은 있지만(sys/menu.md 의 액션 열이 `R E` 다) 그것은
 * **프론트 생성**이다(frontend/grid.md · playbook/excel-download.md 첫째) — 서버가 낼 것이 없으므로 경로도 없다.
 * 권한은 시드에 있고 버튼은 `<DataGrid>` 가 그린다.
 */
export const API_CATALOG_ENDPOINTS = {
  getList: '/system/service/apiCatalog/getList',
  /** 하단 pane — 마스터에서 고른 한 API 의 매핑 메뉴다(screen/admin/system/api-catalog/requirements.md). */
  getMenuList: '/system/service/apiCatalog/getMenuList',
} as const;
