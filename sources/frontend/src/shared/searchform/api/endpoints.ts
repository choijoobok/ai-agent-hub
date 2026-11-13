/**
 * 저장된 조회조건의 API — UX-07 · frontend/search-form.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). 셋 다 **`common` 계층이라
 * `SYS_MENU_API` 시드의 입력이 아니다** — `menuCd` 를 받지만 **그 메뉴의 권한을 묻지
 * 않는다**: 여기서 다루는 것은 *"나는 이 화면을 이렇게 조회한다"* 라는 **사람의 속성**
 * 이며(frontend/tabs.md) 그 화면의 데이터가 아니다.
 *
 * <p>`features/` 가 아니라 `shared/` 에 있는 유일한 `endpoints.ts` 다. 조회조건 폼은
 * 화면 하나의 것이 아니라 **모든 마스터 그리드 화면의 것**이라 소속시킬 도메인이 없다.
 */
export const SEARCH_COND_ENDPOINTS = {
  getList: '/system/common/searchCond/getList',
  save: '/system/common/searchCond/save',
  delete: '/system/common/searchCond/delete',
} as const;
