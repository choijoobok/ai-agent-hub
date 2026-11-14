// @feat FEAT-003
import type {Scope, Workspace, CatalogDetail} from './types';
import {expandIndexes} from './indexRows';
import type {GridRow} from '@/shared/grid/rows';
import type {ValueRow} from './types';
export const areaFor: Record<Scope, keyof Workspace> = {MASTER: 'master', COLUMNS: 'columns', ACCESS_GROUPS: 'groups', COLUMN_GROUP_EXCEPTIONS: 'exceptions', INDEXES: 'indexes'};
export const workspaceOf = (detail: CatalogDetail): Workspace => ({...detail.values, master: [{...detail.row, ...detail.values.master, ...(detail.dependencies.review as ValueRow | undefined)}], indexes: expandIndexes(detail.values.indexes)});
/** 다른 영역 저장으로 바뀐 검수·서버 필드는 갱신하되 실제로 고친 마스터 필드만 보존한다. */
export function rebaseMaster(row: GridRow<ValueRow>, fresh: ValueRow): GridRow<ValueRow> {
  const patch = Object.fromEntries(Object.entries(row.data).filter(([key, value]) => JSON.stringify(value) !== JSON.stringify(row.original?.[key])));
  const data = {...fresh, ...patch, revision: fresh.revision, appliedAt: fresh.appliedAt};
  return {...row, data, original: fresh, state: Object.keys(patch).length ? 'U' : null};
}
