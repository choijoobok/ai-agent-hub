import type {
  ColDef,
  ICellRendererParams,
  ValueGetterParams,
  ValueSetterParams,
} from 'ag-grid-community';
import { DeleteOutlined, EditOutlined, PlusOutlined, UndoOutlined } from '@ant-design/icons';
import { Checkbox, Switch, Tag, Tooltip, Typography } from 'antd';
import type { CheckboxChangeEvent } from 'antd';
import { SelectAllHeader } from '@/shared/grid/SelectAllHeader';
import { planSelectRange, type SelectAnchor } from '@/shared/grid/selectRange';
import { cellIcon } from '@/shared/grid/cellIcons';
import { PopupCellEditor, type PopupCellEditorParams } from '@/shared/grid/PopupCellEditor';
import {
  SearchSelectCellEditor,
  type SearchSelectCellEditorParams,
} from '@/shared/grid/SearchSelectCellEditor';
import {
  ListModalCellEditor,
  type ListModalCellEditorParams,
} from '@/shared/grid/ListModalCellEditor';
import {
  TextModalCellEditor,
  type TextModalCellEditorParams,
} from '@/shared/grid/TextModalCellEditor';
import { IconCell, ProgressCell } from '@/shared/grid/rendererCells';
import { SetFilter } from '@/shared/grid/filter/SetFilter';
import { DateFilter } from '@/shared/grid/filter/DateFilter';
import { DateCellEditor } from '@/shared/grid/DateCellEditor';
import { dateText, precisionOf } from '@/shared/grid/dateFormat';
import { headerMinWidth } from '@/shared/grid/headerWidth';
import { cellPatch, cellValue, headerText } from '@/shared/grid/cell';
import { editableOf, marksRequired, requiredOf } from '@/shared/grid/columnRules';
import { validateCell } from '@/shared/grid/validate';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import type { GridRow } from '@/shared/grid/rows';
import type {
  ColumnDef,
  DeleteColumnSpec,
  EditableResult,
  GridIssue,
  Tone,
} from '@/shared/grid/types';

/**
 * 우리 어휘 → AG Grid — frontend/grid.md 의 <b>어댑터</b>다.
 *
 * <p>화면은 {@link ColumnDef} 만 쓰고 이 파일만 AG Grid 를 안다. 판을 바꾸면 여기가 바뀌고
 * 화면은 그대로다 — <b>반대로 화면이 {@code ColDef} 를 직접 만들기 시작하면 그 경계가
 * 무너진다.</b>
 *
 * <p><b>행 객체가 {@link GridRow} 다.</b> AG Grid 에 넘기는 것은 업무 데이터가 아니라 상태를
 * 두른 봉투이며, 그래서 {@code valueGetter} 가 한 겹 들어간다. 상태를 별도 맵으로 들면
 * 정렬·필터가 행을 재배치할 때 <b>상태와 행이 어긋난다.</b>
 */

const TONE_COLOR: Record<Tone, string | undefined> = {
  none: undefined,
  ok: 'success',
  warn: 'warning',
  err: 'error',
  muted: 'default',
};

/**
 * `tags` 셀이 <b>글자로 그리는 항목 수</b> — 나머지는 `+N` 이다(playbook/INDEX.md).
 *
 * <p><b>폭에서 계산하지 않는다.</b> 태그 하나의 폭은 글자 수에 따라 갈리고 컬럼 폭은
 * 사용자가 바꾸므로, 재려면 셀마다 실측해야 한다 — 렌더 함수를 여는 일이며 frontend/grid.md 가
 * 닫아 둔 문이다. 셋은 <b>좁은 컬럼에서도 잘리지 않는</b> 수이고, 넘친 것은 사라지는
 * 대신 <b>수와 툴팁</b>으로 남는다.
 */
const TAG_LIMIT = 3;

/**
 * ms → <b>사람이 읽는 시간</b> — screen/admin/system/batch/schedule/requirements.md 의 미결을 닫는다.
 *
 * <p><b>형식을 소스가 조립하지 않는다.</b> 그 미결이 <i>"`1시간 2분 5초` 인지 `01:02:05`
 * 인지, <b>다국어에서 갈린다</b>"</i> 였고, 갈리는 것이 맞으므로 <b>형식 자체가
 * 메시지</b>다 — 여기서 문자열을 잇기 시작하면 언어가 늘 때마다 이 함수를 고쳐야 한다.
 *
 * <p><b>0 인 단위는 템플릿을 갈아 없앤다</b>(hms → ms → s). `{h}=0` 을 그대로 그리면
 * *"0시간 3분"* 이 된다.
 *
 * <p><b>1초 미만은 별도 문구</b>다. *"0초"* 라고 쓰면 <i>안 걸린 것</i>처럼 읽히는데,
 * `SKIP_*` 이력이 실제로 0ms 라(sys/batch.md) 둘이 같은 글자가 되면 안 된다.
 *
 * <p><b>값이 없으면 빈 칸</b>이다 — 진행 중인 행의 `ELAPSED_MS` 가 `null` 이며(screen/admin/system/batch/schedule/requirements.md)
 * 거기에 `0초` 를 그리면 <i>0ms 만에 끝났다</i> 로 읽힌다.
 */
