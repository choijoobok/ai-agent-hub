// @feat FEAT-509
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationModal } from '@/modules/system/public/notification/NotificationModal';
import {
  getMyNotifications,
  getMyRequests,
  getSystemInfo,
  markAllNotiRead,
  markNotiRead,
} from '@/modules/system/public/notification/api/notificationApi';
import {
  getNoticeList,
  getNoticeOne,
} from '@/modules/system/public/notice/api/noticeApi';
import type {
  MyNotiRow,
  MyNotifications,
  SystemInfo,
} from '@/modules/system/public/notification/types';
import type { NoticeBrief } from '@/modules/system/public/notice/types';

vi.mock('@/modules/system/public/notification/api/notificationApi', () => ({
  getSystemInfo: vi.fn(),
  getMyNotifications: vi.fn(),
  markNotiRead: vi.fn(),
  markAllNotiRead: vi.fn(),
  getMyRequests: vi.fn(),
}));

vi.mock('@/modules/system/public/notice/api/noticeApi', () => ({
  getNoticeList: vi.fn(),
  getNoticeOne: vi.fn(),
}));

const loadSystemInfo = vi.mocked(getSystemInfo);
const loadNotices = vi.mocked(getNoticeList);
const loadNotice = vi.mocked(getNoticeOne);
const loadMine = vi.mocked(getMyNotifications);
const readOne = vi.mocked(markNotiRead);
const readAll = vi.mocked(markAllNotiRead);
const loadRequests = vi.mocked(getMyRequests);

/**
 * 알림 모달 — frontend/tabs.md · screen/_standalone/README.md.
 *
 * <p>지키는 것 일곱이다 — ① **탭 순서가 방향인가**, ② **목록을 열었다고 전체 읽음이
 * 되지 않는가**(frontend/tabs.md · `NI-06`), ③ **항목을 열면 그 항목만인가**(`NI-07`),
 * ④ **권한 없는 메뉴로 가는 이동이 막히는가**(`NI-11`), ⑤ **이동이 `LINK_PARAM` 을 버리지
 * 않는가**(`NI-10`), ⑥ **시스템 정보는 눌러야 읽는가**, ⑦ **`PageUp`/`PageDown` 이 이 모달의
 * 탭을 옮기고 셸로 내려가지 않는가**(frontend/tabs.md).
 *
 * <p>②가 특히 조용히 깨진다. 목록 조회에 읽음 처리를 붙이면 화면은 오히려 <i>깔끔해
 * 보이고</i>, 사용자는 **훑어만 봤는데 배지가 사라진 것**을 나중에야 안다 — 그때는
 * 놓친 알림을 찾을 방법이 없다.
 */
