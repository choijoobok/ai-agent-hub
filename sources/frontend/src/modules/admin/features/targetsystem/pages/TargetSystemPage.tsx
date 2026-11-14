// @feat FEAT-001
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Input, Modal, Space, Table } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { DetailTabs } from '@/shared/grid/DetailTabs';
import { SplitPane } from '@/shared/grid/SplitPane';
import { useGridController } from '@/shared/grid/useGridController';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { auditColumns } from '@/shared/grid/auditColumns';
import { toRows, type GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi, SearchCondition } from '@/shared/grid/types';
import { SearchForm, type SearchFormHandle } from '@/shared/searchform/SearchForm';
import { useSearchFormHeader } from '@/shared/searchform/useSearchFormHeader';
import { useAuthStore } from '@/shared/stores/authStore';
import { hasPermission } from '@/shared/authz/permission';
import { useTabIdentity } from '@/layouts/ShellLayout/tabContext';
import { post, type RowReport } from '@/shared/api';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { TARGET_ENDPOINTS as E } from '../api/endpoints';
import { TargetCommands, CleanupCommands } from '../api/targetSystemApi';
import type { SystemRow, ConnectionRow, ApiRow, References, CleanupQuery, CleanupPreview } from '../types';

const text = (value: unknown) => typeof value === 'string' ? value : '';
const MENU = 'HUB_TARGET_SYSTEM';
const LOCAL: GridApi<ConnectionRow> = {};
const hash = (rows: GridRow<ConnectionRow>[]) => JSON.stringify(rows.map(({ data }) => data.metadataOverrides));
const mismatch = (value: boolean | null) => label(value === true ? MSG.TARGET_MISSING : value === false ? MSG.TARGET_MATCHED : MSG.GROUP_UNKNOWN);
interface Detail { rows: GridRow<ConnectionRow>[]; original: GridRow<ConnectionRow>[]; baseline: string }

