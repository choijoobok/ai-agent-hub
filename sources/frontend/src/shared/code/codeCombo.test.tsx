import { createRef, useEffect } from 'react';
import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DataGrid } from '@/shared/grid/DataGrid';
import { toRows } from '@/shared/grid/rows';
import { useGridController } from '@/shared/grid/useGridController';
import type { ColumnDef } from '@/shared/grid/types';
import { SearchForm, type SearchFormHandle } from '@/shared/searchform/SearchForm';
import type { SearchField } from '@/shared/searchform/types';
import { useMessageStore } from '@/shared/i18n/messageStore';
import { useCodeStore } from '@/shared/code/codeStore';
import { CodeSelect } from '@/shared/code/CodeSelect';

const post = vi.fn();

vi.mock('@/shared/api/request', () => ({
  post: (url: string, body?: unknown) => post(url, body) as Promise<unknown>,
  get: vi.fn(),
  request: vi.fn(),
}));

/**
 * 선언에 <b>그룹코드만</b> 적으면 조회조건과 그리드가 공통코드 콤보를 낸다 — sys/code.md · frontend/search-form.md.
 *
 * <p>이 파일이 지키는 것은 <b>세 조각이 실제로 이어져 있다</b>는 것이다.
 *
 * <ol>
 *   <li>선언(`SearchField.grpCd` · `ColumnDef.grpCd`)이 캐시에 <b>요청을 일으킨다</b>
 * <li>서버가 준 <b>코드값</b>에 `SYS_MESSAGE` 의 이름이 <b>메모리에서</b> 붙는다(sys/code.md)
 *   <li>고른 값은 <b>코드값</b>이다 — 화면에 보이는 이름이 조건으로 나가면 서버가 못 찾는다
 * </ol>
 *
 * <p><b>화면 하나가 콤보를 여럿 띄워도 요청은 하나다.</b> 조회조건과 그리드가 같은 커밋에
 * 뜨므로, 그 둘의 그룹이 한 번에 모여 나가는지를 함께 본다 — sys/code.md 이 프론트 캐시의 근거로
 * 든 것이 그 왕복이다.
 *
 * <p>이름이 없는 코드는 <b>키 문자열이 그대로</b> 보인다(sys/code.md). 빈 칸으로 떨어뜨리지
 * 않는 것이 규약이며, 여기서도 그것을 검사한다 — 등록을 빠뜨렸다는 사실이 화면에서 즉시
 * 드러나야 한다.
 */
