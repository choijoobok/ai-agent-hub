// @feat FEAT-503
/**
 * 샘플 엑셀 업로드 API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). **이 선언이 `SYS_MENU_API` 시드의 입력**이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/excel` → `@/modules/sample/features/excel` 을 따라와 여기를 읽는다.
 *
 * <p>**둘뿐이고, 세그먼트 둘이 그 둘을 함께 쓴다.** 탭마다 경로가 하나가 아니다 —
 * playbook/excel-upload.md 가 업로드에 새 경로를 두지 않기로 했기 때문이다:
 *
 * - `getFlatItemList` 는 업무 탭의 조회이자, 업로드 탭이 **파일을 읽은 직후 한 번**
 * 부르는 대조용 조회다. 업로드 탭에는 조회 **버튼**이 없다(playbook/INDEX.md).
 * - `saveFlatItems` 는 **두 탭의 저장이 같은 요청**이다. 업로드는 파일을 서버에 올리는
 *   일이 아니라 브라우저가 읽어 그리드에 부은 행을 저장하는 일이라, 새 경로를 만들 근거가
 *   없다.
 *
 * <p>**업로드 엔드포인트가 없다**(playbook/excel-upload.md). 파일이 서버에 올라가지 않으므로 받을 곳이
 * 없고 `SYS_FILE` 에도 아무것도 남지 않는다 — 이 파일에 그 경로가 없는 것이 그 결정의
 * 실물이다.
 *
 * <p>**③의 `getItemList` 를 쓰지 않는다.** 저것은 고른 주문 하나의 품목이라 조회조건이
 * 없고(파라미터다), 이 화면은 **주문을 가로질러** 넷으로 거른다(playbook/INDEX.md) — 투영이 아니라
 * **대상**이 다른 자리라 경로가 갈린다.
 */
export const SAMPLE_EXCEL_ENDPOINTS = {
  getFlatItemList: '/sample/service/getFlatItemList',
  saveFlatItems: '/sample/service/saveFlatItems',
} as const;
