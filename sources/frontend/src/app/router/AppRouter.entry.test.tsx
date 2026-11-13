// @feat FEAT-501
import { App } from 'antd';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRouter } from './AppRouter';
import { useAuthStore } from '@/shared/stores/authStore';
import { useMessageStore } from '@/shared/i18n/messageStore';
import { fetchMe } from '@/modules/system/public/auth/api/authApi';
import { useSessionSignals } from '@/modules/system/public/auth/useSessionSignals';

vi.mock('@/layouts/ShellLayout', () => ({ ShellLayout: () => <div>관리자 셸</div> }));
vi.mock('@/shared/routes/sampleScreens', () => ({ SAMPLE_SCREENS: { '/sample/registered': () => null } }));
vi.mock('@/modules/system/public/auth/api/authApi', () => ({ fetchMe: vi.fn() }));
vi.mock('@/modules/system/public/auth/useSessionSignals', () => ({ useSessionSignals: vi.fn() }));

describe('TC-50115/50117 ADMIN 진입 소유 경계', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ user: { userId: 'admin', userNm: '관리자', localeCd: 'ko' }, mustChangePassword: false, restored: true });
    useMessageStore.setState({ status: 'ready' });
    vi.mocked(fetchMe).mockImplementation(() => new Promise(() => {}));
    vi.mocked(useSessionSignals).mockReturnValue({ reauth: null, dismissReauth: vi.fn(), giveUp: vi.fn() });
  });

  it.each(['/admin', '/admin/system/user', '/sample/registered'])('정확 등록된 ADMIN/sample만 복원·신호·셸을 연다: %s', (path) => {
    window.history.replaceState({}, '', path);
    render(<App><AppRouter /></App>);
    expect(fetchMe).toHaveBeenCalledOnce();
    expect(useSessionSignals).toHaveBeenCalled();
    expect(screen.queryByText('관리자 셸')).not.toBeNull();
  });

  it.each(['/', '/portal/login', '/internal/chat-test', '/sample/registered/deep', '/sample/unknown', '/admin/unknown', '/login', '/password/change'])('ADMIN 로그인 상태도 다른 영역과 미등록 경로의 슬롯을 열지 않는다: %s', (path) => {
    window.history.replaceState({}, '', path);
    render(<App><AppRouter /></App>);
    expect(fetchMe).not.toHaveBeenCalled();
    expect(useSessionSignals).not.toHaveBeenCalled();
    expect(screen.queryByText('관리자 셸')).toBeNull();
    expect(window.location.pathname).toBe(path);
  });
});
