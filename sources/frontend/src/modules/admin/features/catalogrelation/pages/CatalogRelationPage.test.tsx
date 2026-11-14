// @feat FEAT-011
import {App, ConfigProvider} from 'antd';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {ReactNode} from 'react';
import {useAuthStore} from '@/shared/stores/authStore';
import type {GridController} from '@/shared/grid/useGridController';
import type {GridRow} from '@/shared/grid/rows';
import {MSG} from '@/shared/i18n/messageKeys';
import {CatalogRelationPage} from './CatalogRelationPage';
import type {OwnerDetail, RelationRow} from '../types';
const display = (value: unknown) => typeof value === 'string' || typeof value === 'number' ? String(value) : '';
const mocks = vi.hoisted(() => ({send: vi.fn(), discard: vi.fn()}));
vi.mock('@/shared/api/request', () => ({post: mocks.send, get: vi.fn(), request: vi.fn()}));
vi.mock('@/shared/grid/useConfirmDiscard', () => ({useConfirmDiscard: () => mocks.discard}));
vi.mock('@/shared/code/useCodeOptionsByRef', () => ({useCodeOptionsByRef: () => ({status: 'ready', options: [{value: 'PG_INT', text: '정수'}], retry: vi.fn()})}));
vi.mock('@/shared/searchform/SearchForm', () => ({SearchForm: ({fields}: {fields: {options?: {value: string; text: string}[]}[]}) => <div>{fields.flatMap(field => field.options ?? []).map(option => <span key={option.value}>{option.text}</span>)}</div>}));
vi.mock('@/shared/grid/SplitPane', () => ({SplitPane: ({first, second}: {first: (collapsed: boolean, expand: () => void) => ReactNode; second: () => ReactNode}) => <>{first(false, () => {})}{second()}</>}));
vi.mock('@/shared/grid/DetailTabs', () => ({DetailTabs: ({items}: {items: {key: string; children: ReactNode}[]}) => <>{items.map(item => <div key={item.key}>{item.children}</div>)}</>}));
// 공통 그리드 대신 화면 컨트롤러/선택/명령/모달과 편집 보존을 검증한다.
vi.mock('@/shared/grid/DataGrid', () => ({DataGrid: ({gridId, controller: c, onSelect, onRead, controls = []}: {gridId: string; controller: GridController<RelationRow>; onSelect?: (row: GridRow<RelationRow>) => Promise<boolean>; onRead?: () => void; controls?: {key: string; label: string; disabled: boolean; onClick: () => void}[]}) => <section data-testid={gridId}>
  {controls.map(button => <button key={button.key} disabled={button.disabled} onClick={button.onClick}>{button.label}</button>)}
  <button onClick={onRead}>read-{gridId}</button><button disabled={!c.canSave} onClick={() => void c.save()}>save-{gridId}</button>
  {c.rows.map(row => <div key={row.id}><button onClick={() => void onSelect?.(row)}>select-{gridId}-{display(row.data.relationId ?? row.data.columnId ?? row.data.id)}</button>
    <button onClick={() => c.toggleDelete(row.id)}>delete-{gridId}-{display(row.data.relationId ?? row.data.position)}</button><span>{display(row.data.leftName)}/{display(row.data.rightName)}</span>
    {['relationName','description'].map(field => <input key={field} disabled={!c.canEdit} aria-label={`${gridId}-${row.data.relationId ?? ''}-${field}`} value={typeof row.data[field] === 'string' ? row.data[field] : ''} onChange={event => c.edit(row.id, {[field]: event.target.value})} />)}
  </div>)}
</section>}));
let owner: OwnerDetail;
beforeEach(() => {
  sessionStorage.clear();mocks.send.mockReset();mocks.discard.mockReset().mockResolvedValue(false);
  useAuthStore.setState({user: {userId: 'relation-test', userNm: '관계', localeCd: 'ko'}, permissions: ['HUB_CATALOG:READ','HUB_CATALOG:SAVE','HUB_CATALOG_RELATION:READ']});
  const base: RelationRow = {relationId: '11', ownerCatalogId: '7', revision: '3', relationName: '관계 A', dataSourceId: '2', leftCatalogId: '7', rightCatalogId: '8', leftRevision: '3', rightRevision: '2', leftRole: '주문', rightRole: '고객', cardinality: 'N:1', description: '원 설명', reviewStatus: 'UNKNOWN', pairs: [{position: 1, leftColumnId: '71', rightColumnId: '81'}], leftColumns: [{columnId: '71', columnName: '주문고객', sourceTypeCode: 'PG_INT'}], rightColumns: [{columnId: '81', columnName: '고객번호', sourceTypeCode: 'PG_INT'}]};
  owner = {row: {id: '7', revision: '3'}, values: {master: {dataSourceId: '2'}, columns: [], relations: [base, {...base, relationId: '12', relationName: '관계 B'}, {...base, relationId: '13', relationName: '화면 밖 관계'}]}, dependencies: {}};
  mocks.send.mockImplementation((url: string, body: Record<string, unknown>) => {
    if(url.endsWith('/getReferenceCandidates')) return Promise.resolve({dataSources: [{id: '2', alias: '업무', dbmsCode: 'POSTGRESQL'}], timezones: [], maskingTypes: [], maskingState: 'NOT_CONNECTED'});
    if(url.endsWith('/getRelationList')) return Promise.resolve({mode: 'offset', content: structuredClone(owner.values.relations.slice(0, 2)), totalElements: 2, totalPages: 1, hasNext: false});
    if(url.endsWith('/getOne')) return Promise.resolve(structuredClone(owner));
    if(url.endsWith('/getList')) return Promise.resolve({mode: 'offset', content: [{id: '7', dataSourceId: '2', revision: '3', objectName: 'orders'}, {id: '8', dataSourceId: '2', revision: '2', objectName: 'customers'}], totalElements: 2, totalPages: 1, hasNext: false});
    if(url.endsWith('/saveChange')) {owner.values.relations = (body.values as {relations: RelationRow[]}).relations;owner.row.revision = '4';return Promise.resolve({status: 'APPLIED', targets: [{targetId: '7', appliedRevision: '4', historyNo: '19'}]});}
    throw new Error(`예상하지 않은 호출 ${url}`);
  });
});
afterEach(cleanup);
const start = async () => {render(<ConfigProvider theme={{token: {motion: false}}}><App><CatalogRelationPage /></App></ConfigProvider>);await screen.findByRole('button', {name: 'select-relation-main-11'});fireEvent.click(screen.getByRole('button', {name: 'select-relation-main-11'}));await screen.findByText('주문고객/고객번호');};
it('TC-0111 TC-0116 다른 관계 이동 후 편집과 화면 밖 전체 소유 관계를 함께 보존한다', async () => {
  await start();fireEvent.change(screen.getByRole('textbox', {name: 'relation-main-11-description'}), {target: {value: '첫 관계 편집'}});
  fireEvent.click(screen.getByRole('button', {name: 'select-relation-main-12'}));fireEvent.change(screen.getByRole('textbox', {name: 'relation-main-12-description'}), {target: {value: '둘째 관계 편집'}});
  fireEvent.click(screen.getByRole('button', {name: 'save-relation-main'}));const dialog = await screen.findByRole('dialog');fireEvent.change(within(dialog).getByRole('textbox'), {target: {value: '변경 사유'}});fireEvent.click(within(dialog).getByRole('button', {name: MSG.GRID_CONFIRM_OK}));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(owner.values.relations.map(row => row.description)).toEqual(['첫 관계 편집','둘째 관계 편집','원 설명']);
  const saved = mocks.send.mock.calls.find(([url]) => String(url).endsWith('/saveChange'));
  expect(saved?.[1]).toMatchObject({targetId: '7', expectedRevision: '3', scope: 'RELATIONSHIPS'});
});
it('TC-0116 테이블 변경의 컬럼 쌍 초기화를 취소하면 원 편집을 보존한다', async () => {
  await start();fireEvent.click(screen.getByRole('button', {name: MSG.REL_PICK}));await screen.findByRole('button', {name: 'select-relation-pick-left-8'});
  fireEvent.click(screen.getByRole('button', {name: 'select-relation-pick-left-8'}));fireEvent.click(screen.getByRole('button', {name: 'select-relation-pick-right-7'}));
  fireEvent.click(screen.getByRole('button', {name: MSG.CAT_APPLY}));const dialogs = await screen.findAllByRole('dialog');await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(2));
  const confirm = screen.getAllByRole('dialog').find(dialog => within(dialog).queryByRole('heading', {name: MSG.REL_RESET_PAIRS}) || dialog.querySelector('.ant-modal-confirm-title')?.textContent === MSG.REL_RESET_PAIRS);expect(confirm).toBeTruthy();fireEvent.click(within(confirm!).getByRole('button', {name: MSG.COMMON_CANCEL}));
  await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1));expect(within(screen.getByTestId('relation-pairs')).getByText('주문고객/고객번호')).toBeTruthy();expect(dialogs.length).toBeGreaterThan(0);
});
it('TC-0067 TC-0116 조회 전용 관리자는 관계와 컬럼 쌍을 저장할 수 없다', async () => {
  useAuthStore.setState({permissions: ['HUB_CATALOG:READ','HUB_CATALOG_RELATION:READ']});await start();
  expect(screen.getByRole('button', {name: MSG.REL_NEW}).hasAttribute('disabled')).toBe(true);expect(screen.getByRole('button', {name: 'save-relation-main'}).hasAttribute('disabled')).toBe(true);expect(screen.getByRole('button', {name: MSG.REL_ADD_PAIR}).hasAttribute('disabled')).toBe(true);
  expect(mocks.send.mock.calls.some(([url]) => String(url).endsWith('/saveChange'))).toBe(false);
});