describe('알림 모달', () => {
  const FULL: SystemInfo = {
    profiles: ['local'],
    version: '0.0.1-SNAPSHOT',
    commit: '75743cb',
    branch: 'main',
    buildTime: '2026-08-29T13:32:39Z',
    startedAt: '2026-08-29T13:35:00Z',
    cacheVersions: { message: 9 },
  };

  const NOTICE: NoticeBrief = {
    noticeId: 1,
    title: '시스템 점검 안내',
    noticeFromDtm: '2026-08-25T00:00:00Z',
    noticeToDtm: null,
    regId: 'admin',
    regNm: '시스템 관리자',
    noticeStat: 'ACTIVE',
  };

  /** 이동 링크와 파라미터가 있는 알림 — `NI-10` · `NI-11` 의 대상이다. */
  const UNREAD: MyNotiRow = {
    notiId: 10,
    notiTypeCd: 'BATCH',
    title: '배치 실패 — 야간 정산',
    content: '노드 was-02',
    linkMenuCd: 'BATCH_HISTORY',
    // 실행 번호가 아니라 **배치 ID** 다 — 받는 화면의 넘겨주기 한 자리가 그것이다.
    linkParam: 'RETENTION_PURGE',
    readYn: false,
    creDtm: '2026-08-26T02:00:00Z',
  };

  const MINE: MyNotifications = { rows: [UNREAD], unreadCount: 1, totalCount: 1 };

  beforeEach(() => {
    vi.clearAllMocks();
    loadSystemInfo.mockResolvedValue(FULL);
    loadNotices.mockResolvedValue([NOTICE]);
    loadNotice.mockResolvedValue({ ...NOTICE, content: '본문', contentTypeCd: 'TEXT' });
    loadMine.mockResolvedValue(MINE);
    readOne.mockResolvedValue({ ...MINE, rows: [{ ...UNREAD, readYn: true }], unreadCount: 0 });
    readAll.mockResolvedValue({ ...MINE, rows: [{ ...UNREAD, readYn: true }], unreadCount: 0 });
    loadRequests.mockResolvedValue({ rows: [], totalCount: 0 });
  });

  /** `canOpenMenu` 를 인자로 받는 것은 `NI-11` 을 양쪽으로 확인하기 위해서다. */
  const show = (options?: {
    canOpenMenu?: boolean;
    onUnread?: (count: number) => void;
    onOpenMenu?: (menuCd: string, linkParam: string | null) => void;
  }) =>
    render(
      <NotificationModal
        open
        onClose={vi.fn()}
        onUnread={options?.onUnread ?? vi.fn()}
        canOpenMenu={() => options?.canOpenMenu ?? true}
        onOpenMenu={options?.onOpenMenu ?? vi.fn()}
      />,
    );

  const openMineTab = async () => {
    fireEvent.click(screen.getByRole('tab', { name: 'notification.tab.mine' }));
    await waitFor(() => expect(loadMine).toHaveBeenCalled());
  };

  /** antd 의 활성 탭. 셸의 탭바와 구별하려 모달 안에서 찾는다. */
  const activeTab = () => document.querySelector('.ant-modal .ant-tabs-tab-active')?.textContent;

  it('TC-50911 PORTAL_CHANGE도 기존 알림함에서 표시·읽음 처리하고 권한 없는 상세 이동은 막는다', async () => {
    const row: MyNotiRow = { ...UNREAD, notiTypeCd: 'PORTAL_CHANGE', title: '조직 이동에 따른 Portal 이용 상태 변경' };
    loadMine.mockResolvedValue({ rows: [row], unreadCount: 1, totalCount: 1 });
    show({ canOpenMenu: false });
    await openMineTab();
    fireEvent.click(await screen.findByText(row.title));
    expect(readOne).toHaveBeenCalledWith(row.notiId);
    expect((await screen.findByRole('button', { name: 'notification.mine.goto' })).hasAttribute('disabled')).toBe(true);
  });

  it('탭 셋이 방향 순서로 있다 — 관리자→전체, 시스템→나, 나→관리자', () => {
    show();

    const tabs = screen.getAllByRole('tab').map((element) => element.textContent);
    expect(tabs).toEqual([
      'notification.tab.notice',
      'notification.tab.mine',
      'notification.tab.request',
    ]);
  });

  it('공지 탭이 먼저 열리고 지난 공지는 빠져 있다 — 켜야 들어온다(NI-04)', async () => {
    show();

    await waitFor(() => expect(loadNotices).toHaveBeenCalledWith(false));

    fireEvent.click(screen.getByRole('checkbox'));
    await waitFor(() => expect(loadNotices).toHaveBeenCalledWith(true));
  });

  it('목록을 열어도 읽음이 되지 않는다 — 훑어만 봐도 배지가 사라지면 안 된다(NI-06)', async () => {
    show();
    await openMineTab();

    expect(readOne).not.toHaveBeenCalled();
    expect(readAll).not.toHaveBeenCalled();
  });

  it('항목을 열면 그 항목만 읽음이 된다 — 배지도 그만큼만 준다(NI-07)', async () => {
    const onUnread = vi.fn();
    show({ onUnread });
    await openMineTab();

    fireEvent.click(screen.getByText('배치 실패 — 야간 정산'));

    await waitFor(() => expect(readOne).toHaveBeenCalledWith(10));
    await waitFor(() => expect(onUnread).toHaveBeenCalledWith(0));
  });

  it('권한이 없어진 메뉴로 가는 알림은 이동이 막힌다 — 403 을 보이는 것보다 낫다(NI-11)', async () => {
    show({ canOpenMenu: false });
    await openMineTab();

    fireEvent.click(screen.getByText('배치 실패 — 야간 정산'));

    const goto = await screen.findByRole('button', { name: 'notification.mine.goto' });
    expect(goto.hasAttribute('disabled')).toBe(true);
  });

  it('이동은 LINK_PARAM 을 함께 넘긴다 — 화면이 그 값으로 조회된 채 열린다(NI-10)', async () => {
    const onOpenMenu = vi.fn();
    show({ onOpenMenu });
    await openMineTab();

    fireEvent.click(screen.getByText('배치 실패 — 야간 정산'));
    fireEvent.click(await screen.findByRole('button', { name: 'notification.mine.goto' }));

    /*
      값을 버리면 화면은 열리지만 **아무 데도 가 있지 않다** — 탭이 바뀌었으므로 사용자는
      무언가 일어났다고 읽는데(`handoffStore` 가 같은 실패를 이미 겪었다) 목록은 전체다.
    */
    expect(onOpenMenu).toHaveBeenCalledWith('BATCH_HISTORY', 'RETENTION_PURGE');
  });

  it('PageDown 은 이 모달의 탭을 옮기고 셸로 내려가지 않는다 — frontend/tabs.md', async () => {
    const shell = vi.fn();
    window.addEventListener('keydown', shell);
    try {
      show();
      await waitFor(() => expect(activeTab()).toBe('notification.tab.notice'));

      // **모달 밖에서** 난 키다 — 목록의 빈 곳을 눌러 포커스가 빠진 상황이며, 이 창이
      // 듣지 않으면 뒤에 가린 셸의 탭바가 조용히 움직인다.
      fireEvent.keyDown(document.body, { key: 'PageDown' });

      await waitFor(() => expect(activeTab()).toBe('notification.tab.mine'));
      expect(shell).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('keydown', shell);
    }
  });

  it('상세를 보는 동안은 옮기지 않되 셸로 내려가지도 않는다 — 옮길 탭이 없다(NI-09)', async () => {
    const shell = vi.fn();
    window.addEventListener('keydown', shell);
    try {
      show();
      await openMineTab();
      fireEvent.click(screen.getByText('배치 실패 — 야간 정산'));
      await waitFor(() => expect(activeTab()).toBeUndefined());

      fireEvent.keyDown(document.body, { key: 'PageDown' });

      // 탭이 그려지지 않는 구간이라 옮길 것이 없다. 그래도 **삼킨다** — 삼키지 않으면
      // 상세를 읽는 동안에만 셸의 탭바가 움직이고, 닫았을 때 다른 화면이 열려 있다.
      expect(activeTab()).toBeUndefined();
      expect(shell).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('keydown', shell);
    }
  });

  it('수정자가 붙으면 이 키가 아니다 — 셸과 같은 규칙이라 길이 하나다', async () => {
    show();
    await waitFor(() => expect(activeTab()).toBe('notification.tab.notice'));

    fireEvent.keyDown(document.body, { key: 'PageDown', altKey: true });
    fireEvent.keyDown(document.body, { key: 'PageDown', ctrlKey: true });

    expect(activeTab()).toBe('notification.tab.notice');
  });

  it('시스템 정보는 눌러야 읽는다 — 알림을 보러 온 사람이 볼 일이 거의 없다', async () => {
    show();
    expect(loadSystemInfo).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /system\.info/ }));

    await waitFor(() => expect(loadSystemInfo).toHaveBeenCalledTimes(1));
    // 배포를 특정하는 것은 버전이 아니라 리비전이다 — 태그를 붙이기 전까지 버전은 같다.
    await waitFor(() => expect(screen.queryByText('75743cb (main)')).not.toBeNull());
  });

  it('빌드 정보가 없으면 모른다고 말한다 — 빈 칸으로 두면 잘못된 단서가 된다', async () => {
    // IDE 가 클래스만 컴파일해 띄운 서버다. build-info.properties 는 Gradle 이 만든다.
    loadSystemInfo.mockResolvedValue({
      ...FULL,
      version: null,
      commit: null,
      branch: null,
      buildTime: null,
    });
    show();

    fireEvent.click(screen.getByRole('button', { name: /system\.info/ }));

    await waitFor(() =>
      expect(screen.getAllByText('system.info.unknown').length).toBeGreaterThanOrEqual(3),
    );
    // 기동 시각은 서버가 언제나 아는 값이라 여기서도 남아 있어야 한다.
    expect(screen.queryByText('2026-08-29 22:35')).not.toBeNull();
  });

  it('프로필이 비면 default 로 도는 중이라고 말한다', async () => {
    loadSystemInfo.mockResolvedValue({ ...FULL, profiles: [] });
    show();

    fireEvent.click(screen.getByRole('button', { name: /system\.info/ }));

    await waitFor(() =>
      expect(screen.queryByText('system.info.profile.default')).not.toBeNull(),
    );
  });
});
