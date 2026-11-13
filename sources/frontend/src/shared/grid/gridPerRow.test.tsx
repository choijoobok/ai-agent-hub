import { App } from 'antd';
import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { toRows } from '@/shared/grid/rows';
import { useGridController, type GridController } from '@/shared/grid/useGridController';
import type { ActionApi, ColumnDef, GridApi } from '@/shared/grid/types';
import { useAuthStore } from '@/shared/stores/authStore';
import { useMessageStore } from '@/shared/i18n/messageStore';

const post = vi.fn();

vi.mock('@/shared/api/request', () => ({
  post: (url: string, body?: unknown) => post(url, body) as Promise<unknown>,
  get: vi.fn(),
  request: vi.fn(),
}));

/**
 * <b>건별 처리의 결과</b>를 화면이 어떻게 받는가 — aidd-guide/standard/backend/row-transaction.md §7.
 *
 * <p><b>이 파일이 잠그는 것은 하나다: 일부만 된 일을 <i>됐다</i> 고 말하지 않는다.</b>
 * 서버는 200 으로 답하므로 배관이 아무것도 하지 않으면 <b>488건 저장 · 12건 실패</b>가
 * 평소의 <i>저장했습니다</i> 모달로 닫힌다 — 예외도 경고도 나지 않고, 사용자는 12건이
 * 안 됐다는 사실을 <b>영영 모른다</b>.
 *
 * <p><b>선언이 이긴다.</b> 응답 모양을 훔쳐보고 정하면 서버가 칸 하나를 바꾼 날 화면이
 * 조용히 다른 길로 간다 — {@code progress} 와 같은 규약이다.
 */
