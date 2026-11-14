// @feat FEAT-006
import { post, ApiError, type RowReport } from '@/shared/api';
import type { GridRow } from '@/shared/grid/rows';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { GROUP_ENDPOINTS } from './endpoints';
import type { GroupProjection, GroupScope, UserGroupRow } from '../types';

interface Command {
  commandKey: string; targetId: string | null; expectedRevision: string | null;
  scope: 'definition'; values: Record<string, string>; reason: string;
}
interface Pending { row: GridRow<UserGroupRow>; command: Command; action: 'save' | 'remove' }
type Send = typeof post;

export function fetchGroupProjection(targetId: string, scope: GroupScope): Promise<GroupProjection> {
  return post(scope === 'membership' ? GROUP_ENDPOINTS.getMembers : GROUP_ENDPOINTS.getAssets, { targetId, scope });
}
export function fetchGroup(targetId: string): Promise<UserGroupRow> {
  return post(GROUP_ENDPOINTS.getOne, { targetId });
}

/** 원 명령을 탭 저장소에 먼저 보존한다. 행마다 독립 요청이므로 앞 행 성공은 뒤 행 실패로 되돌리지 않는다. */
export class UserGroupCommands {
  private readonly pending: Map<string, Pending>;
  private readonly key: string;
  private readonly storage: Storage;
  private readonly send: Send;
  constructor(key: string, storage: Storage, send: Send = post) {
    this.key = key; this.storage = storage; this.send = send;
    const raw = storage.getItem(key);
    this.pending = new Map(raw ? JSON.parse(raw) as [string, Pending][] : []);
  }
  restoredRows(): GridRow<UserGroupRow>[] { return [...this.pending.values()].map((entry) => entry.row); }
  hasPending(): boolean { return this.pending.size > 0; }
  private persist(): void { this.storage.setItem(this.key, JSON.stringify([...this.pending])); }
  async save(rows: GridRow<UserGroupRow>[], reason: string): Promise<RowReport> {
    const report: RowReport = { total: rows.length, done: 0, failed: 0, rows: [] };
    for (const [index, row] of rows.entries()) {
      let entry = this.pending.get(row.id);
      let submitted = false;
      try {
        if (entry) {
          // 결과를 먼저 확인한다. 사용자가 고친 입력을 원 키에 붙이거나 새 키로 자동 재전송하지 않는다.
          const checked = await this.send<{ status: string }>(GROUP_ENDPOINTS.getResult, { commandKey: entry.command.commandKey, ownerFeature: 'FEAT-006' });
          if (checked.status === 'APPLIED') {
            this.pending.delete(row.id); this.persist(); report.done += 1; continue;
          }
          if (JSON.stringify(row.data) !== JSON.stringify(entry.row.data) || row.state !== entry.row.state)
            throw new Error(label(MSG.GROUP_PENDING_CHANGED));
        } else {
          const action = row.state === 'D' ? 'remove' : 'save';
          const command: Command = { commandKey: crypto.randomUUID(), targetId: row.data.groupId, expectedRevision: row.data.revision,
            scope: 'definition', values: action === 'remove' ? {} : { groupNm: row.data.groupNm, description: row.data.description }, reason };
          entry = { row: structuredClone(row), command, action };
          this.pending.set(row.id, entry); this.persist();
        }
        submitted = true;
        await this.send(GROUP_ENDPOINTS[entry.action], entry.command);
        this.pending.delete(row.id); this.persist(); report.done += 1;
      } catch (error) {
        // 4xx는 업무 미반영이 확정됐다. 통신/5xx는 원 명령을 보존해 결과 확인 뒤에만 재수신한다.
        if (submitted && error instanceof ApiError && error.status >= 400 && error.status < 500 && error.code !== 'err.managed.commandExpired') {
          this.pending.delete(row.id); this.persist();
        }
        report.failed += 1;
        report.rows.push({ index, fields: [{ field: 'groupNm', message: error instanceof Error ? error.message : label(MSG.GROUP_PENDING) }] });
      }
    }
    return report;
  }
}
