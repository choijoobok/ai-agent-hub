// @feat FEAT-040
import { post, ApiError, type RowReport } from '@/shared/api';
import type { GridRow } from '@/shared/grid/rows';
import { label } from '@/shared/i18n/label';
export interface ManagedCommand {
  commandKey: string; targetId: string | null; expectedRevision: string | null;
  scope: string; values: Record<string, unknown>; reason: string;
}
type Command = ManagedCommand;
interface Pending<T> { row: GridRow<T>; command: Command; action: 'save' | 'remove' }
type Send = typeof post;
interface Contract<T> {
  feature: string; getResult: string; save: string; remove: string;
  field: string; changedMessage: string; pendingMessage: string;
  resultBody?: (commandKey: string) => Record<string, unknown>;
  encode: (row: GridRow<T>, reason: string) => Omit<ManagedCommand, 'commandKey'>;
}

/** 원 명령을 탭 저장소에 먼저 보존한다. 행마다 독립 요청이므로 앞 행 성공은 뒤 행 실패로 되돌리지 않는다. */
export class ManagedCommands<T> {
  private readonly pending: Map<string, Pending<T>>;
  private readonly key: string;
  private readonly storage: Storage;
  private readonly send: Send;
  private readonly contract: Contract<T>;
  constructor(key: string, storage: Storage, contract: Contract<T>, send: Send = post) {
    this.key = key; this.storage = storage; this.send = send; this.contract = contract;
    const raw = storage.getItem(key);
    this.pending = new Map(raw ? JSON.parse(raw) as [string, Pending<T>][] : []);
  }
  restoredRows(): GridRow<T>[] { return [...this.pending.values()].map((entry) => entry.row); }
  hasPending(): boolean { return this.pending.size > 0; }
  private persist(): void { this.storage.setItem(this.key, JSON.stringify([...this.pending])); }
  async save(rows: GridRow<T>[], reason: string): Promise<RowReport> {
    const report: RowReport = { total: rows.length, done: 0, failed: 0, rows: [] };
    for (const [index, row] of rows.entries()) {
      let entry = this.pending.get(row.id);
      let submitted = false;
      try {
        if (entry) {
          // 결과를 먼저 확인한다. 사용자가 고친 입력을 원 키에 붙이거나 새 키로 자동 재전송하지 않는다.
          const checked = await this.send<{ status: string }>(this.contract.getResult, this.contract.resultBody?.(entry.command.commandKey) ?? { commandKey: entry.command.commandKey, ownerFeature: this.contract.feature });
          if (checked.status === 'APPLIED') {
            this.pending.delete(row.id); this.persist(); report.done += 1; continue;
          }
          if (JSON.stringify(row.data) !== JSON.stringify(entry.row.data) || row.state !== entry.row.state)
            throw new Error(label(this.contract.changedMessage));
        } else {
          const action = row.state === 'D' ? 'remove' : 'save';
          const command: Command = { commandKey: crypto.randomUUID(), ...this.contract.encode(row, reason) };
          entry = { row: structuredClone(row), command, action };
          this.pending.set(row.id, entry); this.persist();
        }
        submitted = true;
        await this.send(this.contract[entry.action], entry.command);
        this.pending.delete(row.id); this.persist(); report.done += 1;
      } catch (error) {
        // 4xx는 업무 미반영이 확정됐다. 통신/5xx는 원 명령을 보존해 결과 확인 뒤에만 재수신한다.
        if (submitted && error instanceof ApiError && error.status >= 400 && error.status < 500 && error.code !== 'err.managed.commandExpired') {
          this.pending.delete(row.id); this.persist();
        }
        report.failed += 1;
        report.rows.push({ index, fields: [{ field: this.contract.field, message: error instanceof Error ? error.message : label(this.contract.pendingMessage) }] });
      }
    }
    return report;
  }
}
