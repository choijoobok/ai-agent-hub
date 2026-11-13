// @feat FEAT-501
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PasswordTab } from '@/modules/system/public/profile/tabs/PasswordTab';
import { changePassword } from '@/modules/system/public/profile/api/profileApi';
import type { MyProfile } from '@/modules/system/public/profile/types';
import { ApiError } from '@/shared/api';
import { fetchMe } from '@/modules/system/public/auth/api/authApi';
import { useAuthStore } from '@/shared/stores/authStore';

vi.mock('@/modules/system/public/profile/api/profileApi', () => ({ changePassword: vi.fn() }));

vi.mock('@/modules/system/public/auth/api/authApi', () => ({ fetchMe: vi.fn() }));

const submit = vi.mocked(changePassword);

/**
 * 비밀번호 변경 탭 — AUTH-05 · frontend/tabs.md.
 *
 * <p><b>모달을 거치지 않고 탭만 그린다.</b> 여기서 재는 것은 전부 이 탭 안의 판정이며,
 * 모달을 세우고 탭을 눌러 들어가면 **antd 폼 검증 두 겹 위에 탭 전환이 얹혀** 느려지고
 * 실제로 5초 타임아웃에 걸렸다(2026-08-29). 모달의 일은 `ProfileModal.test.tsx` 가 잰다.
 *
 * <p>지키는 것 셋 — ① **확인 재입력은 서버까지 가지 않는다**, ② **규칙을 거절 전에 말한다**,
 * ③ **지금 무엇이 문제인지 급한 것부터 말한다**.
 */
describe('비밀번호 변경 탭', () => {
  const BASE: MyProfile = {
    userId: 'kim',
    userNm: '김철수',
    email: null,
    localeCd: 'ko',
    validFromDt: null,
    validToDt: null,
    lastLoginDtm: null,
    pwdChgDtm: '2026-06-01T01:00:00Z',
    pwdInitYn: false,
    pwdExpiresInDays: 12,
    passwordRule: { minLength: 9, requireCharsetKinds: 3, reuseBlockCount: 3, expireDays: 90 },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ user: { userId: 'kim', userNm: '김철수', localeCd: 'ko' }, mustChangePassword: true });
    vi.mocked(fetchMe).mockResolvedValue({ user: { userId: 'kim', userNm: '김철수', localeCd: 'ko' }, mustChangePassword: false, menus: [], permissions: [], menuTree: [], cacheVersions: {}, expiresInSeconds: 900 });
  });

  const show = (profile: MyProfile = BASE) =>
    render(
      <QueryClientProvider client={new QueryClient()}><App>
        <PasswordTab profile={profile} />
      </App></QueryClientProvider>,
    );

  const fill = (current: string, next: string, confirm: string) => {
    const fields = screen.getAllByLabelText(/profile\.pwd\./);
    fireEvent.change(fields[0], { target: { value: current } });
    fireEvent.change(fields[1], { target: { value: next } });
    fireEvent.change(fields[2], { target: { value: confirm } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.pwd.submit' }));
  };

  it('확인 재입력이 다르면 서버까지 가지 않는다 — 서버는 이 값을 받지도 않는다', async () => {
    show();

    fill('OldPassw0rd!', 'NewPassw0rd!', 'Different0!');

    await waitFor(() => expect(screen.queryByText('profile.pwd.confirmMismatch')).not.toBeNull());
    expect(submit).not.toHaveBeenCalled();
  });

  it('맞으면 현재와 새 값 둘만 보낸다 — 확인은 화면의 것이다', async () => {
    submit.mockResolvedValue(undefined);
    show();

    fill('OldPassw0rd!', 'NewPassw0rd!', 'NewPassw0rd!');

    await waitFor(() => expect(submit).toHaveBeenCalledWith('OldPassw0rd!', 'NewPassw0rd!'));
  });

  it('성공하면 제한 세션을 푼다 — 유도의 끝에는 문이 열려야 한다', async () => {
    submit.mockResolvedValue(undefined);
    show();

    fill('OldPassw0rd!', 'NewPassw0rd!', 'NewPassw0rd!');

    await waitFor(() => expect(useAuthStore.getState().mustChangePassword).toBe(false));
  });

  it('규칙을 거절 전에 말한다 — 서버 문구는 정책 값을 나를 수 없다', () => {
    show();

    // 값 치환이 없는 테스트 환경에서는 키가 그대로 보인다 — 자리가 있다는 것이 판정이다.
    expect(screen.queryByText('profile.pwd.rule')).not.toBeNull();
    expect(screen.queryByText('profile.pwd.rule.reuse')).not.toBeNull();
  });

  it('재사용 금지가 0 이면 없는 규칙을 안내하지 않는다', () => {
    show({ ...BASE, passwordRule: { ...BASE.passwordRule, reuseBlockCount: 0 } });

    expect(screen.queryByText('profile.pwd.rule.reuse')).toBeNull();
  });

  it('임시 비밀번호가 만료 안내보다 앞선다 — 그 상태에서는 남은 날이 뜻이 없다', () => {
    // 체인 #5 의 독립 조건이라 만료 계산과 무관하다(backend/security.md).
    show({ ...BASE, pwdInitYn: true, pwdExpiresInDays: 12 });

    expect(screen.queryByText('profile.pwd.temporary')).not.toBeNull();
    expect(screen.queryByText('profile.pwd.expiresIn')).toBeNull();
  });

  it('이미 지났으면 만료라고 말한다 — 0 과 음수가 다른 문구다', () => {
    show({ ...BASE, pwdExpiresInDays: -3 });

    expect(screen.queryByText('profile.pwd.expired')).not.toBeNull();
  });
  it.each([['PASSWORD_CHANGED_REAUTH_REQUIRED', true], ['OTHER_FAILURE', false]])('암호 확정 오류와 일반503을 구별하며 재전송하지 않는다: %s', async (code, cleared) => {
    submit.mockRejectedValue(new ApiError({ code, status: 503, message: '저장 실패' }));
    show();
    fill('OldPassw0rd!', 'NewPassw0rd!', 'NewPassw0rd!');
    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(useAuthStore.getState().user === null).toBe(cleared));
    expect(fetchMe).not.toHaveBeenCalled();
    const inputs = screen.getAllByLabelText<HTMLInputElement>(/profile\.pwd\./);
    expect(inputs[0].value).toBe(cleared ? '' : 'OldPassw0rd!');
  });

  it('저장이 성공해도 getMe가 제한 상태를 확인하면 화면이 제한을 풀지 않는다', async () => {
    submit.mockResolvedValue(undefined);
    vi.mocked(fetchMe).mockResolvedValue({ user: { userId: 'kim', userNm: '김철수', localeCd: 'ko' }, mustChangePassword: true, menus: [], permissions: [], menuTree: [], cacheVersions: {}, expiresInSeconds: 900 });
    show();
    fill('OldPassw0rd!', 'NewPassw0rd!', 'NewPassw0rd!');
    await waitFor(() => expect(fetchMe).toHaveBeenCalledOnce());
    expect(useAuthStore.getState().mustChangePassword).toBe(true);
  });

});
