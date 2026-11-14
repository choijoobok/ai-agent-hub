// @feat FEAT-010
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/shared/stores/authStore';
import { MSG } from '@/shared/i18n/messageKeys';
import { ChatHelpPage } from './ChatHelpPage';
import { ContentPreview } from './ContentPreview';
import type { ChatHelp } from '../types';
const send = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/request', () => ({ post: send, get: vi.fn(), request: vi.fn() }));
const initial: ChatHelp[] = ['USAGE_HELP', 'CHAT_START', 'INPUT_NOTICE'].map((code) => ({
  helpCd: code as ChatHelp['helpCd'], name: code, position: '위치', timing: '시점',
  formatCd: 'MARKDOWN', body: `${code} 원문`, revision: '1', state: 'READY', acceptedDtm: '2026-11-14T00:00:00Z', changeReason: 'INITIAL_UNSET', modId: null, modDtm: '2026-11-14T00:00:00Z',
}));
let rows: ChatHelp[];
beforeEach(() => {
  sessionStorage.clear(); send.mockReset(); rows = structuredClone(initial);
  useAuthStore.setState({ user: { userId: 'review', userNm: '검토', localeCd: 'ko' }, permissions: ['HUB_CHAT_HELP:READ', 'HUB_CHAT_HELP:SAVE'] });
  send.mockImplementation((url: string, body: Record<string, unknown>) => {
    if (url.endsWith('/getList')) return Promise.resolve(structuredClone(rows));
    if (url.endsWith('/getOne')) return Promise.resolve(structuredClone(rows.find((row) => row.helpCd === body.helpCd)));
    if (url.endsWith('/save')) {
      const row = rows.find((value) => value.helpCd === body.targetId)!;
      Object.assign(row, body.values, { revision: '2' }); return Promise.resolve({ outcome: 'APPLIED' });
    }
    if (url.endsWith('/getPreview')) return Promise.resolve({ policyVersion: 'chat-content/1', html: '<p>안전 표시</p>', removed: true, hasContent: true });
    throw new Error(`예상하지 않은 호출: ${url}`);
  });
});
afterEach(cleanup);
const start = async () => { render(<ConfigProvider theme={{ token: { motion: false } }}><App><ChatHelpPage /></App></ConfigProvider>); await screen.findByDisplayValue('USAGE_HELP 원문'); };
const input = () => screen.getByRole('textbox', { name: MSG.CHAT_HELP_BODY });
const choose = async (label: string, value: string) => {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: label }));
  const option = await screen.findByTitle(value); fireEvent.click(option);
};
it('TC-0101/0103 형식 변경과 구분 왕복은 원문을 보존하고 선택한 구분만 새 사유로 저장한다', async () => {
  await start(); fireEvent.change(input(), { target: { value: '  <b>사용 초안</b>  ' } });
  await choose(MSG.CHAT_HELP_FORMAT, 'HTML'); expect((input() as HTMLTextAreaElement).value).toBe('  <b>사용 초안</b>  ');
  await choose(MSG.CHAT_HELP_KIND, 'CHAT_START · CHAT_START');
  fireEvent.change(input(), { target: { value: '시작 초안' } });
  await choose(MSG.CHAT_HELP_KIND, 'USAGE_HELP · USAGE_HELP *');
  expect((input() as HTMLTextAreaElement).value).toBe('  <b>사용 초안</b>  ');
  fireEvent.click(screen.getByRole('button', { name: MSG.GRID_SAVE }));
  const modal = await screen.findByRole('dialog');
  expect(within(modal).getByRole('button', { name: MSG.GRID_SAVE }).hasAttribute('disabled')).toBe(true);
  fireEvent.change(within(modal).getByRole('textbox'), { target: { value: '사용 안내 수정' } });
  fireEvent.click(within(modal).getByRole('button', { name: MSG.GRID_SAVE }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  const saves = send.mock.calls.filter(([url]) => String(url).endsWith('/save'));
  expect(saves).toHaveLength(1); expect(saves[0][1]).toMatchObject({ targetId: 'USAGE_HELP', expectedRevision: '1', reason: '사용 안내 수정', values: { formatCd: 'HTML', body: '  <b>사용 초안</b>  ' } });
  await choose(MSG.CHAT_HELP_KIND, 'CHAT_START · CHAT_START *'); expect((input() as HTMLTextAreaElement).value).toBe('시작 초안');
});
it('TC-0103 확대 편집 취소는 원문을 보존하고 적용은 초안만 바꾼다', async () => {
  await start(); fireEvent.click(screen.getByRole('button', { name: MSG.CHAT_HELP_ENLARGE }));
  let modal = await screen.findByRole('dialog'); fireEvent.change(within(modal).getByRole('textbox'), { target: { value: '취소할 입력' } });
  fireEvent.click(within(modal).getByRole('button', { name: MSG.COMMON_CANCEL }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect((input() as HTMLTextAreaElement).value).toBe('USAGE_HELP 원문');
  fireEvent.click(screen.getByRole('button', { name: MSG.CHAT_HELP_ENLARGE })); modal = await screen.findByRole('dialog');
  fireEvent.change(within(modal).getByRole('textbox'), { target: { value: '적용할 입력' } }); fireEvent.click(within(modal).getByRole('button', { name: MSG.CHAT_HELP_APPLY }));
  await waitFor(() => expect((input() as HTMLTextAreaElement).value).toBe('적용할 입력'));
  expect(send.mock.calls.some(([url]) => String(url).endsWith('/save'))).toBe(false);
});
it('TC-0105 READ 전용도 저장 없이 같은 정책으로 미리보기 한다', async () => {
  useAuthStore.setState({ permissions: ['HUB_CHAT_HELP:READ'] }); await start();
  expect((input() as HTMLTextAreaElement).readOnly).toBe(true);
  expect(screen.getByRole('button', { name: MSG.CHAT_HELP_ENLARGE }).hasAttribute('disabled')).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: MSG.CHAT_HELP_PREVIEW }));
  const frame = await screen.findByTitle(MSG.CHAT_HELP_PREVIEW);
  expect(frame.getAttribute('srcdoc')).toContain('<p>안전 표시</p>'); expect(frame.getAttribute('sandbox')).not.toMatch(/allow-scripts|allow-same-origin/);
  expect(send.mock.calls.some(([url]) => String(url).endsWith('/save'))).toBe(false);
});
it('TC-0102 알 수 없는 렌더 정책은 iframe에 표시하지 않는다', () => {
  const { container } = render(<ContentPreview display={{ policyVersion: 'unknown', html: '<script>bad()</script>', removed: false, hasContent: true }} />);
  expect(container.querySelector('iframe')).toBeNull();
});
