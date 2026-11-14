// @feat FEAT-003
import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, App, Button, Input, Modal} from 'antd';
import {FillColumn} from '@/shared/components/FillColumn';
import {DataGrid} from '@/shared/grid/DataGrid';
import {SplitPane} from '@/shared/grid/SplitPane';
import {DetailTabs} from '@/shared/grid/DetailTabs';
import {useGridController, type GridController} from '@/shared/grid/useGridController';
import {useConfirmDiscard} from '@/shared/grid/useConfirmDiscard';
import {toRows, editRow, type GridRow} from '@/shared/grid/rows';
import {validateAll} from '@/shared/grid/validate';
import type {ColumnDef, GridApi} from '@/shared/grid/types';
import {SearchForm, type SearchFormHandle} from '@/shared/searchform/SearchForm';
import {useSearchFormHeader} from '@/shared/searchform/useSearchFormHeader';
import {useAuthStore} from '@/shared/stores/authStore';
import {hasPermission} from '@/shared/authz/permission';
import {useTabIdentity} from '@/layouts/ShellLayout/tabContext';
import {useCodeOptionsByRef} from '@/shared/code/useCodeOptionsByRef';
import {post} from '@/shared/api';
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import {CATALOG_ENDPOINTS as E} from '../api/endpoints';
import {CatalogCommands, loadCatalog, loadReferences, resolveObject} from '../api/catalogApi';
import {collectIndexes, indexKey} from '../indexRows';
import {areaFor, workspaceOf, rebaseMaster} from '../workspace';
import {masterColumns, columnColumns, masterValues, newColumn, choice} from '../columns';
import {CatalogEditors, type EditorState} from './CatalogEditors';
import type {CatalogDetail, CatalogOptions, Scope, ValueRow, Workspace, CommandDraft, Projection} from '../types';

const MENU = 'HUB_CATALOG';
const live = (rows: GridRow<ValueRow>[]) => rows.filter(r => r.state !== 'D').map(r => r.data);
const names: Record<Scope, string> = {MASTER: MSG.CAT_TITLE, COLUMNS: MSG.CAT_COLUMNS, ACCESS_GROUPS: MSG.CAT_GROUPS, COLUMN_GROUP_EXCEPTIONS: MSG.CAT_EXCEPTIONS, INDEXES: MSG.CAT_INDEXES};
const emptyOptions: CatalogOptions = {dataSources: [], timezones: [], maskingTypes: [], maskingState: 'UNAVAILABLE'};
const localApi: GridApi<ValueRow> = {save: {url: E.saveChange}};

