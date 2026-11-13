// @feat FEAT-501
import type { AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';

const adapter = vi.fn<AxiosAdapter>();
const original = apiClient.defaults.adapter;

describe('TC-50115 ADMIN CSRF 소비', () => {
  beforeEach(() => {
    document.cookie = 'AIHUB_ADMIN_CSRF=; Max-Age=0; Path=/';
    adapter.mockReset();
    apiClient.defaults.adapter = adapter;
    adapter.mockImplementation((config) => {
      if (config.url === '/system/public/auth/getCsrf') document.cookie = 'AIHUB_ADMIN_CSRF=prepared-token; Path=/';
      return Promise.resolve({ config, status: 200, statusText: 'OK', headers: {}, data: { success: true } });
    });
  });
  afterEach(() => { apiClient.defaults.adapter = original; });

  it('처음 POST 전에 ADMIN CSRF를 준비하고 해당 쿠키만 헤더로 보낸다', async () => {
    document.cookie = 'AIHUB_PORTAL_CSRF=portal-token; Path=/';
    await apiClient.post('/system/public/auth/login', {});
    expect(adapter.mock.calls.map(([config]) => config.url)).toEqual(['/system/public/auth/getCsrf', '/system/public/auth/login']);
    expect(adapter.mock.calls[1][0].headers.get('X-AIHUB-ADMIN-CSRF')).toBe('prepared-token');
  });

  it('회전 뒤 현재 쿠키를 사용하며 Portal 요청은 ADMIN 준비/헤더를 쓰지 않는다', async () => {
    document.cookie = 'AIHUB_ADMIN_CSRF=rotated-token; Path=/';
    await apiClient.post('/system/common/auth/changePassword', {});
    expect(adapter).toHaveBeenCalledOnce();
    expect(adapter.mock.calls[0][0].headers.get('X-AIHUB-ADMIN-CSRF')).toBe('rotated-token');
    await apiClient.post('/portal/common/profile/save', {});
    expect(adapter.mock.calls[1][0].headers.has('X-AIHUB-ADMIN-CSRF')).toBe(false);
  });

  it('공개 문구 조회는 ADMIN 세션이나 CSRF 준비를 만들지 않는다', async () => {
    await apiClient.get('/system/public/message/getBundle');
    expect(adapter).toHaveBeenCalledOnce();
    expect(adapter.mock.calls[0][0].headers.has('X-AIHUB-ADMIN-CSRF')).toBe(false);
  });
  it('오래된 쿠키가 있어도 로그인은 POST 전에 CSRF 준비를 새로 확인한다', async () => {
    document.cookie = 'AIHUB_ADMIN_CSRF=expired-token; Path=/';
    await apiClient.post('/system/public/auth/login', {});
    expect(adapter.mock.calls.map(([config]) => config.url)).toEqual(['/system/public/auth/getCsrf', '/system/public/auth/login']);
    expect(adapter.mock.calls[1][0].headers.get('X-AIHUB-ADMIN-CSRF')).toBe('prepared-token');
  });

});