function durationText(value: unknown): string {
  if (value === null || value === undefined || value === '') return '';
  const ms = Number(value);
  if (!Number.isFinite(ms) || ms < 0) return '';
  if (ms < 1000) return label(MSG.GRID_DURATION_SUB);

  const total = Math.floor(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;

  if (h > 0) return label(MSG.GRID_DURATION_HMS, { h, m, s });
  if (m > 0) return label(MSG.GRID_DURATION_MS, { m, s });
  return label(MSG.GRID_DURATION_S, { s });
}

/**
 * 값 <b>뒤에</b> 붙는 표시 — screen/admin/system/code/requirements.md · screen/admin/system/batch/schedule/requirements.md.
 *
 * <p><b>값이 아니라 값의 내력이다</b> — 그래서 복사에서 빠진다(frontend/grid.md · `copyCell` 이
 * `bo-cell-aside` 를 걷어낸다). {@code renderer: 'badge'} 의 <i>본</i> 태그에는 이 클래스가
 * 없다: 거기서는 <b>배지가 곧 값</b>이라 복사되는 것이 맞다.
 *
 * <p>{@code tip} 은 <b>왜 붙었는지</b>다. `유령` 세 글자만으로는 무엇을 하면 되는지가
 * 읽히지 않으며, screen/admin/system/log/access/requirements.md 이 <i>꺼진 것에는 이유를 붙인다</i> 로 적은 규칙과 같은 축이다.
 *
 * <p><b>컴포넌트가 아니라 함수다</b>(이름이 소문자인 이유). 이 파일은 어댑터라 컴포넌트를
 * 내보내지 않으며, 여기에 컴포넌트를 하나 두면 `react-refresh` 가 <i>파일이 컴포넌트만
 * 내보내야 한다</i> 고 경고한다 — 셀 렌더러 안에서만 쓰이는 조각이라 함수가 맞다.
 */
function asideTag(mark: { text: string; tone: Tone; tip?: string }) {
  const tag = (
    <Tag className="bo-cell-aside" color={TONE_COLOR[mark.tone]} style={{ marginInlineStart: 6 }}>
      {mark.text}
    </Tag>
  );
  return mark.tip ? <Tooltip title={mark.tip}>{tag}</Tooltip> : tag;
}

interface AdapterOptions<T> {
  columns: ColumnDef<T>[];
  /** 편집 결과를 행에 반영한다 — 상태 전이는 {@code rows.ts} 가 한다. */
  onEdit: (rowId: string, patch: Partial<T>) => void;
  /** 편집 거부 메시지 — frontend/grid.md. 토스트로 낸다. */
  onEditDenied: (message: string) => void;
  /**
   * 방금 고친 칸이 <b>규칙을 어겼다</b> — frontend/grid.md ④. 〃 통로다.
   *
   * <p>{@link onEditDenied} 와 <b>축이 다르다</b>: 저것은 <i>고칠 수 없다</i> 이고 이것은
   * <b>고쳤는데 그대로 저장되지 않는다</b> 다. 값은 행에 들어가고 편집도 막지 않는다.
   */
  onInvalidEdit: (message: string) => void;
  /** 삭제 컬럼의 아이콘. 없으면 컬럼 자체가 나오지 않는다 — frontend/grid.md. */
  onToggleDelete?: (rowId: string) => void;
  /** 삭제 컬럼의 라벨과 게이트 — screen/admin/system/code/requirements.md. 없으면 <i>삭제</i> 이고 전 행이 열린다. */
  deleteColumn?: DeleteColumnSpec<T>;
  /** 상태 컬럼의 되돌리기 아이콘 — 수정한 행에만 나온다(frontend/grid.md). */
  onRevert: (rowId: string) => void;
  /** 검증 실패 표시 — frontend/grid.md. */
  issues: GridIssue[];
  /**
   * 이 그리드가 매인 메뉴 — <b>참조 팝업의 인가가 여기에 매인다</b>(backend/api.md · playbook/INDEX.md).
   *
   * <p>팝업은 자기 메뉴가 없다. 조회 엔드포인트가 <b>이 화면의 폴더</b>에 있고 시드가
   * 이 화면의 메뉴에 매핑하므로, 넘기지 않으면 그 조회는 <b>어느 메뉴로도 인가되지
   * 않아</b> 403 이 된다.
   */
  menuCd: string;
  /** 서버 정렬이면 그리드 자체 정렬을 끈다 — frontend/grid.md. */
  serverSort: boolean;
  /** 셀이 열리는가. */
  editable: boolean;
  /**
   * 상태 컬럼을 그리는가 — <b>편집이 열리는 그리드면 그린다</b>(frontend/grid.md).
   *
   * <p>저장 단위인가와 다른 축이다. screen/admin/system/code/requirements.md 의 하단처럼 <b>남의 저장에 실리는 그리드</b>도
   * 고친 행이 무엇인지 보여야 하고, 되돌릴 수도 있어야 한다 — 표시가 마스터에만 있으면
   * <i>"어느 언어를 고쳤더라"</i> 를 하단에서 알 수 없다(2026-08-30).
   */
  showState: boolean;
  /** 다중 선택이면 번호 <b>오른쪽</b>에 체크박스 컬럼이 붙는다(frontend/grid.md). */
  multiSelect: boolean;
  /**
   * 헤더 글자를 재는 데 쓰는 <b>CSS 글꼴 문자열</b> — `headerWidth` 가 이것으로 잰다.
   *
   * <p>글꼴과 크기가 사용자 설정이라(frontend/tabs.md) 상수로 둘 수 없다. 주지 않으면 재지 않으며,
   * 그때는 선언한 폭이 그대로다 — 폭이 <b>측정 실패로 조용히 0 이 되는 일</b>은 없다.
   */
  headerFont?: string;
}

/** 체크박스 컬럼의 id — 선택이 바뀔 때 이 컬럼만 다시 그린다. */
export const SELECT_COL_ID = '__select';

/** 행 번호 컬럼의 id — 페이지가 바뀔 때 이 컬럼만 다시 그린다. */
export const ROW_NO_COL_ID = '__rowNo';

/**
 * 판에 실어 보내는 값 — {@code valueGetter} 가 여기서 offset 을 읽는다.
 *
 * <p><b>컬럼 선언에 offset 을 박지 않는다.</b> 박았더니 <b>페이지 번호가 한 장씩 늦었다</b>
 * (2026-08-30 실물) — 새 행과 새 컬럼 선언이 같은 렌더에 함께 들어가는데 판은 <b>행부터</b>
 * 그리고, 그때 쓰는 선언은 아직 <b>앞 페이지의 것</b>이다. 2쪽이 1부터, 3쪽이 21부터
 * 시작하는 모양이 그것이다.
 */
export interface GridContext {
  rowOffset: number;
  /**
   * `Shift` 범위 선택의 앵커 — <b>그리드 하나에 하나</b>이며 `DataGrid` 가 들고 있다.
   *
   * <p>{@code rowOffset} 과 같은 이유로 문맥에 있다: 컬럼 선언은 폭·코드 후보·검증
   * 결과가 바뀔 때마다 다시 만들어지므로, 여기 아닌 곳에 두면 <b>사용자가 방금 누른
   * 자리를 잊는다</b>.
   */
  selectAnchor: SelectAnchor;
}

/**
 * 스위치의 폭 — 판의 {@code small}(28px)의 두 배다(frontend/grid.md).
 *
 * <p><b>셀과 필터 목록이 같은 값을 쓴다.</b> 필터 항목이 곧 그 스위치이므로(frontend/grid.md)
 * 둘의 크기가 다르면 <b>같은 것을 다른 것으로</b> 보게 된다.
 */
export const SWITCH_WIDTH = 56;

export function toColDefs<T>(options: AdapterOptions<T>): ColDef<GridRow<T>>[] {
  const { columns, showState, multiSelect } = options;
  const defs: ColDef<GridRow<T>>[] = [];

  // 순서가 결정이다 — 번호 → 체크박스 → 상태(frontend/grid.md). 번호가 맨 왼쪽인 이유는 그것이
  // **행을 부르는 이름**이기 때문이다: 옆 사람에게 "3번 행" 이라고 말할 수 있어야 한다.
  defs.push(rowNumberColumn<T>());
  if (multiSelect) {
    defs.push(selectionColumn<T>());
  }
  // 상태 컬럼 — frontend/grid.md. 사용자가 끄거나 옮길 수 없다.
  if (showState) {
    defs.push(
      systemColumn(MSG.GRID_COL_STATE, 84, stateRenderer(options.onRevert), options.headerFont),
    );
  }

  for (const column of columns) {
    // hidden 은 화면에 나오지 않으므로 고정할 자리도 설정 창의 줄도 없다 — frontend/grid.md.
    if (column.hidden) continue;
    defs.push(toColDef(column, options));
  }

  if (options.onToggleDelete) {
    const toggle = options.onToggleDelete;
    const header = options.deleteColumn?.header ?? MSG.GRID_COL_DELETE;
    const enabled = options.deleteColumn?.enabled;
    // 라벨이 <i>삭제</i> 보다 길면 넓혀야 한다 — 시스템 컬럼은 폭이 고정이라 넘치면 잘린다.
    const width = options.deleteColumn?.width ?? 84;
    defs.push(
      systemColumn(header, width, (params: ICellRendererParams<GridRow<T>>) => {
        const row = params.data;
        if (!row) return null;
        /*
          <b>비활성 버튼을 두지 않는다.</b> 회색 아이콘은 눌러 봐야 아무 일이 없고 왜
          안 되는지 말하지 못한다 — 지울 것이 없는 행에는 <b>버튼 자체가 없는</b> 것이
          정직하다(screen/admin/system/code/requirements.md 의 *변경됨 인 행만*).
        */
        if (enabled && !enabled(row)) {
          // <b>사유를 말한다</b> — screen/admin/system/code/requirements.md. 없으면 그냥 비운다: 시스템 행은 자기 배지가
          // 이미 말하고 있고, 같은 말을 두 번 하면 그 배지가 뜻을 잃는다.
          const why = options.deleteColumn?.reason?.(row) ?? null;
          const dash = <span style={{ opacity: 0.35 }}>—</span>;
          return why ? <Tooltip title={why}>{dash}</Tooltip> : dash;
        }
        return (
          <button
            type="button"
            aria-label={label(header)}
            onClick={() => toggle(row.id)}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: 0,
              opacity: row.state === 'D' ? 1 : 0.55,
            }}
          >
            {row.state === 'D' ? <UndoOutlined /> : <DeleteOutlined />}
          </button>
        );
      },
      options.headerFont,
      // 맨 오른쪽이다 — 왼쪽에 두면 상태 컬럼과 붙어 무엇이 무엇인지 읽히지 않는다(frontend/grid.md).
      { pinned: 'right' }),
    );
  }
  return defs;
}

