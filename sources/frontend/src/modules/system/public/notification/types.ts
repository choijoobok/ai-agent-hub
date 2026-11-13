// @feat FEAT-509
/**
 * 시스템 정보 — frontend/tabs.md. 서버 record 와 1:1 이다.
 *
 * **셋이 `null` 일 수 있다** — `version` · `commit` · `buildTime`. `build-info.properties`
 * 는 Gradle 이 만들므로 IDE 가 클래스만 컴파일해 띄운 서버에는 없다. 없는 것을 있는 것처럼
 * 채우면 그 값이 곧 잘못된 단서가 된다.
 */
export interface SystemInfo {
  /** 활성 Spring 프로필. 비면 `default` 하나로 도는 중이다. */
  profiles: string[];
  version: string | null;
  /** git 리비전. `-dirty` 접미가 붙으면 그 커밋과 같지 않은 빌드다. */
  commit: string | null;
  branch: string | null;
  /** 빌드 시각 — **배포된 것이 만들어진 때**. */
  buildTime: string | null;
  /** 기동 시각 — **마지막으로 올라온 때**. 빌드 시각과 갈리면 재기동만 한 것이다. */
  startedAt: string;
  cacheVersions: Record<string, number>;
}

/** 기존 생산자 네 종류와 외부 퇴직·조직 이동 사건(CR-034). */
export type NotiType = 'REQUEST' | 'AUTH' | 'AUTHZ' | 'BATCH' | 'PORTAL_CHANGE';

/** 요청 유형 — sys/notification.md. 공통코드가 아니다(screen/admin/system/notice/requirements.md). */
export type ReqType = 'ROLE_GRANT' | 'UNLOCK' | 'ETC';

/** 요청 상태 — **셋에서 닫힌다**(sys/notification.md · NI-12). 워크플로가 아니다. */
export type ReqStatus = 'RECEIVED' | 'DONE' | 'REJECTED';

/** 내 알림 한 줄 — frontend/tabs.md. */
export interface MyNotiRow {
  notiId: number;
  notiTypeCd: NotiType;
  title: string;
  content: string | null;
  /**
   * 클릭하면 갈 메뉴. `null` 이면 이동 없음이다.
   *
   * **내 메뉴 트리에 없으면 이동 버튼을 비활성한다**(`NI-11`) — 눌러서 403 을 보는 것보다
   * 낫다. **알림이 권한을 주지 않는다.**
   */
  linkMenuCd: string | null;
  linkParam: string | null;
  readYn: boolean;
  creDtm: string;
}

/**
 * 내 알림 탭이 한 번에 받는 것.
 *
 * **셋이 함께 온다.** 목록에는 상한이 있으므로 화면이 미읽음을 세면 그 셈은 상한 안에서만
 * 맞다 — 배지가 세야 하는 것은 **전건의 미읽음**이다(frontend/tabs.md).
 */
export interface MyNotifications {
  rows: MyNotiRow[];
  unreadCount: number;
  /** `rows.length` 와 다르면 **잘렸다** — 화면이 "최근 N건" 이라고 말한다. */
  totalCount: number;
}

/** 내 요청 한 줄 — screen/_standalone/README.md. 요청자는 언제나 나라서 싣지 않는다. */
export interface MyRequestRow {
  reqId: number;
  creDtm: string;
  reqTypeCd: ReqType;
  reqContent: string;
  reqStatCd: ReqStatus;
  handleDtm: string | null;
  /** **반려 사유가 여기 온다**(screen/admin/system/notice/requirements.md). 처리에서는 비어 있을 수 있다. */
  handleMemo: string | null;
}

/** 내 요청 탭이 받는 것 — **미읽음이 없다**(내가 보낸 것이다 · frontend/tabs.md). */
export interface MyRequests {
  rows: MyRequestRow[];
  totalCount: number;
}
