// @feat FEAT-042
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Input } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { SplitPane } from '@/shared/grid/SplitPane';
import { useGridController } from '@/shared/grid/useGridController';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { auditColumns } from '@/shared/grid/auditColumns';
import { toRows, type GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi } from '@/shared/grid/types';
import { SearchForm, type SearchFormHandle } from '@/shared/searchform/SearchForm';
import { useSearchFormHeader } from '@/shared/searchform/useSearchFormHeader';
import { useAuthStore } from '@/shared/stores/authStore';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { QUOTA_GRADE_ENDPOINTS } from '../api/endpoints';
import { QuotaGradeCommands, fetchQuotaBasis, copyPreviousBasis } from '../api/quotaGradeApi';
import type { QuotaBasis, QuotaDetail, QuotaGrade } from '../types';
import type { RowReport } from '@/shared/api';

const MENU_CD = 'HUB_QUOTA_GRADE';
const DETAIL_API: GridApi<QuotaGrade> = {};
interface Draft { rows: GridRow<QuotaGrade>[]; original: GridRow<QuotaGrade>[]; baseline: string; state: QuotaDetail['referenceState'] }
const fingerprint = (rows: GridRow<QuotaGrade>[]) => JSON.stringify(rows.filter((row) => row.state !== 'D').map((row) => row.data));