function toColDef<T>(column: ColumnDef<T>, options: AdapterOptions<T>): ColDef<GridRow<T>> {
  const { issues, serverSort } = options;
  const header = headerText(column);
  /*
    <b>헤더가 잘리지 않을 폭을 바닥값으로 깐다</b>(frontend/grid.md · `headerWidth`). 선언의 폭은
    <b>값</b>을 보고 고른 숫자인데 헤더는 다국어라, 같은 컬럼이 영어에서는 남고 한글에서는
    말줄임표가 됐다(2026-08-31 실물).

    <b>최소값으로만 쓴다</b> — 선언이 더 넓으면 그대로이고, 사용자가 끌어 놓은 폭은
    {@code DataGrid} 가 이 위에 덮는다(frontend/grid.md). 판의 자동 맞춤을 쓰지 않는 이유는
    `headerWidth` 머리말에 있다.
  */
  const declared = column.width ?? 0;
  const fitted = options.headerFont === undefined
    ? declared
    : Math.max(
        declared,
        headerMinWidth(header, options.headerFont, {
          sortable: column.sortable ?? false,
          required: marksRequired(column),
          filter: filterEnabled(column),
        }),
      );

  const def: ColDef<GridRow<T>> = {
    colId: column.field,
    // 동적 컬럼만 메시지 키가 아니다 — frontend/grid.md. `cell.ts` 가 그 예외를 한 곳에 담는다.
    headerName: header,
    /*
      필수 표시(`*`)는 <b>클래스</b>이지 글자가 아니다 — frontend/grid.md.

      헤더 글자에 붙이면 그 문자열이 <b>정렬·필터·엑셀</b>로 함께 흘러 `그룹 코드 *` 가
      파일의 헤더가 된다. 붙는 자리는 화면 하나이므로 CSS 가 그린다.
    */
    headerClass: marksRequired(column) ? 'bo-header-required' : undefined,
    // 헤더에만 붙는다 — 셀의 값이 아니라 컬럼 자체에 대한 설명이다(frontend/grid.md).
    headerTooltip: column.headerTooltip ? label(column.headerTooltip) : undefined,
    width: fitted === 0 ? column.width : fitted,
    // 남는 폭을 채우는 컬럼은 <b>폭이 계산된다</b> — 선언한 폭은 최소 폭이 된다(frontend/grid.md).
    ...(column.fill ? { flex: 1, minWidth: fitted || column.width, width: undefined } : {}),
    // `undefined` 를 두지 않는다 — 판은 그것을 <i>바꾸지 않는다</i> 로 읽어, 고정을 풀어도
    // 앞의 상태가 남는다. `null` 이 <b>고정 없음</b>이다.
    pinned: column.pinned ?? null,
    hide: column.visible === false,
    resizable: true,
    // 판의 컬럼 메뉴를 끈다. 컬럼 표시·순서·틀고정의 자리는 그리드 설정 창(③)이고 그것은
    // 개인화 테이블 스키마가 Phase 6 미결이라 아직 없다(frontend/grid.md) — 지금 열어 두면
    // **저장되지 않는 설정**을 사용자가 만지게 되고, 그것은 "설정이 저장되지 않는다" 다.
    suppressHeaderMenuButton: true,
    sortable: column.sortable ?? false,
    valueGetter: (params: ValueGetterParams<GridRow<T>>) =>
      // 파생 컬럼은 저장되는 값이 아니라 행에서 계산한 것이고(frontend/grid.md), 동적 컬럼은 맵의 한
      // 칸이다 — 읽는 규칙은 `cell.ts` 한 곳이다.
      params.data ? cellValue(column, params.data.data) : undefined,
  };

  // 서버가 정렬해 준 것을 그리드가 한 번 더 정렬하면 tie-breaker · 콜레이션 · NULL 위치 ·
  // 표시값에서 어긋난다(frontend/grid.md). 헤더는 상태 표시와 이벤트만 내고 순서는 서버가 준 그대로다.
  if (serverSort) {
    def.comparator = () => 0;
  } else if (column.type === 'bigint') {
    /*
      <b>큰 정수는 문자열로 온다</b>(playbook/INDEX.md) — 그래서 판의 기본 비교자가 <b>글자로</b>
      견준다: {@code '9'} 가 {@code '10'} 보다 뒤에 오고, 그것이 <i>정렬을 눌렀는데
      순서가 이상하다</i> 로만 드러난다.

      <b>서버 정렬이면 위 가지가 이미 막았다.</b> 여기 오는 것은 클라이언트가 정렬하는
      그리드(전량 조회)이며, 그런 화면에서도 같은 컬럼이 같은 순서로 보여야 한다 —
      playbook/INDEX.md 이 *"정렬은 서버가 한다"* 로 닫아 둔 것은 <b>페이징이 있는 화면</b> 얘기다.

      {@code Number()} 로 바꾸지 않는다 — 그러면 이 타입이 존재하는 이유를 비교자 안에서
      되돌리는 꼴이다. 자릿수를 먼저 보고 같으면 글자로 견주는 것이 <b>정확</b>하다.
    */
    def.comparator = compareBigint;
  }

  // `pk` 는 스스로 편집 규칙이다 — `editable` 선언이 없어도 신규 행에서 열린다(frontend/grid.md).
  if ((column.editable !== undefined || column.pk) && options.editable) {
    def.editable = (params) => {
      const row = params.data;
      // 삭제 상태의 행은 셀 편집이 막힌다 — frontend/grid.md.
      if (!row || row.state === 'D') return false;
      return editableOf(column, row.data, { state: row.state }) === true;
    };
    def.valueSetter = (params: ValueSetterParams<GridRow<T>>) =>
      commitEdit(column, options, params.data, params.newValue as unknown);
  }

  applyFilter(def, column);
  applyEditor(def, column, options);
  applyViewer(def, column);
  applyRenderer(def, column, options);
  applyDateFormat(def, column);

  // 한 건이면 셀 툴팁이다 — frontend/grid.md. 표시(테두리·배경)만으로는 무엇이 틀렸는지 말하지 못한다.
  def.tooltipValueGetter = (params) => {
    const issue = issues.find(
      (candidate) => candidate.rowId === params.data?.id && candidate.field === column.field,
    );
    if (issue) return issue.message;
    /*
      오류가 없을 때만 값이 온다. 폭보다 긴 값을 <b>펼쳐 읽는</b> 자리이며(로그인 이력의
      `User-Agent` — screen/admin/system/log/access/requirements.md), 틀린 칸에서는 무엇이 틀렸는지가 먼저다(frontend/grid.md).
    */
    return column.cellTooltip ? scalar(params.value) || null : null;
  };

  def.cellClassRules = {
    /*
      취소선이 <b>이 칸에만</b> 걸린다 — screen/admin/system/code/requirements.md. 선언이 없으면 행 전체이며(frontend/grid.md)
      그때는 이 규칙이 아무 행에도 붙지 않는다.
    */
    'bo-cell-removed': (params) =>
      options.deleteColumn?.strikeField === column.field && params.data?.state === 'D',
    'bo-cell-invalid': (params) =>
      params.data !== undefined
        && issues.some((issue) => issue.rowId === params.data?.id && issue.field === column.field),
    'bo-cell-required': (params) =>
      params.data !== undefined && requiredOf(column, params.data.data, { state: params.data.state }),
    'bo-cell-readonly': (params) =>
      params.data !== undefined
        && editableOf(column, params.data.data, { state: params.data.state }) !== true,
  };
  return def;
}

/**
 * 필터 유형은 <b>컬럼 타입이 정한다</b> — frontend/grid.md.
 *
 * <p><b>고르는 값에는 고르는 필터를 준다.</b> {@code ynFlag} 와 {@code options} 가 있는
 * 컬럼(코드·셀렉트)은 값 집합이 닫혀 있으므로 체크박스이며, 거기에 텍스트 필터를 두면
 * 사용자는 화면에 보이는 <i>이름</i> 을 치는데 판은 <i>코드값</i> 을 비교한다.
 *
 * <p>나머지는 아직 판의 기본이다 — 날짜 범위·숫자 범위는 그것을 처음 요구하는 화면이
 * 가져온다(frontend/grid.md의 구현 노트).
 *
 * <p>{@code list} 는 배열이라 비교 대상이 아니다.
 */
/**
 * 이 컬럼에 <b>필터를 걸 수 있나</b> — {@link applyFilter} 와 <b>헤더 폭이 같은 답을 본다</b>.
 *
 * <p>폭이 이것을 물어야 하는 이유가 판의 <b>필터 버튼</b>이다. 판은 필터를 걸 수 있는 컬럼
 * 이면 <b>언제나</b> 헤더에 그 버튼을 그리므로({@code isFilterAllowed}) 아이콘 하나만큼
 * 글자 자리가 줄어든다 — 걸렸을 때만 붙는 것으로 읽고 자리를 비워 두지 않았더니 필터 있는
 * 컬럼이 계속 잘렸다(2026-09-01).
 *
 * <p>두 곳이 각자 판정하면 <b>한쪽만 고쳐졌을 때 조용히 갈린다</b> — 폭은 필터가 있다고
 * 보고 판은 없다고 보는 식이며, 그 어긋남은 <i>컬럼이 조금 넓다</i> 로만 드러난다.
 */
function filterEnabled<T>(column: ColumnDef<T>): boolean {
  return column.filter !== false && column.type !== 'list';
}

function applyFilter<T>(def: ColDef<GridRow<T>>, column: ColumnDef<T>): void {
  if (!filterEnabled(column)) {
    def.filter = false;
    return;
  }
  if (column.type === 'ynFlag') {
    def.filter = SetFilter;
    def.filterParams = { kind: 'ynFlag' };
    return;
  }
  if (column.options) {
    def.filter = SetFilter;
    def.filterParams = { kind: 'code', options: column.options };
    return;
  }
  /*
    <b>날짜는 달력이다</b> — frontend/grid.md · playbook/INDEX.md. 판의 날짜 필터는 사람이 형식을 손으로
    치는 텍스트 칸이고, 틀리면 <b>조용히 걸리지 않는다</b>.

    {@code options} 가지보다 <b>뒤에</b> 있는 것은 순서가 아니라 사실이다 — 날짜 컬럼에
    후보 목록이 달릴 일이 없으므로 둘은 만나지 않는다. 만난다면 그것은 선언 실수이며,
    그때는 <i>고르는 필터</i> 가 이기는 편이 낫다(집합이 닫혀 있다는 뜻이므로).
  */
  if (column.type === 'date' || column.type === 'dateTime') {
    def.filter = DateFilter;
    def.filterParams = { precision: precisionOf(column.type, column.precision) };
    return;
  }
  def.filter = column.filter ?? true;
}

