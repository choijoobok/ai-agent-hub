// @feat FEAT-024
import { describe, expect, it, vi } from 'vitest';
import { SearchTagCommands, loadTagDetails } from './searchTagApi';
import type { GridRow } from '@/shared/grid/rows';
import type { TagRow } from '../types';
describe('TC-0244 태그 전체 저장과 상세 판 확인', () => {
  it('동의어 전체 집합만 보내고 삭제 동의어·서버 파생 필드는 제외한다', async () => {
    const send = vi.fn().mockResolvedValue({});
    const row: GridRow<TagRow> = { id: 'row', state: 'U', original: null, data: { tagId: '9007199254740993', revision: '2', searchText: ' WMS ', useYn: 'N', detailHash: 'changed', synonyms: [
      { id: 's', state: 'C', original: null, data: { synonymId: null, clientKey: 'new', searchText: 'Warehouse', description: '' } },
      { id: 'd', state: 'D', original: null, data: { synonymId: '10', searchText: 'removed', description: null } },
    ] } };
    const commands = new SearchTagCommands('tag-unit', sessionStorage, send);
    expect((await commands.save([row], '정비')).done).toBe(1);
    expect(send.mock.calls[0][1]).toMatchObject({ targetId: '9007199254740993', expectedRevision: '2', values: { searchText: ' WMS ', useYn: 'N', synonyms: [{ clientKey: 'new', searchText: 'Warehouse' }] } });
    expect((send.mock.calls[0][1] as { values: { synonyms: unknown[] } }).values.synonyms).toHaveLength(1);
    expect(JSON.stringify(send.mock.calls[0][1])).not.toContain('detailHash');
  });
  it('다른 판의 상세·참조나 조회 실패를 정상 빈 상세로 저장하지 않는다', async () => {
    const send = vi.fn().mockResolvedValueOnce({ tag: { revision: '2' }, synonyms: [] }).mockResolvedValueOnce({ revision: '3', rows: [], reference: 'ABSENT' });
    await expect(loadTagDetails('1', '2', send)).rejects.toThrow();
    const failed = vi.fn().mockRejectedValue(new Error('조회 실패'));
    await expect(loadTagDetails('1', '2', failed)).rejects.toThrow('조회 실패');
  });
});
