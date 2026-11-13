/**
 * 즐겨찾기의 API — UX-03 · sys/menu.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). 셋 다 **`common` 계층이라
 * `SYS_MENU_API` 시드의 입력이 아니다** — `menuCd` 를 받지만 **그 메뉴의 권한을 묻지
 * 않는다**: 담는 것은 *"나는 이 메뉴를 자주 쓴다"* 라는 **사람의 속성**이지 그 메뉴의
 * 데이터가 아니다.
 *
 * <p>`shared/` 에 있는 것은 조회조건과 같은 이유다 — 사이더 · 탭바 · 진입 모달 셋이
 * 함께 쓰므로 소속시킬 도메인이 없다.
 */
export const FAVORITE_ENDPOINTS = {
  getList: '/system/common/favorite/getList',
  save: '/system/common/favorite/save',
  delete: '/system/common/favorite/delete',
} as const;