function applyEditor<T>(
  def: ColDef<GridRow<T>>,
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
): void {
  /*
    <b>날짜는 타입이 편집기를 정한다</b>(frontend/grid.md 의 표 — `date` 계열의 기본 에디터가 `date`).
    선언에 {@code editor} 를 적지 않아도 달력이 뜬다: 판의 기본은 텍스트 칸이고 그것을
    그대로 두면 사람이 `2026-08-31` 을 손으로 치는데, 형식이 틀리면 <b>조용히 값이 되지
    않는다</b>(2026-08-31 실물 — 보유 역할 탭의 시작·종료일).

    <b>{@code cellEditorPopup} 이다.</b> 셀 안에 두면 달력이 셀의 {@code overflow: hidden}
    에 잘린다.
  */
  if (column.type === 'date' || column.type === 'dateTime') {
    def.cellEditor = DateCellEditor;
    def.cellEditorPopup = true;
    def.cellEditorParams = {
      precision: precisionOf(column.type, column.precision),
      valueType: column.type,
    };
    return;
  }

  /*
    <b>팝업은 타입이 정한다</b> — frontend/grid.md. {@code editor: 'popupRef'} 를 적어도 같은 뜻이며
    (아래 {@code switch} 가 그것을 받는다) 날짜와 같은 모양이다.

    <b>선언이 반쪽이면 열지 않는다.</b> {@code type} 만 있고 {@code popup} 이 없는 칸에
    편집기를 달면 <i>더블클릭했는데 아무 일도 없는</i> 셀이 되고, 그 원인은 화면 어디에도
    없다 — 판의 기본 텍스트 편집기로 떨어뜨리는 편이 <b>적어도 무슨 일이 일어났는지</b>
    보인다.
  */
  if ((column.type === 'popupRef' || column.editor === 'popupRef') && column.popup) {
    const spec = column.popup;
    def.cellEditor = PopupCellEditor;
    /*
      <b>팝업으로 띄운다.</b> 셀 안에 두면 판이 편집기 자리를 셀 크기로 자르는데, 이
      편집기는 그리는 것이 없고 <b>모달이 화면 가운데</b>에 뜬다 — 셀 안에 갇힌 편집기가
      모달을 여는 모양이면 판의 포커스 관리가 그 모달의 클릭을 <i>그리드를 떠났다</i> 로
      읽어 편집을 끝낸다(날짜 편집기의 {@code ag-custom-component-popup} 과 같은 자리다).
    */
    def.cellEditorPopup = true;
    const editorParams: PopupCellEditorParams = {
      popupKey: spec.key,
      param: spec.param
        ? (data: unknown) => spec.param?.(data as T)
        : undefined,
      menuCd: options.menuCd,
      onPick: (data: unknown, picked) => {
        // 행 봉투를 다시 찾지 않는다 — 편집기가 판에게 받은 그 행이다.
        commitPopupPick(column, options, data as GridRow<T>, picked);
      },
    };
    def.cellEditorParams = editorParams;
    return;
  }

  switch (column.editor) {
    case 'largeText':
      // 셀에서 볼 수 없는 값이라 팝업으로 연다 — frontend/grid.md.
      def.cellEditor = 'agLargeTextCellEditor';
      def.cellEditorPopup = true;
      def.cellEditorParams = { maxLength: 2000, rows: 8, cols: 60 };
      break;
    case 'number':
      def.cellEditor = 'agNumberCellEditor';
      break;
    case 'select':
      def.cellEditor = 'agSelectCellEditor';
      def.cellEditorParams = { values: (column.options ?? []).map((option) => option.value) };
      break;
    /*
      검색 콤보 — frontend/grid.md · playbook/INDEX.md.

      <b>후보를 여기서 채워 넣는다.</b> {@code grpCd} 인 컬럼은 판에 닿기 전에
      {@code options} 가 채워지므로({@code DataGrid}) 편집기는 <b>한 자리만</b> 본다 —
      공통코드인지 화면이 아는 목록인지 편집기가 다시 묻지 않는다(frontend/grid.md).
    */
    case 'richSelect': {
      const searchParams: SearchSelectCellEditorParams = { options: column.options ?? [] };
      def.cellEditor = SearchSelectCellEditor;
      // 목록이 셀의 {@code overflow: hidden} 에 잘린다 — 날짜 달력과 같은 자리다.
      def.cellEditorPopup = true;
      def.cellEditorParams = searchParams;
      break;
    }
    /*
      배열 편집 — frontend/grid.md · playbook/INDEX.md.

      <b>선언이 반쪽이면 열지 않는다</b>({@code popupRef} 와 같은 규칙) — 고를 팝업이
      없으면 <i>추가</i> 버튼이 아무 일도 하지 않는 창이 뜨고, 그 원인은 화면 어디에도
      없다. 판의 기본 편집기로 떨어지면 <b>배열이 글자로 보이므로</b> 적어도 무엇이
      잘못됐는지 읽힌다.
    */
    case 'listModal': {
      if (!column.listModal) break;
      const listParams: ListModalCellEditorParams = {
        popupKey: column.listModal.key,
        menuCd: options.menuCd,
        display: column.display,
        title: headerText(column),
        /*
          <b>읽기 전용이어도 열린다</b>(frontend/grid.md 와 같은 자리) — 배열은 셀에서 다 볼 수
          없다. 판은 {@code def.editable} 이 꺼진 칸의 편집기를 아예 열지 않으므로
          (아래 {@code toColDef} 가 그것을 정한다) 여기서 보는 것은 <b>행마다 갈리는
          닫힘</b>이 아니라 <b>컬럼 선언의 닫힘</b>이다.
        */
        readOnly: column.editable === undefined || column.editable === false,
      };
      def.cellEditor = ListModalCellEditor;
      def.cellEditorPopup = true;
      def.cellEditorParams = listParams;
      break;
    }
    // 긴 텍스트의 넓은 쪽 — frontend/grid.md · playbook/INDEX.md. `largeText` 와 갈리는 것이 <b>글자 수</b>다.
    case 'textModal': {
      const textParams: TextModalCellEditorParams = {
        title: headerText(column),
        maxLength: column.maxLength ?? DEFAULT_TEXT_MODAL_LENGTH,
        readOnly: column.editable === undefined || column.editable === false,
      };
      def.cellEditor = TextModalCellEditor;
      def.cellEditorPopup = true;
      def.cellEditorParams = textParams;
      break;
    }
    default:
      break;
  }
}

/**
 * {@code textModal} 이 상한을 못 받았을 때 — 판의 긴 글 편집기와 <b>같은 값</b>이다(위
 * {@code largeText} 분기의 {@code maxLength}).
 *
 * <p>둘이 다르면 <b>같은 `nclob` 컬럼이 어느 에디터로 열렸는가에 따라</b> 다른 길이를
 * 받는다.
 */
const DEFAULT_TEXT_MODAL_LENGTH = 2000;

/**
 * 읽기 전용인데 셀에서 다 볼 수 없는 칸 — frontend/grid.md.
 *
 * <p><b>편집기를 열지 않는다.</b> 여는 순간 고칠 수 있는 것처럼 보이고, screen/admin/system/code/requirements.md 의 설명
 * 컬럼처럼 <i>편집하면 값을 안 바꿨는데 행이 생기는</i> 칸에서는 그것이 곧 사고다. 대신
 * {@code DataGrid} 가 더블클릭을 받아 <b>읽기 전용 모달</b>을 연다 — 여기서는 그 칸임을
 * 알리고 커서로 <b>눌러 볼 수 있다는 것</b>만 말한다.
 */
function applyViewer<T>(def: ColDef<GridRow<T>>, column: ColumnDef<T>): void {
  if (!column.viewer) return;
  def.cellStyle = { ...(def.cellStyle as object), cursor: 'zoom-in' };
}