export function QuotaGradePage() {
  const search = useRef<SearchFormHandle>(null);
  const askDiscard = useConfirmDiscard();
  const header = useSearchFormHeader();
  const actor = useAuthStore((store) => store.user?.userId ?? '');
  const commands = useMemo(() => new QuotaGradeCommands(`quota-grade-command/${actor}`, sessionStorage), [actor]);
  const drafts = useRef(new Map<string, Draft>());
  const serial = useRef(0);
  const detailOwner = useRef<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [pending, setPending] = useState(commands.hasPending());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(0);
  const [referenceState, setReferenceState] = useState<QuotaDetail['referenceState'] | null>(null);
  const [previous, setPrevious] = useState<{ month: string; detail: QuotaDetail | null } | null>(null);
  const blocked = pending || loading;
  const columns = useMemo<ColumnDef<QuotaBasis>[]>(() => [
    { field: 'startMonth', header: MSG.QUOTA_GRADE_MONTH, type: 'text', width: 180, required: true, sortable: true,
      editable: (row) => !blocked && row.gradeSetId === null, maxLength: 7 },
    { field: 'memo', header: MSG.QUOTA_GRADE_MEMO, type: 'text', editor: 'largeText', width: 370,
      editable: (row) => !blocked && row.editable },
    ...auditColumns<QuotaBasis>({ sortable: true }).map((column) => column.field === 'regId' || column.field === 'regDtm' ? { ...column, visible: true } : column),
  ], [blocked]);
  const collect = useCallback(() => {
    serial.current += 1; detailOwner.current = null; drafts.current.clear(); setSelectedId(null); setReferenceState(null); setError('');
    return Promise.resolve(search.current?.collect() ?? []);
  }, []);
  const api = useMemo<GridApi<QuotaBasis>>(() => ({ read: pending ? undefined : { url: QUOTA_GRADE_ENDPOINTS.getList, emptyNotice: true },
    save: { url: QUOTA_GRADE_ENDPOINTS.save, reloadAfter: false, perRow: { after: 'keepFailed' },
      validate: { grid: (rows) => rows.filter((row) => row.state !== null).flatMap((row) => {
        const message = loading ? MSG.QUOTA_GRADE_DETAIL_REQUIRED : !reason.trim() && !pending ? MSG.GROUP_REASON_REQUIRED
          : !row.data.editable ? MSG.QUOTA_GRADE_LOCKED : drafts.current.get(row.id)?.state === 'UNAVAILABLE' ? MSG.QUOTA_GRADE_UNAVAILABLE : null;
        return message ? [{ rowId: row.id, message: label(message) }] : [];
      }) },
      submit: async (payload) => {
        const rows = (payload as { rows: GridRow<QuotaBasis>[] }).rows;
        const report: RowReport = { total: rows.length, done: 0, failed: 0, rows: [] };
        for (const [index, row] of rows.entries()) {
          try {
            let grades = row.data.grades ?? drafts.current.get(row.id)?.rows;
            if (!grades && row.data.gradeSetId && row.state !== 'D') {
              const found = await fetchQuotaBasis(row.data.gradeSetId);
              if (found.basis.revision !== row.data.revision) throw new Error(label(MSG.QUOTA_GRADE_STALE));
              grades = toRows(found.grades);
            }
            const result = await commands.save([{ ...row, data: { ...row.data, grades: grades ?? [] } }], reason.trim());
            report.done += result.done; report.failed += result.failed;
            report.rows.push(...result.rows.map((entry) => ({ ...entry, index })));
          } catch (failure) {
            report.failed += 1; report.rows.push({ index, fields: [{ field: 'startMonth', message: failure instanceof Error ? failure.message : label(MSG.QUOTA_GRADE_UNAVAILABLE) }] });
          }
        }
        setPending(commands.hasPending()); return report;
      },
      onSuccess: () => setSaved((value) => value + 1),
    } }), [commands, loading, pending, reason]);
  const master = useGridController<QuotaBasis>({ menuCd: MENU_CD, columns, api, params: collect,
    addRow: { defaults: () => ({ gradeSetId: null, startMonth: '', memo: '', revision: null, editable: true }),
      canAdd: () => blocked ? { ok: false, message: label(MSG.GROUP_PENDING) } : true }, toSaveRow: (row) => row,
  });
  const selected = master.rows.find((row) => row.id === selectedId);
  const canEdit = !!selected && selected.state !== 'D' && selected.data.editable && !blocked && referenceState !== 'UNAVAILABLE';
  const [gradeOptions, setGradeOptions] = useState<{ value: string; text: string }[]>([]);
  const detailColumns = useMemo<ColumnDef<QuotaGrade>[]>(() => [
    { field: 'gradeNm', header: MSG.QUOTA_GRADE_NAME, type: 'text', width: 150, required: true, maxLength: 100, editable: canEdit },
    { field: 'rankNo', header: MSG.QUOTA_GRADE_RANK, type: 'number', editor: 'number', width: 95, required: true, editable: canEdit },
    { field: 'weight', header: MSG.QUOTA_GRADE_WEIGHT, type: 'number', editor: 'number', width: 140, required: true, editable: canEdit,
      patch: (_row, value) => ({ weight: typeof value === 'number' || typeof value === 'string' ? String(value) : '' }) },
    { field: 'transferGradeId', header: MSG.QUOTA_GRADE_TRANSFER, type: 'code', width: 150, editor: 'select', options: gradeOptions, editable: canEdit },
    { field: 'description', header: MSG.QUOTA_GRADE_DESCRIPTION, type: 'text', width: 250, editable: canEdit },
    { field: 'referenceUsers', header: MSG.QUOTA_GRADE_REFERENCES, type: 'number', width: 145,
      derive: (row) => row.referenceUsers ?? label(MSG.QUOTA_GRADE_UNKNOWN) },
  ], [canEdit, gradeOptions]);
  const detail = useGridController<QuotaGrade>({ menuCd: MENU_CD, columns: detailColumns, api: DETAIL_API, saveOwner: master,
    editable: master.canSave && canEdit,
    addRow: { canAdd: () => canEdit ? true : { ok: false, message: label(MSG.QUOTA_GRADE_SELECT) },
      defaults: () => ({ gradeId: null, clientKey: crypto.randomUUID(), gradeNm: '', rankNo: 1, weight: '1.00', description: '', referenceUsers: null }) },
  });
  const masterEdit = master.edit;
  useEffect(() => {
    if (loading || !selectedId || detailOwner.current !== selectedId) return;
    const found = drafts.current.get(selectedId); if (!found) return;
    found.rows = detail.rows;
    const hash = fingerprint(detail.rows);
    masterEdit(selectedId, { detailHash: hash === found.baseline ? undefined : hash });
    setGradeOptions(detail.rows.filter((row) => row.state !== 'D' && row.data.gradeId).map((row) => ({ value: row.data.gradeId!, text: row.data.gradeNm })));
  }, [detail.rows, loading, masterEdit, selectedId]);
  const select = async (row: GridRow<QuotaBasis> | null, force = false) => {
    if (blocked) return false;
    if (!force && row?.id === selectedId) return true;
    const request = ++serial.current; detailOwner.current = null; setSelectedId(row?.id ?? null); setReferenceState(null); setError('');
    detail.setRows([]); if (!row) return true;
    setLoading(true);
    try {
      let draft = force ? undefined : drafts.current.get(row.id);
      if (!draft) {
        const found = row.data.gradeSetId ? await fetchQuotaBasis(row.data.gradeSetId) : null;
        if (request !== serial.current) return false;
        if (found && found.basis.revision !== row.data.revision) throw new Error(label(MSG.QUOTA_GRADE_STALE));
        const rows = row.data.grades ?? toRows(found?.grades ?? []);
        draft = { rows, original: rows, baseline: fingerprint(rows), state: found?.referenceState ?? 'NOT_INSTALLED' };
        drafts.current.set(row.id, draft);
        if (force) master.edit(row.id, { detailHash: undefined });
      }
      detailOwner.current = row.id; detail.setRows(draft.rows); setReferenceState(draft.state);
    } catch (failure) { setError(failure instanceof Error ? failure.message : label(MSG.QUOTA_GRADE_UNAVAILABLE)); setReferenceState('UNAVAILABLE'); }
    finally { if (request === serial.current) setLoading(false); }
    return true;
  };
  const restored = useRef(false); const masterSetRows = master.setRows;
  useEffect(() => { if (!restored.current) { restored.current = true; const rows = commands.restoredRows(); if (rows.length) masterSetRows(rows); } }, [commands, masterSetRows]);
  const refreshed = useRef(0); const read = master.read; const detailSetRows = detail.setRows;
  useEffect(() => { if (saved > refreshed.current) { refreshed.current = saved; masterSetRows([]); detailSetRows([]); void read(true); } }, [saved, masterSetRows, detailSetRows, read]);
  const hasSelected = !!selected;
  useEffect(() => { if (!hasSelected) { detailOwner.current = null; detailSetRows([]); } }, [selectedId, hasSelected, detailSetRows]);
  const previousHash = useRef<{ id: string; hash?: string } | null>(null);
  const selectedHash = selected?.data.detailHash;
  useEffect(() => {
    const old = previousHash.current;
    if (old?.id === selectedId && old.hash && !selectedHash && selectedId) {
      const draft = drafts.current.get(selectedId);
      if (draft) { draft.rows = draft.original; detailSetRows(draft.original); }
    }
    previousHash.current = selectedId ? { id: selectedId, hash: selectedHash } : null;
  }, [selectedId, selectedHash, detailSetRows]);
  const copyMonth = selected?.state === 'C' ? selected.data.startMonth : null;
  useEffect(() => {
    if (pending || !copyMonth || !/^[1-9][0-9]{3}-(0[1-9]|1[0-2])$/.test(copyMonth)) return;
    let active = true;
    void copyPreviousBasis(copyMonth).then((found) => { if (active) setPrevious({ month: copyMonth, detail: found }); })
      .catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : label(MSG.QUOTA_GRADE_UNAVAILABLE)); });
    return () => { active = false; };
  }, [copyMonth, pending]);
  const reloadDetail = async () => {
    if (!selected || blocked || (selected.data.detailHash && !(await askDiscard()))) return;
    await select(selected, true);
  };
  const copy = async () => {
    if (!selected || selected.state !== 'C' || blocked || previous?.month !== selected.data.startMonth) return;
    if (selected.data.detailHash && !(await askDiscard())) return;
    const found = previous.detail;
    const rows = toRows(found?.grades ?? []);drafts.current.set(selected.id, { rows, original: [], baseline: '', state: found?.referenceState ?? 'NOT_INSTALLED' });
    detailOwner.current = selected.id; detail.setRows(rows); setReferenceState(found?.referenceState ?? 'NOT_INSTALLED');
  };
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{ field: 'startMonth', label: MSG.QUOTA_GRADE_MONTH, type: 'text', op: 'eq' }, { field: 'gradeNm', label: MSG.QUOTA_GRADE_NAME, type: 'text', op: 'eq' }]}
      onSubmit={() => void master.read()} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    {error && <Alert type="error" title={error} showIcon />}
    <Input aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000} disabled={blocked} onChange={(event) => setReason(event.target.value)} />
    <div style={{ flex: 1, minHeight: 0 }}><SplitPane storageKey="quota-grade"
      first={(collapsed, expand) => <DataGrid title={MSG.QUOTA_GRADE_TITLE} menuCd={MENU_CD} gridId="master" controller={master} onSelect={select}
        collapsed={collapsed} onExpand={expand} excel={{ pageOnly: true }}
        controls={[{ key: 'copy', type: 'button', slot: 'beforeTools', label: MSG.QUOTA_GRADE_COPY, actionCd: 'SAVE',
          disabled: blocked || selected?.state !== 'C' || previous?.month !== copyMonth, hint: MSG.QUOTA_GRADE_COPY_NEW, onClick: () => void copy() }]}
        deletable={{ enabled: (row) => !blocked && (row.state === 'C' || row.state === 'D' || row.data.editable && drafts.current.has(row.id)
          && (drafts.current.get(row.id)!.state === 'NOT_INSTALLED' || drafts.current.get(row.id)!.state === 'READY' && drafts.current.get(row.id)!.rows.every((grade) => grade.data.referenceUsers === 0))),
          reason: (row) => label(row.data.editable ? MSG.QUOTA_GRADE_DETAIL_REQUIRED : MSG.QUOTA_GRADE_LOCKED) }} />}
      second={() => <FillColumn>
        {selected && referenceState && referenceState !== 'READY' && <Alert type={referenceState === 'UNAVAILABLE' ? 'warning' : 'info'} title={label(referenceState === 'UNAVAILABLE' ? MSG.QUOTA_GRADE_UNAVAILABLE : MSG.QUOTA_GRADE_NOT_INSTALLED)} showIcon />}
        <DataGrid title={MSG.QUOTA_GRADE_DETAIL} menuCd={MENU_CD} gridId="grades" controller={detail} onRead={() => void reloadDetail()} excel={{ pageOnly: true }}
          emptyText={MSG.QUOTA_GRADE_SELECT} deletable={{ enabled: () => canEdit, reason: () => label(MSG.QUOTA_GRADE_LOCKED) }} />
      </FillColumn>} /></div>
  </FillColumn>;
}
