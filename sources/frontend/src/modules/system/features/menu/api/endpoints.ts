// @feat FEAT-503
/**
 * 메뉴 트리 API — screen/admin/system/menu/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/menu` → `@/modules/system/features/menu` 를 따라와 여기를 읽는다.
 *
 * <p><b>여덟인 것이 이 화면의 모양이다.</b> 조회 하나 · 쓰기 넷 · 탭 셋.
 *
 * <p><b>쓰기가 넷인 것이 즉시 저장의 값이다</b>(screen/admin/system/menu/requirements.md). 위치 변경은 즉시일 수밖에 없고
 * (형제 전체 재작성 + `@CustomLock(WAIT)` — sys/menu.md) 한 그리드 안에서 일괄과 즉시를 섞지
 * 않으므로 전부 즉시가 됐다. 저장 버튼이 없는 대신 경로가 연산마다 하나다.
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀은 있지만(sys/menu.md 의 액션 열이 `R S E` 다) 그것은
 * **프론트 생성**이다(frontend/grid.md · playbook/excel-download.md 첫째 · screen/admin/system/menu/requirements.md) — 서버가 낼 것이 없다.
 */
export const MENU_ENDPOINTS = {
  getTree: '/system/service/menu/getTree',
  /** 신규 노드 — 모달이 받는 칸은 `MENU_CD` 하나다(screen/admin/system/menu/requirements.md). */
  saveNode: '/system/service/menu/saveNode',
  /** 속성 — **셀 하나에 요청 하나**다(screen/admin/system/menu/requirements.md). */
  save: '/system/service/menu/save',
  /** 위치 — 드래그 결과의 **형제 목록**이다(sys/menu.md). */
  saveTree: '/system/service/menu/saveTree',
  deleteNode: '/system/service/menu/deleteNode',
  getApiList: '/system/service/menu/getApiList',
  getAccessibleUsers: '/system/service/menu/getAccessibleUsers',
  getRoleList: '/system/service/menu/getRoleList',
} as const;
