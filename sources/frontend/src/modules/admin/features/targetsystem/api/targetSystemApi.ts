// @feat FEAT-001
import { ManagedCommands, post } from '@/shared/api';
import { MSG } from '@/shared/i18n/messageKeys';
import { TARGET_ENDPOINTS as E } from './endpoints';
import type { SystemRow, CleanupDraft } from '../types';

const resultBody = (commandKey: string) => ({ commandKey });
const common = { feature: 'FEAT-001', getResult: E.getResult, resultBody, field: 'systemName', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING };
export class TargetCommands extends ManagedCommands<SystemRow> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...common, save: E.save, remove: E.save,
      encode: ({ data }, reason) => ({ targetId: data.id, expectedRevision: data.version, scope: 'display', reason,
        values: { systemNameOverride: data.systemNameOverride, descriptionOverride: data.descriptionOverride,
          connectionOverrides: data.connections.map((c) => ({ connectionId: c.connectionId, ...c.metadataOverrides })),
        } }),
    }, send);
  }
}
export class CleanupCommands extends ManagedCommands<CleanupDraft> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...common, save: E.cleanup, remove: E.cleanup,
      encode: ({ data }, reason) => ({ targetId: null, expectedRevision: null, scope: 'cleanup', reason, values: { ...data } }),
    }, send);
  }
}
