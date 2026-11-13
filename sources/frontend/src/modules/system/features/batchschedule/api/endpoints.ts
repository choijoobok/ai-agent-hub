// @feat FEAT-508
/**
 * 배치 스케줄 관리 API — screen/admin/system/batch/schedule/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/batch/schedule` → `@/modules/system/features/batchschedule` 를 따라와 여기를 읽는다.
 *
 * <p><b>폴더가 인가 경계다</b>(seed.md). 실행 이력과 한 폴더에 두면 두 화면의 엔드포인트가
 * 서로에게 매핑되어 <b>조회 전용 화면의 권한이 등록 저장을 연다</b> — `features/log` 를
 * 갈랐던 것이 그 사고였다(screen/admin/system/log/access/requirements.md). 여기서는 특히 나쁘다: `BATCH_HISTORY` 는
 * <b>저장이 없는 화면</b>인데(screen/admin/system/batch/schedule/requirements.md) 합치면 `save` 가 그쪽에도 붙는다.
 *
 * <p><b>`exportExcel` 이 없다 — 엑셀 자체가 없다.</b> sys/menu.md 의 액션 열이 `R S X` 이며
 * <b>②가 통째로 사라지는 첫 화면</b>이다(frontend/grid.md · screen/admin/system/batch/schedule/requirements.md). 등록이 수십 건이고
 * <b>원본이 화면</b>이라(시드가 아니다) 내려받아도 결국 화면에서 고쳐야 하고,
 * `PARAM_JSON` 중괄호가 셀에 들어간 파일은 읽기도 어렵다.
 */
export const BATCH_SCHEDULE_ENDPOINTS = {
  getList: '/system/service/batchSchedule/getList',
  save: '/system/service/batchSchedule/save',
  /**
   * `BEAN_ID` 셀렉트의 후보 — <b>스프링 컨텍스트가 원본</b>이다(screen/admin/system/batch/schedule/requirements.md).
   *
   * <p>시드가 아니다. 시드로 두면 배포마다 구현과 목록이 어긋날 수 있고, 어긋나면 화면이
   * <i>고를 수 있는데 실행되지 않는</i> 빈을 보여준다.
   */
  getBeanList: '/system/service/batchSchedule/getBeanList',
  /**
   * `TIMEZONE` 셀렉트의 후보 — <b>tzdb 가 원본</b>이다(screen/admin/system/batch/schedule/requirements.md §4-1).
   *
   * <p>후보를 좁히지 않는다 — 자주 쓰는 것만 골라 두면 <b>고객사가 바뀔 때마다 소스를
   * 고쳐야</b> 한다. 맨 앞이 업무 시간대이고 나머지는 오름차순이다.
   */
  getTimezoneList: '/system/service/batchSchedule/getTimezoneList',
  /** 하단 pane — 이 배치의 최근 실행 이력(screen/admin/system/batch/schedule/requirements.md). 페이징이 없다. */
  getRecentHistory: '/system/service/batchSchedule/getRecentHistory',
  /**
   * 즉시 실행 — 액션은 <b>`EXECUTE`</b> 다(경로 접두가 정한다 — backend/api.md).
   *
   * <p>`SYS_SCHEDULE_BATCH` 를 <b>읽기만</b> 하고 쓰는 것은 이력이다 — screen/admin/system/user/requirements.md 의
   * 기준(*"우리 테이블의 컬럼을 쓰는가"*)으로 봐도 저장이 아니다(screen/admin/system/batch/schedule/requirements.md).
   */
  runNow: '/system/service/batchSchedule/runNow',
} as const;
