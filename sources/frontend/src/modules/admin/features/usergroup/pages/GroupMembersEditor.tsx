// @feat FEAT-005
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Input, Select, Space, Switch } from 'antd';
import { DataGrid } from '@/shared/grid/DataGrid';
import { FillColumn } from '@/shared/components/FillColumn';
import { useGridController } from '@/shared/grid/useGridController';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { auditColumns } from '@/shared/grid/auditColumns';
import { addRow, toRows, type GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi } from '@/shared/grid/types';
import { useAuthStore } from '@/shared/stores/authStore';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { hasPermission } from '@/shared/authz/permission';
import { fetchPortalDetail, portalCandidates, PortalScopeCommands } from '../../portaluser/api/portalUserApi';
import { PORTAL_USER_ENDPOINTS } from '../../portaluser/api/endpoints';
import type { PortalDetailRow, PortalUserRow } from '../../portaluser/types';
import type { GroupProjection } from '../types';

export interface GroupMemberDraft { rows: GridRow<PortalDetailRow>[]; reason: string; deactivate: boolean }
interface Props { groupId: string; detail?: GroupProjection; onRead: () => void; cache: Map<string, GroupMemberDraft> }
export function GroupMembersEditor({ groupId, detail, onRead, cache }: Props) {
  const actor = useAuthStore((s) => s.user?.userId ?? '');
  const permissions = useAuthStore((s) => s.permissions);
  const allowed = hasPermission(permissions, 'HUB_PORTAL_USER', 'READ');
  const [reason, setReason] = useState(cache.get(groupId)?.reason ?? '');
  const [deactivate, setDeactivate] = useState(cache.get(groupId)?.deactivate ?? false);
  const [error, setError] = useState('');
  const [candidates, setCandidates] = useState<PortalUserRow[]>([]);
  const [candidate, setCandidate] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const request = useRef(0);
  const loaded = useRef(false);
  const askDiscard = useConfirmDiscard();
  const initialCommands = useMemo(() => {
    const result = new Map<string, PortalScopeCommands>();const prefix = `portal-group/${actor}/${groupId}/`;
    for (let index = 0; index < sessionStorage.length; index += 1) {
      const key = sessionStorage.key(index);if (key?.startsWith(prefix)) result.set(key.slice(prefix.length), new PortalScopeCommands(key, sessionStorage));
    }
    return result;
  }, [actor, groupId]);
  const commands = useRef(initialCommands);
  const [pending, setPending] = useState([...initialCommands.values()].some((c) => c.hasPending()));
  const commandFor = (id: string) => {
    let command = commands.current.get(id);
    if (!command) { command = new PortalScopeCommands(`portal-group/${actor}/${groupId}/${id}`, sessionStorage);commands.current.set(id, command); }
    return command;
  };
  const writable = detail?.state === 'READY' && allowed && hasPermission(permissions, 'HUB_PORTAL_USER', 'SAVE') && !busy && !pending;
  const columns = useMemo<ColumnDef<PortalDetailRow>[]>(() => [
    { field: 'loginId', header: MSG.GROUP_LOGIN, type: 'text', width: 160 },
    { field: 'userNm', header: MSG.PORTAL_NAME, type: 'text', width: 150 },
    { field: 'groupId', header: MSG.GROUP_ID, type: 'bigint', width: 130 },
    { field: 'activeYn', header: MSG.GROUP_ACTIVE, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), width: 120, editable: writable },
    { field: 'memo', header: MSG.GROUP_MEMO, type: 'text', width: 260, required: true, editable: writable, maxLength: 2000 },
    ...auditColumns<PortalDetailRow>(),
  ], [writable]);
  const api: GridApi<PortalDetailRow> = { save: { url: PORTAL_USER_ENDPOINTS.save, reloadAfter: false, perRow: { after: 'keepFailed' },
    validate: { grid: (rows) => !reason.trim() && !pending ? rows.map((r) => ({ rowId: r.id, message: label(MSG.GROUP_REASON_REQUIRED) })) : [] },
    submit: async (payload) => {
      const rows = (payload as { rows: GridRow<PortalDetailRow>[] }).rows;
      const report = { total: rows.length, done: 0, failed: 0, rows: [] as { index: number; fields: { field: string; message: string }[] }[] };
      for (const [index, row] of rows.entries()) {
        const id = String(row.data.portalUserId);const commands = commandFor(id);
        const result = await commands.save([{ id: `${groupId}/${id}`, state: 'U', original: null, data: { portalUserId: id, revision: String(row.data.revision), scope: 'memberships', rows: [row], deactivateUser: deactivate } }], reason.trim());
        report.done += result.done;report.failed += result.failed;
        report.rows.push(...result.rows.map((r) => ({ ...r, index })));
      }
      setPending([...commands.current.values()].some((c) => c.hasPending()));return report;
    }, onSuccess: () => { cache.delete(groupId);loaded.current = false;onRead(); },
  } };
  const grid = useGridController<PortalDetailRow>({ menuCd: 'HUB_PORTAL_USER', columns, api, editable: writable, toSaveRow: (row) => row });
  const setRows = grid.setRows;
  useEffect(() => {
    if (loaded.current || !detail) return;
    const stored = cache.get(groupId);let rows = stored?.rows ?? toRows(detail.rows.map((row) => ({ ...row, groupId })));
    for (const command of commands.current.values()) {
      const restored = command.restoredRows()[0]?.data;
      if (restored) { rows = [...rows.filter((r) => r.data.portalUserId !== restored.portalUserId), ...restored.rows]; }
    }
    setRows(rows);loaded.current = true;
  }, [actor, cache, commands, detail, groupId, setRows]);
  useEffect(() => { if (loaded.current) cache.set(groupId, { rows: grid.rows, reason, deactivate }); }, [cache, deactivate, grid.rows, groupId, reason]);
  const search = async (login: string) => {
    const serial = ++request.current;
    try {const result = await portalCandidates(login);if (serial === request.current) setCandidates(result.content);}
    catch (failure) {if (serial === request.current) {setCandidates([]);setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE));}}
  };
  const add = async () => {
    if (!candidate || !writable || !grid.canSave) return;setBusy(true);setError('');
    try {
      if (grid.rows.some((r) => r.data.portalUserId === candidate)) { setError(label(MSG.PORTAL_USER_REQUIRED));return; }
      const found = await fetchPortalDetail(candidate, 'memberships');const existing = found.rows.find((r) => r.groupId === groupId);
      const data = { portalUserId: candidate, revision: found.user.revision, loginId: found.user.loginId, userNm: found.user.userNm, groupId, activeYn: 'Y', memo: '', ...existing };
      setRows(addRow(grid.rows, data));setCandidate(null);
    } catch (failure) {setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE));}
    finally {setBusy(false);}
  };
  const reload = async () => {if (pending || grid.dirty && !(await askDiscard())) return;cache.delete(groupId);loaded.current = false;onRead();};
  return <FillColumn>
    {detail?.state !== 'READY' && <Alert type="info" title={label(MSG.PORTAL_NOT_CONNECTED)} showIcon />}
    {error && <Alert type="warning" title={error} showIcon />}
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    {allowed && <Space.Compact block>
      <Select style={{ minWidth: 240 }} showSearch={{ filterOption: false, onSearch: (text) => void search(text) }} placeholder={label(MSG.PORTAL_USER_PICK)} value={candidate} disabled={!writable || !grid.canSave}
        onOpenChange={(open) => { if (open) void search(''); }} onChange={setCandidate} options={candidates.map((u) => ({ value: u.portalUserId!, label: `${u.loginId} · ${u.userNm}` }))} />
      <Button disabled={!candidate || !writable || !grid.canSave} onClick={() => void add()}>{label(MSG.GRID_ADD_ROW)}</Button>
      <Input value={reason} aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} disabled={pending} onChange={(e) => setReason(e.target.value)} />
      <Space style={{ whiteSpace: 'nowrap', paddingInline: 8 }}><Switch checked={deactivate} disabled={!writable || !grid.canSave} onChange={setDeactivate} />{label(MSG.PORTAL_DEACTIVATE_TOGETHER)}</Space>
    </Space.Compact>}
    <DataGrid title={MSG.PORTAL_GROUP_USER} menuCd="HUB_PORTAL_USER" gridId="group-members" controller={grid} onRead={() => void reload()} excel={{ pageOnly: true }}
      deletable={{ enabled: (row) => writable && row.state === 'C', reason: () => label(MSG.PORTAL_KEEP_HISTORY) }} />
  </FillColumn>;
}
