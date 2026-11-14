// @feat FEAT-005
import { App } from 'antd';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { GroupMembersEditor, type GroupMemberDraft } from './GroupMembersEditor';
import type { GridController } from '@/shared/grid/useGridController';
import type { PortalDetailRow } from '../../portaluser/types';
import type { GroupProjection } from '../types';
import { useAuthStore } from '@/shared/stores/authStore';
import { useMessageStore } from '@/shared/i18n/messageStore';
import { MSG } from '@/shared/i18n/messageKeys';

vi.mock('@/shared/grid/DataGrid', () => ({ DataGrid: ({ controller }: { controller: GridController<PortalDetailRow> }) => <>
  <output data-testid="rows">{JSON.stringify(controller.rows.map((r) => r.data))}</output>
  <button onClick={() => controller.edit(controller.rows[0].id, { memo: '보존할 입력' })}>시험 편집</button>
  <output data-testid="save-permission">{String(controller.canSave)}</output>
</> }));
vi.mock('@/shared/api/request', () => ({ post: vi.fn(), get: vi.fn(), request: vi.fn() }));
beforeEach(() => {
  sessionStorage.clear();useAuthStore.setState({ permissions: ['HUB_PORTAL_USER:READ', 'HUB_PORTAL_USER:SAVE'] });
  useMessageStore.setState({ status: 'ready', messages: { [MSG.GROUP_REASON]: '변경 사유' } });
});
test('TC-0062 그룹·다른 탭 왕복 후 소속 입력과 사유가 보존되고 권한 회수는 즉시 잠근다', async () => {
  const cache = new Map<string, GroupMemberDraft>();const read = vi.fn();
  const detail: GroupProjection = { state: 'READY', rows: [{ portalUserId: '7', revision: '3', loginId: 'review', userNm: '검토', activeYn: 'Y', memo: '원문' }] };
  const view = (group: string | null) => <App>{group ? <GroupMembersEditor key={group} groupId={group} detail={detail} onRead={read} cache={cache} /> : <div>다른 탭</div>}</App>;
  const { rerender } = render(view('1'));
  await waitFor(() => expect(screen.getByTestId('rows').textContent).toContain('원문'));
  fireEvent.click(screen.getByRole('button', { name: '시험 편집' }));fireEvent.change(screen.getByLabelText('변경 사유'), { target: { value: '그룹1 정비' } });
  rerender(view(null));rerender(view('2'));
  await waitFor(() => expect(screen.getByTestId('rows').textContent).toContain('원문'));
  expect(screen.getByLabelText<HTMLInputElement>('변경 사유').value).toBe('');
  rerender(view('1'));
  await waitFor(() => expect(screen.getByTestId('rows').textContent).toContain('보존할 입력'));
  expect(screen.getByLabelText<HTMLInputElement>('변경 사유').value).toBe('그룹1 정비');expect(read).not.toHaveBeenCalled();
  act(() => useAuthStore.setState({ permissions: ['HUB_PORTAL_USER:READ'] }));
  expect(screen.getByTestId('save-permission').textContent).toBe('false');
});
