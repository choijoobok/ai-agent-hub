// @feat FEAT-502
/**
 * 역할 권한 관리 API — screen/admin/system/role-auth/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/role-auth` → `@/modules/system/features/roleauth` 를 따라와 여기를 읽는다.
 *
 * <p><b>넷이고 그중 하나는 옆 화면도 부른다.</b> `getAffectedUsers` 는 `ROLE_MGMT` 의 하단
 * pane 이기도 하며(screen/admin/system/role-auth/requirements.md — *"§2.5 와 같은 그리드"*), §3.7 이 *"경로도 같아야 한다"* 고
 * 못박았다. 그래서 `features/role/api/endpoints.ts` 가 **같은 URL 을 한 번 더 선언**하고,
 * 시드에는 `SYS_MENU_API` 행이 **둘** 생긴다(sys/menu.md — 매핑은 다대다다).
 *
 * <p><b>`getSaveImpact` 같은 경로가 없다.</b> 저장 확인의 셋째 줄(*공집합이 되어 차단될
 * 사람*)은 새 질문이 아니라 **하단 그리드가 이미 답한 것**이다 — `hasPersonalGrant` ·
 * `hasOtherRole` 두 열이 그 계산의 입력이며 화면이 그 자리에서 센다(§2.5 · 목업 §4 주석 6).
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀 자체가 없다 — sys/menu.md 의 액션 열이 `R S` 이고,
 * screen/admin/system/user/requirements.md 의 *"액션 미정의"* 가 **화면 단위로 나타난 첫 자리**다(②가 통째로 빈다).
 */
export const ROLE_AUTH_ENDPOINTS = {
  /** 좌 — 고르기만 하는 목록이다. 비활성 역할도 나온다(screen/admin/system/role-auth/requirements.md). */
  getList: '/system/service/roleAuth/getList',
  /** 우 — 메뉴 계층 전건 + 이 역할의 부여 상태. **페이징도 조건도 없다**(screen/admin/system/user/requirements.md). */
  getMenuTree: '/system/service/roleAuth/getMenuTree',
  /** **저장 대상은 트리뿐이다** — screen/admin/system/user/requirements.md 의 개인 예외와 갈리는 자리(screen/admin/system/role-auth/requirements.md). */
  save: '/system/service/roleAuth/save',
  /** 하 — 영향 인원. `ROLE_MGMT` 에서는 *보유 사용자* 라 부른다(screen/admin/system/role-auth/requirements.md). */
  getAffectedUsers: '/system/service/roleAuth/getAffectedUsers',
} as const;
