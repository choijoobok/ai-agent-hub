// @feat FEAT-005
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Input, Space, Switch } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { useGridController } from '@/shared/grid/useGridController';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { auditColumns } from '@/shared/grid/auditColumns';
import { toRows, type GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi } from '@/shared/grid/types';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { useAuthStore } from '@/shared/stores/authStore';
import { hasPermission } from '@/shared/authz/permission';
import { PORTAL_USER_ENDPOINTS } from '../api/endpoints';
import { fetchPortalDetail, PortalScopeCommands } from '../api/portalUserApi';
import type { PortalDetail, PortalDetailRow, PortalScope, PortalUserRow } from '../types';
import { PORTAL_TABS } from './portalTabs';

export interface PortalDraftCache { detail: PortalDetail; rows: GridRow<PortalDetailRow>[]; reason: string; deactivate: boolean; choiceMonth: string }
interface Props { userId: string; scope: PortalScope; observedRevision?: string; cache: Map<string, PortalDraftCache>; onCommitted: (user: PortalUserRow) => void }
export function PortalDetailEditor({ userId, scope, observedRevision, cache, onCommitted }: Props) {
  const key = `${userId}/${scope}`;
  const actor = useAuthStore((store) => store.user?.userId ?? '');
  const permissions = useAuthStore((store) => store.permissions);
  const commands = useMemo(() => new PortalScopeCommands(`portal-scope/${actor}/${key}`, sessionStorage), [actor, key]);
  const [detail, setDetail] = useState<PortalDetail | null>(cache.get(key)?.detail ?? null);
  const [reason, setReason] = useState(cache.get(key)?.reason ?? '');
  const [deactivate, setDeactivate] = useState(cache.get(key)?.deactivate ?? false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(!cache.has(key));
  const [pending, setPending] = useState(commands.hasPending());
  const [refresh, setRefresh] = useState(0);
  const [choiceMonth, setChoiceMonth] = useState(cache.get(key)?.choiceMonth ?? '');
  const choiceRequest = useRef(0);
  const restored = useRef(false);
  const askDiscard = useConfirmDiscard();
  const stale = !!detail && !!observedRevision && detail.user.revision !== observedRevision;
  const writable = hasPermission(permissions, 'HUB_PORTAL_USER', 'SAVE') && scope !== 'extraTokens' && !!detail && !loading && !pending && !stale && (detail.state === 'READY' || scope === 'grades' && detail.state === 'NOT_INSTALLED');
  const columns = useMemo<ColumnDef<PortalDetailRow>[]>(() => {
    const text = (field: string, header: string, editable = false, width = 180): ColumnDef<PortalDetailRow> => ({ field, header, type: 'text', editable, width });
    if (scope === 'memberships') return [
      { field: 'groupId', header: MSG.GROUP_ID, type: 'code', editor: 'richSelect', width: 180, required: true,
        editable: (row) => writable && !row.regDtm, options: (detail?.choices ?? []).map((r) => ({ value: String(r.groupId), text: `${String(r.groupNm)} (${String(r.groupId)})` })),
        patch: (_row, value) => ({ groupId: value, groupNm: detail?.choices.find((r) => r.groupId === value)?.groupNm ?? '' }) },
      text('groupNm', MSG.GROUP_NAME), { field: 'activeYn', header: MSG.GROUP_ACTIVE, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), editable: writable, width: 120 },
      { ...text('memo', MSG.GROUP_MEMO, writable, 250), required: true, maxLength: 2000 }, ...auditColumns<PortalDetailRow>()];
    if (scope === 'grades') return [
      { ...text('effectiveMonth', MSG.PORTAL_EFFECTIVE_MONTH), required: true, maxLength: 7, editable: (row) => writable && (!row.regDtm || String(row.effectiveMonth) > detail.today.substring(0, 7)) },
      { field: 'gradeId', header: MSG.QUOTA_GRADE_NAME, type: 'code', editor: 'select', width: 180, required: true, renderer: 'badge',
        display: (value) => detail?.gradeChoices.find((r) => r.gradeId === value)?.gradeNm ?? String(detail?.rows.find((r) => r.gradeId === value)?.gradeNm ?? value),
        options: detail?.gradeChoices.map((row) => ({ value: row.gradeId, text: row.gradeNm })) ?? [], editable: (row) => writable && String(row.effectiveMonth) >= detail.today.substring(0, 7) },
      { field: 'activeYn', header: MSG.GROUP_ACTIVE, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), width: 120, editable: (row) => writable && String(row.effectiveMonth) > detail.today.substring(0, 7) },
      text('sourceKind', MSG.PORTAL_SOURCE), text('changeReason', MSG.GROUP_REASON, false, 240), ...auditColumns<PortalDetailRow>()];
    if (scope === 'extraTokens') return [text('requestId', MSG.PORTAL_REQUEST_ID), text('requestedTokens', MSG.PORTAL_REQUESTED), text('approvedTokens', MSG.PORTAL_APPROVED), text('status', MSG.PORTAL_STATUS), text('reason', MSG.GROUP_REASON, false, 260), ...auditColumns<PortalDetailRow>()];
    const editable = (row: PortalDetailRow) => writable && row.available === true;
    return [text('targetId', MSG.GROUP_ASSET_ID, false, 130), text('targetNm', MSG.GROUP_ASSET_NAME),
      { ...text('externalUserId', MSG.PORTAL_EXTERNAL_ID), editable, maxLength: 200 },
      { field: 'mappingActiveYn', header: MSG.GROUP_MAPPING, type: 'ynFlag', derive: (row) => row.mappingActiveYn === 'Y', patch: (_row, value) => ({ mappingActiveYn: value === true ? 'Y' : 'N' }), width: 140, editable },
      { ...text('identityEvidence', MSG.PORTAL_IDENTITY_EVIDENCE, false, 300), editable, editor: 'largeText' },
      text('reason', MSG.PORTAL_TARGET_STATE), ...auditColumns<PortalDetailRow>()];
  }, [detail, scope, writable]);
  const api = useMemo<GridApi<PortalDetailRow>>(() => scope === 'extraTokens' ? {} : ({ save: {
    url: PORTAL_USER_ENDPOINTS.save, reloadAfter: false, perRow: { after: 'keepFailed' },
    validate: { grid: (rows) => {
      const problem = stale ? MSG.PORTAL_STALE : !reason.trim() && !pending ? MSG.GROUP_REASON_REQUIRED : !detail ? MSG.PORTAL_SELECT : null;
      return problem ? rows.map((r) => ({ rowId: r.id, message: label(problem) })) : [];
    } },
    submit: async (payload) => {
      const rows = (payload as { rows: GridRow<PortalDetailRow>[] }).rows;
      const result = await commands.save([{ id: key, state: 'U', original: null, data: { portalUserId: userId, revision: detail!.user.revision!, scope, rows, deactivateUser: deactivate } }], reason.trim());
      setPending(commands.hasPending());
      return { total: rows.length, done: result.done ? rows.length : 0, failed: result.failed ? rows.length : 0,
        rows: result.failed ? rows.map((_row, index) => ({ index, fields: [{ field: scope === 'grades' ? 'gradeId' : scope === 'memberships' ? 'activeYn' : 'externalUserId', message: result.rows[0]?.fields[0]?.message ?? label(MSG.GROUP_PENDING) }] })) : [] };
    }, onSuccess: () => { cache.delete(key); restored.current = false; setRefresh((v) => v + 1); },
  } }), [cache, commands, deactivate, detail, key, pending, reason, scope, stale, userId]);
  const grid = useGridController<PortalDetailRow>({ menuCd: 'HUB_PORTAL_USER', columns, api, editable: writable,
    addRow: scope === 'memberships' || scope === 'grades' ? { canAdd: () => writable ? true : { ok: false, message: label(MSG.PORTAL_SELECT) },
      defaults: () => scope === 'memberships' ? { groupId: '', groupNm: '', activeYn: 'Y', memo: '' } : { effectiveMonth: '', gradeId: '', activeYn: 'Y' } } : undefined,
    toSaveRow: (row) => row,
  });
  const setRows = grid.setRows;
  useEffect(() => {
    let active = true;
    const saved = cache.get(key);
    const pendingRows = commands.restoredRows()[0]?.data;
    if (saved && refresh === 0) { setRows(pendingRows?.rows ?? saved.rows); restored.current = true; return; }
    void fetchPortalDetail(userId, scope).then((found) => {
      if (!active) return;
      let content = found.rows;
      if (scope.startsWith('identity')) content = found.choices.map((target) => ({ ...target, externalUserId: '', mappingActiveYn: 'N', identityEvidence: '', ...found.rows.find((r) => r.targetId === target.targetId) }));
      if (pendingRows) found = { ...found, user: { ...found.user, revision: pendingRows.revision } };
      setDetail(found);setRows(pendingRows?.rows ?? toRows(content));setError('');restored.current = true;onCommitted(found.user);
    }).catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [cache, commands, key, onCommitted, refresh, scope, setRows, userId]);
  useEffect(() => { if (restored.current && detail) cache.set(key, { detail, rows: grid.rows, reason, deactivate, choiceMonth }); }, [cache, choiceMonth, deactivate, detail, grid.rows, key, reason]);
  const reload = useCallback(async () => {
    if (pending || loading || grid.dirty && !(await askDiscard())) return;
    cache.delete(key);restored.current = false;setLoading(true);setRefresh((v) => v + 1);
  }, [askDiscard, cache, grid.dirty, key, loading, pending]);
  const chooseMonth = async (month: string) => {
    setChoiceMonth(month);const request = ++choiceRequest.current;
    if (!/^[1-9][0-9]{3}-(0[1-9]|1[0-2])$/.test(month) || !detail) return;
    setLoading(true);
    try {
      const found = await fetchPortalDetail(userId, 'grades', month);
      if (request !== choiceRequest.current) return;
      onCommitted(found.user);
      if (found.user.revision !== detail.user.revision) { setError(label(MSG.PORTAL_STALE));return; }
      setDetail((before) => before ? { ...before, gradeChoices: found.gradeChoices } : before);
    } catch (failure) { if (request === choiceRequest.current) setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE)); }
    finally { if (request === choiceRequest.current) setLoading(false); }
  };
  return <FillColumn>
    {error && <Alert type="error" title={error} showIcon />}
    {stale && <Alert type="warning" title={label(MSG.PORTAL_STALE)} showIcon />}
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    {detail && detail.state !== 'READY' && <Alert type="info" title={label(detail.state === 'NOT_INSTALLED' ? MSG.PORTAL_NOT_INSTALLED : MSG.PORTAL_NOT_CONNECTED)} showIcon />}
    {scope === 'grades' && detail?.allocation && <Space>{label(MSG.PORTAL_TOKEN_LIMIT)}: {detail.allocation.tokenLimit ?? '—'} / {label(MSG.PORTAL_TOKEN_USED)}: {detail.allocation.usedTokens ?? '—'} / {label(MSG.PORTAL_TOKEN_REMAINING)}: {detail.allocation.remainingTokens ?? '—'}</Space>}
    {scope === 'grades' && <Space><Input type="month" aria-label={label(MSG.QUOTA_GRADE_MONTH)} value={choiceMonth || detail?.today.substring(0, 7) || ''} disabled={pending} onChange={(e) => void chooseMonth(e.target.value)} /><span>{label(MSG.PORTAL_GRADE_HINT)}</span></Space>}
    {scope !== 'extraTokens' && <Space.Compact block>
      <Input value={reason} aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} maxLength={2000} disabled={pending} onChange={(e) => setReason(e.target.value)} />
      {scope === 'memberships' && <Space style={{ whiteSpace: 'nowrap', paddingInline: 8 }}><Switch checked={deactivate} disabled={!writable} onChange={setDeactivate} />{label(MSG.PORTAL_DEACTIVATE_TOGETHER)}</Space>}
    </Space.Compact>}
    <DataGrid title={PORTAL_TABS[scope]} menuCd="HUB_PORTAL_USER" gridId={scope} controller={grid} onRead={() => void reload()} excel={{ pageOnly: true }}
      deletable={{ enabled: (row) => writable && row.state === 'C', reason: () => label(MSG.PORTAL_KEEP_HISTORY) }} />
  </FillColumn>;
}
