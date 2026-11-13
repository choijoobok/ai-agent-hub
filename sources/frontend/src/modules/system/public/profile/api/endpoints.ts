// @feat FEAT-501
/**
 * 프로필 모달의 API — frontend/tabs.md.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). 다섯 다 **`common` 계층이라
 * `SYS_MENU_API` 시드의 입력이 아니다** — 전부 "나에 대한 것" 이며 특정 메뉴에 소속시킬 수
 * 없다(backend/security.md).
 *
 * <p>**`changePassword` 만 `auth` 아래인 것이 요건이다.** 제한 세션(체인 #5)에서 열려 있는
 * 유일한 업무 경로이며 인가 인터셉터가 그 경로 문자열을 화이트리스트로 갖는다 — 여기서
 * `user/` 로 옮기면 비밀번호가 만료된 사용자가 아무것도 못 하는 세션에 갇힌다.
 */
export const PROFILE_ENDPOINTS = {
  getMyProfile: '/system/common/user/getMyProfile',
  saveMyProfile: '/system/common/user/saveMyProfile',
  getMyLoginHistory: '/system/common/user/getMyLoginHistory',
  getMyRoles: '/system/common/user/getMyRoles',
  changePassword: '/system/common/auth/changePassword',
} as const;
