// @feat FEAT-011
import type {GridRow} from '@/shared/grid/rows';
import type {RelationRow} from './types';
export const relationKey = (row: RelationRow) => row.relationId ?? row.clientKey;
/** 화면에 없는 소유 관계는 원본을 보존한다. 다른 소유의 편집은 이 저장에 넣지 않는다. */
export function mergeOwner(owner: string, original: RelationRow[], rows: GridRow<RelationRow>[]): RelationRow[] {
  const merged = new Map(original.map(row => [relationKey(row), row]));
  for (const row of rows.filter(row => row.data.ownerCatalogId === owner)) {
    const key = relationKey(row.data);
    if (row.state === 'D') merged.delete(key); else if (row.state !== null) merged.set(key, row.data);
  }
  return [...merged.values()];
}
const fields = ['relationName','leftCatalogId','leftRevision','rightCatalogId','rightRevision','leftRole','rightRole','cardinality','description','reviewStatus'] as const;
export function editableRelation(row: RelationRow): Record<string, unknown> {
  return {...(row.relationId ? {relationId: row.relationId} : {clientKey: row.clientKey}), ...Object.fromEntries(fields.map(key => [key, row[key] ?? null])),
    pairs: (row.pairs ?? []).map(pair => ({position: pair.position, leftColumnId: pair.leftColumnId, rightColumnId: pair.rightColumnId}))};
}
