/**
 * 그리드 개인화의 API — UX-06 · frontend/grid.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). 셋 다 **`common` 계층이라
 * `SYS_MENU_API` 시드의 입력이 아니다** — `menuCd` 를 받지만 **그 메뉴의 권한을 묻지
 * 않는다**: *"나는 이 그리드를 이렇게 본다"* 는 **사람의 속성**이다(backend/security.md).
 */
export const GRID_PREF_ENDPOINTS = {
  getOne: '/system/common/gridPref/getOne',
  save: '/system/common/gridPref/save',
  delete: '/system/common/gridPref/delete',
} as const;
