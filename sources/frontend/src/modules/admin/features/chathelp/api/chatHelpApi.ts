// @feat FEAT-010
import { post, ManagedCommands } from '@/shared/api';
import { MSG } from '@/shared/i18n/messageKeys';
import { CHAT_HELP_ENDPOINTS as E } from './endpoints';
import type { ChatHelp, HelpCode, ContentDisplay } from '../types';
export const fetchChatHelpList = () => post<ChatHelp[]>(E.getList, {});
export const fetchChatHelp = (helpCd: HelpCode) => post<ChatHelp>(E.getOne, { helpCd });
export const previewChatHelp = (value: Pick<ChatHelp, 'formatCd' | 'body'>) => post<ContentDisplay>(E.getPreview, { formatCd: value.formatCd, body: value.body });
export class ChatHelpCommands extends ManagedCommands<ChatHelp> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...E, remove: '', feature: 'FEAT-010', field: 'body', changedMessage: MSG.CHAT_HELP_CHANGED, pendingMessage: MSG.CHAT_HELP_PENDING,
      encode: (row, reason) => ({ targetId: row.data.helpCd, expectedRevision: row.data.revision, scope: 'content', reason, values: { formatCd: row.data.formatCd, body: row.data.body } }),
    }, send);
  }
}
