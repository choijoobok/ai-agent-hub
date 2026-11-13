// @feat FEAT-505
/**
 * 다국어 메시지 관리 API — screen/admin/system/message/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/message` → `@/modules/system/features/message` 를 따라와 여기를 읽는다.
 *
 * <p>선언하지 않고 호출하면 **403** 이다(fail-closed 가 잡는다). 반대로 선언해 놓고 쓰지
 * 않으면 **접근 범위가 조용히 넓어진다** — 그래서 URL 문자열 리터럴을 ESLint 가 막는다.
 *
 * <p>`baseURL` 이 `/api` 이므로 그 다음 마디부터 적는다. 첫 마디가 계층이며 `service` 는
 * `SYS_API` 판정을 받는다는 뜻이다(backend/security.md).
 *
 * <p>**엑셀 다운로드(`exportExcel`)가 아직 없다.** screen/admin/system/message/requirements.md 이 정한 것은 서버 POI 경로이고
 * (playbook/excel-download.md 셋째) 그것은 다운로드 이력·상한과 함께 와야 한다 — 지금 넣으면 `EXPORT` 권한이
 * 있는데 아무 데도 닿지 않는 버튼이 생긴다.
 */
export const MESSAGE_ADMIN_ENDPOINTS = {
  getList: '/system/service/message/getList',
  save: '/system/service/message/save',
} as const;