function applyRenderer<T>(
  def: ColDef<GridRow<T>>,
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
): void {
  const textOf = (value: unknown): string => {
    const raw = scalar(value);
    const option = column.options?.find((candidate) => candidate.value === raw);
    return option ? option.text : raw;
  };

  if (column.type === 'ynFlag') {
    // 값이 둘인 데이터 칸은 스위치다 — 체크박스가 아니다(frontend/grid.md).
    /*
      <b>가로만 늘린다.</b> 높이는 행 높이에 매여 있으므로 `small` 그대로이고, 켜짐·꺼짐이
      갈리는 것은 <b>손잡이가 어느 쪽 끝에 있는가</b>라 폭이 그 신호를 만든다 — 좁으면
      두 상태의 손잡이 위치가 몇 px 차이다(2026-08-30 실물).
    */
    /*
      <b>스위치가 곧 편집기다</b>(frontend/grid.md · 2026-08-30 실물). 여기 있던 것은 언제나
      {@code disabled} 인 <b>표시용</b> 스위치였고, 그래서 `editable` 을 선언한 컬럼도
      <b>켜고 끌 수가 없었다</b> — `CODE_MGMT` 의 사용 여부와 `ROLE_MGMT` 의 사용 여부가
      같이 걸려 있었다(screen/menu/requirements.md 의 확인 세 줄이 이 스위치에서 시작한다).

      <b>판의 편집기를 아예 열지 않는다</b>(2026-08-31 — 고쳤다). {@link EditorType} 에
      {@code switch} 가 있지만 판의 편집기로 옮기지 않는 것은, 값이 둘인 칸에서 <i>편집
      모드로 들어갔다 나오는</i> 단계가 순수한 마찰이기 때문이다 — 한 번 누르면 그것이 값이다.

      한동안 {@code def.editable} 을 켠 채로 두고 {@code suppressKeyboardEvent} 로 {@code Enter}
      · {@code F2} 만 막았다. 그러면 <b>더블 클릭이 남는다</b> — 스위치를 비껴 <b>셀 배경</b>을
      두 번 누르면 판의 텍스트 편집기가 열려 {@code true}/{@code false} 를 손으로 치게 됐다
      (2026-08-31 실물).

      <b>{@code def.editable} 을 끄는 것이 잃는 것이 없다.</b> 그것을 켜 두었던 근거는
      <i>필수·읽기전용 배경색과 검증의 입력</i> 이었는데, 둘 다 <b>우리 어휘</b>를 본다 —
      배경은 아래 {@code cellClassRules} 가 {@code editableOf} 로 판정하고(frontend/grid.md) 검증은
      {@code validate.ts} 가 같은 함수를 쓴다. 스위치의 커밋도 {@code valueSetter} 가 아니라
      {@code commitEdit} 를 직접 부르므로 경로가 끊기지 않는다.
    */
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const verdict = switchVerdict(column, options, params.data);
      const control = (
        <Switch
          size="small"
          style={{ minWidth: SWITCH_WIDTH }}
          checked={params.value === true}
          disabled={verdict !== true}
          onChange={(next) => {
            commitEdit(column, options, params.data, next);
          }}
        />
      );
      /*
        <b>왜 못 켜는지 말한다</b>(2026-08-30 — screen/admin/system/user/requirements.md). 텍스트 칸은 편집을 시도하면
        {@code denyEdit} 가 사유를 내지만, <b>비활성 스위치는 이벤트를 내지 않아</b> 그
        문구가 영영 나가지 않는다 — screen/admin/system/code/requirements.md 가 비활성 버튼에 대해 적은 것과 같은 자리다.

        <b>사유가 있을 때만 감싼다.</b> 컬럼 전체가 읽기 전용인 칸(선언에 {@code editable}
        이 없는 것)은 사유가 없고, 거기 툴팁을 붙이면 <i>못 고치는 칸마다 빈 말풍선</i>이
        생긴다 — screen/admin/system/user/requirements.md 의 시스템 축 셋이 정확히 그런 칸이다.

        <b>{@code span} 으로 감싼다</b> — 비활성 요소는 마우스 이벤트를 내지 않으므로
        툴팁이 붙을 자리가 따로 필요하다.
      */
      return typeof verdict === 'boolean' ? control : (
        <Tooltip title={verdict.message}>
          <span style={{ display: 'inline-flex' }}>{control}</span>
        </Tooltip>
      );
    };
    /*
      <b>판의 편집을 닫는다</b> — 스위치가 편집기다. 더블 클릭 · {@code Enter} · {@code F2}
      가 함께 막히므로 {@code suppressKeyboardEvent} 로 키만 가려낼 필요가 없다.
    */
    def.editable = false;
    /*
      <b>셀을 플렉스로 만든다.</b> 판의 셀은 행 높이만 한 {@code line-height} 를 가진
      글자 줄이고, 그 안의 인라인 요소는 <b>글자의 베이스라인에 앉는다</b> — 스위치처럼
      글자가 없는 것은 <i>내려간 g·y 자리(디센더)만큼</i> 아래로 밀려 보인다. 계산상
      가운데인데 눈에는 아니었던 것이 이것이다(2026-08-30 실물).
    */
    def.cellStyle = { display: 'flex', alignItems: 'center' };
    return;
  }
  if (column.renderer === 'badge') {
    /*
      <b>{@code display} 가 글자를 정하고 값이 색을 정한다</b> — 축이 둘이다.

      값이 코드(`SKIP_LOCK`)이고 글자가 라벨(`건너뜀(락)`)인 배지가 있다(screen/admin/system/batch/schedule/requirements.md ·
      screen/admin/system/user/requirements.md). 라벨을 {@code derive} 로 만들어 넣으면 <b>{@code tone} 조회가 라벨
      문자열로 가서 색이 하나도 안 칠해지고</b>, 정렬·필터·엑셀도 라벨을 보게 된다 —
      값을 원본으로 두는 것이 frontend/grid.md 의 규칙이다.

      어휘를 새로 만들지 않는다 — {@code display} 는 frontend/grid.md 이 이미 <i>값을 글자로
      바꾸는 방법</i> 으로 정의해 둔 자리이며 {@code link} 가 같은 뜻으로 쓴다.
    */
    const render = column.display;
    const badge = column.badge;
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const color = TONE_COLOR[toneOf(column.tone, params.value)];
      const text = render ? render(params.value) : textOf(params.value);
      // 맵에 없는 값은 칠하지 않는다 — 정상이 압도적인 목록에서는 그것이 기본이다(frontend/grid.md).
      const main = color ? <Tag color={color}>{text}</Tag> : <span>{text}</span>;
      /*
        <b>값의 내력이 함께 올 수 있다</b>(screen/admin/system/batch/schedule/requirements.md). 실행 이력의 *결과* 가 그 자리이며
        `진행 중` 옆에 `유령` 이 붙는다 — 값이 사라지지 않으므로 아래 `column.badge` 분기가
        금지한 겹침(값 <i>대신</i> 배지)이 아니다.
      */
      const mark = badge && params.data ? badge(params.data.data) : null;
      if (!mark) return main;
      return (
        <span>
          {main}
          {asideTag(mark)}
        </span>
      );
    };
    return;
  }
  /*
    밀리초를 사람이 읽는 단위로 — frontend/grid.md · screen/admin/system/batch/schedule/requirements.md.

    <b>{@code 3725000} 을 사람이 읽지 않는다.</b> 서버가 포맷한 문자열 컬럼을 따로 주면
    그 컬럼의 정렬이 <b>문자열 정렬</b>이 되고(`9분` > `10분`), 셀에서 계산하면 렌더 함수를
    여는 것이라 frontend/grid.md 가 닫아 둔 문이다.

    <b>값은 원본 ms 다</b> — 정렬·필터·엑셀이 전부 그것을 본다.
  */
  if (column.renderer === 'duration') {
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => (
      <span>{durationText(params.value)}</span>
    );
    return;
  }
  /*
    누를 수 있는 글자 — frontend/grid.md.

    <b>{@code onClick} 이 없으면 보통 텍스트다.</b> 링크처럼 보이는데 아무 일도 하지 않는
    셀은 <i>고장</i> 으로 읽힌다.

    <b>값이 아니라 {@code display} 가 그린다</b>(screen/admin/system/log/access/requirements.md의 {@code [보기]}). 값은 그대로
    남아 엑셀과 복사가 그것을 쓴다.
  */
  if (column.renderer === 'link') {
    const onClick = column.onClick;
    const render = column.display;
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const row = params.data;
      const text = render ? render(params.value) : textOf(params.value);
      if (!onClick || !row || text === '') return <span>{text}</span>;
      return (
        <Typography.Link
          disabled={column.clickEnabled ? !column.clickEnabled(row.data) : false}
          aria-disabled={column.clickEnabled ? !column.clickEnabled(row.data) : undefined}
          onClick={(event) => {
            // 판이 같은 클릭으로 행 선택·편집 진입을 함께 처리한다 — 링크만 눌린다.
            event.stopPropagation();
            if (!column.clickEnabled || column.clickEnabled(row.data)) onClick(row.data);
          }}
        >
          {text}
        </Typography.Link>
      );
    };
    return;
  }
  /*
    0~100 막대 — playbook/INDEX.md.

    <b>값은 원본 숫자다</b>(frontend/grid.md). 서버가 `43%` 라는 문자열을 주면 정렬이 문자열
    정렬이 되고 엑셀에도 그 글자가 실린다 — `duration` 과 정확히 같은 규칙이다.

    <b>막대 굵기가 행 높이를 따라가지 않는다</b>(2026-09-02 — playbook/INDEX.md 의 미결을 닫는다).
    판의 행 높이는 사용자가 40px 까지 올릴 수 있는데(frontend/tabs.md), 굵기를 거기 매면 <b>셀을
    가득 채운 색 덩어리</b>가 되어 옆 칸의 글자를 눌러 버린다. 진행률은 <i>값</i> 이지
    <i>배경</i> 이 아니다. 대신 <b>세로 가운데</b>에 놓아 어느 행 높이에서도 글자 줄과
    같은 선에 오게 한다 — 스위치와 같은 처리다.
  */
  if (column.renderer === 'progress') {
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      /*
        <b>{@code Number()} 로 먼저 묻지 않는다</b> — {@code Number(null)} 과
        {@code Number('')} 이 <b>둘 다 0</b> 이라, 값이 없는 칸에 <b>0% 막대</b>가 그려진다
        (2026-09-02 실물 — 테스트가 잡았다). 그러면 <i>아직 시작 안 함</i> 과 <i>진행률을
        모름</i> 이 화면에서 같은 것이 되고, 후자는 <b>고장</b>인데 전자로 읽힌다.
        `duration` 이 {@code null} 을 빈 칸으로 두는 것과 같은 자리다(screen/admin/system/batch/schedule/requirements.md).
      */
      const raw: unknown = params.value;
      const numeric = raw === null || raw === undefined || raw === '' ? NaN : Number(raw);
      if (!Number.isFinite(numeric)) return <span>{textOf(raw)}</span>;
      return <ProgressCell percent={numeric} tone={toneOf(column.tone, numeric)} />;
    };
    def.cellStyle = { display: 'flex', alignItems: 'center' };
    return;
  }
  /*
    아이콘 + 툴팁 — playbook/INDEX.md.

    <b>모양은 {@code icons} 가, 색은 {@code tone} 이 정한다</b>(축 둘). 툴팁에는
    <b>화면에 보이던 글자</b>가 온다 — 아이콘만 남으면 색맹·흑백 인쇄·모르는 아이콘
    셋 다에서 그 칸을 읽을 수 없다.

    <b>맵에 없는 값은 글자로 떨어진다</b>(frontend/grid.md). 물음표 아이콘을 그리면 진짜 값이
    화면 어디에도 남지 않는다 — {@code cellIcons.tsx} 머리말과 같은 결정이다.
  */
  if (column.renderer === 'icon') {
    const render = column.display;
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const text = render ? render(params.value) : textOf(params.value);
      const glyph = cellIcon(column.icons?.[scalar(params.value)]);
      if (!glyph) return <span>{text}</span>;
      return <IconCell glyph={glyph} text={text} tone={toneOf(column.tone, params.value)} />;
    };
    def.cellStyle = { display: 'flex', alignItems: 'center' };
    return;
  }
  /*
    배열을 태그로 — frontend/grid.md · playbook/INDEX.md.

    <b>앞 몇 개와 `+N` 이다.</b> 전부 그리면 태그가 셀 밖으로 밀려 나가고, 판은 셀을
    한 줄로 자르므로 <b>넘친 것이 화면에서 사라진다</b> — 열 개 붙은 행과 세 개 붙은
    행이 같아 보이고, 사용자는 <i>세 개인 줄 알고</i> 저장한다.

    <b>남은 것은 툴팁이 말한다.</b> `+7` 만으로는 무엇이 더 있는지 알 수 없고, 그것을
    보려고 편집기를 여는 것은 <b>읽기 위해 편집을 시작하는</b> 일이다.
  */
  if (column.renderer === 'tags') {
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const items: unknown[] = Array.isArray(params.value) ? params.value : [];
      const text = (item: unknown) => (column.display ? column.display(item) : String(item));
      const shown = items.slice(0, TAG_LIMIT);
      const rest = items.slice(TAG_LIMIT);
      return (
        <span>
          {shown.map((item, index) => (
            <Tag key={index}>{text(item)}</Tag>
          ))}
          {rest.length > 0 && (
            <Tooltip title={rest.map(text).join(', ')}>
              <Tag>{`+${rest.length}`}</Tag>
            </Tooltip>
          )}
        </span>
      );
    };
    return;
  }
  /*
    값 <b>과 함께</b> 내는 표시 — screen/admin/system/code/requirements.md.

    `renderer: 'badge'` 아래에 두는 것은, 저것이 값 <i>대신</i> 배지를 그리므로 둘이
    한 셀에서 만나면 값이 사라지기 때문이다. 여기 오는 셀은 값이 주인공이고 굵기와
    배지는 그 값의 <b>내력</b>을 덧붙일 뿐이다.
  */
  if (column.emphasis || column.badge) {
    const emphasis = column.emphasis;
    const badge = column.badge;
    def.cellRenderer = (params: ICellRendererParams<GridRow<T>>) => {
      const row = params.data;
      if (!row) return null;
      const mark = badge ? badge(row.data) : null;
      return (
        <span>
          {emphasis && emphasis(row.data) ? <b>{textOf(params.value)}</b> : textOf(params.value)}
          {mark ? asideTag(mark) : null}
        </span>
      );
    };
    return;
  }
  if (column.options) {
    def.valueFormatter = (params) => textOf(params.value);
  }
}

