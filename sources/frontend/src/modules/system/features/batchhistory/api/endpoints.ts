// @feat FEAT-508
/**
 * 배치 실행 이력 API — screen/admin/system/batch/history/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/batch/history` → `@/features/batchhistory` 를 따라와 여기를 읽는다.
 *
 * <p><b>`save*` 가 없는 것이 이 화면에 쓰기가 없다는 사실의 집행</b>이다 — 시드 생성기가
 * `SAVE` 액션을 만들 수 없고, 만들 수 없으므로 <b>어느 역할에도 줄 수 없다</b>.
 * `ERROR_LOG_ENDPOINTS` 와 같은 장치이며, 그런데 여기에는 <b>`X` 가 하나 있다</b>.
 *
 * <p><b>`cancel` 이 `SAVE` 가 아닌 이유</b>는 screen/admin/system/batch/history/requirements.md 다. `CANCEL_REQ_DTM` 을 쓰지만
 * 컬럼 쓰기가 <b>결과 그 자체가 아니라</b> 실행 중인 절차에 요청을 전달하는 <b>통로</b>이며,
 * 아무도 읽지 않으면 아무 일도 일어나지 않는다. 경로 접두(`cancel` → *그 외* → `EXECUTE`)와
 * 판단이 일치한다.
 *
 * <p><b>`exportExcel` 이 없다 — 그런데 엑셀 버튼은 있다.</b> 프론트 생성이라 서버 호출이
 * 없다(frontend/grid.md · playbook/excel-download.md). `E` 액션은 `SYS_MENU.EXPORT_YN` 이 선언하며(sys/menu.md) 056 시드가
 * 이 메뉴를 이미 켜 두었다.
 */
export const BATCH_HISTORY_ENDPOINTS = {
  getList: '/system/service/batchHistory/getList',
  /**
   * <b>탭 둘을 한 번에 가져온다</b> — 결과 상세(`RSLT_DTL_MSG`)와 오류(`ERR_MSG`). screen/admin/system/batch/history/requirements.md.
   *
   * <p>탭마다 부르지 않는 이유는 screen/admin/system/log/access/requirements.md 와 같다 — <b>두 번째가 실패했을 때 절반만 열린
   * 모달</b>이 된다. 여기는 둘이 <b>같은 행의 두 컬럼</b>이라 조인조차 없다.
   */
  getDetail: '/system/service/batchHistory/getDetail',
  /**
   * 중단 요청(BATCH-05) — <b>요청까지가 화면의 책임</b>이다(screen/admin/system/batch/history/requirements.md).
   *
   * <p>협조적 중단이라 즉시 멈추지 않을 수 있고, 그래서 확인 문구가 *"중단합니다"* 가
   * 아니다 — 말하지 않으면 안 멈췄을 때 화면이 거짓말한 것이 된다.
   */
  cancel: '/system/service/batchHistory/cancel',
} as const;
