// @feat FEAT-506
/**
 * 사용자 설정 API — frontend/tabs.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). **`common` 계층이라 `SYS_MENU_API`
 * 시드의 입력이 아니다** — 특정 메뉴에 소속시킬 수 없는 것이며, 매핑하려 들면 메뉴를
 * 전부 나열하게 된다(backend/security.md).
 *
 * <p>여기 있는 것은 **언어 하나**다. frontend/tabs.md 의 나머지는 전부 `localStorage` 이며
 * 기기·화면의 속성이라 서버가 볼 이유가 없다(sys/user-role.md).
 */
export const SETTINGS_ENDPOINTS = {
  saveLocale: '/system/common/user/saveLocale',
} as const;
