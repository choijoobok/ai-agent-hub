import { useCallback, useEffect, useMemo, useRef } from 'react';
import { App } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/shared/stores/authStore';
import { useTabStore, type OpenOutcome, type OpenTarget } from '@/shared/stores/tabStore';
import { findByUrl, indexMenuTree, type MenuIndex, type MenuNode } from '@/shared/authz/menuTree';
import { setActiveMenuCode } from '@/shared/api';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';

export interface ShellNavigation {
  index: MenuIndex;
  /** 트리·검색·즐겨찾기가 모두 이것을 부른다. `limit` 이 오면 부르는 쪽이 확인을 받는다. */
  openMenu: (node: MenuNode) => OpenOutcome | null;
  /** frontend/tabs.md — 이미 열려 있어도 항상 새 인스턴스. */
  openMenuInNewTab: (node: MenuNode) => OpenOutcome | null;
  selectTab: (key: string) => void;
  /**
   * 탭을 닫아 활성 탭이 바뀐 뒤 <b>주소를 맞춘다</b>.
   *
   * <p>닫기는 스토어만 건드리므로 주소창은 <b>방금 닫은 화면</b>을 가리킨 채 남는다. 그
   * 상태로 새로고침하면 아래 URL → 탭 효과가 <b>그 경로를 다시 열어</b>, 닫은 탭이 되살아난다.
   */
  syncUrlToActiveTab: () => void;
  /** 상한 확인을 받은 뒤 — 가장 먼저 연 탭을 닫고 연다(frontend/packages.md). */
  replaceOldest: (target: OpenTarget, victimKey: string) => void;
}

/**
 * URL 과 탭을 잇는 <b>단 하나의 자리</b> — frontend/packages.md.
 *
 * <pre>
 * 탭 전환    → navigate(pathname, {state:{tabKey}})  — 방문 순서가 history 에 쌓인다
 * 뒤로/앞으로 → location 이 바뀌고 state.tabKey 로 그 탭을 활성화한다
 * </pre>
 *
 * <p><b>동기화 지점이 하나여야 한다.</b> 클릭 핸들러마다 `activate` 와 `navigate` 를 각자
 * 부르면 뒤로가기로 들어온 전환에서 한쪽이 빠지고, <i>"주소는 바뀌었는데 탭은 그대로"</i> 가
 * 생긴다. 그래서 <b>쓰는 쪽은 언제나 `navigate` 만</b> 하고, 탭 활성화는 아래 효과 하나가 한다.
 *
 * <p><b>탭 안의 화면 전환에는 뒤로가기가 동작하지 않는다</b>(frontend/packages.md 가 감수한 제약) —
 * 뒤로가기가 탭 이동에 배정됐기 때문이다. 목록으로 돌아가는 것은 화면의 버튼이 한다.
 */