/**
 * 시각을 <b>보이는 대로</b> 그린다 — frontend/grid.md.
 *
 * <p>서버가 주는 것은 오프셋을 포함한 ISO-8601 이고(backend/packages.md) 그대로 두면 셀에
 * `2026-08-30T09:50:12.345Z` 가 보인다. 읽는 사람은 <b>자기 시간대의 시각</b>을 기대하므로
 * `dayjs` 가 현지로 옮겨 자른다 — 이력 화면이 처음으로 시각 컬럼을 <b>보이게</b> 두면서
 * 필요해졌다(그 전까지 시각 컬럼은 전부 감사 4컬럼이라 기본 숨김이었다).
 *
 * <p><b>형식 문자열은 시스템 설정이 정한다</b>(2026-08-31 — `dateFormat`). 여기 상수로 두면
 * 같은 값이 화면과 설정 화면에서 다른 모양으로 보인다.
 *
 * <p><b>값은 바꾸지 않는다</b>(frontend/grid.md) — 바뀌는 것은 표시뿐이고 엑셀과 조건은 원본을 쓴다.
 * 다만 <b>필터는 보이는 글자를 본다</b>: 셀에 `2026-08-30 18:50:12` 가 보이는데 필터가
 * ISO 원문을 비교하면 <i>화면에 있는 글자로 걸러지지 않는다</i>(frontend/grid.md).
 */
function applyDateFormat<T>(def: ColDef<GridRow<T>>, column: ColumnDef<T>): void {
  if (column.type !== 'date' && column.type !== 'dateTime') {
    return;
  }
  const precision = precisionOf(column.type, column.precision);
  const text = (value: unknown) => dateText(value, precision);
  def.valueFormatter = (params) => text(params.value);
  def.filterValueGetter = (params) =>
    params.data ? text(cellValue(column, params.data.data)) : '';
}

/**
 * 큰 정수 문자열 둘의 순서 — {@code Number} 를 거치지 않는다(playbook/INDEX.md).
 *
 * <p><b>부호 → 자릿수 → 글자</b> 순으로 본다. 앞의 {@code 0} 을 떼고 나면 <i>자릿수가
 * 많은 쪽이 크다</i> 가 양수에서 언제나 참이고, 자릿수가 같으면 <b>글자 비교가 곧 수
 * 비교</b>다 — 자리마다 같은 무게를 갖기 때문이다.
 *
 * <p><b>빈 값은 뒤로 보낸다</b> — 판이 {@code null} 을 어디에 두는지는 정렬 방향에 따라
 * 갈리는데, 여기서는 <i>값이 없는 것</i>이 <i>0</i> 보다 작다고 말할 근거가 없다.
 */
function compareBigint(left: unknown, right: unknown): number {
  // `scalar` 를 쓴다 — 객체가 오면 빈 칸이고 `[object Object]` 가 값 행세를 하지 않는다.
  const text = (value: unknown): string => scalar(value).trim();
  const a = text(left);
  const b = text(right);
  if (a === b) return 0;
  if (a === '') return 1;
  if (b === '') return -1;

  const negative = (value: string) => value.startsWith('-');
  if (negative(a) !== negative(b)) return negative(a) ? -1 : 1;

  // 부호를 떼고 크기만 견준 뒤, 둘 다 음수면 결과를 뒤집는다.
  const magnitude = (value: string) => value.replace(/^[+-]/, '').replace(/^0+(?=\d)/, '');
  const x = magnitude(a);
  const y = magnitude(b);
  const size = x.length === y.length ? (x < y ? -1 : x > y ? 1 : 0) : x.length - y.length;
  return negative(a) ? -size : size;
}

