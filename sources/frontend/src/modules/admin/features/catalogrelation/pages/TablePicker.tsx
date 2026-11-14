// @feat FEAT-011
import {useEffect, useRef, useState} from 'react';
import {Input, Modal} from 'antd';
import {auditColumns} from '@/shared/grid/auditColumns';
import {DataGrid} from '@/shared/grid/DataGrid';
import {FillColumn} from '@/shared/components/FillColumn';
import {useGridController} from '@/shared/grid/useGridController';
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import {CATALOG_ENDPOINTS as C} from '../../catalog/api/endpoints';
import type {CatalogCandidate} from '../types';
interface Props {sourceId: string; busy: boolean; onCancel: () => void; onApply: (left: CatalogCandidate, right: CatalogCandidate) => Promise<void>}
export function TablePicker({sourceId, busy, onCancel, onApply}: Props) {
  const [left, setLeft] = useState<CatalogCandidate | null>(null); const [right, setRight] = useState<CatalogCandidate | null>(null);
  const initial = useRef(false); const leftTerm = useRef(''); const rightTerm = useRef('');
  const columns = [{field: 'id', header: MSG.REL_CATALOG_ID, type: 'bigint' as const, width: 130}, {field: 'objectName', header: MSG.CAT_OBJECT_NAME, width: 180}, {field: 'businessName', header: MSG.CAT_BUSINESS_NAME, width: 200}, {field: 'schemaName', header: MSG.CAT_SCHEMA, width: 140}, ...auditColumns<CatalogCandidate>()];
  const leftGrid = useGridController<CatalogCandidate>({menuCd: 'HUB_CATALOG', columns, api: {read: {url: C.getList}}, editable: false, searchParams: () => ({dataSourceId: sourceId, keyword: leftTerm.current || null})});
  const rightGrid = useGridController<CatalogCandidate>({menuCd: 'HUB_CATALOG', columns, api: {read: {url: C.getList}}, editable: false, searchParams: () => ({dataSourceId: sourceId, keyword: rightTerm.current || null})});
  const readLeft = leftGrid.read; const readRight = rightGrid.read;
  useEffect(() => {if(initial.current)return;initial.current = true;void readLeft(); void readRight();}, [readLeft, readRight]);
  return <Modal open width={1250} title={label(MSG.REL_PICK)} confirmLoading={busy} onCancel={onCancel} okText={label(MSG.CAT_APPLY)} cancelText={label(MSG.COMMON_CANCEL)} okButtonProps={{disabled: busy || !left || !right}} onOk={() => {if (left && right) void onApply(left, right);}}>
    <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, height: 430}}>
      <FillColumn><Input.Search aria-label={label(MSG.REL_LEFT)} placeholder={label(MSG.REL_SEARCH)} onChange={e => {leftTerm.current = e.target.value;}} onSearch={() => {setLeft(null);void readLeft(true);}} /><DataGrid title={MSG.REL_LEFT} menuCd="HUB_CATALOG" gridId="relation-pick-left" controller={leftGrid} onSelect={row => {setLeft(row?.data ?? null);return true;}} deletable={false} excel={false} /></FillColumn>
      <FillColumn><Input.Search aria-label={label(MSG.REL_RIGHT)} placeholder={label(MSG.REL_SEARCH)} onChange={e => {rightTerm.current = e.target.value;}} onSearch={() => {setRight(null);void readRight(true);}} /><DataGrid title={MSG.REL_RIGHT} menuCd="HUB_CATALOG" gridId="relation-pick-right" controller={rightGrid} onSelect={row => {setRight(row?.data ?? null);return true;}} deletable={false} excel={false} /></FillColumn>
    </div>
  </Modal>;
}
