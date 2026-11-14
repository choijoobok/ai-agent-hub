// @feat FEAT-024
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Input } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { DetailTabs } from '@/shared/grid/DetailTabs';
import { SplitPane } from '@/shared/grid/SplitPane';
import { useGridController } from '@/shared/grid/useGridController';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { auditColumns } from '@/shared/grid/auditColumns';
import { toRows, type GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi } from '@/shared/grid/types';
import { SearchForm, type SearchFormHandle } from '@/shared/searchform/SearchForm';
import { useSearchFormHeader } from '@/shared/searchform/useSearchFormHeader';
import { useAuthStore } from '@/shared/stores/authStore';
import type { RowReport } from '@/shared/api';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { SEARCH_TAG_ENDPOINTS as E } from '../api/endpoints';
import { SearchTagCommands, loadTagDetails } from '../api/searchTagApi';
import type { TagRow, Synonym, Knowledge, TagReferences } from '../types';

const MENU = 'HUB_KNOWLEDGE_TAG';
const LOCAL_API: GridApi<Synonym> = {};
interface Draft { rows: GridRow<Synonym>[]; original: GridRow<Synonym>[]; baseline: string; references: TagReferences }
const fingerprint = (rows: GridRow<Synonym>[]) => JSON.stringify(rows.filter((row) => row.state !== 'D').map((row) => row.data));

