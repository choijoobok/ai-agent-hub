// @feat FEAT-501
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login } from './authApi';
import { AUTH_ENDPOINTS } from './endpoints';
import { get, post } from '@/shared/api';

vi.mock('@/shared/api', () => ({ get: vi.fn(), post: vi.fn() }));

describe('TC-50115 로그인 새 쿠키 첫 사용', () => {
  beforeEach(() => vi.clearAllMocks());
  it('발급 응답 대신 getMe가 확인한 현재 상태를 반환한다', async () => {
    const issued = { mustChangePassword: false };
    const confirmed = { mustChangePassword: true };
    vi.mocked(post).mockResolvedValue(issued);
    vi.mocked(get).mockResolvedValue(confirmed);
    expect(await login({ loginId: 'admin', password: 'secret' })).toBe(confirmed);
    expect(post).toHaveBeenCalledExactlyOnceWith(AUTH_ENDPOINTS.login, { loginId: 'admin', password: 'secret' });
    expect(get).toHaveBeenCalledExactlyOnceWith(AUTH_ENDPOINTS.getMe);
    expect(vi.mocked(post).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(get).mock.invocationCallOrder[0]);
  });
  it('첫 사용 실패는 로그인 실패로 남기며 원 로그인 POST를 자동 재전송하지 않는다', async () => {
    vi.mocked(post).mockResolvedValue({});
    vi.mocked(get).mockRejectedValue(new Error('첫 사용 거부'));
    await expect(login({ loginId: 'admin', password: 'secret' })).rejects.toThrow('첫 사용 거부');
    expect(post).toHaveBeenCalledOnce();
  });
});
