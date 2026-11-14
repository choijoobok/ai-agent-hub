// @feat FEAT-003
import type {ColumnDef, SelectOption} from '@/shared/grid/types';
import {auditColumns} from '@/shared/grid/auditColumns';
import {label} from '@/shared/i18n/label';
import {MSG} from '@/shared/i18n/messageKeys';
import type {CatalogOptions, ValueRow} from './types';

export const choice = (...values: string[]): SelectOption[] => values.map(value => ({value, text: value}));
export const display = (value: unknown, fallback = '—'): string => typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint' ? String(value) : fallback;
export const reviewed = (): SelectOption[] => [
  {value: 'UNKNOWN', text: label(MSG.CAT_UNKNOWN)}, {value: 'VERIFIED', text: label(MSG.CAT_VERIFIED)}, {value: 'INVALID', text: label(MSG.CAT_INVALID)},
];
export const triState = (na = false): SelectOption[] => [
  {value: 'UNKNOWN', text: label(MSG.CAT_UNKNOWN)}, {value: 'YES', text: label(MSG.CAT_YES)}, {value: 'NO', text: label(MSG.CAT_NO)}, ...(na ? [{value: 'NA', text: label(MSG.CAT_NA)}] : []),
];
type Editable = ColumnDef<ValueRow>['editable'];
const text = (field: string, header: string, editable: Editable, wide = false): ColumnDef<ValueRow> => ({field, header, type: 'text', width: wide ? 250 : 150, editable, editor: wide ? 'largeText' : 'text', patch: (_row, value) => ({[field]: value === '' ? null : value})});
const select = (field: string, header: string, options: SelectOption[], editable: Editable): ColumnDef<ValueRow> => ({field, header, type: 'code', editor: 'richSelect', width: 150, editable, options});
const number = (field: string, header: string, editable: Editable): ColumnDef<ValueRow> => ({field, header, type: 'number', width: 110, editable, patch: (_row, value) => ({[field]: value === '' || value === null ? null : Number(value)})});

export function masterColumns(editable: Editable, options: CatalogOptions | null): ColumnDef<ValueRow>[] {
  return [
    {field: 'id', header: MSG.GROUP_ASSET_ID, type: 'bigint', width: 120, visible: false},
    {...select('dataSourceId', MSG.DS_TITLE, options?.dataSources.filter(d => d.dbmsCode === 'POSTGRESQL').map(d => ({value: d.id, text: d.alias})) ?? [], editable), required: true, editable: (r, ctx) => ctx.state === 'C' && !!editable && (typeof editable !== 'function' || editable(r, ctx))},
    {...text('objectName', MSG.CAT_OBJECT_NAME, editable), required: true},
    text('businessName', MSG.CAT_BUSINESS_NAME, editable),
    select('objectType', MSG.CAT_OBJECT_TYPE, [{value: 'TABLE', text: label(MSG.CAT_TABLE)}, {value: 'VIEW', text: label(MSG.CAT_VIEW)}], editable),
    {field: 'useYn', header: MSG.CAT_USE, type: 'ynFlag', width: 100, editable},
    {...text('databaseName', MSG.CAT_DATABASE, editable), required: true},
    {...text('schemaName', MSG.CAT_SCHEMA, editable), required: true},
    text('description', MSG.CAT_DESCRIPTION, editable, true),
    {field: 'dbmsCode', header: MSG.DS_DBMS, type: 'text', width: 140, derive: r => options?.dataSources.find(d => d.id === r.dataSourceId)?.dbmsCode ?? null},
    {field: 'dbmsVersion', header: MSG.DS_VERSION, type: 'text', width: 140, derive: r => options?.dataSources.find(d => d.id === r.dataSourceId)?.dbmsVersion ?? null},
    select('reviewStatus', MSG.CAT_REVIEW, reviewed(), editable),
    text('reviewEvidence', MSG.CAT_EVIDENCE, editable, true),
    {field: 'revision', header: MSG.CAT_REVISION, type: 'bigint', width: 120},
    ...[
      text('definitionEvidence', MSG.CAT_DEFINITION, editable, true), text('identityEvidence', MSG.CAT_IDENTITY, editable, true),
      text('pkName', MSG.CAT_PK_NAME, editable), select('pkReview', MSG.CAT_PK_REVIEW, reviewed(), editable), text('pkEvidence', MSG.CAT_PK_EVIDENCE, editable, true),
      text('serverName', MSG.CAT_SERVER, false), text('identityProfile', MSG.CAT_PROFILE, false), text('appliedAt', MSG.CAT_APPLIED, false),
      text('reviewerId', MSG.CAT_REVIEWER, false), text('reviewedAt', MSG.CAT_REVIEWED_AT, false),
    ].map(c => ({...c, visible: false})), ...auditColumns<ValueRow>(),
  ];
}

