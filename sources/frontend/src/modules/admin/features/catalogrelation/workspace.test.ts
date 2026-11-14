// @feat FEAT-011
import {expect, it} from 'vitest';
import {mergeOwner, editableRelation} from './workspace';
import type {RelationRow} from './types';
const a: RelationRow = {relationId: '9007199254740993', ownerCatalogId: '7', relationName: '원 관계', pairs: [{position: 1, leftColumnId: '101', rightColumnId: '201', regId: '서버'}]};
it('TC-0111 TC-0116 조회하지 않은 소유 관계와 다른 소유 편집을 서로 침범하지 않는다', () => {
  const hidden = {...a, relationId: '9'};
  const changed = {...a, relationName: '수정'};
  const rows = [{id: 'a', state: 'U' as const, original: a, data: changed}, {id: 'other', state: 'D' as const, original: null, data: {relationId: '21', ownerCatalogId: '8'}}];
  expect(mergeOwner('7', [a, hidden], rows)).toEqual([changed, hidden]);
  expect(mergeOwner('7', [a, hidden], [{...rows[0], state: 'D'}])).toEqual([hidden]);
});
it('TC-01110 전체 쌍과 큰 식별자는 보존하고 등록 근거·표시 필드는 저장 입력에서 제외한다', () => {
  const source = {...a, regId: '관리자', leftObjectName: '조회 이름'};
  expect(editableRelation(source)).toMatchObject({relationId: '9007199254740993', pairs: [{position: 1, leftColumnId: '101', rightColumnId: '201'}]});
  expect(editableRelation(source)).not.toHaveProperty('regId');
  expect(editableRelation(source)).not.toHaveProperty('ownerCatalogId');
  expect((editableRelation(source).pairs as unknown[])[0]).not.toHaveProperty('regId');
  expect(source.pairs?.[0].regId).toBe('서버');
});
