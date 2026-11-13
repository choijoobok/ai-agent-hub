// @feat FEAT-501
import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Button, Result, Spin } from 'antd';
import { useAuthStore } from '@/shared/stores/authStore';
import { BlankLayout } from '@/layouts/BlankLayout';
import { ShellLayout } from '@/layouts/ShellLayout';
import { LoginPage } from '@/modules/system/public/auth/pages/LoginPage';
import { PasswordChangePage } from '@/modules/system/public/auth/pages/PasswordChangePage';
import { ReauthModal } from '@/modules/system/public/auth/components/ReauthModal';
import { useSessionSignals } from '@/modules/system/public/auth/useSessionSignals';
import { fetchMe } from '@/modules/system/public/auth/api/authApi';
import { useMessageStore } from '@/shared/i18n/messageStore';
import { useCodeStore } from '@/shared/code/codeStore';
import { screenFor } from '@/shared/routes/screenRegistry';
import { SAMPLE_SCREENS } from '@/shared/routes/sampleScreens';
import { staticText } from '@/locales/staticText';

/**
 * 라우트는 <b>로그인 · 비밀번호 강제 변경 · 셸</b>로 나뉜다. 메뉴별 경로는 `Routes` 에 없다.
 *
 * <p><b>탭이 라우트를 대신하기 때문</b>이다(frontend/packages.md). 경로 하나가 탭 여러 개에 대응할 수
 * 있으므로 라우터가 화면을 고르면 같은 경로의 두 인스턴스를 구분하지 못한다 — 매칭은
 * `MENU_URL` → 정적 컴포넌트 맵이 하고(`screenRegistry`), 어느 탭인지는 `pushState` 의
 * `state.tabKey` 가 갖는다.
 *
 * <p>새로고침 복원이 여기 있는 이유는 `authStore` 에 `persist` 를 붙이지 않기로 했기
 * 때문이다(frontend/packages.md) — F5 마다 `/auth/me` 한 번이 그 결정의 대가다.
 */
export function AppRouter() {
  return <BrowserRouter><EntryRouter /></BrowserRouter>;
}

/** ADMIN 복원/신호보다 먼저 소유 영역을 고른다. 미구현 영역은 닫힌 슬롯이다. */
function EntryRouter() {
  const { pathname } = useLocation();
  const admin = pathname === '/admin' || pathname === '/admin/login'
    || pathname === '/admin/password/change'
    || (pathname.startsWith('/admin/') && screenFor(pathname) !== null);
  const sample = Object.hasOwn(SAMPLE_SCREENS, pathname);
  return admin || sample ? <AdminRouter /> : <Result status="404" />;
}

function AdminRouter() {
  const restored = useAuthStore((state) => state.restored);
  const user = useAuthStore((state) => state.user);
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword);
  const messageStatus = useMessageStore((state) => state.status);

  useBoot();

  // 401 · 권한 변경은 셸 밖에서 듣는다 — 기준값을 실어 오는 로그인 응답이 셸보다 먼저다.
  const { reauth, dismissReauth, giveUp } = useSessionSignals();

  // 문구 없이 화면을 그리지 않는다. 정적 폴백으로 먼저 그리면 문구의 원본이
  // 둘이 되고, 시간이 갈수록 DB 번들과 벌어진다.
  if (messageStatus === 'failed') {
    return <BootFailure />;
  }

  // 복원 전에 판단하면 로그인한 사용자가 F5 할 때마다 로그인 화면이 한 번 번쩍인다.
  if (!restored || messageStatus === 'idle') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '20vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <>
      <Routes>
        <Route
          path="/admin/login"
          element={
            <RequireAnonymous>
              <BlankLayout>
                <LoginPage />
              </BlankLayout>
            </RequireAnonymous>
          }
        />
        <Route
          path="/admin/password/change"
          element={
            <RequireAuth>{mustChangePassword ? (
              <BlankLayout><PasswordChangePage /></BlankLayout>
            ) : <Navigate to="/admin/system/dashboard" replace />}</RequireAuth>
          }
        />
        <Route
          path="*"
          element={
            <RequireAuth>{mustChangePassword ? <Navigate to="/admin/password/change" replace /> : <ShellLayout />}</RequireAuth>
          }
        />
      </Routes>

      {reauth && user ? (
        <ReauthModal
          reason={reauth.reason}
          onRecovered={dismissReauth}
          onGiveUp={() => void giveUp()}
        />
      ) : null}
    </>
  );
}

