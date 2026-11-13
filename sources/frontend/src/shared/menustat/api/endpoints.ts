/**
 * 자주 쓰는 메뉴의 API — UX-16 · sys/menu.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). 둘 다 **`common` 계층**이다 —
 * 모든 메뉴에 해당하므로 특정 메뉴에 소속시킬 수 없다(backend/security.md).
 */
export const MENU_STAT_ENDPOINTS = {
  saveSelect: '/system/common/menu/saveSelect',
  getFrequent: '/system/common/menu/getFrequent',
} as const;
