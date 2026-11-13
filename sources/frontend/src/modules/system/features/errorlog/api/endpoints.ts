// @feat FEAT-507
/**
 * 에러 로그 API — screen/admin/system/log/error/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/log/error` → `@/modules/system/features/errorlog` 를 따라와 여기를 읽는다.
 *
 * <p><b>폴더가 인가 경계다</b>(seed.md). 사용 이력·로그인 이력과 한 폴더에 두면 세 화면의
 * 엔드포인트가 서로에게 매핑되어 <b>한쪽 권한이 다른 쪽을 연다</b> — `features/log` 를
 * 갈랐던 것이 그 사고였다(screen/admin/system/log/access/requirements.md).
 *
 * <p><b>둘뿐이고 둘 다 `get*` 이다.</b> 이력 행은 불변이라 저장도 삭제도 없다(screen/admin/system/log/error/requirements.md) —
 * `save*` 경로가 없으므로 시드 생성기가 `SAVE` 액션을 만들 수 없고, 만들 수 없으므로
 * **어느 역할에도 줄 수 없다**.
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀은 있지만(LOG-08) **프론트 생성**이다(playbook/excel-download.md 첫째).
 */
export const ERROR_LOG_ENDPOINTS = {
  getList: '/system/service/errorLog/getList',
  /**
   * **탭 둘을 한 번에 가져온다** — 스택(자기 테이블)과 요청 전문(사용 이력에서
   * `TRACE_ID` 로). screen/admin/system/log/error/requirements.md.
   *
   * <p>탭마다 부르지 않는 이유가 여기 있다: 스택을 보다가 전문을 보는 것이 진단의 기본
   * 동선이고, 왕복이 둘이면 **두 번째가 실패했을 때 절반만 열린 모달**이 된다.
   */
  getDetail: '/system/service/errorLog/getDetail',
} as const;
