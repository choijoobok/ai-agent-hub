// @feat FEAT-003
import {describe, expect, it, vi} from 'vitest';
import {CatalogCommands} from './catalogApi';
import {CATALOG_ENDPOINTS as E} from './endpoints';

describe('카탈로그 영역별 저장', () => {
  it('TC-00323 컬럼만 전송하고 명령 응답의 발급 번호와 리비전을 받는다', async () => {
    const send = vi.fn().mockResolvedValue({status: 'APPLIED', targets: [{targetId: '7', appliedRevision: '3', historyNo: '15'}]});
    const workspace = {master: [{businessName: '아직 저장하지 않은 이름'}], columns: [{clientKey: 'c1', columnName: 'code'}], groups: [{groupId: '5', mappingActive: true}], exceptions: [], indexes: []};
    const commands = new CatalogCommands('cat-area-test', sessionStorage, false, send);
    await commands.save([{id: '7/COLUMNS', state: 'U', original: null, data: {id: '7', revision: '2', scope: 'COLUMNS', values: {columns: workspace.columns}, workspace}}], '컬럼 보완');
    expect(send.mock.calls[0][0]).toBe(E.saveChange);
    expect(send.mock.calls[0][1]).toMatchObject({targetId: '7', expectedRevision: '2', scope: 'COLUMNS', values: {columns: workspace.columns}, reason: '컬럼 보완'});
    expect(send.mock.calls[0][1]).not.toHaveProperty('workspace');
    expect(commands.result()).toMatchObject({targetId: '7', revision: '3'});
  });
  it('TC-00324 응답 유실 후 원 영역·사유·전체 입력으로 복구한다', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({status: 'UNKNOWN'}).mockResolvedValueOnce({status: 'APPLIED', targets: [{targetId: '7', appliedRevision: '3', historyNo: '15'}]});
    const workspace = {master: [], columns: [{columnName: '보존할 편집'}], groups: [], exceptions: [], indexes: []};
    const draft = {id: '7', revision: '2', scope: 'INDEXES' as const, values: {indexes: []}, workspace};
    await new CatalogCommands('cat-recovery-test', sessionStorage, false, send).save([{id: '7/INDEXES', state: 'U', original: null, data: draft}], '원 사유');
    const restored = new CatalogCommands('cat-recovery-test', sessionStorage, false, send);
    expect(restored.restoredRows()[0].data.workspace.columns).toEqual(workspace.columns);
    await restored.save(restored.restoredRows(), '다른 사유');
    expect(send.mock.calls[2]).toEqual(send.mock.calls[0]);
    expect(restored.hasPending()).toBe(false);
  });
});
