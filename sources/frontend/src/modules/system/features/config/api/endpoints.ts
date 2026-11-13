// @feat FEAT-506
/**
 * 시스템 환경설정 API — screen/config/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/config` → `@/modules/system/features/config` 를 따라와 여기를 읽는다.
 *
 * <p><b>`exportExcel` 이 없다.</b> 감춘 것이 아니라 이 화면에 `EXPORT` 액션 자체가 없다
 * (sys/menu.md) — 키 목록은 `yml` 에 있고 바뀐 값은 *변경 여부* 조회로 화면에서 끝난다.
 * 수십 건짜리 목록이다.
 *
 * <p><b>되돌리기용 URL 도 없다.</b> 일괄 저장이라 수정과 삭제가 `save` 하나로 나간다
 * (UX-14) — 쪼개면 저장 버튼 하나가 두 번 나가고, 둘 중 하나만 실패하는 상태가 생긴다.
 */
export const CONFIG_ENDPOINTS = {
  getList: '/system/service/config/getList',
  /** 조회조건의 접두사 콤보가 부른다 — 목록 응답에 실리지 않는다(screen/config/requirements.md). */
  getPrefixes: '/system/service/config/getPrefixes',
  save: '/system/service/config/save',
} as const;
