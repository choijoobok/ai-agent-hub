// @feat FEAT-502
/**
 * 개인 예외 권한 API — screen/admin/system/user-auth/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/user-auth` → `@/modules/system/features/userauth` 를 따라와 여기를 읽는다.
 *
 * <p><b>셋인데 화면은 넷이다.</b> 좌(사용자) · 우(트리) · 하(예외 목록) 셋 중 <b>우와 하가
 * 경로 하나</b>를 나눠 쓴다 — screen/admin/system/user-auth/requirements.md 가 둘을 *"같은 데이터의 두 표시"* 로 정했고, 표시가
 * 둘이라고 원본이 둘이 되면 **같은 질문에 답이 둘**이 된다. `ROLE_MGMT` 의 하단이
 * `roleAuth/getAffectedUsers` 를 그대로 부르는 것과 같은 규칙이다(screen/menu/requirements.md).
 *
 * <p><b>`exportExcel` 이 없다.</b> 액션 열은 `R S E` 인데(sys/menu.md) `E` 에 대응하는 경로가
 * 없다 — 엑셀은 프론트가 만들고(playbook/excel-download.md) `EXPORT` 는 `SYS_MENU.EXPORT_YN` 이 선언한다
 * (sys/menu.md).
 *
 * <p><b>`USER_MGMT` 의 탭 2 와 경로를 나눈다</b>(screen/admin/system/user-auth/requirements.md). 거기는 *"이 사람이 무슨 예외를 갖고
 * 있나"* 이고 여기는 *"이 사람에게 예외를 준다"* 다 — 편집을 탭에 두지 않는 이유는
 * **사용자 관리 권한이 있다고 예외 권한 부여 권한이 따라오면 안 되기** 때문이다.
 */
export const USER_AUTH_ENDPOINTS = {
  /** 좌 — 고르기만 하는 목록이다. **못 쓰는 계정도 나온다**(screen/admin/system/user-auth/requirements.md). */
  getList: '/system/service/userAuth/getList',
  /** 우 + 하 — 메뉴 계층 전건 · 개인 예외(기간·사유) · 역할 경유. **페이징도 조건도 없다**. */
  getMenuTree: '/system/service/userAuth/getMenuTree',
  /** **저장 대상은 예외 목록이다** — 트리는 그 투영이다(screen/admin/system/user-auth/requirements.md). */
  save: '/system/service/userAuth/save',
} as const;
