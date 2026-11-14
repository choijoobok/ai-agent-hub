// @feat FEAT-003
import {ManagedCommands, post} from '@/shared/api';
import {MSG} from '@/shared/i18n/messageKeys';
import {label} from '@/shared/i18n/label';
import {CATALOG_ENDPOINTS as E} from './endpoints';
import type {CatalogDetail, CommandDraft, CommandResult, Projection, ValueRow} from '../types';

interface ManagementResult {status: string; targets: {targetId: string; appliedRevision: string; historyNo: string | null}[]}
const editableValues = (data: CommandDraft) => data.scope === 'COLUMN_GROUP_EXCEPTIONS'
  ? {exceptions: (data.values.exceptions as ValueRow[]).map(row => ({columnId: row.columnId, groupId: row.groupId, mode: row.mode, reason: row.reason}))}
  : data.values;
const resultOf = (value: unknown): CommandResult => {
  const result = value as ManagementResult | null;
  const target = result?.targets?.[0];
  if (!target || result?.targets.length !== 1 || !target.targetId || !target.appliedRevision) throw new Error(label(MSG.CAT_RELOAD));
  return {targetId: target.targetId, revision: target.appliedRevision, historyNo: target.historyNo, outcome: result.status};
};

/** 원 명령 복구는 공통 구현에 맡기고 신규 발급 ID/공통 리비전만 화면에 전달한다. */
export class CatalogCommands extends ManagedCommands<CommandDraft> {
  private readonly last: {value: CommandResult | null};
  constructor(key: string, storage: Storage, create = false, send: typeof post = post) {
    const last: {value: CommandResult | null} = {value: null};
    const save = create ? E.saveCreate : E.saveChange;
    const transport: typeof post = async <T,>(url: string, body?: unknown): Promise<T> => {
      const response = await send<T>(url, body);
      if (url === save) last.value = resultOf(response);
      else if (url === E.getCommandResult) {
        const checked = response as {status: string; result?: ManagementResult};
        if (checked.status === 'APPLIED') last.value = resultOf(checked.result);
      }
      return response;
    };
    super(key, storage, {feature: 'FEAT-003', save, remove: save, getResult: E.getCommandResult, resultBody: commandKey => ({commandKey}), field: 'objectName', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING,
      encode: ({data}, reason) => ({targetId: data.id, expectedRevision: data.revision, scope: data.scope, values: editableValues(data), reason})}, transport);
    this.last = last;
  }
  result(): CommandResult | null {return this.last.value;}
}
export const loadCatalog = (id: string) => post<CatalogDetail>(E.getOne, {id});
export const loadReferences = (id: string) => post<Projection>(E.getReferences, {id});
export const resolveObject = (values: Record<string, unknown>) => post<Record<string, unknown>>(E.getResolvedObject, values);
