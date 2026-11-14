// @feat FEAT-011
import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, App, Button, Input, Modal} from 'antd';
import {FillColumn} from '@/shared/components/FillColumn';
import {DataGrid} from '@/shared/grid/DataGrid';
import {SplitPane} from '@/shared/grid/SplitPane';
import {DetailTabs} from '@/shared/grid/DetailTabs';
import {auditColumns} from '@/shared/grid/auditColumns';
import {useGridController} from '@/shared/grid/useGridController';
import {toRows, editRow, toggleDelete, revertRow, type GridRow} from '@/shared/grid/rows';
import type {ColumnDef} from '@/shared/grid/types';
import {useConfirmDiscard} from '@/shared/grid/useConfirmDiscard';
import {SearchForm, type SearchFormHandle} from '@/shared/searchform/SearchForm';
import {useSearchFormHeader} from '@/shared/searchform/useSearchFormHeader';
import {useCodeOptionsByRef} from '@/shared/code/useCodeOptionsByRef';
import {useAuthStore} from '@/shared/stores/authStore';
import {hasPermission} from '@/shared/authz/permission';
import {useTabIdentity} from '@/layouts/ShellLayout/tabContext';
import {post} from '@/shared/api';
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import {CATALOG_ENDPOINTS as C} from '../../catalog/api/endpoints';
import type {CatalogOptions, ValueRow} from '../../catalog/types';
import {RELATION_ENDPOINTS as E} from '../api/endpoints';
import {RelationCommands, loadOwner} from '../api/relationApi';
import {mergeOwner} from '../workspace';
import type {CatalogCandidate, OwnerDetail, RelationDraft, RelationRow} from '../types';
import {TablePicker} from './TablePicker';
const MENU = 'HUB_CATALOG';
const pairColumns: ColumnDef<ValueRow>[] = [{field: 'position', header: MSG.CAT_ORDER, type: 'number', width: 90, required: true, editable: true}, {field: 'leftName', header: MSG.REL_LEFT_COLUMN, width: 180}, {field: 'rightName', header: MSG.REL_RIGHT_COLUMN, width: 180}, ...auditColumns<ValueRow>()];
const columnColumns: ColumnDef<ValueRow>[] = [{field: 'ordinalPosition', header: MSG.CAT_ORDER, type: 'number', width: 90}, {field: 'columnName', header: MSG.CAT_COLUMN_NAME, width: 170}, {field: 'columnBusinessName', header: MSG.CAT_BUSINESS_NAME, width: 170}, {field: 'sourceType', header: MSG.CAT_SOURCE_TEXT, width: 140}, ...auditColumns<ValueRow>()];
export function CatalogRelationPage() {
  const {message, modal} = App.useApp(); const discard = useConfirmDiscard(); const search = useRef<SearchFormHandle>(null); const header = useSearchFormHeader();
  const permissions = useAuthStore(s => s.permissions); const actor = useAuthStore(s => s.user?.userId ?? ''); const identity = useTabIdentity();
  const canSave = hasPermission(permissions, MENU, 'READ') && hasPermission(permissions, MENU, 'SAVE');
  const commands = useMemo(() => new RelationCommands(`catalog-relation/${actor}/${identity?.tabKey ?? 'standalone'}`, sessionStorage), [actor, identity?.tabKey]);
  const owners = useRef(new Map<string, OwnerDetail>()); const pairEdits = useRef(new Map<string, GridRow<ValueRow>[]>()); const flight = useRef(false); const initial = useRef(false); const readState = useRef({dirty: false, blocked: false});
  const [selectedId, setSelectedId] = useState<string | null>(null); const [busy, setBusy] = useState(false); const [pending, setPending] = useState(commands.hasPending());
  const [error, setError] = useState(''); const [options, setOptions] = useState<CatalogOptions | null>(null); const [tab, setTab] = useState('pairs'); const [catalogContext, setCatalogContext] = useState(() => new URLSearchParams(window.location.search).get('catalogId')); 
  const [picker, setPicker] = useState(false); const [saveOpen, setSaveOpen] = useState(false); const [reason, setReason] = useState('');
  const [leftSelection, setLeftSelection] = useState<ValueRow | null>(null); const [rightSelection, setRightSelection] = useState<ValueRow | null>(null);
  const [pairSelection, setPairSelection] = useState<string | null>(null); const [applied, setApplied] = useState<RelationDraft | null>(null); const blocked = busy || pending || applied !== null; const editable = canSave && !blocked;
  const fail = (e: unknown) => setError(e instanceof Error ? e.message : label(MSG.TARGET_FAILED));
  const collect = useCallback(async () => {if(readState.current.blocked || readState.current.dirty && !await discard())return null; owners.current.clear();pairEdits.current.clear();setSelectedId(null);return search.current?.collect() ?? [];}, [discard]);
  const mainColumns: ColumnDef<RelationRow>[] = [
    {field: 'relationId', header: MSG.REL_ID, type: 'bigint', width: 140}, {field: 'relationName', header: MSG.REL_NAME, width: 180, required: true, editable},
    {field: 'dataSourceId', header: MSG.DS_TITLE, type: 'code', editor: 'select', width: 160, options: options?.dataSources.map(source => ({value: source.id, text: source.alias})) ?? [], editable: row => editable && !row.relationId && !row.leftCatalogId}, {field: 'leftObjectName', header: MSG.REL_LEFT, width: 170}, {field: 'leftRole', header: MSG.REL_LEFT_ROLE, width: 150, required: true, editable},
    {field: 'rightObjectName', header: MSG.REL_RIGHT, width: 170}, {field: 'rightRole', header: MSG.REL_RIGHT_ROLE, width: 150, required: true, editable},
    {field: 'cardinality', header: MSG.REL_CARDINALITY, type: 'code', editor: 'select', width: 120, options: ['1:1','1:N','N:1'].map(value => ({value, text: value})), required: true, editable},
    {field: 'description', header: MSG.REL_DESCRIPTION, editor: 'largeText', width: 350, required: true, editable},
    {field: 'reviewStatus', header: MSG.CAT_REVIEW, type: 'code', editor: 'select', width: 130, options: [{value: 'UNKNOWN', text: label(MSG.CAT_UNKNOWN)}, {value: 'VERIFIED', text: label(MSG.CAT_VERIFIED)}, {value: 'INVALID', text: label(MSG.CAT_INVALID)}], editable},
    {field: 'revision', header: MSG.CAT_REVISION, width: 110}, ...auditColumns<RelationRow>()];
  const master = useGridController<RelationRow>({menuCd: MENU, columns: mainColumns, api: {read: {url: E.getList}, save: {url: C.saveChange}}, params: collect, searchParams: () => ({catalogId: catalogContext || undefined}), editable});
  const pairs = useGridController<ValueRow>({menuCd: MENU, columns: pairColumns, api: {save: {url: C.saveChange}}, editable: editable && !!selectedId});
  const left = useGridController<ValueRow>({menuCd: MENU, columns: columnColumns, api: {}, editable: false}); const right = useGridController<ValueRow>({menuCd: MENU, columns: columnColumns, api: {}, editable: false});
  const knowledge = useGridController<ValueRow>({menuCd: MENU, columns: [], api: {}, editable: false});
  useEffect(() => {readState.current = {dirty: master.dirty, blocked};}, [master.dirty, blocked]);
  const selected = master.rows.find(row => row.id === selectedId) ?? null;
  const sourceId = typeof selected?.data.dataSourceId === 'string' ? selected.data.dataSourceId : ''; const dbms = options?.dataSources.find(source => source.id === sourceId)?.dbmsCode;
  const types = useCodeOptionsByRef('DBMS_DATA_TYPE', dbms ? {refCd: 'PARENT', refVal: dbms} : undefined);
  const setMaster = master.setRows; const read = master.read; const setPairs = pairs.setRows; const setLeft = left.setRows; const setRight = right.setRows;
  const viewPairs = useCallback((row: RelationRow) => (row.pairs ?? []).map(pair => ({...pair,
    leftName: (row.leftColumns as ValueRow[] ?? []).find(c => c.columnId === pair.leftColumnId)?.columnName ?? pair.leftColumnId,
    rightName: (row.rightColumns as ValueRow[] ?? []).find(c => c.columnId === pair.rightColumnId)?.columnName ?? pair.rightColumnId})), []);
  const pairValues = selected?.data.pairs; const leftValues = selected?.data.leftColumns as ValueRow[] | undefined; const rightValues = selected?.data.rightColumns as ValueRow[] | undefined;
  useEffect(() => {let active = true;queueMicrotask(() => {if (!active) return;
    setPairs(selectedId ? pairEdits.current.get(selectedId) ?? toRows(viewPairs({pairs: pairValues, leftColumns: leftValues, rightColumns: rightValues})) : []);
    const ordered = (columns: ValueRow[]) => [...columns].sort((a,b) => Number(a.ordinalPosition ?? Number.MAX_SAFE_INTEGER) - Number(b.ordinalPosition ?? Number.MAX_SAFE_INTEGER) || (typeof a.columnId === 'string' ? a.columnId : '').localeCompare(typeof b.columnId === 'string' ? b.columnId : ''));
    setLeft(toRows(ordered(leftValues ?? []))); setRight(toRows(ordered(rightValues ?? [])));
  });return () => {active = false;};}, [selectedId, pairValues, leftValues, rightValues, setLeft, setRight, setPairs, viewPairs]);
  useEffect(() => {let active = true;queueMicrotask(() => {if (!active || initial.current) return;initial.current = true;
    const restored = commands.restoredRows()[0]?.data;
    if (restored) {setMaster(restored.rows);setSelectedId(restored.selectedId);} else void read();
  });return () => {active = false;};}, [commands, read, setMaster]);
  useEffect(() => {let active = true;void post<CatalogOptions>(C.getReferenceCandidates, {}).then(value => {if(active)setOptions(value);}).catch(e => {if(active)fail(e);});return () => {active = false;};}, []);
  const select = async (row: GridRow<RelationRow> | null) => {
    if (blocked || row?.id === selectedId) return false; if (!row) {setSelectedId(null);return true;}
    const owner = row.data.ownerCatalogId; setError('');
    if (owner && !owners.current.has(owner)) {setBusy(true);try {const detail = await loadOwner(owner);if (detail.row.revision !== row.data.revision) throw new Error(label(MSG.REL_OWNER_CHANGED));owners.current.set(owner, detail);} catch (e) {fail(e);return false;} finally {setBusy(false);}}
    setLeftSelection(null);setRightSelection(null);setPairSelection(null);setSelectedId(row.id); return true;
  };
  const edit = (id: string, patch: Partial<RelationRow>) => {
    if (!editable) return; setMaster(editRow(master.rows, id, {...patch, ...(!('reviewStatus' in patch) ? {reviewStatus: 'UNKNOWN'} : {})}));
  };
  const syncPairs = (rows: GridRow<ValueRow>[]) => {if (!selected || !editable) return;pairEdits.current.set(selected.id, rows);setPairs(rows);edit(selected.id, {pairs: rows.filter(row => row.state !== 'D').map(row => row.data)});};
  const addPair = () => {
    if (!selected || !leftSelection || !rightSelection || types.status !== 'ready' || !types.options.some(option => option.value === leftSelection.sourceTypeCode) || !leftSelection.sourceTypeCode || leftSelection.sourceTypeCode !== rightSelection.sourceTypeCode || pairs.rows.some(row => row.state !== 'D' && row.data.leftColumnId === leftSelection.columnId && row.data.rightColumnId === rightSelection.columnId)) {setError(label(MSG.REL_PAIR_INVALID));return;}
    const data = {position: Math.max(0, ...pairs.rows.map(row => Number(row.data.position) || 0)) + 1, leftColumnId: leftSelection.columnId, rightColumnId: rightSelection.columnId, leftName: leftSelection.columnName, rightName: rightSelection.columnName};
    syncPairs([...pairs.rows, {id: crypto.randomUUID(), state: 'C', original: null, data}]);setError('');
  };
  const startNew = () => {if (!editable) return;const id = crypto.randomUUID(); const data: RelationRow = {clientKey: id, relationName: '', dataSourceId: null, leftRole: '', rightRole: '', cardinality: 'N:1', description: '', reviewStatus: 'UNKNOWN', pairs: []};setMaster([{id, state: 'C', original: null, data}, ...master.rows]);setLeftSelection(null);setRightSelection(null);setPairSelection(null);setSelectedId(id);setError('');};
  const applyTables = async (a: CatalogCandidate, b: CatalogCandidate) => {
    if (!selected || !editable || a.dataSourceId !== sourceId || b.dataSourceId !== sourceId) {setError(label(MSG.REL_SELECT_BOTH));return;}
    const changed = a.id !== selected.data.leftCatalogId || b.id !== selected.data.rightCatalogId || a.revision !== selected.data.leftRevision || b.revision !== selected.data.rightRevision;
    if (changed && (selected.data.pairs?.length ?? 0) > 0 && !await modal.confirm({title: label(MSG.REL_RESET_PAIRS), okText: label(MSG.GRID_CONFIRM_OK), cancelText: label(MSG.COMMON_CANCEL)})) return;
    setBusy(true);try {const [leftDetail, rightDetail] = await Promise.all([loadOwner(a.id), loadOwner(b.id)]);if (a.revision !== leftDetail.row.revision || b.revision !== rightDetail.row.revision) throw new Error(label(MSG.REL_OWNER_CHANGED));
      const owner = selected.data.relationId ? selected.data.ownerCatalogId! : a.id;const ownerDetail = owners.current.get(owner) ?? (owner === a.id ? leftDetail : await loadOwner(owner));owners.current.set(owner, ownerDetail);
      setLeftSelection(null);setRightSelection(null);setPairSelection(null);pairEdits.current.delete(selected.id);setMaster(editRow(master.rows, selected.id, {ownerCatalogId: owner, revision: ownerDetail.row.revision, leftCatalogId: a.id, leftRevision: a.revision, rightCatalogId: b.id, rightRevision: b.revision, leftObjectName: a.objectName, rightObjectName: b.objectName, dataSourceKey: options?.dataSources.find(source => source.id === sourceId)?.alias, leftColumns: leftDetail.values.columns, rightColumns: rightDetail.values.columns, ...(changed ? {pairs: [], reviewStatus: 'UNKNOWN'} : {})}));setPicker(false);setError('');
    } catch(e) {fail(e);} finally {setBusy(false);}
  };
  const reconcile = async (draft: RelationDraft) => {
    const fresh = await loadOwner(draft.ownerId);owners.current.set(draft.ownerId, fresh);
    const freshRows = toRows(fresh.values.relations.map(row => ({...row, ownerCatalogId: draft.ownerId, revision: fresh.row.revision, dataSourceId: fresh.values.master.dataSourceId})));
    setMaster([...master.rows.filter(row => row.data.ownerCatalogId !== draft.ownerId), ...freshRows]);for(const row of master.rows.filter(row => row.data.ownerCatalogId === draft.ownerId))pairEdits.current.delete(row.id);
    setSelectedId(null);setSaveOpen(false);setApplied(null);setError('');
  };
  const submit = async (retry = false) => {
    if (flight.current || !canSave) return; let draft: RelationDraft;
    if (retry) {const restored = commands.restoredRows()[0]?.data;if (!restored) return;draft = restored;} else {
      if (!selected?.data.ownerCatalogId) return;const owner = owners.current.get(selected.data.ownerCatalogId);if (!owner) return;
      draft = {ownerId: owner.row.id, revision: owner.row.revision, relations: mergeOwner(owner.row.id, owner.values.relations, master.rows), rows: master.rows, selectedId: selected.id};
    }
    flight.current = true;setBusy(true);try {const result = await commands.save([{id: draft.ownerId, state: 'U', original: null, data: draft}], reason.trim());setPending(commands.hasPending());if (result.failed) throw new Error(result.rows[0]?.fields?.[0]?.message ?? label(MSG.TARGET_FAILED));setApplied(draft);await reconcile(draft);void message.success(label(MSG.GRID_SAVE));} catch(e) {fail(e);} finally {flight.current = false;setBusy(false);setPending(commands.hasPending());}
  };
  const startSave = () => {if (!editable || !selected?.data.ownerCatalogId || !ownerDirty) return;setReason('');setSaveOpen(true);};
  const readAgain = async () => {if (!blocked) await read(true);};
  const ownerDirty = !!selected?.data.ownerCatalogId && master.rows.some(row => row.data.ownerCatalogId === selected.data.ownerCatalogId && row.state !== null);
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{field: 'dataSourceId', label: MSG.DS_TITLE, type: 'select', op: 'eq', options: options?.dataSources.map(source => ({value: source.id, text: source.alias})) ?? []}, {field: 'keyword', label: MSG.REL_KEYWORD, type: 'text', op: 'contains'}]} onSubmit={() => void readAgain()} onReset={() => {master.clearFilters();setCatalogContext(null);}} collapsed={header.collapsed} onActiveCountChange={header.onActiveCountChange} />
    <Alert type="info" title={label(MSG.REL_HELP)} description={label(MSG.REL_TYPE_HELP)} />
    {catalogContext && <Alert type="info" closable onClose={() => setCatalogContext(null)} title={label(MSG.REL_FILTER, {catalogId: catalogContext})} />}
    {error && <Alert type="error" title={error} />}
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} action={<Button disabled={busy || !canSave} onClick={() => void submit(true)}>{label(MSG.TARGET_RESULT)}</Button>} />}
    {applied && <Alert type="warning" title={label(MSG.CAT_RELOAD)} action={<Button disabled={busy} onClick={() => {setBusy(true);void reconcile(applied).catch(fail).finally(() => setBusy(false));}}>{label(MSG.GRID_READ)}</Button>} />}
    <div style={{flex: 1, minHeight: 0}}><SplitPane storageKey="catalog-relation" first={(collapsed, expand) => <DataGrid title={MSG.REL_TITLE} menuCd={MENU} gridId="relation-main" controller={{...master, edit, canEdit: editable, canSave: editable, loading: master.loading || blocked, save: () => {startSave();return Promise.resolve();}, toggleDelete: id => {pairEdits.current.delete(id);setMaster(toggleDelete(master.rows, id));}, revert: id => {pairEdits.current.delete(id);const rows = revertRow(master.rows, id);setMaster(rows);if(id === selectedId)setPairs(toRows(viewPairs(rows.find(row => row.id === id)?.data ?? {})));}}} collapsed={collapsed} onExpand={expand} onRead={() => void readAgain()} onSelect={select} deletable={editable} excel={{pageOnly: true}} controls={[{key: 'new', type: 'button', slot: 'beforeTools', label: MSG.REL_NEW, actionCd: 'SAVE', disabled: !editable, onClick: startNew}, {key: 'pick', type: 'button', slot: 'beforeTools', label: MSG.REL_PICK, actionCd: 'SAVE', disabled: !editable || !sourceId || !selected, onClick: () => setPicker(true)}]} />}
    second={() => <FillColumn>
      <DetailTabs activeKey={tab} onChange={setTab} items={[{key: 'pairs', label: label(MSG.REL_PAIRS), forceRender: true, children: <div style={{display: 'grid', gridTemplateColumns: '1fr 1.35fr 1fr', gap: 8, flex: 1, minHeight: 0}}>
        <DataGrid title={MSG.REL_LEFT} menuCd={MENU} gridId="relation-left" controller={left} deletable={false} excel={false} onSelect={row => {setLeftSelection(row?.data ?? null);return true;}} />
        <DataGrid title={MSG.REL_PAIR_MAP} menuCd={MENU} gridId="relation-pairs" controller={{...pairs, canEdit: editable, canSave: editable && !!selectedId, save: () => {startSave();return Promise.resolve();}, edit: (id, patch) => syncPairs(editRow(pairs.rows, id, patch)), toggleDelete: id => syncPairs(toggleDelete(pairs.rows, id)), revert: id => syncPairs(revertRow(pairs.rows, id))}} deletable={editable} excel={{pageOnly: true}} onSelect={row => {setPairSelection(row?.id ?? null);return true;}} controls={[{key: 'add', type: 'button', slot: 'beforeTools', label: MSG.REL_ADD_PAIR, actionCd: 'SAVE', disabled: !editable || !selected, onClick: addPair}, {key: 'remove', type: 'button', slot: 'beforeTools', label: MSG.REL_REMOVE_PAIR, actionCd: 'SAVE', disabled: !editable || !pairSelection, onClick: () => {if(pairSelection)syncPairs(toggleDelete(pairs.rows, pairSelection));}}]} />
        <DataGrid title={MSG.REL_RIGHT} menuCd={MENU} gridId="relation-right" controller={right} deletable={false} excel={false} onSelect={row => {setRightSelection(row?.data ?? null);return true;}} />
      </div>}, {key: 'knowledge', label: label(MSG.CAT_KNOWLEDGE), forceRender: true, children: <FillColumn><Alert type="info" title={label(MSG.REL_KNOWLEDGE_PENDING)} /><DataGrid title={MSG.CAT_KNOWLEDGE} menuCd={MENU} gridId="relation-knowledge" controller={knowledge} deletable={false} excel={false} /></FillColumn>}]} />
    </FillColumn>} /></div>
    {picker && <TablePicker sourceId={sourceId} busy={busy} onCancel={() => setPicker(false)} onApply={applyTables} />}
    <Modal open={saveOpen} title={label(MSG.REL_SAVE)} confirmLoading={busy} onCancel={() => setSaveOpen(false)} okText={label(MSG.GRID_CONFIRM_OK)} cancelText={label(MSG.COMMON_CANCEL)} okButtonProps={{disabled: blocked || !reason.trim()}} onOk={() => void submit()}><p>{label(MSG.REL_SAVE_HINT)}</p><Input.TextArea aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000} onChange={e => setReason(e.target.value)} /></Modal>
  </FillColumn>;
}