it('TC-0117 관계 삭제 취소는 편집한 컬럼 쌍 캐시도 원본으로 되돌린다', async () => {
  await start();fireEvent.click(screen.getByRole('button', {name: 'delete-relation-pairs-1'}));
  fireEvent.click(screen.getByRole('button', {name: 'delete-relation-main-11'}));fireEvent.click(screen.getByRole('button', {name: 'delete-relation-main-11'}));
  fireEvent.change(screen.getByRole('textbox', {name: 'relation-main-11-description'}), {target: {value: '원복 뒤 설명'}});
  fireEvent.click(screen.getByRole('button', {name: 'save-relation-main'}));const dialog = await screen.findByRole('dialog');fireEvent.change(within(dialog).getByRole('textbox'), {target: {value: '삭제 취소 검증'}});fireEvent.click(within(dialog).getByRole('button', {name: MSG.GRID_CONFIRM_OK}));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());expect(owner.values.relations[0].pairs).toEqual([{position: 1, leftColumnId: '71', rightColumnId: '81'}]);
});

it('TC-0116 관계 목록보다 늦게 도착한 데이터소스 후보도 유지한다', async () => {
  let resolveOptions!: (value: unknown) => void;const pendingOptions = new Promise(resolve => {resolveOptions = resolve;});const previous = mocks.send.getMockImplementation()!;
  mocks.send.mockImplementation((url: string, body: Record<string, unknown>) => url.endsWith('/getReferenceCandidates') ? pendingOptions : previous(url, body) as unknown);
  await start();resolveOptions({dataSources: [{id: '2', alias: '늦은 데이터소스', dbmsCode: 'POSTGRESQL'}], timezones: [], maskingTypes: [], maskingState: 'NOT_CONNECTED'});
  expect(await screen.findByText('늦은 데이터소스')).toBeTruthy();
});
