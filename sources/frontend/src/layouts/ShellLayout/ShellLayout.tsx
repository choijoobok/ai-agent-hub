import { useCallback, useEffect, useMemo, useState } from 'react';
import { App, Layout, theme } from 'antd';
import {
  TAB_LIMIT,
  tabTitle,
  useTabStore,
  type OpenTarget,
  type WorkTab,
} from '@/shared/stores/tabStore';
import { useSessionCleanup } from '@/modules/system/public/auth/session';
import { useUiStore } from '@/shared/stores/uiStore';
import { useSessionExpiry } from '@/modules/system/public/auth/useSessionExpiry';
import { ExpiryWarningModal } from '@/modules/system/public/auth/components/ExpiryWarningModal';
import { SettingsModal } from '@/modules/system/public/settings/SettingsModal';
import { ProfileModal } from '@/modules/system/public/profile/ProfileModal';
import { NotificationModal } from '@/modules/system/public/notification/NotificationModal';
import { NoticePopupModal } from '@/modules/system/public/notice/NoticePopupModal';
import { getUnreadCount } from '@/modules/system/public/notification/api/notificationApi';
import { handOff } from '@/shared/routes/handoffStore';
import { rememberRecentMenu } from '@/shared/authz/recentMenus';
import { useFavoriteStore } from '@/shared/favorite/favoriteStore';
import { useFrequentStore } from '@/shared/menustat/frequentStore';
import type { MenuNode } from '@/shared/authz/menuTree';
import { useAuthStore } from '@/shared/stores/authStore';
import { MenuEntryModal } from '@/layouts/ShellLayout/MenuEntryModal';
import type { EntryTab } from '@/layouts/ShellLayout/entryTabs';
import { useShellHotkeys } from '@/layouts/ShellLayout/useShellHotkeys';
import { label } from '@/shared/i18n/label';
import { AutoHideEdge } from '@/layouts/ShellLayout/AutoHideEdge';
import { ShellBreadcrumb } from '@/layouts/ShellLayout/ShellBreadcrumb';
import { ShellHeader } from '@/layouts/ShellLayout/ShellHeader';
import { ShellSider } from '@/layouts/ShellLayout/ShellSider';
import { ShellTabBar } from '@/layouts/ShellLayout/ShellTabBar';
import { TabPanes } from '@/layouts/ShellLayout/TabPanes';
import { allowNextUnload, useUnsavedGuard } from '@/layouts/ShellLayout/useUnsavedGuard';
import { useShellNavigation } from '@/layouts/ShellLayout/useShellNavigation';
import { useTabHotkeys } from '@/layouts/ShellLayout/useTabHotkeys';
import { openMenuInBrowser } from '@/layouts/ShellLayout/browserMenuOpen';
import { MSG } from '@/shared/i18n/messageKeys';
import { thinScrollbar } from '@/shared/theme/scrollbar';

/**
 * 메인 셸 — frontend/tabs.md.
 *
 * <pre>
 * 헤더
 * 사이더 | 탭바
 *        | 브레드크럼
 *        | 콘텐츠 (활성 탭의 화면)
 * </pre>
 *
 * <p><b>푸터가 없다.</b> 백오피스는 세로 공간이 그리드에 쓰이고 멀티탭이라 화면을 바꿔도
 * 셸이 남는다 — 항상 보이는 띠 하나가 모든 화면에서 그리드 몇 행을 가져간다(frontend/tabs.md).
 *
 * <p><b>이 컴포넌트가 갖는 것은 "묻는 일" 뿐이다.</b> 탭 상한 확인과 미저장 확인 — 둘 다
 * 스토어에 넣을 수 없다(모달을 띄우는 것은 UI 의 일이다). 나머지는 전부 아래로 위임한다.
 */