/** 세션이 있으면 셸로 보낸다 — 로그인 화면에 다시 들어올 이유가 없다(screen/_standalone/README.md). */
function RequireAnonymous({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword);
  return user ? <Navigate to={mustChangePassword ? '/admin/password/change' : '/admin'} replace /> : <>{children}</>;
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  return user ? <>{children}</> : <Navigate to="/admin/login" replace />;
}

/**
 * 번들이 닿지 않았다.
 *
 * <p>여기 쓰이는 문구만 정적이다(`src/locales`). <b>내용이 고정적이고 몇 개뿐이라 DB 번들과
 * 벌어질 여지가 없다</b> — 전체 폴백을 두는 것과는 다른 결정이다.
 */
function BootFailure() {
  return (
    <Result
      status="warning"
      title={staticText('boot.failed.title')}
      subTitle={staticText('boot.failed.desc')}
      extra={
        <Button type="primary" onClick={() => window.location.reload()}>
          {staticText('boot.retry')}
        </Button>
      }
    />
  );
}

/**
 * 첫 진입에 한 번 — <b>세션을 확인하고 그 답에 따라 번들을 받는다</b>.
 *
 * <p>401 은 <b>실패가 아니라 답</b>이다 — 로그인하지 않았다는 뜻이므로 에러로 다루지 않는다.
 * 재인증 모달이 뜨지 않는 것도 같은 이유이며, {@code useSessionSignals} 가
 * <b>{@code authStore.user} 가 있을 때만</b> 모달을 띄우므로 여기서 따로 막을 것이 없다.
 *
 * <p><b>두 경로 모두 서버가 알려 준 버전을 확인한다</b>.
 *
 * <table>
 *   <tr><th></th><th>언어</th><th>버전 확인</th></tr>
 *   <tr><td>세션 있음</td><td>{@code LOCALE_CD}</td><td>한다 — 로그인 응답의 메시지 버전과 다를 때만 받는다</td></tr>
 *   <tr><td>세션 없음</td><td>브라우저가 정한다</td><td>한다 — 로그인 화면을 그리기 전이다</td></tr>
 * </table>
 *
 * <p>세션 확인이 먼저인 이유는 <b>언어를 아는 쪽이 그것</b>이기 때문이다. 병렬로 쏘면 세션이
 * 있는 사람은 브라우저 언어로 한 번 받고 사용자 언어로 또 받는다.
 */
function useBoot() {
  const signIn = useAuthStore((state) => state.signIn);
  const markRestored = useAuthStore((state) => state.markRestored);

  useEffect(() => {
    let cancelled = false;
    const messages = useMessageStore.getState();

    fetchMe()
      .then(async (session) => {
        if (cancelled) return;
        signIn(session);
        // 코드·메시지 모두 응답의 버전과 맞춘다. 다를 때만 번들을 다시 받으므로
        // 로컬 저장소 캐시를 무효화하면서도 매 복원에 전량을 받지는 않는다.
        useCodeStore.getState().syncWithSession(session.cacheVersions?.code);
        await messages.syncWithSession(session.user.localeCd, session.cacheVersions?.message);
      })
      .catch(async () => {
        if (cancelled) return;
        markRestored();
        await messages.load({ verify: true });
      });

    return () => {
      cancelled = true;
    };
  }, [signIn, markRestored]);
}
