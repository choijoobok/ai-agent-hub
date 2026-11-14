// @feat FEAT-006
import { post, ManagedCommands } from '@/shared/api';
import { MSG } from '@/shared/i18n/messageKeys';
import { GROUP_ENDPOINTS } from './endpoints';
import type { GroupProjection, GroupScope, UserGroupRow } from '../types';

export function fetchGroupProjection(targetId: string, scope: GroupScope): Promise<GroupProjection> {
  return post(scope === 'membership' ? GROUP_ENDPOINTS.getMembers : GROUP_ENDPOINTS.getAssets, { targetId, scope });
}
export function fetchGroup(targetId: string): Promise<UserGroupRow> {
  return post(GROUP_ENDPOINTS.getOne, { targetId });
}
export class UserGroupCommands extends ManagedCommands<UserGroupRow> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...GROUP_ENDPOINTS, feature: 'FEAT-006', field: 'groupNm',
      changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING,
      encode: (row, reason) => ({ targetId: row.data.groupId, expectedRevision: row.data.revision, scope: 'definition', reason,
        values: row.state === 'D' ? {} : { groupNm: row.data.groupNm, description: row.data.description } }),
    }, send);
  }
}