describe('공통코드 콤보', () => {
  interface Row {
    id: string;
    status: string;
  }

  const STATUS_CODES = ['NEW', 'PAID', 'DONE'];

  beforeEach(() => {
    post.mockReset();
    window.localStorage.clear();
    // 캐시는 모듈 싱글턴이다 — 앞 케이스가 받아 둔 것이 뒤로 새면 요청 수가 어긋난다.
    useCodeStore.setState({ version: null, groups: {} });

    useMessageStore.setState({
      status: 'ready',
      messages: {
        'code.ORD_STATUS.NEW': '신규',
        'code.ORD_STATUS.PAID': '결제완료',
        // DONE 은 일부러 비운다 — 키가 그대로 보이는 것이 규약이다(sys/code.md).
      },
    });

    post.mockResolvedValue({ version: 1, groups: { ORD_STATUS: STATUS_CODES } });
  });

  describe('조회조건 — frontend/search-form.md', () => {
    const fields: SearchField[] = [
      { field: 'status', label: 'x', type: 'select', ops: ['eq', 'in'], grpCd: 'ORD_STATUS' },
    ];

    function mount() {
      const handle = createRef<SearchFormHandle>();
      render(<SearchForm fields={fields} handleRef={handle} onSubmit={() => undefined} />);
      return handle;
    }

    it('그룹코드만 적으면 후보를 받아 온다 — 화면에는 이름이 보인다', async () => {
      mount();

      await waitFor(() =>
        expect(post).toHaveBeenCalledWith('/system/common/code/getList', { grpCds: ['ORD_STATUS'] }));

      fireEvent.mouseDown(screen.getByRole('combobox'));

      await waitFor(() => expect(screen.queryByText('신규')).not.toBeNull());
      expect(screen.queryByText('결제완료')).not.toBeNull();
    });

    it('이름이 없는 코드는 키 문자열이 그대로 보인다 — 누락이 시끄럽게 드러난다(sys/code.md)', async () => {
      mount();
      await waitFor(() => expect(post).toHaveBeenCalled());

      fireEvent.mouseDown(screen.getByRole('combobox'));

      await waitFor(() => expect(post).toHaveBeenCalled());

      // 후보가 온 <b>뒤</b>에 셀이 다시 그려진다 — 판을 띄운 채 기다리므로 여유를 준다.
      await waitFor(() => expect(screen.queryByText('code.ORD_STATUS.DONE')).not.toBeNull(), {
        timeout: 5000,
      });
    });

    it('조건으로 나가는 것은 이름이 아니라 코드값이다', async () => {
      const handle = mount();
      await waitFor(() => expect(post).toHaveBeenCalled());

      fireEvent.mouseDown(screen.getByRole('combobox'));
      await waitFor(() => expect(screen.queryByText('신규')).not.toBeNull());
      fireEvent.click(screen.getByText('신규'));

      await waitFor(() =>
        expect(handle.current?.collect()).toEqual([
          { field: 'status', op: 'eq', value: 'NEW' },
        ]));
    });

    it('캐시가 차 있으면 서버를 부르지 않는다 — 두 번째 화면 진입이 그 자리다', async () => {
      mount();
      await waitFor(() => expect(post).toHaveBeenCalledTimes(1));

      // 탭을 닫았다 다시 연 모양이다 — 스토어는 살아 있다.
      mount();
      await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(2));

      expect(post).toHaveBeenCalledTimes(1);
    });
  });

  describe('그리드 — frontend/grid.md', () => {
    const columns: ColumnDef<Row>[] = [
      { field: 'id', header: 'x', type: 'text', width: 120 },
      { field: 'status', header: 'y', type: 'code', width: 160, grpCd: 'ORD_STATUS' },
    ];

    function Harness(props: { rows: Row[] }) {
      const controller = useGridController<Row>({ menuCd: 'TEST', columns });
      const { setRows } = controller;
      useEffect(() => setRows(toRows(props.rows)), [setRows, props.rows]);

      return (
        <App>
          <DataGrid<Row>
            title="t"
            menuCd="TEST"
            controller={controller}
            // 개인화를 읽지 않는다 — 이 테스트가 보는 것은 후보가 어디서 오는가 하나다.
            settings={false}
            height={400}
          />
        </App>
      );
    }

    it('컬럼에 그룹코드만 적으면 셀이 코드값이 아니라 이름을 보인다', async () => {
      render(<Harness rows={[{ id: 'a', status: 'PAID' }]} />);

      await waitFor(() =>
        expect(post).toHaveBeenCalledWith('/system/common/code/getList', { grpCds: ['ORD_STATUS'] }));

      await waitFor(() => expect(screen.queryByText('결제완료')).not.toBeNull(), { timeout: 5000 });
    });

    it('이름이 없는 코드는 여기서도 키 문자열이다', async () => {
      render(<Harness rows={[{ id: 'a', status: 'DONE' }]} />);

      await waitFor(() => expect(post).toHaveBeenCalled());

      // 후보가 온 <b>뒤</b>에 셀이 다시 그려진다 — 판을 띄운 채 기다리므로 여유를 준다.
      await waitFor(() => expect(screen.queryByText('code.ORD_STATUS.DONE')).not.toBeNull(), {
        timeout: 5000,
      });
    });
  });

  describe('CodeSelect — 컨트롤을 직접 놓는 자리', () => {
    it('그룹코드만 주면 이름이 붙은 후보를 낸다', async () => {
      render(<CodeSelect grpCd="ORD_STATUS" style={{ width: 200 }} />);

      await waitFor(() =>
        expect(post).toHaveBeenCalledWith('/system/common/code/getList', { grpCds: ['ORD_STATUS'] }));

      fireEvent.mouseDown(screen.getByRole('combobox'));

      await waitFor(() => expect(screen.queryByText('신규')).not.toBeNull());
    });

    it('고른 값은 코드값이다 — 이름이 아니다', async () => {
      const picked: unknown[] = [];
      render(<CodeSelect grpCd="ORD_STATUS" onChange={(value) => picked.push(value)} />);
      await waitFor(() => expect(post).toHaveBeenCalled());

      fireEvent.mouseDown(screen.getByRole('combobox'));
      await waitFor(() => expect(screen.queryByText('신규')).not.toBeNull());
      fireEvent.click(screen.getByText('신규'));

      expect(picked).toEqual(['NEW']);
    });
  });

  it('폼과 판이 같은 화면에 있어도 요청은 하나다 — sys/code.md 이 캐시를 둔 이유가 그 왕복이다', async () => {
    function Screen() {
      const controller = useGridController<Row>({
        menuCd: 'TEST',
        columns: [{ field: 'status', header: 'y', type: 'code', width: 160, grpCd: 'ORD_STATUS' }],
      });
      return (
        <App>
          <SearchForm
            fields={[{ field: 'status', label: 'x', type: 'select', grpCd: 'ORD_TYPE' }]}
            onSubmit={() => undefined}
          />
          <DataGrid<Row> title="t" menuCd="TEST" controller={controller} settings={false} height={300} />
        </App>
      );
    }
    post.mockResolvedValue({
      version: 1,
      groups: { ORD_STATUS: STATUS_CODES, ORD_TYPE: ['ONLINE'] },
    });

    render(<Screen />);

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledTimes(1);

    // 순서는 <b>어느 컴포넌트가 먼저 붙었나</b>이지 규약이 아니다 — 담긴 것만 본다.
    const [url, body] = post.mock.calls[0] as [string, { grpCds: string[] }];
    expect(url).toBe('/system/common/code/getList');
    expect([...body.grpCds].sort()).toEqual(['ORD_STATUS', 'ORD_TYPE']);
  });
});
