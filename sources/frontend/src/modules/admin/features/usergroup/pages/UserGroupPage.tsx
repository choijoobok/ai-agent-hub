// @feat FEAT-006
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Input } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { DataGrid } from '@/shared/grid/DataGrid';
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
import { GROUP_ENDPOINTS } from '../api/endpoints';
import { UserGroupCommands } from '../api/userGroupApi';
import type { UserGroupRow } from '../types';
import { GroupLinksPanel } from './GroupLinksPanel';

const MENU_CD = 'HUB_USER_GROUP';
export function UserGroupPage() {
  const search = useRef<SearchFormHandle>(null);
  const header = useSearchFormHeader();
  const actor = useAuthStore((store) => store.user?.userId ?? '');
  const commands = useMemo(() => new UserGroupCommands(`user-group-command/${actor}`, sessionStorage), [actor]);
  const [reason, setReason] = useState('');
  const [pending, setPending] = useState(commands.hasPending());
  const [groupId, setGroupId] = useState<string | null>(null);
  const [saved, setSaved] = useState(0);
  const columns = useMemo<ColumnDef<UserGroupRow>[]>(() => [
    { field: 'groupId', header: MSG.GROUP_ID, type: 'bigint', width: 150, sortable: true },
    { field: 'groupNm', header: MSG.GROUP_NAME, type: 'text', width: 180, sortable: true, required: true, editable: !pending, maxLength: 100 },
    { field: 'description', header: MSG.GROUP_DESCRIPTION, type: 'text', editor: 'largeText', width: 300, required: true, editable: !pending },
    { field: 'useState', header: MSG.GROUP_STATE, type: 'text', width: 130,
      derive: (row) => label(row.useState === 'USED' ? MSG.GROUP_USED : row.useState === 'UNUSED' ? MSG.GROUP_UNUSED : MSG.GROUP_UNKNOWN) },
    ...auditColumns<UserGroupRow>({ sortable: true }),
  ], [pending]);
  const api = useMemo<GridApi<UserGroupRow>>(() => ({ read: pending ? undefined : { url: GROUP_ENDPOINTS.getList, emptyNotice: true },
    save: { url: GROUP_ENDPOINTS.save, reloadAfter: false, perRow: { after: 'keepFailed' },
      validate: { grid: (rows) => reason.trim() || commands.hasPending() ? [] : rows.filter((row) => row.state !== null).map((row) => ({ rowId: row.id, message: label(MSG.GROUP_REASON_REQUIRED) })) },
      submit: async (payload) => {
        const result = await commands.save((payload as { rows: GridRow<UserGroupRow>[] }).rows, reason.trim());
        setPending(commands.hasPending()); return result;
      },
      onSuccess: () => { setGroupId(null); setSaved((value) => value + 1); },
    } }), [commands, reason, pending]);
  const collect = useCallback(() => Promise.resolve(search.current?.collect() ?? []), []);
  const master = useGridController<UserGroupRow>({ menuCd: MENU_CD, columns, api, params: collect,
    addRow: { defaults: () => ({ groupId: null, groupNm: '', description: '', revision: null, useState: 'UNKNOWN' }),
      canAdd: () => pending ? { ok: false, message: label(MSG.GROUP_PENDING) } : true },
    toSaveRow: (row) => row,
  });
  const restored = useRef(false);
  const setRows = master.setRows;
  useEffect(() => {
    if (!restored.current) { restored.current = true; const rows = commands.restoredRows(); if (rows.length) setRows(rows); }
  }, [commands, setRows]);
  const refreshed = useRef(0);
  const read = master.read;
  useEffect(() => {
    if (saved > refreshed.current) { refreshed.current = saved; setRows([]); void read(true); }
  }, [saved, setRows, read]);
  const select = useCallback((row: GridRow<UserGroupRow> | null) => { setGroupId(row?.data.groupId ?? null); }, []);
  return <FillColumn>
    <SearchForm handleRef={search} fields={[{ field: 'groupNm', label: MSG.GROUP_NAME, type: 'text', op: 'contains' }]}
      onSubmit={() => { setGroupId(null); void master.read(); }} onReset={master.clearFilters}
      onActiveCountChange={header.onActiveCountChange} collapsed={header.collapsed} />
    {pending && <Alert type="warning" title={label(MSG.GROUP_PENDING)} showIcon />}
    <Input aria-label={label(MSG.GROUP_REASON)} placeholder={label(MSG.GROUP_REASON)} value={reason} maxLength={2000}
      disabled={pending} onChange={(event) => setReason(event.target.value)} />
    <div style={{ flex: 1, minHeight: 0 }}><SplitPane storageKey="user-group"
      first={(collapsed, expand) => <DataGrid<UserGroupRow> title={MSG.GROUP_TITLE} menuCd={MENU_CD} gridId="master" controller={master}
        onSelect={select} collapsed={collapsed} onExpand={expand} excel={{ pageOnly: true }}
        deletable={{ enabled: (row) => !pending && (row.state === 'C' || row.data.useState === 'UNUSED'),
          reason: (row) => row.data.useState !== 'UNUSED' && row.state !== 'C' ? label(MSG.GROUP_DELETE_UNKNOWN) : null }} />}
      second={() => <GroupLinksPanel groupId={groupId} />} /></div>
  </FillColumn>;
}
