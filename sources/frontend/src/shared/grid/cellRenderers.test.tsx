import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import type { ReactElement } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CELL_ICONS, cellIcon } from '@/shared/grid/cellIcons';
import { toColDefs } from '@/shared/grid/columnDefs';
import type { GridRow } from '@/shared/grid/rows';
import type { ColumnDef } from '@/shared/grid/types';

interface Row {
  stat: string;
  rate: number;
}

/**
 * 렌더러 둘 — {@code progress} · {@code icon}(playbook/INDEX.md).
 *
 * <p>둘의 공통 규칙 하나가 이 파일의 축이다: <b>모르는 값에서 값을 잃지 않는다.</b>
 * 아이콘 맵에 없는 코드에 물음표를 그리거나 숫자가 아닌 값에 0% 막대를 그리면, 그 셀은
 * <b>값이 물음표</b>이고 <b>값이 0</b> 인 것처럼 읽힌다 — 진짜 값은 화면 어디에도 남지
 * 않고, 사용자는 자기가 잘못 읽었다는 것을 알 방법이 없다.
 *
 * <p>frontend/grid.md 의 <i>맵에 없는 값은 칠하지 않는다</i> 와 같은 규칙이며, 여기서는
 * <b>칠하지 않는 것</b>이 아니라 <b>글자로 떨어지는 것</b>이 그 표현이다.
 */
describe('progress · icon 렌더러', () => {
  const build = (column: ColumnDef<Row>): ColDef<GridRow<Row>> => {
    const defs = toColDefs<Row>({
      columns: [column],
      onEdit: () => undefined,
      onEditDenied: () => undefined,
      onInvalidEdit: () => undefined,
      menuCd: 'TEST',
      issues: [],
      serverSort: true,
      editable: false,
      showState: false,
      onRevert: () => undefined,
      multiSelect: false,
    });
    const def = defs.find((candidate) => candidate.colId === column.field);
    if (!def) throw new Error('컬럼이 없다');
    return def;
  };

  /** 판이 셀을 그리듯 렌더러를 부른다 — 값만 있으면 되는 렌더러들이다. */
  const draw = (def: ColDef<GridRow<Row>>, value: unknown) => {
    const renderer = def.cellRenderer as (
      params: ICellRendererParams<GridRow<Row>>,
    ) => ReactElement;
    render(renderer({ value } as ICellRendererParams<GridRow<Row>>));
  };

  const iconColumn: ColumnDef<Row> = {
    field: 'stat',
    header: 'h',
    type: 'code',
    renderer: 'icon',
    icons: { DONE: 'ok', FAIL: 'fail' },
    tone: { DONE: 'ok', FAIL: 'err' },
    options: [
      { value: 'DONE', text: '완료' },
      { value: 'FAIL', text: '실패' },
    ],
  };

  it('행의 실행 조건이 거짓이면 링크를 비활성화하고 클릭을 전달하지 않는다', () => {
    const clicked = vi.fn();
    const def = build({ field: 'stat', header: 'h', type: 'text', renderer: 'link', onClick: clicked, clickEnabled: (row) => row.stat === 'READY' });
    const renderer = def.cellRenderer as (params: ICellRendererParams<GridRow<Row>>) => ReactElement;
    const drawRow = (stat: string) => renderer({ value: '실행', data: { id: '1', state: null, original: null, data: { stat, rate: 0 } } } as ICellRendererParams<GridRow<Row>>);
    const view = render(drawRow('UNKNOWN'));
    expect(screen.getByText('실행').getAttribute('aria-disabled')).toBe('true');
    fireEvent.click(screen.getByText('실행'));
    expect(clicked).not.toHaveBeenCalled();
    view.rerender(drawRow('READY'));
    fireEvent.click(screen.getByText('실행'));
    expect(clicked).toHaveBeenCalledOnce();
  });

  it('아이콘 칸에는 읽는 이름이 붙는다 — 아이콘만 남으면 흑백 인쇄에서 읽히지 않는다', () => {
    draw(build(iconColumn), 'DONE');
    expect(screen.getByLabelText('완료')).toBeTruthy();
  });

  it('맵에 없는 값은 아이콘이 아니라 글자다 — 물음표를 그리면 진짜 값이 사라진다', () => {
    draw(build(iconColumn), 'XYZ');
    expect(screen.getByText('XYZ')).toBeTruthy();
    // 이름 붙은 아이콘 칸이 아니다 — 그 자리가 통째로 없다.
    expect(screen.queryByLabelText('XYZ')).toBeNull();
  });

  const progressColumn: ColumnDef<Row> = {
    field: 'rate',
    header: 'h',
    type: 'number',
    renderer: 'progress',
    tone: [
      { max: 49, tone: 'err' },
      { max: 99, tone: 'warn' },
      { max: 100, tone: 'ok' },
    ],
  };

  it('막대에 값이 글자로 함께 있다 — 43% 와 47% 를 막대만으로 가를 수 없다', () => {
    draw(build(progressColumn), 43);
    expect(screen.getByText('43%')).toBeTruthy();
  });

  it('숫자가 아니면 막대를 그리지 않는다 — 0% 막대는 *0 이다* 로 읽힌다', () => {
    draw(build(progressColumn), null);
    expect(screen.queryByText('0%')).toBeNull();
  });
});

/**
 * 아이콘 집합이 <b>닫혀 있다</b> — playbook/INDEX.md.
 *
 * <p>메뉴 아이콘 집합과 <b>갈라 둔 것</b>이 결정이며(2026-09-02), 그래서 여기서 지키는
 * 것은 <i>어떤 아이콘이 있는가</i> 가 아니라 <b>모르는 이름이 조용히 통과하지 않는다</b>
 * 하나다 — 타입이 컴파일에서 막지만, 시드나 서버 응답에서 온 문자열은 타입을 통과한다.
 */
describe('셀 아이콘 집합', () => {
  it('모르는 이름은 없는 것이다 — 폴백 아이콘을 두지 않는다', () => {
    expect(cellIcon('nope')).toBeUndefined();
    expect(cellIcon(null)).toBeUndefined();
    expect(cellIcon(undefined)).toBeUndefined();
  });

  it('아는 이름은 그린다', () => {
    expect(cellIcon('ok')).toBeTruthy();
  });

  /*
    <b>메뉴 아이콘과 이름이 겹치지 않아야 하는 것은 아니다</b> — 두 집합은 서로를 모른다.
    여기서 세는 것은 <i>집합이 비어 버리는 회귀</i> 하나이며, 그 사고는 임포트 정리 한 번에
    난다: 맵이 비면 모든 아이콘 컬럼이 <b>말없이 글자로</b> 떨어져 화면은 멀쩡해 보인다.
  */
  it('집합이 비어 있지 않다', () => {
    expect(Object.keys(CELL_ICONS).length).toBeGreaterThan(0);
  });
});
