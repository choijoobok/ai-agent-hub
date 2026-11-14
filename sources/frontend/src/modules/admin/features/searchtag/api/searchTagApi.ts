// @feat FEAT-024
import { post, ManagedCommands } from '@/shared/api';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { SEARCH_TAG_ENDPOINTS as E } from './endpoints';
import type { TagRow, TagDetail, TagReferences } from '../types';
export async function loadTagDetails(targetId: string, revision: string, send: typeof post = post) {
  const [detail, references] = await Promise.all([send<TagDetail>(E.getOne, { targetId }), send<TagReferences>(E.getReferences, { targetId })]);
  if (detail.tag.revision !== revision || references.revision !== revision) throw new Error(label(MSG.SEARCH_TAG_STALE));
  return { detail, references };
}
export class SearchTagCommands extends ManagedCommands<TagRow> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...E, feature: 'FEAT-024', field: 'searchText', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING,
      encode: (row, reason) => ({ targetId: row.data.tagId, expectedRevision: row.data.revision, scope: 'definition', reason,
        values: row.state === 'D' ? {} : { searchText: row.data.searchText, useYn: row.data.useYn,
          synonyms: (row.data.synonyms ?? []).filter((item) => item.state !== 'D').map(({ data }) => ({ synonymId: data.synonymId, clientKey: data.synonymId ? null : data.clientKey, searchText: data.searchText, description: data.description })),
        } }),
    }, send);
  }
}
