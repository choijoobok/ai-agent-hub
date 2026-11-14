// @feat FEAT-003
import {Alert, Descriptions, Form, InputNumber, Modal, Select, Table} from 'antd';
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import {display} from '../columns';
import type {ValueRow} from '../types';

export interface EditorState {kind: 'dimensions' | 'source'; row: ValueRow; value: ValueRow}
interface Props {editor: EditorState | null; setEditor: (value: EditorState | null) => void; onApply: (row: ValueRow, patch: ValueRow, kind: 'dimensions') => void}
const dimensionFields = [['characterLength', MSG.CAT_CHAR_LENGTH], ['octetLength', MSG.CAT_OCTET_LENGTH], ['numericPrecision', MSG.CAT_PRECISION], ['numericScale', MSG.CAT_SCALE]];

export function CatalogEditors({editor, setEditor, onApply}: Props) {
  const update = (patch: ValueRow) => {if (editor) setEditor({...editor, value: {...editor.value, ...patch}});};
  const source = editor?.kind === 'source';
  return <Modal open={editor !== null} width={source ? 1050 : 680} title={label(source ? MSG.CAT_OBJECT_CHECK : MSG.CAT_DIMENSIONS)} onCancel={() => setEditor(null)} footer={source ? null : undefined} okText={label(MSG.CAT_APPLY)} cancelText={label(MSG.COMMON_CANCEL)} onOk={() => {
    if (!editor || editor.kind === 'source') return;
    onApply(editor.row, {sourceDimensions: editor.value}, editor.kind);
  }}>
    {editor?.kind === 'dimensions' && <Form layout="vertical">
      <Alert type="info" title={label(MSG.CAT_DIMENSION_HELP)} />
      <Form.Item label={label(MSG.CAT_DIMENSION_STATE)}><Select value={editor.value.state as string} onChange={state => update({state})} options={[
        {value: 'KNOWN', label: label(MSG.CAT_KNOWN)}, {value: 'UNKNOWN', label: label(MSG.CAT_UNKNOWN)}, {value: 'NA', label: label(MSG.CAT_NA)}, {value: 'UNSUPPORTED', label: 'UNSUPPORTED'},
      ]} /></Form.Item>
      {dimensionFields.map(([field, title]) => <Form.Item key={field} label={label(title)}><InputNumber aria-label={label(title)} style={{width: '100%'}} disabled={editor.value.state !== 'KNOWN'} min={field === 'numericScale' ? undefined : 0} precision={0} value={editor.value[field] as number | null} onChange={value => update({[field]: value})} /></Form.Item>)}
      <Form.Item label={label(MSG.CAT_LENGTH_UNIT)}><Select allowClear disabled={editor.value.state !== 'KNOWN'} value={editor.value.lengthUnit as string | undefined} onChange={value => update({lengthUnit: value ?? null})} options={['CHAR','BYTE'].map(value => ({value, label: value}))} /></Form.Item>
    </Form>}
    {source && editor && <>
      <Alert type="info" title={label(MSG.CAT_SOURCE_HELP)} />
      <Descriptions size="small" column={2} items={[
        {key: 'state', label: label(MSG.CAT_STATE), children: display(editor.value.state ?? editor.value.status, 'UNKNOWN')},
        {key: 'reason', label: label(MSG.CAT_REASON), children: display(editor.value.reason)},
        {key: 'type', label: label(MSG.CAT_OBJECT_TYPE), children: display(editor.value.objectType)},
        {key: 'time', label: label(MSG.CAT_APPLIED), children: display(editor.value.observedAt)},
      ]} />
      <Table<ValueRow> size="small" rowKey={r => String(r.name)} pagination={false} scroll={{x: 900, y: 240}} dataSource={editor.value.columns as ValueRow[] ?? []} columns={[
        {title: label(MSG.CAT_COLUMN_NAME), dataIndex: 'name'}, {title: label(MSG.CAT_SOURCE_TEXT), dataIndex: 'formattedType'},
        {title: label(MSG.CAT_NULLABLE), dataIndex: 'nullable', render: v => label(v === 'YES' ? MSG.CAT_NO : v === 'NO' ? MSG.CAT_YES : MSG.CAT_UNKNOWN)},
        {title: label(MSG.CAT_CHAR_LENGTH), dataIndex: 'characterLength'}, {title: label(MSG.CAT_OCTET_LENGTH), dataIndex: 'octetLength'}, {title: label(MSG.CAT_PRECISION), dataIndex: 'numericPrecision'}, {title: label(MSG.CAT_SCALE), dataIndex: 'numericScale'},
      ]} />
      <Table<ValueRow> size="small" rowKey={r => String(r.name)} pagination={false} scroll={{x: 900, y: 220}} dataSource={editor.value.indexes as ValueRow[] ?? []} columns={[
        {title: label(MSG.CAT_INDEX_NAME), dataIndex: 'name', width: 200}, {title: label(MSG.CAT_RAW_DEFINITION), dataIndex: 'definition'},
      ]} />
      <Table<ValueRow> size="small" rowKey={r => String(r.name)} pagination={false} dataSource={editor.value.keys as ValueRow[] ?? []} columns={[
        {title: label(MSG.CAT_INDEX_KIND), dataIndex: 'kind'}, {title: label(MSG.CAT_PK_NAME), dataIndex: 'name'},
        {title: label(MSG.CAT_INDEX_COLUMNS), dataIndex: 'columns', render: (value: unknown) => Array.isArray(value) ? value.map(v => display(v)).join(', ') : '—'},
      ]} />
    </>}
  </Modal>;
}