export function CatalogPage() {
  const {message} = App.useApp(); const search = useRef<SearchFormHandle>(null); const header = useSearchFormHeader(); const discard = useConfirmDiscard();
  const permissions = useAuthStore(s => s.permissions); const actor = useAuthStore(s => s.user?.userId ?? ''); const identity = useTabIdentity();
  const canSave = hasPermission(permissions, MENU, 'READ') && hasPermission(permissions, MENU, 'SAVE');
  const policySave = canSave;
  const key = `catalog/${actor}/${identity?.tabKey ?? 'standalone'}`;
  const commands = useMemo(() => ({create: new CatalogCommands(`${key}/create`, sessionStorage, true), change: new CatalogCommands(`${key}/change`, sessionStorage)}), [key]);
  const [pending, setPending] = useState(commands.create.hasPending() || commands.change.hasPending());
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [options, setOptions] = useState<CatalogOptions | null>(null);
  const [detail, setDetail] = useState<CatalogDetail | null>(null); const [selectedId, setSelectedId] = useState<string | null>(null); const [isNew, setIsNew] = useState(false);
  const [tab, setTab] = useState('columns'); const [saveScope, setSaveScope] = useState<Scope | null>(null); const [reason, setReason] = useState('');
  const [editor, setEditor] = useState<EditorState | null>(null); const [projection, setProjection] = useState<Projection | null>(null);
  const [applied, setApplied] = useState<{id: string; scope: Scope} | null>(null);
  const serial = useRef(0); const flight = useRef(false); const initial = useRef(false); const refs = useRef<(Record<keyof Workspace, GridController<ValueRow>> & {knowledge: GridController<ValueRow>}) | null>(null);
  const blocked = busy || pending || applied !== null; const editable = canSave && !blocked && (isNew || detail !== null); const childEdit = editable && !isNew;
  const fail = (e: unknown) => setError(e instanceof Error ? e.message : label(MSG.TARGET_FAILED));
  const clearDetail = useCallback(() => {++serial.current; setSelectedId(null);setDetail(null);setIsNew(false);setProjection(null);refs.current?.knowledge.setRows([]);setEditor(null);for (const area of ['columns','groups','exceptions','indexes'] as const) refs.current?.[area].setRows([]);}, []);
  const collect = useCallback(async () => {
    if (flight.current || commands.create.hasPending() || commands.change.hasPending()) return null;
    if (refs.current && Object.entries(refs.current).some(([k, g]) => k !== 'master' && g.dirty) && !await discard()) return null;
    clearDetail(); setError('');return search.current?.collect() ?? [];
  }, [commands, discard, clearDetail]);
  const master = useGridController<ValueRow>({menuCd: MENU, columns: masterColumns((r) => editable && (isNew ? !r.id : r.id === detail?.row.id), options), api: {read: {url: E.getList, emptyNotice: true}, save: {url: E.saveChange}}, params: collect});
  const selected = master.rows.find(r => r.id === selectedId);
  const dbms = options?.dataSources.find(s => s.id === selected?.data.dataSourceId)?.dbmsCode;
  const sourceTypes = useCodeOptionsByRef('DBMS_DATA_TYPE', dbms ? {refCd: 'PARENT', refVal: dbms} : undefined);
  const columns = useGridController<ValueRow>({menuCd: MENU, columns: columnColumns(childEdit, sourceTypes.options, options, row => {if (childEdit) setEditor({kind: 'dimensions', row, value: {...(row.sourceDimensions as ValueRow ?? {state: 'UNKNOWN'})}});}, detail?.values.master.objectType === 'VIEW'), api: localApi, editable: childEdit, addRow: {canAdd: () => childEdit ? true : {ok: false, message: label(MSG.CAT_NOT_SELECTED)}, defaults: newColumn}});
  const candidates = detail?.dependencies.groupCandidates ?? [];
  const groups = useGridController<ValueRow>({menuCd: MENU, columns: [
    {field: 'groupId', header: MSG.GROUP_NAME, type: 'code', editor: 'richSelect', width: 250, required: true, options: candidates.map(g => ({value: g.groupId, text: g.groupName})), editable: policySave && childEdit},
    {field: 'mappingActive', header: MSG.DS_GROUP_ACTIVE, type: 'ynFlag', width: 150, editable: policySave && childEdit},
  ], api: localApi, editable: policySave && childEdit, addRow: {canAdd: () => policySave && childEdit ? true : {ok: false, message: label(MSG.CAT_NOT_SELECTED)}, defaults: () => ({groupId: null, mappingActive: true})}});
  const savedColumns = detail?.values.columns ?? [];
  const exceptions = useGridController<ValueRow>({menuCd: MENU, columns: [
    {field: 'columnId', header: MSG.CAT_COLUMN_NAME, type: 'code', editor: 'richSelect', width: 230, required: true, options: savedColumns.map(c => ({value: String(c.columnId), text: String(c.columnName ?? c.columnId)})), editable: policySave && childEdit},
    {field: 'groupId', header: MSG.GROUP_NAME, type: 'code', editor: 'richSelect', width: 230, required: true, options: (detail?.values.groups ?? []).map(g => ({value: String(g.groupId), text: candidates.find(c => c.groupId === g.groupId)?.groupName ?? String(g.groupId)})), editable: policySave && childEdit},
    {field: 'mode', header: MSG.CAT_EXCEPTION_TYPE, type: 'code', editor: 'richSelect', width: 150, required: true, options: [{value: 'HIDE', text: label(MSG.CAT_HIDE)}, ...(options?.maskingState === 'READY' ? [{value: 'MASK', text: label(MSG.CAT_MASK_MODE)}] : [])], editable: policySave && childEdit},
    {field: 'reason', header: MSG.CAT_REASON, type: 'text', editor: 'largeText', width: 350, required: true, editable: policySave && childEdit},
  ], api: localApi, editable: policySave && childEdit, addRow: {canAdd: () => policySave && childEdit ? true : {ok: false, message: label(MSG.CAT_NOT_SELECTED)}, defaults: () => ({columnId: null, groupId: null, mode: null, reason: ''})}});
  const indexCols: ColumnDef<ValueRow>[] = [
    {field: 'definitionKind', header: MSG.CAT_INDEX_KIND, type: 'code', editor: 'richSelect', width: 140, options: [{value: 'UNIQUE_KEY', text: label(MSG.CAT_UNIQUE_KEY)}, {value: 'INDEX', text: label(MSG.CAT_INDEX)}], required: true, editable: r => childEdit && !r.indexId},
    {field: 'name', header: MSG.CAT_INDEX_NAME, type: 'text', width: 210, editable: childEdit},
    {field: 'unique', header: MSG.CAT_UNIQUE, type: 'code', editor: 'richSelect', options: choice('UNKNOWN','YES','NO'), width: 130, derive: r => r.definitionKind === 'UNIQUE_KEY' ? 'YES' : r.unique, editable: r => childEdit && r.definitionKind === 'INDEX'},
    {field: 'indexPosition', header: MSG.CAT_ORDER, type: 'number', width: 110, required: true, editable: childEdit},
    {field: 'columnId', header: MSG.CAT_INDEX_COLUMNS, type: 'code', editor: 'richSelect', options: savedColumns.map(c => ({value: String(c.columnId), text: String(c.columnName ?? c.columnId)})), width: 230, required: true, editable: childEdit},
    {field: 'direction', header: MSG.CAT_DIRECTION, type: 'code', editor: 'richSelect', options: [...choice('UNKNOWN','ASC','DESC'), {value: 'NA', text: label(MSG.CAT_NA)}], width: 140, required: true, derive: r => r.definitionKind === 'UNIQUE_KEY' ? 'NA' : r.direction, editable: r => childEdit && r.definitionKind === 'INDEX'},
    {field: 'definitionEvidence', header: MSG.CAT_DEFINITION, type: 'text', editor: 'largeText', width: 300, editable: childEdit},
  ];
  const indexes = useGridController<ValueRow>({menuCd: MENU, columns: indexCols, api: localApi, editable: childEdit, addRow: {canAdd: () => childEdit ? true : {ok: false, message: label(MSG.CAT_NOT_SELECTED)}, defaults: () => ({clientKey: crypto.randomUUID(), definitionKind: 'INDEX', name: null, unique: 'UNKNOWN', indexPosition: 1, columnId: null, direction: 'UNKNOWN', definitionEvidence: null})}});
  const knowledge = useGridController<ValueRow>({menuCd: MENU, columns: [{field: 'id', header: MSG.GROUP_ASSET_ID, type: 'bigint', width: 140}, {field: 'name', header: MSG.GROUP_ASSET_NAME, type: 'text', width: 400}], editable: false});
  useEffect(() => {refs.current = {master, columns, groups, exceptions, indexes, knowledge};});
  const dirty = master.dirty || columns.dirty || groups.dirty || exceptions.dirty || indexes.dirty;
  const setMaster = master.setRows; const read = master.read; const setKnowledge = knowledge.setRows;
  const install = (found: CatalogDetail, rowId: string, baseRows: GridRow<ValueRow>[]) => {
    const fresh = workspaceOf(found);setMaster(baseRows.map(r => r.id === rowId ? {...r, state: null, data: fresh.master[0], original: fresh.master[0]} : r));
    for (const area of ['columns','groups','exceptions','indexes'] as const) refs.current?.[area].setRows(toRows(fresh[area]));
    setDetail(found);setSelectedId(rowId);setIsNew(false);
  };
  const select = async (row: GridRow<ValueRow> | null) => {
    if (blocked || row?.id === selectedId) return false;
    if (dirty && !await discard()) return false;
    const kept = master.rows.filter(r => !(isNew && r.id === selectedId)).map(r => r.id === selectedId && r.original ? {...r, data: r.original, state: null} : r);
    master.setRows(kept);clearDetail();if (!row) return true;
    const ticket = ++serial.current;setBusy(true);setError('');
    try {const found = await loadCatalog(String(row.data.id));if (ticket !== serial.current) return false;install(found, row.id, kept);
      const projected = await loadReferences(found.row.id);if (ticket === serial.current) {setProjection(projected);setKnowledge(toRows(projected.rows));}
    } catch (e) {if (ticket === serial.current) fail(e);} finally {if (ticket === serial.current) setBusy(false);}return true;
  };
  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
    if (!active || initial.current) return;initial.current = true;
    const recovery = commands.create.hasPending() ? commands.create : commands.change;
    if (recovery.hasPending()) {
      const draft = recovery.restoredRows()[0]?.data;if (!draft) return;
      const snapshots = draft.snapshots;
      for (const area of ['master','columns','groups','exceptions','indexes'] as const) refs.current?.[area].setRows(snapshots?.[area] ?? toRows(draft.workspace[area]).map(r => ({...r, state: 'U'})));
      setSelectedId(snapshots?.master[0]?.id ?? null);setIsNew(draft.id === null);setTab(draft.scope === 'MASTER' ? 'columns' : areaFor[draft.scope]);
    } else void read();
    });return () => {active = false;};
  }, [commands, read]);
  useEffect(() => {let active = true;const sequence = serial;void post<CatalogOptions>(E.getReferenceCandidates, {}).then(v => {if (active) setOptions(v);}).catch(e => {if (active) fail(e);});return () => {active = false;++sequence.current;};}, []);
  const startNew = async () => {
    if (!canSave || blocked || dirty && !await discard()) return;clearDetail();setError('');
    const data: ValueRow = {...Object.fromEntries(Object.keys(masterValues({})).map(k => [k, null])), dataSourceId: null, objectName: '', objectType: 'TABLE', useYn: false, reviewStatus: 'UNKNOWN', pkReview: 'UNKNOWN'};
    const row: GridRow<ValueRow> = {id: crypto.randomUUID(), state: 'C', original: null, data};setMaster([row, ...master.rows.filter(r => r.state !== 'C').map(r => ({...r, data: r.original ?? r.data, state: null}))]);setSelectedId(row.id);setIsNew(true);
  };
  const reconcile = async (id: string, scope: Scope) => {
    const found = await loadCatalog(id);const fresh = workspaceOf(found);const area = areaFor[scope];
    const current = refs.current;if (!current) return;
    const rowId = selectedId ?? current.master.rows[0]?.id;
    current.master.setRows(current.master.rows.map(r => r.id !== rowId ? r : scope === 'MASTER' ? {...r, state: null, data: fresh.master[0], original: fresh.master[0]} : rebaseMaster(r, fresh.master[0])));
    if (area !== 'master') current[area].setRows(toRows(fresh[area]));
    if (scope === 'ACCESS_GROUPS') {const kept = new Set(fresh.groups.map(g => g.groupId));current.exceptions.setRows(current.exceptions.rows.filter(r => kept.has(r.data.groupId)));}
    setDetail(found);setSelectedId(rowId);setIsNew(false);setApplied(null);setError('');
  };
  const submit = async (scope: Scope, retry = false) => {
    if (flight.current || !canSave) return;
    const action = commands.create.hasPending() ? commands.create : commands.change.hasPending() ? commands.change : isNew ? commands.create : commands.change;
    let row: GridRow<CommandDraft>;
    if (retry) row = action.restoredRows()[0];
    else {
      if (!selected || !refs.current) return;
      const workspace: Workspace = {master: [selected.data], columns: live(columns.rows), groups: live(groups.rows), exceptions: live(exceptions.rows), indexes: live(indexes.rows)};
      const area = areaFor[scope];row = {id: selected.id, state: isNew ? 'C' : 'U', original: null, data: {id: isNew ? null : String(selected.data.id), revision: isNew ? null : detail?.row.revision ?? null, scope, values: scope === 'MASTER' ? masterValues(selected.data) : {[area]: scope === 'INDEXES' ? collectIndexes(workspace.indexes) : workspace[area]}, workspace, snapshots: {master: [selected], columns: columns.rows, groups: groups.rows, exceptions: exceptions.rows, indexes: indexes.rows}}};
    }
    if (!row) return;flight.current = true;setBusy(true);setError('');
    try {
      const result = await action.save([row], reason.trim());setPending(commands.create.hasPending() || commands.change.hasPending());
      if (result.failed) throw new Error(result.rows[0]?.fields?.[0]?.message ?? label(MSG.TARGET_FAILED));
      const id = action.result()?.targetId ?? row.data.id;if (!id) throw new Error(label(MSG.CAT_RELOAD));
      setSaveScope(null);setApplied({id, scope: row.data.scope});await reconcile(id, row.data.scope);void message.success(label(MSG.GRID_SAVE));
    } catch (e) {fail(e);} finally {flight.current = false;setBusy(false);setPending(commands.create.hasPending() || commands.change.hasPending());}
  };
  const startSave = (scope: Scope) => {
    if (blocked || !canSave || !selected || scope !== 'MASTER' && isNew) return;
    if ((scope === 'ACCESS_GROUPS' || scope === 'COLUMN_GROUP_EXCEPTIONS') && !policySave) return;
    const grid = refs.current?.[areaFor[scope]];if (!grid || !grid.dirty) return;
    const issues = validateAll(scope === 'MASTER' ? [selected] : grid.rows, grid.columns);grid.setIssues(issues);if (issues.length) {setError(label(MSG.CAT_REQUIRED));return;}
    if (scope === 'INDEXES') {try {collectIndexes(live(indexes.rows));} catch (e) {fail(e);return;}}
    setReason('');setSaveScope(scope);
  };
  const editIndex = (rowId: string, patch: ValueRow) => {
    const row = indexes.rows.find(r => r.id === rowId);if (!row) return;
    if (patch.definitionKind === 'UNIQUE_KEY') patch = {...patch, unique: 'YES', direction: 'UNKNOWN'};
    const shared = Object.keys(patch).some(k => ['name','definitionKind','unique','definitionEvidence'].includes(k));
    indexes.setRows(indexes.rows.reduce((rows, r) => r.id === rowId || shared && indexKey(r.data) === indexKey(row.data) ? editRow(rows, r.id, patch) : rows, indexes.rows));
  };
  const controller = (grid: GridController<ValueRow>, scope: Scope) => ({...grid, edit: scope === 'INDEXES' ? editIndex : grid.edit, loading: grid.loading || blocked, canSave: (scope === 'ACCESS_GROUPS' || scope === 'COLUMN_GROUP_EXCEPTIONS' ? policySave : canSave) && !blocked && (scope === 'MASTER' || !isNew && detail !== null), canAddRow: grid.canAddRow && childEdit && (scope !== 'ACCESS_GROUPS' && scope !== 'COLUMN_GROUP_EXCEPTIONS' || policySave), canEdit: scope === 'MASTER' ? editable : childEdit, save: () => {startSave(scope);return Promise.resolve();}});
  const readAgain = async () => {if (blocked) return;if (dirty && !await discard()) return;for (const grid of Object.values(refs.current ?? {})) grid.setRows([]);clearDetail();await read(true);};
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{field: 'dataSourceId', label: MSG.DS_TITLE, type: 'select', op: 'eq', options: (options ?? emptyOptions).dataSources.map(d => ({value: d.id, text: d.alias}))}, {field: 'businessName', label: MSG.CAT_BUSINESS_NAME, type: 'text', op: 'contains'}, {field: 'objectType', label: MSG.CAT_OBJECT_TYPE, type: 'select', op: 'eq', options: [{value: 'TABLE', text: label(MSG.CAT_TABLE)}, {value: 'VIEW', text: label(MSG.CAT_VIEW)}]}]} onSubmit={() => void readAgain()} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    <Alert type="info" title={label(MSG.CAT_HELP)} showIcon />
    {error && <Alert type="error" title={error} />}
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} action={<Button disabled={busy || !canSave} onClick={() => void submit('MASTER', true)}>{label(MSG.TARGET_RESULT)}</Button>} />}
    {applied && <Alert type="warning" title={label(MSG.CAT_RELOAD)} action={<Button disabled={busy} onClick={() => {setBusy(true);void reconcile(applied.id, applied.scope).catch(fail).finally(() => setBusy(false));}}>{label(MSG.GRID_READ)}</Button>} />}
    <div style={{flex: 1, minHeight: 0}}><SplitPane storageKey="catalog" first={(collapsed, expand) => <DataGrid title={MSG.CAT_TITLE} menuCd={MENU} gridId="master" controller={controller(master, 'MASTER')} onSelect={select} onRead={() => void readAgain()} deletable={false} collapsed={collapsed} onExpand={expand} excel={{pageOnly: true}} controls={[
      {key: 'new', type: 'button', slot: 'beforeRead', label: MSG.CAT_NEW, actionCd: 'SAVE', disabled: blocked || !canSave, onClick: () => void startNew()},
      {key: 'source', type: 'button', slot: 'beforeTools', label: MSG.CAT_OBJECT_CHECK, actionCd: 'READ', disabled: blocked || !selected, onClick: () => {if (!selected) return;setBusy(true);setError('');void resolveObject(masterValues(selected.data)).then(value => setEditor({kind: 'source', row: selected.data, value})).catch(fail).finally(() => setBusy(false));}},
    ]} />} second={() => <DetailTabs activeKey={tab} onChange={setTab} items={[
      {key: 'columns', label: label(MSG.CAT_COLUMNS), forceRender: true, children: <FillColumn>{sourceTypes.status === 'error' && <Alert type="warning" title={label(MSG.CAT_TYPE_UNAVAILABLE)} action={<Button onClick={sourceTypes.retry}>{label(MSG.GRID_READ)}</Button>} />}{options?.maskingState !== 'READY' && <Alert type="info" title={label(MSG.CAT_MASK_UNAVAILABLE)} />}<DataGrid title={MSG.CAT_COLUMNS} menuCd={MENU} gridId="columns" controller={controller(columns, 'COLUMNS')} deletable={childEdit} excel={{pageOnly: true}} /></FillColumn>},
      {key: 'groups', label: label(MSG.CAT_GROUPS), forceRender: true, children: <FillColumn>{!policySave && <Alert type="info" title={label(MSG.CAT_POLICY_READONLY)} />}<DataGrid title={MSG.CAT_GROUPS} menuCd={MENU} gridId="groups" controller={controller(groups, 'ACCESS_GROUPS')} deletable={policySave && childEdit} excel={{pageOnly: true}} /></FillColumn>},
      {key: 'exceptions', label: label(MSG.CAT_EXCEPTIONS), forceRender: true, children: <FillColumn>{!policySave && <Alert type="info" title={label(MSG.CAT_POLICY_READONLY)} />}<DataGrid title={MSG.CAT_EXCEPTIONS} menuCd={MENU} gridId="exceptions" controller={controller(exceptions, 'COLUMN_GROUP_EXCEPTIONS')} deletable={policySave && childEdit} excel={{pageOnly: true}} /></FillColumn>},
      {key: 'indexes', label: label(MSG.CAT_INDEXES), forceRender: true, children: <DataGrid title={MSG.CAT_INDEXES} menuCd={MENU} gridId="indexes" controller={controller(indexes, 'INDEXES')} controls={[{key: 'part', type: 'button', slot: 'beforeTools', label: MSG.CAT_INDEX_PART_ADD, actionCd: 'SAVE', disabled: !childEdit || indexes.selected.length !== 1, onClick: () => {
        const row = indexes.selected[0];if (!row) return;const positions = live(indexes.rows).filter(r => indexKey(r) === indexKey(row.data)).map(r => Number(r.indexPosition));
        indexes.setRows([...indexes.rows, {id: crypto.randomUUID(), state: 'C', original: null, data: {...row.data, indexPosition: Math.max(...positions) + 1, columnId: null, direction: 'UNKNOWN'}}]);
      }}]} deletable={childEdit} excel={{pageOnly: true}} />},
      {key: 'knowledge', label: label(MSG.CAT_KNOWLEDGE), forceRender: true, children: <FillColumn>{projection?.state !== 'READY' && <Alert type="info" title={label(MSG.CAT_PENDING_REF)} />}<DataGrid title={MSG.CAT_KNOWLEDGE} menuCd={MENU} gridId="knowledge" controller={knowledge} deletable={false} excel={false} /></FillColumn>},
    ]} />} /></div>
    <Modal open={saveScope !== null} title={label(MSG.CAT_SAVE)} confirmLoading={busy} closable={!busy} keyboard={!busy} mask={{closable: !busy}} onCancel={() => setSaveScope(null)} okText={label(MSG.GRID_CONFIRM_OK)} cancelText={label(MSG.COMMON_CANCEL)} okButtonProps={{disabled: blocked || !reason.trim()}} onOk={() => {if (saveScope) void submit(saveScope);}}>
      <p>{label(MSG.CAT_SCOPE)}: {saveScope && label(names[saveScope])}</p>
      {saveScope === 'ACCESS_GROUPS' && (detail?.values.groups ?? []).some(g => !live(groups.rows).some(r => r.groupId === g.groupId)) && <Alert type="warning" title={label(MSG.CAT_GROUP_REMOVE)} description={<ul>{(detail?.values.groups ?? []).filter(g => !live(groups.rows).some(r => r.groupId === g.groupId)).map(g => <li key={String(g.groupId)}>{label(MSG.CAT_GROUP_REMOVAL_COUNT, {group: candidates.find(c => c.groupId === g.groupId)?.groupName ?? String(g.groupId), saved: (detail?.values.exceptions ?? []).filter(e => e.groupId === g.groupId).length, draft: exceptions.rows.filter(r => r.state !== null && r.data.groupId === g.groupId).length})}</li>)}</ul>} />}
      <Input.TextArea aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000} onChange={e => setReason(e.target.value)} />
    </Modal>
    <CatalogEditors editor={editor} setEditor={setEditor} onApply={(row, patch) => {const grid = columns;const target = grid.rows.find(r => r.data === row);if (target) grid.edit(target.id, patch);setEditor(null);}} />
  </FillColumn>;
}
