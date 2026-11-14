// @feat FEAT-001
import { describe, expect, it, vi } from 'vitest';
import { TargetCommands, CleanupCommands } from './targetSystemApi';
import { TARGET_ENDPOINTS as E } from './endpoints';
import type { GridRow } from '@/shared/grid/rows';
import type { SystemRow, CleanupDraft } from '../types';

describe('대상 시스템 원 명령', () => {
  it('TC-0011 TC-0012 표시 저장은 기술 관측값을 보내지 않고 null과 빈 설명을 구별한다', async () => {
    const send = vi.fn().mockResolvedValue({});
    const data = { id: '1', version: '2', systemNameOverride: null, descriptionOverride: '', appliedSettingsId: 'not-an-edit',
      connections: [{ connectionId: 'main', currentDisplay: { baseUrl: 'https://safe.invalid' }, metadataOverrides: { nameOverride: null, descriptionOverride: '' } }] } as SystemRow;
    const row: GridRow<SystemRow> = { id: 'row1', state: 'U', data, original: null };
    await new TargetCommands('target-display-test', sessionStorage, send).save([row], '표시 보완');
    expect(send.mock.calls[0]).toEqual([E.save, expect.objectContaining({ targetId: '1', expectedRevision: '2', values: {
      systemNameOverride: null, descriptionOverride: '', connectionOverrides: [{ connectionId: 'main', nameOverride: null, descriptionOverride: '' }],
    } })]);
  });
  it('TC-0018 정리 응답 유실 뒤에는 소유 결과 경로와 원 키를 사용하고 원 전체 집합을 재전송한다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({ status: 'UNKNOWN' }).mockResolvedValueOnce({});
    const row: GridRow<CleanupDraft> = { id: 'preview', state: 'U', original: null, data: {
      previewToken: crypto.randomUUID(), query: { scope: 'SYSTEM', parentId: null, conditions: [] },
      confirmedTargets: [{ id: '1', kind: 'SYSTEM', parentId: null, expectedVersion: '2' }],
    } };
    await new CleanupCommands('target-cleanup-test', sessionStorage, send).save([row], '정리');
    const restored = new CleanupCommands('target-cleanup-test', sessionStorage, send);
    await restored.save(restored.restoredRows(), '변경한 사유');
    expect(send.mock.calls[1]).toEqual([E.getResult, { commandKey: (send.mock.calls[0][1] as { commandKey: string }).commandKey }]);
    expect(send.mock.calls[2]).toEqual(send.mock.calls[0]);
    expect(restored.hasPending()).toBe(false);
  });
});