export function SearchTagPage() {
  const search = useRef<SearchFormHandle>(null);
  const header = useSearchFormHeader();
  const confirmDiscard = useConfirmDiscard();
  const actor = useAuthStore((store) => store.user?.userId ?? '');
  const commands = useMemo(() => new SearchTagCommands(`search-tag-command/${actor}`, sessionStorage), [actor]);
  const drafts = useRef(new Map<string, Draft>());
  const serial = useRef(0); const owner = useRef<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [pending, setPending] = useState(commands.hasPending());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(0);
  const [references, setReferences] = useState<TagReferences | null>(null);
  const [tab, setTab] = useState('synonyms');
  const blocked = pending || loading || saving;
  const columns = useMemo<ColumnDef<TagRow>[]>(() => [
    { field: 'tagId', header: MSG.SEARCH_TAG_ID, type: 'bigint', width: 150, visible: false, sortable: true },
    { field: 'searchText', header: MSG.SEARCH_TAG_TEXT, type: 'text', width: 300, required: true, editable: !blocked },
    { field: 'revision', header: MSG.CHAT_HELP_REVISION, type: 'bigint', width: 130 },
    { field: 'useYn', header: MSG.SEARCH_TAG_USE, type: 'ynFlag', width: 110, editable: !blocked, derive: (row) => row.useYn === 'Y', patch: (_row, value) => ({ useYn: value === true ? 'Y' : 'N' }) },
    ...auditColumns<TagRow>({ sortable: true }),
  ], [blocked]);
  const collect = useCallback(() => {
    serial.current += 1; owner.current = null; drafts.current.clear(); setSelectedId(null); setReferences(null); setError(''); setLoading(false);
    return Promise.resolve(search.current?.collect() ?? []);
  }, []);
  const api = useMemo<GridApi<TagRow>>(() => ({ read: pending ? undefined : { url: E.getList, emptyNotice: true },
    save: { url: E.save, reloadAfter: false, perRow: { after: 'keepFailed' },
      validate: { grid: (rows) => rows.filter((row) => row.state !== null).flatMap((row) => loading || saving || !reason.trim() && !pending
        ? [{ rowId: row.id, message: label(loading ? MSG.SEARCH_TAG_LOADING : MSG.GROUP_REASON_REQUIRED) }] : []) },
      submit: async (payload) => {
        setSaving(true);
        const rows = (payload as { rows: GridRow<TagRow>[] }).rows;
        const report: RowReport = { total: rows.length, done: 0, failed: 0, rows: [] };
        try {
          for (const [index, row] of rows.entries()) {
            try {
              let synonyms = row.data.synonyms ?? drafts.current.get(row.id)?.rows;
              if (!synonyms && row.data.tagId && row.state !== 'D') synonyms = toRows((await loadTagDetails(row.data.tagId, row.data.revision!)).detail.synonyms);
              const result = await commands.save([{ ...row, data: { ...row.data, synonyms: synonyms ?? [] } }], reason.trim());
              report.done += result.done; report.failed += result.failed; report.rows.push(...result.rows.map((entry) => ({ ...entry, index })));
            } catch (failure) {
              report.failed += 1; report.rows.push({ index, fields: [{ field: 'searchText', message: failure instanceof Error ? failure.message : label(MSG.SEARCH_TAG_FAILED) }] });
            }
          }
          return report;
        } finally { setPending(commands.hasPending()); setSaving(false); }
      }, onSuccess: () => setSaved((value) => value + 1),
    } }), [commands, loading, pending, reason, saving]);
  const master = useGridController<TagRow>({ menuCd: MENU, columns, api, params: collect, toSaveRow: (row) => row,
    addRow: { defaults: () => ({ tagId: null, searchText: '', useYn: 'N', revision: null }), canAdd: () => blocked ? { ok: false, message: label(MSG.GROUP_PENDING) } : true },
  });
  const selected = master.rows.find((row) => row.id === selectedId);
  const canEdit = !!selected && selected.state !== 'D' && !blocked && references !== null && !error;
  const detailColumns = useMemo<ColumnDef<Synonym>[]>(() => [
    { field: 'searchText', header: MSG.SEARCH_TAG_TEXT, type: 'text', width: 300, required: true, editable: canEdit },
    { field: 'description', header: MSG.SEARCH_TAG_DESCRIPTION, type: 'text', editor: 'largeText', width: 400, editable: canEdit },
  ], [canEdit]);
  const detail = useGridController<Synonym>({ menuCd: MENU, columns: detailColumns, api: LOCAL_API, saveOwner: master, editable: master.canSave && canEdit,
    addRow: { defaults: () => ({ synonymId: null, clientKey: crypto.randomUUID(), searchText: '', description: '' }), canAdd: () => canEdit ? true : { ok: false, message: label(MSG.SEARCH_TAG_SELECT) } },
  });
  const knowledgeColumns = useMemo<ColumnDef<Knowledge>[]>(() => [
    { field: 'knowledgeId', header: MSG.SEARCH_TAG_KNOWLEDGE_ID, type: 'bigint', width: 130 },
    { field: 'knowledgeNm', header: MSG.SEARCH_TAG_KNOWLEDGE_NAME, type: 'text', width: 280 },
    { field: 'revision', header: MSG.SEARCH_TAG_REVISION, type: 'bigint', width: 130 },
    { field: 'activeYn', header: MSG.SEARCH_TAG_MAPPING, type: 'text', width: 150, derive: (row) => row.activeYn ?? label(MSG.GROUP_UNKNOWN) },
  ], []);
  const knowledge = useGridController<Knowledge>({ menuCd: MENU, columns: knowledgeColumns, editable: false });
  const edit = master.edit; const setDetailRows = detail.setRows; const setKnowledgeRows = knowledge.setRows;
  useEffect(() => {
    if (loading || !selectedId || owner.current !== selectedId) return;
    const draft = drafts.current.get(selectedId); if (!draft) return;
    draft.rows = detail.rows; const hash = fingerprint(detail.rows);
    edit(selectedId, { detailHash: hash === draft.baseline ? undefined : hash });
  }, [detail.rows, edit, loading, selectedId]);
  const select = async (row: GridRow<TagRow> | null, force = false) => {
    if (blocked) return false;
    if (!force && row?.id === selectedId) return true;
    if (selected?.state && selected.state !== 'D') {
      if (!(await confirmDiscard())) return false;
      master.revert(selected.id); drafts.current.delete(selected.id);
    }
    const generation = ++serial.current; owner.current = null; setSelectedId(row?.id ?? null); setReferences(null); setError('');
    setDetailRows([]); setKnowledgeRows([]); if (!row) return true;
    setLoading(true);
    try {
      const found = row.data.tagId ? await loadTagDetails(row.data.tagId, row.data.revision!) : null;
      if (generation !== serial.current) return false;
      const rows = row.data.synonyms ?? toRows(found?.detail.synonyms ?? []);
      const refs: TagReferences = found?.references ?? { revision: '', state: 'NOT_CONNECTED', rows: [], reference: 'ABSENT' };
      const draft = { rows, original: rows, baseline: fingerprint(rows), references: refs };
      drafts.current.set(row.id, draft); owner.current = row.id; setDetailRows(rows); setKnowledgeRows(toRows(refs.rows)); setReferences(refs);
    } catch (failure) { if (generation === serial.current) setError(failure instanceof Error ? failure.message : label(MSG.SEARCH_TAG_FAILED)); }
    finally { if (generation === serial.current) setLoading(false); }
    return true;
  };
  const previousHash = useRef<{ id: string; hash?: string } | null>(null);
  const hash = selected?.data.detailHash;
  useEffect(() => {
    if (previousHash.current?.id === selectedId && previousHash.current.hash && !hash && selectedId) {
      const draft = drafts.current.get(selectedId); if (draft) { draft.rows = draft.original; setDetailRows(draft.original); }
    }
    previousHash.current = selectedId ? { id: selectedId, hash } : null;
  }, [selectedId, hash, setDetailRows]);
  const setMasterRows = master.setRows; const read = master.read;
  const restored = useRef(false); const refreshed = useRef(0);
  useEffect(() => { if (!restored.current) { restored.current = true; const rows = commands.restoredRows(); if (rows.length) setMasterRows(rows); } }, [commands, setMasterRows]);
  useEffect(() => { if (saved > refreshed.current) { refreshed.current = saved; setMasterRows([]); setDetailRows([]); setKnowledgeRows([]); void read(true); } }, [saved, setMasterRows, setDetailRows, setKnowledgeRows, read]);
  const hasSelected = !!selected;
  useEffect(() => { if (!hasSelected) { owner.current = null; setDetailRows([]); setKnowledgeRows([]); } }, [hasSelected, setDetailRows, setKnowledgeRows]);
  useEffect(() => () => { serial.current += 1; }, []);
  const reload = () => { if (selected?.data.tagId) void select(selected, true); };
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{ field: 'searchText', label: MSG.SEARCH_TAG_TEXT, type: 'text', op: 'contains' }, { field: 'tagId', label: MSG.SEARCH_TAG_ID, type: 'text', op: 'eq' },
      { field: 'useYn', label: MSG.SEARCH_TAG_USE, type: 'select', op: 'eq', options: [{ value: 'Y', text: 'Y' }, { value: 'N', text: 'N' }] }]}
      onSubmit={() => void master.read()} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    {error && <Alert type="error" title={error} showIcon />}
    <Input aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000} disabled={blocked} onChange={(event) => setReason(event.target.value)} />
    <div style={{ flex: 1, minHeight: 0 }}><SplitPane storageKey="search-tag"
      first={(collapsed, expand) => <DataGrid title={MSG.SEARCH_TAG_TITLE} menuCd={MENU} gridId="master" controller={master} onSelect={select} collapsed={collapsed} onExpand={expand} excel={{ pageOnly: true }}
        deletable={{ enabled: (row) => { const draft = drafts.current.get(row.id); return !blocked && (row.state === 'C' || row.state === 'D' || !!draft && draft.original.length === 0 && draft.rows.every((item) => item.state === 'D') && draft.references.reference === 'ABSENT' && ['READY', 'NOT_CONNECTED'].includes(draft.references.state)); }, reason: () => label(MSG.SEARCH_TAG_DELETE_BLOCKED) }} />}
      second={() => <DetailTabs activeKey={tab} onChange={setTab} items={[
        { key: 'synonyms', label: label(MSG.SEARCH_TAG_SYNONYMS), forceRender: true, children: <DataGrid title={MSG.SEARCH_TAG_SYNONYMS} menuCd={MENU} gridId="synonyms" controller={detail} onRead={reload} emptyText={selected ? undefined : MSG.SEARCH_TAG_SELECT} excel={{ pageOnly: true }} deletable={{ enabled: () => canEdit, reason: () => label(MSG.SEARCH_TAG_SELECT) }} /> },
        { key: 'knowledge', label: label(MSG.SEARCH_TAG_REFERENCES), forceRender: true, children: <FillColumn>
          {references && references.state !== 'READY' && <Alert type={references.state === 'NOT_CONNECTED' ? 'info' : 'warning'} title={label(MSG[`SEARCH_TAG_${references.state}`])} showIcon />}
          <DataGrid title={MSG.SEARCH_TAG_REFERENCES} menuCd={MENU} gridId="knowledge" controller={knowledge} onRead={reload} emptyText={selected ? undefined : MSG.SEARCH_TAG_SELECT} excel={false} />
        </FillColumn> },
      ]} />} /></div>
  </FillColumn>;
}
