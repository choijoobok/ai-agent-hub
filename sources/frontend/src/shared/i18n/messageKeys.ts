/**
 * 프론트가 쓰는 메시지 키 — sys/message.md.
 *
 * <p><b>이 파일이 곧 "프론트가 어떤 키를 쓰는가" 의 전부다.</b> `generateMessageSeed` 가
 * 이것을 읽어 `SYS_MESSAGE_KEY` 시드를 만들고, 여기서 사라진 키는 다음 생성에서
 * `USE_SCOPE='NONE'`(죽은 키)으로 표시된다 — 그것이 죽은 키 판정의 유일한 경로다(seed.md).
 *
 * <p><b>리터럴을 쓰면 안 된다.</b> `label('shell.logout')` 이라고 적으면 그 키는 시드에
 * 들어가지 않고, 관리 화면에 행이 없어 번역할 방법이 없다 — 화면에는 키 문자열이 그대로
 * 보인다(sys/code.md). ESLint 의 `no-restricted-syntax` 가 `label()` 에 리터럴을 넘기는 것을 막는다.
 *
 * <p><b>JSDoc 이 시드의 `MSG_DESC` 가 된다.</b> 번역하는 사람이 보는 유일한 맥락이므로
 * *"언제 이 문구가 나가는가"* 를 적는다.
 *
 * <p>값은 여기 없다 — 한국어·영어는 `SYS_MESSAGE` 의 것이고 시드가 그 테이블을 열지 않는다.
 * 서버 번들이 닿지 않을 때 쓰는 정적 문구는 `src/locales` 의 것이며 이 목록에 없다.
 */
