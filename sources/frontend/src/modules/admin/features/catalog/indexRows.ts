// @feat FEAT-003
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import type {ValueRow} from './types';
export const indexKey = (row: ValueRow): string => typeof row.indexId === 'string' ? row.indexId : typeof row.clientKey === 'string' ? row.clientKey : '';
export function expandIndexes(indexes: ValueRow[]): ValueRow[] {
  return indexes.flatMap(index => {
    const {columns, ...header} = index;const parts = columns as ValueRow[];
    return (parts.length ? parts : [{columnId: null, direction: 'UNKNOWN'}]).map((part, i) => ({...header, indexPosition: i + 1, columnId: part.columnId, direction: part.direction}));
  });
}
export function collectIndexes(rows: ValueRow[]): ValueRow[] {
  const groups = new Map<string, ValueRow[]>();
  for (const row of rows) {const key = indexKey(row);if (!key) throw new Error(label(MSG.CAT_REQUIRED));groups.set(key, [...groups.get(key) ?? [], row]);}
  return [...groups.values()].map(parts => {
    const ordered = [...parts].sort((a,b) => Number(a.indexPosition) - Number(b.indexPosition));
    const header = Object.fromEntries(Object.entries(ordered[0]).filter(([key]) => !['indexPosition','columnId','direction'].includes(key)));
    const columns = ordered.map((row, i) => {
      if (row.indexPosition !== i + 1 || !row.columnId || ordered.some((other, n) => n !== i && other.columnId === row.columnId)) throw new Error(label(MSG.CAT_REQUIRED));
      for (const key of ['name','definitionKind','unique','definitionEvidence']) if (row[key] !== header[key]) throw new Error(label(MSG.CAT_REQUIRED));
      return {columnId: row.columnId, direction: row.direction};
    });
    return {...header, columns};
  });
}