export function TargetSystemPage() {
  const { message } = App.useApp();
  const search = useRef<SearchFormHandle>(null); const header = useSearchFormHeader();
  const confirmDiscard = useConfirmDiscard(); const identity = useTabIdentity();
  const actor = useAuthStore((s) => s.user?.userId ?? '');
  const permissions = useAuthStore((s) => s.permissions);
  const canSave = hasPermission(permissions, MENU, 'READ') && hasPermission(permissions, MENU, 'SAVE');
  const canExecute = hasPermission(permissions, MENU, 'READ') && hasPermission(permissions, MENU, 'EXECUTE');
  const key = `target-system/${actor}/${identity?.tabKey ?? 'standalone'}`;
  const commands = useMemo(() => new TargetCommands(`${key}/display`, sessionStorage), [key]);
  const cleanupCommands = useMemo(() => new CleanupCommands(`${key}/cleanup`, sessionStorage), [key]);
  const drafts = useRef(new Map<string, Detail>()); const serial = useRef(0); const detailOwner = useRef<string | null>(null);
  const appliedConditions = useRef<SearchCondition[]>([]); const reasonRef = useRef('');
  const [selectedId, setSelectedId] = useState<string | null>(null); const [loadedId, setLoadedId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false); const [pending, setPending] = useState(commands.hasPending() || cleanupCommands.hasPending());
  const [error, setError] = useState(''); const [tab, setTab] = useState('connections');
  const [references, setReferences] = useState<References | null>(null);
  const [dialog, setDialog] = useState<'save' | 'cleanup' | null>(null); const [reason, setReason] = useState('');
  const [preview, setPreview] = useState<CleanupPreview | null>(null); const [saved, setSaved] = useState(0);
  const blocked = busy || pending;
  const fail = (value: unknown) => setError(value instanceof Error ? value.message : label(MSG.TARGET_FAILED));
  const columns = useMemo<ColumnDef<SystemRow>[]>(() => [
    { field: 'id', header: MSG.TARGET_ID, type: 'bigint', width: 120, visible: false, sortable: true },
    { field: 'systemKey', header: MSG.TARGET_KEY, type: 'text', width: 180, sortable: true },
    { field: 'systemName', header: MSG.TARGET_NAME, type: 'text', width: 230, sortable: true, editable: (row) => !blocked && row.id === loadedId,
      patch: (row, value) => ({ systemName: text(value) || row.yamlSystemName || row.systemKey, systemNameOverride: text(value) || null }) },
    { field: 'description', header: MSG.TARGET_DESCRIPTION, type: 'text', editor: 'largeText', width: 300, editable: (row) => !blocked && row.id === loadedId,
      patch: (_row, value) => ({ description: text(value), descriptionOverride: text(value) }) },
    { field: 'configurationMismatch', header: MSG.TARGET_MISMATCH, type: 'text', width: 150, derive: (row) => mismatch(row.configurationMismatch) },
    { field: 'version', header: MSG.CHAT_HELP_REVISION, type: 'bigint', width: 100 },
    ...auditColumns<SystemRow>(),
  ], [blocked, loadedId]);
  const collect = useCallback(() => {
    const conditions = (search.current?.collect() ?? []).map((c) => c.field !== 'configurationMismatch' ? c : c.value === 'UNKNOWN' ? { field: c.field, op: 'isNull' } : { ...c, value: c.value === 'Y' });
    appliedConditions.current = conditions; serial.current += 1; detailOwner.current = null;
    drafts.current.clear(); setSelectedId(null); setLoadedId(null); setReferences(null); setError('');
    return Promise.resolve(conditions);
  }, []);
  const api = useMemo<GridApi<SystemRow>>(() => ({ read: blocked ? undefined : { url: E.getList, emptyNotice: true },
    save: { url: E.save, confirm: false, reloadAfter: false, perRow: { after: 'keepFailed' },
      submit: async (payload) => {
        setBusy(true);
        const report: RowReport = { total: 0, done: 0, failed: 0, rows: [] };
        try {
          const rows = (payload as { rows: GridRow<SystemRow>[] }).rows; report.total = rows.length;
          for (const [index, row] of rows.entries()) {
            const children = drafts.current.get(row.id)?.rows.map((r) => r.data) ?? row.data.connections;
            const result = await commands.save([{ ...row, data: { ...row.data, connections: children } }], reasonRef.current);
            report.done += result.done; report.failed += result.failed; report.rows.push(...result.rows.map((r) => ({ ...r, index })));
          }
          return report;
        } finally { setPending(commands.hasPending() || cleanupCommands.hasPending()); setBusy(false); }
      }, onSuccess: () => { reasonRef.current = ''; setSaved((n) => n + 1); },
    } }), [blocked, commands, cleanupCommands]);
  const master = useGridController<SystemRow>({ menuCd: MENU, columns, api, params: collect, toSaveRow: (row) => row });
  const selected = master.rows.find((row) => row.id === selectedId);
  const detailColumns: ColumnDef<ConnectionRow>[] = [
    { field: 'connectionId', header: MSG.TARGET_CONNECTION_KEY, type: 'text', width: 160 },
    { field: 'connectionName', header: MSG.TARGET_CONNECTION_NAME, type: 'text', width: 210, editable: !blocked && !!loadedId,
      patch: (row, value) => ({ connectionName: text(value) || row.yamlName || row.connectionId, metadataOverrides: { ...row.metadataOverrides, nameOverride: text(value) || null } }) },
    { field: 'description', header: MSG.TARGET_DESCRIPTION, type: 'text', editor: 'largeText', width: 240, editable: !blocked && !!loadedId,
      patch: (row, value) => ({ description: text(value), metadataOverrides: { ...row.metadataOverrides, descriptionOverride: text(value) } }) },
    { field: 'currentDisplay', header: MSG.TARGET_URL, type: 'text', width: 280, derive: (row) => row.currentDisplay.baseUrl ?? label(MSG.GROUP_UNKNOWN) },
    { field: 'authenticationType', header: MSG.TARGET_AUTH, type: 'text', width: 130, derive: (row) => row.currentDisplay.authenticationType ?? label(MSG.GROUP_UNKNOWN) },
    { field: 'configurationMismatch', header: MSG.TARGET_MISMATCH, type: 'text', width: 150, derive: (row) => mismatch(row.configurationMismatch) },
    { field: 'testAllowed', header: MSG.TARGET_TEST, type: 'text', renderer: 'link', width: 130,
      display: () => label(MSG.TARGET_TEST), clickEnabled: (row) => row.testAllowed && canExecute && !blocked && !master.dirty, onClick: (row) => void test(row) },
    ...auditColumns<ConnectionRow>(),
  ];
  const detail = useGridController<ConnectionRow>({ menuCd: MENU, columns: detailColumns, api: LOCAL, saveOwner: master, editable: canSave && !blocked && !!loadedId });
  const apiColumns = useMemo<ColumnDef<ApiRow>[]>(() => [
    { field: 'id', header: MSG.TARGET_API_ID, type: 'bigint', width: 130 }, { field: 'name', header: MSG.TARGET_API_NAME, type: 'text', width: 260 },
    { field: 'apiKind', header: MSG.TARGET_API_KIND, type: 'text', width: 150 }, { field: 'connectionId', header: MSG.TARGET_CONNECTION_KEY, type: 'text', width: 180 },
    { field: 'useYn', header: MSG.SEARCH_TAG_USE, type: 'text', width: 110 },
  ], []);
  const usedApis = useGridController<ApiRow>({ menuCd: MENU, columns: apiColumns, editable: false });
  const edit = master.edit; const setDetailRows = detail.setRows; const setApiRows = usedApis.setRows;
  useEffect(() => {
    if (busy || !selectedId || detailOwner.current !== selectedId) return;
    const draft = drafts.current.get(selectedId); if (!draft) return;
    draft.rows = detail.rows; const changed = hash(detail.rows);
    edit(selectedId, { detailHash: changed === draft.baseline ? undefined : changed });
  }, [detail.rows, edit, busy, selectedId]);
  const previousHash = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (previousHash.current && !selected?.data.detailHash && selectedId) { const draft = drafts.current.get(selectedId); if (draft) setDetailRows(draft.original); }
    previousHash.current = selected?.data.detailHash;
  }, [selected?.data.detailHash, selectedId, setDetailRows]);
  const readReferences = async (id: string, ticket: number) => {
    const refs = await post<References>(E.getUsedApis, { id });
    if (ticket === serial.current) { setReferences(refs); setApiRows(toRows(refs.rows)); }
  };
  const select = async (row: GridRow<SystemRow> | null, force = false) => {
    if (blocked) return false;
    if (!force && row?.id === selectedId) return true;
    if (master.dirty && !(await confirmDiscard())) return false;
    if (selected?.state) master.revert(selected.id);
    const ticket = ++serial.current; detailOwner.current = null; setLoadedId(null); setSelectedId(row?.id ?? null);
    setDetailRows([]); setApiRows([]); setReferences(null); setError('');
    if (!row) return true;
    setBusy(true);
    try {
      const found = await post<SystemRow>(E.getOne, { id: row.data.id });
      if (ticket !== serial.current) return false;
      if (found.version !== row.data.version) throw new Error(label(MSG.TARGET_STALE));
      const rows = toRows(found.connections); drafts.current.set(row.id, { rows, original: rows, baseline: hash(rows) });
      detailOwner.current = row.id; setDetailRows(rows); setLoadedId(found.id);
      if (tab === 'apis') await readReferences(found.id, ticket);
    } catch (e) { if (ticket === serial.current) fail(e); }
    finally { if (ticket === serial.current) setBusy(false); }
    return true;
  };
  async function test(row: ConnectionRow) {
    if (blocked || !canExecute || !row.testAllowed || master.dirty) { void message.warning(label(MSG.TARGET_TEST_BLOCKED)); return; }
    setBusy(true); setError('');
    try { const result = await post<{ status: string; checkedAt: string; elapsedMs: number }>(E.testConnection, { systemId: row.systemId, connectionId: row.connectionId, appliedSettingsId: row.appliedSettingsId });
      void message.info(`${label(result.status === 'SUCCESS' ? MSG.TARGET_TEST_SUCCESS : MSG.TARGET_TEST_FAILED)} ${new Date(result.checkedAt).toLocaleString()} · ${result.elapsedMs} ms`, 8);
    } catch (e) { fail(e); } finally { setBusy(false); }
  }
  const startCleanup = async (scope: CleanupQuery['scope']) => {
    if (!canSave || blocked || master.dirty || scope === 'CONNECTION' && !selected) return;
    setBusy(true); setError('');
    try { const result = await post<CleanupPreview>(E.previewCleanup, { scope, parentId: scope === 'CONNECTION' ? selected!.data.id : null, conditions: scope === 'SYSTEM' ? appliedConditions.current : [] });
      setPreview(result); setReason(''); setDialog('cleanup');
    } catch (e) { fail(e); } finally { setBusy(false); }
  };
  const applyCleanup = async (retry = false) => {
    if (!canSave || busy || !retry && (!preview?.executable || !reason.trim())) return;
    setBusy(true); setError('');
    try {
      const row = retry ? cleanupCommands.restoredRows()[0] : { id: preview!.previewToken, state: 'U' as const, original: null,
        data: { previewToken: preview!.previewToken, query: preview!.query, confirmedTargets: preview!.targets.map((t) => t.target) } };
      const result = await cleanupCommands.save([row], reason.trim());
      if (result.failed) throw new Error(result.rows[0]?.fields?.[0]?.message ?? label(MSG.TARGET_FAILED));
      setDialog(null); setPreview(null); setReason(''); setSaved((n) => n + 1);
    } catch (e) { fail(e); } finally { setPending(commands.hasPending() || cleanupCommands.hasPending()); setBusy(false); }
  };
  const read = master.read; const setMasterRows = master.setRows; const restored = useRef(false); const refreshed = useRef(0);
  useEffect(() => { if (!restored.current) { restored.current = true; if (commands.hasPending()) setMasterRows(commands.restoredRows()); else if (!cleanupCommands.hasPending()) void read(); } }, [commands, cleanupCommands, read, setMasterRows]);
  useEffect(() => { if (!busy && !pending && saved !== refreshed.current) { refreshed.current = saved; serial.current += 1; detailOwner.current = null; setMasterRows([]); setDetailRows([]); setApiRows([]); void read(true); } }, [busy, pending, saved, read, setMasterRows, setDetailRows, setApiRows]);
  const save = async () => { if (pending) { if (commands.hasPending()) await master.save(); return; } if (canSave && !busy && master.dirty) { setReason(''); setDialog('save'); } };
  const reset = () => { if (!selected || blocked || !canSave) return; master.edit(selected.id, { systemNameOverride: null, descriptionOverride: null, systemName: selected.data.yamlSystemName ?? selected.data.systemKey, description: selected.data.yamlDescription });
    for (const row of detail.rows) detail.edit(row.id, { metadataOverrides: { nameOverride: null, descriptionOverride: null }, connectionName: row.data.yamlName ?? row.data.connectionId, description: row.data.yamlDescription }); };
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{ field: 'systemName', label: MSG.TARGET_NAME, type: 'text', op: 'contains' },
      { field: 'configurationMismatch', label: MSG.TARGET_MISMATCH, type: 'select', op: 'eq', options: [{ value: 'Y', text: label(MSG.TARGET_MISSING) }, { value: 'N', text: label(MSG.TARGET_MATCHED) }, { value: 'UNKNOWN', text: label(MSG.GROUP_UNKNOWN) }] }]}
      onSubmit={() => { if (!blocked) void master.read(); }} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    <Alert type="info" title={label(MSG.TARGET_HELP)} showIcon />
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon action={cleanupCommands.hasPending() ? <Button disabled={busy || !canSave} onClick={() => void applyCleanup(true)}>{label(MSG.TARGET_RESULT)}</Button> : undefined} />}
    {error && <Alert type="error" title={error} showIcon />}
    <Space><Button disabled={!canSave || blocked || master.dirty} onClick={() => void startCleanup('SYSTEM')}>{label(MSG.TARGET_CLEAN_SYSTEM)}</Button>
      <Button disabled={!canSave || blocked || master.dirty || !selected} onClick={() => void startCleanup('CONNECTION')}>{label(MSG.TARGET_CLEAN_CONNECTION)}</Button>
      <Button disabled={!canSave || blocked || !loadedId} onClick={reset}>{label(MSG.TARGET_RESET)}</Button></Space>
    <div style={{ flex: 1, minHeight: 0 }}><SplitPane storageKey="target-system"
      first={(collapsed, expand) => <DataGrid title={MSG.TARGET_TITLE} menuCd={MENU} gridId="master" controller={{ ...master, save }} onSelect={select} deletable={false} collapsed={collapsed} onExpand={expand} excel={{ pageOnly: true }} />}
      second={() => <DetailTabs activeKey={tab} onChange={(next) => { setTab(next); if (next === 'apis' && selected && !references && !busy) { setBusy(true); void readReferences(selected.data.id, serial.current).catch(fail).finally(() => setBusy(false)); } }} items={[
        { key: 'connections', label: label(MSG.TARGET_CONNECTIONS), forceRender: true, children: <DataGrid title={MSG.TARGET_CONNECTIONS} menuCd={MENU} gridId="connections" controller={detail} deletable={false} onRead={() => void select(selected ?? null, true)} excel={{ pageOnly: true }} /> },
        { key: 'apis', label: label(MSG.TARGET_APIS), forceRender: true, children: <FillColumn>{references && references.state !== 'READY' && <Alert type="info" title={label(references.state === 'NOT_CONNECTED' ? MSG.TARGET_NOT_CONNECTED : MSG.GROUP_UNKNOWN)} />}
          <DataGrid title={MSG.TARGET_APIS} menuCd={MENU} gridId="apis" controller={usedApis} onRead={() => { if (selected && !blocked) void readReferences(selected.data.id, serial.current).catch(fail); }} excel={false} /></FillColumn> },
      ]} />} /></div>
    <Modal open={dialog !== null} title={label(dialog === 'save' ? MSG.TARGET_SAVE : MSG.TARGET_CLEAN_CONFIRM)} confirmLoading={busy} closable={!busy} keyboard={!busy} mask={{ closable: !busy }}
      onCancel={() => { if (!busy) { setDialog(null); setReason(''); } }} okText={label(MSG.GRID_CONFIRM_OK)} cancelText={label(MSG.COMMON_CANCEL)}
      okButtonProps={{ disabled: !canSave || pending || !reason.trim() || dialog === 'cleanup' && !preview?.executable }}
      onOk={() => { if (dialog === 'cleanup') void applyCleanup(); else { reasonRef.current = reason.trim(); setDialog(null); void master.save(); } }}>
      {dialog === 'cleanup' && <><p>{label(MSG.TARGET_CLEAN_HELP)}</p><Table size="small" rowKey={(r) => r.target.id} dataSource={preview?.targets} pagination={false} scroll={{ y: 260 }} columns={[
        { title: label(MSG.TARGET_NAME), dataIndex: 'name' }, { title: label(MSG.TARGET_KEY), render: (_, r) => r.target.id },
        { title: label(MSG.TARGET_CLEAN_STATE), render: (_, r) => label(r.reason ? MSG.TARGET_CLEAN_BLOCKED : MSG.TARGET_CLEAN_READY) },
      ]} /></>}
      <Input.TextArea aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000} disabled={busy || pending} onChange={(e) => setReason(e.target.value)} />
    </Modal>
  </FillColumn>;
}
