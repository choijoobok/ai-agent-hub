// @feat FEAT-501
/**
 * 사용자 관리 API — screen/admin/system/user/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/user` → `@/modules/system/features/user` 를 따라와 여기를 읽는다.
 *
 * <p><b>넷이 `save` 로 시작한다.</b> 잠금 해제·휴면 해제·비밀번호 초기화는 버튼이 셋이지만
 * 액션은 셋 다 `SAVE` 다 — 기준이 *버튼이 위험해 보이는가* 가 아니라 **우리 테이블의 컬럼을
 * 쓰는가**이기 때문이며(screen/admin/system/user/requirements.md), 셋 다 `SYS_USER` 를 쓴다.
 *
 * <p><b>`exportExcel` 이 없다.</b> 액션 열은 `R S E X` 인데 `E` 에 대응하는 경로가 없다 —
 * 엑셀 두 갈래는 프론트가 만들고(playbook/excel-download.md) `EXPORT` 는 `SYS_MENU.EXPORT_YN` 이 선언한다
 * (sys/menu.md). `ROLE_MGMT` 가 ②를 통째로 비운 것과 **반대 방향의 같은 사실**이다: 거기는
 * 엑셀이 없어서 비었고 여기는 엑셀이 있는데 **경로가 없다**.
 *
 * <p><b>`X` 는 아래 디테일 탭 블록에 있다</b>(`killSession` — screen/admin/system/user/requirements.md). 이 화면의 액션
 * 열 `R S E X` 가 그것으로 처음 다 찼고, 권한 트리에서 `—` 로 남아 있던 `EXECUTE` 칸이
 * 부여할 수 있는 칸이 됐다(screen/admin/system/user/requirements.md).
 */
export const USER_ENDPOINTS = {
  /** 목록 — **곧 상세다**(screen/admin/system/user/requirements.md). `getDetail` 이 없는 이유가 그것이다. */
  getList: '/system/service/user/getList',
  /** 셀 편집 일괄 저장. **신규 행의 임시 비밀번호를 돌려준다**(screen/admin/system/user/requirements.md). */
  save: '/system/service/user/save',
  /** `unlock` 이 아니다 — 접두가 액션을 정하고 이것은 저장이다(screen/admin/system/user/requirements.md). */
  saveUnlock: '/system/service/user/saveUnlock',
  /** 메일이 안 되는 환경을 위한 관리자 경로 — 정상 경로는 본인 재설정이다(sys/user-role.md). */
  saveDormantRelease: '/system/service/user/saveDormantRelease',
  /** AUTH-07. **잠금을 풀지 않는다** — 무한 반복을 막는다(screen/_standalone/README.md). */
  savePasswordReset: '/system/service/user/savePasswordReset',

  // ── 디테일 탭 다섯 (screen/admin/system/user/requirements.md) ────────────────────────────────────
  //
  // **탭이 다섯인데 경로가 여덟이다.** 편집이 있는 탭이 둘(보유 역할 · 접속 IP)이고
  // 실행이 있는 탭이 하나(활성 세션)이며, 역할 후보 하나가 더 있다.
  //
  // **`X` 가 여기서 처음 실물이 된다.** `killSession` 은 접두 규칙의 *그 외* 에 걸려
  // `EXECUTE` 가 되고(backend/api.md), 그 판정이 screen/admin/system/user/requirements.md 의 기준과 일치한다 — **우리 테이블의
  // 컬럼을 쓰지 않는다.** 세션 스토어를 비울 뿐이며 `SYS_USER` 는 그대로다. 그래서 위의
  // `saveUnlock` 과 갈린다: 저것은 `LOCK_YN` 을 쓰므로 `unlock` 이면 안 됐다.

  /** 탭 1 — 보유 역할(USER-03). **기간으로 거르지 않는다** — 만료된 부여가 곧 답이다. */
  getRoleList: '/system/service/user/getRoleList',
  /** 〃 저장. 그 사람의 세션에 권한 재계산을 예약한다(backend/security.md). */
  saveRole: '/system/service/user/saveRole',
  /**
   * 부여 콤보와 마스터 조회조건의 후보 — **이 화면이 자기 경로를 갖는다**.
   *
   * <p>`ROLE_MGMT` 의 목록을 부르면 그 요청이 **역할 관리의 `READ`** 를 요구하게 되고
   * (backend/api.md — 매핑이 경로를 따라간다), 그러면 사용자 관리만 가진 사람이 역할을 부여할
   * 수 없다.
   */
  getRoleCandidates: '/system/service/user/getRoleCandidates',

  /**
   * 탭 2 — 개인 예외 조회(AUTHZ-07). **`USER_AUTH_MGMT` 와 경로를 공유하지 않는다**(screen/admin/system/user/requirements.md).
   *
   * <p>반환 형태가 다르다 — 저쪽은 트리 체크 상태를 함께 내야 해 **메뉴 전건**이 실리고,
   * 여기는 **부여된 행만** 나오는 납작한 목록이다. 이름이 screen/admin/system/user/requirements.md 의 표 그대로다.
   *
   * <p>**`save` 가 없다.** 사용자 관리 권한이 있다고 예외 권한 부여 권한이 따라오면 안 되며,
   * 시드 생성기가 `SAVE` 를 만들 수 없으므로 **어느 역할에도 줄 수 없다**.
   */
  getUserMenuAuth: '/system/service/user/getUserMenuAuth',

  /** 탭 3 — 접속 허용 IP(AUTH-08). **꺼진 행도 온다** — 화면이 다시 켜는 곳이다. */
  getIpList: '/system/service/user/getIpList',
  /** 〃 저장. **세션을 건드리지 않는다** — 인가가 아니라 인증의 검사다(sys/user-role.md). */
  saveIp: '/system/service/user/saveIp',

  /** 탭 4 — 활성 세션(AUTH-10). 원천이 DB 가 아니라 **세션 스토어**다(screen/admin/system/user/requirements.md). */
  getSessionList: '/system/service/user/getSessionList',
  /** 〃 강제 종료 — **이 화면의 유일한 `EXECUTE`** 다(sys/menu.md 의 `X`). */
  killSession: '/system/service/user/killSession',

  /** 탭 5 — 유효 권한(screen/admin/system/user/requirements.md). **읽기만 한다** — 관리자 세션을 바꾸지 않는다(backend/security.md). */
  getEffectiveAuthList: '/system/service/user/getEffectiveAuthList',
} as const;