export function useShellNavigation(): ShellNavigation {
  const menuTree = useAuthStore((state) => state.menuTree);
  const tabs = useTabStore((state) => state.tabs);
  const activeKey = useTabStore((state) => state.activeKey);
  const activate = useTabStore((state) => state.activate);

  const location = useLocation();
  const navigate = useNavigate();
  const { message } = App.useApp();

  const index = useMemo(() => indexMenuTree(menuTree), [menuTree]);

  /*
    <b>{@code navigate} 를 참조로 붙든다.</b>

    react-router 의 {@code useNavigate} 는 <b>지금 pathname 을 deps 에 물고 있다</b>
    (상대 경로를 지금 위치에서 풀어야 하니 그 설계가 맞다). 그래서 <b>이동할 때마다 새
    함수</b>이고, 그것을 deps 에 실은 콜백이 줄줄이 새것이 된다. 사이더까지 내려가면
    {@code actions} → {@code items} 가 다시 만들어져 <b>메뉴 트리 전체가 새로 세워진다</b> —
    탭 전환 한 번이 그것에 23ms 를 쓰고 있었다(2026-08-31 실측).

    <p><b>우리는 상대 경로를 쓰지 않는다.</b> 넘기는 것이 언제나 절대 경로다
    ({@code menuUrl} · {@code tab.pathname}) — <i>지금 위치</i>가 결과를 바꾸지 않으므로
    한 박자 지난 함수를 불러도 같은 곳으로 간다. 그래서 최신 값을 참조에 담고 콜백은 그것만
    본다.

    <p><b>아래 URL → 탭 효과는 {@code navigate} 를 그대로 쓴다</b> — 그쪽은 이미
    {@code location.key} 로 다시 도는 자리라 함수의 신선도가 보장된다.
  */
  const navigateRef = useRef(navigate);
  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  const go = useCallback((outcome: OpenOutcome, pathname: string): OpenOutcome => {
    if (outcome.type !== 'limit') {
      void navigateRef.current(pathname, { state: { tabKey: outcome.key } });
    }
    return outcome;
  }, []);

  const openMenu = useCallback(
    (node: MenuNode) =>
      // 구조 노드는 열 수 없다 — URL 과 자식은 배타다(backend/security.md).
      node.menuUrl ? go(useTabStore.getState().open(targetOf(node)), node.menuUrl) : null,
    [go],
  );

  const openMenuInNewTab = useCallback(
    (node: MenuNode) =>
      node.menuUrl
        ? go(useTabStore.getState().openNewInstance(targetOf(node)), node.menuUrl)
        : null,
    [go],
  );

  const replaceOldest = useCallback(
    (target: OpenTarget, victimKey: string) => {
      go(useTabStore.getState().closeAndOpen(victimKey, target), target.pathname);
    },
    [go],
  );

  const selectTab = useCallback((key: string) => {
    // <b>스토어에서 지금 값을 읽는다.</b> 구독한 값을 deps 에 실으면 탭이 바뀔 때마다 이
    // 함수가 새것이 되고, 받는 쪽(탭바 · 핫키)이 함께 다시 만들어진다 — 위 `go` 와 같은
    // 이유다.
    const { tabs: current, activeKey: active } = useTabStore.getState();
    const tab = current.find((candidate) => candidate.key === key);
    // 같은 탭을 다시 클릭하면 history 엔트리를 쌓지 않는다(frontend/packages.md).
    if (!tab || key === active) {
      return;
    }
    void navigateRef.current(tab.pathname, { state: { tabKey: key } });
  }, []);

  const syncUrlToActiveTab = useCallback(() => {
    const { tabs: current, activeKey: active } = useTabStore.getState();
    const tab = current.find((candidate) => candidate.key === active);
    if (!tab) {
      return;
    }
    // 방문이 아니라 정리다 — 엔트리를 쌓지 않고 지금 것을 고친다. 앞선 엔트리들은 여전히
    // 닫힌 탭을 가리키며, 그쪽은 아래 효과가 "경로로 다시 연다" 로 이미 정해 두었다.
    void navigateRef.current(tab.pathname, { replace: true, state: { tabKey: active } });
  }, []);

  // ── URL → 탭. 이 효과가 유일한 동기화 지점이다 ──────────────────────────
  useEffect(() => {
    const tabKey = (location.state as { tabKey?: string } | null)?.tabKey;
    if (tabKey && useTabStore.getState().tabs.some((tab) => tab.key === tabKey)) {
      activate(tabKey);
      return;
    }

    // state 가 없거나(딥링크·F5) 가리키던 탭이 이미 닫혔다.
    //
    // frontend/packages.md 는 죽은 엔트리를 "건너뛰고 다음으로" 로 정했으나, popstate 안에서 다시
    // history.go(-1) 을 부르면 남은 엔트리가 전부 죽었을 때 앱 밖으로 걸어 나간다.
    // 경로로 다시 맞추는 쪽이 결과가 예측 가능하고 되돌릴 수 없는 실패가 없다.
    const node = findByUrl(index, location.pathname);
    if (node) {
      const outcome = useTabStore.getState().open(targetOf(node));
      if (outcome.type !== 'limit') {
        // 주소는 이미 그 경로다. 엔트리를 하나 더 쌓지 않고 탭 키만 얹는다.
        void navigate(location.pathname, { replace: true, state: { tabKey: outcome.key } });
      }
      return;
    }

    if (location.pathname !== '/admin/system/dashboard' && location.pathname !== '/admin') {
      // 없는 메뉴이거나 권한이 없다 — 프론트는 둘을 구분하지 않는다(frontend/packages.md).
      // 조용히 대시보드로 보내면 사용자는 자기가 잘못 눌렀다고 생각한다.
      message.warning(label(MSG.SCREEN_DENIED));
    }
    void navigate('/admin/system/dashboard', { replace: true, state: { tabKey: 'dashboard' } });
    // location.key 하나로 충분하다 — 같은 경로로 다시 이동해도 값이 바뀐다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key, index]);

  // 활성 탭의 메뉴 코드가 이후 요청의 X-Menu-Code 가 된다(frontend/packages.md).
  useEffect(() => {
    const active = tabs.find((tab) => tab.key === activeKey);
    setActiveMenuCode(active?.menuCd ?? null);
  }, [activeKey, tabs]);

  return { index, openMenu, openMenuInNewTab, selectTab, replaceOldest, syncUrlToActiveTab };
}

export function targetOf(node: MenuNode): OpenTarget {
  return { menuCd: node.menuCd, pathname: node.menuUrl as string, titleKey: node.menuNm };
}
