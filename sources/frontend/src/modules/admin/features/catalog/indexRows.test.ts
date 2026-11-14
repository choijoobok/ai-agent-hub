// @feat FEAT-003
import {expect, it} from 'vitest';
import {expandIndexes, collectIndexes} from './indexRows';
it('TC-00319 두 인덱스의 복합 컬럼 순서·미상 방향을 독립 보존하고 중복 순서는 거부한다', () => {
  const indexes = [{indexId: '10', name: 'a', definitionKind: 'INDEX', unique: 'UNKNOWN', columns: [{columnId: '2', direction: 'DESC'}, {columnId: '1', direction: 'UNKNOWN'}]}, {indexId: '20', name: 'b', definitionKind: 'UNIQUE_KEY', unique: 'YES', columns: [{columnId: '2', direction: 'ASC'}]}];
  const rows = expandIndexes(indexes);
  expect(rows).toHaveLength(3);expect(collectIndexes([rows[1], rows[2], rows[0]])).toEqual(indexes);
  expect(() => collectIndexes([{...rows[0], indexPosition: 2}, rows[1]])).toThrow();
  expect(collectIndexes(rows.filter(r => r.indexId !== '10'))).toEqual([indexes[1]]);
});
