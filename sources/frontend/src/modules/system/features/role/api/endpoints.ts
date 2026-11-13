// @feat FEAT-502
/**
 * 역할 관리 API — screen/admin/system/role/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/role` → `@/modules/system/features/role` 를 따라와 여기를 읽는다.
 *
 * <p><b>셋째가 `roleAuth` 다 — 오타가 아니다.</b> screen/admin/system/role/requirements.md 가 하단을 *"§2.5 와 같은 그리드"*
 * 로 정했으므로 §3.7 이 *"경로도 같아야 한다"* 고 못박았다: `role/getUserList` 를 따로 두면
 * **같은 질문에 답이 둘**이 된다. 이 한 줄이 `SYS_MENU_API` 에 **`ROLE_MGMT` 행을 하나 더**
 * 붙이며, 그것이 시드가 다대다인 이유의 실물이다(sys/menu.md).
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀 자체가 없다 — sys/menu.md 의 액션 열이 `R S` 이고,
 * `ROLE_AUTH_MGMT` 에 이어 **`EXPORT` 가 없는 두 번째 화면**이다(screen/admin/system/role/requirements.md).
 */
export const ROLE_ENDPOINTS = {
  getList: '/system/service/role/getList',
  save: '/system/service/role/save',
  /** 하단 pane — **screen/admin/system/role/requirements.md 과 같은 것**이다(screen/admin/system/role/requirements.md). */
  getAffectedUsers: '/system/service/roleAuth/getAffectedUsers',
} as const;
