// @feat FEAT-005
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Input, Modal, Space } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
import { DetailTabs } from '@/shared/grid/DetailTabs';
import { SplitPane } from '@/shared/grid/SplitPane';
import { useGridController } from '@/shared/grid/useGridController';
import { auditColumns } from '@/shared/grid/auditColumns';
import type { GridRow } from '@/shared/grid/rows';
import type { ColumnDef, GridApi } from '@/shared/grid/types';
import { SearchForm, type SearchFormHandle } from '@/shared/searchform/SearchForm';
import { useSearchFormHeader } from '@/shared/searchform/useSearchFormHeader';
import { useAuthStore } from '@/shared/stores/authStore';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { PORTAL_USER_ENDPOINTS } from '../api/endpoints';
import { fetchPortalDetail, PortalProfileCommands, PortalCredentialCommand, type CredentialResult } from '../api/portalUserApi';
import type { PortalScope, PortalUserRow } from '../types';
import { PortalDetailEditor, type PortalDraftCache } from './PortalDetailEditor';
import { PORTAL_TABS } from './portalTabs';

const MENU_CD = 'HUB_PORTAL_USER';
export function PortalUserPage() {
  const search = useRef<SearchFormHandle>(null);
  const header = useSearchFormHeader();
  const { modal } = App.useApp();
  const actor = useAuthStore((store) => store.user?.userId ?? '');
  const commands = useMemo(() => new PortalProfileCommands(`portal-profile/${actor}`, sessionStorage), [actor]);
  const credentials = useMemo(() => new PortalCredentialCommand(`portal-credential/${actor}`, sessionStorage), [actor]);
  const drafts = useRef(new Map<string, PortalDraftCache>());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [today, setToday] = useState('');
  const [mode, setMode] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(commands.hasPending());
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(0);
  const [secret, setSecret] = useState<CredentialResult | null>(null);
  const [observed, setObserved] = useState<Record<string, string>>({});
  const [tabs, setTabs] = useState<Record<string, PortalScope>>({});
  const blocked = busy || pending;
  const columns = useMemo<ColumnDef<PortalUserRow>[]>(() => [
    { field: 'loginId', header: MSG.GROUP_LOGIN, type: 'text', width: 180, required: (row) => row.authModeCd === 'LOCAL', maxLength: 100, sortable: true, editable: (row) => !blocked && row.portalUserId === null && row.authModeCd === 'LOCAL' },
    { field: 'userNm', header: MSG.PORTAL_NAME, type: 'text', width: 150, required: (row) => row.authModeCd === 'LOCAL', maxLength: 100, sortable: true, editable: (row) => !blocked && row.authModeCd === 'LOCAL' },
    { field: 'organizationNm', header: MSG.PORTAL_ORGANIZATION, type: 'text', width: 160, maxLength: 200, editable: (row) => !blocked && row.authModeCd === 'LOCAL' },
    { field: 'authModeCd', header: MSG.PORTAL_MODE, type: 'ynFlag', derive: (row) => row.authModeCd === 'LOCAL', width: 105, visible: false },
    { field: 'sourceCd', header: MSG.PORTAL_SOURCE, type: 'text', width: 120 },
    { field: 'externalSource', header: MSG.PORTAL_EXTERNAL_SOURCE, type: 'text', width: 150, visible: mode === 'EXTERNAL', required: (row) => row.portalUserId === null && row.authModeCd === 'EXTERNAL', editable: (row) => !blocked && row.portalUserId === null && row.authModeCd === 'EXTERNAL' },
    { field: 'externalSubject', header: MSG.PORTAL_EXTERNAL_SUBJECT, type: 'text', width: 180, visible: mode === 'EXTERNAL', required: (row) => row.portalUserId === null && row.authModeCd === 'EXTERNAL', editable: (row) => !blocked && row.portalUserId === null && row.authModeCd === 'EXTERNAL' },
    { field: 'useStartDt', header: MSG.PORTAL_START, type: 'date', width: 145, required: true, editable: !blocked },
    { field: 'useEndDt', header: MSG.PORTAL_END, type: 'date', width: 145, editable: !blocked },
    { field: 'activeYn', header: MSG.PORTAL_ACTIVE, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), width: 115, editable: (row) => !blocked && row.portalUserId !== null },
    { field: 'lockedYn', header: MSG.PORTAL_LOCKED, type: 'ynFlag', derive: (row) => row.lockedYn === 'Y', width: 125 },
    { field: 'mustChange', header: MSG.PORTAL_MUST_CHANGE, type: 'ynFlag', width: 150 },
    { field: 'passwordChangedDtm', header: MSG.USER_COL_PWD_CHG, type: 'dateTime', width: 190 },
    ...auditColumns<PortalUserRow>({ sortable: true }),
  ], [blocked, mode]);
  const api = useMemo<GridApi<PortalUserRow>>(() => ({ read: pending ? undefined : { url: PORTAL_USER_ENDPOINTS.getList, emptyNotice: true },
    save: { url: PORTAL_USER_ENDPOINTS.save, reloadAfter: false, perRow: { after: 'keepFailed' },
      validate: { grid: (rows) => rows.filter((r) => r.state !== null).flatMap((r) => {
        const problem = !reason.trim() && !pending ? MSG.GROUP_REASON_REQUIRED : r.data.portalUserId && observed[r.data.portalUserId] && observed[r.data.portalUserId] !== r.data.revision ? MSG.PORTAL_STALE : null;
        return problem ? [{ rowId: r.id, message: label(problem) }] : [];
      }) },
      submit: async (payload) => { const result = await commands.save((payload as { rows: GridRow<PortalUserRow>[] }).rows, reason.trim());setPending(commands.hasPending());return result; },
      onSuccess: () => { setSelectedId(null);setSaved((v) => v + 1); },
    } }), [commands, observed, pending, reason]);
  const collect = useCallback(() => { setSelectedId(null);return Promise.resolve(search.current?.collect() ?? []); }, []);
  const master = useGridController<PortalUserRow>({ menuCd: MENU_CD, columns, api, params: collect, toSaveRow: (row) => row,
    addRow: { defaults: () => ({ portalUserId: null, loginId: '', userNm: '', organizationNm: '', authModeCd: mode, sourceCd: mode, useStartDt: today, useEndDt: null, activeYn: 'N', revision: null }),
      canAdd: () => !blocked && !!today ? true : { ok: false, message: label(MSG.PORTAL_UNAVAILABLE) } },
  });
  const selected = master.rows.find((r) => r.id === selectedId);
  const setRows = master.setRows;
  const rowsRef = useRef(master.rows);
  useEffect(() => { rowsRef.current = master.rows; }, [master.rows]);
  const onCommitted = useCallback((user: PortalUserRow) => {
    if (!user.portalUserId || !user.revision) return;
    setObserved((before) => before[user.portalUserId!] === user.revision ? before : { ...before, [user.portalUserId!]: user.revision! });
    const rows = rowsRef.current;
    if (rows.some((r) => r.data.portalUserId === user.portalUserId && r.state === null && r.data.revision !== user.revision))
      setRows(rows.map((r) => r.data.portalUserId === user.portalUserId && r.state === null ? { ...r, data: user, original: user } : r));
  }, [setRows]);
  const restored = useRef(false);
  useEffect(() => { if (!restored.current) { restored.current = true;const rows = commands.restoredRows();if (rows.length) setRows(rows); } }, [commands, setRows]);
  useEffect(() => { let current = true;void fetchPortalDetail(null, 'defaults').then((value) => { if (current) {setToday(value.today);setMode(String(value.credential.authMode));} }).catch((failure: unknown) => { if (current) setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE)); });return () => { current = false; }; }, []);
  const refreshed = useRef(0); const read = master.read;
  useEffect(() => { if (saved > refreshed.current) { refreshed.current = saved;setRows([]);void read(true); } }, [read, saved, setRows]);
  const performCredential = async (scope: 'resetCredential' | 'unlockCredential') => {
    if (!selected?.data.portalUserId || !reason.trim() || blocked) return;
    const user = selected.data;setBusy(true);setError('');
    try {const result = await credentials.run(user, scope, reason.trim());setSecret(result);const updated = await fetchPortalDetail(user.portalUserId, 'profile');onCommitted(updated.user);}
    catch (failure) {setError(failure instanceof Error ? failure.message : label(MSG.PORTAL_UNAVAILABLE));}
    finally {setBusy(false);}
  };
  const confirmCredential = (scope: 'resetCredential' | 'unlockCredential') => modal.confirm({ title: label(scope === 'resetCredential' ? MSG.PORTAL_RESET : MSG.PORTAL_UNLOCK), content: label(MSG.PORTAL_CREDENTIAL_CONFIRM), onOk: () => performCredential(scope) });
  const userId = selected?.data.portalUserId ?? null;
  const tab = tabs[userId ?? ''] ?? 'memberships';
  const credentialEnabled = master.canSave && !!userId && selected?.state === null && selected.data.authModeCd === 'LOCAL' && !!reason.trim() && !blocked;
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{ field: 'loginId', label: MSG.GROUP_LOGIN, type: 'text', op: 'contains' }, { field: 'userNm', label: MSG.PORTAL_NAME, type: 'text', op: 'contains' }, { field: 'activeYn', label: MSG.PORTAL_ACTIVE, type: 'select', op: 'eq', options: [{ value: 'Y', text: label(MSG.PORTAL_ENABLED) }, { value: 'N', text: label(MSG.PORTAL_DISABLED) }] }]}
      onSubmit={() => void master.read()} onReset={master.clearFilters} onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    {error && <Alert type="error" title={error} showIcon />}
    {(pending || credentials.pending()) && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    <Space.Compact block><Input value={reason} aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} maxLength={2000} disabled={blocked} onChange={(e) => setReason(e.target.value)} />
      <Button disabled={!credentialEnabled} onClick={() => { void confirmCredential('resetCredential'); }}>{label(MSG.PORTAL_RESET)}</Button>
      <Button disabled={!credentialEnabled} onClick={() => { void confirmCredential('unlockCredential'); }}>{label(MSG.PORTAL_UNLOCK)}</Button>
    </Space.Compact>
    <div style={{ flex: 1, minHeight: 0 }}><SplitPane storageKey="portal-user"
      first={(collapsed, expand) => <DataGrid title={MSG.PORTAL_TITLE} menuCd={MENU_CD} gridId="master" controller={master} onSelect={(row) => { setSelectedId(row?.id ?? null); }}
        collapsed={collapsed} onExpand={expand} excel={{ pageOnly: true }} deletable={{ enabled: (row) => !blocked && row.state === 'C', reason: () => label(MSG.PORTAL_KEEP_HISTORY) }} />}
      second={() => userId ? <DetailTabs activeKey={tab} onChange={(value) => setTabs((before) => ({ ...before, [userId]: value as PortalScope }))} style={{ ['--bo-tab-min-width' as string]: '80px' }}
        items={(Object.keys(PORTAL_TABS) as PortalScope[]).map((scope) => ({ key: scope, label: label(PORTAL_TABS[scope]), children: <PortalDetailEditor key={`${userId}/${scope}`} userId={userId} scope={scope} observedRevision={observed[userId]} cache={drafts.current} onCommitted={onCommitted} /> }))} />
        : <Alert type="info" title={label(MSG.PORTAL_SELECT)} showIcon />} /></div>
    <Modal open={secret !== null} title={label(MSG.PORTAL_CREDENTIAL_RESULT)} onCancel={() => setSecret(null)} onOk={() => setSecret(null)} destroyOnHidden>
      {secret?.password ? <><Alert type="warning" title={label(MSG.PORTAL_SECRET_ONCE)} /><Input readOnly value={secret.password} aria-label={label(MSG.PORTAL_TEMP_PASSWORD)} autoComplete="off" /></>
        : <Alert type="info" title={label(secret?.secretState === 'UNLOCKED' ? MSG.PORTAL_UNLOCKED : MSG.PORTAL_SECRET_LOST)} />}
    </Modal>
  </FillColumn>;
}
