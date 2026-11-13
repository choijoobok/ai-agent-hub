import { useState } from 'react';
import { FileExcelOutlined } from '@ant-design/icons';
import { App, Button, Dropdown, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { postDownload } from '@/shared/api';
import { hasPermission } from '@/shared/authz/permission';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { downloadExcel, saveBlob, stamp, type ExcelColumn } from '@/shared/grid/excel/workbook';
import type { ExcelScope } from '@/shared/grid/excel/downloadLog';
import type { ExcelServer, ExcelSpec } from '@/shared/grid/excel/types';

/**
 * ② 엑셀 다운로드 — frontend/grid.md · playbook/excel-download.md.
 *
 * <p><b>아이콘만 둔다.</b> 항목이 여럿이라 어느 라벨도 대표하지 못하고, ①이 길어지면 도구가
 * 밀린다 — 라벨은 드롭다운을 열면 나온다.
 *
 * <h2>세 갈래가 <b>여기 하나로</b> 덮인다</h2>
 *
 * <p>playbook/excel-download.md 는 갈래를 셋으로 갈랐고 frontend/packages.md 이 <i>"{@code <ExcelDownloadButton>} 이 세 경로를
 * 하나의 인터페이스로 덮는다"</i> 고 적었다. <b>덮이지 않고 있었다</b> — 두 항목이 프론트
 * 생성에 박혀 있었다. {@code spec} 이 그 자리다.
 *
 * <table>
 *   <tr><th>선언</th><th>경로</th></tr>
 *   <tr><td>없음</td><td>프론트 생성 — 지금까지와 같다</td></tr>
 *   <tr><td>{@code spec.page}·{@code spec.all}</td>
 *       <td><b>이름은 그대로, 경로만 서버</b>. 사용자는 어느 쪽인지 알 필요가 없다</td></tr>
 *   <tr><td>{@code spec.extra}</td><td>구분선 아래에 화면 고유 항목</td></tr>
 * </table>
 *
 * <h2>다운로드 이력을 <b>서버 경로에서는 부르지 않는다</b></h2>
 *
 * <p>{@code POST /api/system/common/excel/saveLog} 는 <i>호출이 없는 경로</i>의 구멍을 메우는 것이다
 * (playbook/excel-download.md). 서버 생성은 <b>요청이 도착하는 것 자체가 기록</b>이므로(사용 이력 인터셉터가
 * 모든 {@code /api/**} 를 남긴다) 여기서 또 부르면 <b>한 번 내려받은 것이 두 행</b>이 되고,
 * <i>몇 번 내려받았나</i> 가 두 배로 세진다.
 *
 * <p>프론트 생성 쪽에서 이력을 부르는 자리는 여전히 <b>이 버튼이 아니라</b>
 * {@code workbook.ts} 다 — 파일이 나가는 자리가 곧 기록이 나가는 자리다.
 */
interface Props<T> {
  /** 파일 이름의 앞부분 — 그리드 제목이다. 서버 경로에서는 서버 이름이 이긴다. */
  fileName: string;
  columns: () => ExcelColumn<T>[];
  /** 지금 화면에 보이는 행 — 필터·정렬을 통과한 것들이다. */
  currentPage: () => T[];
  /** 조회조건 그대로 전량. 거절·실패면 `null` 이며 이유는 그쪽이 이미 말했다. */
  all: () => Promise<T[] | null>;
  /** 세 번째 갈래의 선언 — 없으면 지금까지와 같다. */
  spec?: ExcelSpec;
  /** {@code extra} 의 권한을 묻는 쌍(frontend/tabs.md). */
  permissions: readonly string[];
  menuCd: string;
  disabled?: boolean;
}

export function ExcelButton<T>(props: Props<T>) {
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);
  const spec = props.spec;

  const front = async (rows: T[] | null, scope: ExcelScope) => {
    if (rows === null) return;
    if (rows.length === 0) {
      // 빈 파일을 만들지 않는다 — 열어 보고 나서야 아는 것이 가장 나쁘다.
      // 이력도 남지 않는다: 내려받은 것이 없다.
      message.info(label(MSG.GRID_EXCEL_EMPTY));
      return;
    }
    await downloadExcel(props.fileName, props.columns(), rows, stamp(), scope);
  };

  /**
   * 서버가 만든 파일을 받는다.
   *
   * <p><b>이름은 서버가 정한다.</b> 이 갈래의 파일은 그리드의 표가 아니므로(헤더 블록 +
   * 상품 표, 다중 시트) 그리드 제목으로 이름을 지으면 <b>내용과 이름이 어긋난다</b> —
   * 서버가 말하지 않을 때만 우리 규칙으로 떨어진다.
   */
  const server = async (source: ExcelServer) => {
    const file = await postDownload(source.url, source.body?.());
    saveBlob(file.blob, file.fileName ?? `${props.fileName}_${stamp()}.xlsx`);
  };

  const extras = (spec?.extra ?? []).filter((item) =>
    hasPermission(props.permissions, props.menuCd, item.actionCd ?? 'EXPORT'));

  const items: MenuProps['items'] = [
    { key: 'page', label: label(MSG.GRID_EXCEL_PAGE) },
    ...(spec?.pageOnly ? [] : [{ key: 'all', label: label(MSG.GRID_EXCEL_ALL) }]),
    // 화면 고유 항목은 <b>구분선 아래</b>다 — 섞이면 어느 것이 이 화면만의 것인지 모른다.
    ...(extras.length > 0 ? [{ type: 'divider' as const }] : []),
    ...extras.map((item) => ({ key: item.key, label: label(item.label) })),
  ];

  const run = async (key: string): Promise<void> => {
    if (key === 'page') {
      return spec?.page ? server(spec.page) : front(props.currentPage(), 'PAGE');
    }
    if (key === 'all') {
      return spec?.all ? server(spec.all) : front(await props.all(), 'ALL');
    }
    const extra = extras.find((item) => item.key === key);
    // 목록에 없는 키는 올 수 없다 — 항목을 우리가 만들었다.
    return extra ? server(extra.server) : undefined;
  };

  const choose: MenuProps['onClick'] = (info) => {
    setBusy(true);
    void run(info.key)
      .catch((error: unknown) =>
        message.error(error instanceof Error ? error.message : String(error)))
      .finally(() => setBusy(false));
  };

  return (
    <Dropdown trigger={['click']} disabled={props.disabled || busy} menu={{ items, onClick: choose }}>
      {/*
        <b>툴팁을 단다</b>(2026-08-30 — 되돌렸다). 한 번 뺐던 이유는 <i>"누르면 항목
        이름이 바로 나온다"</i> 였는데, 그 항목이 <b>현재 페이지 · 전체</b> 라 <b>무엇의</b>
        현재 페이지인지 말하지 않는다 — 열어 봐도 이것이 엑셀이라는 말은 어디에도 없다.
        아이콘만 있는 버튼에서 그것을 말하는 자리는 툴팁뿐이다.
      */}
      <Tooltip title={label(MSG.GRID_EXCEL)}>
        {/*
          <b>{@code disabled} 를 버튼이 직접 받는다</b>(2026-08-31 실물). 드롭다운은 자식에게
          그것을 전해 주지만({@code child.props.disabled ?? disabled}) <b>그 자식이 툴팁</b>
          이라 버튼에는 닿지 않았다 — 눌리지는 않는데 아이콘만 <b>활성 상태의 색</b>으로
          남아, 하단 탭이 잠겼을 때(screen/admin/system/user/requirements.md) 이 버튼 하나만 켜져 보였다.
        */}
        <Button
          icon={<FileExcelOutlined />}
          loading={busy}
          disabled={props.disabled}
          aria-label={label(MSG.GRID_EXCEL)}
        />
      </Tooltip>
    </Dropdown>
  );
}