export const MSG = {
  // @feat FEAT-504
  /** 후보를 조회하려면 먼저 상위 조건을 선택하세요. */
  CODE_COMBO_PARENT: 'codeCombo.parent',
  /** 후보를 불러오는 중입니다. */
  CODE_COMBO_LOADING: 'codeCombo.loading',
  /** 조건에 맞는 후보가 없습니다. */
  CODE_COMBO_EMPTY: 'codeCombo.empty',
  /** 후보 조회 실패 — 다시 조회 */
  CODE_COMBO_RETRY: 'codeCombo.retry',
  /** 현재 조건에서 선택할 수 없는 값 */
  CODE_COMBO_UNSUPPORTED: 'codeCombo.unsupported',
  // @feat FEAT-006
  /** 사용자 그룹 */
  GROUP_TITLE: 'group.title',
  /** 그룹 식별자 */
  GROUP_ID: 'group.id',
  /** 그룹명 */
  GROUP_NAME: 'group.name',
  /** 설명 */
  GROUP_DESCRIPTION: 'group.description',
  /** 사용 상태 */
  GROUP_STATE: 'group.state',
  /** 사용 중 */
  GROUP_USED: 'group.used',
  /** 미사용 */
  GROUP_UNUSED: 'group.unused',
  /** 확인 불가 */
  GROUP_UNKNOWN: 'group.unknown',
  /** 사용자 */
  GROUP_MEMBERS: 'group.members',
  /** 지식 */
  GROUP_KNOWLEDGE: 'group.knowledge',
  /** 스키마 카탈로그 */
  GROUP_CATALOG: 'group.catalog',
  /** API */
  GROUP_API: 'group.api',
  /** 커스텀 도구 */
  GROUP_TOOL: 'group.tool',
  /** 원 소유 기능 연결 전입니다. 소속 편집과 자산 조회는 해당 기능 연결 후 제공됩니다. */
  GROUP_NOT_CONNECTED: 'group.not_connected',
  /** 원천을 확인할 수 없습니다. 빈 목록으로 판단하지 말고 다시 조회해 주세요. */
  GROUP_UNAVAILABLE: 'group.unavailable',
  /** 원 소유 정보의 조회 권한이 없습니다. */
  GROUP_FORBIDDEN: 'group.forbidden',
  /** 그룹을 선택하세요. 신규 그룹은 먼저 저장하세요. */
  GROUP_SELECT: 'group.select',
  /** 변경 사유 */
  GROUP_REASON: 'group.reason',
  /** 변경 사유를 입력하세요. */
  GROUP_REASON_REQUIRED: 'group.reason_required',
  /** 원 명령의 결과가 미확정입니다. 내용을 유지하고 저장을 다시 눌러 결과를 확인하세요. */
  GROUP_PENDING: 'group.pending',
  /** 미확정 원 명령이 있습니다. 원래 입력으로 복구한 뒤 결과를 확인하세요. */
  GROUP_PENDING_CHANGED: 'group.pending_changed',
  /** 모든 소유자의 참조 없음이 확인돼야 삭제할 수 있습니다. */
  GROUP_DELETE_UNKNOWN: 'group.delete_unknown',
  /** 매핑 활성 여부 */
  GROUP_MAPPING: 'group.mapping',
  /** 사용자 아이디 */
  GROUP_LOGIN: 'group.login',
  /** 활성화 여부 */
  GROUP_ACTIVE: 'group.active',
  /** 메모 */
  GROUP_MEMO: 'group.memo',
  /** 자산 식별자 */
  GROUP_ASSET_ID: 'group.asset_id',
  /** 자산명 */
  GROUP_ASSET_NAME: 'group.asset_name',
  /** 대상 시스템명 */
  GROUP_SYSTEM: 'group.system',
  /** 데이터소스명 */
  GROUP_DATASOURCE: 'group.datasource',
  /** 테이블명 */
  GROUP_TABLE: 'group.table',
  /** 테이블 설명 */
  GROUP_TABLE_DESC: 'group.table_desc',

  // ── 공통 ────────────────────────────────────────────────────
  /** 모달·확인 대화의 취소 버튼. */
  COMMON_CANCEL: 'common.cancel',
  /** 모달·확인 대화의 닫기 버튼. */
  COMMON_CLOSE: 'common.close',

  // ── 로그인 (screen/_standalone/README.md) ──────────────────────────────────────────
  /** 로그인 화면의 제목. */
  LOGIN_TITLE: 'login.title',
  /** 로그인 ID 입력 칸의 라벨. */
  LOGIN_ID: 'login.id',
  /** 로그인 ID 를 비운 채 제출했을 때. */
  LOGIN_ID_REQUIRED: 'login.id.required',
  /** 비밀번호 입력 칸의 라벨. */
  LOGIN_PASSWORD: 'login.password',
  /** 비밀번호를 비운 채 제출했을 때. */
  LOGIN_PASSWORD_REQUIRED: 'login.password.required',
  /** 로그인 제출 버튼. */
  LOGIN_SUBMIT: 'login.submit',
  /** 5xx 응답에 붙는 문의 번호 안내 — `{traceId}`. */
  LOGIN_TRACE_ID: 'login.traceId',
  /** 로그인 폼 아래의 재설정 진입 링크. */
  LOGIN_FORGOT_PASSWORD: 'login.forgotPassword',
  /**
   * 잠김·휴면 안내 배너 **안에** 붙는 재설정 링크(screen/_standalone/README.md · login 요건서 §6-4).
   *
   * <p>폼 아래의 평소 링크와 문구가 다른 것이 의도다 — 저것은 *어쩌다 잊은 사람* 에게
   * 하는 말이고 이것은 **지금 막힌 사람**에게 하는 말이다.
   */
  LOGIN_LOCKED_RESET: 'login.lockedReset',

  // ── 비밀번호 재설정 (AUTH-07 · login 요건서 §6-3) ─────────────
  //
  // 같은 라우트의 단계 전환이라 화면 이름이 아니라 **단계**로 키를 나눈다.

  /** 재설정 화면의 제목 — 세 단계가 공유한다. */
  RESET_TITLE: 'reset.title',
  /** 단계 표시의 ① 이름. */
  RESET_STEP_REQUEST: 'reset.step.request',
  /** 단계 표시의 ② 이름. */
  RESET_STEP_VERIFY: 'reset.step.verify',
  /** 단계 표시의 ③ 이름. */
  RESET_STEP_DONE: 'reset.step.done',
  /** ① 안내 — 아이디만 받고 메일 주소는 묻지 않는다는 사실을 말한다. */
  RESET_REQUEST_DESC: 'reset.request.desc',
  /** ① 제출 버튼. */
  RESET_SEND: 'reset.send',
  /**
   * ① 성공 안내 — **계정이 있든 없든 이 문구가 나간다**.
   *
   * <p>*"등록된 메일로 보냈습니다"* 라고 단정하지 않는 것이 요건이다. 마스킹한
   * 주소도 보여주지 않는다 — 그것만으로 계정의 존재가 드러난다.
   */
  RESET_SENT: 'reset.sent',
  /** ② 안내 — 유효 시간을 말한다. `{minutes}`. */
  RESET_VERIFY_DESC: 'reset.verify.desc',
  /** 인증번호 입력 칸의 라벨. */
  RESET_CODE: 'reset.code',
  /** 인증번호를 비운 채 제출했을 때. */
  RESET_CODE_REQUIRED: 'reset.code.required',
  /** ② 제출 버튼. */
  RESET_VERIFY: 'reset.verify',
  /** 재발송 버튼 — 쿨다운이 끝난 뒤에만 눌린다. */
  RESET_RESEND: 'reset.resend',
  /** 재발송까지 남은 시간 — `{seconds}`. **서버가 아니라 화면이 센다**. */
  RESET_RESEND_IN: 'reset.resendIn',
  /** ③ 안내 — 새 비밀번호를 정하면 잠금도 함께 풀린다는 사실을 말한다. */
  RESET_DONE_DESC: 'reset.done.desc',
  /** 새 비밀번호 입력 칸의 라벨. */
  RESET_NEW_PWD: 'reset.newPwd',
  /** 새 비밀번호를 비운 채 제출했을 때. */
  RESET_NEW_PWD_REQUIRED: 'reset.newPwd.required',
  /** 새 비밀번호 확인 칸의 라벨. */
  RESET_NEW_PWD_CONFIRM: 'reset.newPwd.confirm',
  /** 두 칸의 값이 다를 때 — **화면이 판정한다**(서버까지 보내지 않는다). */
  RESET_NEW_PWD_MISMATCH: 'reset.newPwd.mismatch',
  /** ③ 제출 버튼. */
  RESET_SUBMIT: 'reset.submit',
  /** 재설정을 마치고 로그인 폼으로 돌아왔을 때의 안내. */
  RESET_COMPLETED: 'reset.completed',
  /** 어느 단계에서든 로그인 폼으로 돌아가는 링크. */
  RESET_BACK_TO_LOGIN: 'reset.backToLogin',

  // ── 재인증 (backend/session.md) ─────────────────────────────
  /** 재인증 모달의 제목 — 세션이 끊긴 것을 알린다. */
  REAUTH_TITLE: 'reauth.title',
  /** 재인증 모달의 안내 — 탭이 그대로임을 알려 사용자를 안심시킨다. */
  REAUTH_DESC: 'reauth.desc',
  /** 재인증 모달에서 읽기 전용으로 보이는 아이디 칸의 라벨. */
  REAUTH_LOGIN_ID: 'reauth.loginId',
  /** 재인증 제출 버튼 — "로그인" 이 아니라 이어서 한다는 뜻이다. */
  REAUTH_SUBMIT: 'reauth.submit',
  /** 다른 계정으로 로그인하려는 사람이 누르는 링크 — 작업 중이던 탭을 버린다. */
  REAUTH_OTHER_ACCOUNT: 'reauth.otherAccount',
  /** 재인증이 서버 코드 없이 실패했을 때의 마지막 문구. */
  REAUTH_RETRY: 'reauth.retry',

  // ── 세션 ────────────────────────────────────────────
  /** 권한이 통째로 사라져 로그아웃할 때의 토스트. */
  SESSION_NO_AUTHORITY: 'session.noAuthority',
  /** 권한 변경으로 탭이 닫혔을 때의 토스트 — `{tabs}` 는 닫힌 탭 제목 목록. */
  SESSION_AUTHZ_CHANGED: 'session.authzChanged',
  /** 비밀번호를 바꿔야 계속할 수 있는 제한 세션의 안내 제목(체인 #5). */
  SESSION_PASSWORD_CHANGE_TITLE: 'session.passwordChange.title',
  /** 초기화·만료된 비밀번호를 변경해야 업무를 계속할 수 있다는 안내. */
  SESSION_PASSWORD_CHANGE_DESC: 'session.passwordChange.desc',

  // ── 셸 (frontend/tabs.md) ────────────────────────────────────────
  /** 접힌 사이더를 펼치는 버튼의 대체 텍스트. */
  SHELL_SIDER_EXPAND: 'shell.sider.expand',
  /** 펼친 사이더를 접는 버튼의 대체 텍스트. */
  SHELL_SIDER_COLLAPSE: 'shell.sider.collapse',
  /** 라이트 테마의 이름 — 설정 모달의 모드 선택지다(frontend/tabs.md). */
  SHELL_THEME_LIGHT: 'shell.theme.light',
  /** 다크 테마의 이름. */
  SHELL_THEME_DARK: 'shell.theme.dark',
  /** OS 설정을 따르는 테마의 이름. */
  SHELL_THEME_SYSTEM: 'shell.theme.system',
  /** 헤더의 로그아웃 버튼(frontend/tabs.md — 독립 버튼이다). */
  SHELL_LOGOUT: 'shell.logout',

  // ── 메뉴 검색 (frontend/tabs.md) ───────────────────────────────
  /** 진입 모달 검색 탭의 placeholder — 사이더의 검색 칸이 사라져 이제 모달만 쓴다. */
  SHELL_SEARCH_PLACEHOLDER: 'shell.search.placeholder',
  /** 트리의 폴더를 전부 편다. */
  SHELL_TREE_EXPAND_ALL: 'shell.tree.expandAll',
  /** 사람이 연 폴더를 전부 접는다 — 활성 탭의 조상은 남는다. */
  SHELL_TREE_COLLAPSE_ALL: 'shell.tree.collapseAll',
  /** 결과가 둘 이상일 때 뜨는 모달의 제목. */
  /** 진입 모달의 제목 — 검색·즐겨찾기·최근·자주가 한 창이다(frontend/tabs.md). */
  SHELL_ENTRY_TITLE: 'shell.entry.title',
  SHELL_ENTRY_TAB_SEARCH: 'shell.entry.tab.search',
  SHELL_ENTRY_TAB_FAVORITE: 'shell.entry.tab.favorite',
  SHELL_ENTRY_TAB_RECENT: 'shell.entry.tab.recent',
  SHELL_ENTRY_TAB_FREQUENT: 'shell.entry.tab.frequent',
  /** 탭 안에서 그 목록만 거른다 — 전체 메뉴 검색과 다른 일이다(frontend/tabs.md). */
  SHELL_ENTRY_FILTER: 'shell.entry.filter',
  /** 아직 묻지 않았다 — 물었는데 없는 것과 다르다. */
  SHELL_ENTRY_SEARCH_PROMPT: 'shell.entry.search.prompt',
  SHELL_ENTRY_EMPTY_RECENT: 'shell.entry.empty.recent',
  /** 즐겨찾기가 비었다 — 넣는 방법까지 말한다. */
  SHELL_ENTRY_EMPTY_FAVORITE: 'shell.entry.empty.favorite',
  /** 자주 쓰는 메뉴가 비었다 — 쓰다 보면 저절로 찬다. */
  SHELL_ENTRY_EMPTY_FREQUENT: 'shell.entry.empty.frequent',
  /** 자주 쓰는 메뉴를 센 기간 — `{from}` 은 시작 달이다. */
  SHELL_ENTRY_FREQUENT_SINCE: 'shell.entry.frequent.since',
  /** 그 메뉴를 연 횟수 — `{count}`. 순위의 근거다. */
  SHELL_ENTRY_FREQUENT_COUNT: 'shell.entry.frequent.count',
  SHELL_SEARCH_TITLE: 'shell.search.title',
  /** 일치하는 메뉴가 없을 때 — 빈 상태(UX-11). */
  SHELL_SEARCH_EMPTY: 'shell.search.empty',
  /** 결과 건수 — `{count}`. */
  SHELL_SEARCH_COUNT: 'shell.search.count',

  // ── 설정 모달 (frontend/tabs.md) ───────────────────────────────────────
  /** 헤더의 설정 버튼과 모달의 제목. */
  SETTINGS_TITLE: 'settings.title',
  /** 그룹 — 일반. */
  SETTINGS_GROUP_GENERAL: 'settings.group.general',
  /** 그룹 — 테마. */
  SETTINGS_GROUP_THEME: 'settings.group.theme',
  /** 그룹 — 글꼴. */
  SETTINGS_GROUP_FONT: 'settings.group.font',
  /** 그룹 — 그리드. 전역 표시 속성이며 컬럼 구성(UX-06)과 다른 것이다. */
  SETTINGS_GROUP_GRID: 'settings.group.grid',
  /** 이 그룹만 기본값으로 되돌린다. */
  SETTINGS_RESET_GROUP: 'settings.reset.group',
  /** 전부 기본값으로. */
  SETTINGS_RESET_ALL: 'settings.reset.all',
  /** 언어 — 유일하게 서버에 저장되는 항목이다(frontend/tabs.md). */
  SETTINGS_LANGUAGE: 'settings.language',
  /** 언어를 바꾸기 전 확인의 제목 — 재로드는 모든 탭을 닫는 것이다. */
  SETTINGS_LANGUAGE_CONFIRM_TITLE: 'settings.language.confirm.title',
  /** 그 본문. */
  SETTINGS_LANGUAGE_CONFIRM_BODY: 'settings.language.confirm.body',
  /** 헤더를 감췄다가 위 가장자리에 마우스를 대면 화면 위로 내려오게 한다. */
  SETTINGS_HEADER_AUTOHIDE: 'settings.headerAutoHide',
  /** 사이더를 감췄다가 왼쪽 가장자리에 마우스를 대면 화면 위로 나오게 한다. */
  SETTINGS_SIDER_AUTOHIDE: 'settings.siderAutoHide',
  /** 라이트 · 다크 · 시스템 따름. */
  SETTINGS_THEME_MODE: 'settings.themeMode',
  /** 라이트일 때의 바탕 농도(%) — 100% 가 완전한 흰색, 낮출수록 회색이다. */
  SETTINGS_WHITE_DENSITY: 'settings.density.white',
  /**
   * 다크일 때의 바탕 농도(%) — 100% 가 완전한 검은색, 낮출수록 회색이다.
   *
   * <p><b>같은 값의 다른 이름이다.</b> 눈금도 저장되는 값도 하나이고 <i>무엇의 농도인가</i>
   * 만 모드를 따라 갈린다 — 한 이름으로 부르면 절반은 거짓말이 된다(frontend/tabs.md).
   */
  SETTINGS_BLACK_DENSITY: 'settings.density.black',
  /** 강조색 — 버튼과 그리드의 선택 표시가 이 색에서 나온다. */
  SETTINGS_PRIMARY_COLOR: 'settings.primaryColor',
  /** 강조색을 판의 기본값으로 되돌리는 항목. */
  SETTINGS_PRIMARY_DEFAULT: 'settings.primaryColor.default',
  /** 고른 색의 대비가 모자랄 때 — 막지는 않는다(frontend/tabs.md). */
  SETTINGS_PRIMARY_CONTRAST: 'settings.primaryColor.contrast',
  /** 중립 회색의 색조. */
  SETTINGS_COLOR_TONE: 'settings.colorTone',
  /** 색 톤 — 중립. */
  SETTINGS_TONE_NEUTRAL: 'settings.colorTone.neutral',
  /** 색 톤 — 따뜻함(노란 기미). */
  SETTINGS_TONE_WARM: 'settings.colorTone.warm',
  /** 색 톤 — 차가움(파란 기미). */
  SETTINGS_TONE_COLD: 'settings.colorTone.cold',
  /** 색 톤 — 초록(눈이 덜 피로하다는 선택지다). */
  SETTINGS_TONE_GREEN: 'settings.colorTone.green',
  /** AntD `compactAlgorithm` — 여백을 줄인다. */
  SETTINGS_COMPACT: 'settings.compact',
  /** 글꼴 이름. */
  SETTINGS_FONT_FAMILY: 'settings.fontFamily',
  /** 판의 기본 글꼴을 쓴다. */
  SETTINGS_FONT_DEFAULT: 'settings.fontFamily.default',
  /** 설치된 글꼴 목록을 읽는 버튼 — 누를 때 권한 프롬프트가 뜬다(frontend/tabs.md). */
  SETTINGS_FONT_LOAD_LOCAL: 'settings.fontFamily.loadLocal',
  /** 목록을 못 읽었을 때 — 기본 목록으로 떨어진다. 기능 실패가 아니다. */
  SETTINGS_FONT_LOCAL_DENIED: 'settings.fontFamily.localDenied',
  /** 기본 글자 크기. */
  SETTINGS_FONT_SIZE: 'settings.fontSize',
  /** 본문 글자의 굵기 — 크기와 다른 축이다. */
  SETTINGS_FONT_WEIGHT: 'settings.fontWeight',
  /** 글자 굵기 — 얇게(300). 글꼴에 Light 가 없으면 보통과 같아 보인다. */
  SETTINGS_FONT_WEIGHT_LIGHT: 'settings.fontWeight.light',
  /** 글자 굵기 — 보통(400). */
  SETTINGS_FONT_WEIGHT_NORMAL: 'settings.fontWeight.normal',
  /** 글자 굵기 — 굵게(600). 500 은 CSS 매칭이 400 으로 내려가 보통과 같아진다. */
  SETTINGS_FONT_WEIGHT_BOLD: 'settings.fontWeight.bold',
  /** 그리드 헤더 높이. */
  SETTINGS_GRID_HEADER_HEIGHT: 'settings.grid.headerHeight',
  /** 그리드 행 높이. */
  SETTINGS_GRID_ROW_HEIGHT: 'settings.grid.rowHeight',
  /** 그리드 글자 크기 — 기본 크기와 별개다. */
  SETTINGS_GRID_FONT_SIZE: 'settings.grid.fontSize',
  /** 슬라이더 왼쪽 버튼 — 아이콘뿐이라 읽어 줄 이름이 필요하다. */
  SETTINGS_STEP_DOWN: 'settings.step.down',
  /** 슬라이더 오른쪽 버튼 — 아이콘뿐이라 읽어 줄 이름이 필요하다. */
  SETTINGS_STEP_UP: 'settings.step.up',

  // ── 프로필 모달 (frontend/tabs.md) ─────────────────────────────────────
  //
  // 탭 넷이 전부 "나에 대한 것" 이다 — 내 정보 · 비밀번호 · 로그인 이력 · 내 역할.
  /** 헤더의 사용자명을 누르면 열리는 모달의 제목. */
  PROFILE_TITLE: 'profile.title',
  /** 탭 이름 — 내 정보. */
  PROFILE_TAB_INFO: 'profile.tab.info',
  /** 탭 이름 — 비밀번호 변경. */
  PROFILE_TAB_PASSWORD: 'profile.tab.password',
  /** 탭 이름 — 내 로그인 이력. */
  PROFILE_TAB_HISTORY: 'profile.tab.history',
  /** 탭 이름 — 내 역할. */
  PROFILE_TAB_ROLES: 'profile.tab.roles',
  // 내 정보 — 조회와 수정이 한 화면이다(frontend/tabs.md).
  /** 읽기 전용 — 자연키라 바꿀 수 없다(sys/user-role.md). */
  PROFILE_USER_ID: 'profile.userId',
  /** 이름 칸의 라벨. 편집할 수 있는 둘 중 하나다. */
  PROFILE_USER_NM: 'profile.userNm',
  /** 이름을 비운 채 저장했을 때 — 화면이 판정한다. */
  PROFILE_USER_NM_REQUIRED: 'profile.userNm.required',
  /** 메일 칸의 라벨. 비울 수 있다. */
  PROFILE_EMAIL: 'profile.email',
  /** 메일 형식이 아닐 때. 비우는 것은 정상이므로 값이 있을 때만 본다. */
  PROFILE_EMAIL_INVALID: 'profile.email.invalid',
  /** 언어 — 여기서는 보이기만 한다. 바꾸는 자리는 설정 모달이다(frontend/tabs.md). */
  PROFILE_LOCALE: 'profile.locale',
  /** 계정 사용 기간(체인 #3). 관리자의 축이라 읽기 전용이다(sys/user-role.md). */
  PROFILE_VALID_PERIOD: 'profile.validPeriod',
  /** 마지막 로그인 시각 — 읽기 전용이다. */
  PROFILE_LAST_LOGIN: 'profile.lastLogin',
  /** 기간이나 값이 없을 때 — 빈 칸으로 두면 조회가 실패한 것처럼 보인다. */
  PROFILE_NONE: 'profile.none',
  /** 내 정보 저장 버튼. */
  PROFILE_SAVE: 'profile.save',
  /** 저장이 끝났을 때의 토스트. */
  PROFILE_SAVED: 'profile.saved',

  // 비밀번호 변경 — AUTH-05.
  /** 현재 비밀번호 칸의 라벨 — 확인용이다. */
  PROFILE_PWD_CURRENT: 'profile.pwd.current',
  /** 새 비밀번호 칸의 라벨. */
  PROFILE_PWD_NEW: 'profile.pwd.new',
  /** 새 비밀번호 확인 칸의 라벨. */
  PROFILE_PWD_CONFIRM: 'profile.pwd.confirm',
  /** 새 비밀번호와 확인이 다르다 — 서버까지 보내지 않고 화면이 잡는다. */
  PROFILE_PWD_CONFIRM_MISMATCH: 'profile.pwd.confirmMismatch',
  /** 비운 칸이 있을 때. */
  PROFILE_PWD_REQUIRED: 'profile.pwd.required',
  /** 정책 안내 — `{minLength}` · `{kinds}`. 거절당하기 전에 규칙을 말하는 유일한 자리다. */
  PROFILE_PWD_RULE: 'profile.pwd.rule',
  /** 재사용 금지 안내 — `{count}`. 0 이면 표시하지 않는다. */
  PROFILE_PWD_RULE_REUSE: 'profile.pwd.rule.reuse',
  /** 마지막으로 바꾼 날 — `{when}`. 만료 주기를 쓰지 않을 때 그 자리를 대신한다. */
  PROFILE_PWD_CHANGED_AT: 'profile.pwd.changedAt',
  /** 만료까지 남은 날 — `{days}`. */
  PROFILE_PWD_EXPIRES_IN: 'profile.pwd.expiresIn',
  /** 이미 지났다 — 제한 세션이라는 뜻이다(체인 #5). */
  PROFILE_PWD_EXPIRED: 'profile.pwd.expired',
  /** 관리자가 발급한 임시 비밀번호를 쓰고 있다. */
  PROFILE_PWD_TEMPORARY: 'profile.pwd.temporary',
  /** 비밀번호 변경 버튼. */
  PROFILE_PWD_SUBMIT: 'profile.pwd.submit',
  /** 변경이 끝났을 때의 토스트. */
  PROFILE_PWD_DONE: 'profile.pwd.done',

  // 내 로그인 이력 — LOG-02. 보안 기능이며 실패도 함께 보인다(frontend/tabs.md).
  /** 이력 컬럼 — 일시. */
  PROFILE_HISTORY_WHEN: 'profile.history.when',
  /** 이력 컬럼 — 결과. */
  PROFILE_HISTORY_RESULT: 'profile.history.result',
  /** 이력 컬럼 — 접속 IP. */
  PROFILE_HISTORY_IP: 'profile.history.ip',
  /** 이력 컬럼 — 브라우저(User-Agent). */
  PROFILE_HISTORY_AGENT: 'profile.history.userAgent',
  /** 이력이 한 건도 없을 때 — 빈 상태(UX-11). */
  PROFILE_HISTORY_EMPTY: 'profile.history.empty',
  /** 무엇을 보고 있는지 — 실패 기록이 섞여 있는 이유를 말한다. */
  PROFILE_HISTORY_NOTE: 'profile.history.note',
  /** 결과 태그 — 로그인 성공. */
  PROFILE_HISTORY_SUCCESS: 'profile.history.success',
  /** 결과 태그 — 로그인 실패. 내가 한 것이 아니면 신고할 일이다. */
  PROFILE_HISTORY_FAILURE: 'profile.history.failure',
  /** 로그아웃 · 세션 만료 · 강제 종료 · 밀려남 — 실패가 아니라 끝난 것이다(sys/logs.md). */
  PROFILE_HISTORY_ENDED: 'profile.history.ended',

  // 내 역할 — AUTHZ-04. 읽기 전용이다.
  /** 역할 표의 제목. */
  PROFILE_ROLES_TITLE: 'profile.roles.title',
  /** 역할 표의 컬럼 — 역할명. */
  PROFILE_ROLE_NM: 'profile.role.roleNm',
  /** 역할·예외 표의 컬럼 — 유효 기간. */
  PROFILE_ROLE_PERIOD: 'profile.role.period',
  /** 역할·예외 표의 컬럼 — 지금 유효한가. */
  PROFILE_ROLE_STATE: 'profile.role.state',
  /** 지금 유효하다. */
  PROFILE_ROLE_EFFECTIVE: 'profile.role.effective',
  /** 기간이 지났다 — 사용자가 기다린다고 돌아오지 않는다. 관리자에게 갈 일이다. */
  PROFILE_ROLE_EXPIRED: 'profile.role.expired',
  /** 아직 시작 전이다 — 인사 발령을 미리 넣어 둔 경우다(sys/menu.md). */
  PROFILE_ROLE_PENDING: 'profile.role.pending',
  /** 역할 자체가 꺼졌다 — 기간과 다른 원인이라 갈라서 말한다. */
  PROFILE_ROLE_DISABLED: 'profile.role.disabled',
  /** 부여된 역할이 없을 때 — 개인 예외만으로 일하는 사용자가 있다(sys/menu.md). */
  PROFILE_ROLES_EMPTY: 'profile.roles.empty',
  /** 개인 예외 — 역할과 합치지 않는다. 출처가 다르고 고치는 사람이 다르다(sys/menu.md). */
  PROFILE_GRANTS_TITLE: 'profile.grants.title',
  /** 개인 예외 표의 컬럼 — 메뉴. */
  PROFILE_GRANT_MENU: 'profile.grant.menu',
  /** 개인 예외 표의 컬럼 — 액션. */
  PROFILE_GRANT_ACTION: 'profile.grant.action',
  /** 개인 예외 표의 컬럼 — 부여 사유. 부여할 때 필수다(sys/menu.md). */
  PROFILE_GRANT_REASON: 'profile.grant.reason',
  /** 개인 예외가 없을 때 — 대부분의 사용자가 그렇다. */
  PROFILE_GRANTS_EMPTY: 'profile.grants.empty',
  /** 이 탭이 읽기 전용이라는 것과 늘리는 경로가 어디인지 — frontend/tabs.md. */
  PROFILE_GRANTS_NOTE: 'profile.grants.note',
  /** 유효 판정의 기준일 — `{date}`. 브라우저의 오늘이 아니다(sys/INDEX.md). */
  PROFILE_BASIS_DATE: 'profile.basisDate',

  // ── 액션 4종 (backend/security.md) ────────────────────────────────────────
  //
  // 프로필의 개인 예외가 첫 소비처다. `profile.` 접두를 붙이지 않은 것은 권한 관리
  // 화면(AUTHZ-07)이 같은 것을 쓰기 때문이다 — 붙이면 그때 같은 말이 두 키가 된다.
  /** 조회 — 화면 진입, 목록·상세. */
  AUTHZ_ACTION_READ: 'authz.action.READ',
  /** 저장 — 추가·수정·삭제를 포함한다. */
  AUTHZ_ACTION_SAVE: 'authz.action.SAVE',
  /** 출력 — 엑셀 다운로드. */
  AUTHZ_ACTION_EXPORT: 'authz.action.EXPORT',
  /** 절차 발동 — 배치 실행, 승인/반려, 강제 로그아웃. */
  AUTHZ_ACTION_EXECUTE: 'authz.action.EXECUTE',

  // ── 알림 모달 (frontend/tabs.md · screen/_standalone/README.md) ───────────────────────────────
  //
  // 게시판 형태의 탭 모달이다. 순서가 방향이다 — 관리자→전체, 시스템→나, 나→관리자.
  /** 헤더의 알림 버튼과 모달의 제목. */
  SHELL_NOTIFICATION: 'shell.notification',
  /** 탭 이름 — 공지사항(관리자 → 전체). */
  NOTIFICATION_TAB_NOTICE: 'notification.tab.notice',
  /** 탭 이름 — 내 알림(시스템 → 나). 헤더 배지가 세는 것이 이것 하나다(frontend/tabs.md). */
  NOTIFICATION_TAB_MINE: 'notification.tab.mine',
  /** 탭 이름 — 내 요청(나 → 관리자). 권한 신청·잠금 해제 요청. */
  NOTIFICATION_TAB_REQUEST: 'notification.tab.request',
  // `notification.empty.pending` 이 여기 있었다 — 탭 셋이 내용을 갖게 되면서 마지막
  // 사용처가 사라졌다. 지워도 시드는 깨지지 않는다: `008` 은 loadUpdateData 이고 코드에서
  // 사라진 키는 **행이 남은 채 USE_SCOPE 만 NONE** 이 된다(seed.md) — `018` 이 넣은
  // 번역 행의 FK 도 그대로 산다. 실제로 DB 에서 지우는 자리는 메시지 관리 화면이다(screen/admin/system/code/requirements.md).
  /** 목록이 상한에 걸려 잘렸다 — 조용히 자르면 목록이 전부로 읽힌다. */
  NOTIFICATION_TRUNCATED: 'notification.truncated',
  /** 상세에서 목록으로 — **새 탭을 열지 않는다**(frontend/tabs.md). */
  NOTIFICATION_BACK: 'notification.back',

  // ── 공지사항 탭 (screen/_standalone/README.md · screen/admin/system/notice/requirements.md) ───────────────────────────
  //
  // 팝업이 최신 1건만 띄우므로(screen/admin/system/notice/requirements.md C안) **공지 목록의 실질 자리가 이 탭**이다.
  // 읽음 상태가 없다(screen/admin/system/notice/requirements.md) — 미읽음 강조를 쓰지 않는다(NI-03).
  /** 게시 기간이 끝난 공지를 켜서 본다 — 기본은 꺼져 있다(NI-04). */
  NOTIFICATION_NOTICE_INCLUDE_EXPIRED: 'notification.notice.includeExpired',
  /** 게시 중인 공지가 하나도 없다 — 정상 상태다. */
  NOTIFICATION_NOTICE_EMPTY: 'notification.notice.empty',
  /** 상세의 라벨 — 게시일. */
  NOTIFICATION_NOTICE_FROM: 'notification.notice.from',
  /** 상세의 라벨 — 작성자. */
  NOTIFICATION_NOTICE_AUTHOR: 'notification.notice.author',
  /** 게시가 끝난 공지임을 줄에 붙인다 — 흐리게 그리는 것만으로는 이유를 말하지 못한다. */
  NOTIFICATION_NOTICE_EXPIRED: 'notification.notice.expired',

  // ── 내 알림 탭 (frontend/tabs.md) ─────────────────────────────
  //
  // **배지가 세는 유일한 탭**이다. 목록을 열었다고 전체 읽음 처리하지 않는다 —
  // 훑어만 봐도 배지가 사라지면 놓친 알림을 찾을 수 없다.
  /** 받은 알림이 없다. */
  NOTIFICATION_MINE_EMPTY: 'notification.mine.empty',
  /** 하단의 읽지 않음 수 — 배지와 같은 값이다. */
  NOTIFICATION_MINE_UNREAD: 'notification.mine.unread',
  /** 사용자가 **의도적으로 비우는** 경로(frontend/tabs.md). */
  NOTIFICATION_MINE_MARK_ALL: 'notification.mine.markAll',
  /** 상세의 라벨 — 받은 시각. */
  NOTIFICATION_MINE_RECEIVED_AT: 'notification.mine.receivedAt',
  /** 상세의 라벨 — 내용. */
  NOTIFICATION_MINE_CONTENT: 'notification.mine.content',
  /** 알림이 가리키는 화면으로 — `MENU_CD` + 파라미터가 곧 메뉴 진입이다(NI-10). */
  NOTIFICATION_MINE_GOTO: 'notification.mine.goto',
  /**
   * **권한이 없어진 메뉴**로 가는 알림 — 버튼이 비활성이고 이 문구가 이유를 말한다.
   *
   * 눌러서 403 을 보는 것보다 낫다(NI-11). **알림이 권한을 주지 않는다.**
   */
  NOTIFICATION_MINE_GOTO_DENIED: 'notification.mine.gotoDenied',

  // ── 알림의 생산자 (sys/notification.md · screen/admin/system/notice/requirements.md) ────────────────────────
  //
  // 공통코드가 아니다 — 값이 늘면 처리 코드가 함께 늘어야 한다. **라벨만** 메시지 키다.
  /** 요청 처리 결과 — screen/admin/system/notice/requirements.md. */
  NOTI_TYPE_REQUEST: 'noti.type.REQUEST',
  /** 비밀번호 만료 예고 — AUTH-05. */
  NOTI_TYPE_AUTH: 'noti.type.AUTH',
  /** 권한 만료 예정 — AUTHZ-08. */
  NOTI_TYPE_AUTHZ: 'noti.type.AUTHZ',
  /** 배치 실패 — BATCH-06. */
  NOTI_TYPE_BATCH: 'noti.type.BATCH',
  /** 외부 퇴직·조직 이동 사건 알림 — CR-034. */
  NOTI_TYPE_PORTAL_CHANGE: 'noti.type.PORTAL_CHANGE',

  // ── 내 요청 탭 (NOTI-06 사용자 측 · screen/_standalone/README.md) ──────────────────
  //
  // **처리는 여기 없다** — `NOTI_REQUEST` 메뉴의 일이고 권한이 필요하다(NI-13).
  // 유형·상태 라벨은 `notiReq.*`(요청함 화면)와 **같은 키를 쓴다** — 같은 값을 두 곳에서
  // 다르게 번역하면 사용자와 관리자가 다른 말을 본다.
  /** 낸 요청이 없다. */
  NOTIFICATION_REQUEST_EMPTY: 'notification.request.empty',
  /** 새 요청 폼을 연다 — 같은 모달 안에서 바뀐다. */
  NOTIFICATION_REQUEST_NEW: 'notification.request.new',
  /** 폼의 라벨 — 유형. */
  NOTIFICATION_REQUEST_TYPE: 'notification.request.type',
  /** 폼의 라벨 — 요청 내용. */
  NOTIFICATION_REQUEST_CONTENT: 'notification.request.content',
  /** 무엇을 적어야 하는지 — 되묻는 단계가 없다. */
  NOTIFICATION_REQUEST_PLACEHOLDER: 'notification.request.placeholder',
  /** 보내기. */
  NOTIFICATION_REQUEST_SUBMIT: 'notification.request.submit',
  /** 줄에 붙는 신청 시각 — "{date} 신청". */
  NOTIFICATION_REQUEST_AT: 'notification.request.at',
  /** 반려 사유가 줄에 붙는다 — "사유: …". */
  NOTIFICATION_REQUEST_REASON: 'notification.request.reason',
  /**
   * **같은 유형이 접수 상태로 열려 있다** — 새 요청 버튼이 비활성이고 이 문구가 이유다.
   *
   * 서버도 거부하지만(마지막 방어) 눌러서 오류를 보는 것보다 낫다.
   */
  NOTIFICATION_REQUEST_PENDING: 'notification.request.pending',

  // ── 공지 팝업 (screen/_standalone/README.md · screen/admin/system/notice/requirements.md C안) ─────────────────────────
  //
  // 도달을 보장하는 장치다. 목록은 알림 모달의 공지 탭이며 **두 곳에 만들지 않는다**.
  /** "오늘 하루 보지 않기" — `localStorage` 에 **다음 자정까지** 담는다(screen/admin/system/notice/requirements.md). */
  NOTICE_POPUP_DISMISS: 'noticePopup.dismiss',
  /** 나머지는 공지 탭에 있다 — "공지 {count}건 더 있음". */
  NOTICE_POPUP_MORE: 'noticePopup.more',

  // ── 시스템 정보 (frontend/tabs.md) ───────────────────────────────────
  //
  // 답하는 물음이 하나다 — "지금 도는 서버가 어느 빌드인가". 운영 문의는 언제나 거기서
  // 시작하고, 화면에서 읽어 줄 수 없으면 확인 경로가 서버 접속뿐이다.
  /** 알림 모달 우측 하단의 버튼과 팝오버의 제목. */
  SYSTEM_INFO: 'system.info',
  /** 활성 Spring 프로필. */
  SYSTEM_INFO_PROFILE: 'system.info.profile',
  /** 프로필을 하나도 지정하지 않고 도는 중 — 빈 칸으로 두면 조회 실패로 보인다. */
  SYSTEM_INFO_PROFILE_DEFAULT: 'system.info.profile.default',
  /** 빌드 버전. 릴리스 태그 전까지는 `0.0.1-SNAPSHOT` 이라 이것만으로는 배포를 특정하지 못한다. */
  SYSTEM_INFO_VERSION: 'system.info.version',
  /** git 리비전. `-dirty` 가 붙었으면 고친 것이 있는 채로 만든 빌드다. */
  SYSTEM_INFO_COMMIT: 'system.info.commit',
  /** 빌드 시각 = 배포된 것이 만들어진 때. */
  SYSTEM_INFO_BUILD_TIME: 'system.info.buildTime',
  /** 기동 시각 = 마지막으로 올라온 때. 빌드 시각과 갈리면 배포 없이 재기동만 한 것이다. */
  SYSTEM_INFO_STARTED_AT: 'system.info.startedAt',
  /** 캐시 버전 — 프론트가 들고 있는 값과 대조할 근거다(sys/code.md). */
  SYSTEM_INFO_CACHE: 'system.info.cacheVersion',
  /** 빌드 정보가 없다 — IDE 가 클래스만 컴파일해 띄운 서버다. 없는 것을 채우지 않는다. */
  SYSTEM_INFO_UNKNOWN: 'system.info.unknown',

  // ── 세션 만료 예고 ──────────────────────────────────
  /** 만료 5분 전에 뜨는 모달의 제목. */
  SESSION_EXPIRY_TITLE: 'session.expiry.title',
  /** 그 본문 — `{minutes}`. */
  SESSION_EXPIRY_BODY: 'session.expiry.body',
  /** 세션을 연장한다 — 사람이 누르는 것이며 자동 연장은 두지 않는다. */
  SESSION_EXPIRY_EXTEND: 'session.expiry.extend',
  /** 연장하지 않고 지금 끝낸다. */
  SESSION_EXPIRY_LOGOUT: 'session.expiry.logout',
  /** 연장하지 않아 자동으로 끝났을 때. */
  SESSION_EXPIRY_TIMEOUT: 'session.expiry.timeout',

  // ── 브레드크럼 오른쪽 슬롯 (frontend/tabs.md) ────────────────────────
  /** 검색 폼을 접는다. */
  SEARCH_FORM_HIDE: 'searchForm.hide',
  /** 접힌 폼을 펼친다. */
  SEARCH_FORM_SHOW: 'searchForm.show',
  /** 접힌 채로 조건이 걸려 있을 때 — `{count}`. */
  SEARCH_FORM_ACTIVE: 'searchForm.active',


  // ── 탭 (frontend/packages.md · frontend/tabs.md) ───────────────────────────────────
  /** 탭 우클릭 메뉴의 "탭 닫기". */
  TAB_CLOSE: 'tab.close',
  /** 탭 우클릭 메뉴의 "다른 탭 닫기" — 현재 탭과 대시보드를 남긴다. */
  TAB_CLOSE_OTHERS: 'tab.closeOthers',
  /** 탭 우클릭 메뉴의 "모든 탭 닫기". */
  TAB_CLOSE_ALL: 'tab.closeAll',
  /** 트리 우클릭 메뉴의 "새 탭으로 열기" — 같은 화면 둘을 나란히 본다. */
  TAB_OPEN_NEW_INSTANCE: 'tab.openNewInstance',
  /** 브라우저 창을 하나 더 열어 그 메뉴를 띄운다 — 앱 안의 탭이 아니다. */
  TAB_OPEN_NEW_WINDOW: 'tab.openNewWindow',
  /** 이 메뉴를 즐겨찾기에 넣는다. */
  FAVORITE_ADD: 'favorite.add',
  /** 즐겨찾기에서 뺀다. */
  FAVORITE_REMOVE: 'favorite.remove',
  /** 탭 상한에 닿았을 때 확인 모달의 제목 — `{limit}`. */
  TAB_LIMIT_TITLE: 'tab.limit.title',
  /** 그 확인 모달의 본문 — `{name}` 은 닫힐 탭의 제목. */
  TAB_LIMIT_CONTENT: 'tab.limit.content',
  /** 닫힐 탭에 저장하지 않은 변경이 있을 때의 본문 — `{name}`. */
  TAB_LIMIT_CONTENT_DIRTY: 'tab.limit.contentDirty',
  /** 그 확인 모달의 확인 버튼. */
  TAB_LIMIT_OK: 'tab.limit.ok',
  /** 저장하지 않은 변경이 있는 탭을 닫으려 할 때의 제목. */
  TAB_DIRTY_TITLE: 'tab.dirty.title',
  /** 그 확인의 본문 — 닫으면 잃는다는 것을 알린다. */
  TAB_DIRTY_CLOSE_CONTENT: 'tab.dirty.close.content',
  /** 미저장 상태로 로그아웃하려 할 때의 본문. */
  TAB_DIRTY_LOGOUT_CONTENT: 'tab.dirty.logout.content',

  // ── 화면 안 최상단 탭 (frontend/tabs.md · playbook/INDEX.md) ───────────────
  /**
   * 미저장인 채로 <b>다른 모드로 옮기려</b> 할 때의 본문.
   *
   * <p><b>탭 닫기 문구를 쓰지 않는다.</b> 저기는 *닫으면 잃는다* 인데 여기서는
   * <b>화면이 남아 있다</b> — 같은 말을 쓰면 사용자는 탭이 닫히는 줄 알고 멈춘다.
   * 잃는 것은 같으므로 제목은 함께 쓴다.
   */
  SCREEN_TABS_DIRTY_CONTENT: 'screenTabs.dirty.content',

  // ── 엑셀 업로드 탭 (playbook/excel-upload.md · playbook/INDEX.md) ────────────────
  //
  // **올라가지 않는다** — 브라우저가 읽어 그리드에 붓는다. 그래서 여기 없는 것이
  // 진행률·업로드 실패·파일 목록이고, 있는 것은 **읽기의 거절 사유**들이다.
  /** 파일을 고르는 버튼 — ①의 `beforeRead` 자리다. */
  EXCEL_UPLOAD_PICK: 'excel.upload.pick',
  /**
   * 양식이 다르다 — `{expected}` · `{actual}`. <b>읽지 않는다</b>(playbook/INDEX.md).
   *
   * <p>어긋난 채로 읽으면 열이 하나 밀려 <b>수량 자리에 단가가</b> 들어가고, 그 파일은
   * 저장까지 통과한다.
   */
  EXCEL_UPLOAD_HEADER_MISMATCH: 'excel.upload.headerMismatch',
  /** 행이 상한을 넘는다 — `{max}` · `{count}`. <b>조용히 자르지 않는다</b>(playbook/INDEX.md). */
  EXCEL_UPLOAD_TOO_MANY: 'excel.upload.tooMany',
  /** 읽었다 — `{count}`. 몇 건이 들어왔는지 말하지 않으면 사용자가 스크롤로 센다. */
  EXCEL_UPLOAD_LOADED: 'excel.upload.loaded',
  /** 숫자가 아니다 — `{value}` 가 파일에 있던 글자다. */
  EXCEL_UPLOAD_NOT_NUMBER: 'excel.upload.notNumber',
  /** 정수가 아니다 — `{value}`. `bigint` 컬럼은 자릿수만 본다(playbook/INDEX.md). */
  EXCEL_UPLOAD_NOT_INTEGER: 'excel.upload.notInteger',
  /** `Y`/`N` 이 아니다 — `{value}`. */
  EXCEL_UPLOAD_NOT_YN: 'excel.upload.notYn',
  /** 목록에 없는 코드다 — `{value}`. 코드값과 코드명 <b>둘 다</b> 찾아본 결과다(playbook/INDEX.md). */
  EXCEL_UPLOAD_UNKNOWN_CODE: 'excel.upload.unknownCode',
  /**
   * 코드명이 <b>두 코드에 걸린다</b> — `{value}`(playbook/INDEX.md).
   *
   * <p>둘 중 하나를 조용히 고르지 않는다 — 그렇게 저장된 행은 <b>화면에서 정상으로
   * 보인다</b>.
   */
  EXCEL_UPLOAD_AMBIGUOUS_CODE: 'excel.upload.ambiguousCode',
  /** 파일 안에서 키가 겹쳤다 — 어느 칸이라 말할 수 없다(키 <b>조합</b>이다). */
  EXCEL_UPLOAD_DUPLICATED: 'excel.upload.duplicated',

  // ── 화면 자리 (frontend/tabs.md) ──────────────────────────────────────
  /** `screenRegistry` 에 컴포넌트가 없는 메뉴를 열었을 때 — `{menu}`. */
  SCREEN_MISSING_TITLE: 'screen.missing.title',
  /** 그 안내의 본문 — `{menuCd}` · `{menuUrl}`. */
  SCREEN_MISSING_DESC: 'screen.missing.desc',
  /** 탭 화면이 예외를 던졌을 때(에러 바운더리는 탭 단위다 — frontend/tabs.md). */
  SCREEN_ERROR_TITLE: 'screen.error.title',
  /** 권한 없는 경로로 직접 들어왔을 때의 토스트(frontend/packages.md). */
  SCREEN_DENIED: 'screen.denied',

  // ── 대시보드 ────────────────────────────────────────────────
  /** 대시보드 인사 — `{name}`. */
  DASHBOARD_GREETING: 'dashboard.greeting',
  /** 접근 가능한 메뉴 수 카드의 제목. */
  DASHBOARD_MENU_COUNT: 'dashboard.menuCount',
  /** 그 카드의 값 — `{count}`. */
  DASHBOARD_MENU_COUNT_VALUE: 'dashboard.menuCount.value',
  /** 보유 권한 수 카드의 제목 — 원소는 (메뉴, 액션) 쌍이다(backend/security.md). */
  DASHBOARD_PERMISSION_COUNT: 'dashboard.permissionCount',
  /** 그 카드의 값 — `{count}`. */
  DASHBOARD_PERMISSION_COUNT_VALUE: 'dashboard.permissionCount.value',

  // ── 그리드 크롬 (frontend/grid.md) ────────────────────────────────────
  /** 그리드 헤더 ①의 조회 버튼. */
  GRID_READ: 'grid.read',
  /** 그리드 헤더 ①의 저장 버튼. */
  GRID_SAVE: 'grid.save',
  /** 그리드 헤더 ①의 행 추가 버튼 — `addRow` 를 선언한 그리드에만 나온다(frontend/grid.md). */
  GRID_ADD_ROW: 'grid.addRow',
  /** 맨 왼쪽 행 번호 컬럼의 헤더 — 페이지를 넘어도 번호가 이어진다(frontend/grid.md). */
  GRID_COL_ROW_NO: 'grid.col.rowNo',
  /** 다중 선택 그리드의 체크박스 컬럼 헤더 — 글자는 안 보이고 전체 선택 체크박스가 그 자리다. */
  GRID_COL_SELECT: 'grid.col.select',
  /** 다중 선택 컬럼 헤더의 전체 선택 체크박스 이름. */
  GRID_SELECT_ALL: 'grid.selectAll',
  /** 다중 선택 컬럼에서 행 하나를 고르는 체크박스 이름. */
  GRID_SELECT_ROW: 'grid.selectRow',
  /** 상태 컬럼의 헤더 — 사용자가 끄거나 옮길 수 없다(frontend/grid.md). */
  GRID_COL_STATE: 'grid.col.state',
  /** 상태 컬럼의 되돌리기 아이콘 — 수정한 행을 원본으로 돌린다(frontend/grid.md). */
  GRID_REVERT: 'grid.revert',
  /** 맨 오른쪽 삭제 컬럼의 헤더 — 물리 삭제하는 그리드에만 있다(frontend/grid.md). */
  GRID_COL_DELETE: 'grid.col.delete',
  /** 등록자 컬럼 — 감사 4컬럼은 기본이 숨김이다(frontend/grid.md). */
  GRID_COL_REG_ID: 'grid.col.regId',
  /** 등록일시 컬럼. */
  GRID_COL_REG_DTM: 'grid.col.regDtm',
  /** 수정자 컬럼. */
  GRID_COL_MOD_ID: 'grid.col.modId',
  /** 수정일시 컬럼. */
  GRID_COL_MOD_DTM: 'grid.col.modDtm',
  /** 푸터 왼쪽 — `{total}` · `{pages}`. */
  GRID_FOOTER_TOTAL: 'grid.footer.total',
  /** 컬럼 필터가 걸렸을 때 덧붙는 문구 — `{loaded}` · `{shown}`(frontend/grid.md). */
  GRID_FOOTER_FILTERED: 'grid.footer.filtered',
  /** 페이징이 없는 그리드(`mode: 'all'`)의 푸터 — `{count}`. */
  GRID_FOOTER_COUNT: 'grid.footer.count',
  /**
   * 커서형 푸터의 왼쪽 — `{count}`(screen/admin/system/log/access/requirements.md).
   *
   * <p>`GRID_FOOTER_TOTAL` 과 다른 말이다. 저것은 <b>전부가 몇 건</b>이고 이것은
   * <b>이 장이 몇 건</b>이다 — 총 건수를 세지 않기로 한 화면에서 <i>총</i> 이라고 쓰면
   * 거짓말이 된다.
   */
  GRID_FOOTER_LOADED: 'grid.footer.loaded',
  /** 페이지 사이즈 콤보의 항목 — `{size}`. */
  /** 페이지 이동 — 한 쪽 앞으로. */
  GRID_PAGE_PREV: 'grid.page.prev',
  /** 한 쪽 뒤로. */
  GRID_PAGE_NEXT: 'grid.page.next',
  /** 다섯 쪽 앞으로 — 판의 점 셋이 그 뜻이다. */
  GRID_PAGE_JUMP_PREV: 'grid.page.jumpPrev',
  /** 다섯 쪽 뒤로 — 판의 점 셋이 그 뜻이다. */
  GRID_PAGE_JUMP_NEXT: 'grid.page.jumpNext',
  GRID_FOOTER_PAGE_SIZE: 'grid.footer.pageSize',
  /** 행이 없을 때 그리드 본문에 나오는 표시. */
  GRID_EMPTY: 'grid.empty',
  /** 조회 결과가 0건일 때의 알림 — 마스터만 기본으로 켠다(frontend/grid.md). */
  GRID_READ_EMPTY: 'grid.read.empty',

  // ── 엑셀 다운로드 (frontend/grid.md · playbook/excel-download.md) ─────────────────────
  /** ②의 아이콘 버튼 이름 — 항목이 둘이라 어느 라벨도 대표하지 못해 글자가 없다. */
  GRID_EXCEL: 'grid.excel',
  /** 항목 ① — 지금 화면에 보이는 행. 컬럼 필터가 걸려 있으면 걸러진 결과다(frontend/grid.md). */
  GRID_EXCEL_PAGE: 'grid.excel.page',
  /** 항목 ② — 조회조건으로 전량 재조회. 상한이 있다(playbook/excel-download.md). */
  GRID_EXCEL_ALL: 'grid.excel.all',
  /** 전량이 상한을 넘었을 때 — 조용히 자르지 않고 거절한다. */
  GRID_EXCEL_TOO_MANY: 'grid.excel.tooMany',
  /** 내려받을 행이 없을 때 — 빈 파일을 만들지 않는다. */
  GRID_EXCEL_EMPTY: 'grid.excel.empty',

  // ── 컬럼 필터 (frontend/grid.md) ────────────────────────────────────
  /** `set` 필터의 전체 선택 — 초기 상태이자 *필터 없음* 이다. */
  GRID_FILTER_ALL: 'grid.filter.all',
  /** 값이 비어 있는 행을 고르는 항목 — 빈 칸도 골라야 거를 수 있다. */
  GRID_FILTER_BLANK: 'grid.filter.blank',
  /** 로드된 행에 값이 하나도 없을 때 — 목록이 비면 고장으로 보인다. */
  GRID_FILTER_EMPTY: 'grid.filter.empty',
  /**
   * 날짜 필터의 <b>시작 칸</b> — frontend/grid.md · playbook/INDEX.md.
   *
   * <p>플레이스홀더이지 라벨이 아니다. 칸이 둘뿐이라 <i>시작</i>·<i>끝</i> 이라는 글자가
   * 밖에 또 서면 폭의 절반을 먹는데, 헤더 팝업은 컬럼 폭에 매여 있어 그럴 자리가 없다.
   */
  GRID_FILTER_FROM: 'grid.filter.from',
  /** 〃 <b>끝 칸</b>. 비워 두면 위쪽이 열린다 — 단측이다. */
  GRID_FILTER_TO: 'grid.filter.to',
  /**
   * 날짜 필터를 <b>푸는</b> 버튼.
   *
   * <p>칸마다 있는 × 로도 지워지지만 두 번 눌러야 한다. 필터를 푸는 것은 거는 것보다
   * 급한 동작이다 — 걸어 둔 것을 잊고 조회하면 <b>0건이 데이터가 없는 것으로</b> 읽힌다.
   */
  GRID_FILTER_CLEAR: 'grid.filter.clear',

  // ── 참조 팝업 (frontend/grid.md · playbook/INDEX.md) ────────────────────────
  //
  // 한 번 골라 여러 칸을 채우는 창이다. 셋뿐인 것이 요점 — 창의 제목과 컬럼은
  // **팝업 선언**이 갖고(레지스트리), 여기 있는 것은 어느 팝업에서나 같은 글자다.
  /** 고른 행으로 확정한다 — 더블클릭이 같은 일을 한다. */
  POPUP_PICK: 'popup.pick',
  /**
   * 값을 <b>비운다</b> — 딸려 온 칸까지 함께 지운다(frontend/grid.md).
   *
   * <p><b>취소와 갈라야 하는 버튼이다.</b> 창을 그냥 닫는 것과 한 뜻으로 두면 마음을
   * 바꾼 사람이 원래 값을 잃고, 그 사고는 저장하기 전까지 화면에 아무 경고도 남기지
   * 않는다. 지금 값이 있는 칸에서만 나온다.
   */
  POPUP_CLEAR: 'popup.clear',

  // ── 배열 편집 모달 (frontend/grid.md · playbook/INDEX.md) ─────────────────
  //
  // 창의 제목은 **컬럼 헤더**이고 확인·취소는 공통이라, 여기 있는 것은 이 창에만
  // 있는 동작 넷이다.
  /** 항목을 고르는 팝업을 연다 — 여기서 타이핑을 받지 않는다(목록에 없는 값이 생긴다). */
  GRID_LIST_ADD: 'grid.list.add',
  /** 항목 하나를 뺀다. 배열 전체를 비우는 버튼이 아니다. */
  GRID_LIST_REMOVE: 'grid.list.remove',
  /**
   * 항목을 <b>한 칸 위로</b> — 순서가 값의 일부다(playbook/INDEX.md 의 `SORT_NO`).
   *
   * <p>아이콘만 있는 버튼이라 툴팁이 유일한 설명이다.
   */
  GRID_LIST_UP: 'grid.list.up',
  /** 〃 아래로. */
  GRID_LIST_DOWN: 'grid.list.down',
  /**
   * 항목이 하나도 없을 때 — <b>비었다고 말한다</b>.
   *
   * <p>빈 창을 그냥 두면 <i>아직 안 불러온 것</i>과 구별되지 않는다.
   */
  GRID_LIST_EMPTY: 'grid.list.empty',

  // ── 실행 파라미터 모달 (frontend/grid.md · playbook/INDEX.md) ──────────────
  //
  // 제목은 **그 실행의 이름**이라 화면이 주고, 칸 이름도 선언에서 온다. 여기 있는 것은
  // 어느 실행에서나 같은 둘이다.
  /**
   * 대상 건수 — `{count}`. <b>이 모달이 확인을 겸하므로</b> 있다(frontend/grid.md).
   *
   * <p>없으면 사용자는 <i>무엇에 대해</i> 값을 넣는지 모른 채 실행한다.
   */
  PARAMS_COUNT: 'params.count',
  /**
   * 실행 버튼 — <b>`확인` 이 아니다</b>.
   *
   * <p>이 창은 확인 다이얼로그를 대신하므로(frontend/grid.md) 버튼이 곧 실행이며, 그 사실을
   * 글자가 말해야 한다 — `확인` 은 <i>내가 적은 값이 맞다</i> 로도 읽힌다.
   */
  PARAMS_RUN: 'params.run',

  // ── 진행률 (backend/crosscutting.md · sys/batch.md · playbook/INDEX.md) ───────────────────
  //
  // 창의 제목은 그 실행의 이름이라 화면이 주고, 단계 문구와 실패 사유는 **서버가**
  // 준다. 여기 있는 것은 창의 틀이다.
  /**
   * 처리한 건수와 완료율 — `{cur}` / `{tot}` / `{percent}`.
   *
   * <p>**전체를 모르면 아래 {@link PROGRESS_COUNT_UNKNOWN} 이 대신 온다**(sys/batch.md) — 그때는
   * 나눌 수가 없어 퍼센트가 아예 없다.
   */
  PROGRESS_COUNT: 'progress.count',
  /**
   * **전체를 모르는 작업의 건수** — `{cur}` 뿐이다(sys/batch.md).
   *
   * <p>이 줄이 없으면 그 작업의 창에는 도는 막대만 남고, 그것은 <i>멈춘 것</i>과 구별되지
   * 않는다 — 건수라도 올라가는 것이 <b>돌고 있다</b>는 유일한 증거다.
   */
  PROGRESS_COUNT_UNKNOWN: 'progress.count.unknown',
  /**
   * **창이 뜬 지 얼마나 지났는가** — `{min}` 분 `{sec}` 초.
   *
   * <p>퍼센트만으로는 기다릴 수 없다. 사용자가 이 창 앞에서 하는 결정은 <b>기다릴까 접을까</b>
   * 하나이며, 3초 지난 24% 와 5분 지난 24% 는 그 결정이 정반대다.
   */
  PROGRESS_ELAPSED: 'progress.elapsed',
  /**
   * 작업이 <b>실패했다</b>. 사유는 서버가 준 `ERR_MSG` 가 아래 붙는다.
   *
   * <p>아래 {@link PROGRESS_UNREACHABLE} 과 <b>갈라야 하는 문구다</b>.
   */
  PROGRESS_FAILED: 'progress.failed',
  /**
   * <b>진행률을 볼 수 없다</b> — 작업이 실패한 것이 아니다.
   *
   * <p>폴링이 연속으로 실패한 경우이며 <b>작업은 여전히 돌고 있을 수 있다</b>. 이것을
   * 실패로 말하면 사용자는 다시 실행하고, 그때 같은 작업이 <b>두 번</b> 돈다.
   */
  PROGRESS_UNREACHABLE: 'progress.unreachable',
  /**
   * <b>창을 닫아도 계속된다</b> — playbook/INDEX.md 의 <i>모달은 닫을 수 있고 작업은 계속 돈다</i>.
   *
   * <p>말하지 않으면 사용자는 <i>닫으면 취소되는 것</i>으로 읽고 끝날 때까지 앉아 있는다.
   */
  PROGRESS_BACKGROUND: 'progress.background',
  /**
   * <b>접힌 표시를 누르면 창이 돌아온다</b> — playbook/INDEX.md.
   *
   * <p>표시에는 그 실행의 이름이 이미 적혀 있다. 이 문구는 <b>그것이 눌린다</b>는 사실을
   * 말하며 — 진행 중 표시는 흔히 <i>보여 주기만 하는 것</i>이라, 누를 수 있다는 것을
   * 말하지 않으면 사용자는 <b>다시 실행</b>으로 창을 되찾으려 한다.
   */
  PROGRESS_REOPEN: 'progress.reopen',
  /**
   * <b>접힌 채 끝났다</b> — `{title}` 은 그 실행의 이름이다.
   *
   * <p>창이 열려 있었으면 100% 막대가 그 말을 한다. 접혀 있으면 <b>표시가 사라지는 것</b>
   * 말고는 끝났다는 신호가 없어, 사용자는 <i>내가 놓쳤나</i> 를 묻는다.
   */
  PROGRESS_DONE: 'progress.done',

  // ── 그리드 설정 창 (UX-06 · frontend/grid.md) ─────────────────────────
  /** ③의 아이콘 버튼 이름 — 글자가 없어 툴팁이 유일한 설명이다. */
  GRID_SETTINGS: 'grid.settings',
  /** 창의 제목 — `{grid}` 는 그 그리드의 제목이다. 화면에 창이 둘일 수 있다(frontend/grid.md). */
  GRID_SETTINGS_TITLE: 'grid.settings.title',
  // 선 둘의 생김새가 같아 툴팁이 유일한 설명이다.
  /** 위 경계선의 이름 — 이 선보다 위가 왼쪽 고정이다(frontend/grid.md). */
  GRID_SETTINGS_PIN_LEFT: 'grid.settings.pin.left',
  /** 아래 경계선의 이름 — 이 선보다 <b>아래</b>가 오른쪽 고정이다. */
  GRID_SETTINGS_PIN_RIGHT: 'grid.settings.pin.right',
  /** 순서를 바꾸는 손잡이 — 아이콘만 보이며 읽는 것은 스크린리더다. */
  GRID_SETTINGS_DRAG: 'grid.settings.drag',
  /** 적용 버튼 — 고친 것을 화면에만 두고 닫는다. DB 에는 남기지 않는다(frontend/grid.md). */
  GRID_SETTINGS_APPLY: 'grid.settings.apply',
  /** 저장 성공 토스트. */
  GRID_SETTINGS_SAVED: 'grid.settings.saved',
  /** 초기화 버튼 — 저장된 설정을 지우고 화면 선언으로 돌아간다. */
  GRID_SETTINGS_RESET: 'grid.settings.reset',
  /** 그 확인 문구 — 되돌릴 수 없다. */
  GRID_SETTINGS_RESET_CONFIRM: 'grid.settings.reset.confirm',
  /** 초기화가 꺼진 이유 — 저장된 설정이 없다. 꺼진 버튼은 스스로 이유를 말하지 못한다(frontend/grid.md). */
  GRID_SETTINGS_RESET_NONE: 'grid.settings.reset.none',
  /** 컬럼을 전부 끄고 저장하려 했을 때 — 빈 그리드가 되면 되돌릴 컨트롤이 그 안에 있다. */
  GRID_SETTINGS_NONE_VISIBLE: 'grid.settings.noneVisible',
  /** 스플리터의 위 화살표 — 막대를 맨 위로 보내 <b>위 그리드를 접는다</b>(frontend/grid.md). */
  GRID_SPLIT_COLLAPSE_FIRST: 'grid.split.collapseFirst',
  /** 스플리터의 아래 화살표 — 막대를 맨 아래로 보내 <b>아래 그리드를 접는다</b>(frontend/grid.md). */
  GRID_SPLIT_COLLAPSE_SECOND: 'grid.split.collapseSecond',
  /** 접힌 상태에 남는 화살표 하나 — 누르면 <b>되돌린다</b>(frontend/grid.md). */
  GRID_SPLIT_EXPAND: 'grid.split.expand',

  // ── 그리드 편집·저장 (frontend/grid.md) ───────────────────────
  /** 미저장 변경이 있는데 재조회를 유발했을 때의 제목(조회·페이지·사이즈·정렬). */
  GRID_DISCARD_TITLE: 'grid.discard.title',
  /** 그 본문 — 되돌릴 수 없다는 것을 말한다. */
  GRID_DISCARD_BODY: 'grid.discard.body',
  /**
   * 건수 확인 <b>앞</b>에 서는 경고의 제목 — screen/admin/system/code/requirements.md.
   *
   * <p>본문은 화면이 만든다. 건수는 얼마나를 말할 뿐이라 <b>무엇이 일어나는지</b> 를
   * 말하지 못하며, 그것을 아는 것은 화면이다.
   */
  GRID_SAVE_WARN_TITLE: 'grid.save.warn.title',
  /** 저장 전 확인의 제목. */
  GRID_SAVE_CONFIRM_TITLE: 'grid.save.confirm.title',
  /** 그 본문 — `{added}` · `{updated}` · `{removed}`. 건수가 유일한 요약이다. */
  GRID_SAVE_CONFIRM_BODY: 'grid.save.confirm.body',
  /** 컬럼 필터로 보이지 않는 변경 행이 있을 때 덧붙는다 — `{hidden}`(frontend/grid.md). */
  GRID_SAVE_CONFIRM_HIDDEN: 'grid.save.confirm.hidden',
  /** 저장 성공 — 확인을 누르면 재조회가 돈다(frontend/grid.md). */
  GRID_SAVE_DONE: 'grid.save.done',
  /** 저장할 변경이 없는데 저장을 눌렀을 때. */
  GRID_SAVE_NOTHING: 'grid.save.nothing',
  /** 검증 실패 건수 — `{count}`(frontend/grid.md). */
  GRID_VALIDATION_COUNT: 'grid.validation.count',
  /**
   * 검증 실패 한 줄 — `{column}` · `{message}`(frontend/grid.md ④의 *목록*).
   *
   * <p>건수만으로는 **무엇이 틀렸는지** 알 수 없다. 붉은 셀이 *어디* 를 말하고 이 줄이
   * *무엇* 을 말한다 — 컬럼 이름을 앞에 붙이는 것은 같은 문구가 여러 컬럼에서 날 수 있기
   * 때문이다(`필수 항목입니다`).
   */
  GRID_VALIDATION_LINE: 'grid.validation.line',
  /** 표의 상한을 넘었다 — `{count}`. 남은 수만 말한다(frontend/save-errors.md). */
  GRID_VALIDATION_MORE: 'grid.validation.more',
  /**
   * <b>건별 처리의 결과</b> 제목 — backend/row-transaction.md.
   *
   * <p><b>성공도 실패도 아닌 이름이어야 한다.</b> 488건이 저장되고 12건이 안 된 일은
   * 둘 중 어느 쪽도 아니며, 어느 한쪽으로 부르면 <b>화면이 거짓말을 한다</b>.
   */
  GRID_PARTIAL_TITLE: 'grid.partial.title',
  /** 그 요약 — `{total}` · `{done}` · `{failed}`. <b>건수가 먼저다</b>: 표는 그 뒤의 상세다. */
  GRID_PARTIAL_SUMMARY: 'grid.partial.summary',
  /**
   * <b>목록을 비웠다</b>고 알린다 — `{count}` · `{failed}`. {@code after: 'keepFailed'} 전용.
   *
   * <p><b>말하지 않으면 사라진 것으로 보인다.</b> 500건을 저장했는데 화면에 12줄만 남으면
   * 사용자는 <i>488건도 실패했나</i> 를 먼저 의심한다 — 남은 것이 <b>고칠 것</b>이라는 사실은
   * 배관만 알고 있으므로 배관이 말해야 한다.
   */
  GRID_PARTIAL_KEPT: 'grid.partial.kept',
  /**
   * <b>곧 다시 읽는다</b>고 미리 알린다 — {@code after: 'reload'} 전용.
   *
   * <p><b>닫는 순간 이 표가 사라진다.</b> 재조회가 목록을 갈아 끼우므로 <i>어느 넷이 왜
   * 안 됐나</i> 를 다시 볼 방법이 없고, 남는 것은 <b>내려받아 둔 파일</b>뿐이다.
   *
   * <p>그래서 <b>닫기 전에</b> 말한다. 사후 통보이면 사용자가 <b>아무 일도 하지 못한다</b> —
   * 이미 사라진 뒤이며, 그 안내는 알림이 아니라 <i>부고</i> 다.
   *
   * <p>{@link #GRID_PARTIAL_KEPT} 와 <b>함께 뜨지 않는다.</b> 앞은 *목록을 비웠다* 이고
   * 여기는 *목록을 새로 읽는다* 라 <b>서로를 부정한다</b> — 뒤처리가 둘 중 하나뿐이므로
   * 문구도 하나만 선다.
   */
  GRID_PARTIAL_GONE: 'grid.partial.gone',
  /**
   * 저장 실패 표의 머리 셋 — `컬럼` · `값` · `사유`(frontend/grid.md ④).
   *
   * <p><b>행 번호 칸이 없다.</b> 판에 클라이언트 정렬이 걸리면 우리가 센 배열 순서와 화면의
   * 번호가 어긋나고, *틀린 번호는 없는 번호보다 나쁘다*. 어느 줄인지는 <b>붉은 셀</b>이
   * 말하고 판이 첫 실패로 데려간다.
   */
  GRID_ISSUE_COLUMN: 'grid.issue.column',
  /** 그 표의 <b>값</b> 칸 — *무엇이* 중복인가에 답하는 자리다(frontend/grid.md ④). */
  GRID_ISSUE_VALUE: 'grid.issue.value',
  /** 그 표의 <b>사유</b> 칸. 서버가 완성한 문구를 그대로 싣는다(backend/packages.md). */
  GRID_ISSUE_REASON: 'grid.issue.reason',
  /**
   * 그 표 아래의 <b>실패분 내려받기</b> — backend/row-transaction.md §7.
   *
   * <p>표는 <i>읽을</i> 수는 있어도 <b>고쳐서 다시 낼</b> 수는 없다. 오백 행을 올려 열둘이
   * 걸린 자리에서 남는 일은 <b>화면에서 원본 파일로 옮겨 적는 것</b>이며, 그것이 이 버튼이
   * 없앤다.
   */
  GRID_ISSUE_EXCEL: 'grid.issue.excel',
  /** 그 파일 이름의 가운데 토막 — 메뉴 코드와 시각 사이에 들어간다. */
  GRID_ISSUE_EXCEL_FILE: 'grid.issue.excel.file',
  /**
   * 그 파일의 <b>둘째 시트</b> 이름.
   *
   * <p>사유가 둘째 시트인 것이 결정이다 — 첫 시트에 열로 붙이면 양식이 달라져 <b>다시
   * 올릴 수 없다</b>({@code readWorkbook} 이 뒤에 붙은 열까지 세어 막는다).
   */
  GRID_ISSUE_EXCEL_SHEET: 'grid.issue.excel.sheet',
  /** 그 시트의 첫 머리 — 첫 시트의 <b>몇 번째 데이터 줄</b>인가. */
  GRID_ISSUE_ROW: 'grid.issue.row',
  /** 필수 컬럼이 비었을 때의 기본 문구(frontend/grid.md). */
  GRID_VALIDATION_REQUIRED: 'grid.validation.required',
  /**
   * 저장된 행의 <b>PK 칸</b>을 고치려 했을 때 — frontend/grid.md 의 `pk`.
   *
   * <p>*"고치려면 지우고 다시 만든다"* 가 답인데 그것은 화면 어디에도 적혀 있지 않다 —
   * 잠긴 칸은 <b>왜 잠겼는지</b>를 말하지 않으면 버그로 읽힌다.
   */
  GRID_PK_LOCKED: 'grid.pk.locked',
  /** 저장 확인 대화의 확인 버튼. */
  GRID_CONFIRM_OK: 'grid.confirm.ok',

  // ── 그리드 실행 (frontend/grid.md) ────────────────────────────────────
  //
  // `read` · `save` 가 아닌 버튼이 부르는 자리다. screen/admin/system/user/requirements.md 의 *잠금 해제* · *휴면 해제* ·
  // *비밀번호 초기화* 가 첫 실물이다.
  /** 실행 전 확인의 제목. */
  GRID_ACTION_CONFIRM_TITLE: 'grid.action.confirm.title',
  /** 그 본문 — `{name}`(버튼 이름) · `{count}`(선택 건수). */
  GRID_ACTION_CONFIRM_BODY: 'grid.action.confirm.body',
  /** 행과 무관한 실행의 본문 — `{name}`. 건수를 넣지 않는다(frontend/grid.md). */
  GRID_ACTION_CONFIRM_NONE: 'grid.action.confirm.none',
  /** 실행 성공 — 확인을 누르면 재조회가 돈다(frontend/grid.md 의 꼬리). */
  GRID_ACTION_DONE: 'grid.action.done',

  // ── 그리드 판(AG Grid)이 내는 문구 ─────────────────────────
  //
  // 판이 자기 언어로 그리는 자리다. 두지 않으면 한국어 화면에 "Loading…" 이 섞인다.
  /** 조회 중 오버레이. */
  GRID_AG_LOADING: 'grid.ag.loading',
  /**
   * <b>컬럼 필터가 전부 걸러냈을 때</b> — 판이 별도 오버레이로 그린다.
   *
   * <p>*"데이터가 없습니다"* 와 다른 문구여야 한다. 조회 결과는 있는데 필터가 가린 것이며,
   * 그 사실을 말하지 않으면 사용자가 <b>접힌 헤더 메뉴 안의 잊힌 필터</b>를 찾지 못한다
   * (frontend/grid.md).
   */
  GRID_AG_NO_MATCHING_ROWS: 'grid.ag.noMatchingRows',
  /** 컬럼 필터 입력 칸의 placeholder. */
  GRID_AG_FILTER_OOO: 'grid.ag.filterOoo',
  /** 필터 패널의 적용 버튼. */
  GRID_AG_APPLY_FILTER: 'grid.ag.applyFilter',
  /** 필터 패널의 되돌리기 버튼. */
  GRID_AG_RESET_FILTER: 'grid.ag.resetFilter',
  /** 필터 패널의 지우기 버튼. */
  GRID_AG_CLEAR_FILTER: 'grid.ag.clearFilter',
  /** 텍스트 필터 — 포함하지 않음. */
  GRID_AG_NOT_CONTAINS: 'grid.ag.notContains',
  /** 숫자 필터 — 초과. */
  GRID_AG_GREATER_THAN: 'grid.ag.greaterThan',
  /** 숫자 필터 — 이상. */
  GRID_AG_GREATER_OR_EQUAL: 'grid.ag.greaterOrEqual',
  /** 숫자 필터 — 미만. */
  GRID_AG_LESS_THAN: 'grid.ag.lessThan',
  /** 숫자 필터 — 이하. */
  GRID_AG_LESS_OR_EQUAL: 'grid.ag.lessOrEqual',
  /** 숫자 필터 — 범위. */
  GRID_AG_IN_RANGE: 'grid.ag.inRange',
  /** 범위 필터의 시작 칸. */
  GRID_AG_IN_RANGE_START: 'grid.ag.inRangeStart',
  /** 범위 필터의 끝 칸. */
  GRID_AG_IN_RANGE_END: 'grid.ag.inRangeEnd',
  /** 조건 둘을 묶는 접속사 — 그리고. */
  GRID_AG_AND: 'grid.ag.and',
  /** 조건 둘을 묶는 접속사 — 또는. */
  GRID_AG_OR: 'grid.ag.or',

  // ── 검색 폼 (57) ───────────────────────────────────────────
  /** 폼의 유일한 버튼 — 조건과 컬럼 필터를 함께 지운다(frontend/grid.md). */
  SEARCH_RESET: 'search.reset',
  /** 값을 고르지 않았을 때의 콤보 항목. */
  SEARCH_ALL: 'search.all',
  /** 연산자 셀렉터 — 같음. */
  SEARCH_OP_EQ: 'search.op.eq',
  /** 연산자 셀렉터 — 같지 않음. */
  SEARCH_OP_NE: 'search.op.ne',
  /** 연산자 셀렉터 — 여럿 중 하나. 고르면 콤보가 다중 선택이 된다(`부분 일치` 와 다르다). */
  SEARCH_OP_IN: 'search.op.in',
  /** 연산자 셀렉터 — 시작 문자 일치. */
  SEARCH_OP_STARTS_WITH: 'search.op.startsWith',
  /** 연산자 셀렉터 — 부분 일치. */
  SEARCH_OP_CONTAINS: 'search.op.contains',
  /** 연산자 셀렉터 — 끝 문자 일치. */
  SEARCH_OP_ENDS_WITH: 'search.op.endsWith',
  /** 연산자 셀렉터 — 값 없음. 고르면 값 칸이 사라진다(backend/api.md). */
  SEARCH_OP_IS_NULL: 'search.op.isNull',
  /** 연산자 셀렉터 — 값 있음. `in` 으로 표현할 방법이 아예 없는 조건이다. */
  SEARCH_OP_IS_NOT_NULL: 'search.op.isNotNull',
  /** 연산자 셀렉터 — 두 끝 사이. 크기 계열의 첫 실물이다(frontend/search-form.md). */
  SEARCH_OP_BETWEEN: 'search.op.between',
  /**
   * 연산자 셀렉터 — <b>이상</b>(`>=`). 크기 계열의 둘째 실물이다(frontend/search-form.md).
   *
   * <p>screen/admin/system/batch/schedule/requirements.md 의 *소요 시간 이상* 이 처음 요구했다.
   */
  SEARCH_OP_GTE: 'search.op.gte',
  /**
   * 연산자 셀렉터 — <b>이하</b>(`<=`). 크기 계열의 셋째 실물이다(frontend/search-form.md).
   *
   * <p>screen/admin/system/user/requirements.md 의 기간 칸 둘이 처음 요구했다(2026-08-31). *한쪽만 아는 조건* 이 실제로
   * 있다 — <i>이 날짜 이후로 한 번도 안 들어온 계정</i> 이 `lte` 이고 그 반대가 `gte` 다.
   */
  SEARCH_OP_LTE: 'search.op.lte',
  /**
   * 연산자 셀렉터 — <b>초과</b>(`>`). 크기 계열의 넷째 실물이다(playbook/INDEX.md).
   *
   * <p><b>이상과 한 글자 차이라 글자가 일해야 한다.</b> 기호(`>`)는 `≥` 와 밑줄 하나
   * 차이이고 좁은 칸에서는 그 차이가 안 보인다 — 이름이 <i>초과</i> 라고 말해야 사람이
   * 경계값이 들어가는지를 안다.
   */
  SEARCH_OP_GT: 'search.op.gt',
  /** 연산자 셀렉터 — <b>미만</b>(`<`). 크기 계열의 다섯째이자 마지막이다(playbook/INDEX.md). */
  SEARCH_OP_LT: 'search.op.lt',
  /** 필수 조건을 비우고 조회했다 — screen/admin/system/log/access/requirements.md. 조회 자체가 일어나지 않는다. */
  SEARCH_REQUIRED: 'search.required',
  /** 기간이 상한을 넘었다 — `{days}`. 조용히 자르지 않는다(screen/admin/system/log/access/requirements.md). */
  SEARCH_RANGE_TOO_WIDE: 'search.rangeTooWide',

  // ── 저장된 조회조건 (UX-07 · frontend/search-form.md) ────────────────────────
  //
  // 조회조건 영역 오른쪽 위, 초기화 옆의 설정 버튼이 여는 목록이다. 저장되는 것은
  // 와이어가 아니라 **폼 값**이며 기본값과 같은 칸은 담기지 않는다(frontend/search-form.md).
  /** 목록을 여는 버튼의 글자 — 이 버튼 하나가 저장과 불러오기 둘을 한다. */
  SEARCH_COND_TITLE: 'searchCond.title',
  /** 목록 아래의 항목 — 지금 폼에 채워진 값을 이름 붙여 담는다. */
  SEARCH_COND_SAVE: 'searchCond.save',
  /** 이름을 묻는 모달의 제목. */
  SEARCH_COND_SAVE_TITLE: 'searchCond.save.title',
  /** 그 모달의 이름 칸 라벨 — 화면 안에서 중복될 수 없다(frontend/search-form.md). */
  SEARCH_COND_NAME: 'searchCond.name',
  /** 이름을 비운 채 저장하려 했을 때. */
  SEARCH_COND_NAME_REQUIRED: 'searchCond.name.required',
  /** 같은 이름이 이미 있을 때의 안내 — 막지 않고 <b>덮어쓴다</b>는 것을 말한다. */
  SEARCH_COND_OVERWRITE: 'searchCond.overwrite',
  /** 기본 조건 체크박스 — 화면에 들어올 때 이 조건으로 폼이 채워진다. */
  SEARCH_COND_DEFAULT: 'searchCond.default',
  /** 목록에서 기본 조건임을 나타내는 표시의 이름. */
  SEARCH_COND_DEFAULT_MARK: 'searchCond.default.mark',
  /** 저장된 것이 하나도 없을 때 목록에 나오는 문구. */
  SEARCH_COND_EMPTY: 'searchCond.empty',
  /** 항목의 삭제 버튼 — 아이콘만 보인다. */
  SEARCH_COND_DELETE: 'searchCond.delete',
  /** 삭제 확인의 본문 — `{name}`. 되돌릴 수 없다. */
  SEARCH_COND_DELETE_CONFIRM: 'searchCond.delete.confirm',
  /** 저장 성공 토스트. */
  SEARCH_COND_SAVED: 'searchCond.saved',

  /**
   * 로고를 눌러 앱을 다시 띄울 때의 확인 — `{tabs}`(frontend/tabs.md).
   *
   * <p>닫기와 문구가 다르다. 닫는 것이 아니라 <b>브라우저를 새로 그린다</b>는 것을
   * 말해야 사용자가 무엇을 잃는지 안다.
   */
  TAB_DIRTY_RELOAD_CONTENT: 'tab.dirty.reload.content',
  /** 그 확인의 실행 버튼. */
  SHELL_RELOAD: 'shell.reload',

  // ── 다국어 메시지 관리 (screen/admin/system/code/requirements.md) ──────────────────────────────
  /** 마스터 그리드의 제목 — 화면 제목이 아니라 그리드 제목이다(frontend/grid.md). */
  MESSAGE_MASTER_TITLE: 'message.master.title',
  /** 하단 그리드의 제목. */
  MESSAGE_DETAIL_TITLE: 'message.detail.title',
  /** 마스터의 키 컬럼 — 신규 행에서만 편집된다. */
  MESSAGE_COL_KEY: 'message.col.msgKey',
  /** 사용처 컬럼 — 읽기 전용이다. 시드가 소유한다(sys/message.md). */
  MESSAGE_COL_SCOPE: 'message.col.useScope',
  /** 번들 컬럼 — 사용처의 파생이다(sys/code.md). */
  MESSAGE_COL_BUNDLED: 'message.col.bundled',
  /** 번역 컬럼 — `list` 에서 계산한다. `1/2` 처럼 낸다. */
  MESSAGE_COL_TRANSLATED: 'message.col.translated',
  /** 설명 컬럼 — 상수의 Javadoc 에서 온 값이라 읽기 전용이다. */
  MESSAGE_COL_DESC: 'message.col.msgDesc',
  /** 하단의 언어 컬럼. */
  MESSAGE_COL_LANG: 'message.col.langCd',
  /** 하단의 값 컬럼 — 2000자라 셀에서 보지 않는다(frontend/grid.md). */
  MESSAGE_COL_VALUE: 'message.col.msgVal',
  /** 사용처 `BOTH` — 프론트·백엔드 양쪽 상수에 있다. */
  MESSAGE_SCOPE_BOTH: 'message.scope.BOTH',
  /** 사용처 `FRONT`. */
  MESSAGE_SCOPE_FRONT: 'message.scope.FRONT',
  /** 사용처 `BACK` — 번들에 실리지 않는다. */
  MESSAGE_SCOPE_BACK: 'message.scope.BACK',
  /** 사용처 `DATA` — `menu.*` · `code.*`. */
  MESSAGE_SCOPE_DATA: 'message.scope.DATA',
  /** 사용처 `NONE` — 아무도 안 쓰는 죽은 키다. */
  MESSAGE_SCOPE_NONE: 'message.scope.NONE',
  /** 번역이 하나라도 비었을 때 붙는 배지(MASTER-06). */
  MESSAGE_UNTRANSLATED: 'message.untranslated',
  /** 조회조건 — 메시지 키. */
  MESSAGE_SEARCH_KEY: 'message.search.msgKey',
  /** 조회조건 — 메시지 값. 어느 언어든 걸린다(screen/admin/system/code/requirements.md). */
  MESSAGE_SEARCH_VALUE: 'message.search.msgVal',
  /** 조회조건 — 사용처. */
  MESSAGE_SEARCH_SCOPE: 'message.search.useScope',
  /** 조회조건 — 미번역 언어. 서버 조건이다(screen/admin/system/code/requirements.md). */
  MESSAGE_SEARCH_UNTRANSLATED: 'message.search.untranslated',
  /** 마스터에서 행을 고르기 전 하단의 빈 상태(frontend/grid.md). */
  MESSAGE_DETAIL_EMPTY: 'message.detail.empty',
  /** 같은 키가 이 화면 안에 둘 있을 때 — 클라이언트가 판정할 수 있다. */
  MESSAGE_DUPLICATE_KEY: 'message.validate.duplicate',

  // ── 시스템 환경설정 (screen/admin/system/config/requirements.md) ────────────────────────────────
  /** 그리드의 제목 — 단일 그리드다. */
  CONFIG_GRID_TITLE: 'config.grid.title',
  /** 키 컬럼 — PK 이고 <b>코드가 정한다</b>. 열리지 않는다. */
  CONFIG_COL_KEY: 'config.col.cfgKey',
  /** 기본값 컬럼 — `yml` 이며 <b>언제나 있다</b>. 숨기지 않는다(screen/admin/system/config/requirements.md). */
  CONFIG_COL_DEFAULT: 'config.col.defaultVal',
  /** 적용값 컬럼 — <b>이 화면의 유일한 입력 칸</b>이다. */
  CONFIG_COL_VALUE: 'config.col.cfgVal',
  /** 유형 컬럼 — 필드 타입에서 온다. 읽기 전용. */
  CONFIG_COL_TYPE: 'config.col.cfgType',
  /** 설명 컬럼 — 필드 Javadoc 에서 온다. `CFG_DESC` 가 아니다(screen/admin/system/config/requirements.md). */
  CONFIG_COL_DESC: 'config.col.cfgDesc',
  /**
   * 삭제 컬럼의 헤더 — <b>되돌리기</b> 다(screen/admin/system/config/requirements.md).
   *
   * <p>*삭제* 면 관리자는 값이 사라질 것을 기대하고, 목록에 같은 키가 남아 있으면
   * <b>버그로 읽는다</b> — 기대를 라벨이 만든다.
   */
  CONFIG_COL_REVERT: 'config.col.revert',
  /**
   * 적용값 뒤에 붙는 배지 — 근거는 <b>`SYS_CONFIG` 행의 존재</b>다(screen/admin/system/config/requirements.md).
   *
   * <p>값이 기본값과 같아도 붙는다. 같은 값으로 저장해 두면 <b>다음 배포가 기본값을
   * 올려도 그 키만 옛 값에 묶이며</b>, 눈으로는 구분되지 않으므로 이것이 유일한 신호다.
   */
  CONFIG_OVERRIDDEN: 'config.overridden',
  /** 조회조건의 반대쪽 — 오버라이드 행이 없는 키. */
  CONFIG_DEFAULTED: 'config.defaulted',
  /** 유형 `STRING`. */
  CONFIG_TYPE_STRING: 'config.type.STRING',
  /** 유형 `NUMBER` — 화면이 문자를 막는다(frontend/grid.md). */
  CONFIG_TYPE_NUMBER: 'config.type.NUMBER',
  /** 유형 `BOOLEAN` — `true`/`false` 아닌 값을 막는다. */
  CONFIG_TYPE_BOOLEAN: 'config.type.BOOLEAN',
  /** 유형 `JSON` — 컬렉션을 한 칸에 담은 문자열이다(sys/batch.md). */
  CONFIG_TYPE_JSON: 'config.type.JSON',
  /** 조회조건 — 키와 설명을 <b>한 칸이 함께 건다</b>(sys/code.md). */
  CONFIG_SEARCH_KEYWORD: 'config.search.keyword',
  /** 조회조건 — 접두사. 전 키에서 <b>도출</b>한다(screen/admin/system/config/requirements.md). */
  CONFIG_SEARCH_PREFIX: 'config.search.prefix',
  /** 조회조건 — 변경 여부. 판정은 행의 존재다. */
  CONFIG_SEARCH_OVERRIDDEN: 'config.search.overridden',
  /**
   * 되돌리기 확인 — `{key}` · `{defaultVal}`. <b>기본값을 함께 말한다</b>(screen/admin/system/config/requirements.md).
   *
   * <p>값이 사라지는 것이 아니라 `yml` 이 드러난다는 것을 미리 말해 두지 않으면,
   * 되돌린 뒤에도 목록에 남은 같은 키가 <i>"지웠는데 그대로다"</i> 로 읽힌다.
   */
  CONFIG_REVERT_WARN: 'config.revert.warn',
  /**
   * `auth.password.admin-reset-enabled` 를 끌 때 — sys/code.md.
   *
   * <p><b>되돌릴 수는 있지만 되돌릴 사람이 못 들어올 수 있다.</b> 메일 연동이 없는
   * 배포에서 잠긴 계정을 푸는 유일한 경로다.
   */
  CONFIG_ADMIN_RESET_WARN: 'config.adminReset.warn',
  /** `NUMBER` 유형에 숫자가 아닌 값 — 화면이 아는 것은 <b>유형</b>까지다(screen/admin/system/config/requirements.md). */
  CONFIG_VALIDATE_NUMBER: 'config.validate.number',
  /** `BOOLEAN` 유형에 `true`/`false` 아닌 값. */
  CONFIG_VALIDATE_BOOLEAN: 'config.validate.boolean',

  /*
    ── 공통 코드 관리 (screen/admin/system/code/requirements.md) ──────────────────────────────────

    <b>접두가 `code.` 가 아니라 `codeMgmt.` 다.</b> `code.` 는 **데이터가 만드는 키**의
    자리다(sys/code.md — `code.{GRP_CD}.{DTL_CD}`). `generateMessageSeed` 는 그 접두를 아예
    건드리지 않으므로(seed.md), 화면 문구를 거기 두면 **키 행이 시드에 만들어지지 않고**
    값만 넣는 changelog 가 FK 로 죽는다 — 시드를 돌려 보기 전에는 드러나지 않는다.

    `message.` · `config.` 가 도메인 이름 그대로인 것과 갈리는 자리이며, 갈리는 이유가
    이 화면에만 있다: **이 화면의 도메인 이름이 이미 데이터 접두로 쓰이고 있다.**
  */
  /** 마스터 그리드의 제목 — 코드 그룹이다. */
  CODE_GROUP_TITLE: 'codeMgmt.group.title',
  /** 하단 그리드의 제목 — 고른 그룹의 코드다. */
  CODE_DETAIL_TITLE: 'codeMgmt.detail.title',
  /** 그룹 코드 컬럼 — PK 라 <b>신규 행에서만</b> 열린다(screen/admin/system/code/requirements.md). */
  CODE_COL_GRP_CD: 'codeMgmt.col.grpCd',
  /** 그룹명 컬럼 — <b>다국어가 아니다</b>. 관리 화면 전용 명칭이라 컬럼 값이다(sys/code.md). */
  CODE_COL_GRP_NM: 'codeMgmt.col.grpNm',
  /** 그룹 설명 컬럼. */
  CODE_COL_GRP_DESC: 'codeMgmt.col.grpDesc',
  /** 그룹의 코드 수 — 파생이며 <b>삭제 게이트가 읽는다</b>(screen/admin/system/code/requirements.md). */
  CODE_COL_COUNT: 'codeMgmt.col.codeCount',
  /** 코드 컬럼 — 그룹과 복합 PK 다. 신규 행에서만 열린다. */
  CODE_COL_DTL_CD: 'codeMgmt.col.dtlCd',
  /**
   * 코드명 컬럼 — 값이 `SYS_MESSAGE` 로 간다(screen/admin/system/code/requirements.md).
   *
   * <p>여기서 채우는 것은 <b>지금 세션 언어</b> 하나다. 다른 언어는 `MESSAGE_MGMT` 로
   * 간다 — 언어를 컬럼으로 늘리면 지원 언어를 하나 켤 때마다 그리드가 옆으로 자란다.
   */
  CODE_COL_CODE_NM: 'codeMgmt.col.codeNm',
  /** 코드 설명 컬럼 — 관리 화면 전용이다(sys/code.md). */
  CODE_COL_DTL_DESC: 'codeMgmt.col.dtlDesc',
  /** 사용 여부 컬럼 — 스위치다(frontend/grid.md). 시스템 행에서는 잠긴다(screen/admin/system/code/requirements.md). */
  CODE_COL_USE_YN: 'codeMgmt.col.useYn',
  /** 정렬 컬럼 — 그룹에서는 관리 화면 정렬, 코드에서는 <b>콤보 정렬</b>이다. */
  CODE_COL_SORT_NO: 'codeMgmt.col.sortNo',
  /** 시스템 여부 컬럼 — <b>읽기 전용</b>이다. 왜 못 지우는지가 눌러 보기 전에 보인다. */
  CODE_COL_SYS_YN: 'codeMgmt.col.sysYn',
  /** 시스템 행에 붙는 배지의 글자. */
  CODE_SYSTEM_BADGE: 'codeMgmt.systemBadge',
  /** 마스터에서 그룹을 고르기 전 하단의 빈 상태(frontend/grid.md). */
  CODE_DETAIL_EMPTY: 'codeMgmt.detail.empty',
  /**
   * 미저장 신규 그룹을 골랐을 때 — <b>잠긴 상태</b>다(frontend/grid.md).
   *
   * <p>부모 키가 아직 DB 에 없어 자식을 붙일 수 없다. screen/admin/system/code/requirements.md 의 메시지 화면이 이 규칙을
   * 비껴간 것은 하단이 DB 에 붙지 않아서였고, 여기 하단은 <b>자기 테이블에 FK 로 붙어
   * 있다</b> — 2단계 등록을 그대로 치른다.
   */
  CODE_DETAIL_LOCKED: 'codeMgmt.detail.locked',
  /** 하단 ⓐ의 버튼 — <b>서버를 부르지 않고 모달을 연다</b>(frontend/grid.md · screen/admin/system/code/requirements.md). */
  CODE_REF_DEF_BUTTON: 'codeMgmt.refDef.button',
  /** 정의 모달의 제목 — `{grpNm}` 이 어느 그룹의 것인지를 말한다. */
  CODE_REF_DEF_TITLE: 'codeMgmt.refDef.title',
  /** 정의 모달 안 그리드의 제목. */
  CODE_REF_DEF_GRID: 'codeMgmt.refDef.grid',
  /** 참조 코드 컬럼 — `CODE_REF_KEY` 화이트리스트에서 고른다(sys/code.md). */
  CODE_COL_REF_CD: 'codeMgmt.col.refCd',
  /** 참조 코드명 컬럼 — <b>하단 그리드의 컬럼 헤더가 된다</b>. 필수다(sys/code.md). */
  CODE_COL_REF_NM: 'codeMgmt.col.refNm',
  /** 참조 코드 설명 컬럼. */
  CODE_COL_REF_DESC: 'codeMgmt.col.refDesc',
  /** 입력된 참조값 건수 — 정의 삭제 확인이 말하는 수다(screen/admin/system/code/requirements.md). */
  CODE_COL_REF_COUNT: 'codeMgmt.col.refValueCount',
  /**
   * 정의 버튼이 잠긴 이유 — <b>미저장 편집이 있다</b>(screen/admin/system/code/requirements.md).
   *
   * <p>정의가 바뀌면 컬럼 집합이 바뀌고 하단 재조회가 편집을 버린다. 열고 나서 묻는
   * 것보다 <b>누를 수 없는 것이 먼저 보이는 편</b>이 낫고, 그 사유를 말한다.
   */
  CODE_REF_DEF_DIRTY: 'codeMgmt.refDef.dirty',
  /** 그룹을 고르지 않은 채 정의를 열려 했을 때. */
  CODE_REF_DEF_NO_GROUP: 'codeMgmt.refDef.noGroup',
  /** 그룹 삭제가 잠긴 이유 — `{count}`. 회색 체크박스만 두면 <b>버그로 읽힌다</b>(screen/admin/system/code/requirements.md). */
  CODE_GROUP_HAS_CODE: 'codeMgmt.group.hasCode',
  /** 코드 삭제 저장 전 확인 — `{count}`. 참조값은 <b>그 행의 파생</b>이라 함께 지운다. */
  CODE_DELETE_REF_WARN: 'codeMgmt.delete.refWarn',
  /** 정의 삭제 저장 전 확인 — `{count}`. 값 → 정의 순서라 FK 가 거부하지 않는다. */
  CODE_REF_DEF_DELETE_WARN: 'codeMgmt.refDef.deleteWarn',
  /** 같은 코드가 이 그리드 안에 둘 있을 때 — 클라이언트가 판정할 수 있다. */
  CODE_DUPLICATE: 'codeMgmt.validate.duplicate',
  /** 조회조건 — 그룹 코드. */
  CODE_SEARCH_GRP_CD: 'codeMgmt.search.grpCd',
  /** 조회조건 — 그룹명. */
  CODE_SEARCH_GRP_NM: 'codeMgmt.search.grpNm',
  /**
   * 조회조건 — 코드. <b>하단 조건이 마스터를 거른다</b>(screen/admin/system/code/requirements.md).
   *
   * <p>*"이 코드값이 어느 그룹에 있더라"* 가 실제로 자주 묻는 것이며, 고르면 하단이
   * 그 조건으로 미리 걸려 있다.
   */
  CODE_SEARCH_DTL_CD: 'codeMgmt.search.dtlCd',
  /** 조회조건 — 코드명. 이름은 `SYS_MESSAGE` 에 있고 <b>어느 언어든 걸린다</b>. */
  CODE_SEARCH_CODE_NM: 'codeMgmt.search.codeNm',

  /*
    ── API 카탈로그 (screen/menu/requirements.md) ────────────────────────────────────

    <b>이 묶음에 저장·삭제·확인 문구가 하나도 없다.</b> 화면이 데이터를 고치지 않기
    때문이며(screen/menu/requirements.md), 그 사실이 메시지 목록의 모양에까지 드러난다 — 여기 있는 것은
    <b>컬럼 이름과 진단 문구</b>뿐이다.
  */
  /** 마스터 그리드의 제목 — API 마스터 목록이다. */
  API_CATALOG_GRID_TITLE: 'apiCatalog.grid.title',
  /** 하단 pane 의 제목 — <b>탭이 아니다</b>(screen/menu/requirements.md). */
  API_CATALOG_MENU_TITLE: 'apiCatalog.menu.title',
  /** API URL 컬럼 — PK 이고 <b>기본 정렬</b>이다. 열리지 않는다. */
  API_CATALOG_COL_API_URL: 'apiCatalog.col.apiUrl',
  /** API 명 컬럼 — `@Operation.summary` 다. <b>빌 수 없다</b>(ArchUnit 이 빌드를 깬다). */
  API_CATALOG_COL_API_NM: 'apiCatalog.col.apiNm',
  /** 액션 컬럼 — <b>공통코드가 아니라 API 의 속성</b>이다(sys/code.md). */
  API_CATALOG_COL_ACTION_CD: 'apiCatalog.col.actionCd',
  /** 설명 컬럼 — `@Operation.description` 이며 <b>빌 수 있다</b>. 선택이다. */
  API_CATALOG_COL_API_DESC: 'apiCatalog.col.apiDesc',
  /** 매핑 메뉴 수 컬럼 — <b>`0` 이 곧 진단이다</b>(screen/menu/requirements.md). */
  API_CATALOG_COL_MENU_COUNT: 'apiCatalog.col.menuCount',
  /**
   * 감사 컬럼의 라벨 — <b>최종 시드 반영</b>이다. *등록일* 이 아니다(screen/menu/requirements.md).
   *
   * <p>시드는 전건 삭제 후 전건 삽입이라(seed.md) <b>전 행이 같은 값</b>이며, 뜻은
   * *"이 배포에서 시드가 다시 쓰인 시각"* 이다. 라벨이 그 기대를 만든다.
   */
  API_CATALOG_COL_SEED_DTM: 'apiCatalog.col.seedDtm',
  /**
   * 매핑 메뉴 `0` 에 붙는 배지 — screen/menu/requirements.md 의 `미연결` 과 <b>같은 어휘, 다른 뜻</b>이다.
   *
   * <p>둘 다 *"연결이 끊겼다"* 이고 <b>고치는 곳만 다르다</b> — 미연결은 프론트 라우트,
   * 미매핑은 `endpoints.ts` 와 시드다. 화면이 둘이어도 사용자가 배우는 것은 하나다.
   */
  API_CATALOG_UNMAPPED: 'apiCatalog.unmapped',
  /** 조회조건 `매핑 상태` 의 반대쪽 — 이 API 를 쓰는 메뉴가 하나라도 있다. */
  API_CATALOG_MAPPED: 'apiCatalog.mapped',
  /** 하단 — 메뉴 경로 컬럼. <b>전체 경로</b>다(screen/menu/requirements.md). */
  API_CATALOG_COL_MENU_PATH: 'apiCatalog.col.menuPath',
  /** 하단 — 메뉴 코드 컬럼. */
  API_CATALOG_COL_MENU_CD: 'apiCatalog.col.menuCd',
  /** 하단 — 메뉴의 사용 여부. 꺼져 있으면 매핑이 <b>살아 있어도 소용없다</b>(backend/security.md). */
  API_CATALOG_COL_MENU_USE_YN: 'apiCatalog.col.menuUseYn',
  /** 하단 — 접근 가능 인원(AUTHZ-13). <b>게이트 ④를 먹이지 않은 수</b>다. */
  API_CATALOG_COL_USER_COUNT: 'apiCatalog.col.accessibleUserCount',
  /** 조회조건 — API URL. 로그에서 얻은 URL 하나가 들어오는 칸이다(screen/admin/system/log/access/requirements.md). */
  API_CATALOG_SEARCH_API_URL: 'apiCatalog.search.apiUrl',
  /** 조회조건 — API 명. */
  API_CATALOG_SEARCH_API_NM: 'apiCatalog.search.apiNm',
  /** 조회조건 — 액션. <b>기본은 전체</b>다. */
  API_CATALOG_SEARCH_ACTION: 'apiCatalog.search.actionCd',
  /**
   * 조회조건 — 매핑 상태. <b>진단의 입구</b>다(screen/menu/requirements.md).
   *
   * <p>컬럼 필터로 두면 현재 페이지 안에서만 걸리므로(frontend/grid.md) <b>서버 조건</b>이다.
   */
  API_CATALOG_SEARCH_MAPPED: 'apiCatalog.search.mapped',
  /** 보통의 빈 상태 — 조건이 안 맞아 0건이다(screen/menu/requirements.md). */
  API_CATALOG_EMPTY: 'apiCatalog.empty',
  /**
   * <b>여기가 진단이다</b> — URL 칸에만 값을 넣었는데 0건일 때(screen/menu/requirements.md).
   *
   * <p>미등록은 화면이 <i>보여줄 수</i> 없다 — 없는 행을 목록에 낼 방법이 없기 때문이다.
   * *"조회 결과가 없습니다"* 로 끝내면 이 화면의 세 갈래 중 하나가 사라진다.
   */
  API_CATALOG_NOT_REGISTERED: 'apiCatalog.notRegistered',
  /** 마스터에서 API 를 고르기 전 하단의 빈 상태(frontend/grid.md). */
  API_CATALOG_MENU_EMPTY: 'apiCatalog.menu.empty',
  /** 하단이 0건일 때 — <b>배지와 같은 말을 pane 에서도 한다</b>(screen/menu/requirements.md). */
  API_CATALOG_MENU_UNMAPPED: 'apiCatalog.menu.unmapped',

  // ── 사용 이력 (screen/admin/system/log/access/requirements.md) ──────────────────────────────────────
  //
  // 접두가 `accessLog.` 다. `log.` 로 두지 않은 것은 이 폴더에 화면이 셋이기 때문이며
  // (사용 이력 · 로그인 이력 · 에러 로그), 셋이 같은 접두를 쓰면 어느 화면의 문구인지
  // 키만 보고 알 수 없다.

  /** 마스터 그리드의 제목 — 이 화면에 그리드가 하나다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_GRID_TITLE: 'accessLog.grid.title',
  /** 일시 컬럼 — <b>정렬이 열린 유일한 컬럼</b>이다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_COL_CRE_DTM: 'accessLog.col.creDtm',
  /** 상태 컬럼 — 응답의 HTTP 상태. 전 요청을 받으므로 필요하다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_COL_HTTP_STAT: 'accessLog.col.httpStat',
  /** 사용자 아이디 컬럼 — 미인증 요청은 {@link #ACCESS_LOG_ANONYMOUS} 다. */
  ACCESS_LOG_COL_USER_ID: 'accessLog.col.userId',
  /**
   * 사용자명 컬럼 — `SYS_USER` 를 조인해 얻는다(2026-09-01).
   *
   * <p>미인증 요청과 <b>지워진 계정</b>에서 비며, 그 자리를 채우지 않는다 — 바로 왼쪽
   * 칸이 이미 `(익명)` 이라고 말하고 있어 <b>이 칸의 빈칸은 덧말</b>이다. 로그인 이력이
   * `없는 계정` 배지를 붙이는 것과 갈리는 자리다(거기는 없는 계정이 <i>흔한 일</i>이다).
   */
  ACCESS_LOG_COL_USER_NM: 'accessLog.col.userNm',
  /** 메뉴 컬럼 — 코드가 아니라 <b>메뉴명</b>으로 그린다(sys/code.md). */
  ACCESS_LOG_COL_MENU: 'accessLog.col.menu',
  /** 요청 URI 컬럼. */
  ACCESS_LOG_COL_REQUEST_URI: 'accessLog.col.requestUri',
  /** 전문 컬럼 — 값이 아니라 `[보기]` 다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_COL_PAYLOAD: 'accessLog.col.payload',
  /** IP 컬럼. */
  ACCESS_LOG_COL_CLIENT_IP: 'accessLog.col.clientIp',
  /** 추적 ID 컬럼 — 5xx 토스트의 그 값이다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_COL_TRACE_ID: 'accessLog.col.traceId',
  /** 순번 컬럼 — tie-breaker 이며 기본 숨김이다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_COL_SEQ: 'accessLog.col.seq',
  /**
   * 사용자 칸이 빈 행의 표시 — screen/admin/system/log/access/requirements.md.
   *
   * <p>빈칸으로 두면 <b>*수집이 안 된 것*과 구분되지 않는다</b>. 미인증 요청에
   * `USER_ID` 가 없는 것은 정상이다.
   */
  ACCESS_LOG_ANONYMOUS: 'accessLog.anonymous',
  /** 메뉴 문맥 없는 요청의 표시 — 로그인·하트비트가 그렇다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_NO_MENU: 'accessLog.noMenu',
  /**
   * 메뉴 컬럼 헤더의 툴팁 — <b>클라이언트가 밝힌 값</b>이다(backend/packages.md).
   *
   * <p>붙이지 않으면 이 화면의 메뉴별 집계가 <b>감사 근거로 인용된다</b>(screen/admin/system/log/access/requirements.md).
   */
  ACCESS_LOG_MENU_CLIENT_CLAIM: 'accessLog.menu.clientClaim',
  /** 전문 셀에 그려지는 글자 — 값이 아니다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_PAYLOAD_LINK: 'accessLog.payload.link',
  /** 전문 모달의 제목. */
  ACCESS_LOG_PAYLOAD_TITLE: 'accessLog.payload.title',
  /** 전문 모달 하단의 글자 수 — `{count}`(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_PAYLOAD_LENGTH: 'accessLog.payload.length',
  /**
   * 전문이 상한에서 잘렸다 — backend/packages.md.
   *
   * <p>말하지 않으면 잘린 JSON 을 받은 사람이 *"저장이 깨졌다"* 로 읽는다. 이 화면에서
   * 가장 흔한 오독이다.
   */
  ACCESS_LOG_PAYLOAD_TRUNCATED: 'accessLog.payload.truncated',
  /** 푸터 왼쪽의 덧붙임 — `{days}`. 총 건수를 잃은 자리를 메운다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_FOOTER_RANGE: 'accessLog.footer.range',
  /**
   * 0건일 때 — <b>보존 기간을 함께 말한다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>*"조회 결과가 없습니다"* 로 끝내면 조건이 좁은 것인지 거기 데이터가 없는 것인지
   * 구분되지 않는다. <b>0건에는 언제나 이유가 있고 화면이 아는 이유는 화면이 말한다.</b>
   */
  ACCESS_LOG_EMPTY: 'accessLog.empty',
  /** 조회조건 — 기간. <b>필수</b>이며 상한이 31일이다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_SEARCH_PERIOD: 'accessLog.search.period',
  /** 조회조건 — 상태. 파생이며 기본은 전체다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_SEARCH_OUTCOME: 'accessLog.search.outcome',
  /** 상태 조건의 항목 — 2xx·3xx. */
  ACCESS_LOG_OUTCOME_SUCCESS: 'accessLog.outcome.success',
  /** 상태 조건의 항목 — 4xx·5xx. */
  ACCESS_LOG_OUTCOME_FAILURE: 'accessLog.outcome.failure',
  /**
   * 조회조건 — 사용자 아이디. <b>문자열 비교 넷</b>(같음 · 시작 · 포함 · 끝)이다(2026-09-01).
   *
   * <p>인덱스가 없는 것은 그대로인데 <b>기간이 먼저 좁힌다</b>(screen/admin/system/log/access/requirements.md) — 완전 일치만
   * 열어 두면 조각을 든 사람이 기간을 넓혀 훑고, 그것이 더 비싸다.
   */
  ACCESS_LOG_SEARCH_USER_ID: 'accessLog.search.userId',
  /** 조회조건 — 사용자명. 이름은 `SYS_USER` 조인이 답한다(2026-09-01). */
  ACCESS_LOG_SEARCH_USER_NM: 'accessLog.search.userNm',
  /*
    아래 하나는 <b>선언만 남았다</b>(2026-09-01) — 메뉴 칸이 콤보 하나에서 텍스트 둘
    (코드 · 이름)로 갈렸다.

    <b>지우지 못한다.</b> 값 시드에 그 키의 ko·en 행이 이미 들어 있고 `SeedMessageTest` 는
    <i>값에는 키 테이블에 없는 행이 없다</i> 를 검사한다 — 선언을 지우면 생성 시드에서
    키가 빠져 <b>그 행들이 고아가 되고 빌드가 깨진다</b>(`MENU_SEARCH_NM` 과 같은 사정이다).
  */
  ACCESS_LOG_SEARCH_MENU: 'accessLog.search.menu',
  /**
   * 조회조건 — 메뉴 코드. 뿌리 테이블의 컬럼이라 값 비교 하나로 끝난다.
   *
   * <p><b>`메뉴 없음` 항목이 여기 없다.</b> 콤보였을 때는 `in` 배열의 `null` 이
   * `IS NULL` 로 펴졌는데(screen/admin/system/log/access/requirements.md) 텍스트 칸에는 담을 자리가 없다 — 그 물음은
   * <b>메뉴 컬럼의 필터</b>가 답한다(frontend/grid.md).
   */
  ACCESS_LOG_SEARCH_MENU_CD: 'accessLog.search.menuCd',
  /**
   * 조회조건 — 메뉴명. <b>이름은 두 테이블 건너에 있다</b>(sys/code.md).
   *
   * <p>`SYS_MENU.MENU_NM` 이 담은 것은 메시지 키이고 이름은 `SYS_MESSAGE` 의 값이라
   * 서버가 `EXISTS` 로 푼다. <b>어느 언어든 걸린다</b> — 운영자는 화면에 보이는 그 이름을
   * 들고 오지 어느 언어의 값인지를 들고 오지 않는다(screen/admin/system/code/requirements.md).
   */
  ACCESS_LOG_SEARCH_MENU_NM: 'accessLog.search.menuNm',
  /** 조회조건 — 요청 URI. 앞 일치다(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_SEARCH_REQUEST_URI: 'accessLog.search.requestUri',
  /** 조회조건 — IP. 완전 일치다. */
  ACCESS_LOG_SEARCH_CLIENT_IP: 'accessLog.search.clientIp',
  /** 조회조건 — 추적 ID. <b>넣으면 기간이 선택이 된다</b>(screen/admin/system/log/access/requirements.md). */
  ACCESS_LOG_SEARCH_TRACE_ID: 'accessLog.search.traceId',

  // ── 로그인 이력 (screen/admin/system/log/access/requirements.md) ────────────────────────────────────
  //
  // 접두가 `loginLog.` 다. 이 화면에는 **모달도 파생 컬럼도 없어** 사용 이력보다 키가
  // 적다 — `nclob` 컬럼이 하나도 없는 유일한 화면이라 목록이 곧 전부다.
  //
  // **결과 코드 15종의 이름이 여기 없다.** 그것은 공통코드이며 `code.LOGIN_RSLT_CD.{코드}`
  // 로 데이터에서 온다(sys/code.md) — 소스 상수로 두면 코드를 더할 때 시드와 이 파일 둘을
  // 고쳐야 한다.

  /** 마스터 그리드의 제목 — 이 화면에 그리드가 하나다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_GRID_TITLE: 'loginLog.grid.title',
  /** 일시 컬럼 — <b>정렬이 열린 유일한 컬럼</b>이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_COL_CRE_DTM: 'loginLog.col.creDtm',
  /**
   * 사용자 아이디 컬럼 — <b>시도한 ID</b> 다(screen/admin/system/log/access/requirements.md).
   *
   * <p>헤더가 *시도 ID* 였다(2026-09-01 에 바뀌었다). 이력 셋의 헤더를 <b>사용자 아이디 ·
   * 사용자명</b>으로 통일하면서 갈렸는데, *사용자* 라고 쓰면 `SYS_USER` 에 있는 것처럼
   * 읽힌다던 걱정은 <b>이름 칸의 `없는 계정` 배지가 대신 말한다</b> — 헤더를 화면마다
   * 다르게 부르는 대가가 더 크다. 툴팁({@link #LOGIN_LOG_USER_ID_ATTEMPTED})은 남는다.
   */
  LOGIN_LOG_COL_USER_ID: 'loginLog.col.userId',
  /**
   * 사용자명 컬럼 — `SYS_USER` 를 조인해 얻는다(2026-09-01).
   *
   * <p>없는 계정의 시도에서는 비고, 그 자리에 <b>`없는 계정` 배지</b>가 붙는다. 배지의
   * 글자와 색은 <b>결과 컬럼의 `FAIL_NO_USER` 와 같은 것</b>이며 같은 공통코드 행
   * (`code.LOGIN_RSLT_CD.FAIL_NO_USER`)에서 온다 — 한 행의 두 칸이 같은 사실을 두 글자로
   * 말하면 사람이 <b>둘을 다른 사실로 읽는다</b>.
   */
  LOGIN_LOG_COL_USER_NM: 'loginLog.col.userNm',
  /** 결과 컬럼 — 공통코드 15종이며 배지의 색은 셋이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_COL_RESULT: 'loginLog.col.result',
  /** IP 컬럼. */
  LOGIN_LOG_COL_CLIENT_IP: 'loginLog.col.clientIp',
  /** User-Agent 컬럼 — <b>기본 숨김</b>이다. 켜면 다른 컬럼이 전부 밀린다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_COL_USER_AGENT: 'loginLog.col.userAgent',
  /** 추적 ID 컬럼 — 5xx 토스트의 그 값이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_COL_TRACE_ID: 'loginLog.col.traceId',
  /** 순번 컬럼 — tie-breaker 이며 기본 숨김이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_COL_SEQ: 'loginLog.col.seq',
  /**
   * 시도 ID 헤더의 툴팁 — <b>등록된 계정이 아닐 수 있다</b>(sys/logs.md · screen/admin/system/log/access/requirements.md).
   *
   * <p>말하지 않으면 없는 ID 의 행이 <b>결함처럼</b> 보인다. 그 행들이야말로 이 화면의
   * 두 번째 용도(침입 시도 탐지)가 찾는 것이다.
   */
  LOGIN_LOG_USER_ID_ATTEMPTED: 'loginLog.userId.attempted',
  /** 시도 ID 가 빈 행의 표시 — 빈칸은 <b>*수집이 안 된 것*과 구분되지 않는다</b>(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_NO_USER_ID: 'loginLog.noUserId',
  /** 푸터 왼쪽의 덧붙임 — `{days}`. 총 건수를 잃은 자리를 메운다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_FOOTER_RANGE: 'loginLog.footer.range',
  /** 0건일 때 — <b>보존 기간(1년)을 함께 말한다</b>(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_EMPTY: 'loginLog.empty',
  /**
   * <i>실패만</i> 으로 물었는데 0건이다 — <b>문구가 다르다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>*"조회 결과가 없습니다"* 로 끝내면 <b>진단이 빈 결과로 읽힌다</b>. 이 기간에
   * 로그인 실패가 없었다는 것은 답이지 실패가 아니다.
   */
  LOGIN_LOG_EMPTY_FAILED: 'loginLog.empty.failed',
  /** 조회조건 — 기간. <b>필수</b>이며 상한은 시스템 값(92일) 그대로다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_SEARCH_PERIOD: 'loginLog.search.period',
  /**
   * 조회조건 — 사용자 아이디. <b>문자열 비교 넷</b>(같음 · 시작 · 포함 · 끝)이다(2026-09-01).
   *
   * <p>인덱스가 없는 것은 그대로인데 <b>기간이 먼저 좁힌다</b>(screen/admin/system/log/access/requirements.md). IP 를 완전 일치로
   * 둔 것과 갈리는 자리다 — 대역 검색은 <i>결과만 넓어지는</i> 조건이지만, 부분 아이디는
   * <b>다른 답</b>을 낸다(`admin` 으로 끝나는 시도가 그렇다).
   */
  LOGIN_LOG_SEARCH_USER_ID: 'loginLog.search.userId',
  /** 조회조건 — 사용자명. 없는 계정의 시도는 이름이 없어 <b>이 조건에 걸리지 않는다</b>. */
  LOGIN_LOG_SEARCH_USER_NM: 'loginLog.search.userNm',
  /** 조회조건 — 결과. 다중 선택이며 후보는 공통코드가 낸다(sys/code.md). */
  LOGIN_LOG_SEARCH_RESULT: 'loginLog.search.result',
  /** 조회조건 — 결과 묶음. 파생이며 배지의 색 셋과 같은 축이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_SEARCH_RSLT_GROUP: 'loginLog.search.rsltGroup',
  /** 결과 묶음의 항목 — `FAIL_*` 열. <b>이 화면의 기본 동선</b>이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_RSLT_GROUP_FAILED: 'loginLog.rsltGroup.failed',
  /** 결과 묶음의 항목 — 로그아웃·만료·강제·밀려남 넷. */
  LOGIN_LOG_RSLT_GROUP_CLOSED: 'loginLog.rsltGroup.closed',
  /** 조회조건 — IP. 완전 일치다. 실패 행에서 복사해 다시 조회하는 동선이다(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_SEARCH_CLIENT_IP: 'loginLog.search.clientIp',
  /** 조회조건 — 추적 ID. <b>넣으면 기간이 선택이 된다</b>(screen/admin/system/log/access/requirements.md). */
  LOGIN_LOG_SEARCH_TRACE_ID: 'loginLog.search.traceId',

  // ── 에러 로그 (screen/admin/system/log/access/requirements.md) ──────────────────────────────────────
  //
  // 접두가 `errorLog.` 다. 셋 중 <b>키가 가장 많다</b> — 상세 모달에 탭이 둘이고, 그 둘이
  // 각각 <i>비어 있는 이유</i> 를 따로 말해야 하기 때문이다: 스택이 없는 것은 4xx 라
  // 저장하지 않은 것이고, 전문이 없는 것은 사용 이력 90일이 지난 것이다. <b>둘은 다른
  // 사실이므로 문구도 둘</b>이며, 하나로 합치면 4xx 를 본 사람이 <i>데이터가 날아갔다</i>
  // 로 읽는다.
  //
  // <b>HTTP 상태의 이름이 여기 없다.</b> 표준이라 번역할 것이 아니고 관리자가 늘릴 것도
  // 아니다 — 공통코드로도 두지 않는다(screen/admin/system/log/access/requirements.md).

  /** 마스터 그리드의 제목 — 이 화면에 그리드가 하나다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_GRID_TITLE: 'errorLog.grid.title',
  /** 일시 컬럼 — <b>정렬이 열린 유일한 컬럼</b>이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_CRE_DTM: 'errorLog.col.creDtm',
  /** 상태 컬럼 — <b>4xx 와 5xx 를 가른다</b>. 배지의 색이 그 둘이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_HTTP_STAT: 'errorLog.col.httpStat',
  /** 오류 코드 컬럼 — 문자열 상수라 <b>이 값으로 묶어 센다</b>(backend/packages.md). */
  ERROR_LOG_COL_ERR_CD: 'errorLog.col.errCd',
  /** 메시지 컬럼 — <b>서버가 완성한 문장</b>이며 사용자가 그때 본 그 글이다. */
  ERROR_LOG_COL_ERR_MSG: 'errorLog.col.errMsg',
  /** 사용자 아이디 컬럼 — 미인증 요청은 {@link #ERROR_LOG_ANONYMOUS} 다. */
  ERROR_LOG_COL_USER_ID: 'errorLog.col.userId',
  /**
   * 사용자명 컬럼 — `SYS_USER` 를 조인해 얻는다(2026-09-01).
   *
   * <p>미인증 요청과 지워진 계정에서 빈다 — 왼쪽 칸이 이미 `(익명)` 이라고 말하므로
   * 그 자리를 채우지 않는다(사용 이력과 같은 규칙이다).
   */
  ERROR_LOG_COL_USER_NM: 'errorLog.col.userNm',
  /**
   * 메뉴 컬럼 — <b>요청 URI 앞</b>에 보인다(2026-09-01. 그 전에는 기본 숨김이었다).
   *
   * <p>클라이언트 주장이라 헤더 툴팁이 그 사실을 말한다(backend/packages.md).
   */
  ERROR_LOG_COL_MENU: 'errorLog.col.menu',
  /** 요청 URI 컬럼 — <b>더블 클릭이 `API_CATALOG` 로 건너뛴다</b>(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_REQUEST_URI: 'errorLog.col.requestUri',
  /** 상세 컬럼 — 파생이며 셀에 그려지는 것은 `[보기]` 다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_DETAIL: 'errorLog.col.detail',
  /** 추적 ID 컬럼 — 5xx 토스트의 그 값이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_TRACE_ID: 'errorLog.col.traceId',
  /** IP 컬럼 — <b>기본 숨김</b>이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_CLIENT_IP: 'errorLog.col.clientIp',
  /** 순번 컬럼 — tie-breaker 이며 기본 숨김이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_COL_SEQ: 'errorLog.col.seq',
  /** 상세 셀에 그려지는 글자 — 값이 아니라 <b>여는 것</b>이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_DETAIL_LINK: 'errorLog.detail.link',
  /** 사용자 컬럼이 빈 행의 표시 — 빈칸은 <b>*수집이 안 된 것*과 구분되지 않는다</b>(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_ANONYMOUS: 'errorLog.anonymous',
  /** 메뉴 컬럼이 빈 행의 표시 — 로그인·하트비트는 셸 진입 전이라 메뉴가 없다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_NO_MENU: 'errorLog.noMenu',
  /** 메뉴 헤더의 툴팁 — <b>클라이언트 주장이다</b>(backend/packages.md · backend/security.md). */
  ERROR_LOG_MENU_CLIENT_CLAIM: 'errorLog.menu.clientClaim',
  /** 푸터 왼쪽의 덧붙임 — `{days}`. 총 건수를 잃은 자리를 메운다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_FOOTER_RANGE: 'errorLog.footer.range',
  /** 0건일 때 — <b>보존 기간(180일)을 함께 말한다</b>(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_EMPTY: 'errorLog.empty',
  /**
   * <i>5xx 만</i> 으로 물었는데 0건이다 — <b>진단으로 읽히게 한다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>*"조회 결과가 없습니다"* 로 끝내면 답이 빈 결과로 읽힌다. 이 기간에 서버 오류가
   * 없었다는 것은 <b>가장 듣고 싶은 답</b>이다.
   */
  ERROR_LOG_EMPTY_SERVER: 'errorLog.empty.server',
  /**
   * 추적 ID 로 물었는데 0건이다 — <b>유실이 아니다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>§1.4 이후 모든 요청은 사용 이력에 있고 여기는 <b>예외가 난 것만</b> 받는다. 그래서
   * 0건이면 *"그 요청은 오류가 아니었다"* 는 뜻이며 <b>사용 이력에는 반드시 있다</b> —
   * 문구가 그 사실을 사용자 말로 옮겨 <b>사용 이력으로 보낸다</b>.
   */
  ERROR_LOG_EMPTY_TRACE: 'errorLog.empty.trace',
  /** 조회조건 — 기간. <b>필수</b>이며 상한은 시스템 값(92일) 그대로다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_SEARCH_PERIOD: 'errorLog.search.period',
  /** 조회조건 — 상태. 파생이며 묶음 둘이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_SEARCH_STATUS: 'errorLog.search.status',
  /** 상태 묶음의 항목 — <b>이 화면의 가장 흔한 조회</b>다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_STATUS_SERVER: 'errorLog.status.server',
  /** 상태 묶음의 항목 — 403 이 대부분이며 대개 <b>정상 동작</b>이다. */
  ERROR_LOG_STATUS_CLIENT: 'errorLog.status.client',
  /** 조회조건 — 오류 코드. <b>완전 일치</b>다. 셀을 복사해 넣는 동선이다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_SEARCH_ERR_CD: 'errorLog.search.errCd',
  /** 조회조건 — 사용자 아이디. <b>문자열 비교 넷</b>이다 — 기간이 먼저 좁힌다(2026-09-01). */
  ERROR_LOG_SEARCH_USER_ID: 'errorLog.search.userId',
  /** 조회조건 — 사용자명. 이름은 `SYS_USER` 조인이 답한다(2026-09-01). */
  ERROR_LOG_SEARCH_USER_NM: 'errorLog.search.userNm',
  /*
    아래 하나는 <b>선언만 남았다</b>(2026-09-01) — 메뉴 칸이 코드와 이름 둘로 갈렸다.
    값 시드의 행을 지울 수 없으므로 선언도 남는다(`ACCESS_LOG_SEARCH_MENU` 와 같은 사정).
  */
  ERROR_LOG_SEARCH_MENU: 'errorLog.search.menu',
  /**
   * 조회조건 — 메뉴 코드. <b>문의 전화가 주는 것</b>이 시각과 화면 이름이다(screen/admin/system/log/access/requirements.md).
   *
   * <p>이름을 받는 칸이 아래에 따로 있으므로 <b>이 칸은 코드를 아는 사람의 것</b>이다 —
   * 대개 개발자이며, 오류 코드 칸과 같은 동선(셀을 복사해 넣는다)이다.
   */
  ERROR_LOG_SEARCH_MENU_CD: 'errorLog.search.menuCd',
  /** 조회조건 — 메뉴명. 이름은 `SYS_MESSAGE` 에 있어 서버가 `EXISTS` 로 푼다(sys/code.md). */
  ERROR_LOG_SEARCH_MENU_NM: 'errorLog.search.menuNm',
  /** 조회조건 — 요청 URI. 앞 일치다(인덱스가 없다). */
  ERROR_LOG_SEARCH_REQUEST_URI: 'errorLog.search.requestUri',
  /** 조회조건 — 추적 ID. <b>넣으면 기간이 선택이 된다</b>(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_SEARCH_TRACE_ID: 'errorLog.search.traceId',
  /** 상세 모달의 제목. */
  ERROR_LOG_DETAIL_TITLE: 'errorLog.detail.title',
  /** 탭 하나 — 요청 전문. <b>다른 테이블에서 온다</b>(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_TAB_PAYLOAD: 'errorLog.tab.payload',
  /** 탭 하나 — 스택트레이스. 4xx 는 비활성이다. */
  ERROR_LOG_TAB_STACK: 'errorLog.tab.stack',
  /**
   * 스택 탭이 비어 있는 이유 — <b>4xx 라 저장하지 않았다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>*"없습니다"* 로 끝내지 않는다. 인가 거부의 스택은 인터셉터 프레임뿐이고 전 행이
   * 같아서 <b>일부러 저장하지 않은 것</b>이며, 그것을 말하지 않으면 유실로 읽는다.
   */
  ERROR_LOG_STACK_NONE: 'errorLog.stack.none',
  /**
   * 전문 탭이 비어 있는 이유 — <b>사용 이력 90일이 지났다</b>(screen/admin/system/log/access/requirements.md).
   *
   * <p>§3.7 의 *"이미 파기된 이력입니다"* 와 같은 어휘다. 보존이 180일 / 90일로 달라
   * 생기는 일이며 <b>고장이 아니다</b>.
   */
  ERROR_LOG_PAYLOAD_PURGED: 'errorLog.payload.purged',
  /** 전문 탭이 비어 있는데 파기는 아니다 — 멀티파트 요청·캐싱이 꺼진 구간이다. */
  ERROR_LOG_PAYLOAD_NONE: 'errorLog.payload.none',
  /** 모달 하단의 글자 수 — `{count}`. */
  ERROR_LOG_DETAIL_LENGTH: 'errorLog.detail.length',
  /** 상한에서 잘렸다 — 말하지 않으면 잘린 값을 <b>깨진 값</b>으로 읽는다(screen/admin/system/log/access/requirements.md). */
  ERROR_LOG_DETAIL_TRUNCATED: 'errorLog.detail.truncated',

  /*
    ── 메뉴 트리 (screen/menu/requirements.md) ───────────────────────────────────────

    <b>여기에 저장 문구가 없다.</b> 통째로 즉시 저장이라 저장 버튼도 미저장 상태도
    없기 때문이며, 대신 <b>확인 문구가 많다</b> — 되돌릴 수 없는 조작마다 놓기 전에 묻는다.
  */
  /** 트리 그리드의 제목. */
  MENU_TREE_TITLE: 'menuTree.grid.title',
  /** 계층 컬럼 — <b>서버가 만든다</b>. 고치는 곳은 `MESSAGE_MGMT` 하나다(sys/menu.md). */
  MENU_COL_MENU_NM: 'menuTree.col.menuNm',
  /** 메뉴 코드 컬럼 — PK 이며 <b>신규 행에서도 셀이 아니다</b>(§4.6 의 모달이 받는다). */
  MENU_COL_MENU_CD: 'menuTree.col.menuCd',
  /** URL 컬럼 — <b>라우트 목록에서 고른다</b>. 자식이 있으면 잠긴다. */
  MENU_COL_MENU_URL: 'menuTree.col.menuUrl',
  /** 아이콘 컬럼 — 아이콘 세트는 frontend/packages.md 이다. */
  MENU_COL_ICON: 'menuTree.col.iconCd',
  /** 사용 여부 컬럼 — 최종 게이트다(backend/security.md). 구조 노드에 놓으면 자손 전체에 쓴다. */
  MENU_COL_USE_YN: 'menuTree.col.useYn',
  /** 매핑 API 수 — `0` 이면 미매핑이다(sys/menu.md). */
  MENU_COL_API_COUNT: 'menuTree.col.apiCount',
  /** 선언에서 사라진 메뉴의 배지 — screen/menu/requirements.md · seed.md. */
  MENU_ORPHAN: 'menuTree.orphan',
  /** 그 배지의 툴팁 — 무엇을 해야 하는지까지 말한다. */
  MENU_ORPHAN_HINT: 'menuTree.orphan.hint',
  /** 접근 가능 인원 — AUTHZ-13. 리프만 값이 있다. */
  MENU_COL_USER_COUNT: 'menuTree.col.accessibleUserCount',
  /** 매핑 역할 수 — 삭제 게이트의 근거다. */
  MENU_COL_ROLE_COUNT: 'menuTree.col.roleCount',
  /** URL 콤보의 첫 항목 — <b>URL 을 비워 폴더로 되돌리는 유일한 경로</b>다. */
  MENU_URL_NONE: 'menuTree.url.none',
  /** 라우트가 없는 URL 에 붙는 배지 — <b>사후 진단</b>이다. */
  MENU_UNLINKED: 'menuTree.unlinked',
  /** Ⓑ — 모두 접기. <b>드래그의 전제</b>다. */
  MENU_COLLAPSE_ALL: 'menuTree.collapseAll',
  /** Ⓑ — 모두 펼치기. */
  MENU_EXPAND_ALL: 'menuTree.expandAll',
  /** ① — 최상위 메뉴 추가. <b>언제나 활성</b>이며 트리의 맨 아래에 붙는다. */
  MENU_ADD_ROOT: 'menuTree.addRoot',
  /** ① — 하위 메뉴 추가. 선택이 없으면 비활성이다. */
  MENU_ADD_CHILD: 'menuTree.addChild',
  /** *하위 메뉴 추가* 가 잠긴 이유 — 선택이 없다. */
  MENU_ADD_CHILD_NO_SELECTION: 'menuTree.addChild.noSelection',
  /** 〃 — URL 이 있는 노드다. 드롭의 가운데 구역이 죽는 조건과 같다. */
  MENU_ADD_CHILD_LEAF: 'menuTree.addChild.leaf',
  /** 〃 — 깊이 5단에 닿았다(sys/menu.md). */
  MENU_ADD_CHILD_DEPTH: 'menuTree.addChild.depth',
  /** ① — 삭제. 게이트 넷이 전부 `0` 일 때만 활성이다. */
  MENU_DELETE: 'menuTree.delete',
  /** 삭제가 잠긴 이유 — `{blockers}` 가 <b>어느 쪽이 막는지</b> 말한다. */
  MENU_DELETE_BLOCKED: 'menuTree.delete.blocked',
  /** 삭제 확인 — `{name}`. 즐겨찾기가 함께 지워지는 것을 말한다. */
  MENU_DELETE_CONFIRM: 'menuTree.delete.confirm',
  /** 신규 모달의 제목 — `{parent}` 가 어디 아래인지 말한다. */
  MENU_ADD_TITLE: 'menuTree.add.title',
  /** 신규 모달의 제목 — 최상위일 때. */
  MENU_ADD_TITLE_ROOT: 'menuTree.add.titleRoot',
  /** 신규 모달의 유일한 칸 — 명명 규약은 sys/menu.md 이다. */
  MENU_ADD_CODE: 'menuTree.add.menuCd',
  /** 그 칸의 안내 — 대문자·숫자·밑줄. */
  MENU_ADD_CODE_HINT: 'menuTree.add.menuCdHint',
  /**
   * 드롭 문장 — <b>안에 넣는다</b>. `{name}`.
   *
   * <p>표시가 최종 판정이 아니라 <b>문장이 최종 판정</b>이다.
   */
  MENU_DROP_INSIDE: 'menuTree.drop.inside',
  /** 드롭 문장 — 이 행 <b>앞</b>에 형제로. `{name}`. */
  MENU_DROP_BEFORE: 'menuTree.drop.before',
  /** 드롭 문장 — 이 행 <b>다음</b>에 형제로. `{name}`. */
  MENU_DROP_AFTER: 'menuTree.drop.after',
  /** 드롭 문장 — 펼친 폴더의 아래 1/4 는 <b>첫 자식 앞</b>이다. `{name}`. */
  MENU_DROP_FIRST_CHILD: 'menuTree.drop.firstChild',
  /** 놓을 수 없다 — 자기 자손이다. <b>이유를 낸다</b>. */
  MENU_DROP_SELF: 'menuTree.drop.self',
  /** 놓을 수 없다 — 그 노드는 화면이 있다. `{name}`. */
  MENU_DROP_LEAF: 'menuTree.drop.leaf',
  /** 놓을 수 없다 — 깊이 상한을 넘는다. `{max}`. */
  MENU_DROP_DEPTH: 'menuTree.drop.depth',
  /** 드롭 확인의 제목 — `{name}` 을 옮긴다. */
  MENU_MOVE_TITLE: 'menuTree.move.title',
  /** 드롭 확인 — <b>지금</b> 자리. `{path}` · `{index}`. */
  MENU_MOVE_FROM: 'menuTree.move.from',
  /** 드롭 확인 — <b>이동</b>할 자리. `{path}` · `{index}`. */
  MENU_MOVE_TO: 'menuTree.move.to',
  /** 드롭 확인의 마지막 줄 — <b>되돌리려면 다시 끌어야 한다</b>. */
  MENU_MOVE_WARN: 'menuTree.move.warn',
  /** 사용 여부를 끌 때 — `{name}`. */
  MENU_USE_OFF_TITLE: 'menuTree.useOff.title',
  /** 〃 자손이 함께 꺼진다 — `{count}`. <b>`0` 이면 이 줄을 내지 않는다</b>. */
  MENU_USE_OFF_DESCENDANTS: 'menuTree.useOff.descendants',
  /** 〃 접근할 수 있던 인원 — `{count}`. */
  MENU_USE_OFF_USERS: 'menuTree.useOff.users',
  /** 사용 여부를 켤 때 — `{name}`. <b>켜는 방향에도 경고가 있다</b>. */
  MENU_USE_ON_TITLE: 'menuTree.useOn.title',
  /** 〃 개별로 꺼 두었던 것까지 켜진다 — `{count}` · `{off}`. */
  MENU_USE_ON_DESCENDANTS: 'menuTree.useOn.descendants',
  /** 하단 탭 — 매핑 API(§4.11 탭 1). */
  MENU_TAB_API: 'menuTree.tab.api',
  /** 하단 탭 — 사용 가능 사용자(AUTHZ-13). */
  MENU_TAB_USER: 'menuTree.tab.user',
  /** 하단 탭 — 매핑된 역할. */
  MENU_TAB_ROLE: 'menuTree.tab.role',
  /** 탭 1 이 0건일 때 — <b>진단이다</b>. */
  MENU_TAB_API_EMPTY: 'menuTree.tab.apiEmpty',
  /** 마스터에서 메뉴를 고르기 전 하단의 빈 상태(frontend/grid.md). */
  MENU_DETAIL_EMPTY: 'menuTree.detail.empty',
  /** 탭 2 의 경유 컬럼 — 역할명 또는 이 문구(개인 예외). */
  MENU_VIA_PERSONAL: 'menuTree.via.personal',
  /**
   * 탭 3 — 역할 ID 컬럼.
   *
   * <p><b>메뉴 쪽 라벨을 돌려쓰지 않는다.</b> 한때 {@link MENU_COL_MENU_CD} 를 그대로 썼는데
   * 헤더가 <i>메뉴 코드</i> 인 칸에 역할 ID 가 들어가 있었다(2026-08-30 실물) — 같은 화면
   * 안에서 컬럼 폭과 자리가 맞으면 <b>틀린 라벨이 눈에 띄지 않는다</b>.
   */
  MENU_COL_ROLE_ID: 'menuTree.col.roleId',
  /** 탭 3 — 역할명 컬럼. */
  MENU_COL_ROLE_NM: 'menuTree.col.roleNm',
  /** 탭 2 — 경유 컬럼의 헤더. */
  MENU_COL_VIA: 'menuTree.col.via',
  /** 탭 2 — 계정 상태. `USE_YN` 과 기간만 본다(sys/user-role.md). */
  MENU_COL_ACCOUNT: 'menuTree.col.accountUsable',
  /** 탭 2 — 액션 4열을 한 칸에 묶은 헤더. */
  MENU_COL_ACTIONS: 'menuTree.col.actions',
  /** 탭 2 — 부여 상태. */
  MENU_COL_GRANT_STATE: 'menuTree.col.grantState',
  /** 탭 2 — 부여 기간. */
  MENU_COL_PERIOD: 'menuTree.col.period',
  /** 부여 상태 — 예정. */
  MENU_GRANT_SCHEDULED: 'menuTree.grant.SCHEDULED',
  /** 부여 상태 — 유효. */
  MENU_GRANT_ACTIVE: 'menuTree.grant.ACTIVE',
  /** 부여 상태 — 만료. <b>감추지 않는다</b> — *어제까지 되던 것* 에 답해야 한다. */
  MENU_GRANT_EXPIRED: 'menuTree.grant.EXPIRED',
  /** 계정이 쓸 수 있는 상태. */
  MENU_ACCOUNT_USABLE: 'menuTree.account.usable',
  /** 계정이 막힌 상태 — 관리자가 정한 뜻이다(`USE_YN` 또는 기간). */
  MENU_ACCOUNT_BLOCKED: 'menuTree.account.blocked',
  /** 조회조건 — 메뉴명. <b>거르지 않는다</b>. */
  /**
   * 트리 검색 — <b>칸 하나가 셋을 함께 본다</b>(2026-08-31 요청 · screen/menu/requirements.md).
   *
   * <p>칸이 셋이었다. 그런데 <b>사람은 손에 있는 것으로 찾는다</b> — 이름이거나 코드이거나
   * URL 이고, <i>어느 칸에 넣을지</i> 를 먼저 정해야 하는 것이 곧 마찰이다. 셋을 `OR` 로
   * 잇는 칸 하나면 그 결정이 사라진다(`userAuth.search.user` 가 ID 와 이름을 함께 보는 것과
   * 같은 판단이다 — screen/admin/system/user/requirements.md).
   *
   * <p>자리표에 <b>무엇을 보는지</b> 를 적는다 — 하나로 합친 칸은 그 말이 없으면 <i>이름만
   * 찾는 칸</i> 으로 읽힌다.
   */
  MENU_SEARCH_ANY: 'menuTree.search.any',
  /*
    아래 다섯은 <b>선언만 남았다</b>(2026-08-31) — 칸 셋이 하나가 되고 이동 버튼이 사라졌다.

    <b>지우지 못한다.</b> 값 시드(`0NN-init-message.csv`)에 그 키의 ko·en 행이 이미 들어
    있고, `SeedMessageTest` 는 <i>값에는 키 테이블에 없는 행이 없다</i> 를 검사한다 —
    선언을 지우면 생성 시드에서 키가 빠져 <b>그 행들이 고아가 되고 빌드가 깨진다</b>.
    적재된 changeset 을 고칠 수도 없다(체크섬 — backend/persistence.md).
  */
  MENU_SEARCH_NM: 'menuTree.search.menuNm',
  /** 조회조건 — 메뉴 코드. */
  MENU_SEARCH_CD: 'menuTree.search.menuCd',
  /** 조회조건 — URL. */
  MENU_SEARCH_URL: 'menuTree.search.menuUrl',
  /** 검색 결과의 위치 — `{index}` / `{total}`. 강조만 하고 이동 수단이 없으면 소용없다. */
  MENU_SEARCH_POSITION: 'menuTree.search.position',
  /** 검색 이동 — 이전. */
  MENU_SEARCH_PREV: 'menuTree.search.prev',
  /** 검색 이동 — 다음. */
  MENU_SEARCH_NEXT: 'menuTree.search.next',
  /** 엑셀의 맨 앞 컬럼 — <b>경로</b>다. 들여쓰기는 정렬 한 번에 무너진다. */
  MENU_EXCEL_PATH: 'menuTree.excel.path',

  // ── 역할 관리 (screen/menu/requirements.md) · 역할 권한 관리 (screen/menu/requirements.md) ────────────────
  //
  // 접두가 둘이다. `role.*` 는 **역할 자체의 생애**(만들고·끄고·지운다)이고
  // `roleAuth.*` 는 **그 역할이 무엇을 할 수 있나**다. 하단 pane 은 두 화면이 같은
  // 그리드라(screen/menu/requirements.md) 그 컬럼들이 `role.col.*` 하나로 모여 있다 — 제목만 화면마다 다르다.

  /** 마스터 그리드의 제목 — 역할 목록. */
  ROLE_GRID_TITLE: 'role.grid.title',
  /** 하단 pane 의 제목(`ROLE_MGMT`) — 같은 그리드를 `ROLE_AUTH_MGMT` 는 *영향 인원* 이라 부른다. */
  ROLE_HOLDER_TITLE: 'role.holder.title',
  /** PK. **신규 행에서만 편집 가능**하다(sys/user-role.md). */
  ROLE_COL_ROLE_ID: 'role.col.roleId',
  /** 필수 컬럼. 다국어가 아니라 관리 화면 전용 명칭이다(sys/code.md). */
  ROLE_COL_ROLE_NM: 'role.col.roleNm',
  ROLE_COL_ROLE_DESC: 'role.col.roleDesc',
  /** 유효 권한 계산의 **입력 필터**다(backend/security.md ②) — 메뉴의 같은 이름과 자리가 다르다. */
  ROLE_COL_USE_YN: 'role.col.useYn',
  /** **삭제 게이트가 보는 수** — 기간을 보지 않는다(screen/menu/requirements.md). */
  ROLE_COL_HOLDER: 'role.col.holderCount',
  /** 그중 오늘 유효한 부여. 게이트가 보지 않는 수이며 운영자가 궁금한 쪽이다. */
  ROLE_COL_ACTIVE_HOLDER: 'role.col.activeHolderCount',
  /** **디테일이 없는 자식**의 건수 — 편집은 `ROLE_AUTH_MGMT` 다(screen/menu/requirements.md). */
  ROLE_COL_AUTH: 'role.col.authCount',
  /** 하단 — 사용자 이름. */
  ROLE_COL_USER_NM: 'role.col.userNm',
  /** 하단 — 사용자 ID. */
  ROLE_COL_USER_ID: 'role.col.userId',
  /** 하단 — 계정 상태. `USE_YN` 과 계정 유효기간만 본다(sys/user-role.md). */
  ROLE_COL_ACCOUNT: 'role.col.accountUsable',
  /** 하단 — 부여 상태(예정·유효·만료). 기간에서 파생한다(sys/menu.md). */
  ROLE_COL_GRANT_STATE: 'role.col.grantState',
  /** 하단 — 부여 기간. 양끝 포함이며 `null` 은 제한 없음이다(sys/INDEX.md). */
  ROLE_COL_PERIOD: 'role.col.period',
  /** 하단 — **공집합 판정의 입력 ①**(screen/menu/requirements.md). 개인 예외가 있으면 차단되지 않는다. */
  ROLE_COL_PERSONAL: 'role.col.hasPersonalGrant',
  /** 하단 — **공집합 판정의 입력 ②**. 확인 문구의 *"다른 역할 없음"* 이 이 값이다. */
  ROLE_COL_OTHER_ROLE: 'role.col.hasOtherRole',
  /** 계정이 쓸 수 있는 상태. */
  ROLE_ACCOUNT_USABLE: 'role.account.usable',
  /** 계정이 막힌 상태 — 관리자가 정한 뜻이다. */
  ROLE_ACCOUNT_BLOCKED: 'role.account.blocked',
  /** 부여 상태 — 예정. */
  ROLE_GRANT_SCHEDULED: 'role.grant.SCHEDULED',
  /** 부여 상태 — 유효. */
  ROLE_GRANT_ACTIVE: 'role.grant.ACTIVE',
  /** 부여 상태 — 만료. **감추지 않는다** — 다시 유효해질 수 있다(screen/menu/requirements.md). */
  ROLE_GRANT_EXPIRED: 'role.grant.EXPIRED',
  /** 기간 양끝이 비었을 때 — 역할 부여는 `null` 이 무기한이다(sys/menu.md). */
  ROLE_PERIOD_UNLIMITED: 'role.period.unlimited',
  /** 있음/없음 열의 *있음*. */
  ROLE_HAS_YES: 'role.has.yes',
  /** 있음/없음 열의 *없음*. */
  ROLE_HAS_NO: 'role.has.no',
  /** 조회조건 — 역할명. */
  ROLE_SEARCH_ROLE_NM: 'role.search.roleNm',
  /** 조회조건 — 사용 여부. **기본값이 *전체* 다**(screen/menu/requirements.md). */
  ROLE_SEARCH_USE_YN: 'role.search.useYn',
  /** 조회조건의 후보 — 사용. 폼에서는 스위치가 아니라 콤보다(*전체* 를 낼 자리가 필요하다). */
  ROLE_USE_ON: 'role.use.on',
  /** 조회조건의 후보 — 미사용. */
  ROLE_USE_OFF: 'role.use.off',
  /** 같은 요청 안에 같은 역할 ID 가 둘 — 클라이언트가 판정한다(frontend/grid.md 의 3층). */
  ROLE_DUPLICATE: 'role.duplicate',
  /** 삭제 게이트가 막았을 때의 사유 — `{users}` · `{auths}`. **대안까지 말한다**(screen/menu/requirements.md). */
  ROLE_DELETE_BLOCKED: 'role.delete.blocked',
  /** 하단의 빈 상태 — 역할을 아직 고르지 않았다(frontend/grid.md). */
  ROLE_DETAIL_EMPTY: 'role.detail.empty',
  /** 하단의 잠긴 상태 — 미저장 신규 행이라 FK 로 자식이 있을 수 없다(frontend/grid.md). */
  ROLE_DETAIL_LOCKED: 'role.detail.locked',
  /** 저장 확인 둘째 줄 — `{name}` 을 끄면 `{users}` 명에게서 권한 `{auths}` 건이 빠진다. */
  ROLE_DISABLE_IMPACT: 'role.disable.impact',
  /** 저장 확인 셋째 줄 — `{count}` 명이 공집합이 되어 다음 로그인부터 차단된다(backend/security.md). */
  ROLE_DISABLE_BLOCKED: 'role.disable.blocked',
  /**
   * 보유자가 한 장에 다 담기지 않았다 — `{counted}` / `{total}`.
   *
   * <p>screen/menu/requirements.md 가 *영향 인원의 규모* 를 미결로 남겼다. 그 답이 나오기 전까지 화면이 할 수
   * 있는 정직한 일은 **못 본 사람이 있다고 말하는 것**이다 — 세어 보니 0명인 것과
   * 1000명까지만 세어 0명인 것은 다른 사실이다.
   */
  ROLE_BLOCKED_PARTIAL: 'role.blocked.partial',

  /** 좌 — 역할 목록의 제목. **읽기 전용**이며 생성은 `ROLE_MGMT` 다(screen/menu/requirements.md). */
  ROLE_AUTH_ROLE_TITLE: 'roleAuth.role.title',
  /** 우 — 권한 트리의 제목. */
  ROLE_AUTH_TREE_TITLE: 'roleAuth.tree.title',
  /** 우 — 역할을 고르기 전의 빈 상태. */
  ROLE_AUTH_TREE_EMPTY: 'roleAuth.tree.empty',
  /** 트리의 계층 컬럼 헤더 — 맨 왼쪽 고정이다(screen/admin/system/user/requirements.md). */
  ROLE_AUTH_COL_MENU: 'roleAuth.col.menu',
  /** `—` 셀의 툴팁 — *안 줬다* 가 아니라 **줄 것이 없다**(screen/admin/system/user/requirements.md). */
  ROLE_AUTH_UNDEFINED: 'roleAuth.undefined',
  /** 폴더 파생 3-state — 전체. **저장되지 않는다**(sys/menu.md). */
  ROLE_AUTH_TRI_ALL: 'roleAuth.tri.all',
  /** 폴더 파생 3-state — 부분. */
  ROLE_AUTH_TRI_SOME: 'roleAuth.tri.some',
  /** 폴더 파생 3-state — 없음. */
  ROLE_AUTH_TRI_NONE: 'roleAuth.tri.none',
  /** 다른 액션을 켜서 조회가 함께 켜졌다 — **조용히 하지 않는다**(screen/admin/system/user/requirements.md). */
  ROLE_AUTH_READ_ON: 'roleAuth.read.on',
  /** 조회를 꺼서 나머지 `{count}` 건이 함께 꺼졌다 — 〃. */
  ROLE_AUTH_READ_OFF: 'roleAuth.read.off',
  /** 저장 확인 첫째 줄 — `{added}` 추가 · `{removed}` 삭제. **수정이 없다**(screen/menu/requirements.md). */
  ROLE_AUTH_SAVE_COUNTS: 'roleAuth.save.counts',
  /** 저장 확인 둘째 줄 — 이 역할을 가진 `{count}` 명에게 적용된다(backend/security.md). */
  ROLE_AUTH_SAVE_HOLDERS: 'roleAuth.save.holders',
  /** 저장 확인 셋째 줄 — 그중 `{count}` 명이 공집합이 되어 차단된다. */
  ROLE_AUTH_SAVE_BLOCKED: 'roleAuth.save.blocked',
  /** 하단 — 영향 인원의 제목. `ROLE_MGMT` 에서는 *보유 사용자* 라 부른다(screen/menu/requirements.md). */
  ROLE_AUTH_AFFECTED_TITLE: 'roleAuth.affected.title',
  /** 하단의 빈 상태. */
  ROLE_AUTH_AFFECTED_EMPTY: 'roleAuth.affected.empty',
  /** 트리 검색 — 메뉴명. **거르지 않는다**(screen/menu/requirements.md 과 같은 어휘다). */
  ROLE_AUTH_SEARCH_MENU: 'roleAuth.search.menu',

  // ── 사용자 관리 (screen/admin/system/user/requirements.md) ────────────────────────────────────
  /** 마스터 그리드의 제목. */
  USER_GRID_TITLE: 'user.grid.title',

  // 계정 상태 다섯 — **공통코드가 아니다**(screen/admin/system/user/requirements.md). 서버가 계산하는 파생값이고
  // 다섯에서 늘 일이 없으므로 메시지 키로 둔다. 로그인 이력의 결과 코드와 1:1 이다.
  /** 접근 제어 체인 넷을 다 통과했다. */
  USER_STAT_NORMAL: 'user.stat.NORMAL',
  /** `USE_YN='N'` — **관리자가 껐다**. */
  USER_STAT_DISABLED: 'user.stat.DISABLED',
  /** 오늘이 사용 기간 밖이다. */
  USER_STAT_EXPIRED: 'user.stat.EXPIRED',
  /** `LOCK_YN='Y'` — 연속 실패로 시스템이 걸었다. */
  USER_STAT_LOCKED: 'user.stat.LOCKED',
  /** `DORMANT_YN='Y'` — 배치가 걸었다. */
  USER_STAT_DORMANT: 'user.stat.DORMANT',

  /** 컬럼 — 로그인 ID. PK 이며 신규 행에서만 열린다(sys/user-role.md). */
  USER_COL_USER_ID: 'user.col.userId',
  /** 컬럼 — 이름. */
  USER_COL_USER_NM: 'user.col.userNm',
  /** 컬럼 — 이메일. 임시 비밀번호의 발송처다(AUTH-07). */
  USER_COL_EMAIL: 'user.col.email',
  /** 컬럼 — 계정 상태(파생). */
  USER_COL_STAT: 'user.col.accountStat',
  /**
   * 그 헤더의 한 줄 설명 — **넷을 하나로 접은 값**이라는 사실을 말한다.
   *
   * <p>접은 것을 말하지 않으면 *"잠금을 풀었는데 여전히 못 쓴다"* 의 이유(미사용이 함께
   * 걸려 있었다)를 화면에서 읽을 수 없다(screen/admin/system/user/requirements.md).
   */
  USER_COL_STAT_TIP: 'user.col.accountStat.tip',
  /** 컬럼 — 사용 여부. **관리자가 건다**(screen/admin/system/user/requirements.md). */
  USER_COL_USE_YN: 'user.col.useYn',
  /** 컬럼 — 사용 기간 시작. */
  USER_COL_VALID_FROM: 'user.col.validFromDt',
  /** 컬럼 — 사용 기간 종료. */
  USER_COL_VALID_TO: 'user.col.validToDt',
  /** 컬럼 — 최종 로그인. 휴면 판정의 근거다. */
  USER_COL_LAST_LOGIN: 'user.col.lastLoginDtm',
  /** 컬럼 — 언어. 서버가 알아야 하는 값이다(sys/user-role.md). */
  USER_COL_LOCALE: 'user.col.localeCd',
  /** 컬럼 — 잠금. **읽기 전용**이며 푸는 것은 ①의 버튼이다. */
  USER_COL_LOCK_YN: 'user.col.lockYn',
  /** 컬럼 — 잠금 시각. */
  USER_COL_LOCK_DTM: 'user.col.lockDtm',
  /** 컬럼 — 로그인 실패 횟수. 잠금 직전 진단용이다. */
  USER_COL_FAIL_CNT: 'user.col.loginFailCnt',
  /** 컬럼 — 휴면. **읽기 전용**이다. */
  USER_COL_DORMANT_YN: 'user.col.dormantYn',
  /** 컬럼 — 임시 비밀번호(발급 후 미변경). */
  USER_COL_PWD_INIT: 'user.col.pwdInitYn',
  /** 컬럼 — 비밀번호 변경일. 만료 임박 확인용이다. */
  USER_COL_PWD_CHG: 'user.col.pwdChgDtm',

  /** 언어 후보 — 한국어. 조회조건과 셀 에디터가 같은 목록을 본다. */
  USER_LOCALE_KO: 'user.locale.ko',
  /** 언어 후보 — 영어. */
  USER_LOCALE_EN: 'user.locale.en',

  /** 조회조건 — 로그인 ID. 앞 일치이며 PK 인덱스를 탄다(screen/admin/system/user/requirements.md). */
  USER_SEARCH_USER_ID: 'user.search.userId',
  /** 조회조건 — 이름. 부분 일치라 인덱스를 타지 않는다(screen/admin/system/user/requirements.md 미결). */
  USER_SEARCH_USER_NM: 'user.search.userNm',
  /** 조회조건 — 이메일. 〃 */
  USER_SEARCH_EMAIL: 'user.search.email',
  /** 조회조건 — 계정 상태(다중). **기본값이 없다** — 끈 계정을 찾으러 오는 화면이다(screen/admin/system/user/requirements.md). */
  USER_SEARCH_STAT: 'user.search.accountStat',
  /** 조회조건 — 보유 역할. `EXISTS` 라 벤더 중립이다(screen/admin/system/user/requirements.md). */
  USER_SEARCH_ROLE: 'user.search.roleId',
  /**
   * 조회조건 — 역할 만료 예정 N일.
   *
   * <p>숫자 칸이 아니라 **후보 콤보**다. 파생 조건은 부등호를 받지 못하므로(backend/api.md)
   * *N일 이내* 라는 뜻을 번역 함수가 갖는다 — 그 김에 screen/admin/system/user/requirements.md 의 *기본값 미결* 이
   * **기본값 없음**으로 닫힌다.
   */
  USER_SEARCH_ROLE_EXPIRING: 'user.search.roleExpiringInDays',
  /** 그 후보의 표시 — `{days}` 일 이내. */
  USER_SEARCH_DAYS: 'user.search.days',
  /** 조회조건 — 최종 로그인 기간. */
  USER_SEARCH_LAST_LOGIN: 'user.search.lastLoginDtm',
  /** 조회조건 — 등록일 기간. */
  USER_SEARCH_REG_DTM: 'user.search.regDtm',

  /** ①의 실행 — 잠금 해제(AUTH-04). 액션은 `SAVE` 다(screen/admin/system/user/requirements.md). */
  USER_ACTION_UNLOCK: 'user.action.unlock',
  /** ①의 실행 — 휴면 해제. 메일이 안 되는 환경을 위한 관리자 경로다. */
  USER_ACTION_DORMANT: 'user.action.dormantRelease',
  /** ①의 실행 — 비밀번호 초기화(AUTH-07). **잠금은 풀리지 않는다**. */
  USER_ACTION_RESET_PWD: 'user.action.passwordReset',

  /** 발급 결과 모달의 제목. */
  USER_ISSUED_TITLE: 'user.issued.title',
  /**
   * 그 안내 — **이 창을 닫으면 다시 볼 수 없다**.
   *
   * <p>값이 응답에만 있기 때문이다(screen/admin/system/user/requirements.md). 말하지 않으면 관리자가 창을 닫고 나서
   * 재발급을 누르는데, 그러면 방금 전한 값이 그 자리에서 무효가 된다.
   */
  USER_ISSUED_DESC: 'user.issued.desc',
  /** 발급 값이 곧 만료된다는 안내 — `{hours}` 시간. */
  USER_ISSUED_EXPIRY: 'user.issued.expiry',

  /** 같은 그리드 안에 같은 로그인 ID 가 둘이다 — 화면이 판정할 수 있는 중복이다(frontend/grid.md). */
  USER_DUPLICATE: 'user.duplicate',
  /** 사용 기간의 시작이 종료보다 늦다. */
  USER_PERIOD_REVERSED: 'user.period.reversed',
  /** 자기 계정의 사용 여부 셀을 끄려 했을 때 — **권한이 아니라 대상 제약**이다(screen/admin/system/user/requirements.md). */
  USER_SELF_DISABLE: 'user.selfDisable',
  /** 저장 확인 앞의 경고 — 신규 `{count}` 건에 임시 비밀번호가 발급된다(screen/admin/system/user/requirements.md). */
  USER_WARN_NEW_PASSWORD: 'user.warn.newPassword',
  /** 저장 확인 앞의 경고 — 사용 중지하는 `{count}` 건은 다음 로그인부터 막힌다. */
  USER_WARN_DISABLE: 'user.warn.disable',

  // ── 디테일 탭 다섯 (screen/admin/system/user/requirements.md) ───────────────────────────────
  //
  // **다섯이 다 그리드다**(frontend/grid.md) — 폼이 하나도 없으므로 탭 전환이 곧 그리드 교체이고,
  // 조회 전용 탭도 frontend/grid.md 의 크롬을 그대로 갖는다.
  //
  // **액션 넷의 헤더는 `authz.action.*` 를 그대로 쓴다.** 유효 권한 탭의 네 열이 그것인데,
  // 같은 액션을 화면마다 다른 말로 부르면 그것이 학습 비용이다 — screen/admin/system/user/requirements.md 가 트리 표시를
  // 두 화면이 공유하게 한 것과 같은 규칙이다.

  /** 탭 1 — 보유 역할(USER-03). */
  USER_TAB_ROLE: 'user.tab.role',
  /** 탭 2 — 개인 예외(AUTHZ-07 조회). **조회 전용**이다(screen/admin/system/user/requirements.md). */
  USER_TAB_GRANT: 'user.tab.grant',
  /** 탭 3 — 접속 IP(AUTH-08). */
  USER_TAB_IP: 'user.tab.ip',
  /** 탭 4 — 활성 세션(AUTH-10). */
  USER_TAB_SESSION: 'user.tab.session',
  /** 탭 5 — 유효 권한. sys/menu.md 의 미결을 닫는 진단이다(screen/admin/system/user/requirements.md). */
  USER_TAB_EFFECTIVE: 'user.tab.effective',

  /**
   * 아직 아무도 고르지 않았다 — *"사용자를 선택하세요"*(frontend/grid.md).
   *
   * <p>**비활성되는 것은 버튼이지 탭이 아니다**(screen/admin/system/user/requirements.md). 탭은 열리되 비어 있고 안내가
   * 뜬다 — 탭을 잠그면 *"이 사용자에게 역할 탭이 없나"* 로 읽힌다.
   */
  USER_DETAIL_EMPTY: 'user.detail.empty',
  /**
   * 미저장 신규 행을 골랐다 — *"저장 후 편집할 수 있습니다"*(frontend/grid.md).
   *
   * <p>`SYS_USER_ROLE` 이 `SYS_USER` 를 FK 로 참조하므로(sys/user-role.md) **부모가 저장되기 전에는
   * 자식을 만들 수 없다.** 빈 상태와 **같은 빈 판이지만 다음에 할 일이 다르다**.
   */
  USER_DETAIL_LOCKED: 'user.detail.locked',

  /** 상태 — 예정. 오늘이 시작일 전이다. 세 탭이 함께 쓴다. */
  USER_STATE_SCHEDULED: 'user.state.SCHEDULED',
  /** 상태 — 유효. 기간 안이다(**양끝 포함** — sys/INDEX.md). */
  USER_STATE_ACTIVE: 'user.state.ACTIVE',
  /** 상태 — 만료. 종료일이 지났다. **행은 남는다** — 회수는 삭제가 아니다(sys/menu.md). */
  USER_STATE_EXPIRED: 'user.state.EXPIRED',

  /** 탭 1 의 제목. */
  USER_ROLE_TITLE: 'user.role.title',
  /** 컬럼 — 역할. **PK 의 절반**이라 신규 행에서만 열린다(sys/menu.md). */
  USER_ROLE_COL_ROLE: 'user.role.col.role',
  /** 컬럼 — 역할명. 기본 정렬이 이것이다. */
  USER_ROLE_COL_ROLE_NM: 'user.role.col.roleNm',
  /** 컬럼 — 설명. */
  USER_ROLE_COL_ROLE_DESC: 'user.role.col.roleDesc',
  /** 컬럼 — **역할 자체**의 사용 여부. 부여의 것이 아니다(sys/menu.md 의 AND). */
  USER_ROLE_COL_USE_YN: 'user.role.col.roleUseYn',
  /** 컬럼 — 시작일. `null` 이 **무기한**이다. */
  USER_ROLE_COL_FROM: 'user.role.col.validFromDt',
  /** 컬럼 — 종료일. 〃 **즉시 회수는 어제 날짜**다(sys/menu.md). */
  USER_ROLE_COL_TO: 'user.role.col.validToDt',
  /** 컬럼 — 부여 상태. **기간에서 파생**한다. */
  USER_ROLE_COL_STATE: 'user.role.col.state',
  /** 부여가 하나도 없다 — 이 계정은 개인 예외로만 화면에 닿는다. */
  USER_ROLE_EMPTY: 'user.role.empty',
  /** 역할이 꺼져 있다 — **기간이 유효해도 지금은 아무 권한도 주지 않는다**. 배지의 사유다. */
  USER_ROLE_OFF: 'user.role.off',
  /** 같은 역할을 두 번 줬다 — 화면이 판정할 수 있는 중복이다(frontend/grid.md). */
  USER_ROLE_DUPLICATE: 'user.role.duplicate',
  /** 저장 확인 앞의 경고 — 회수하는 `{count}` 건은 **다음 로그인부터** 반영된다(backend/security.md). */
  USER_ROLE_WARN_REVOKE: 'user.role.warn.revoke',

  /** 탭 2 의 제목. */
  USER_GRANT_TITLE: 'user.grant.title',
  /**
   * 메뉴 컬럼의 헤더 툴팁 — **여기서는 고칠 수 없다**(screen/admin/system/user/requirements.md).
   *
   * <p>사용자 관리 권한이 있다고 예외 권한 부여 권한이 따라오면 안 되며, 권한은
   * `(MENU_CD, ACTION_CD)` 쌍이므로 **메뉴가 갈려야 갈린다**. 회색 그리드만 두면
   * *"왜 안 고쳐지나"* 가 된다.
   */
  USER_GRANT_READONLY: 'user.grant.readonly',
  /** 컬럼 — 메뉴(조상 경로 포함). */
  USER_GRANT_COL_MENU: 'user.grant.col.menu',
  /** 컬럼 — 대상 메뉴가 살아 있는가(게이트 ④ — backend/security.md). */
  USER_GRANT_COL_MENU_OK: 'user.grant.col.menuOk',
  /** 컬럼 — 액션. */
  USER_GRANT_COL_ACTION: 'user.grant.col.action',
  /** 컬럼 — 사유. **이 행이 곧 감사 기록**이다(sys/logs.md). */
  USER_GRANT_COL_REASON: 'user.grant.col.grantRsn',
  /** 개인 예외가 하나도 없다. */
  USER_GRANT_EMPTY: 'user.grant.empty',

  /** 탭 3 의 제목. */
  USER_IP_TITLE: 'user.ip.title',
  /** 컬럼 — 단일 IP 또는 CIDR. **PK 의 절반**이라 고치지 않고 지우고 다시 넣는다. */
  USER_IP_COL_CIDR: 'user.ip.col.ipCidr',
  /** 컬럼 — 사용. **지우지 않고 끄는** 수단이다(sys/user-role.md). */
  USER_IP_COL_USE_YN: 'user.ip.col.useYn',
  /**
   * 컬럼 — 허용 기간의 시작(2026-08-31 · sys/user-role.md).
   *
   * <p>**파견·출장처럼 기한이 있는 접근**이 이 기능의 둘째 용도다. 기간이 없으면 그 대역을
   * 사람이 기억해서 지워야 하고, 잊으면 **끝난 계약의 IP 가 열려 있는 상태**가 된다.
   */
  USER_IP_COL_FROM: 'user.ip.col.validFromDt',
  /** 〃 종료. **양끝 포함**이며 비우면 제한 없음이다(sys/INDEX.md). */
  USER_IP_COL_TO: 'user.ip.col.validToDt',
  /** 두 컬럼의 헤더 툴팁 — **판정 시각이 서버 기준**이라는 사실을 말한다. */
  USER_IP_PERIOD_TIP: 'user.ip.period.tip',
  /**
   * 허용 대역이 하나도 없다 — **제한 없음이라고 말한다**(sys/user-role.md).
   *
   * <p>*"등록된 대역이 없습니다"* 로 끝내면 **지금 이 계정이 어디서든 들어올 수 있다**는
   * 사실을 아무도 말하지 않는다. 빈 화이트리스트를 전면 차단으로 읽는 오해가 정확히
   * 반대편에 있고, 그 오해는 관리자 계정에서 사고가 된다.
   */
  USER_IP_EMPTY: 'user.ip.empty',
  /** IP 도 CIDR 도 아니다 — 서버가 다시 본다(frontend/grid.md). */
  USER_IP_MALFORMED: 'user.ip.malformed',
  /** 같은 대역이 이미 목록에 있다. */
  USER_IP_DUPLICATE: 'user.ip.duplicate',
  /**
   * 첫 대역을 더할 때의 경고 — **그 순간 나머지 전부가 막힌다**.
   *
   * <p>행이 없으면 제한 없음이므로(sys/user-role.md) 첫 행 하나가 **정책을 켜는 스위치**다. 건수는
   * *얼마나* 를 말할 뿐이라 그것을 말하지 못한다(screen/admin/system/code/requirements.md 가 연 자리).
   */
  USER_IP_WARN_FIRST: 'user.ip.warn.first',

  /** 탭 4 의 제목. */
  USER_SESSION_TITLE: 'user.session.title',
  /** 컬럼 — 세션. **앞 8자만** 그린다. */
  USER_SESSION_COL_ID: 'user.session.col.sessionId',
  /** 컬럼 — 현재 세션인가. screen/admin/system/user/requirements.md 의 **자기 자신 금지**를 눈으로 확인시킨다. */
  USER_SESSION_COL_CURRENT: 'user.session.col.current',
  /** 컬럼 — 생성 시각. */
  USER_SESSION_COL_CREATED: 'user.session.col.created',
  /** 컬럼 — 최근 접근. ***끊을까 말까* 를 가르는 값**이다(screen/admin/system/user/requirements.md). */
  USER_SESSION_COL_ACCESS: 'user.session.col.lastAccess',
  /** 컬럼 — 만료 예정 = 최근 접근 + 유휴 한도. **파생**이다. */
  USER_SESSION_COL_EXPIRY: 'user.session.col.expiry',
  /**
   * 컬럼 — 접속 IP(2026-08-31).
   *
   * <p>**세션 스토어에는 없는 값**이며 로그인 이력에서 온다. 담지 않고 조회 시점에 붙이므로
   * 원본은 여전히 `SYS_LO_LOGIN` 하나다(sys/logs.md).
   */
  USER_SESSION_COL_IP: 'user.session.col.clientIp',
  /** 〃 브라우저(`User-Agent`). `VARCHAR(500)` 이라 셀에서 잘리고 툴팁으로 펼친다. */
  USER_SESSION_COL_AGENT: 'user.session.col.userAgent',
  /**
   * 두 컬럼의 헤더 툴팁 — **출처를 말한다**(screen/admin/system/log/access/requirements.md 과 같은 규약).
   *
   * <p>이 컬럼을 어디까지 믿을 수 있나가 값의 옆에 없으면, **빈 칸이 결함으로** 읽히고
   * 채워진 칸은 세션이 들고 있는 사실처럼 읽힌다.
   */
  USER_SESSION_FROM_LOGIN: 'user.session.fromLogin',
  /** 활성 세션이 없다 — 지금 아무 데도 들어와 있지 않다. */
  USER_SESSION_EMPTY: 'user.session.empty',
  /** 실행 버튼 — 강제 종료(AUTH-10). */
  USER_SESSION_KILL: 'user.session.kill',
  /**
   * 확인 문구 — **계정은 바뀌지 않는다**는 것을 말한다(screen/admin/system/user/requirements.md).
   *
   * <p>*"N건을 종료합니다"* 만으로는 **다시 로그인할 수 있다**는 사실이 빠진다 — 막으려면
   * `USE_YN='N'` 이고 그것은 **다른 축**이다(sys/user-role.md). 말하지 않으면 이 버튼이 계정을
   * 잠그는 것으로 읽힌다.
   */
  USER_SESSION_KILL_CONFIRM: 'user.session.kill.confirm',
  /** 결과 — `{count}` 건을 끊었다. **고른 수와 다를 수 있고 다른 것이 사실**이다. */
  USER_SESSION_KILL_DONE: 'user.session.kill.done',
  /** 버튼이 왜 비활성인가 — 자기 계정의 세션은 끊을 수 없다(screen/admin/system/user/requirements.md). */
  USER_SESSION_SELF: 'user.session.self',

  /** 탭 5 의 제목. */
  USER_EFF_TITLE: 'user.eff.title',
  /** 헤더 ⓐ 의 셀렉트 라벨 — 표시 범위(frontend/grid.md 의 `select` 첫 실물). */
  USER_EFF_SCOPE: 'user.eff.scope',
  /** 범위 — 가진 것만. 기본값이다. */
  USER_EFF_SCOPE_HELD: 'user.eff.scope.held',
  /**
   * 범위 — 전체 메뉴. **없는 것도 보여야 한다**(screen/admin/system/user/requirements.md 의 요구 2).
   *
   * <p>*"왜 못 보나"* 는 **권한이 없는 행**에 답이 있다 — 가진 것만 보여주면 이 탭은
   * 진단이 아니라 요약이다.
   */
  USER_EFF_SCOPE_ALL: 'user.eff.scope.all',
  /** 컬럼 — 메뉴(조상 경로 포함). */
  USER_EFF_COL_MENU: 'user.eff.col.menu',
  /** 컬럼 — 출처. 역할명이거나 개인 예외다(backend/security.md 의 두 갈래). */
  USER_EFF_COL_VIA: 'user.eff.col.via',
  /** 컬럼 — 상태. 부여가 없는 행에서는 비어 있다. */
  USER_EFF_COL_STATE: 'user.eff.col.state',
  /** 컬럼 — 사유. 개인 예외 경유일 때만 있다(역할 부여에는 사유가 없다 — sys/menu.md). */
  USER_EFF_COL_REASON: 'user.eff.col.grantRsn',
  /** 출처가 개인 예외다 — 서버가 보내는 `PERSONAL` 을 화면이 이 글자로 바꾼다. */
  USER_EFF_VIA_PERSONAL: 'user.eff.via.personal',
  /** 출처가 없다 — **부여 자체가 없는 행**이다(전체 메뉴 모드에서만 나온다). */
  USER_EFF_VIA_NONE: 'user.eff.via.none',
  /** 조회 결과가 비었다. */
  USER_EFF_EMPTY: 'user.eff.empty',
  /**
   * **유효 권한이 공집합이다** — 배너로 먼저 알린다(screen/admin/system/user/requirements.md).
   *
   * <p>그 계정은 **다음 로그인부터 차단된다**(backend/security.md). 탭을 열어 빈 표를 보고 유추하게
   * 두지 않는다 — 빈 표는 *조회가 안 된 것* 으로도 읽힌다.
   */
  USER_EFF_NONE: 'user.eff.none',
  /** 부여는 살아 있는데 **대상이 죽었다** — 게이트 ④(backend/security.md). 행의 배지 사유다. */
  USER_EFF_GATE: 'user.eff.gate',
  /** 부여가 예정이거나 만료다 — 지금은 효력이 없다. 〃 */
  USER_EFF_INACTIVE: 'user.eff.inactive',

  // ── 개인 예외 권한 (screen/admin/system/user/requirements.md) ─────────────────────────────────
  //
  // **트리의 표시 문구는 `roleAuth.*` 를 그대로 쓴다.** 폴더의 파생 3-state 와 `—` 의
  // 툴팁이 그것인데, screen/admin/system/user/requirements.md 가 *"읽는 것은 마찰의 대상이 아니므로 표시는 양쪽 공통"* 이라고
  // 정했다 — 같은 트리를 두 화면에서 다른 말로 부르면 그것이 학습 비용이다. 키 이름이
  // `roleAuth` 로 시작하는 것은 **먼저 쓴 화면이 이름을 가져간** 결과일 뿐이다.
  /** 좌 pane 의 제목 — 사용자 목록. */
  USER_AUTH_USER_TITLE: 'userAuth.user.title',
  /** 우 pane 의 제목 — 권한 트리. */
  USER_AUTH_TREE_TITLE: 'userAuth.tree.title',
  /** 사용자를 고르기 전의 트리 — *"사용자를 선택하세요"*. */
  USER_AUTH_TREE_EMPTY: 'userAuth.tree.empty',
  /** 하 pane 의 제목 — 예외 목록. */
  USER_AUTH_GRANT_TITLE: 'userAuth.grant.title',
  /** 예외가 하나도 없을 때 — **고장이 아니다**. 예외는 예외다. */
  USER_AUTH_GRANT_EMPTY: 'userAuth.grant.empty',

  /** 조회조건 — 사용자. **ID 와 이름을 함께** 본다(screen/admin/system/user/requirements.md). */
  USER_AUTH_SEARCH_USER: 'userAuth.search.user',
  /** 트리 검색 — 메뉴명. **거르지 않는다**(screen/admin/system/user/requirements.md · screen/menu/requirements.md 과 같은 어휘다). */
  USER_AUTH_SEARCH_MENU: 'userAuth.search.menu',

  /**
   * 좌 pane 컬럼 — **지금 유효한** 예외 건수(screen/admin/system/user/requirements.md).
   *
   * <p>예정·만료를 세지 않는 것이 이름에 들어가야 했다 — *예외 건수* 로 두면 만료가 쌓인
   * 계정에서 **화면의 어느 수와도 맞지 않는다**.
   */
  USER_AUTH_COL_GRANT_CNT: 'userAuth.col.grantCnt',
  /** 그 툴팁 — **무엇을 세지 않는지**를 말한다. 값만 보고는 알 수 없다. */
  USER_AUTH_COL_GRANT_CNT_TIP: 'userAuth.col.grantCnt.tip',

  /** 목록 컬럼 — 메뉴(조상 경로 포함). */
  USER_AUTH_COL_MENU: 'userAuth.col.menu',
  /** 목록 컬럼 — 액션. */
  USER_AUTH_COL_ACTION: 'userAuth.col.action',
  /** 목록 컬럼 — 상태(예정 · 유효 · 만료). **기간에서 파생**한다(screen/admin/system/user/requirements.md). */
  USER_AUTH_COL_STATE: 'userAuth.col.state',
  /** 목록 컬럼 — 시작일. **필수**다. */
  USER_AUTH_COL_FROM: 'userAuth.col.validFromDt',
  /** 목록 컬럼 — 종료일. 〃 **무기한이라는 선택지가 없다**(sys/menu.md). */
  USER_AUTH_COL_TO: 'userAuth.col.validToDt',
  /** 목록 컬럼 — 사유. 〃 이 행이 곧 감사 기록이다(sys/logs.md). */
  USER_AUTH_COL_REASON: 'userAuth.col.grantRsn',

  /** 상태 — 예정. 오늘이 시작일 전이다. */
  USER_AUTH_STATE_SCHEDULED: 'userAuth.state.SCHEDULED',
  /** 상태 — 유효. 스위치가 켜지는 것은 이것 하나뿐이다. */
  USER_AUTH_STATE_ACTIVE: 'userAuth.state.ACTIVE',
  /** 상태 — 만료. 종료일이 지났다. */
  USER_AUTH_STATE_EXPIRED: 'userAuth.state.EXPIRED',

  /** 역할 경유 배지의 툴팁 — `{roles}` 가 역할명 목록이다(screen/admin/system/user/requirements.md). */
  USER_AUTH_VIA_ROLE: 'userAuth.viaRole',
  /** 역할 경유 배지의 글자 — 좁은 칸이라 짧다. 이름은 툴팁이 말한다. */
  USER_AUTH_VIA_ROLE_TAG: 'userAuth.viaRole.tag',
  /** 부여는 있는데 그 액션이 정의에서 사라졌다 — **고아**다(seed.md). */
  USER_AUTH_ORPHAN: 'userAuth.orphan',

  /** 다른 액션을 켜서 조회가 함께 켜졌다 — **조용히 하지 않는다**(screen/admin/system/user/requirements.md). */
  USER_AUTH_READ_ON: 'userAuth.read.on',
  /** 조회를 회수해서 나머지 `{count}` 건도 함께 회수됐다 — 〃. */
  USER_AUTH_READ_OFF: 'userAuth.read.off',
  /** 역할이 조회를 주고 있어 조회를 따로 켜지 않았다 — 합집합이라 그것으로 충분하다. */
  USER_AUTH_READ_VIA_ROLE: 'userAuth.read.viaRole',

  /** 만료된 부여를 다시 켤 때의 확인 제목(screen/admin/system/user/requirements.md). */
  USER_AUTH_REVIVE_TITLE: 'userAuth.revive.title',
  /** 그 본문 — `{from}` · `{to}` · `{reason}` 이 **대체될 이전 값**이다. */
  USER_AUTH_REVIVE_BODY: 'userAuth.revive.body',
  /** 예정 부여를 켜려 했을 때 — **값을 건드리지 않는다**. 고치려면 목록에서 고친다. */
  USER_AUTH_SCHEDULED_KEEP: 'userAuth.scheduled.keep',
  /** 한 번에 `{count}` 건을 골랐다 — *역할 신설을 검토하세요*(screen/admin/system/user/requirements.md). **막지는 않는다**. */
  USER_AUTH_THRESHOLD: 'userAuth.threshold',

  /**
   * 시작일이 종료일보다 늦다.
   *
   * <p>**비어 있는 것은 여기서 말하지 않는다** — 컬럼의 `required` 가 그리드 공용 문구
   * (`grid.validation.required`)를 낸다(frontend/grid.md). 같은 말을 화면마다 다시 적으면 그것이
   * 곧 번역해야 할 문장이 하나 더 느는 일이다.
   */
  USER_AUTH_PERIOD_REVERSED: 'userAuth.period.reversed',

  // ── duration 렌더러 (frontend/grid.md · screen/admin/system/batch/schedule/requirements.md) ─────────────────────
  //
  // **형식 자체가 메시지다.** screen/admin/system/batch/schedule/requirements.md 이 *"`1시간 2분 5초` 인지 `01:02:05` 인지, 다국어에서
  // 갈린다"* 를 미결로 남겼는데, 갈리는 것이 맞으므로 소스가 조립하지 않는다 — 조립하면
  // 언어가 늘 때마다 소스를 고쳐야 하고 그것이 90 이 막으려는 것이다.
  //
  // 0 인 단위는 **템플릿을 갈아** 없앤다(hms → ms → s). `{h}=0` 을 그대로 그리면
  // *"0시간 3분"* 이 된다.

  /** `1시간 2분 5초` — `{h}` `{m}` `{s}`. 한 시간을 넘긴 소요. */
  GRID_DURATION_HMS: 'grid.duration.hms',
  /** `3분 20초` — `{m}` `{s}`. */
  GRID_DURATION_MS: 'grid.duration.ms',
  /** `12초` — `{s}`. */
  GRID_DURATION_S: 'grid.duration.s',
  /**
   * `1초 미만` — <b>`0초` 라고 쓰지 않는다</b>.
   *
   * <p>`SKIP_*` 이력이 실제로 0ms 이므로(sys/batch.md) 둘이 같은 글자가 되면 <i>안 걸린 것</i>과
   * <i>빨리 끝난 것</i>이 구분되지 않는다.
   */
  GRID_DURATION_SUB: 'grid.duration.sub',

  // ── 배치 결과·실행 구분의 라벨 (screen/admin/system/batch/schedule/requirements.md) ────────────────────
  //
  // **공통코드가 아니다.** 값은 소스가 갖고(`BatchResultCode` · `BatchExecType`) 라벨만
  // 여기 있다 — 관리자가 늘릴 일이 없고, 공통코드로 두면 `SYS_YN='Y'` 로 잠가야 하는데
  // 잠가 두면 관리자에게 남는 것이 없다(sys/code.md).
  //
  // 색은 여기 없다 — 값이 일곱이고 색이 넷이라 `tone` 맵이 화면의 것이다(frontend/grid.md).

  /** 진행 중 — 배지 `none`. 오래 남으면 유령이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_RSLT_RUNNING: 'batch.rslt.RUNNING',
  /** 정상 종료 — 배지 `ok`. */
  BATCH_RSLT_SUCCESS: 'batch.rslt.SUCCESS',
  /** 예외 종료 — 배지 `err`. */
  BATCH_RSLT_FAIL: 'batch.rslt.FAIL',
  /** 락 선점 실패 — <b>다중화에서는 정상</b>이라 배지가 `muted` 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_RSLT_SKIP_LOCK: 'batch.rslt.SKIP_LOCK',
  /** 쿨다운에 막힘 — <b>나면 안 되는 일</b>이라 `warn` 이다(backend/persistence.md). */
  BATCH_RSLT_SKIP_COOLDOWN: 'batch.rslt.SKIP_COOLDOWN',
  /** 사람이 중단시켰고 배치가 그것을 읽었다 — 배지 `muted`. */
  BATCH_RSLT_CANCELED: 'batch.rslt.CANCELED',
  /**
   * 실행하던 노드가 사라져 <b>결과를 모른다</b> — 배지 `err` (screen/admin/system/batch/schedule/requirements.md).
   *
   * <p>`FAIL` 과 가르는 이유는 <b>스택이 없다</b>는 것이다. 죽은 것은 배치가 아니라
   * 프로세스이고, 같은 색을 쓰되 <b>다른 글자</b>여야 스택을 찾다 못 찾은 사람이
   * *로그가 유실됐다* 로 잘못 결론짓지 않는다.
   */
  BATCH_RSLT_LOST: 'batch.rslt.LOST',
  /** 크론이 깨웠다. */
  BATCH_EXEC_SCHEDULE: 'batch.execType.SCHEDULE',
  /** 사람이 즉시 실행을 눌렀다 — 쿨다운을 우회한 실행이다(backend/persistence.md). */
  BATCH_EXEC_MANUAL: 'batch.execType.MANUAL',

  // ── 배치 스케줄 관리 (screen/admin/system/batch/schedule/requirements.md) ────────────────────────────────

  /** 마스터 그리드의 제목. */
  BATCH_SCHEDULE_GRID_TITLE: 'batchSchedule.grid.title',
  /** 순서 컬럼 — <b>사람이 정한다</b>. 드래그가 아니라 숫자다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_SORT_NO: 'batchSchedule.col.sortNo',
  /** 배치 ID 컬럼 — PK 이자 <b>락 키</b>다(backend/persistence.md). */
  BATCH_SCHEDULE_COL_BATCH_ID: 'batchSchedule.col.batchId',
  /** 배치명 컬럼 — 관리자 전용 명칭이라 다국어가 아니다(sys/batch.md). */
  BATCH_SCHEDULE_COL_BATCH_NM: 'batchSchedule.col.batchNm',
  /** 설명 컬럼 — <b>끄면 무엇이 멈추는가</b>를 적는 자리다(sys/batch.md). 필수다. */
  BATCH_SCHEDULE_COL_BATCH_DESC: 'batchSchedule.col.batchDesc',
  /** 실행 빈 컬럼 — 타이핑이 아니라 <b>고르는 것</b>이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_BEAN_ID: 'batchSchedule.col.beanId',
  /** 크론 컬럼 — 저장이 쿨다운과의 관계를 검증한다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_CRON: 'batchSchedule.col.cron',
  /**
   * 시간대 컬럼 — 크론을 <b>어느 시간대로 읽나</b>(screen/admin/system/batch/schedule/requirements.md §4-1).
   *
   * <p>식은 변환되지 않고 그대로 저장된다. 신규 행은 서버의 업무 시간대로 채워진다.
   */
  BATCH_SCHEDULE_COL_TIMEZONE: 'batchSchedule.col.timezone',
  /** 다음 실행 컬럼 — <b>서버가 계산한다</b>. 크론을 눈으로 확인시킨다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_NEXT_EXEC: 'batchSchedule.col.nextExec',
  /** 파라미터 컬럼 — `nclob` 이 아니므로 `[보기]` 가 아니라 `textModal` 이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_PARAM: 'batchSchedule.col.paramJson',
  /** 락 만료 컬럼 — 장애 안전망이며 유령 판정의 임계값이기도 하다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_LOCK_EXPIRY: 'batchSchedule.col.lockExpiry',
  /** 쿨다운 컬럼 — 크론과 비교된다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_COOLDOWN: 'batchSchedule.col.cooldown',
  /** 사용 컬럼 — 스위치지만 <b>즉시 저장이 아니다</b>(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_USE_YN: 'batchSchedule.col.useYn',
  /** 최근 결과 컬럼 — 하단 pane 첫 행과 같은 값이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_COL_LAST_RSLT: 'batchSchedule.col.lastRslt',

  /** 조회조건 — 배치명. */
  BATCH_SCHEDULE_SEARCH_NM: 'batchSchedule.search.batchNm',
  /** 조회조건 — 실행 빈. */
  BATCH_SCHEDULE_SEARCH_BEAN: 'batchSchedule.search.beanId',
  /** 조회조건 — 사용 여부. <b>기본이 *전체*</b> 다: 꺼 둔 것을 감추면 또 만든다. */
  BATCH_SCHEDULE_SEARCH_USE_YN: 'batchSchedule.search.useYn',

  /**
   * 조회조건 <b>콤보의 후보</b> — 그리드의 스위치와 다른 자리다(frontend/search-form.md).
   *
   * <p>폼의 타입이 셋뿐이라 값이 둘인 칸도 콤보로 내며, 스위치로 두면 <i>끄기 = N</i> 이
   * 되어 <b>*전체* 를 표현할 자리가 없다</b>.
   *
   * <p><b>`role.use.on` 을 돌려쓰지 않는다.</b> 역할 관리의 문구를 고치면 배치 화면이
   * 함께 바뀌는 결합이 생긴다 — `MsgKey` 가 `err.user` 와 `err.user-admin` 을 가른 것과
   * 같은 종류의 사고다.
   */
  BATCH_SCHEDULE_USE_ON: 'batchSchedule.use.on',
  /** 〃 — 미사용. */
  BATCH_SCHEDULE_USE_OFF: 'batchSchedule.use.off',

  /** 0건 — 등록 자체가 없다. */
  BATCH_SCHEDULE_EMPTY: 'batchSchedule.empty',
  /** 파라미터 셀의 컬럼 검증 — 프론트가 `JSON.parse` 로 즉시 판정한다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_PARAM_INVALID: 'batchSchedule.paramInvalid',
  /** 같은 그리드 안에 같은 배치 ID 가 둘이다 — 화면이 판정할 수 있는 중복이다(frontend/grid.md). */
  BATCH_SCHEDULE_DUPLICATE: 'batchSchedule.duplicate',

  /** 저장 확인의 첫 줄 — 끄는 배치가 `{count}` 건 있다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_WARN_DISABLE: 'batchSchedule.warn.disable',
  /**
   * 그 목록의 한 줄 — `{name}` `{batchId}`. <b>다음 줄에 설명이 그대로 붙는다.</b>
   *
   * <p>설명이 데이터라 메시지가 아니다 — sys/batch.md 의 셋째 항목(*"껐다 켜면 따라잡는가"*)을
   * <b>끄는 순간에</b> 읽히게 하는 것이 이 확인의 목적 전부다.
   */
  BATCH_SCHEDULE_WARN_DISABLE_ITEM: 'batchSchedule.warn.disableItem',

  /**
   * 저장 확인 — 시간대를 바꾼 배치가 `{count}` 건 있다(screen/admin/system/batch/schedule/requirements.md §5-1).
   *
   * <p><b>크론이 그대로라 경고가 필요하다.</b> 화면상 달라진 것은 한 칸인데 실제 실행
   * 시각은 몇 시간 옮겨진다 — 보이는 변화의 크기와 결과의 크기가 어긋나는 자리다.
   */
  BATCH_SCHEDULE_WARN_TIMEZONE: 'batchSchedule.warn.timezone',
  /** 그 목록의 한 줄 — `{name}` `{batchId}` `{from}` `{to}`. <b>바뀐 값 둘을 나란히</b> 놓는다. */
  BATCH_SCHEDULE_WARN_TIMEZONE_ITEM: 'batchSchedule.warn.timezoneItem',

  /** 삭제 게이트 — 켜져 있다. <b>대안까지 말한다</b>(먼저 끄세요). */
  BATCH_SCHEDULE_DELETE_ENABLED: 'batchSchedule.delete.enabled',
  /** 삭제 게이트 — 실행 중이다. 락이 잡힌 채 등록이 사라지면 안 된다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_DELETE_RUNNING: 'batchSchedule.delete.running',

  /** ①의 실행 — 즉시 실행(BATCH-02). 액션은 `EXECUTE` 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_ACTION_RUN: 'batchSchedule.action.runNow',
  /** 실행 앞의 검증 — 미저장 변경이 있으면 막는다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_RUN_UNSAVED: 'batchSchedule.run.unsaved',
  /** 실행 결과 모달의 제목 — 성공/실패를 건별로 낸다(frontend/grid.md). */
  BATCH_SCHEDULE_RUN_TITLE: 'batchSchedule.run.title',
  /** 그중 시작한 것 — `{count}`. 진행은 하단 pane 의 `RUNNING` 행이 보여준다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_RUN_STARTED: 'batchSchedule.run.started',
  /**
   * 거절 — `{batchId}` `{node}` `{time}`.
   *
   * <p><b>노드와 시각이 요점이다</b>(screen/admin/system/batch/schedule/requirements.md). *"실행 중입니다"* 만 내면 운영자는 얼마나
   * 기다려야 하는지 모른다 — backend/persistence.md 이 `NODE_ID` 를 남긴 것과 같은 이유다.
   */
  BATCH_SCHEDULE_RUN_REJECTED_NODE: 'batchSchedule.run.rejectedNode',
  /** 거절인데 노드를 모른다 — 락은 잡혔는데 `RUNNING` 이력이 아직 없는 짧은 창이다. */
  BATCH_SCHEDULE_RUN_REJECTED: 'batchSchedule.run.rejected',

  /** 하단 pane 의 제목 — 탭이 아니라 pane 하나다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_SCHEDULE_RECENT_TITLE: 'batchSchedule.recent.title',
  /** 하단 빈 상태 — 아직 아무 배치도 고르지 않았다(frontend/grid.md). */
  BATCH_SCHEDULE_RECENT_EMPTY: 'batchSchedule.recent.empty',
  /** 하단 빈 상태 — 미저장 신규 행이라 조회할 것이 없다(frontend/grid.md). */
  BATCH_SCHEDULE_RECENT_LOCKED: 'batchSchedule.recent.locked',
  /**
   * 하단 빈 상태 — <b>고른 배치가 한 번도 안 돌았다</b>.
   *
   * <p>위 둘과 다른 사실이다. screen/admin/system/batch/schedule/requirements.md 이 *"행이 없으면 꺼져 있는 것"* 이라고 한 그 신호이며,
   * *"선택하세요"* 로 뭉치면 <b>조사하러 온 사람이 아무것도 못 읽는다</b>.
   */
  BATCH_SCHEDULE_RECENT_NONE: 'batchSchedule.recent.none',

  // ── 배치 실행 이력 (screen/admin/system/batch/schedule/requirements.md) ──────────────────────────────────

  /** 마스터 그리드의 제목. */
  BATCH_HISTORY_GRID_TITLE: 'batchHistory.grid.title',
  /** 시작 컬럼 — <b>기본 정렬</b>(내림)이다. */
  BATCH_HISTORY_COL_START: 'batchHistory.col.startDtm',
  /** 배치 ID 컬럼 — 더블 클릭하면 `BATCH_SCHEDULE` 이 그 배치로 열린다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_BATCH_ID: 'batchHistory.col.batchId',
  /** 배치명 컬럼 — 조인이며 없으면 `(삭제된 배치)` 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_BATCH_NM: 'batchHistory.col.batchNm',
  /** 실행 구분 컬럼 — 스케줄 · 수동. 중복 진단의 첫 단계다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_EXEC_TYPE: 'batchHistory.col.execType',
  /** 결과 컬럼 — 배지. 색은 `tone` 맵이 정한다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_RSLT: 'batchHistory.col.rslt',
  /** 소요 컬럼 — <b>정렬이 이 화면의 핵심</b>이다(screen/admin/system/batch/schedule/requirements.md). 값은 ms 원본이다. */
  BATCH_HISTORY_COL_ELAPSED: 'batchHistory.col.elapsed',
  /** 요약 컬럼 — `VARCHAR(1000)` 이라 <b>셀에 싣는다</b>(sys/batch.md). */
  BATCH_HISTORY_COL_RSLT_MSG: 'batchHistory.col.rsltMsg',
  /** 상세 컬럼 — 파생이며 셀에 그려지는 것은 `[보기]` 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_DETAIL: 'batchHistory.col.detail',
  /** 종료 컬럼 — <b>NULL 이면 진행 중</b>이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_END: 'batchHistory.col.endDtm',
  /** 노드 컬럼 — 기본 숨김이며 <b>중복이 의심될 때 켠다</b>(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_COL_NODE: 'batchHistory.col.nodeId',
  /** 중단 요청 컬럼 — 결과가 아니라 <b>요청</b>이다(sys/batch.md). */
  BATCH_HISTORY_COL_CANCEL_REQ: 'batchHistory.col.cancelReq',
  /** 순번 컬럼 — PK 이자 tie-breaker. */
  BATCH_HISTORY_COL_HIST_ID: 'batchHistory.col.histId',

  /** 상세 셀에 그려지는 글자 — 값이 아니다(screen/admin/system/batch/schedule/requirements.md). 엑셀은 원본 값을 쓴다(frontend/grid.md). */
  BATCH_HISTORY_DETAIL_LINK: 'batchHistory.detail.link',
  /** 배치명이 비었을 때 — <b>이름을 지어내지 않는다</b>(screen/admin/system/log/access/requirements.md 와 같은 판단). */
  BATCH_HISTORY_DELETED_BATCH: 'batchHistory.deletedBatch',
  /** `RUNNING` 인데 락 만료를 넘긴 행의 배지 — 죽었다는 뜻이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_GHOST: 'batchHistory.ghost',
  /** 그 배지의 툴팁 — 왜 유령이라고 부르는지 말한다. */
  BATCH_HISTORY_GHOST_TIP: 'batchHistory.ghostTip',
  /** 중단 버튼이 비활성인 이유 — `{time}` 에 이미 요청됐다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_CANCELING: 'batchHistory.canceling',

  /** 조회조건 — 시작 일시. <b>필수가 아니다</b>. 기본값만 둔다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_SEARCH_START: 'batchHistory.search.startDtm',
  /** 조회조건 — 배치. */
  BATCH_HISTORY_SEARCH_BATCH: 'batchHistory.search.batchId',
  /** 조회조건 — 결과(다중). */
  BATCH_HISTORY_SEARCH_RSLT: 'batchHistory.search.rsltCd',
  /** 조회조건 — 결과 묶음. 파생 조건의 <b>네 번째 사례</b>다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_SEARCH_GROUP: 'batchHistory.search.rsltGroup',
  /** 조회조건 — 실행 구분. */
  BATCH_HISTORY_SEARCH_EXEC_TYPE: 'batchHistory.search.execType',
  /** 조회조건 — 노드. 특정 노드만 보는 것이 진단의 한 단계다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_SEARCH_NODE: 'batchHistory.search.nodeId',
  /** 조회조건 — 소요 시간 이상(초). <b>입력은 초, 전송은 ms</b> 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_SEARCH_ELAPSED: 'batchHistory.search.elapsed',
  /** 결과 묶음의 후보 — 실패만(`FAIL` + `CANCELED`). */
  BATCH_HISTORY_GROUP_FAILED: 'batchHistory.group.failed',
  /** 결과 묶음의 후보 — 건너뜀만(`SKIP_LOCK` + `SKIP_COOLDOWN`). */
  BATCH_HISTORY_GROUP_SKIPPED: 'batchHistory.group.skipped',

  /** 0건 — 조건 없이 물었을 때. */
  BATCH_HISTORY_EMPTY: 'batchHistory.empty',
  /** 0건인데 <b>답이다</b> — *실패만* 으로 물어 0건이면 이 기간에 실패가 없었다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_EMPTY_FAILED: 'batchHistory.emptyFailed',
  /** 0건인데 답이다 — *건너뜀만* 으로 물어 0건이면 락도 쿨다운도 걸리지 않았다. */
  BATCH_HISTORY_EMPTY_SKIPPED: 'batchHistory.emptySkipped',
  /** 푸터 주석 — 보존 1년(retention.md). 없는 것과 파기된 것을 사용자가 가를 수 있게 한다. */
  BATCH_HISTORY_RETENTION: 'batchHistory.retention',

  /** ①의 실행 — 중단(BATCH-05). 액션은 `EXECUTE` 다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_ACTION_CANCEL: 'batchHistory.action.cancel',
  /**
   * 확인 문구 — `{count}`. <b>*"중단합니다"* 가 아니다</b>(screen/admin/system/batch/schedule/requirements.md).
   *
   * <p>협조적 중단이라 <b>요청까지가 화면의 책임</b>이고, 그것을 말하지 않으면 안 멈췄을 때
   * 화면이 거짓말한 것이 된다.
   */
  BATCH_HISTORY_CANCEL_CONFIRM: 'batchHistory.cancel.confirm',
  /** 결과 — `{count}` 건에 요청했다. 멈추는 시점은 배치의 체크포인트다. */
  BATCH_HISTORY_CANCEL_DONE: 'batchHistory.cancel.done',
  /** 결과 — `{count}` 건은 이미 요청돼 있었다. <b>오류가 아니다</b>(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_CANCEL_ALREADY: 'batchHistory.cancel.already',

  /** 상세 모달의 제목 — <b>탭 둘</b>이다(screen/admin/system/batch/schedule/requirements.md). */
  BATCH_HISTORY_DETAIL_TITLE: 'batchHistory.detail.title',
  /** 탭 — 결과 상세(`RSLT_DTL_MSG`). 처리한 대상 목록이다(sys/batch.md). */
  BATCH_HISTORY_DETAIL_TAB_RESULT: 'batchHistory.detail.tabResult',
  /** 탭 — 오류(`ERR_MSG`). 예외 스택이다. */
  BATCH_HISTORY_DETAIL_TAB_ERROR: 'batchHistory.detail.tabError',
  /**
   * 결과 상세 탭이 비활성인 이유 — <b>탭 이름의 툴팁</b>이다(screen/admin/system/log/access/requirements.md).
   *
   * <p>탭 안에 두면 비활성 탭이 열리지 않아 볼 방법이 없다. 그리고 <b>비어 있는 것과
   * 남기지 않은 것이 다르다</b> — 대개의 배치는 대상 목록을 남기지 않는다.
   */
  BATCH_HISTORY_DETAIL_NO_RESULT: 'batchHistory.detail.noResult',
  /** 오류 탭이 비활성인 이유 — 성공한 실행이다. 위와 <b>사유가 다르다</b>. */
  BATCH_HISTORY_DETAIL_NO_ERROR: 'batchHistory.detail.noError',

  // ── 공지사항 관리 (screen/admin/system/notice/requirements.md) ──────────────────────────────────────

  /** 마스터 그리드의 제목. */
  NOTICE_GRID_TITLE: 'notice.grid.title',
  /** 순서 — <b>상단 고정용</b>이며 드래그가 아니라 숫자다(screen/admin/system/notice/requirements.md). */
  NOTICE_COL_SORT_NO: 'notice.col.sortNo',
  NOTICE_COL_NOTICE_ID: 'notice.col.noticeId',
  NOTICE_COL_TITLE: 'notice.col.title',
  /** 본문 — `nclob` 이지만 목록에 싣는다. 공지는 수백 건이다(screen/admin/system/notice/requirements.md). */
  NOTICE_COL_CONTENT: 'notice.col.content',
  NOTICE_COL_FROM_DTM: 'notice.col.fromDtm',
  /** 게시 종료 — <b>비우면 무기한</b>이다(sys/notification.md). */
  NOTICE_COL_TO_DTM: 'notice.col.toDtm',
  /**
   * 게시 상태 — <b>파생이며 서버가 판정한다</b>(screen/admin/system/notice/requirements.md).
   *
   * <p>클라이언트가 계산하면 PC 시계가 틀린 사람에게 다른 배지가 뜨고, 기준 시각은
   * 브라우저가 아니라 업무 타임존이다(sys/INDEX.md).
   */
  NOTICE_COL_STAT: 'notice.col.stat',
  /** 사용 — <b>즉시 내림</b>이며 기간과 축이 다르다(sys/notification.md). */
  NOTICE_COL_USE_YN: 'notice.col.useYn',
  NOTICE_COL_POPUP_YN: 'notice.col.popupYn',
  NOTICE_COL_POPUP_FROM: 'notice.col.popupFrom',
  NOTICE_COL_POPUP_TO: 'notice.col.popupTo',

  /** 게시 상태 배지 넷 — 값은 코드이고 글자만 라벨이다(frontend/grid.md). */
  NOTICE_STAT_SCHEDULED: 'notice.stat.SCHEDULED',
  NOTICE_STAT_ACTIVE: 'notice.stat.ACTIVE',
  NOTICE_STAT_EXPIRED: 'notice.stat.EXPIRED',
  NOTICE_STAT_DOWN: 'notice.stat.DOWN',

  NOTICE_SEARCH_TITLE: 'notice.search.title',
  NOTICE_SEARCH_PERIOD: 'notice.search.period',
  NOTICE_SEARCH_STAT: 'notice.search.stat',
  NOTICE_SEARCH_POPUP: 'notice.search.popup',
  NOTICE_POPUP_ON: 'notice.popup.on',
  NOTICE_POPUP_OFF: 'notice.popup.off',

  /** 조회 0건 — 일반 문구다. */
  NOTICE_EMPTY: 'notice.empty',
  /**
   * <b>`게시중` 으로 걸렀는데 0건</b> — 진단으로 읽히게 한다(screen/admin/system/notice/requirements.md · screen/admin/system/log/access/requirements.md).
   *
   * <p>지금 사용자에게 아무 공지도 보이지 않는다는 뜻이고, 그것을 알고 싶어서 거른 것이다.
   */
  NOTICE_EMPTY_ACTIVE: 'notice.empty.active',
  /** 삭제 확인 — <b>되돌릴 수 없다</b>고 말하고 대안(끄기)을 함께 준다(screen/admin/system/notice/requirements.md). */
  NOTICE_DELETE_CONFIRM: 'notice.delete.confirm',

  // ── 요청함 처리 (screen/admin/system/notice/requirements.md) ────────────────────────────────────────

  NOTI_REQUEST_GRID_TITLE: 'notiRequest.grid.title',
  NOTI_REQUEST_COL_REQ_ID: 'notiRequest.col.reqId',
  /** 요청일시 — <b>오름차순</b>이 기본이다. 대기열은 오래 기다린 것이 먼저다(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_COL_CRE_DTM: 'notiRequest.col.creDtm',
  NOTI_REQUEST_COL_USER: 'notiRequest.col.user',
  NOTI_REQUEST_COL_TYPE: 'notiRequest.col.type',
  NOTI_REQUEST_COL_CONTENT: 'notiRequest.col.content',
  NOTI_REQUEST_COL_STAT: 'notiRequest.col.stat',
  NOTI_REQUEST_COL_HANDLER: 'notiRequest.col.handler',
  NOTI_REQUEST_COL_HANDLE_DTM: 'notiRequest.col.handleDtm',
  NOTI_REQUEST_COL_MEMO: 'notiRequest.col.memo',

  /** 유형 셋 · 상태 셋 — 공통코드가 아니라 소스의 값이다(sys/notification.md). */
  NOTI_REQUEST_TYPE_ROLE_GRANT: 'notiReq.type.ROLE_GRANT',
  NOTI_REQUEST_TYPE_UNLOCK: 'notiReq.type.UNLOCK',
  NOTI_REQUEST_TYPE_ETC: 'notiReq.type.ETC',
  NOTI_REQUEST_STAT_RECEIVED: 'notiReq.stat.RECEIVED',
  NOTI_REQUEST_STAT_DONE: 'notiReq.stat.DONE',
  NOTI_REQUEST_STAT_REJECTED: 'notiReq.stat.REJECTED',

  NOTI_REQUEST_SEARCH_PERIOD: 'notiRequest.search.period',
  NOTI_REQUEST_SEARCH_STAT: 'notiRequest.search.stat',
  NOTI_REQUEST_SEARCH_TYPE: 'notiRequest.search.type',
  NOTI_REQUEST_SEARCH_USER: 'notiRequest.search.user',

  /** 처리 · 반려 버튼 — <b>저장이 아니라 판정</b>이다(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_ACTION_DONE: 'notiRequest.action.done',
  NOTI_REQUEST_ACTION_REJECT: 'notiRequest.action.reject',
  /** 모달 제목 둘. */
  NOTI_REQUEST_MODAL_DONE: 'notiRequest.modal.done',
  NOTI_REQUEST_MODAL_REJECT: 'notiRequest.modal.reject',
  /** 처리는 메모가 선택이다 — 결과 자체가 답이다(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_MEMO_OPTIONAL: 'notiRequest.memo.optional',
  /** 반려는 사유가 필수다 — 이유가 없으면 답이 아니다(sys/notification.md). */
  NOTI_REQUEST_MEMO_REQUIRED: 'notiRequest.memo.required',
  NOTI_REQUEST_MEMO_PLACEHOLDER: 'notiRequest.memo.placeholder',
  /** <b>사유가 요청자에게 간다</b>는 사실을 확인 문구가 말해야 한다(frontend/grid.md · screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_REJECT_NOTICE: 'notiRequest.reject.notice',
  /** 처리하면 요청자의 알림함에 행이 하나 생긴다 — 같은 트랜잭션이다(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_DONE_NOTICE: 'notiRequest.done.notice',

  /** 조회 0건 — <b>비어 있는 것이 정상인 유일한 화면</b>이다(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_EMPTY: 'notiRequest.empty',
  /** 선택에 이미 처리된 것이 섞였다 — 버튼이 비활성인 이유(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_HINT_NOT_RECEIVED: 'notiRequest.hint.notReceived',
  /** 미사용 계정 표시 — <b>`(삭제된 사용자)` 는 없다</b>(screen/admin/system/notice/requirements.md). */
  NOTI_REQUEST_USER_DISABLED: 'notiRequest.user.disabled',

  // ── 샘플 · 마스터-디테일 상하 분할 (playbook/INDEX.md) ─────────────────────
  //
  // 접두가 `sample.` 이다 — playbook/INDEX.md 이 요구한 기계가 알아볼 접두이며, 샘플을 걷어낼 때
  // 제거 changelog 의 LIKE 한 줄이 이 키들을 잡는다. 다른 접두를 쓴 키는 남아서 관리
  // 화면의 고아가 되고, 그 누락은 조용하다.

  /** 위 pane 그리드의 제목. */
  SAMPLE_ORDER_GRID_TITLE: 'sample.order.grid.title',
  /** 아래 pane 그리드의 제목 — 뒤에 고른 주문의 번호가 붙는다(frontend/grid.md). */
  SAMPLE_ITEM_GRID_TITLE: 'sample.item.grid.title',

  /** 조회조건 넷의 라벨. */
  SAMPLE_COND_ORD_NO: 'sample.cond.ordNo',
  SAMPLE_COND_CUST_NM: 'sample.cond.custNm',
  SAMPLE_COND_ORD_STAT: 'sample.cond.ordStat',
  SAMPLE_COND_ORD_DT: 'sample.cond.ordDt',

  /** 마스터 컬럼 아홉 — playbook/INDEX.md. */
  SAMPLE_COL_ORD_NO: 'sample.col.ordNo',
  SAMPLE_COL_ORD_NM: 'sample.col.ordNm',
  SAMPLE_COL_CUST_NM: 'sample.col.custNm',
  SAMPLE_COL_ORD_STAT: 'sample.col.ordStat',
  SAMPLE_COL_ORD_DT: 'sample.col.ordDt',
  SAMPLE_COL_ORD_QTY: 'sample.col.ordQty',
  SAMPLE_COL_ORD_AMT: 'sample.col.ordAmt',
  SAMPLE_COL_URGENT: 'sample.col.urgent',
  SAMPLE_COL_USE: 'sample.col.use',

  /** 디테일 컬럼 — playbook/INDEX.md. */
  SAMPLE_COL_ITEM_SEQ: 'sample.col.itemSeq',
  SAMPLE_COL_ITEM_CD: 'sample.col.itemCd',
  SAMPLE_COL_ITEM_NM: 'sample.col.itemNm',
  SAMPLE_COL_UOM: 'sample.col.uom',
  SAMPLE_COL_QTY: 'sample.col.qty',
  SAMPLE_COL_UNIT_AMT: 'sample.col.unitAmt',
  SAMPLE_COL_AMT: 'sample.col.amt',
  SAMPLE_COL_DLVR_DT: 'sample.col.dlvrDt',
  SAMPLE_COL_ITEM_STAT: 'sample.col.itemStat',
  SAMPLE_COL_RMK: 'sample.col.rmk',

  /** 출고 지시 버튼 — playbook/INDEX.md 의 실행 액션(프로그레스 없음). */
  SAMPLE_ACTION_SHIP: 'sample.action.ship',
  /** 실행 확인 문구 — `{count}`(frontend/grid.md). */
  SAMPLE_ACTION_SHIP_CONFIRM: 'sample.action.ship.confirm',
  /** 확정 아닌 행이 섞였다 — 버튼이 비활성인 이유(frontend/grid.md 의 `hint`). */
  SAMPLE_ACTION_SHIP_HINT: 'sample.action.ship.hint',

  /**
   * 품목 금액 재계산 버튼 — playbook/INDEX.md 의 실행 <b>둘 중 뒤</b>(<b>행×디테일 진행률</b>).
   *
   * <p>위의 출고 지시와 <b>같은 자리에 나란히 선다</b>: 같은 `EXECUTE` 이고 같은
   * `target:'selected'` 인데 하나는 즉시 끝나고 하나는 진행률을 갖는다 — 고른 주문마다
   * 딸린 품목이 <b>수천 건</b>일 수 있어서다(backend/crosscutting.md).
   */
  SAMPLE_ACTION_RECALC_ITEMS: 'sample.action.recalcItems',
  /**
   * 실행 확인 문구 — `{count}` 는 <b>주문 수</b>다(frontend/grid.md).
   *
   * <p>품목 건수를 적지 않는다. 그 값은 주문마다 세어야 알 수 있고, 확인 창을 띄우려고
   * 조회를 한 번 더 도는 것은 <b>실행 전에 실행만큼 일하는</b> 꼴이다 — 품목 건수는
   * 진행률 창이 구간마다 말한다.
   */
  SAMPLE_ACTION_RECALC_ITEMS_CONFIRM: 'sample.action.recalcItems.confirm',
  /**
   * 접수 아닌 행이나 품목 없는 행이 섞였다 — 버튼이 비활성인 이유(frontend/grid.md 의 `hint`).
   *
   * <p><b>확정부터는 금액이 잠긴다</b>(playbook/INDEX.md). 화면의 `amountEditable` 이 그렇게 서 있으므로
   * 이 실행이 그 문을 열어 주면 안 된다.
   */
  SAMPLE_ACTION_RECALC_ITEMS_HINT: 'sample.action.recalcItems.hint',

  /**
   * ②의 <b>세 번째 항목</b> — 품목이 없는 주문만 내린다.
   *
   * <p>조회로는 낼 수 없는 표다: 마스터는 주문을 내고 디테일은 고른 하나의 품목을 내므로,
   * *품목이 하나도 없는 주문들* 은 어느 그리드의 결과도 아니다(playbook/excel-download.md 의 임의 API).
   */
  SAMPLE_EXCEL_NO_ITEM: 'sample.excel.noItem',

  /** 품목이 있어 삭제 체크가 열리지 않는 이유 — `{count}`(frontend/grid.md). */
  SAMPLE_DELETE_BLOCKED: 'sample.delete.blocked',
  /** 잠긴 주문이라 편집이 열리지 않는 이유 — playbook/INDEX.md 의 `LOCK_YN`. */
  SAMPLE_LOCKED: 'sample.locked',
  /** 마스터 0건. */
  SAMPLE_ORDER_EMPTY: 'sample.order.empty',
  /** 품목 0건 — <b>행 추가는 열려 있다</b>(playbook/INDEX.md). */
  SAMPLE_ITEM_EMPTY: 'sample.item.empty',
  /** 주문을 고르지 않았다 — 아래 pane 이 통째로 비활성인 상태(frontend/grid.md). */
  SAMPLE_ITEM_NO_MASTER: 'sample.item.noMaster',

  // ── 샘플 · 단일 그리드 오프셋 (playbook/INDEX.md) ──────────────────────────
  //
  // **어휘의 본진이다.** playbook/INDEX.md 의 커버리지 표에서 굵은 점이 열둘인 화면이라 키도 그만큼
  // 는다 — 컬럼 서른과 조회조건 열둘이 전부 여기 이름을 갖는다.

  /** 그리드 제목 — 이 화면은 그리드가 하나다. */
  SAMPLE_OFFSET_GRID_TITLE: 'sample.offset.grid.title',
  /** 조회 결과 0건. */
  SAMPLE_OFFSET_EMPTY: 'sample.offset.empty',

  /** ①이 더한 조회조건 여덟 — playbook/INDEX.md. 앞의 넷은 ③과 같은 키를 쓴다. */
  SAMPLE_COND_ORD_NM: 'sample.cond.ordNm',
  SAMPLE_COND_CUST_ID: 'sample.cond.custId',
  SAMPLE_COND_CHNL: 'sample.cond.chnl',
  SAMPLE_COND_DLVR_YM: 'sample.cond.dlvrYm',
  SAMPLE_COND_ORD_AMT: 'sample.cond.ordAmt',
  /** **초로 받아 밀리초로 보낸다** — 라벨이 단위를 말해야 한다(57 의 `scale`). */
  SAMPLE_COND_PROC_SEC: 'sample.cond.procSec',
  SAMPLE_COND_URGENT: 'sample.cond.urgent',
  SAMPLE_COND_TAG: 'sample.cond.tag',
  SAMPLE_COND_ERR_MSG: 'sample.cond.errMsg',

  /** ①이 더한 컬럼 — playbook/INDEX.md 의 서른 중 ③이 그리지 않는 것들. */
  SAMPLE_COL_ORD_ID: 'sample.col.ordId',
  SAMPLE_COL_CUST_ID: 'sample.col.custId',
  SAMPLE_COL_CUST_GRD: 'sample.col.custGrd',
  SAMPLE_COL_CHNL: 'sample.col.chnl',
  SAMPLE_COL_PRIORITY: 'sample.col.priority',
  SAMPLE_COL_DLVR_YM: 'sample.col.dlvrYm',
  SAMPLE_COL_ORD_DTM: 'sample.col.ordDtm',
  SAMPLE_COL_VAT_AMT: 'sample.col.vatAmt',
  SAMPLE_COL_DC_RT: 'sample.col.dcRt',
  SAMPLE_COL_PROC_MS: 'sample.col.procMs',
  SAMPLE_COL_TAG_LIST: 'sample.col.tagList',
  SAMPLE_COL_MEMO: 'sample.col.memo',
  SAMPLE_COL_ERR_MSG: 'sample.col.errMsg',
  SAMPLE_COL_SRC_SYS: 'sample.col.srcSys',
  /**
   * `hidden` 컬럼의 헤더 — **어디에도 보이지 않는다**(frontend/grid.md).
   *
   * <p>그래도 선언하는 것은 헤더가 컬럼의 필수 항목이기 때문이며, 값을 비워 두면
   * 설정 창을 여는 다른 화면에서 **빈 줄**로 나타난다.
   */
  SAMPLE_COL_LOCK: 'sample.col.lock',
  SAMPLE_COL_REG_ID: 'sample.col.regId',
  SAMPLE_COL_REG_DTM: 'sample.col.regDtm',
  SAMPLE_COL_MOD_ID: 'sample.col.modId',
  SAMPLE_COL_MOD_DTM: 'sample.col.modDtm',

  /** 확정 — `target:'selected'`, 프로그레스 없음(playbook/INDEX.md). */
  SAMPLE_ACTION_CONFIRM: 'sample.action.confirm',
  SAMPLE_ACTION_CONFIRM_HINT: 'sample.action.confirm.hint',
  SAMPLE_ACTION_CONFIRM_CONFIRM: 'sample.action.confirm.confirm',

  /**
   * 일괄 재계산 — `target:'all'`, **`params` 모달 + 진행률**(playbook/INDEX.md).
   *
   * <p>확정과 <b>같은 `X` 액션인데 사용자 경험이 다른 것</b>이 이 화면의 핵심 시연이다.
   */
  SAMPLE_ACTION_RECALC: 'sample.action.recalc',
  /** `params` 모달의 제목이자 진행률 모달의 제목. */
  SAMPLE_ACTION_RECALC_TITLE: 'sample.action.recalc.title',
  SAMPLE_RECALC_BASE_DT: 'sample.recalc.baseDt',
  SAMPLE_RECALC_VAT_RATE: 'sample.recalc.vatRate',
  /** 진행률 모달이 도는 동안 보이는 문구 — 퍼센트만으로는 멈춘 것과 느린 것이 같다. */
  SAMPLE_RECALC_RUNNING: 'sample.recalc.running',

  /** `controls` 좌 — `beforeRead`(frontend/grid.md). */
  SAMPLE_CONTROL_RANGE: 'sample.control.range',
  SAMPLE_CONTROL_RANGE_ALL: 'sample.control.range.all',
  SAMPLE_CONTROL_RANGE_MINE: 'sample.control.range.mine',
  /** `controls` 우 — `beforeTools`. */
  SAMPLE_CONTROL_HELP: 'sample.control.help',
  SAMPLE_CONTROL_PRINT: 'sample.control.print',
  SAMPLE_HELP_BODY: 'sample.help.body',

  /** 고객 참조 팝업 — 레지스트리 키 `sample.customer` 가 쓰는 문구(playbook/INDEX.md). */
  SAMPLE_POPUP_CUSTOMER: 'sample.popup.customer',
  SAMPLE_POPUP_CUSTOMER_ID: 'sample.popup.customer.id',
  SAMPLE_POPUP_CUSTOMER_NM: 'sample.popup.customer.nm',
  SAMPLE_POPUP_CUSTOMER_GRD: 'sample.popup.customer.grd',

  /*
    `sample.tag.modal.title` 이 여기 있었다(2026-09-02 에 지웠다). 창의 제목은 **컬럼
    헤더**이며(`columnDefs` 의 `headerText`) `textModal` 도 같다 — 편집기마다 제목 키를
    따로 두면 같은 컬럼이 셀에서와 창에서 다른 이름으로 보인다.

    그 키는 frontend/grid.md 의 초안(*모달 자체를 레지스트리에서 고른다*)이 남긴 것이다. 껍데기가
    하나로 정리되면서 제목을 고를 자리가 없어졌고, 화면은 그 키를 **레지스트리 키 자리에**
    넣어 쓰고 있었다 — 그래서 추가 버튼이 아무 일도 하지 않았다.

    시드는 `NONE` 으로 표시만 하고 행을 지우지 않는다(seed.md — 번역이 딸려 있다).
  */
  /**
   * 태그 후보 팝업 — 레지스트리 키 `sample.tag` 가 쓰는 제목(playbook/INDEX.md).
   *
   * <p>**`listModal` 의 창과 갈린다.** 저것은 *편집하는 창*(쌓고 순서를 바꾸고 뺀다)이라
   * 제목이 **컬럼 헤더**이고, 이것은 그 위에 뜨는 **고르는 창**이다 — 껍데기가 하나이고
   * 레지스트리가 정하는 것이 후자다(frontend/grid.md). 헤더를 그대로 쓰면 모달 위에 모달이 뜬
   * 화면에서 **제목이 같은 창 둘**이 겹친다.
   */
  SAMPLE_POPUP_TAG: 'sample.popup.tag',
  /**
   * 태그가 상한을 넘었다 — playbook/INDEX.md 의 결정. `{max}` 를 채운다.
   *
   * <p>**층이 둘인데 문구는 하나다**(frontend/grid.md) — ①은 컬럼 층에서 배열을 세고 ⑤는 그리드
   * 층에서 행을 세는데, 사용자가 알아야 하는 사실은 *이 주문에 태그가 몇 개까지* 하나다.
   * 서버도 같은 것을 본다(`err.sample.tag-too-many`).
   */
  SAMPLE_TAG_TOO_MANY: 'sample.tag.tooMany',

  /** 조건부 필수 — `DC_RT > 0` 이면 비고가 필수다(playbook/INDEX.md · frontend/grid.md). */
  SAMPLE_RMK_REQUIRED: 'sample.rmk.required',

  // ── 샘플 · 단일 그리드 커서 (playbook/INDEX.md) ────────────────────────────
  //
  // **커서형 페이징의 유일한 실물**이다. 키가 적은 것이 이 화면의 성격이며 — 조회 전용에
  // 조회조건 셋 — ①의 *어휘를 전부 낸다* 와 반대 방향이다.

  SAMPLE_CURSOR_GRID_TITLE: 'sample.cursor.grid.title',
  SAMPLE_CURSOR_EMPTY: 'sample.cursor.empty',
  /** 조회조건은 셋인데 둘은 ①과 같은 키를 쓴다 — 갈리는 것은 기간 칸 하나다. */
  SAMPLE_CURSOR_COND_ORD_DTM: 'sample.cursor.cond.ordDtm',
  /**
   * **총 건수를 잃은 자리**를 메우는 문구 — `{days}`(playbook/INDEX.md).
   *
   * <p>무엇을 물어서 나온 N건인지가 함께 있어야 *전부가 N건* 으로 읽히지 않는다.
   */
  SAMPLE_CURSOR_FOOTER_RANGE: 'sample.cursor.footer.range',
  /** `controls` 좌의 토글 — 값이 둘이라 스위치다(playbook/INDEX.md). */
  SAMPLE_CURSOR_AUTO_REFRESH: 'sample.cursor.autoRefresh',
  SAMPLE_CURSOR_HELP_BODY: 'sample.cursor.help.body',
  /** 할인율이 0~100 을 벗어났다 — 그리드 층의 검증(frontend/grid.md). */
  SAMPLE_DC_RT_RANGE: 'sample.dcRt.range',
  /** 조회조건의 Y/N 콤보 — **`select` 이지 스위치가 아니다**(frontend/search-form.md). */
  SAMPLE_YN_Y: 'sample.yn.y',
  SAMPLE_YN_N: 'sample.yn.n',

  // ── 샘플 · 마스터-디테일 복수 탭 (playbook/INDEX.md) ──────────────────────
  //
  // **하단 디테일 탭의 유일한 실물**이다. 마스터와 조회조건은 ③의 키를 그대로 쓰고,
  // 여기 있는 것은 **탭 이름 둘과 태그 탭이 새로 그리는 것**뿐이다 — 같은 표에 새 키를
  // 만들면 ③과 ⑤에서 같은 컬럼이 다른 이름으로 보이는 날이 온다.

  SAMPLE_TAB_ITEM: 'sample.tab.item',
  SAMPLE_TAB_TAG: 'sample.tab.tag',
  SAMPLE_TAG_GRID_TITLE: 'sample.tag.grid.title',
  SAMPLE_TAG_EMPTY: 'sample.tag.empty',
  /**
   * **`SORT_NO` 와 나란히 보여야 뜻이 있는 컬럼**이다(playbook/INDEX.md).
   *
   * <p>복합 PK 의 절반이라 고칠 수 없고, ①에서는 `SORT_NO` 와 같은 값이었다 — 둘을 함께
   * 그려야 *왜 컬럼이 둘인가* 를 테이블에서 유추하지 않아도 된다.
   */
  SAMPLE_COL_TAG_SEQ: 'sample.col.tagSeq',
  SAMPLE_COL_TAG_NM: 'sample.col.tagNm',
  SAMPLE_COL_TAG_TYPE: 'sample.col.tagType',
  SAMPLE_COL_SORT_NO: 'sample.col.sortNo',
  /** 정렬순서가 겹쳤다 — 그리드 층의 검증(frontend/grid.md). 서버도 같은 것을 본다. */
  SAMPLE_TAG_SORT_DUPLICATED: 'sample.tag.sortDuplicated',

  // ── 샘플 · 엑셀 업로드 (playbook/INDEX.md) ────────────────────────────────
  //
  // **화면 안 최상단 세그먼트와 엑셀 업로드의 유일한 실물**이다. 파일을 읽고 대조하는
  // 문구는 여기 없다 — `excel.upload.*` 가 공용 컴포넌트의 것이며(`ExcelUploadTab`),
  // 화면마다 베끼면 같은 사고를 두 벌의 문구로 설명하게 된다.

  /** 세그먼트 둘 — **한 화면의 두 모드**이지 화면 둘이 아니다(playbook/INDEX.md). */
  SAMPLE_EXCEL_SEG_DATA: 'sample.excel.seg.data',
  SAMPLE_EXCEL_SEG_UPLOAD: 'sample.excel.seg.upload',
  SAMPLE_EXCEL_DATA_TITLE: 'sample.excel.data.title',
  /** 업로드 탭의 제목 — **미리보기가 곧 행 상태다**(playbook/INDEX.md). */
  SAMPLE_EXCEL_UPLOAD_TITLE: 'sample.excel.upload.title',
  SAMPLE_EXCEL_EMPTY: 'sample.excel.empty',
  SAMPLE_COND_ITEM_CD: 'sample.cond.itemCd',
  SAMPLE_COND_ITEM_STAT: 'sample.cond.itemStat',
  SAMPLE_COND_DLVR_DT: 'sample.cond.dlvrDt',
  /** 파일 안에서 같은 `(주문번호, 품목코드)` 가 두 번이다 — 그리드 층(frontend/grid.md). */
  SAMPLE_EXCEL_KEY_DUPLICATED: 'sample.excel.keyDuplicated',

  // @feat FEAT-042
  QUOTA_GRADE_TITLE: 'quota_grade.title',
  QUOTA_GRADE_DETAIL: 'quota_grade.detail',
  QUOTA_GRADE_MONTH: 'quota_grade.month',
  QUOTA_GRADE_MEMO: 'quota_grade.memo',
  QUOTA_GRADE_NAME: 'quota_grade.name',
  QUOTA_GRADE_RANK: 'quota_grade.rank',
  QUOTA_GRADE_WEIGHT: 'quota_grade.weight',
  QUOTA_GRADE_TRANSFER: 'quota_grade.transfer',
  QUOTA_GRADE_DESCRIPTION: 'quota_grade.description',
  QUOTA_GRADE_REFERENCES: 'quota_grade.references',
  QUOTA_GRADE_COPY: 'quota_grade.copy',
  QUOTA_GRADE_SELECT: 'quota_grade.select',
  QUOTA_GRADE_LOCKED: 'quota_grade.locked',
  QUOTA_GRADE_NOT_INSTALLED: 'quota_grade.not_installed',
  QUOTA_GRADE_UNAVAILABLE: 'quota_grade.unavailable',
  QUOTA_GRADE_DETAIL_REQUIRED: 'quota_grade.detail_required',
  QUOTA_GRADE_EMPTY: 'quota_grade.empty',
  QUOTA_GRADE_COPY_NEW: 'quota_grade.copy_new',
  QUOTA_GRADE_UNKNOWN: 'quota_grade.unknown',
  QUOTA_GRADE_STALE: 'quota_grade.stale',
  // @feat FEAT-005
  /** Portal 이용자 */
  PORTAL_TITLE: 'portal_user.title',
  /** 이용자 식별자 */
  PORTAL_ID: 'portal_user.id',
  /** 이름 */
  PORTAL_NAME: 'portal_user.name',
  /** 조직 */
  PORTAL_ORGANIZATION: 'portal_user.organization',
  /** 자체 계정 구분 */
  PORTAL_MODE: 'portal_user.mode',
  /** 정보 원천 */
  PORTAL_SOURCE: 'portal_user.source',
  /** 사용 시작일 */
  PORTAL_START: 'portal_user.start',
  /** 사용 종료일 */
  PORTAL_END: 'portal_user.end',
  /** 이용 상태 */
  PORTAL_ACTIVE: 'portal_user.active',
  /** 사용 */
  PORTAL_ENABLED: 'portal_user.enabled',
  /** 중지 */
  PORTAL_DISABLED: 'portal_user.disabled',
  /** 추가 토큰 */
  PORTAL_EXTRA: 'portal_user.extra',
  /** 데이터소스 아이디 매핑 */
  PORTAL_DS_IDENTITY: 'portal_user.ds_identity',
  /** 대상 시스템 아이디 매핑 */
  PORTAL_SYSTEM_IDENTITY: 'portal_user.system_identity',
  /** 등급 효력 시작월 */
  PORTAL_EFFECTIVE_MONTH: 'portal_user.effective_month',
  /** 신청 번호 */
  PORTAL_REQUEST_ID: 'portal_user.request_id',
  /** 신청 토큰 */
  PORTAL_REQUESTED: 'portal_user.requested',
  /** 승인 토큰 */
  PORTAL_APPROVED: 'portal_user.approved',
  /** 처리 상태 */
  PORTAL_STATUS: 'portal_user.status',
  /** 사용자 아이디 */
  PORTAL_EXTERNAL_ID: 'portal_user.external_id',
  /** 신원 연결 근거 */
  PORTAL_IDENTITY_EVIDENCE: 'portal_user.identity_evidence',
  /** 설정 가능 사유 */
  PORTAL_TARGET_STATE: 'portal_user.target_state',
  /** 한도 */
  PORTAL_TOKEN_LIMIT: 'portal_user.token_limit',
  /** 사용량 */
  PORTAL_TOKEN_USED: 'portal_user.token_used',
  /** 잔여량 */
  PORTAL_TOKEN_REMAINING: 'portal_user.token_remaining',
  /** 이용자도 함께 비활성화 */
  PORTAL_DEACTIVATE_TOGETHER: 'portal_user.deactivate_together',
  /** 기존 자료는 삭제하지 않고 비활성화합니다. */
  PORTAL_KEEP_HISTORY: 'portal_user.keep_history',
  /** 저장된 이용자를 선택하세요. 신규 이용자는 상단에서 먼저 저장합니다. */
  PORTAL_SELECT: 'portal_user.select',
  /** 다른 영역에서 이용자 정보가 변경되었습니다. 입력을 확인한 뒤 이 영역을 다시 조회하세요. */
  PORTAL_STALE: 'portal_user.stale',
  /** 이용자 정보를 확인할 수 없습니다. 다시 조회하세요. */
  PORTAL_UNAVAILABLE: 'portal_user.unavailable',
  /** 월별 배정 기능 연결 전입니다. 한도·사용량·잔여량은 아직 제공하지 않습니다. */
  PORTAL_NOT_INSTALLED: 'portal_user.not_installed',
  /** 이 원천의 연결이 준비되지 않았습니다. */
  PORTAL_NOT_CONNECTED: 'portal_user.not_connected',
  /** 비밀번호 초기화 */
  PORTAL_RESET: 'portal_user.reset',
  /** 잠금 해제 */
  PORTAL_UNLOCK: 'portal_user.unlock',
  /** 선택한 계정의 자격정보를 변경합니다. 변경 사유를 함께 기록합니다. */
  PORTAL_CREDENTIAL_CONFIRM: 'portal_user.credential_confirm',
  /** 자격정보 처리 결과 */
  PORTAL_CREDENTIAL_RESULT: 'portal_user.credential_result',
  /** 이 임시 비밀번호는 지금 한 번만 표시됩니다. 전달 후 창을 닫으세요. */
  PORTAL_SECRET_ONCE: 'portal_user.secret_once',
  /** 임시 비밀번호 */
  PORTAL_TEMP_PASSWORD: 'portal_user.temp_password',
  /** 원 명령은 처리되었으며 비밀번호를 다시 표시할 수 없습니다. 필요하면 별도 초기화를 요청하세요. */
  PORTAL_SECRET_LOST: 'portal_user.secret_lost',
  /** 잠금 해제가 완료되었습니다. */
  PORTAL_UNLOCKED: 'portal_user.unlocked',
  /** 외부 원천 키 */
  PORTAL_EXTERNAL_SOURCE: 'portal_user.external_source',
  /** 외부 주체 식별자 */
  PORTAL_EXTERNAL_SUBJECT: 'portal_user.external_subject',
  /** 추가 토큰은 소유 화면에서 변경합니다. */
  PORTAL_EXTRA_READ_ONLY: 'portal_user.extra_read_only',
  /** 먼저 원 계정의 처리 결과를 확인하세요. */
  PORTAL_CREDENTIAL_PENDING: 'portal_user.credential_pending',
  /** 소속 이용자 */
  PORTAL_GROUP_USER: 'portal_user.group_user',
  /** 이용자 선택 */
  PORTAL_USER_PICK: 'portal_user.user_pick',
  /** 소속을 편집할 이용자를 선택하세요. */
  PORTAL_USER_REQUIRED: 'portal_user.user_required',
  /** 다른 영역의 저장으로 기준본이 변경되었습니다. */
  PORTAL_REVISION_CONFLICT: 'portal_user.revision_conflict',
  /** 등급은 적용할 월의 기준에 포함되어야 합니다. 미래 예약만 취소·이동할 수 있습니다. */
  PORTAL_GRADE_HINT: 'portal_user.grade_hint',
  /** 확인 필요 */
  PORTAL_UNKNOWN: 'portal_user.unknown',
  /** 계정 잠금 */
  PORTAL_LOCKED: 'portal_user.locked',
  /** 비밀번호 변경 필요 */
  PORTAL_MUST_CHANGE: 'portal_user.must_change',
  /** 구분 */
  CHAT_HELP_KIND: 'chat_help.kind',
  /** 작성 형식 */
  CHAT_HELP_FORMAT: 'chat_help.format',
  /** 본문 */
  CHAT_HELP_BODY: 'chat_help.body',
  /** 적용 버전 */
  CHAT_HELP_REVISION: 'chat_help.revision',
  /** 사용 도움말 */
  CHAT_HELP_USAGE: 'chat_help.usage',
  /** Portal 도움말 영역 */
  CHAT_HELP_USAGE_POSITION: 'chat_help.usage_position',
  /** 도움말 열기 */
  CHAT_HELP_USAGE_TIMING: 'chat_help.usage_timing',
  /** 새 대화 초기 안내 */
  CHAT_HELP_START: 'chat_help.start',
  /** 새 대화 시작 영역 */
  CHAT_HELP_START_POSITION: 'chat_help.start_position',
  /** 새 대화 시작 */
  CHAT_HELP_START_TIMING: 'chat_help.start_timing',
  /** 개인정보·전송 주의사항 */
  CHAT_HELP_NOTICE: 'chat_help.notice',
  /** 입력창 주변 */
  CHAT_HELP_NOTICE_POSITION: 'chat_help.notice_position',
  /** 요청 제출 전 */
  CHAT_HELP_NOTICE_TIMING: 'chat_help.notice_timing',
  /** 미리보기 */
  CHAT_HELP_PREVIEW: 'chat_help.preview',
  /** 본문 크게 편집 */
  CHAT_HELP_ENLARGE: 'chat_help.enlarge',
  /** 반영 */
  CHAT_HELP_APPLY: 'chat_help.apply',
  /** 본문 미설정입니다. 작성 후 저장해야 안내에 사용할 수 있습니다. */
  CHAT_HELP_UNSET: 'chat_help.unset',
  /** 허용하지 않는 표현은 미리보기에서 제거했습니다. 저장 원문은 보존됩니다. */
  CHAT_HELP_REMOVED: 'chat_help.removed',
  /** 안전하게 표시할 본문이 없습니다. 형식과 내용을 확인하세요. */
  CHAT_HELP_EMPTY: 'chat_help.empty',
  /** 변경 사유 */
  CHAT_HELP_REASON: 'chat_help.reason',
  /** 선택 구분만 저장 */
  CHAT_HELP_SAVE_SCOPE: 'chat_help.save_scope',
  /** 저장 결과 확인이 필요합니다. 원 명령을 확인한 뒤 계속하세요. */
  CHAT_HELP_PENDING: 'chat_help.pending',
  /** 확인 중인 원 저장 내용과 다릅니다. 원 명령 결과를 먼저 확인하세요. */
  CHAT_HELP_CHANGED: 'chat_help.changed',
  /** 저장 결과 확인·재전송 */
  CHAT_HELP_RECOVER: 'chat_help.recover',
  /** 처리하지 못했습니다. 입력을 보존했습니다. 다시 확인하세요. */
  CHAT_HELP_FAILED: 'chat_help.failed',
  /** 현재 권한으로 콘텐츠를 조회할 수 없습니다. */
  CHAT_HELP_DENIED: 'chat_help.denied',
  /** 원문 최대 200,000자. 미리보기는 저장하거나 외부 미디어를 불러오지 않습니다. */
  CHAT_HELP_HINT: 'chat_help.hint',
  /** 조회 전용입니다. 본문과 미리보기를 확인할 수 있습니다. */
  CHAT_HELP_READONLY: 'chat_help.readonly',
  /** 불러오는 중… */
  CHAT_HELP_LOADING: 'chat_help.loading',
  /** 조회 */
  CHAT_HELP_READ: 'chat_help.read',
  /** 선택 구분을 저장했습니다. */
  CHAT_HELP_SAVED: 'chat_help.saved',
  /** 표시 정책 */
  CHAT_HELP_POLICY: 'chat_help.policy',
  /** 태그 ID */
  SEARCH_TAG_ID: 'search_tag.tag_id',
  /** 지식 검색 태그·동의어 */
  SEARCH_TAG_TITLE: 'search_tag.title',
  /** 검색 텍스트 */
  SEARCH_TAG_TEXT: 'search_tag.text',
  /** 사용 여부 */
  SEARCH_TAG_USE: 'search_tag.use',
  /** 동의어 */
  SEARCH_TAG_SYNONYMS: 'search_tag.synonyms',
  /** 연결 지식 */
  SEARCH_TAG_REFERENCES: 'search_tag.references',
  /** 설명 */
  SEARCH_TAG_DESCRIPTION: 'search_tag.description',
  /** 지식 ID */
  SEARCH_TAG_KNOWLEDGE_ID: 'search_tag.knowledge_id',
  /** 지식명 */
  SEARCH_TAG_KNOWLEDGE_NAME: 'search_tag.knowledge_name',
  /** 지식 리비전 */
  SEARCH_TAG_REVISION: 'search_tag.revision',
  /** 매핑 활성 여부 */
  SEARCH_TAG_MAPPING: 'search_tag.mapping',
  /** 태그를 선택하면 상세를 조회합니다. */
  SEARCH_TAG_SELECT: 'search_tag.select',
  /** 지식 연결 기능이 아직 연결되지 않았습니다. */
  SEARCH_TAG_NOT_CONNECTED: 'search_tag.not_connected',
  /** 참조를 확인할 수 없어 삭제할 수 없습니다. */
  SEARCH_TAG_UNAVAILABLE: 'search_tag.unavailable',
  /** 연결 지식을 조회할 권한이 없습니다. */
  SEARCH_TAG_FORBIDDEN: 'search_tag.forbidden',
  /** 상세를 조회하고 동의어·지식 연결이 없는지 확인해 주세요. */
  SEARCH_TAG_DELETE_BLOCKED: 'search_tag.delete_blocked',
  /** 태그가 변경되었습니다. 다시 조회해 주세요. */
  SEARCH_TAG_STALE: 'search_tag.stale',
  /** 상세를 조회하고 있습니다. */
  SEARCH_TAG_LOADING: 'search_tag.loading',
  /** 상세를 조회하지 못했습니다. 다시 조회해 주세요. */
  SEARCH_TAG_FAILED: 'search_tag.failed',
} as const;
