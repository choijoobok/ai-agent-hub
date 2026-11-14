// @feat FEAT-002
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Input, Modal, Select, Table } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { SplitPane } from '@/shared/grid/SplitPane';
import { DetailTabs } from '@/shared/grid/DetailTabs';
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
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { DATA_SOURCE_ENDPOINTS as E } from '../api/endpoints';
import { DataSourceCommands, DataSourceCleanupCommands, loadSource, loadTimezones, previewCleanup } from '../api/dataSourceApi';
import type { SourceRow, Group, Guide, Projection, Detail, Preview } from '../types';
const MENU = 'HUB_DATA_SOURCE';
const LOCAL = {};
const live = <T,>(rows: GridRow<T>[]) => rows.filter(r => r.state !== 'D').map(r => r.data);
const mismatch = (v: boolean | null) => label(v === true ? MSG.TARGET_MISSING : v === false ? MSG.TARGET_MATCHED : MSG.GROUP_UNKNOWN);
const fingerprint = (groups: Group[], guides: Guide[]) => JSON.stringify({groups, guides});

export function DataSourcePage() {
  const search = useRef<SearchFormHandle>(null); const header = useSearchFormHeader(); const confirmDiscard = useConfirmDiscard();
  const actor = useAuthStore(s => s.user?.userId ?? ''); const permissions = useAuthStore(s => s.permissions); const identity = useTabIdentity();
  const canSave = hasPermission(permissions, MENU, 'READ') && hasPermission(permissions, MENU, 'SAVE');
  const key = `data-source/${actor}/${identity?.tabKey ?? 'standalone'}`;
  const commands = useMemo(() => new DataSourceCommands(`${key}/save`, sessionStorage), [key]);
  const cleanup = useMemo(() => new DataSourceCleanupCommands(`${key}/cleanup`, sessionStorage), [key]);
  const [busy, setBusy] = useState(false); const [pending, setPending] = useState(commands.hasPending() || cleanup.hasPending());
  const [selectedRowId, setSelectedRowId] = useState<string | null>(null);
  const [loadedId, setLoadedId] = useState<string | null>(null); const [error, setError] = useState(''); const [tab, setTab] = useState('groups');
  const [zones, setZones] = useState<string[] | null>(null); const [detail, setDetail] = useState<Detail | null>(null);
  const [dialog, setDialog] = useState<'save' | 'cleanup' | 'groups' | null>(null); const [reason, setReason] = useState(''); const [picked, setPicked] = useState<string[]>([]);
  const [preview, setPreview] = useState<Preview | null>(null); const [saved, setSaved] = useState(0);
  const serial = useRef(0); const reasonRef = useRef(''); const applied = useRef<SearchCondition[]>([]); const baseline = useRef('');
  const children = useRef<{groups: Group[]; guides: Guide[]}>({groups: [], guides: []}); const previousDirty = useRef(false);
  const blocked = busy || pending; const editable = canSave && !blocked && !!loadedId;
  const fail = (e: unknown) => setError(e instanceof Error ? e.message : label(MSG.TARGET_FAILED));
  const columns = useMemo<ColumnDef<SourceRow>[]>(() => [
    {field: 'id', header: MSG.GROUP_ASSET_ID, type: 'bigint', visible: false, width: 120},
    {field: 'dataSourceKey', header: MSG.DS_KEY, type: 'text', width: 160, sortable: true},
    {field: 'alias', header: MSG.DS_ALIAS, type: 'text', width: 180, sortable: true},
    {field: 'dbmsCode', header: MSG.DS_DBMS, type: 'text', width: 140, sortable: true},
    {field: 'dbmsVersion', header: MSG.DS_VERSION, type: 'text', width: 140, editable: r => editable && r.id === loadedId},
    {field: 'sourceType', header: MSG.DS_TYPE, type: 'code', editor: 'richSelect', width: 120, options: [{value: 'DB', text: 'DB'}, {value: 'DW', text: 'DW'}], editable: r => editable && r.id === loadedId},
    {field: 'syncMethodNote', header: MSG.DS_SYNC_NOTE, type: 'text', editor: 'largeText', width: 230, editable: r => editable && r.id === loadedId && r.sourceType === 'DW'},
    {field: 'timeZoneId', header: MSG.DS_TIMEZONE, type: 'code', editor: 'richSelect', width: 190, options: zones ? [{value: '', text: label(MSG.DS_UNSPECIFIED)}, ...zones.map(value => ({value, text: value}))] : [], patch: (_row, value) => ({timeZoneId: typeof value === 'string' && value ? value : null}), editable: r => editable && r.id === loadedId && zones !== null},
    {field: 'dataBasis', header: MSG.DS_BASIS, type: 'text', editor: 'largeText', width: 220, editable: r => editable && r.id === loadedId},
    {field: 'description', header: MSG.TARGET_DESCRIPTION, type: 'text', editor: 'largeText', width: 260, editable: r => editable && r.id === loadedId},
    {field: 'configurationMismatch', header: MSG.TARGET_MISMATCH, type: 'text', width: 160, derive: r => mismatch(r.configurationMismatch)},
    {field: 'connectionStatus', header: MSG.DS_LOADED, type: 'text', width: 150},
    {field: 'parserSupport', header: MSG.DS_SQL_UNKNOWN, type: 'text', width: 220, derive: () => label(MSG.DS_SQL_UNKNOWN)},
    {field: 'managementVersion', header: MSG.CHAT_HELP_REVISION, type: 'bigint', width: 100}, ...auditColumns<SourceRow>(),
  ], [editable, loadedId, zones]);
  const collect = useCallback(() => {
    applied.current = (search.current?.collect() ?? []).map(c => c.field !== 'configurationMismatch' ? c : c.value === 'UNKNOWN' ? {field: c.field, op: 'isNull'} : {...c, value: c.value === 'Y'});
    ++serial.current; setSelectedRowId(null); setLoadedId(null); setDetail(null); setError(''); return Promise.resolve(applied.current);
  }, []);
  const api = useMemo<GridApi<SourceRow>>(() => ({read: blocked ? undefined : {url: E.getList, emptyNotice: true}, save: {url: E.save, confirm: false, reloadAfter: false, perRow: {after: 'keepFailed'}, submit: async payload => {
    setBusy(true);
    try {
      const rows = (payload as {rows: GridRow<SourceRow>[]}).rows.map(r => ({...r, data: {...r.data, values: r.data.values ?? {dbmsVersion: r.data.dbmsVersion, sourceType: r.data.sourceType, syncMethodNote: r.data.syncMethodNote, timeZoneId: r.data.timeZoneId ?? null, description: r.data.description, dataBasis: r.data.dataBasis, applicableGroups: children.current.groups.map(g => ({groupId: g.groupId, mappingActive: g.mappingActive})), queryGuides: children.current.guides}}}));
      return await commands.save(rows, reasonRef.current);
    } finally {setPending(commands.hasPending() || cleanup.hasPending()); setBusy(false);}
  }, onSuccess: () => setSaved(n => n + 1)}}), [blocked, commands, cleanup]);
  const master = useGridController<SourceRow>({menuCd: MENU, columns, api, params: collect, toSaveRow: r => r});
  const guideColumns: ColumnDef<Guide>[] = [{field: 'guideId', header: MSG.GROUP_ASSET_ID, type: 'bigint', width: 130, visible: false}, {field: 'title', header: MSG.DS_GUIDE_TITLE, type: 'text', width: 240, editable, required: true}, {field: 'description', header: MSG.DS_GUIDE_BODY, type: 'text', editor: 'largeText', width: 700, editable, required: true}];
  const guides = useGridController<Guide>({menuCd: MENU, columns: guideColumns, api: LOCAL, saveOwner: master, editable, addRow: {canAdd: () => editable ? true : {ok: false, message: label(MSG.GROUP_UNKNOWN)}, defaults: () => ({clientKey: crypto.randomUUID(), title: '', description: ''})}});
  const groupColumns: ColumnDef<Group>[] = [{field: 'groupId', header: MSG.GROUP_ID, type: 'bigint', width: 130}, {field: 'groupName', header: MSG.GROUP_NAME, type: 'text', width: 260}, {field: 'mappingActive', header: MSG.DS_GROUP_ACTIVE, type: 'ynFlag', width: 150, editable}];
  const groups = useGridController<Group>({menuCd: MENU, columns: groupColumns, api: LOCAL, saveOwner: master, editable});
  const refs = useGridController<Record<string, unknown>>({menuCd: MENU, columns: [{field: 'id', header: MSG.GROUP_ASSET_ID, type: 'bigint', width: 130}, {field: 'name', header: MSG.GROUP_ASSET_NAME, type: 'text', width: 300}], editable: false});
  const edit = master.edit; const setGuides = guides.setRows; const setGroups = groups.setRows; const setRefs = refs.setRows;
  useEffect(() => {
    if (busy || !loadedId || !selectedRowId || !detail) return;
    children.current = {groups: live(groups.rows), guides: live(guides.rows)};
    const hash = fingerprint(children.current.groups, children.current.guides);
    edit(selectedRowId, {detailHash: hash === baseline.current ? undefined : hash});
  }, [groups.rows, guides.rows, loadedId, selectedRowId, detail, busy, edit]);
  useEffect(() => {
    if (previousDirty.current && !master.dirty && detail && loadedId && !busy) {
      const sourceGroups = detail.values.applicableGroups.map(g => ({...g, groupName: detail.groups.find(x => x.groupId === g.groupId)?.groupName}));
      setGroups(toRows(sourceGroups)); setGuides(toRows(detail.values.queryGuides));
    }
    previousDirty.current = master.dirty;
  }, [master.dirty, detail, loadedId, busy, setGroups, setGuides]);
  const select = async (row: GridRow<SourceRow> | null, force = false) => {
    if (blocked || !force && row?.id === selectedRowId) return false;
    if (master.dirty && !(await confirmDiscard())) return false;
    if (selectedRowId) master.revert(selectedRowId);
    const ticket = ++serial.current; setSelectedRowId(row?.id ?? null); setLoadedId(null); setDetail(null); setError(''); setGroups([]); setGuides([]); setRefs([]);
    if (!row) return true; setBusy(true);
    try {
      const found = await loadSource(row.data.id); if (ticket !== serial.current) return false;
      if (found.row.managementVersion !== row.data.managementVersion) throw new Error(label(MSG.TARGET_STALE));
      const groupRows = found.values.applicableGroups.map(g => ({...g, groupName: found.groups.find(x => x.groupId === g.groupId)?.groupName}));
      baseline.current = fingerprint(groupRows, found.values.queryGuides); children.current = {groups: groupRows, guides: found.values.queryGuides};
      setGroups(toRows(groupRows)); setGuides(toRows(found.values.queryGuides)); setRefs(toRows(found.catalogReferences.rows)); setDetail(found); setLoadedId(found.row.id);
    } catch (e) {if (ticket === serial.current) fail(e);} finally {if (ticket === serial.current) setBusy(false);} return true;
  };
  const read = master.read; const setMaster = master.setRows; const restored = useRef(false); const refreshed = useRef(0);
  useEffect(() => {if (!restored.current) {restored.current = true; if (commands.hasPending()) setMaster(commands.restoredRows()); else if (!cleanup.hasPending()) void read();} }, [commands, cleanup, read, setMaster]);
  useEffect(() => {if (!busy && !pending && saved !== refreshed.current) {refreshed.current = saved; setMaster([]); setGuides([]); setGroups([]); void read(true);} }, [saved, busy, pending, read, setMaster, setGuides, setGroups]);
  useEffect(() => {let current = true; void loadTimezones().then(z => {if (current) setZones([...new Set(z)]);}).catch(e => {if (current) fail(e);}); return () => {current = false;};}, []);
  const save = async () => {if (pending && commands.hasPending()) {await master.save(); return;} if (canSave && !busy && master.dirty) {setReason(''); setDialog('save');}};
  const startCleanup = async () => {if (!canSave || blocked || master.dirty) return; setBusy(true); try {setPreview(await previewCleanup({scope: 'DATASOURCE', parentId: null, conditions: applied.current})); setReason(''); setDialog('cleanup');} catch (e) {fail(e);} finally {setBusy(false);}};
  const applyCleanup = async (retry = false) => {
    if (!canSave || busy || !retry && (!preview?.executable || !reason.trim())) return; setBusy(true);
    try {const row = retry ? cleanup.restoredRows()[0] : {id: preview!.previewToken, state: 'U' as const, original: null, data: {previewToken: preview!.previewToken, query: preview!.query, confirmedTargets: preview!.targets.map(t => t.target)}};
      const result = await cleanup.save([row], reason.trim()); if (result.failed) throw new Error(result.rows[0]?.fields?.[0]?.message ?? label(MSG.TARGET_FAILED));setDialog(null);setSaved(n => n + 1);
    } catch (e) {fail(e);} finally {setPending(commands.hasPending() || cleanup.hasPending());setBusy(false);}
  };
  const projection: Projection | undefined = detail?.catalogReferences;
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{field: 'alias', label: MSG.DS_ALIAS, type: 'text', op: 'contains'}, {field: 'configurationMismatch', label: MSG.TARGET_MISMATCH, type: 'select', op: 'eq', options: [{value: 'Y', text: label(MSG.TARGET_MISSING)}, {value: 'N', text: label(MSG.TARGET_MATCHED)}, {value: 'UNKNOWN', text: label(MSG.GROUP_UNKNOWN)}]}]} onSubmit={() => {if (!blocked) void master.read();}} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    <Alert type="info" title={label(MSG.DS_HELP)} description={label(MSG.DS_TECHNICAL_HELP)} showIcon />
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} action={cleanup.hasPending() ? <Button onClick={() => void applyCleanup(true)} disabled={busy || !canSave}>{label(MSG.TARGET_RESULT)}</Button> : undefined} />}
    {error && <Alert type="error" title={error} />}
    <div style={{flex: 1, minHeight: 0}}><SplitPane storageKey="data-source" first={(collapsed, expand) => <DataGrid title={MSG.DS_TITLE} menuCd={MENU} gridId="master" controller={{...master, save}} controls={[{key: 'cleanup', type: 'button', slot: 'beforeTools', label: MSG.DS_CLEAN, actionCd: 'SAVE', disabled: !canSave || blocked || master.dirty, onClick: () => void startCleanup()}]} onSelect={select} deletable={false} collapsed={collapsed} onExpand={expand} excel={{pageOnly: true}} />}
      second={() => <DetailTabs activeKey={tab} onChange={setTab} items={[
        {key: 'groups', label: label(MSG.DS_GROUPS), forceRender: true, children: <DataGrid title={MSG.DS_GROUPS} menuCd={MENU} gridId="groups" controller={groups} deletable={editable} onRead={() => void select(master.rows.find(r => r.id === selectedRowId) ?? null, true)} excel={{pageOnly: true}} controls={[{key: 'add', type: 'button', slot: 'beforeRead', label: MSG.DS_GROUP_ADD, actionCd: 'SAVE', disabled: !editable, onClick: () => {setPicked([]);setDialog('groups');}}]} />},
        {key: 'catalogs', label: label(MSG.DS_CATALOGS), forceRender: true, children: <FillColumn>{projection?.state !== 'READY' && <Alert type="info" title={label(MSG.DS_CATALOG_PENDING)} />}<DataGrid title={MSG.DS_CATALOGS} menuCd={MENU} gridId="catalogs" controller={refs} deletable={false} onRead={() => void select(master.rows.find(r => r.id === selectedRowId) ?? null, true)} excel={false} /></FillColumn>},
        {key: 'columns', label: label(MSG.DS_COLUMNS), children: <Alert type="info" title={label(MSG.DS_COMPARE_PENDING)} />},
        {key: 'indexes', label: label(MSG.DS_INDEXES), children: <Alert type="info" title={label(MSG.DS_COMPARE_PENDING)} />},
        {key: 'guides', label: label(MSG.DS_GUIDES), forceRender: true, children: <DataGrid title={MSG.DS_GUIDES} menuCd={MENU} gridId="guides" controller={guides} deletable={editable} onRead={() => void select(master.rows.find(r => r.id === selectedRowId) ?? null, true)} excel={{pageOnly: true}} />},
      ]} />} /></div>
    <Modal open={dialog !== null} title={label(dialog === 'groups' ? MSG.DS_GROUP_PICK : dialog === 'cleanup' ? MSG.TARGET_CLEAN_CONFIRM : MSG.DS_SAVE)} confirmLoading={busy} closable={!busy} keyboard={!busy} mask={{closable: !busy}} onCancel={() => {if (!busy) setDialog(null);}} okText={label(MSG.GRID_CONFIRM_OK)} cancelText={label(MSG.COMMON_CANCEL)} okButtonProps={{disabled: !canSave || blocked || (dialog === 'groups' ? !editable || picked.length === 0 : !reason.trim() || dialog === 'cleanup' && !preview?.executable)}} onOk={() => {
      if (dialog === 'groups') {const existing = new Set(live(groups.rows).map(g => g.groupId)); setGroups([...groups.rows, ...toRows(picked.filter(id => !existing.has(id)).map(groupId => ({groupId, groupName: detail?.groupCandidates.find(g => g.groupId === groupId)?.groupName, mappingActive: true})))]);setDialog(null);}
      else if (dialog === 'cleanup') void applyCleanup(); else {reasonRef.current = reason.trim();setDialog(null);void master.save();}
    }}>
      {dialog === 'groups' ? <Select mode="multiple" style={{width: '100%'}} showSearch optionFilterProp="label" value={picked} onChange={setPicked} options={detail?.groupCandidates.map(g => ({value: g.groupId, label: g.groupName, disabled: live(groups.rows).some(r => r.groupId === g.groupId)}))} /> : <>
        {dialog === 'cleanup' && <><p>{label(MSG.TARGET_CLEAN_HELP)}</p><Table size="small" rowKey={r => r.target.id} dataSource={preview?.targets} pagination={false} columns={[{title: label(MSG.DS_ALIAS), dataIndex: 'name'}, {title: label(MSG.TARGET_CLEAN_STATE), render: (_, r) => label(r.reason ? MSG.TARGET_CLEAN_BLOCKED : MSG.TARGET_CLEAN_READY)}]} /></>}
        <Input.TextArea value={reason} placeholder={label(MSG.GROUP_REASON)} aria-label={label(MSG.GROUP_REASON)} maxLength={2000} onChange={e => setReason(e.target.value)} />
      </>}
    </Modal>
  </FillColumn>;
}
