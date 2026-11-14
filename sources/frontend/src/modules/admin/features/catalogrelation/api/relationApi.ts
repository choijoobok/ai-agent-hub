// @feat FEAT-011
import {ManagedCommands, post} from '@/shared/api';
import {MSG} from '@/shared/i18n/messageKeys';
import {CATALOG_ENDPOINTS as C} from '../../catalog/api/endpoints';
import {editableRelation} from '../workspace';
import type {RelationDraft, OwnerDetail} from '../types';
export const loadOwner = (id: string) => post<OwnerDetail>(C.getOne, {id});
export class RelationCommands extends ManagedCommands<RelationDraft> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, {feature: 'FEAT-003', save: C.saveChange, remove: C.saveChange, getResult: C.getCommandResult, resultBody: commandKey => ({commandKey}), field: 'relationName', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING,
      encode: ({data}, reason) => ({targetId: data.ownerId, expectedRevision: data.revision, scope: 'RELATIONSHIPS', values: {relations: data.relations.map(editableRelation)}, reason})}, send);
  }
}
