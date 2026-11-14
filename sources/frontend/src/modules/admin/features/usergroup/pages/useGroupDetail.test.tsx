// @feat FEAT-006
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useGroupDetail } from './useGroupDetail';
import type { GroupProjection } from '../types';

const api = vi.hoisted(() => ({ group: vi.fn(), detail: vi.fn() }));
vi.mock('../api/userGroupApi', () => ({ fetchGroup: api.group, fetchGroupProjection: api.detail }));
const empty: GroupProjection = { state: 'NOT_CONNECTED', rows: [] };
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}
beforeEach(() => vi.resetAllMocks());
describe('TC-00611 하단 조회 후 삭제 판정', () => {
  it('선택 전에는 호출하지 않고 하단·참조 확인이 모두 끝나야 삭제를 연다', async () => {
    const detail = deferred<GroupProjection>(); const reference = vi.fn();
    api.detail.mockImplementation((_id: string, scope: string) => scope === 'tool' ? detail.promise : Promise.resolve(empty)); api.group.mockResolvedValue({ useState: 'UNUSED' });
    const { rerender } = renderHook(({ id }: { id: string | null }) => useGroupDetail(id, reference), { initialProps: { id: null as string | null } });
    expect(api.group).not.toHaveBeenCalled(); expect(api.detail).not.toHaveBeenCalled();
    rerender({ id: '1' });
    expect(reference).toHaveBeenLastCalledWith('1', 'UNKNOWN');
    expect(api.group).toHaveBeenCalledTimes(1); expect(api.detail).toHaveBeenCalledTimes(5);
    await act(async () => { detail.resolve(empty); await detail.promise; });
    expect(reference).toHaveBeenLastCalledWith('1', 'UNUSED');
  });
  it('참조 확인 실패면 하단이 비어도 삭제를 열지 않는다', async () => {
    api.detail.mockResolvedValue(empty); api.group.mockRejectedValue(new Error('시험용 참조 확인 실패'));
    const reference = vi.fn();
    const { result } = renderHook(() => useGroupDetail('1', reference));
    await waitFor(() => expect(result.current.details.membership?.state).toBe('UNAVAILABLE'));
    expect(reference.mock.calls.every((call) => call[1] === 'UNKNOWN')).toBe(true);
  });
  it('화면에 열지 않은 탭에 의존 데이터가 있어도 삭제를 막는다', async () => {
    api.group.mockResolvedValue({ useState: 'UNUSED' });
    api.detail.mockImplementation((_id: string, scope: string) => Promise.resolve(scope === 'tool' ? { state: 'READY', rows: [{ assetId: '9' }] } : empty));
    const reference = vi.fn();
    renderHook(() => useGroupDetail('1', reference));
    await waitFor(() => expect(reference).toHaveBeenLastCalledWith('1', 'USED'));
  });
  it('그룹 전환 뒤 이전 응답은 현재 삭제 판정을 바꾸지 않고 선택 해제 시 조회하지 않는다', async () => {
    const first = deferred<GroupProjection>(); const reference = vi.fn();
    api.detail.mockImplementation((id: string, scope: string) => id === '1' && scope === 'tool' ? first.promise : Promise.resolve(empty));
    api.group.mockResolvedValue({ useState: 'UNUSED' });
    const { rerender } = renderHook(({ id }: { id: string | null }) => useGroupDetail(id, reference), { initialProps: { id: '1' } });
    rerender({ id: '2' });
    await waitFor(() => expect(reference).toHaveBeenLastCalledWith('2', 'UNUSED'));
    await act(async () => { first.resolve(empty); await first.promise; });
    expect(reference).not.toHaveBeenCalledWith('1', 'UNUSED');
    rerender({ id: null });
    expect(api.detail).toHaveBeenCalledTimes(10);
  });
});
