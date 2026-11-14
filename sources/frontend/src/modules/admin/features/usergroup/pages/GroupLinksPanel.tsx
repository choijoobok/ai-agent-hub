// @feat FEAT-006
import { useEffect, useMemo, useState } from 'react';
import { Alert } from 'antd';
import { DetailTabs } from '@/shared/grid/DetailTabs';
import { DataGrid } from '@/shared/grid/DataGrid';
import { useGridController } from '@/shared/grid/useGridController';
import { auditColumns } from '@/shared/grid/auditColumns';
import { toRows } from '@/shared/grid/rows';
import type { ColumnDef } from '@/shared/grid/types';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import type { GroupProjection, GroupScope } from '../types';
import { useGroupDetail, type ReferenceChange } from './useGroupDetail';
import { GroupMembersEditor, type GroupMemberDraft } from './GroupMembersEditor';

const titles: Record<GroupScope, string> = { membership: MSG.GROUP_MEMBERS, knowledge: MSG.GROUP_KNOWLEDGE, catalog: MSG.GROUP_CATALOG, api: MSG.GROUP_API, tool: MSG.GROUP_TOOL };
type DetailRow = Record<string, unknown>;
function columnsFor(scope: GroupScope): ColumnDef<DetailRow>[] {
  const text = (field: string, header: string): ColumnDef<DetailRow> => ({ field, header, type: 'text', width: 180 });
  if (scope === 'membership') return [text('loginId', MSG.GROUP_LOGIN), text('groupId', MSG.GROUP_ID),
    { field: 'activeYn', header: MSG.GROUP_ACTIVE, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), width: 110 }, ...auditColumns<DetailRow>(), text('memo', MSG.GROUP_MEMO)];
  const names = scope === 'catalog' ? [text('tableNm', MSG.GROUP_TABLE), text('tableDescription', MSG.GROUP_TABLE_DESC), text('systemNm', MSG.GROUP_SYSTEM), text('datasourceNm', MSG.GROUP_DATASOURCE)]
    : [text('assetId', MSG.GROUP_ASSET_ID), text('assetNm', MSG.GROUP_ASSET_NAME)];
  return [...names, { field: 'activeYn', header: MSG.GROUP_MAPPING, type: 'ynFlag', derive: (row) => row.activeYn === 'Y', patch: (_row, value) => ({ activeYn: value === true ? 'Y' : 'N' }), width: 130 }];
}

function ProjectionGrid({ scope, detail, onRead }: { scope: GroupScope; detail?: GroupProjection; onRead: () => void }) {
  const columns = useMemo(() => columnsFor(scope), [scope]);
  const grid = useGridController<DetailRow>({ menuCd: 'HUB_USER_GROUP', columns, editable: false });
  const setRows = grid.setRows;
  useEffect(() => setRows(toRows(detail?.rows ?? [])), [detail, setRows]);
  const state = detail?.state ?? 'SELECT';
  return <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
    {state !== 'READY' && <Alert type={state === 'UNAVAILABLE' || state === 'FORBIDDEN' ? 'warning' : 'info'} title={label(MSG[`GROUP_${state}`])} showIcon />}
    <DataGrid<DetailRow> title={titles[scope]} menuCd="HUB_USER_GROUP" gridId={`links-${scope}`} controller={grid}
      onRead={onRead} excel={false} />
  </div>;
}

export function GroupLinksPanel({ groupId, onReference }: { groupId: string | null; onReference: ReferenceChange }) {
  const members = useMemo(() => new Map<string, GroupMemberDraft>(), []);
  const [selected, setSelected] = useState<Record<string, GroupScope>>({});
  const key = groupId ?? 'new';
  const scope = selected[key] ?? 'membership';
  const { details, onRead } = useGroupDetail(groupId, onReference);
  return <DetailTabs style={{ ['--bo-tab-min-width' as string]: '80px' }} activeKey={scope} onChange={(next) => setSelected((before) => ({ ...before, [key]: next as GroupScope }))}
    items={(Object.keys(titles) as GroupScope[]).map((tab) => ({ key: tab, label: label(titles[tab]),
      children: tab === 'membership' && groupId ? <GroupMembersEditor key={groupId} groupId={groupId} detail={details[tab]} onRead={onRead} cache={members} />
        : <ProjectionGrid key={`${key}/${tab}`} scope={tab} detail={details[tab]} onRead={onRead} /> }))} />;
}