export function ShellLayout() {
  const { token } = theme.useToken();
  const { modal } = App.useApp();
  const cleanup = useSessionCleanup();

  /*
    <b>브라우저가 하는 이탈</b>은 아래의 확인들이 볼 수 없다 — 새로고침 · 주소창 · 브라우저
    탭 닫기 · 뒤로 가기. 그 넷은 이 훅이 브라우저에게 물어 달라고 요청한다.

    <b>돌려받은 값을 구독으로 쓴다.</b> 예전에는 이 사실을 `useTabStore.getState()` 로
    렌더 도중 한 번 읽었는데, 그러면 값이 바뀌어도 셸이 다시 그려지지 않아 <b>낡은 값을
    들고 있게</b> 된다. 지금까지 안 틀린 것은 설정 모달을 <i>여는 클릭</i> 이 마침
    리렌더를 일으켰기 때문이고, 그것은 규칙이 아니라 우연이다.
  */
  const hasUnsaved = useUnsavedGuard();

  const { index, openMenu, openMenuInNewTab, selectTab, replaceOldest, syncUrlToActiveTab } =
    useShellNavigation();
  const closeTab = useTabStore((state) => state.close);

  const expiresInSeconds = useAuthStore((state) => state.expiresInSeconds);
  const onExpire = useCallback(() => {
    // 연장하지 않았다 — 정식 로그아웃으로 끝낸다. 서버 세션은 이미 만료됐지만
    // 호출은 그대로 한다: 실패해도 프론트 정리는 반드시 돈다.
    void cleanup();
  }, [cleanup]);
  const expiry = useSessionExpiry({ expiresInSeconds, onExpire });

  /**
   * 닫기 계열의 <b>유일한 입구</b>. frontend/tabs.md 은 미저장 변경이 있을 때만 묻고 없으면 묻지
   * 말라고 한다 — 그 판정이 경로마다 있으면 어딘가는 묻지 않고 닫는다.
   */
  const closeWithConfirm = useCallback(
    (keys: string[]) => {
      const { tabs, dirtyKeys } = useTabStore.getState();
      const doomed = tabs.filter((tab) => tab.closable && keys.includes(tab.key));
      const dirty = doomed.filter((tab) => dirtyKeys.includes(tab.key));

      const closeAll = () => {
        doomed.forEach((tab) => closeTab(tab.key));
        // 닫은 화면을 주소창이 계속 가리키면, 새로고침이 그 탭을 되살린다.
        syncUrlToActiveTab();
      };
      if (dirty.length === 0) {
        closeAll();
        return;
      }
      modal.confirm({
        title: label(MSG.TAB_DIRTY_TITLE),
        // 어느 탭인지 나열한다 — 무엇을 잃는지 모르면 확인의 의미가 없다(frontend/tabs.md).
        content: label(MSG.TAB_DIRTY_CLOSE_CONTENT, {
          tabs: dirty.map((tab) => tabTitle(tab, label)).join(', '),
        }),
        okText: label(MSG.COMMON_CLOSE),
        cancelText: label(MSG.COMMON_CANCEL),
        onOk: closeAll,
      });
    },
    [closeTab, modal, syncUrlToActiveTab],
  );

  /**
   * 로고 클릭 — <b>탭을 닫는 것이 아니라 앱을 다시 띄운다</b>(frontend/tabs.md).
   *
   * <p>탭만 닫으면 <b>브라우저에 쌓인 것은 그대로 남는다</b>. 비활성 탭을 언마운트하지 않는
   * 설계라(frontend/tabs.md) 하루를 쓰면 DOM·리스너·캐시가 계속 늘고, 그때 사람이 원하는 것은
   * *"탭 정리"* 가 아니라 <b>깨끗한 시작</b>이다 — 그 수단이 새로고침이며 이 자리가 그
   * 입구다.
   *
   * <p><b>도메인 루트로 간다.</b> 지금 주소로 다시 부르면 그 화면이 탭으로 되살아나
   * (§4 의 URL → 탭 매핑) <i>초기 화면</i> 이 아니게 된다.
   *
   * <p><b>확인은 그대로다.</b> 좌상단 한 번의 오클릭으로 일어나는 일이라는 사실이 바뀌지
   * 않았고, 잃는 것은 오히려 더 크다 — 미저장 변경이 있으면 어느 탭인지 나열하고 묻는다.
   */
  const reloadShell = useCallback(() => {
    const { tabs, dirtyKeys } = useTabStore.getState();
    const dirty = tabs.filter((tab) => dirtyKeys.includes(tab.key));
    // 되돌아올 자리가 없는 이동이라 히스토리에 남기지 않는다 — 뒤로 가면 방금 버린
    // 상태의 주소로 돌아가는데, 그 상태는 이미 없다.
    //
    // <b>브라우저에게는 묻지 말라고 한다</b> — 어느 탭인지 이름까지 대며 이미 물었고,
    // 그 뒤의 브라우저 창은 같은 결정을 무엇을 잃는지도 못 밝힌 채 다시 시킨다.
    const go = () => {
      allowNextUnload();
      window.location.replace('/admin');
    };

    if (dirty.length === 0) {
      go();
      return;
    }
    modal.confirm({
      title: label(MSG.TAB_DIRTY_TITLE),
      content: label(MSG.TAB_DIRTY_RELOAD_CONTENT, {
        tabs: dirty.map((tab) => tabTitle(tab, label)).join(', '),
      }),
      okText: label(MSG.SHELL_RELOAD),
      cancelText: label(MSG.COMMON_CANCEL),
      onOk: go,
    });
  }, [modal]);

  const logout = useCallback(() => {
    const { tabs, dirtyKeys } = useTabStore.getState();
    const dirty = tabs.filter((tab) => dirtyKeys.includes(tab.key));
    if (dirty.length === 0) {
      void cleanup();
      return;
    }
    modal.confirm({
      title: label(MSG.TAB_DIRTY_TITLE),
      content: label(MSG.TAB_DIRTY_LOGOUT_CONTENT, {
        tabs: dirty.map((tab) => tabTitle(tab, label)).join(', '),
      }),
      okText: label(MSG.SHELL_LOGOUT),
      cancelText: label(MSG.COMMON_CANCEL),
      onOk: () => cleanup(),
    });
  }, [cleanup, modal]);

  /**
   * 상한 확인 — frontend/packages.md. <b>클릭한 그 자리에서 묻는다.</b>
   *
   * <p>처음에는 "대기 중인 열기" 를 상태로 두고 효과에서 모달을 띄웠는데, 확인 뒤 스토어가
   * 바뀌면서 효과가 한 번 더 돌아 <b>같은 모달이 두 번</b> 떴다. 묻는 일에는 상태가 필요 없다 —
   * 이미 이벤트 핸들러 안이다.
   *
   * <p>닫을 대상을 사용자가 고르게 하지 않는다. 20개 목록에서 하나를 고르는 UI 는 이 순간에
   * 과하고, 원하는 탭이 따로 있으면 취소하고 그것을 직접 닫으면 된다.
   */
  const confirmLimit = useCallback(
    (target: OpenTarget, victim: WorkTab) => {
      const dirty = useTabStore.getState().dirtyKeys.includes(victim.key);
      const name = tabTitle(victim, label);
      modal.confirm({
        title: label(MSG.TAB_LIMIT_TITLE, { limit: TAB_LIMIT }),
        // 대상 탭의 제목을 보여준다 — 무엇이 닫히는지 모르면 확인의 의미가 없다.
        // dirty 면 한 줄 더 붙이되 확인을 두 번 받지는 않는다.
        content: label(dirty ? MSG.TAB_LIMIT_CONTENT_DIRTY : MSG.TAB_LIMIT_CONTENT, { name }),
        okText: label(MSG.TAB_LIMIT_OK),
        cancelText: label(MSG.COMMON_CANCEL),
        // 취소하면 새 탭을 열지 않는다 — 현재 화면 그대로다.
        onOk: () => replaceOldest(target, victim.key),
      });
    },
    [modal, replaceOldest],
  );

  // 자주 쓰는 메뉴를 세는 함수 — 아래 handleOutcome 이 쓴다(sys/menu.md).
  const countMenu = useFrequentStore((state) => state.count);

  /**
   * 열기의 결과를 받는 자리 — 상한에 걸렸으면 그때 묻는다(frontend/packages.md).
   *
   * <p>사이더 클릭 · 우클릭 · <b>검색 결과</b> 셋이 같은 문을 지난다. 경로마다 따로 두면
   * 어느 하나가 상한을 조용히 무시한다.
   */
  const handleOutcome = useCallback(
    (
      outcome: ReturnType<typeof openMenu>,
      node: { menuCd: string; menuNm: string; menuUrl: string | null },
    ) => {
      // 메뉴를 여는 모든 경로가 이 문을 지난다 — 최근 목록을 여기서 적는다(frontend/tabs.md).
      // 탭바에서 탭을 고르는 것은 여기로 오지 않으므로 세지 않는다. 이미 열려 있으니
      // 찾는 수고가 없다.
      rememberRecentMenu(node.menuCd);
      // 자주 쓰는 메뉴도 <b>같은 문</b>에서 센다(sys/menu.md) — 경로마다 세면 어느 하나가
      // 빠지고, 빠진 경로로만 여는 사용자의 목록은 영영 비어 있다.
      countMenu(node.menuCd);

      if (outcome?.type === 'limit' && node.menuUrl) {
        confirmLimit(
          { menuCd: node.menuCd, pathname: node.menuUrl, titleKey: node.menuNm },
          outcome.victim,
        );
      }
    },
    [confirmLimit, countMenu],
  );

  /**
   * 브라우저 창을 하나 더 열어 그 메뉴를 띄운다 — frontend/tabs.md.
   *
   * <p><b>여는 것은 경로 하나뿐이다.</b> 셸은 이미 <i>URL → 탭</i> 을 갖고 있어
   * (`useShellNavigation`) 새 창이 그 경로로 뜨면 그 메뉴가 열린 채 시작한다 — 넘길
   * 상태도, 창끼리 주고받을 메시지도 없다.
   *
   * <p><b>`noopener` 다.</b> 없으면 새 창이 `window.opener` 로 이쪽을 만질 수 있다.
   */
  const openInBrowser = useCallback(
    (node: { menuCd: string; menuUrl: string | null }) => openMenuInBrowser(node, countMenu),
    [countMenu],
  );

  /*
    즐겨찾기를 셸이 뜰 때 한 번 읽는다 — 원본은 DB 다(sys/menu.md).

    사이더 · 탭바 · 진입 모달 셋이 같은 목록을 보므로 <b>읽는 자리도 하나</b>여야 한다.
    각자 읽으면 한쪽에서 별을 눌렀을 때 나머지가 옛 목록을 그린다.
  */
  const loadFavorites = useFavoriteStore((state) => state.load);
  useEffect(() => {
    void loadFavorites();
  }, [loadFavorites]);

  /**
   * 진입 모달을 여는 경로 — frontend/tabs.md. `null` 이면 닫혀 있다.
   *
   * <p>탭과 질의를 <b>한 덩어리로</b> 갖는다. 따로 두면 <i>"검색 탭으로 열되 지난 질의를
   * 그대로"</i> 같은 어중간한 상태가 생기고, 무엇을 보고 있는지 설명할 수 없게 된다.
   */
  const [entry, setEntry] = useState<{ tab: EntryTab; query: string } | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);

  /**
   * 헤더 벨의 미읽음 — frontend/tabs.md. **내 알림의 미읽음뿐**이다.
   *
   * <p><b>주기 조회를 하지 않는다.</b> 폴링을 붙이면 사람이 보고 있지 않은 시간에도 요청이
   * 계속 나가는데, 알림은 <i>지금 당장</i> 알아야 하는 것이 아니다(급한 것은 알림이 아니라
   * 화면이 막는다 — screen/_standalone/README.md). 맞춰지는 시점이 셋이다:
   *
   * <ul>
   *   <li>셸이 설 때 한 번</li>
   *   <li>모달을 열어 <i>내 알림</i> 탭이 조회할 때</li>
   *   <li>읽음·모두 읽음이 목록을 되돌릴 때</li>
   * </ul>
   *
   * <p>그래서 <b>탭을 열지 않으면 숫자가 낡는다</b> — 그것이 감수한 것이며, 새로고침이
   * 곧 갱신이다.
   */
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    let alive = true;
    getUnreadCount().then(
      (count) => {
        if (alive) setUnreadCount(count);
      },
      () => {
        /*
          **삼킨다.** 배지가 못 뜬 것이 셸을 막지 않는다 — 공지 팝업과 같은 방향이며
          (NP-02) 실패는 서버 로그에 남는다.
        */
      },
    );
    return () => {
      alive = false;
    };
  }, []);
  const [expiryDismissed, setExpiryDismissed] = useState(false);

  /**
   * 자동 숨기기 — frontend/tabs.md. <b>탭바와 브레드크럼은 대상이 아니다.</b>
   *
   * <p>탭바가 사라지면 지금 어느 화면인지 알 수 없고, 브레드크럼은 그 화면이 어디에
   * 있는지를 말한다 — 둘은 <b>보고 있는 동안 계속 필요한 것</b>이라 켜고 끌 대상이 아니다.
   * 감추는 것은 <b>가는 길</b>(사이더)과 <b>세션 조작</b>(헤더)이며, 둘 다 쓸 때만 있으면 된다.
   *
   * <p><b>사이더는 접힘 상태를 그대로 갖고 나온다.</b> 헤더 왼쪽 버튼이 정한 너비가
   * 240 이든 80 이든 그대로이며, 자동 숨기기는 <b>자리를 비우는 축</b>이라 그 둘이 섞이지
   * 않는다.
   */
  const headerAutoHide = useUiStore((state) => state.headerAutoHide);
  const siderAutoHide = useUiStore((state) => state.siderAutoHide);

  useTabHotkeys(selectTab);

  /**
   * 이미 그 탭으로 열려 있으면 그대로 둔다.
   *
   * <p>모달 안에서 치는 동안에도 `Shift` 는 눌린다 — 대문자 하나 치려다 두 번이 되면
   * <b>방금 친 검색어가 날아간다</b>.
   */
  const openEntry = useCallback(
    (tab: EntryTab) =>
      setEntry((current) => (current?.tab === tab ? current : { tab, query: '' })),
    [],
  );
  useShellHotkeys(openEntry);

  const openFromEntry = useCallback(
    (node: MenuNode) => {
      setEntry(null);
      handleOutcome(openMenu(node), node);
    },
    [openMenu, handleOutcome],
  );


  const openNode = useCallback(
    (node: MenuNode) => handleOutcome(openMenu(node), node),
    [handleOutcome, openMenu],
  );
  const openNodeInNewTab = useCallback(
    (node: MenuNode) => handleOutcome(openMenuInNewTab(node), node),
    [handleOutcome, openMenuInNewTab],
  );
  const openSettings = useCallback(() => setSettingsOpen(true), []);
  const openProfile = useCallback(() => setProfileOpen(true), []);
  const openNotification = useCallback(() => setNotificationOpen(true), []);

  /*
    <b>엘리먼트를 붙든다.</b> 감쌀 때와 아닐 때 <b>같은 엘리먼트</b>여야 한다 — 갈래마다 따로
    쓰면 설정을 켜고 끌 때 React 가 다른 자리로 보고 다시 만들어, 사이더 트리의 펼침 상태가
    초기화된다.

    <p><b>그 요구를 {@code useMemo} 로 못 박는 것은 전환 비용 때문이다.</b> 탭 전환은 셸을
    두 번 다시 그린다({@code navigate} 로 location 이 바뀌고, 그 뒤 효과가 {@code activate} 를
    부른다). 그때 사이더는 <b>바뀔 이유가 없는데도</b> 함께 그려졌다 — 권한 메뉴 트리 전체를
    antd Menu 로 펼치는 컴포넌트라 한 번이 36~63ms 이고, 렌더가 둘이니 전환마다 80~115ms 였다
    (2026-08-31 Profiler 실측. 그때 정작 탭 화면은 0.2~2.5ms 였다).

    <p><b>같은 엘리먼트 참조를 다시 주면 React 는 그 subtree 를 건너뛴다.</b> {@code memo} 를
    {@code ShellSider} 에 거는 대신 여기서 붙드는 것은, 이 자리가 이미 엘리먼트 동일성을
    요구하고 있어 <b>두 요구가 한 곳에서 지켜지기</b> 때문이다.

    <p><b>핸들러를 인라인으로 두면 무의미하다</b> — 화살표가 매 렌더 새것이면 위의 useMemo 가
    매번 새 엘리먼트를 낸다. 그래서 다섯을 모두 {@code useCallback} 으로 고정했다.
  */
  const header = useMemo(
    () => (
      <ShellHeader
        onLogoClick={reloadShell}
        onLogout={logout}
        onSettings={openSettings}
        onProfile={openProfile}
        onNotification={openNotification}
        unreadCount={unreadCount}
      />
    ),
    [reloadShell, logout, openSettings, openProfile, openNotification, unreadCount],
  );
  const sider = useMemo(
    () => (
      <ShellSider
        index={index}
        onOpen={openNode}
        onOpenInNewTab={openNodeInNewTab}
        onOpenInBrowser={openInBrowser}
        onOpenEntry={openEntry}
      />
    ),
    [index, openNode, openNodeInNewTab, openInBrowser, openEntry],
  );

  return (
    // 높이가 뷰포트에 고정된다 — minHeight 가 아니라 height 다.
    //
    // 그리드 화면은 남는 높이를 **채워야** 한다(frontend/grid.md 의 크롬은 헤더·본문·푸터 셋이고
    // 본문만 늘어난다). minHeight 로 두면 콘텐츠 영역의 높이가 auto 라 그 안의
    // `height: 100%` 가 0 으로 풀리고, **그리드가 줄 하나 없이 접혀 보인다** —
    // 첫 그리드 화면(screen/admin/system/code/requirements.md)에서 실제로 그렇게 렌더됐다.
    // `position: relative` 가 자동 숨기기의 기준면이다 — 헤더는 이 면의 위 가장자리에서,
    // 사이더는 **한 겹 안쪽** 면의 왼쪽 가장자리에서 나온다. 사이더가 이 면을 쓰면 헤더까지
    // 덮는다.
    <Layout style={{ height: '100vh', position: 'relative' }}>
      {headerAutoHide ? (
        <AutoHideEdge edge="top">{header}</AutoHideEdge>
      ) : (
        header
      )}
      {/* flex 자식이 줄어들 수 있어야 한다 — minHeight: 0 이 없으면 콘텐츠가 넘쳐 잘린다. */}
      <Layout style={{ minHeight: 0, position: 'relative' }}>
        {siderAutoHide ? <AutoHideEdge edge="left">{sider}</AutoHideEdge> : sider}
        <Layout style={{ minHeight: 0 }}>
          <ShellTabBar onSelect={selectTab} onClose={closeWithConfirm} />
          <ShellBreadcrumb index={index} />
          <Layout.Content
            style={{
              flex: 1,
              minHeight: 0,
              // 8 이다 — 16 은 탭 안쪽이 헐거워 보였다(2026-08-29 실물).
              padding: 8,
              background: token.colorBgLayout,
              overflow: 'auto',
              ...thinScrollbar(token),
            }}
          >
            <TabPanes />
          </Layout.Content>
        </Layout>
      </Layout>

      <MenuEntryModal
        open={entry !== null}
        tab={entry?.tab ?? 'search'}
        query={entry?.query ?? ''}
        index={index}
        onOpen={openFromEntry}
        onClose={() => setEntry(null)}
      />
      <NotificationModal
        open={notificationOpen}
        onClose={() => setNotificationOpen(false)}
        onUnread={setUnreadCount}
        /*
          **권한 판정을 여기서 한다.** 메뉴 트리는 유효 권한으로 이미 걸러져 오므로
          (frontend/tabs.md) 코드가 색인에 있는지 묻는 것이 곧 권한 확인이다 — 알림 모달은
          권한을 따로 물을 자리가 없다(screen/_standalone/README.md).
        */
        canOpenMenu={(menuCd) => index.byCode.get(menuCd)?.menuUrl != null}
        onOpenMenu={(menuCd, linkParam) => {
          const node = index.byCode.get(menuCd);
          if (!node) return;
          /*
            **넘겨주기를 걸고 나서 연다.** 화면 → 화면 건네주기와 같은 채널이며
            (screen/menu/requirements.md · screen/admin/system/log/access/requirements.md · screen/admin/system/batch/schedule/requirements.md) 알림이 그 셋째 입구다. 순서가 규약이다 —
            받는 쪽이 마운트하며 읽으므로 열기 전에 넣어야 한다(`handoffStore`).
          */
          if (linkParam !== null) handOff(menuCd, linkParam);
          handleOutcome(openMenu(node), node);
        }}
      />
      {/*
        **셸이 선 뒤에 뜬다**(NP-01). 여기 렌더 트리에 있는 것이 곧 그 순서이며,
        조회가 실패해도 아무것도 열지 않는다 — 셸은 정상 동작한다(NP-02).
      */}
      <NoticePopupModal onOpenNotifications={() => setNotificationOpen(true)} />
      <ProfileModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      <SettingsModal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        hasUnsaved={hasUnsaved}
      />
      <ExpiryWarningModal
        open={expiry.warning && !expiryDismissed}
        remainingMinutes={expiry.remainingMinutes}
        onExtend={() => {
          setExpiryDismissed(false);
          void expiry.extend();
        }}
        onLogout={() => void cleanup()}
        // 닫아도 시한은 그대로다 — 아무 요청이나 나가면 세션이 밀려 예고가 다시 잡힌다.
        onClose={() => setExpiryDismissed(true)}
      />
    </Layout>
  );

}
