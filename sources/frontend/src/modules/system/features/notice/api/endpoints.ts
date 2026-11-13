// @feat FEAT-509
/**
 * 공지사항 관리 API — screen/admin/system/notice/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/notice` → `@/modules/system/features/notice` 를 따라와 여기를 읽는다.
 *
 * <p><b>폴더가 인가 경계다</b>(seed.md). 요청함과 한 폴더에 두면 두 화면의 엔드포인트가
 * 서로에게 매핑되어 <b>공지 권한이 요청 처리를 연다</b> — 요청 처리는 남의 요청을
 * 판정하는 일이라 그 사고의 대가가 크다.
 *
 * <p><b>표시 측이 여기 없다.</b> 공지 팝업과 알림 모달의 공지 탭은 전 사용자에게 열리는
 * 화면이라 `/api/common/**` 이며 `SYS_API` 스캔 대상이 아니다(backend/api.md · screen/_standalone/README.md) —
 * sys/menu.md 이 NOTI-01 을 *관리는 권한이 걸리는 화면, 팝업은 전 사용자에게 뜨는 모달* 로
 * 쪼갠 것이 여기서 파일의 갈림으로 나타난다.
 *
 * <p><b>`exportExcel` 이 없다 — 엑셀 자체가 없다.</b> sys/menu.md 의 액션 열이 `R S` 이며
 * ②가 감춰진다(frontend/grid.md). 근거가 배치와 다르다(screen/admin/system/notice/requirements.md): 저쪽은 *원본이 화면* 이었고
 * 여기는 <b>본문이 `nclob` 이라 엑셀 셀 상한에서 잘린다</b>(frontend/grid.md).
 */
export const NOTICE_ENDPOINTS = {
  getList: '/system/service/notice/getList',
  /** 추가 · 수정 · <b>물리 삭제</b>가 한 트랜잭션이다(screen/admin/system/notice/requirements.md). */
  save: '/system/service/notice/save',
} as const;
