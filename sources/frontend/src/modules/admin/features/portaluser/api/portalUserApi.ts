// @feat FEAT-005
import { post, ApiError, ManagedCommands, type ManagedCommand } from '@/shared/api';
import { MSG } from '@/shared/i18n/messageKeys';
import { label } from '@/shared/i18n/label';
import { PORTAL_USER_ENDPOINTS as URL } from './endpoints';
import type { PortalUserRow, PortalDetail, PortalScopeDraft, PortalSaved } from '../types';

export const fetchPortalDetail = (targetId: string | null, scope: string, effectiveMonth?: string): Promise<PortalDetail> =>
  post(scope.startsWith('identity') ? URL.getIdentityTargets : URL.getOne, { targetId, scope, effectiveMonth });
export const portalCandidates = (login = ''): Promise<{ content: PortalUserRow[] }> => post(URL.getList, { conditions: login ? [{ field: 'loginId', op: 'contains', value: login }] : [], page: { page: 0, size: 50 } });
const contract = { ...URL, remove: URL.save, feature: 'FEAT-005', changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING };
export class PortalProfileCommands extends ManagedCommands<PortalUserRow> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...contract, field: 'loginId', encode: ({ data }, reason) => ({ targetId: data.portalUserId, expectedRevision: data.revision, scope: 'profile', reason,
      values: { loginId: data.loginId, userNm: data.userNm, organizationNm: data.organizationNm, useStartDt: data.useStartDt, useEndDt: data.useEndDt || null, activeYn: data.activeYn,
        ...(data.portalUserId === null && data.authModeCd === 'EXTERNAL' ? { externalSource: data.externalSource, externalSubject: data.externalSubject } : {}) } }) }, send);
  }
}
export function scopeValues(draft: PortalScopeDraft): Record<string, unknown> {
  const rows = draft.rows.filter((row) => row.state !== null && row.state !== 'D');
  if (draft.scope === 'memberships') return { memberships: rows.map(({ data }) => ({ groupId: data.groupId, activeYn: data.activeYn, memo: data.memo })), deactivateUser: draft.deactivateUser };
  if (draft.scope === 'grades') return { grades: rows.map(({ data, original }) => ({ effectiveMonth: data.effectiveMonth, originalMonth: original?.effectiveMonth ?? data.effectiveMonth, gradeId: data.gradeId, activeYn: data.activeYn })) };
  if (draft.scope === 'extraTokens') throw new Error(label(MSG.PORTAL_EXTRA_READ_ONLY));
  return { identities: rows.map(({ data }) => ({ targetId: data.targetId, externalUserId: data.externalUserId ?? '', mappingActiveYn: data.mappingActiveYn, identityEvidence: data.identityEvidence ?? '' })) };
}
export class PortalScopeCommands extends ManagedCommands<PortalScopeDraft> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...contract, field: 'activeYn', encode: ({ data }, reason) => ({ targetId: data.portalUserId, expectedRevision: data.revision, scope: data.scope, reason, values: scopeValues(data) }) }, send);
  }
}
export interface CredentialResult { result: PortalSaved; password: string | null; secretState: string }
/** 비밀번호는 응답 메모리에만 둔다. 원 명령 메타데이터만 저장하고 유실 후에는 결과부터 확인한다. */
export class PortalCredentialCommand {
  private readonly key: string;
  private readonly storage: Storage;
  private readonly send: typeof post;
  constructor(key: string, storage: Storage, send: typeof post = post) { this.key = key; this.storage = storage; this.send = send; }
  pending(): boolean { return this.storage.getItem(this.key) !== null; }
  async run(user: PortalUserRow, scope: 'resetCredential' | 'unlockCredential', reason: string): Promise<CredentialResult> {
    const raw = this.storage.getItem(this.key);
    const command: ManagedCommand = raw ? JSON.parse(raw) as ManagedCommand : { commandKey: crypto.randomUUID(), targetId: user.portalUserId, expectedRevision: user.revision, scope, values: {}, reason };
    if (raw) {
      const checked = await this.send<{ status: string; result: PortalSaved }>(URL.getResult, { commandKey: command.commandKey, ownerFeature: 'FEAT-005' });
      if (checked.status === 'APPLIED') { this.storage.removeItem(this.key); return { result: checked.result, password: null, secretState: 'SECRET_NOT_REPLAYABLE' }; }
      if (command.targetId !== user.portalUserId || command.scope !== scope) throw new Error(label(MSG.PORTAL_CREDENTIAL_PENDING));
    } else this.storage.setItem(this.key, JSON.stringify(command));
    try {
      const response = await this.send<CredentialResult | PortalSaved>(URL[scope], command);
      this.storage.removeItem(this.key);
      return scope === 'resetCredential' ? response as CredentialResult : { result: response as PortalSaved, password: null, secretState: 'UNLOCKED' };
    } catch (error) {
      if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.code !== 'err.managed.commandExpired') this.storage.removeItem(this.key);
      throw error;
    }
  }
}
