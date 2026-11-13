/**
 * 엑셀 다운로드 이력의 API — playbook/excel-download.md · LOG-08.
 *
 * <p>호출 URL 은 `api/endpoints.ts` 에만 둔다(frontend/packages.md). **`common` 계층이라 `SYS_MENU_API`
 * 시드의 입력이 아니다** — 엑셀 버튼이 있는 모든 화면이 부르므로 매핑하려면 **메뉴를
 * 전부 나열하게 된다**(backend/security.md 의 판별 신호).
 *
 * <p>**`EXPORT` 권한을 묻지 않는다.** 이 호출은 판정이 아니라 **기록**이며, 서버도 권한을
 * 보지 않는다 — 이유는 `ExcelLogController` 가 갖는다.
 */
export const EXCEL_LOG_ENDPOINTS = {
  saveLog: '/system/common/excel/saveLog',
} as const;
