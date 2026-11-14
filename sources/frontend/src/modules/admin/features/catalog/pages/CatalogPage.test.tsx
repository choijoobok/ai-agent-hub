// @feat FEAT-003
import {App, ConfigProvider} from 'antd';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {useAuthStore} from '@/shared/stores/authStore';
import {MSG} from '@/shared/i18n/messageKeys';
import type {GridController} from '@/shared/grid/useGridController';
import type {GridRow} from '@/shared/grid/rows';
import type {CatalogDetail, ValueRow} from '../types';
import {CatalogPage} from './CatalogPage';
import {CatalogCommands} from '../api/catalogApi';
import type {ReactNode} from 'react';
const mocks = vi.hoisted(() => ({send: vi.fn(), discard: vi.fn()}));
vi.mock('@/shared/api/request', () => ({post: mocks.send, get: vi.fn(), request: vi.fn()}));
vi.mock('@/shared/grid/useConfirmDiscard', () => ({useConfirmDiscard: () => mocks.discard}));
vi.mock('@/shared/code/useCodeOptionsByRef', () => ({useCodeOptionsByRef: () => ({status: 'ready', options: [], retry: vi.fn()})}));
vi.mock('@/shared/searchform/SearchForm', () => ({SearchForm: () => null}));
vi.mock('@/shared/grid/SplitPane', () => ({SplitPane: ({first, second}: {first: (collapsed: boolean, expand: () => void) => ReactNode; second: () => ReactNode}) => <>{first(false, () => {})}{second()}</>}));
vi.mock('@/shared/grid/DetailTabs', () => ({DetailTabs: ({activeKey, items}: {activeKey: string; items: {key: string; children: ReactNode}[]}) => <><span data-testid="active-tab">{activeKey}</span>{items.map(i => <div key={i.key}>{i.children}</div>)}</>}));
// 그리드 엔진은 공통 시험에서 검증한다. 화면의 실제 컨트롤러·명령·모달·영역 전이를 시험한다.
vi.mock('@/shared/grid/DataGrid', () => ({DataGrid: ({gridId, controller: c, onSelect, onRead, controls = []}: {gridId: string; controller: GridController<ValueRow>; onSelect?: (r: GridRow<ValueRow>) => Promise<boolean>; onRead?: () => void; controls?: {key: string; label: string; disabled: boolean; onClick: () => void}[]}) => <section data-testid={gridId}>
  {controls.map(b => <button key={b.key} disabled={b.disabled} onClick={b.onClick}>{b.label}</button>)}
  <button onClick={onRead}>read-{gridId}</button><button disabled={!c.canSave} onClick={() => void c.save()}>save-{gridId}</button>
  <button disabled={!c.canAddRow} onClick={c.addRow}>add-{gridId}</button>
  {c.rows.map(r => <div key={r.id}><button onClick={() => {void onSelect?.(r);}}>select-{typeof r.data.id === 'string' ? r.data.id : ''}</button>
    <button onClick={() => c.toggleDelete(r.id)}>delete-{gridId}-{typeof r.data.groupId === 'string' ? r.data.groupId : ''}</button>
    {['businessName','columnBusinessName','objectName','reason'].map(field => <input key={field} aria-label={`${gridId}-${field}`} value={typeof r.data[field] === 'string' ? r.data[field] : ''} onChange={e => c.edit(r.id, {[field]: e.target.value})} />)}
  </div>)}
</section>}));
let found: CatalogDetail;
beforeEach(() => {
  sessionStorage.clear();mocks.send.mockReset();mocks.discard.mockReset().mockResolvedValue(false);
  useAuthStore.setState({user: {userId: 'catalog-review', userNm: '검토', localeCd: 'ko'}, permissions: ['HUB_CATALOG:READ','HUB_CATALOG:SAVE']});
  found = {row: {id: '7', catalogId: '7', dataSourceId: '2', objectName: 'orders', businessName: '서버 이름', revision: '1'}, values: {master: {dataSourceId: '2', databaseName: 'erp', schemaName: 'public', objectName: 'orders', businessName: '서버 이름', objectType: 'TABLE', useYn: false, reviewStatus: 'VERIFIED', reviewEvidence: '확인한 근거', pkReview: 'UNKNOWN'}, columns: [{columnId: '11', columnName: 'code', columnBusinessName: '기존 컬럼'}], groups: [], exceptions: [], indexes: []}, dependencies: {groupCandidates: []}};
  mocks.send.mockImplementation((url: string, body: ValueRow) => {
    if (url.endsWith('/getReferenceCandidates')) return Promise.resolve({dataSources: [{id: '2', alias: '업무 DB', dbmsCode: 'POSTGRESQL'}], timezones: [], maskingTypes: [], maskingState: 'UNAVAILABLE'});
    if (url.endsWith('/getList')) return Promise.resolve({mode: 'offset', content: [structuredClone(found.row)], totalElements: 1, totalPages: 1, hasNext: false});
    if (url.endsWith('/getOne')) return Promise.resolve(structuredClone(found));
    if (url.endsWith('/getReferences')) return Promise.resolve({state: 'NOT_INSTALLED', rows: []});
    if (url.endsWith('/saveChange')) {
      const values = body.values as ValueRow;
      if (body.scope === 'COLUMNS') {found.values.columns = values.columns as ValueRow[];found.values.master.reviewStatus = 'UNKNOWN';}
      if (body.scope === 'MASTER') found.values.master = values;
      if (body.scope === 'ACCESS_GROUPS') {found.values.groups = values.groups as ValueRow[];found.values.exceptions = found.values.exceptions.filter(e => found.values.groups.some(g => g.groupId === e.groupId));}
      found.row.revision = String(Number(found.row.revision) + 1);
      return Promise.resolve({status: 'APPLIED', targets: [{targetId: found.row.id, appliedRevision: found.row.revision, historyNo: '15'}]});
    }
    throw new Error(`예상하지 않은 호출: ${url}`);
  });
});
afterEach(cleanup);
const start = async () => {render(<ConfigProvider theme={{token: {motion: false}}}><App><CatalogPage /></App></ConfigProvider>);await screen.findByRole('button', {name: 'select-7'});fireEvent.click(screen.getByRole('button', {name: 'select-7'}));await screen.findByDisplayValue('기존 컬럼');await waitFor(() => expect(screen.getByRole('button', {name: 'save-master'}).hasAttribute('disabled')).toBe(false));};
const save = async (area: string) => {
  fireEvent.click(screen.getByRole('button', {name: `save-${area}`}));const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByRole('textbox'), {target: {value: '업무 정의 보완'}});fireEvent.click(within(dialog).getByRole('button', {name: MSG.GRID_CONFIRM_OK}));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  await waitFor(() => expect(screen.getByRole('button', {name: 'save-master'}).hasAttribute('disabled')).toBe(false));
};
it('TC-00323 컬럼 저장 후 마스터 입력을 유지하고 다음 저장은 갱신된 공통 리비전을 사용한다', async () => {
  await start();fireEvent.change(screen.getByRole('textbox', {name: 'master-businessName'}), {target: {value: '미저장 마스터'}});
  fireEvent.change(screen.getByRole('textbox', {name: 'columns-columnBusinessName'}), {target: {value: '보완한 컬럼'}});
  await save('columns');expect(screen.getByRole<HTMLInputElement>('textbox', {name: 'master-businessName'}).value).toBe('미저장 마스터');
  await save('master');const saved = mocks.send.mock.calls.filter(([url]) => String(url).endsWith('/saveChange'));
  expect(saved).toHaveLength(2);expect(saved[0][1]).toMatchObject({scope: 'COLUMNS', expectedRevision: '1', values: {columns: [{columnId: '11', columnBusinessName: '보완한 컬럼'}]}});
  expect((saved[0][1] as ValueRow).values).not.toHaveProperty('master');expect(saved[1][1]).toMatchObject({scope: 'MASTER', expectedRevision: '2', values: {businessName: '미저장 마스터', reviewStatus: 'UNKNOWN'}});
});
it('TC-00323 다른 탭 입력이 있을 때 조회 취소는 입력과 조회 대상을 보존한다', async () => {
  await start();fireEvent.change(screen.getByRole('textbox', {name: 'columns-columnBusinessName'}), {target: {value: '보존할 컬럼'}});
  const calls = mocks.send.mock.calls.length;fireEvent.click(screen.getByRole('button', {name: 'read-master'}));
  await waitFor(() => expect(mocks.discard).toHaveBeenCalledOnce());expect(mocks.send.mock.calls).toHaveLength(calls);
  expect(screen.getByRole<HTMLInputElement>('textbox', {name: 'columns-columnBusinessName'}).value).toBe('보존할 컬럼');
});
it('TC-00323 신규 입력을 폐기하고 기존 행을 고르면 신규 후보는 남지 않는다', async () => {
  await start();mocks.discard.mockResolvedValue(true);
  fireEvent.click(screen.getByRole('button', {name: MSG.CAT_NEW}));
  await waitFor(() => expect(within(screen.getByTestId('master')).getAllByRole('textbox', {name: 'master-objectName'})).toHaveLength(2));
  fireEvent.click(screen.getByRole('button', {name: 'select-7'}));
  await waitFor(() => expect(within(screen.getByTestId('master')).getAllByRole('textbox', {name: 'master-objectName'})).toHaveLength(1));
  expect(mocks.discard).toHaveBeenCalledOnce();
});
it('TC-00324 신규 저장 응답 유실은 원 명령 결과로 ID를 복구하고 유효한 상세 탭을 연다', async () => {
  const workspace = {master: [found.values.master], columns: [], groups: [], exceptions: [], indexes: []};
  const row: GridRow<ValueRow> = {id: 'local-new', state: 'C', original: null, data: found.values.master};
  await new CatalogCommands('catalog/catalog-review/standalone/create', sessionStorage, true, vi.fn().mockRejectedValue(new Error('응답 유실'))).save([{id: row.id, state: 'C', original: null, data: {id: null, revision: null, scope: 'MASTER', values: found.values.master, workspace, snapshots: {master: [row], columns: [], groups: [], exceptions: [], indexes: []}}}], '원 사유');
  const original = mocks.send.getMockImplementation() as (url: string, body: ValueRow) => Promise<unknown>;
  mocks.send.mockImplementation((url: string, body: ValueRow) => url.endsWith('/getCommandResult') ? Promise.resolve({status: 'APPLIED', result: {status: 'APPLIED', targets: [{targetId: '7', appliedRevision: '1', historyNo: '15'}]}}) : original(url, body));
  render(<ConfigProvider theme={{token: {motion: false}}}><App><CatalogPage /></App></ConfigProvider>);
  expect(await screen.findByTestId('active-tab')).toHaveProperty('textContent', 'columns');
  fireEvent.click(await screen.findByRole('button', {name: MSG.TARGET_RESULT}));
  await waitFor(() => expect(screen.getByRole('button', {name: 'add-columns'}).hasAttribute('disabled')).toBe(false));
  expect(mocks.send.mock.calls.some(([url, body]) => String(url).endsWith('/getOne') && (body as ValueRow).id === '7')).toBe(true);
  expect(mocks.send.mock.calls.some(([url]) => String(url).endsWith('/saveCreate'))).toBe(false);
});
it('TC-00325 통합 SAVE로 그룹을 제거하면 해당 예외만 정리하고 다른 그룹의 편집을 보존한다', async () => {
  found.values.groups = [{groupId: '1', mappingActive: true}, {groupId: '2', mappingActive: true}];
  found.values.exceptions = [{columnId: '11', groupId: '1', mode: 'HIDE', reason: '제거될 예외'}, {columnId: '11', groupId: '2', mode: 'HIDE', reason: '다른 그룹 기존 사유'}];
  found.dependencies.groupCandidates = [{groupId: '1', groupName: '영업'}, {groupId: '2', groupName: '물류'}];
  await start();fireEvent.change(screen.getByDisplayValue('다른 그룹 기존 사유'), {target: {value: '물류 미저장 보완'}});
  fireEvent.click(screen.getByRole('button', {name: 'delete-groups-1'}));await save('groups');
  expect(screen.queryByDisplayValue('제거될 예외')).toBeNull();expect(screen.getByDisplayValue('물류 미저장 보완')).toBeTruthy();
  const saved = mocks.send.mock.calls.find(([url]) => String(url).endsWith('/saveChange'));
  expect(saved?.[1]).toMatchObject({scope: 'ACCESS_GROUPS', values: {groups: [{groupId: '2', mappingActive: true}]}});
  expect((saved?.[1] as ValueRow | undefined)?.values).not.toHaveProperty('exceptions');
});
