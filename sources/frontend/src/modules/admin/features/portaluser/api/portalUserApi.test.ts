// @feat FEAT-005
import { beforeEach, expect, test, vi } from 'vitest';
import { PortalCredentialCommand, scopeValues } from './portalUserApi';
import { PORTAL_USER_ENDPOINTS } from './endpoints';
import type { post } from '@/shared/api';
import type { PortalUserRow } from '../types';

const user: PortalUserRow = { portalUserId: '8', loginId: 'review', userNm: '검토', organizationNm: '', authModeCd: 'LOCAL', sourceCd: 'LOCAL', useStartDt: '2026-01-01', useEndDt: null, activeYn: 'Y', revision: '2' };
beforeEach(() => sessionStorage.clear());
test('TC-0069 현재 소속 변경만 전송하고 표시명·감사와 다른 탭은 제외한다', () => {
  const changed = { id: 'a', state: 'U' as const, original: null, data: { groupId: '4', groupNm: '표시명', activeYn: 'N', memo: '이동', regId: '위조', targetId: '99' } };
  expect(scopeValues({ portalUserId: '8', revision: '2', scope: 'memberships', rows: [changed, { ...changed, id: 'b', state: null }], deactivateUser: true })).toEqual({ memberships: [{ groupId: '4', activeYn: 'N', memo: '이동' }], deactivateUser: true });
  expect(scopeValues({ portalUserId: '8', revision: '2', scope: 'grades', rows: [{ id: 'c', state: 'U', original: { effectiveMonth: '2027-01' }, data: { effectiveMonth: '2027-02', gradeId: '3', activeYn: 'Y', observed: 123 } }], deactivateUser: false })).toEqual({ grades: [{ effectiveMonth: '2027-02', originalMonth: '2027-01', gradeId: '3', activeYn: 'Y' }] });
});
test('TC-0058 임시 비밀번호 응답 유실 후 원 결과만 확인하고 발급을 반복하지 않는다', async () => {
  const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({ status: 'APPLIED', result: { targetId: '8', revision: '3', outcome: 'APPLIED' } });
  const command = new PortalCredentialCommand('credential-test', sessionStorage, send as typeof post);
  await expect(command.run(user, 'resetCredential', '초기화')).rejects.toThrow();
  const raw = sessionStorage.getItem('credential-test')!;expect(raw).not.toContain('password');
  const result = await command.run(user, 'resetCredential', '다른 사유');
  expect(send.mock.calls.map((c) => String(c[0]))).toEqual([PORTAL_USER_ENDPOINTS.resetCredential, PORTAL_USER_ENDPOINTS.getResult]);
  expect(result.password).toBeNull();expect(result.secretState).toBe('SECRET_NOT_REPLAYABLE');expect(command.pending()).toBe(false);
});
test('TC-0058 최초 비밀번호는 반환만 하고 브라우저 저장소에 보존하지 않는다', async () => {
  const send = vi.fn().mockResolvedValue({ result: { targetId: '8', revision: '3' }, password: 'one-time-test', secretState: 'ISSUED_ONCE' });
  const command = new PortalCredentialCommand('credential-test', sessionStorage, send as typeof post);
  expect((await command.run(user, 'resetCredential', '초기화')).password).toBe('one-time-test');
  expect(sessionStorage.getItem('credential-test')).toBeNull();
});
