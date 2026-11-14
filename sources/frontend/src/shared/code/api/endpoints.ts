/**
 * 공통코드 API 경로 — sys/code.md · backend/security.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). `common` 계층이므로 `SYS_MENU_API` 시드의
 * 입력이 아니다(screen/_standalone/README.md) — 콤보를 쓰는 메뉴를 전부 나열하게 되는 API 는 업무 API 가
 * 아니라는 것이 그 계층의 판별 신호다(`ApiLayer.COMMON`).
 *
 * <p>관리 화면의 `CODE_ENDPOINTS`(`/system/service/code/**`)와 <b>다른 파일</b>인 것이 중요하다.
 * 저쪽은 CODE_MGMT 메뉴에 매인 선언이고 이쪽은 어느 화면에나 딸린다 — 한 파일에 두면
 * 시드 생성기가 읽는 범위와 읽지 않는 범위가 섞인다.
 */
export const CODE_COMMON_ENDPOINTS = {
  getList: '/system/common/code/getList',
  getListByRef: '/system/common/code/getListByRef',
} as const;
