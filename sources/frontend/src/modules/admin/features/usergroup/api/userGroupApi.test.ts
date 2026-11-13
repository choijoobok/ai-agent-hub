// @feat FEAT-006
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/shared/api';
import { UserGroupCommands } from './userGroupApi';
import type { GridRow } from '@/shared/grid/rows';
import type { UserGroupRow } from '../types';

const row = (id: string): GridRow<UserGroupRow> => ({ id, state: 'C', original: null, data: { groupId: null, revision: null, groupNm: id, description: '담당', useState: 'UNKNOWN' } });
describe('TC-00611 그룹 독립 명령', () => {
  it('독립 성공은 유지하고 실패한 행만 보고한다', async () => {
    const send = vi.fn().mockResolvedValueOnce({}).mockRejectedValueOnce(new ApiError({ status: 400, code: 'invalid', message: '입력 오류' }));
    const commands = new UserGroupCommands('partial', sessionStorage, send);
    const report = await commands.save([row('a'), row('b')], '등록');
    expect(report).toMatchObject({ total: 2, done: 1, failed: 1, rows: [{ index: 1 }] });
    expect(commands.hasPending()).toBe(false);
  });
  it('응답 유실·페이지 재생성 뒤에도 원 키와 원 본문으로 확인하고 재시도한다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({ status: 'UNKNOWN' }).mockResolvedValueOnce({});
    const first = new UserGroupCommands('lost', sessionStorage, send);
    await first.save([row('lost')], '원 사유');
    const restored = new UserGroupCommands('lost', sessionStorage, send);
    await restored.save(restored.restoredRows(), '다른 사유');
    expect(send.mock.calls[2][1]).toEqual(send.mock.calls[0][1]);
    expect(restored.hasPending()).toBe(false);
  });
  it('이미 적용된 원 명령은 결과 확인으로 종료하고 저장을 반복하지 않는다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({ status: 'APPLIED' });
    const commands = new UserGroupCommands('applied', sessionStorage, send);
    await commands.save([row('applied')], '등록');
    expect((await commands.save(commands.restoredRows(), '재확인')).done).toBe(1);
    expect(send).toHaveBeenCalledTimes(2);
  });
  it('원 결과 조회 권한이 사라져도 미확정 명령을 버리지 않는다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockRejectedValueOnce(new ApiError({ status: 403, code: 'forbidden', message: '현재 권한 없음' }));
    const commands = new UserGroupCommands('forbidden', sessionStorage, send);
    await commands.save([row('forbidden')], '등록');
    await commands.save(commands.restoredRows(), '재확인');
    expect(commands.hasPending()).toBe(true);
    expect(send).toHaveBeenCalledTimes(2);
  });
});