describe('건별 처리의 결과 — backend/row-transaction.md', () => {
  interface Row {
    ordNo: string;
  }

  const columns: ColumnDef<Row>[] = [{ field: 'ordNo', header: '주문번호', type: 'text' }];

  const DONE = '실행했습니다';
  const SAVED = '저장했습니다';
  const TITLE = '건별 처리 결과';
  const SUMMARY = '총 {total}건 · 성공 {done}건 · 실패 {failed}건';

  /** 서버가 200 으로 돌려주는 것 — {@code RowReport} 다. */
  const report = (total: number, failures: number[]) => ({
    total,
    done: total - failures.length,
    failed: failures.length,
    rows: failures.map((index) => ({
      index,
      fields: [{ field: 'ordNo', message: '접수 상태가 아닙니다', value: `SO-${index}` }],
    })),
  });

  beforeEach(() => {
    post.mockReset();
    useAuthStore.setState({ permissions: ['TEST:READ', 'TEST:SAVE', 'TEST:EXECUTE'] });
    useMessageStore.setState({
      status: 'ready',
      messages: {
        'grid.action.done': DONE,
        'grid.save.done': SAVED,
        'grid.partial.title': TITLE,
        'grid.partial.summary': SUMMARY,
        'grid.issue.column': '컬럼',
        'grid.issue.value': '값',
        'grid.issue.reason': '사유',
        'grid.confirm.ok': '확인',
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * 확인 모달을 거치지 않는다({@code confirm: false}) — 이 파일이 보는 것은 <b>응답을
   * 받은 뒤</b>이고, 확인 흐름은 다른 자리가 잠근다.
   */
  const action = (extra: Partial<ActionApi<Row>>): ActionApi<Row> => ({
    key: 'shipEach',
    label: 'ship',
    url: '/service/x/shipEach',
    // 행과 무관한 대상으로 둔다 — 선택 상태를 만들지 않고 실행 하나만 본다.
    target: 'none',
    confirm: false,
    reloadAfter: false,
    ...extra,
  });

  let controller: GridController<Row> | null = null;

  /*
    <b>컨트롤러가 {@code App} 안이어야 한다.</b> 모달은 {@code App.useApp()} 이 주는 것이고,
    프로바이더 밖에서 부르면 정적 폴백이 와서 <b>아무것도 DOM 에 뜨지 않는다</b> — 그러면
    이 파일의 단언이 전부 조용히 무의미해진다.
  */
  function Inner(props: { api: GridApi<Row>; saves?: boolean }) {
    controller = useGridController<Row>({
      menuCd: 'TEST',
      columns,
      api: props.api,
      // 저장은 배열을 만들 줄 알아야 나간다 — 없으면 save() 가 조용히 돌아선다.
      toSaveRow: props.saves === true ? (row) => row.data : undefined,
    });
    return null;
  }

  function Harness(props: { api: GridApi<Row>; saves?: boolean }) {
    return (
      <App>
        <Inner api={props.api} saves={props.saves} />
      </App>
    );
  }

  /*
    <b>실행을 기다리지 않는다.</b> 결과 모달도 성공 모달도 <i>확인</i> 을 누를 때까지
    끝나지 않으므로, 여기서 {@code await} 하면 영영 돌아오지 않고 시험이 시간 초과로
    죽는다 — 그때 죽는 것은 그 시험 하나가 아니라 뒤따르는 것들도 함께다.
  */
  const run = async (api: GridApi<Row>) => {
    render(<Harness api={api} />);
    act(() => {
      void controller?.runAction('shipEach');
    });
    await act(async () => {
      await Promise.resolve();
    });
  };

  it('실패가 있으면 결과 표가 성공 모달을 대신한다', async () => {
    post.mockResolvedValue(report(500, [3, 17]));

    await run({ actions: [action({ perRow: { after: 'reload' } })] });

    // 판의 확인 모달은 제목을 두 자리에 그린다(읽어 주는 것과 보이는 것).
    await waitFor(() => expect(screen.getAllByText(TITLE).length).toBeGreaterThan(0));
    expect(screen.getByText('총 500건 · 성공 498건 · 실패 2건')).toBeTruthy();
    // 값이 다르면 접히지 않는다 — 실패 둘이 두 줄이다(saveIssues.ts 의 접기 규칙).
    expect(screen.getAllByText('접수 상태가 아닙니다')).toHaveLength(2);
    expect(screen.getByText('SO-17')).toBeTruthy();
    // 일부만 된 일을 "실행했습니다" 로 닫으면 화면이 거짓말한 것이 된다.
    expect(screen.queryByText(DONE)).toBeNull();
  });

  it('전건이 성공하면 평소의 성공 모달이다 — 결과 표를 띄우지 않는다', async () => {
    post.mockResolvedValue(report(500, []));

    await run({ actions: [action({ perRow: { after: 'reload' } })] });

    await waitFor(() => expect(screen.getByText(DONE)).toBeTruthy());
    expect(screen.queryAllByText(TITLE)).toHaveLength(0);
  });

  /** 전건 실패도 <b>같은 200</b> 이라 같은 표로 온다 — 화면이 할 일이 같기 때문이다. */
  it('전건이 실패해도 같은 결과 표다', async () => {
    post.mockResolvedValue(report(2, [0, 1]));

    await run({ actions: [action({ perRow: { after: 'reload' } })] });

    await waitFor(() => expect(screen.getByText('총 2건 · 성공 0건 · 실패 2건')).toBeTruthy());
  });

  /**
   * <b>선언하지 않았으면 보지 않는다.</b> 모양만 보고 갈라지면 서버가 우연히 비슷한 칸을
   * 돌려준 날 화면이 <b>아무도 시키지 않은 모달</b>을 띄운다.
   */
  it('perRow 를 선언하지 않으면 리포트 모양이 와도 성공 모달이다', async () => {
    post.mockResolvedValue(report(500, [3]));

    await run({ actions: [action({})] });

    await waitFor(() => expect(screen.getByText(DONE)).toBeTruthy());
    expect(screen.queryAllByText(TITLE)).toHaveLength(0);
  });

  /**
   * <b>어긋난 것은 응답이지 사용자의 입력이 아니다.</b> 빈 표를 띄우면 사용자는 자기가
   * 무언가 틀린 줄 알고, 고칠 것을 찾다 못 찾는다.
   */
  it('선언과 응답이 어긋나면 개발자에게 말하고 평소대로 닫는다', async () => {
    const complain = vi.spyOn(console, 'error').mockImplementation(() => {});
    post.mockResolvedValue({ ok: true });

    await run({ actions: [action({ perRow: { after: 'reload' } })] });

    await waitFor(() => expect(screen.getByText(DONE)).toBeTruthy());
    expect(complain).toHaveBeenCalledTimes(1);
  });

  /**
   * <b>저장과 실행이 같은 길을 쓴다.</b> 사용자에게 둘은 구분되지 않는 사건이며(<i>일부는
   * 됐고 일부는 안 됐다</i>), 표시가 갈리면 같은 일이 두 얼굴로 보인다.
   */
  const saveOneEdit = async () => {
    act(() => {
      controller?.setRows(toRows([{ ordNo: 'A' }, { ordNo: 'B' }, { ordNo: 'C' }]));
    });
    act(() => {
      if (controller) controller.edit(controller.rows[1].id, { ordNo: 'B2' });
    });
    act(() => {
      void controller?.save();
    });
    await act(async () => {
      await Promise.resolve();
    });
  };

  it('저장도 같은 결과 표를 쓴다', async () => {
    post.mockResolvedValue(report(3, [1]));

    render(
      <Harness
        api={{
          save: {
            url: '/service/x/save',
            confirm: false,
            reloadAfter: false,
            perRow: { after: 'reload' },
          },
        }}
        saves
      />,
    );
    await saveOneEdit();

    await waitFor(() => expect(screen.getByText('총 3건 · 성공 2건 · 실패 1건')).toBeTruthy());
  });

  /**
   * <b>저장도 전건 성공이면 평소대로 닫는다.</b> 실행 쪽과 갈림이 하나뿐인 코드라 함께
   * 두기 쉬운데, <b>둘 중 하나만 잠그면 나머지 하나가 조용히 뒤집힌다</b> — 이 시험이
   * 없을 때 실제로 <i>실패 0건인 저장에도 결과 표를 띄운다</i> 는 변형이 살아남았다.
   */
  it('저장이 전건 성공하면 결과 표를 띄우지 않는다', async () => {
    post.mockResolvedValue(report(3, []));

    render(
      <Harness
        api={{ save: { url: '/service/x/save', confirm: false, reloadAfter: false, perRow: { after: 'reload' } } }}
        saves
      />,
    );
    await saveOneEdit();

    await waitFor(() => expect(screen.getByText(SAVED)).toBeTruthy());
    expect(screen.queryAllByText(TITLE)).toHaveLength(0);
  });

  it('TC-00611 소유 전송은 공통 전송을 중복 호출하지 않고 실패 행을 유지한다', async () => {
    const submit = vi.fn().mockResolvedValue(report(1, [0]));
    render(<Harness api={{ save: { url: '/service/x/save', submit, confirm: false, reloadAfter: false, perRow: { after: 'keepFailed' } } }} saves />);
    await saveOneEdit();
    await waitFor(() => expect(screen.getByText('총 1건 · 성공 0건 · 실패 1건')).toBeTruthy());
    expect(submit).toHaveBeenCalledTimes(1);
    expect(post).not.toHaveBeenCalled();
    expect(controller?.rows).toHaveLength(1);
    expect(controller?.rows[0].data.ordNo).toBe('B2');
  });
});
