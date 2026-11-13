// @feat FEAT-501
/**
 * 인증 API 경로 — backend/security.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). 화면이 문자열을 직접 쓰면 ESLint 가 막는다 —
 * 이 선언이 `SYS_MENU_API` 시드의 입력이므로 <b>선언 = 사용</b>이 구조적으로 보장돼야 한다.
 *
 * <p>`baseURL` 이 `/api` 이므로 여기서는 그 다음 마디부터 적는다. 첫 마디가 계층이며
 * (`public` · `common` · `service`) 그것이 접근 정책을 정한다(backend/security.md · backend/api.md).
 *
 * <p>`public`·`common` 은 인가 판정을 받지 않으므로 시드 대상이 아니다(screen/_standalone/README.md).
 */
export const AUTH_ENDPOINTS = {
  getCsrf: '/system/public/auth/getCsrf',
  login: '/system/public/auth/login',
  getMe: '/system/common/auth/getMe',
  heartbeat: '/system/common/auth/heartbeat',
  logout: '/system/common/auth/logout',

  /*
    분실 비밀번호 재설정 — AUTH-07 · documents/screen/_standalone/system/login/requirements.md §7.

    **셋을 `reset` 으로 묶은 것이 결정이다.** 한 묶음임이 경로만 봐도 보이고, 이미 있는
    `/system/service/user/savePasswordReset`(관리자 임시 비밀번호 발급)과 이름이 겹치지 않는다 —
    둘은 다른 일이며 겹치면 로그에서 구분되지 않는다.

    **정책만 GET 이다** — `common`·`public` 의 조회는 GET, 나머지는 POST(backend/api.md).
  */
  getPasswordPolicy: '/system/public/auth/getPasswordPolicy',
  sendResetCode: '/system/public/auth/sendResetCode',
  verifyResetCode: '/system/public/auth/verifyResetCode',
  saveResetPassword: '/system/public/auth/saveResetPassword',
} as const;