/**
 * 값 → 색 — frontend/grid.md 의 <b>축 둘</b>.
 *
 * <p>맵은 값이 무엇인가로, 범위는 값이 어디에 드는가로 고른다. <b>둘 다 없는 값은 칠하지
 * 않는다</b>: 정상이 압도적인 목록에서는 그것이 기본이고, 칠한 것만 눈에 남는다(frontend/grid.md).
 */
function toneOf(tone: ColumnDef<never>['tone'], value: unknown): Tone {
  if (!tone) return 'none';
  if (Array.isArray(tone)) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return 'none';
    // `max` 오름차순으로 훑어 첫 칸이 이긴다 — 선언 순서에 기대지 않는다.
    const found = [...tone].sort((left, right) => left.max - right.max).find((band) => numeric <= band.max);
    return found ? found.tone : 'none';
  }
  return tone[String(value)] ?? 'none';
}

/** 표시용 문자열. 객체가 오면 `[object Object]` 가 아니라 빈 칸이다 — frontend/grid.md 이 기각한 모양이다. */
/**
 * 이 행에서 <b>스위치를 만질 수 있는가</b> — {@code def.editable} 과 같은 판정이다.
 *
 * <p>같은 규칙을 두 곳에 적지 않으려고 {@link editableOf} 하나를 본다. 다른 것은 <b>거절을
 * 말하지 않는다</b>는 것뿐이다: 비활성 스위치는 <i>못 만지는 것</i>으로 이미 읽히고
 * (screen/admin/system/user/requirements.md — <i>회색 체크박스는 꺼진 것과 구분되지 않는다</i> 의 반대편이다), 누를 수 없는
 * 컨트롤은 사유를 낼 계기 자체가 없다.
 */
/**
 * 스위치가 열리는가 — <b>그리고 안 열리면 왜인가</b>.
 *
 * <p>{@code false} 와 {@code {ok:false, message}} 를 가르는 것이 요점이다(frontend/grid.md). 앞엣것은
 * <b>이 컬럼은 원래 못 고친다</b>(선언에 {@code editable} 이 없거나 지운 행이다)이고,
 * 뒤엣것은 <b>이 행에서만 못 고친다</b> — screen/admin/system/user/requirements.md 의 <i>자기 계정은 사용 중지할 수 없다</i>
 * 가 그것이며, 그때는 <b>사유를 말해야 한다</b>: 말하지 않으면 <i>왜 이 행만 안 되나</i> 가
 * 된다.
 */
function switchVerdict<T>(
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
  row: GridRow<T> | undefined,
): EditableResult {
  if (!row || !options.editable) return false;
  // 삭제 상태의 행은 셀 편집이 막힌다 — frontend/grid.md.
  if (row.state === 'D') return false;
  if (column.editable === undefined && !column.pk) return false;
  return editableOf(column, row.data, { state: row.state });
}

/**
 * 편집 한 번을 행에 반영한다 — <b>판의 {@code valueSetter} 와 스위치가 같은 길을 쓴다</b>.
 *
 * <p>스위치가 {@code onEdit} 를 직접 부르면 <b>거절·패치·후작업 셋이 두 벌</b>이 되고, 한쪽만
 * 고쳐졌을 때 <i>스위치로 바꾼 값만 검증을 지나지 않는</i> 상태가 된다.
 */
function commitEdit<T>(
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
  row: GridRow<T> | undefined,
  value: unknown,
): boolean {
  if (!row) return false;
  return commitPatch(column, options, row, (data) => cellPatch(column, data, value));
}

/**
 * 팝업에서 고른 행을 <b>이 행에 머지한다</b> — frontend/grid.md.
 *
 * <p><b>{@link commitEdit} 와 같은 길을 탄다.</b> 갈리는 것은 <i>패치를 무엇이 만드는가</i>
 * 하나이며(셀 값 하나냐 {@code apply} 의 부분 객체냐), 그 뒤의 <b>편집 가능 판정 →
 * {@code afterEdit} → 검증</b> 순서는 같아야 한다: 셀 편집기 밖에서 온 값이라고 다른
 * 순서를 타면 <b>같은 컬럼이 어떻게 채워졌는가에 따라 다른 행</b>이 된다.
 *
 * <p>{@code picked} 가 {@code null} 이면 <b>지우는 것</b>이다 — 팝업의 <i>지우기</i>
 * 버튼이 그 경로이며, 창을 그냥 닫은 경우는 여기까지 오지 않는다.
 */
export function commitPopupPick<T>(
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
  row: GridRow<T> | undefined,
  picked: Record<string, unknown> | null,
): boolean {
  const spec = column.popup;
  if (!row || !spec) return false;
  return commitPatch(column, options, row, (data) => spec.apply(data, picked));
}

/**
 * 편집 하나가 행에 닿는 <b>공통 경로</b> — frontend/grid.md.
 *
 * <p>갈라 둔 것은 <b>둘이 같은 순서를 타야 하기 때문</b>이다. 예전에는 이 몸통이
 * {@code commitEdit} 안에만 있었고, 팝업이 그것을 흉내 내면 <b>흉내가 한 걸음 모자란
 * 날</b>이 온다 — {@code afterEdit} 를 빠뜨리면 금액이 안 채워지고, 검증을 빠뜨리면
 * 틀린 값이 붉은 표시 없이 저장으로 간다.
 */
function commitPatch<T>(
  column: ColumnDef<T>,
  options: AdapterOptions<T>,
  row: GridRow<T>,
  makePatch: (data: T) => Partial<T>,
): boolean {
  const verdict = editableOf(column, row.data, { state: row.state });
  if (verdict !== true) {
    if (verdict !== false) options.onEditDenied(verdict.message);
    return false;
  }
  const patch = makePatch(row.data);
  const merged = { ...row.data, ...patch };
  // 후작업은 한 단계에서 멈춘다 — 다른 컬럼의 afterEdit 를 다시 부르지 않는다(frontend/grid.md).
  const extra = column.afterEdit ? column.afterEdit(merged) : null;
  const applied = extra ? { ...merged, ...extra } : merged;
  options.onEdit(row.id, extra ? { ...patch, ...extra } : patch);

  /*
    <b>규칙을 어겼으면 그 자리에서 말한다</b>(2026-08-31 실물 — frontend/grid.md ④).

    <b>편집을 막지 않는다</b>(frontend/grid.md) — 값은 이미 행에 들어갔고 붉은 표시도 붙는다. 다만
    그 표시가 <i>무엇이 틀렸는지</i> 를 말하지 않아, IP 형식이 아닌 값을 넣으면 <b>테두리만
    빨개지고 이유가 어디에도 없었다</b>: 유일한 통로가 셀 툴팁인데 판의 기본 지연이 2초라
    <b>2초를 버티고 호버해야</b> 나왔다.

    <b>거절 메시지와 같은 통로를 쓴다</b>(위 {@code onEditDenied} — frontend/grid.md). 둘의 뜻은
    다르지만 사용자가 알아야 하는 것은 같다: <i>지금 한 입력이 그대로 남지 않는다</i>.

    <b>{@code afterEdit} 뒤의 값을 본다</b> — 후작업이 값을 바꿀 수 있으므로 그 전에 재면
    <i>고쳐 준 값</i>에 대해 경고하게 된다.
  */
  const message = validateCell(column, cellValue(column, applied), applied, { state: row.state });
  if (message) options.onInvalidEdit(message);
  return true;
}

function scalar(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return '';
}

/** 상태·삭제 컬럼 — 시스템이 붙이며 숨기지도 옮기지도 못한다(frontend/grid.md). */
function systemColumn<T>(
  headerKey: string,
  width: number,
  renderer: ((params: ICellRendererParams<GridRow<T>>) => React.ReactNode) | undefined,
  font?: string,
  extra?: Partial<ColDef<GridRow<T>>>,
): ColDef<GridRow<T>> {
  const header = label(headerKey);
  /*
    <b>시스템 컬럼도 헤더가 잘린다.</b> 폭이 고정(min = max)이라 사용자가 끌어 늘릴 수도
    없어서 모자라면 <b>영영 말줄임표</b>이고, screen/admin/system/code/requirements.md 처럼 라벨을 바꾸는 자리가 있으므로
    상수 하나로는 덮이지 않는다.
  */
  const fitted = font === undefined
    ? width
    : Math.max(
        width,
        // 시스템 컬럼은 정렬도 필터도 없다 — 아래 선언이 셋을 다 끈다.
        headerMinWidth(header, font, { sortable: false, required: false, filter: false }),
      );
  return {
    colId: `__${headerKey}`,
    headerName: header,
    width: fitted,
    minWidth: fitted,
    maxWidth: fitted,
    pinned: 'left',
    sortable: false,
    filter: false,
    resizable: false,
    suppressMovable: true,
    lockPosition: true,
    editable: false,
    /*
      가운데 정렬을 <b>줄 상자가 아니라 칸</b>에서 한다 — 셀의 내용은 기본적으로 글자
      줄에 얹히므로 글리프가 없는 것(체크박스 · 아이콘)은 내려간 만큼 아래로 처진다
      (스위치 셀과 같은 자리다 — frontend/grid.md).
    */
    cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'center' },
    cellRenderer: renderer,
    ...extra,
  };
}

