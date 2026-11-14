// @feat FEAT-042
import { describe, expect, it, vi } from 'vitest';
import { QuotaGradeCommands } from './quotaGradeApi';
import type { GridRow } from '@/shared/grid/rows';
import type { QuotaBasis, QuotaGrade } from '../types';

describe('TC-0421 등급 기준 저장 단위', () => {
  it('메모·유지 등급·삭제 이전을 한 명령으로 보내고 조회 필드는 제외한다', async () => {
    const grades: GridRow<QuotaGrade>[] = ['1', '2'].map((id) => ({ id, state: id === '2' ? 'D' : null, original: null,
      data: { gradeId: id, gradeNm: id, rankNo: id as unknown as number, weight: '3.00', description: '', referenceUsers: 5, transferGradeId: id === '2' ? '1' : undefined } }));
    const row: GridRow<QuotaBasis> = { id: 'set', state: 'U', original: null, data: {
      gradeSetId: '9007199254740993', revision: '2', startMonth: '2040-01', memo: '전체 변경', editable: true, grades,
    } };
    const send = vi.fn().mockResolvedValue({});
    const commands = new QuotaGradeCommands('quota-test', sessionStorage, send);
    expect((await commands.save([row], '미래 기준 정비')).done).toBe(1);
    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][1]).toMatchObject({ targetId: '9007199254740993', expectedRevision: '2', values: {
      memo: '전체 변경', grades: [{ gradeId: '1', rankNo: 1, weight: '3.00' }], transitionMappings: [{ fromGradeId: '2', toGradeId: '1' }],
    } });
    expect(JSON.stringify(send.mock.calls[0][1])).not.toContain('referenceUsers');
  });
});
