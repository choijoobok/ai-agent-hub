// @feat FEAT-002
import {describe, expect, it, vi} from 'vitest';
import {DataSourceCommands, DataSourceCleanupCommands} from './dataSourceApi';
import {DATA_SOURCE_ENDPOINTS as E} from './endpoints';
import type {SourceRow, CleanupDraft} from '../types';
import type {GridRow} from '@/shared/grid/rows';
describe('데이터소스 전체 명령', () => {
  it('TC-0021 TC-0028 TC-00216 원자 전체 저장에 기술값·후보명·감사값을 보내지 않는다', async () => {
    const send = vi.fn().mockResolvedValue({});
    const data = {id: '9007199254740993', managementVersion: '2', alias: '읽기 전용', values: {dbmsVersion: null, sourceType: 'DB', syncMethodNote: '기존 메모', timeZoneId: null, description: '', dataBasis: null, applicableGroups: [{groupId: '5', groupName: '보내면 안 됨', mappingActive: false}], queryGuides: [{clientKey: 'new', title: '조회', description: '설명'}]}} as SourceRow;
    await new DataSourceCommands('ds-owned-test', sessionStorage, send).save([{id: 'r1', state: 'U', data, original: null}], '보완');
    const body = send.mock.calls[0][1] as {targetId: string; values: Record<string, unknown>};
    expect(send.mock.calls[0][0]).toBe(E.save); expect(body.targetId).toBe('9007199254740993');
    expect(body.values).toEqual({...data.values, applicableGroups: [{groupId: '5', mappingActive: false}]});
    expect(body.values).not.toHaveProperty('alias'); expect(body.values).not.toHaveProperty('parserId');
  });
  it('TC-00228 TC-00229 정리 응답 유실 뒤 원 결과 경로·동일 키·전체 확인 집합을 재사용한다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({status: 'UNKNOWN'}).mockResolvedValueOnce({});
    const row: GridRow<CleanupDraft> = {id: 'preview', state: 'U', original: null, data: {previewToken: crypto.randomUUID(), query: {scope: 'DATASOURCE', parentId: null, conditions: []}, confirmedTargets: [{id: '2', kind: 'DATASOURCE', parentId: null, expectedVersion: '3'}]}};
    await new DataSourceCleanupCommands('ds-cleanup-test', sessionStorage, send).save([row], '원 사유');
    const restored = new DataSourceCleanupCommands('ds-cleanup-test', sessionStorage, send);
    await restored.save(restored.restoredRows(), '변경한 사유');
    expect(send.mock.calls[1]).toEqual([E.getCommandResult, {commandKey: (send.mock.calls[0][1] as {commandKey: string}).commandKey}]);
    expect(send.mock.calls[2]).toEqual(send.mock.calls[0]);expect(restored.hasPending()).toBe(false);
  });
});
