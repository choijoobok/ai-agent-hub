// @feat FEAT-501
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfileModal } from '@/modules/system/public/profile/ProfileModal';
import {
  getMyLoginHistory,
  getMyProfile,
  getMyRoles,
  saveMyProfile,
} from '@/modules/system/public/profile/api/profileApi';
import type { MyProfile } from '@/modules/system/public/profile/types';
import { useAuthStore } from '@/shared/stores/authStore';

vi.mock('@/modules/system/public/profile/api/profileApi', () => ({
  getMyProfile: vi.fn(),
  saveMyProfile: vi.fn(),
  getMyLoginHistory: vi.fn(),
  getMyRoles: vi.fn(),
}));

const loadProfile = vi.mocked(getMyProfile);
const saveProfile = vi.mocked(saveMyProfile);
const loadHistory = vi.mocked(getMyLoginHistory);
const loadRoles = vi.mocked(getMyRoles);
/**
 * 프로필 모달 — frontend/tabs.md.
 *
 * <p>여기서 지키는 것은 **모달의 일**뿐이다 — ① 여는 순간 읽는 것은 하나뿐, ② 탭을 열어야
 * 그 탭이 읽는다, ③ 저장하면 헤더의 이름이 따라온다, ④ `PageUp`/`PageDown` 이 이 모달의 탭을
 * 옮기고 셸로 내려가지 않는다(frontend/tabs.md). 탭 안의 판정은 각 탭의 테스트가 잰다
 * (`PasswordTab.test.tsx`).
 *
 * <p>①·②는 조용히 깨지는 종류다. 넷을 다 읽어도 화면은 똑같이 보이고, 대부분 첫 탭만 보고
 * 닫는다는 사실은 네트워크 탭을 열어야 드러난다.
 */
describe('프로필 모달', () => {
  const PROFILE: MyProfile = {
    userId: 'kim',
    userNm: '김철수',
    email: 'kim@example.com',
    localeCd: 'ko',
    validFromDt: null,
    validToDt: null,
    lastLoginDtm: '2026-08-29T01:00:00Z',
    pwdChgDtm: '2026-06-01T01:00:00Z',
    pwdInitYn: false,
    pwdExpiresInDays: 12,
    passwordRule: { minLength: 9, requireCharsetKinds: 3, reuseBlockCount: 3, expireDays: 90 },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    loadProfile.mockResolvedValue(PROFILE);
    loadHistory.mockResolvedValue([]);
    loadRoles.mockResolvedValue({ roles: [], grants: [], businessToday: '2026-08-29' });
    useAuthStore.setState({ user: { userId: 'kim', userNm: '김철수', localeCd: 'ko' } });
  });

  const show = () =>
    render(
      <QueryClientProvider client={new QueryClient()}><App>
        <ProfileModal open onClose={vi.fn()} />
      </App></QueryClientProvider>,
    );

  /** antd 의 탭 제목은 메시지 키 그대로 나온다 — 번들이 없는 테스트에서는 그것이 이름이다. */
  const tab = (titleKey: string) => screen.getByRole('tab', { name: titleKey });

  /** antd 의 활성 탭. 셸의 탭바와 구별하려 모달 안에서 찾는다. */
  const activeTab = () => document.querySelector('.ant-modal .ant-tabs-tab-active')?.textContent;

  it('여는 순간 읽는 것은 프로필 하나다 — 대부분 첫 탭만 보고 닫는다', async () => {
    show();

    await waitFor(() => expect(loadProfile).toHaveBeenCalledTimes(1));
    expect(loadHistory).not.toHaveBeenCalled();
    expect(loadRoles).not.toHaveBeenCalled();
  });

  it('이력 탭을 열어야 그때 읽는다', async () => {
    show();
    await waitFor(() => expect(screen.queryByDisplayValue('김철수')).not.toBeNull());

    fireEvent.click(tab('profile.tab.history'));

    await waitFor(() => expect(loadHistory).toHaveBeenCalledTimes(1));
    // 역할 탭은 아직 열지 않았다 — 탭 하나가 이웃까지 끌고 오면 지연 로딩이 무의미하다.
    expect(loadRoles).not.toHaveBeenCalled();
  });

  it('PageDown 은 이 모달의 탭을 옮기고 셸로 내려가지 않는다 — frontend/tabs.md', async () => {
    const shell = vi.fn();
    window.addEventListener('keydown', shell);
    try {
      show();
      await waitFor(() => expect(activeTab()).toBe('profile.tab.info'));

      // **모달 밖에서** 난 키다 — 모달 안의 빈 곳을 눌러 포커스가 빠진 상황이며, 이 창이
      // 듣지 않으면 뒤에 가린 셸의 탭바가 조용히 움직인다.
      fireEvent.keyDown(document.body, { key: 'PageDown' });

      await waitFor(() => expect(activeTab()).toBe('profile.tab.password'));
      expect(shell).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('keydown', shell);
    }
  });

  it('PageUp 은 첫 탭에서 마지막 탭으로 돈다 — 셸의 탭바와 같은 순환이다', async () => {
    show();
    await waitFor(() => expect(activeTab()).toBe('profile.tab.info'));

    fireEvent.keyDown(document.body, { key: 'PageUp' });

    await waitFor(() => expect(activeTab()).toBe('profile.tab.roles'));
  });

  it('프로필이 오기 전에는 옮기지 않되 셸로 내려가지도 않는다 — 그 구간엔 탭이 없다', () => {
    const shell = vi.fn();
    window.addEventListener('keydown', shell);
    try {
      // 응답을 붙잡아 둔다 — 뼈대만 그려진 그 한 구간이 판정 대상이다.
      loadProfile.mockReturnValue(new Promise(() => {}));
      show();
      expect(activeTab()).toBeUndefined();

      fireEvent.keyDown(document.body, { key: 'PageDown' });

      expect(activeTab()).toBeUndefined();
      expect(shell).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('keydown', shell);
    }
  });

  it('저장하면 헤더의 이름이 따라온다 — 안 따라오면 저장이 실패한 것처럼 보인다', async () => {
    saveProfile.mockResolvedValue({ ...PROFILE, userNm: '김영희' });
    show();

    const name = await screen.findByDisplayValue('김철수');
    fireEvent.change(name, { target: { value: '김영희' } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.save' }));

    await waitFor(() => expect(useAuthStore.getState().user?.userNm).toBe('김영희'));
    expect(saveProfile).toHaveBeenCalledWith('김영희', 'kim@example.com');
  });

  it('메일을 비우면 null 로 보낸다 — 빈 문자열은 값이 아니다', async () => {
    saveProfile.mockResolvedValue({ ...PROFILE, email: null });
    show();

    const email = await screen.findByDisplayValue('kim@example.com');
    fireEvent.change(email, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.save' }));

    await waitFor(() => expect(saveProfile).toHaveBeenCalledWith('김철수', null));
  });
});
