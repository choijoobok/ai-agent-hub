import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExcelButton } from '@/shared/grid/excel/ExcelButton';
import { useMessageStore } from '@/shared/i18n/messageStore';
import type { ExcelColumn } from '@/shared/grid/excel/workbook';
import type { ExcelSpec } from '@/shared/grid/excel/types';
import type { ColumnDef } from '@/shared/grid/types';

const postDownload = vi.hoisted(() => vi.fn());
const downloadExcel = vi.hoisted(() => vi.fn());
const saveBlob = vi.hoisted(() => vi.fn());

vi.mock('@/shared/api/download', () => ({ postDownload }));
// 시각은 고정한다 — 이 파일이 보는 것은 <b>이름의 모양</b>이지 지금 몇 시인가가 아니다.
vi.mock('@/shared/grid/excel/workbook', () => ({
  downloadExcel,
  saveBlob,
  stamp: () => '20260906_1200',
}));

interface Row {
  ordNo: string;
}

/**
 * ②가 <b>세 갈래를 덮는가</b> — playbook/excel-download.md · frontend/grid.md.
 *
 * <p>ADR 은 갈래를 셋으로 갈랐고 frontend/packages.md 이 <i>"하나의 인터페이스로 덮는다"</i> 고 적었는데
 * <b>구현이 두 개에 박혀 있었다</b>. 그래서 여기서 세는 것은 <i>드롭다운이 열리는가</i> 가
 * 아니라 <b>같은 이름의 항목이 선언에 따라 다른 경로로 가는가</b> 다.
 *
 * <p>그리고 <b>이력을 두 번 남기지 않는가</b> — 서버 경로는 요청 자체가 기록이므로
 * (사용 이력 인터셉터) 프론트가 또 부르면 <b>한 번 내려받은 것이 두 행</b>이 된다.
 */
describe('② 엑셀 다운로드 — 세 갈래', () => {
  const column: ColumnDef<Row> = { field: 'ordNo', header: 'h', type: 'text' };
  const columns = (): ExcelColumn<Row>[] => [{ column, header: '주문번호' }];
  const rows: Row[] = [{ ordNo: 'A-1' }];

  beforeEach(() => {
    vi.clearAllMocks();
    postDownload.mockResolvedValue({ blob: new Blob(['x']), fileName: '주문합본.xlsx' });
    useMessageStore.setState({
      status: 'ready',
      messages: {
        'grid.excel.page': '현재 페이지',
        'grid.excel.all': '전체',
        'smpl.excel.joined': '주문+상품 합본',
      },
    });
  });

  const draw = (spec?: ExcelSpec, permissions: string[] = ['SMPL:EXPORT']) =>
    render(
      <App>
        <ExcelButton<Row>
          fileName="주문"
          columns={columns}
          currentPage={() => rows}
          all={() => Promise.resolve(rows)}
          spec={spec}
          permissions={permissions}
          menuCd="SMPL"
        />
      </App>,
    );

  const open = async () => {
    fireEvent.click(screen.getByRole('button'));
    await screen.findByText('현재 페이지');
  };

  const click = async (text: string) => {
    await open();
    fireEvent.click(screen.getByText(text));
  };

  it('선언이 없으면 프론트가 만든다 — 지금까지와 같다', async () => {
    draw();

    await click('전체');

    await waitFor(() => expect(downloadExcel).toHaveBeenCalledTimes(1));
    expect(postDownload).not.toHaveBeenCalled();
  });

  /*
    <b>이름은 그대로 두고 경로만 돌린다</b>(playbook/excel-download.md). 상단-하단을 조인해 한 장으로 내리는
    화면에서는 <i>전체</i> 도 서버가 조인해야 나오며, 사용자는 <b>어느 경로인지 알 필요가
    없다</b>.
  */
  it('전체를 서버로 돌려도 항목 이름은 그대로다', async () => {
    draw({ all: { url: '/sample/service/orderExcel', body: () => ({ ordNo: 'A-1' }) } });

    await click('전체');

    await waitFor(() =>
      expect(postDownload).toHaveBeenCalledWith('/sample/service/orderExcel', { ordNo: 'A-1' }));
    expect(downloadExcel).not.toHaveBeenCalled();
  });

  /*
    <b>이것이 두 번 세지면 안 된다.</b> 서버 생성은 요청이 도착하는 것 자체가 기록이므로
    (playbook/excel-download.md) 프론트 이력을 또 부르면 한 번 내려받은 것이 <b>두 행</b>이 된다 — 이력은
    유출 조사에서 유일한 단서가 될 수 있고, 그때 건수가 배로 보인다.
  */
  it('서버 경로는 프론트 이력을 부르지 않는다 — 요청 자체가 기록이다', async () => {
    draw({ all: { url: '/sample/service/orderExcel' } });

    await click('전체');

    await waitFor(() => expect(saveBlob).toHaveBeenCalled());
    // downloadExcel 안에서만 recordDownload 가 불린다 — 그것이 불리지 않았다는 것이 곧 답이다.
    expect(downloadExcel).not.toHaveBeenCalled();
  });

  it('서버가 지은 이름이 이긴다 — 그리드 제목은 그 파일의 내용이 아니다', async () => {
    draw({ all: { url: '/sample/service/orderExcel' } });

    await click('전체');

    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(expect.anything(), '주문합본.xlsx'));
  });

  it('서버가 이름을 말하지 않으면 우리 규칙으로 떨어진다', async () => {
    postDownload.mockResolvedValue({ blob: new Blob(['x']), fileName: null });
    draw({ all: { url: '/sample/service/orderExcel' } });

    await click('전체');

    await waitFor(() =>
      expect(saveBlob).toHaveBeenCalledWith(expect.anything(), expect.stringMatching(/^주문_\d{8}_\d{4}\.xlsx$/)));
  });

  describe('화면 고유 항목 — extra', () => {
    const spec: ExcelSpec = {
      extra: [
        { key: 'joined', label: 'smpl.excel.joined', server: { url: '/sample/service/joinedExcel' } },
      ],
    };

    it('구분선 아래에 서고 눌리면 그 URL 로 간다', async () => {
      draw(spec);

      await click('주문+상품 합본');

      await waitFor(() =>
        expect(postDownload).toHaveBeenCalledWith('/sample/service/joinedExcel', undefined));
    });

    /*
      <b>권한이 없으면 아예 없다</b>(frontend/tabs.md). 비활성으로 남기면 <i>왜 안 되나</i> 를 묻게
      되고, 그 답은 화면 어디에도 없다.
    */
    it('권한이 없으면 항목이 아예 없다', async () => {
      draw({
        extra: [{
          key: 'joined',
          label: 'smpl.excel.joined',
          actionCd: 'EXECUTE',
          server: { url: '/sample/service/joinedExcel' },
        }],
      }, ['SMPL:EXPORT']);

      await open();

      expect(screen.queryByText('주문+상품 합본')).toBeNull();
    });
  });
});
