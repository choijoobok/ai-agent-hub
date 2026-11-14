// @feat FEAT-011
import {expect, it, vi} from 'vitest';
import {RelationCommands} from './relationApi';
import type {RelationDraft} from '../types';
it('TC-01110 응답 유실 재시도는 원 소유·전체 목록·사유·명령키를 보존한다', async () => {
  sessionStorage.clear();
  const send = vi.fn().mockRejectedValueOnce(new Error('응답 유실')).mockResolvedValueOnce({status: 'UNKNOWN'}).mockResolvedValueOnce({status: 'APPLIED', targets: [{targetId: '7', appliedRevision: '4', historyNo: '19'}]});
  const data: RelationDraft = {ownerId: '7', revision: '3', relations: [{clientKey: 'new-relation', relationName: '자기조인', pairs: [{position: 1, leftColumnId: '71', rightColumnId: '72'}]}], rows: [], selectedId: 'local'};
  await new RelationCommands('relation-retry', sessionStorage, send).save([{id: '7', state: 'U', original: null, data}], '원 사유');
  const restored = new RelationCommands('relation-retry', sessionStorage, send);expect(restored.restoredRows()[0].data).toEqual(data);
  await restored.save(restored.restoredRows(), '바뀐 사유');expect(send.mock.calls[2]).toEqual(send.mock.calls[0]);expect(restored.hasPending()).toBe(false);
});