export function columnColumns(editable: boolean, types: SelectOption[], options: CatalogOptions | null, editDimensions: (row: ValueRow) => void, view = false): ColumnDef<ValueRow>[] {
  return [
    number('ordinalPosition', MSG.CAT_ORDER, editable), text('columnName', MSG.CAT_COLUMN_NAME, editable), text('columnBusinessName', MSG.CAT_COLUMN_BUSINESS, editable),
    select('sourceTypeCode', MSG.CAT_SOURCE_TYPE, types, editable && types.length > 0), text('sourceType', MSG.CAT_SOURCE_TEXT, editable),
    {field: 'sourceDimensions', header: MSG.CAT_DIMENSIONS, type: 'text', width: 160, derive: r => dimensionSummary(r.sourceDimensions), onDoubleClick: editDimensions},
    {...select('primaryKeySummary', MSG.CAT_PK, triState(true), editable && !view), derive: r => view ? 'NA' : r.primaryKeySummary}, {...number('pkPosition', MSG.CAT_PK_POSITION, editable && !view), derive: r => view ? null : r.pkPosition}, {...select('nullable', MSG.CAT_NULLABLE, triState(), editable), derive: r => r.nullable === 'YES' ? 'NO' : r.nullable === 'NO' ? 'YES' : 'UNKNOWN', patch: (_row, value) => ({nullable: value === 'YES' ? 'NO' : value === 'NO' ? 'YES' : 'UNKNOWN'})},
    select('privacyFlag', MSG.CAT_PRIVACY, triState(), editable), select('maskingRef', MSG.CAT_MASK, options?.maskingTypes ?? [], editable && options?.maskingState === 'READY'),
    text('groupCode', MSG.CAT_GROUP_CODE, editable), text('detailDescription', MSG.CAT_DESCRIPTION, editable, true),
    ...[
      {field: 'columnId', header: MSG.GROUP_ASSET_ID, type: 'bigint' as const, width: 120},
      text('privacyType', MSG.CAT_PRIVACY_TYPE, editable), select('subjectIdentifier', MSG.CAT_SUBJECT, triState(), editable), select('privacyReview', MSG.CAT_PRIVACY_REVIEW, reviewed(), editable),
      select('semanticType', MSG.CAT_SEMANTIC, choice('UNKNOWN','DATE','DATETIME','AMOUNT','QUANTITY','FLAG','CODE','IDENTIFIER','TEXT'), editable),
      select('columnReviewStatus', MSG.CAT_COLUMN_REVIEW, reviewed(), editable), text('columnReviewEvidence', MSG.CAT_EVIDENCE, editable, true),
      text('valueFormat', MSG.CAT_FORMAT, editable), {...select('columnTimeZoneId', MSG.CAT_TIMEZONE, [{value: '', text: label(MSG.DS_UNSPECIFIED)}, ...(options?.timezones.map(value => ({value, text: value})) ?? [])], editable), patch: (_row: ValueRow, value: unknown) => ({columnTimeZoneId: value === '' ? null : value})},
      text('unitCode', MSG.CAT_UNIT, editable), select('valueReviewStatus', MSG.CAT_VALUE_REVIEW, reviewed(), editable), text('valueReviewEvidence', MSG.CAT_VALUE_EVIDENCE, editable, true), text('columnIdentityEvidence', MSG.CAT_COLUMN_IDENTITY, editable, true),
    ].map(c => ({...c, visible: false})),
  ];
}
export function dimensionSummary(value: unknown): string {
  if (!value || typeof value !== 'object') return label(MSG.CAT_UNKNOWN);
  const d = value as ValueRow;
  if (d.state !== 'KNOWN') return d.state === 'NA' ? label(MSG.CAT_NA) : display(d.state, label(MSG.CAT_UNKNOWN));
  return [d.characterLength, d.octetLength, d.numericPrecision, d.numericScale].map(x => display(x)).join(' / ');
}
export const masterFields = ['dataSourceId','serverName','databaseName','schemaName','objectName','objectType','businessName','description','useYn','definitionEvidence','identityEvidence','reviewStatus','reviewEvidence','pkName','pkReview','pkEvidence'];
export const masterValues = (row: ValueRow): ValueRow => Object.fromEntries(masterFields.map(k => [k, row[k] ?? null]));
export const newColumn = (): ValueRow => ({clientKey: crypto.randomUUID(), ordinalPosition: null, columnName: null, columnBusinessName: null, sourceTypeCode: null, sourceType: null, sourceDimensions: {state: 'UNKNOWN'}, primaryKeySummary: 'UNKNOWN', pkPosition: null, nullable: 'UNKNOWN', privacyFlag: 'UNKNOWN', maskingRef: null, privacyType: null, subjectIdentifier: 'UNKNOWN', privacyReview: 'UNKNOWN', semanticType: 'UNKNOWN', columnReviewStatus: 'UNKNOWN', groupCode: null, detailDescription: null, valueFormat: null, columnTimeZoneId: null, unitCode: null, valueReviewStatus: 'UNKNOWN', columnReviewEvidence: null, valueReviewEvidence: null, columnIdentityEvidence: null});
