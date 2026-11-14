// @feat FEAT-002
import { ManagedCommands, post } from '@/shared/api';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { DATA_SOURCE_ENDPOINTS as E } from './endpoints';
import type { SourceRow, CleanupDraft } from '../types';
const common = { feature: 'FEAT-002', getResult: E.getCommandResult, resultBody: (commandKey: string) => ({ commandKey }), field: 'alias', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING };
export class DataSourceCommands extends ManagedCommands<SourceRow> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...common, save: E.save, remove: E.save, encode: ({data}, reason) => ({ targetId: data.id, expectedRevision: data.managementVersion, scope: 'main', reason, values: ownedValues(data) }) }, send);
  }
}
export class DataSourceCleanupCommands extends ManagedCommands<CleanupDraft> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...common, save: E.deleteConfigurationMismatches, remove: E.deleteConfigurationMismatches, encode: ({data}, reason) => ({ targetId: null, expectedRevision: null, scope: 'cleanup', reason, values: {...data} }) }, send);
  }
}

export const loadSource = (id: string) => post<import('../types').Detail>(E.getOne, {id});
export const loadTimezones = () => post<string[]>(E.getTimezoneList, {});
export const previewCleanup = (query: CleanupDraft['query']) => post<import('../types').Preview>(E.getConfigurationCleanupPreview, query);

function ownedValues(data: SourceRow): Record<string, unknown> {
  const v = data.values; if (!v) throw new Error(label(MSG.GROUP_UNKNOWN));
  return {dbmsVersion: v.dbmsVersion, sourceType: v.sourceType, syncMethodNote: v.syncMethodNote, timeZoneId: v.timeZoneId, description: v.description, dataBasis: v.dataBasis, applicableGroups: v.applicableGroups.map(g => ({groupId: g.groupId, mappingActive: g.mappingActive})), queryGuides: v.queryGuides.map(g => ({...(g.guideId ? {guideId: g.guideId} : {clientKey: g.clientKey}), title: g.title, description: g.description}))};
}