/**
 * 행 번호 — <b>페이지를 넘어도 이어진다</b>(frontend/grid.md).
 *
 * <p>{@code rowIndex} 는 <b>지금 보이는 자리</b>이고 거기에 이 페이지 앞의 건수를 더한다.
 * 그래서 21번째 행은 2페이지 첫 줄에서도 <b>21</b> 이다 — 페이지마다 1부터 다시 세면
 * <i>"몇 번째 데이터인가"</i> 를 사용자가 손으로 계산하게 된다.
 *
 * <p><b>컬럼 필터가 걸리면 번호가 촘촘해지지 않는다</b> — 걸러진 행은 자리를 비우는 것이
 * 아니라 목록에서 빠지므로 보이는 순서대로 다시 매겨진다. 필터는 현재 페이지 안에서만
 * 걸리며(frontend/grid.md) 그 사실은 푸터가 말한다(frontend/grid.md).
 *
 * <p>정렬 대상이 아니다. 번호는 <b>값이 아니라 자리</b>이므로 그것으로 정렬한다는 말은
 * 뜻이 없다.
 */
function rowNumberColumn<T>(): ColDef<GridRow<T>> {
  return {
    ...systemColumn<T>(MSG.GRID_COL_ROW_NO, 60, undefined),
    colId: ROW_NO_COL_ID,
    valueGetter: (params: ValueGetterParams<GridRow<T>>) => {
      const index = params.node?.rowIndex;
      if (index === null || index === undefined) return null;
      // offset 은 <b>선언이 아니라 문맥</b>에서 온다 — 위 GridContext 의 설명 참조.
      return index + 1 + ((params.context as GridContext | undefined)?.rowOffset ?? 0);
    },
    cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-end' },
  };
}

/**
 * 다중 선택 체크박스 — <b>번호 오른쪽</b>이다(frontend/grid.md).
 *
 * <p>판이 스스로 붙이는 선택 컬럼을 쓰지 않는다. 그것은 {@code lockPosition: 'left'} 라
 * <b>언제나 맨 앞</b>이고, 번호를 그보다 왼쪽에 두는 내장 행번호 컬럼은 <b>Enterprise</b>
 * 다 — Community 에서 내장 체크박스를 켜면 <i>행을 부르는 이름</i> 앞에 <i>행에 하는
 * 일</i> 이 놓인다.
 *
 * <p><b>그 대가로 수식키 처리도 오지 않는다.</b> 판은 내장 컬럼에만 `Shift`·`Ctrl` 범위
 * 선택을 붙이므로 우리가 직접 한다 — 규칙은 {@code selectRange} 가 갖는다.
 *
 * <p><b>행을 눌러도 선택되지 않는다</b>(다중일 때) — 체크박스만이 선택 수단이다. 클릭
 * 선택을 함께 열면 스크롤하다 흘린 클릭 하나가 <b>고른 것을 통째로 갈아 치운다.</b>
 */
function selectionColumn<T>(): ColDef<GridRow<T>> {
  return {
    ...systemColumn<T>(MSG.GRID_COL_SELECT, 44, (params: ICellRendererParams<GridRow<T>>) => (
      <Checkbox
        checked={params.node.isSelected() === true}
        aria-label={label(MSG.GRID_SELECT_ROW)}
        onChange={(event) => selectClicked(params, event)}
      />
    )),
    colId: SELECT_COL_ID,
    headerName: '',
    headerComponent: SelectAllHeader,
  };
}

/**
 * 체크박스를 눌렀다 — 수식키가 있으면 <b>범위</b>다({@code selectRange}).
 *
 * <p><b>앵커가 아직 살아 있는지 먼저 묻는다.</b> 범위는 <i>보이는 순서</i>의 구간이라
 * 자리로 세는데, 그 사이에 조회·정렬·필터·페이지 이동이 자리를 갈아 끼웠을 수 있다 —
 * 그때 인덱스는 <b>남의 행</b>을 가리킨다. 어긋나면 범위를 잡지 않고 한 행 토글로
 * 떨어진다.
 *
 * <p><b>두 번 나눠 부른다</b>(풀고 → 고른다). 한 번에 담을 수 없는 것은 판의
 * {@code setNodesSelected} 가 <i>한 값</i>만 받기 때문이고, 비어 있으면 부르지 않으므로
 * 대개는 한 번이다. 순서가 <b>푸는 쪽이 먼저</b>인 것은 그래야 줄어드는 화면이
 * <i>줄었다</i> 로 보이기 때문이다 — 반대로 하면 한 프레임 동안 <b>둘 다 칠해진</b> 판이
 * 스친다.
 */
function selectClicked<T>(
  params: ICellRendererParams<GridRow<T>>,
  event: CheckboxChangeEvent,
): void {
  /*
    앵커는 <b>선언이 아니라 문맥</b>에서 온다 — 번호 컬럼의 {@code rowOffset} 과 같은
    자리다(위 {@link GridContext}). 선언에 담으면 컬럼이 다시 계산될 때마다(폭 · 코드
    후보 · 검증 결과) 새로 만들어져 <b>사용자가 방금 누른 자리를 잊는다</b>.
  */
  const anchor = (params.context as GridContext | undefined)?.selectAnchor;
  if (!anchor) {
    params.node.setSelected(event.target.checked);
    return;
  }

  const native = event.nativeEvent;
  const index = params.node.rowIndex;
  const shift = native instanceof MouseEvent && native.shiftKey;
  const remove = native instanceof MouseEvent && (native.ctrlKey || native.metaKey);

  const at = anchor.index === null ? undefined : params.api.getDisplayedRowAtIndex(anchor.index);
  const alive = anchor.rowId !== null && at?.data?.id === anchor.rowId;
  const plan = shift && alive && index !== null ? planSelectRange(anchor, index, remove) : null;

  if (plan === null) {
    params.node.setSelected(event.target.checked);
    // 수식키 없는 클릭이 <b>다음 범위의 시작점</b>이다 — 판의 내장 동작과 같다.
    anchor.index = index;
    anchor.rowId = params.data?.id ?? null;
    anchor.painted = [];
    return;
  }

  const nodes = (indexes: number[]) =>
    indexes
      .map((row) => params.api.getDisplayedRowAtIndex(row))
      .filter((node) => node !== null && node !== undefined);

  const off = nodes(plan.deselect);
  if (off.length > 0) {
    params.api.setNodesSelected({ nodes: off, newValue: false, source: 'checkboxSelected' });
  }
  const on = nodes(plan.select);
  if (on.length > 0) {
    params.api.setNodesSelected({ nodes: on, newValue: true, source: 'checkboxSelected' });
  }
  anchor.painted = plan.painted;
}

/**
 * 상태 넷의 표시 — frontend/grid.md. <b>수정에만 되돌리기가 붙는다.</b>
 *
 * <p>신규를 되돌리는 것은 <i>없애는 것</i> 이고 삭제 표시를 되돌리는 것도 삭제 아이콘의
 * 일이다(frontend/grid.md) — 여기에 다 두면 <b>같은 아이콘이 상태마다 다른 일</b>을 한다.
 *
 * <p><b>글리프가 아니라 아이콘이다</b>(2026-08-30 실물). `＋`·`✕`·`✎` 는 글꼴이 주는
 * 글자라 <b>고른 글꼴마다 굵기와 크기가 달라지고</b>(frontend/tabs.md) 셸의 다른 아이콘과 나란히
 * 놓이면 혼자 다른 계열로 보인다.
 */
function stateRenderer<T>(onRevert: (rowId: string) => void) {
  return function StateCell(params: ICellRendererParams<GridRow<T>>): React.ReactNode {
    const row = params.data;
    if (!row) return null;

    switch (row.state) {
      case 'C':
        return <PlusOutlined />;
      case 'D':
        return <DeleteOutlined />;
      case 'U':
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <EditOutlined />
            {/* 브라우저 기본 툴팁(`title`)을 쓰지 않는다 — 셸의 다른 툴팁과 모양이 다르다. */}
            <Tooltip title={label(MSG.GRID_REVERT)}>
              <button
                type="button"
                aria-label={label(MSG.GRID_REVERT)}
                onClick={() => onRevert(row.id)}
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: 0,
                  lineHeight: 1,
                  opacity: 0.55,
                }}
              >
                <UndoOutlined />
              </button>
            </Tooltip>
          </span>
        );
      default:
        return null;
    }
  };
}

