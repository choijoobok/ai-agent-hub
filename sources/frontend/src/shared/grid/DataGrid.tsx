import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AgGridReact } from 'ag-grid-react';
import {
  AllCommunityModule,
  ModuleRegistry,
  type GridApi as AgGridApi,
  type GridReadyEvent,
  type RowClassParams,
  type SortChangedEvent,
} from 'ag-grid-community';
import { PlusOutlined, SaveOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Modal, Select, Space, Switch, Tooltip, Typography, theme } from 'antd';
import { actionOfUrl } from '@/shared/authz/apiAction';
import { hasPermission } from '@/shared/authz/permission';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { useAuthStore } from '@/shared/stores/authStore';
import { useResolvedDark } from '@/shared/theme/useResolvedDark';
import { agGridTheme } from '@/shared/theme/themeTokens';
import { useUiStore } from '@/shared/stores/uiStore';
import { ROW_NO_COL_ID, SELECT_COL_ID, toColDefs, type GridContext } from '@/shared/grid/columnDefs';
import { newAnchor } from '@/shared/grid/selectRange';
import { headerText } from '@/shared/grid/cell';
import { isEditable } from '@/shared/grid/columnRules';
import { ensureGridStyles } from '@/shared/grid/gridStyles';
import { ExcelButton } from '@/shared/grid/excel/ExcelButton';
import type { ExcelColumn } from '@/shared/grid/excel/workbook';
import type { ExcelSpec } from '@/shared/grid/excel/types';
import { GridFooter } from '@/shared/grid/GridFooter';
import type { SplitAxis } from '@/shared/grid/SplitPane';
import { GridSettingsModal } from '@/shared/grid/pref/GridSettingsModal';
import { useGridPref } from '@/shared/grid/pref/useGridPref';
import { gridLocaleText } from '@/shared/grid/gridLocale';
import { cellText, isCopyShortcut, renderedText } from '@/shared/grid/copyCell';
import type { GridRow } from '@/shared/grid/rows';
import type {
  ColumnDef,
  DeleteColumnSpec,
  GridControl,
  TitleSuffixSpec,
} from '@/shared/grid/types';
import { useCodeOptions } from '@/shared/code/useCodeOptions';
import { actionTargets, type GridController } from '@/shared/grid/useGridController';

// Community 전 모듈. 판 경계는 어댑터가 갖는다(frontend/grid.md).
ModuleRegistry.registerModules([AllCommunityModule]);

/**
 * 판의 <b>헤더 글자 굵기</b> — 테마의 {@code headerFontWeight} 를 옮겨 적은 것이다.
 *
 * <p>우리 테마는 이것을 덮지 않고({@code themeTokens}) 판의 기본이 <b>500 고정</b>이다 —
 * {@code ref: 'fontWeight'} 가 아니라 리터럴이므로 본문 굵기를 바꿔도 따라오지 않는다.
 * 헤더 폭을 재는 데 쓰며(`headerWidth`), 이 값이 실제와 어긋나면 <b>증상이 조용하다</b>:
 * 헤더가 다시 잘린다.
 */
const AG_HEADER_FONT_WEIGHT = 500;

/**
 * 그리드 하나 — frontend/grid.md 의 크롬 + AG Grid.
 *
 * <p><b>①의 버튼은 화면이 손으로 놓지 않는다.</b> {@code api} 선언을 읽어 헤더가 만들고
 * 권한 없는 것은 감춘다(frontend/grid.md) — 손으로 두르면 <b>버튼을 추가하고 권한을 빠뜨리는</b>
 * 경로가 열린다.
 *
 * <p><b>렌더 함수(JSX)를 받지 않는다</b>(frontend/grid.md). 화면이 주는 것은 {@code api} 와
 * {@code controls} 선언 둘이고 헤더는 그것을 한 배열로 합쳐 순서대로 그린다 — 열면 그리드의
 * 잠금 상태를 모르는 버튼이 섞인다.
 *
 * <p><b>②(엑셀)와 ③(그리드 설정)이 아직 없다.</b> ②는 이 화면의 엑셀이 그리드와 모양이
 * 달라 ①의 버튼이고(screen/admin/system/code/requirements.md), ③은 개인화 테이블의 스키마가 Phase 6 미결이다(frontend/grid.md) —
 * 자리를 비워 두는 것이 아니라 <b>아직 그릴 것이 없다</b>.
 */

interface DataGridProps<T> {
  /** 화면 제목이 아니라 <b>그리드 제목</b>이다 — frontend/grid.md. 메시지 키. */
  title: string;
  controller: GridController<T>;
  controls?: GridControl[];
  /**
   * ①의 <b>조회를 화면이 건다</b> — frontend/grid.md(2026-09-01).
   *
   * <p>보통은 {@code api.read} 선언이 버튼을 낸다(frontend/grid.md). <b>그럴 수 없는 그리드가
   * 있다</b>: 행이 자기 경로가 아니라 <b>다른 조회에서</b> 오는 경우이며, `USER_AUTH_MGMT`
   * 의 하단이 그것이다 — 트리와 목록을 {@code getMenuTree} 하나가 함께 먹인다(screen/admin/system/user/requirements.md).
   * 그 응답은 {@code PageResponse} 가 아니라 트리라 {@code controller.read()} 가 파싱하지
   * 못하고, 그래서 {@code api.read} 를 선언할 수가 없다.
   *
   * <p><b>버튼이 없어도 되는 것은 아니다.</b> 자동 조회(부모 선택 변경)와 <b>수동 재조회</b>
   * 는 다른 일이며(frontend/grid.md), 마스터의 조회로 대신하면 <b>고른 행을 잃는다</b> — 그쪽은
   * 목록을 통째로 바꾸고 디테일을 리셋한다(frontend/grid.md).
   *
   * <p>{@code TreeGridProps.onRead} 와 <b>같은 모양이다</b> — 트리도 같은 이유로 이 길을
   * 쓴다. <b>미저장 확인은 화면이 한다</b>: 여기서 부르는 것은 컨트롤러의 조회가 아니므로
   * {@code read()} 안의 재조회 규약(frontend/grid.md)이 돌지 않는다.
   *
   * <p><b>{@code api.read} 와 함께 쓰지 않는다.</b> 둘 다 있으면 이것이 이기며, 그것은
   * 곧 선언 하나가 죽어 있다는 뜻이다.
   */
  onRead?: () => void;
  /**
   * 물리 삭제하는 테이블에만 삭제 컬럼이 나온다 — frontend/grid.md.
   *
   * <p>객체를 주면 <b>라벨과 게이트</b>를 바꾼다 — screen/admin/system/code/requirements.md 의 *되돌리기* 가 그것이다.
   */
  deletable?: boolean | DeleteColumnSpec<T>;
  /** 행이 없을 때의 문구. 메시지 키. */
  emptyText?: string;
  /**
   * 마스터-디테일에서 선택 행이 바뀔 때. <b>단일 선택의 것</b>이다.
   *
   * <p><b>거부할 수 있다</b>(2026-08-30 — screen/admin/system/code/requirements.md 가 처음 요구했다). {@code false} 를 돌려주면
   * 판의 선택이 <b>이전 행으로 되돌아간다</b>. 디테일에 미저장 편집이 있을 때 묻는 자리이며
   * (UX-10 · frontend/grid.md), 거부권이 없으면 <b>보고 있는 것과 고른 것이 갈린다</b> — 그 상태로
   * 저장하면 A 그룹의 행이 B 그룹으로 나간다.
   *
   * <p>{@code void} 를 돌려주면 언제나 통과다 — screen/admin/system/code/requirements.md 처럼 잃을 것이 없는 화면은 묻지
   * 않는다.
   */
  onSelect?: (row: GridRow<T> | null) => void | boolean | Promise<boolean>;
  /**
   * 행을 <b>더블 클릭했다</b> — 다른 화면으로 <b>넘겨주는</b> 제스처다(UX-02 멀티탭).
   *
   * <p>screen/menu/requirements.md 가 처음 요구했다(API 카탈로그 하단 → `MENU_TREE`). screen/menu/requirements.md 탭 3(→
   * `ROLE_AUTH_MGMT`)과 screen/admin/system/log/access/requirements.md(요청 URI → `API_CATALOG`)가 <b>같은 제스처를 같은 뜻으로</b>
   * 쓴다 — 화면이 셋이어도 사용자가 배우는 것은 하나다.
   *
   * <p><b>편집이 열린 그리드에서는 오지 않는다.</b> 거기서 더블 클릭은 편집기를 여는
   * 제스처이며(frontend/grid.md) 둘을 겹치면 <b>화면이 열리면서 편집이 시작된다</b>.
   *
   * <p><b>`viewer` 컬럼도 오지 않는다</b>(frontend/grid.md) — 그 칸에서 기대하는 것은 <i>긴 글을
   * 펼쳐 읽는 것</i>이고, 화면 열기가 이기면 읽을 방법이 사라진다.
   */
  onRowDoubleClick?: (row: GridRow<T>) => void;
  /**
   * 선택 방식 — 기본은 <b>단일</b>이다(frontend/grid.md).
   *
   * <p>{@code 'multiple'} 이면 행 번호 <b>오른쪽</b>에 체크박스 컬럼이 서고 <b>행 클릭으로는
   * 선택되지 않는다</b> — 체크박스만이 선택 수단이다. 클릭 선택을 함께 열면 스크롤하다
   * 흘린 클릭 하나가 고른 것을 통째로 갈아 치운다.
   */
  selection?: 'single' | 'multiple';
  /** 다중 선택에서 고른 행들. */
  onSelectionChange?: (rows: GridRow<T>[]) => void;
  height?: number | string;
  menuCd: string;
  /**
   * 화면 안에서 이 그리드의 이름 — 개인화의 키다(frontend/grid.md).
   *
   * <p>마스터-디테일이면 둘이 다른 이름을 가져야 한다. 같으면 <b>위아래의 설정이 서로를
   * 덮는다</b>. 기본값은 {@code 'main'} 이다.
   */
  gridId?: string;
  /**
   * 쪽수 콤보의 후보 — 안 주면 {@code DEFAULT_PAGE_SIZES}(100 · 500 · 1000).
   *
   * <p><b>화면마다 다를 수 있다.</b> 행 하나가 무거운 화면(이미지·긴 글)은 100 도 버겁고,
   * 코드표처럼 가벼운 목록은 1000 이 편하다 — 그것을 아는 것은 화면이다.
   *
   * <p><b>첫 조회의 크기는 이것이 아니라 {@code useGridController} 의 {@code pageSize} 다.</b>
   * 둘을 함께 바꿔야 콤보에 없는 값이 골라진 채로 뜨지 않는다.
   */
  pageSizes?: number[];
  /**
   * 커서형 푸터의 건수 뒤에 붙는 한마디 — {@code 최근 1일}(screen/admin/system/log/access/requirements.md).
   *
   * <p>총 건수를 잃은 자리를 메운다. 무엇을 물어서 나온 N건인지가 함께 있어야
   * <i>"전부가 N건"</i> 으로 읽히지 않는다. 오프셋·전량 모드에서는 무시된다.
   */
  footerNote?: string;
  /**
   * ③ 설정 버튼을 낸다 — 기본은 <b>낸다</b>(frontend/grid.md).
   *
   * <p><b>끄는 자리가 있다.</b> 개인화할 것이 없는 그리드가 있기 때문이다 — 컬럼이 몇 개
   * 안 되고 전부 봐야 하는 디테일 그리드가 그렇다(screen/admin/system/code/requirements.md). 버튼만 감추는 것이 아니라
   * <b>저장된 설정을 읽지도 않는다</b>: 바꿀 길이 없는 설정을 화면마다 한 번씩 물어보는
   * 것은 왕복 하나를 그냥 버리는 일이다.
   */
  settings?: boolean;
  /**
   * ①·②·③을 <b>통째로 잠근다</b> — screen/admin/system/user/requirements.md 가 처음 요구했다.
   *
   * <p>{@code collapsed} 와 다른 축이다. 저것은 <i>안 보인다</i> 이고 이것은 <b>보이는데
   * 누를 수 없다</b>이며, 마스터-디테일에서 <b>대상이 없는 탭</b>이 그 자리다(frontend/grid.md) —
   * 탭을 잠그면 <i>"이 사용자에게 역할 탭이 없나"</i> 로 읽히므로 <b>비활성되는 것은
   * 버튼이지 탭이 아니다</b>.
   *
   * <p><b>②③도 함께 잠근다.</b> 대상이 없는데 엑셀을 내려받을 수는 없고, 빈 판의 컬럼
   * 설정을 여는 것도 할 일이 아니다.
   */
  disabled?: boolean;
  /**
   * 제목 뒤에 <b>부모 행의 이름</b>을 붙인다 — frontend/grid.md · screen/admin/system/user/requirements.md(2026-08-31).
   *
   * <p>디테일 그리드는 자기가 <b>누구의 것인지 말하지 못한다</b>. 마스터의 선택 행이 스크롤
   * 밖으로 나가거나 조회조건이 펼쳐지면 지금 보고 있는 것이 누구의 것인지가 화면 어디에도
   * 없고, 그 상태의 저장은 <i>다른 사람의 것을 고친 것</i>이 될 수 있다.
   *
   * <p><b>문자열을 화면이 만들지 않는다</b> — 부모 컨트롤러와 컬럼 이름만 준다.
   *
   * <p><b>단, 부모가 그리드가 아닐 수 있다</b>(2026-08-31 — screen/menu/requirements.md). {@code MENU_TREE} 의
   * 마스터는 <b>트리</b>라 컨트롤러도 {@code GridRow} 도 없다 — 그때는 <b>완성된 글자</b>를
   * 준다({@code TreeGridProps.titleSuffix} 와 같은 모양이다). 가짜 행을 만들어 넘기는 것보다
   * 낫다: 그 행의 {@code state} 와 {@code original} 은 <b>뜻이 없는 값</b>이고, 뜻이 없는
   * 값은 다음 사람이 읽는다.
   */
  titleSuffix?: TitleSuffixSpec | string;
  /**
   * ②가 무엇을 주는가 — <b>선언하지 않으면 프론트 생성 둘</b>이다(playbook/excel-download.md · frontend/grid.md).
   *
   * <p>playbook/excel-download.md 는 갈래를 셋으로 갈랐고 <i>"임의 API 경로도 현재 페이지·전체 라는 이름을
   * 그대로 쓸 수 있다"</i> 고 적었다. 그것이 이 선언이다 — {@code page}·{@code all} 을
   * 주면 <b>이름은 그대로 두고 경로만</b> 서버로 돌아가고, {@code extra} 는 화면 고유
   * 항목을 구분선 아래에 붙인다.
   *
   * <p><b>이 선언에 판(AG Grid)의 어휘가 없다</b>(frontend/grid.md) — URL 과 메시지 키뿐이므로
   * Enterprise 로 갈아타도 화면이 적은 것은 바뀌지 않는다.
   *
   * <h2>{@code false} — <b>②를 아예 세우지 않는다</b></h2>
   *
   * <p>전에는 이 선언이 <i>무엇을 주는가</i> 만 말하고 <b><i>줄 것이 없다</i> 를 말하지
   * 못했다.</b> ②는 {@code api} 와 {@code EXPORT} 권한만 보고 섰으므로, 내려받기를 막으려면
   * {@code api} 를 통째로 빼는 수밖에 없었고 그러면 저장 버튼도 함께 사라졌다.
   *
   * <p><b>메뉴의 {@code EXPORT_YN} 으로 끄는 것과 다른 자리다.</b> 저쪽은 <i>이 메뉴에서
   * 내려받을 수 있나</i> 라 메뉴 하나에 하나뿐인데, 마스터-디테일은 <b>한 메뉴에 그리드가
   * 둘</b>이다 — 하단만 막을 자리가 없었다.
   *
   * <p><b>쓸 자리는 좁다.</b> 개인정보처럼 <i>화면으로 보는 것은 되지만 파일로 나가면 안
   * 되는</i> 표에 쓴다. <b>마스터에 딸린 목록이라는 이유로는 끄지 않는다</b> — 그것은
   * 내려받으면 안 되는 이유가 아니다.
   */
  excel?: ExcelSpec | false;
  /** 접히면 <b>제목 줄만</b> 남는다 — 본문·푸터·버튼이 함께 접힌다(frontend/grid.md). */
  collapsed?: boolean;
  /**
   * 어느 축으로 접혔는가 — <b>{@code SplitPane} 이 넘겨준다</b>(2026-08-31).
   *
   * <p>좌우로 접히면 남는 것이 <b>48px 짜리 세로 띠</b>라 가로 제목은 그 자리에서 잘린다.
   * 그때 제목을 <b>세로로 세운다</b>({@code writing-mode}) — 글자를 줄여 넣거나 말줄임표로
   * 끝내면 <i>무슨 그리드를 접었는지</i> 를 알 수 없고, 그것이 되돌리려고 누를 대상의 이름이다.
   *
   * <p>화면이 적지 않는다 — 축은 {@code SplitPane} 의 선언이며 그 콜백이 그대로 넘긴다.
   */
  collapsedAxis?: SplitAxis;
  /** 제목 줄 클릭으로 되돌린다. */
  onExpand?: () => void;
}

export function DataGrid<T>(props: DataGridProps<T>) {
  const { controller, deletable, menuCd } = props;
  const { token } = theme.useToken();
  const dark = useResolvedDark();
  const permissions = useAuthStore((state) => state.permissions);
  const gridApi = useRef<AgGridApi<GridRow<T>> | null>(null);
  const [shown, setShown] = useState(0);
  const settings = useGridSettings();

  useEffect(ensureGridStyles, []);

  const strikeField = typeof deletable === 'object' ? deletable.strikeField : undefined;

  const editable = controller.canEdit;
  // 상태 컬럼은 <b>편집이 열리면</b> 그린다 — 저장 단위인가와 다른 축이다(frontend/grid.md).
  const showState = controller.canEdit;
  const stateful = controller.canSave;
  const serverSort = controller.mode !== 'all';
  const multiSelect = props.selection === 'multiple';
  const [settingsOpen, setSettingsOpen] = useState(false);
  /** 펼쳐 읽는 중인 칸 — {@code viewer} 컬럼의 더블클릭이 채운다(frontend/grid.md). */
  const [viewing, setViewing] = useState<{ title: string; text: string } | null>(null);
  /**
   * 사람이 끌어 바꾼 폭 — <b>선언보다 세다</b>.
   *
   * <p>판은 새 {@code columnDefs} 를 받으면 거기 적힌 폭으로 되돌린다. 우리 선언은 폭을
   * 늘 담고 있으므로, 선언이 다시 계산되는 순간 <b>사용자가 끌어 놓은 폭이 사라진다</b>
   * — 그 계산은 검증 실패가 붙거나 설정 창에서 컬럼을 바꿔도 일어난다.
   *
   * <p><b>ref 가 아니라 상태다.</b> 선언이 이 폭을 담아야 하므로(아래 {@code colDefs})
   * 읽는 자리가 렌더 안이다. 끌기가 <b>끝났을 때 한 번</b>만 바뀌고, 값이 그대로면 같은
   * 객체를 돌려주므로 리액트가 렌더를 건너뛴다 — 끌리는 동안 상태가 뛰지 않는다.
   */
  const [widths, setWidths] = useState<Record<string, number>>({});
  /** 지금 화면이 보고 있는 행 — 선택이 거부됐을 때 되돌릴 자리다. */
  const selectedRowId = useRef<string | null>(null);
  /** 되돌리는 중인가 — 우리가 만든 선택 이벤트를 화면에 다시 알리지 않는다. */
  const restoring = useRef(false);
  // 저장된 표시·순서·틀고정을 선언에 입힌다 — 그리드가 그리는 것은 이쪽이다(frontend/grid.md).
  const settingsEnabled = props.settings !== false;
  const pref = useGridPref(
    menuCd,
    props.gridId ?? 'main',
    controller.columns,
    settingsEnabled,
  );
  // `mode: 'all'` 이면 page 가 언제나 0 이라 그대로 0 이 된다 — 예외를 두지 않는다.
  const rowOffset = controller.page * controller.size;

  /*
    공통코드에서 후보를 받는 컬럼 — sys/code.md. 판에 <b>닿기 전에</b> 채우므로 배지·셀 에디터·
    컬럼 필터가 저마다 묻지 않고 <b>같은 목록</b>을 본다.
  */
  const codeOptions = useCodeOptions(
    pref.columns.map((column) => column.grpCd).filter((grpCd) => grpCd !== undefined),
  );
  const columns = useMemo(
    () =>
      pref.columns.map((column) =>
        // 선언이 적은 후보가 이긴다 — 소스가 값을 갖는 코드가 그쪽이다(sys/code.md).
        column.grpCd && !column.options
          ? { ...column, options: codeOptions[column.grpCd] ?? [] }
          : column),
    [pref.columns, codeOptions],
  );

  /**
   * 공통코드에서 후보를 받는 컬럼의 id — 후보가 <b>늦게 도착했을 때</b> 다시 그릴 대상이다.
   *
   * <p>전체를 새로 고치지 않는다. 그러면 코드 칸이 없는 그리드까지 선언이 바뀔 때마다
   * 셀을 통째로 다시 만든다.
   */
  const codeColumnIds = useMemo(
    () => pref.columns.filter((column) => column.grpCd).map((column) => column.field),
    [pref.columns],
  );

  /*
    헤더 글자를 재는 글꼴 — frontend/grid.md · `headerWidth`. <b>판이 실제로 쓰는 값</b>이어야 한다.

    <b>굵기가 본문의 것이 아니다</b>(2026-08-31 — 고쳤다). 판의 {@code headerFontWeight} 는
    <b>500 고정</b>이며 우리가 먹이는 {@code fontWeight} 는 <i>본문</i>의 것이다(그 값이
    {@code ref: 'fontWeight'} 가 아니라 리터럴이다 — ag-grid 36). 400 으로 재면 <b>실제보다
    좁게</b> 나오고, 그만큼 헤더가 다시 잘린다.

    크기는 {@code headerFontSize: { ref: 'fontSize' }} 라 그리드 글자 크기와 같다.
  */
  const headerFont = useMemo(
    () => `${String(AG_HEADER_FONT_WEIGHT)} ${String(settings.gridFontSize)}px `
      + `${settings.fontFamily ?? token.fontFamily}`,
    [settings.gridFontSize, settings.fontFamily, token.fontFamily],
  );

  /*
    `Shift` 범위 선택의 앵커 — <b>손이 어디까지 왔나</b> 이지 화면 상태가 아니다.

    바뀌어도 다시 그릴 것이 없으므로(칠한 결과는 판의 선택 상태가 그린다) 상태로 두지
    않는다. {@code useState} 의 초기화 함수를 쓰는 것은 <b>한 번만 만들어지는 것이
    보장</b>되기 때문이고, 렌더 중에 읽어도 되는 값이라 ref 가 아니다.
  */
  const [selectAnchor] = useState(newAnchor);

  const colDefs = useMemo(
    () =>
      toColDefs<T>({
        columns,
        headerFont,
        // 참조 팝업의 조회가 이 메뉴로 인가된다 — 팝업은 자기 메뉴가 없다(playbook/INDEX.md).
        menuCd,
        
        onEdit: controller.edit,
        onEditDenied: controller.denyEdit,
        onInvalidEdit: controller.warnInvalid,
        // 삭제는 <b>저장 단위</b>의 것이다 — 남의 저장에 실리는 그리드는 행을 지우지 않는다.
        onToggleDelete: deletable && stateful ? controller.toggleDelete : undefined,
        deleteColumn: typeof deletable === 'object' ? deletable : undefined,
        onRevert: controller.revert,
        issues: controller.issues,
        serverSort,
        editable,
        showState,
        multiSelect,
      })
        /*
          <b>사람이 끈 폭을 여기서 이미 입힌다</b> — 되돌린 뒤에 고치지 않는다.

          아래 {@code applyColumnState} 도 같은 폭을 되돌려 주지만 그것은 판이 <i>선언의
          폭으로 한 번 그린 다음</i> 이다. 선언이 다시 계산될 때마다 <b>원래 폭 → 끈 폭</b>
          이 눈에 보이고, 그 계산이 초당 한 번이면 컬럼이 <b>쉬지 않고 떤다</b>
          (2026-08-30 실물). 판에 넘기는 선언 자체가 끈 폭을 담고 있으면 되돌릴 것이 없다.

          {@code flex} 를 함께 지운다 — 남는 폭을 먹던 칸을 사용자가 직접 끌었다면
          <b>그 순간부터는 사용자가 정한다</b>. 남겨 두면 다음 계산이 다시 늘린다.
        */
        .map((def) => {
          const width = widths[String(def.colId)];
          return width === undefined ? def : { ...def, width, flex: undefined };
        }),
    [columns, controller.edit, controller.denyEdit, controller.warnInvalid,
      controller.toggleDelete,
      controller.revert, controller.issues, deletable, editable, serverSort, stateful,
      showState, multiSelect, widths, headerFont, menuCd],
  );

  // 축은 설정이 갖고 조립은 `themeTokens` 한 곳이다 — frontend/tabs.md. 색과 글꼴은 **antd 가 이미
  // 계산한 값**을 그대로 받는다: 그러지 않으면 프리셋을 안 고른 사용자에게 판의 기본
  // 파랑과 antd 의 기본 파랑이 미묘하게 갈리고, 면 색은 다크에서 아예 어긋난다(frontend/tabs.md).
  const agTheme = useMemo(
    () =>
      agGridTheme(
        { ...settings, dark },
        {
          accentColor: token.colorPrimary,
          backgroundColor: token.colorBgContainer,
          foregroundColor: token.colorText,
          fontFamily: token.fontFamily,
        },
      ),
    [
      settings,
      dark,
      token.colorPrimary,
      token.colorBgContainer,
      token.colorText,
      token.fontFamily,
    ],
  );

  const onSortChanged = useCallback(
    (event: SortChangedEvent<GridRow<T>>) => {
      if (!serverSort) return;
      const sorts = event.api
        .getColumnState()
        .filter((state) => state.sort)
        .sort((a, b) => (a.sortIndex ?? 0) - (b.sortIndex ?? 0))
        .map((state) => ({ field: state.colId, dir: state.sort as 'asc' | 'desc' }));
      void controller.changeSorts(sorts);
    },
    [controller, serverSort],
  );

  /**
   * <b>빈 문구가 바뀌면 오버레이를 다시 세운다</b> — frontend/grid.md · screen/admin/system/log/access/requirements.md · screen/admin/system/batch/schedule/requirements.md.
   *
   * <p>세 화면이 <b>같은 빈 판에 다른 문구</b>를 낸다: *"역할을 선택하세요"* 와 *"저장 후
   * 조회할 수 있습니다"*, *"이 기간에 실패한 배치가 없습니다"* 와 *"조회 결과가 없습니다"*.
   * 그 구분이 <b>진단</b>이라 문구가 안 바뀌면 화면이 <i>묻지 않은 것</i>을 말한다.
   *
   * <p>판은 {@code noRowsOverlayComponent} 를 <b>보여줄 때 한 번</b> 만들고, 그 뒤로
   * prop 이 바뀌어도 다시 만들지 않는다 — 오버레이가 <b>이미 떠 있는 동안</b>의 변경이
   * 그대로 묻혔다(2026-08-30 실측: 신규 행을 골라도 하단이 *"선택하세요"* 로 남았다).
   * 숨겼다 다시 보이는 것이 <b>다시 만드는 유일한 수단</b>이다.
   *
   * <h2>세운 것은 <b>우리가 내려야 한다</b></h2>
   *
   * <p>판은 {@code showNoRowsOverlay()} 를 <b>사람이 강제한 것</b>으로 기억하고
   * ({@code userForcedNoRows}), 그 뒤로는 {@code rowDataUpdated}·{@code modelUpdated} 에서
   * 오버레이를 <b>다시 판정하지 않는다</b> — {@code hideOverlay()} 를 부를 때까지다
   * (ag-grid 36 의 {@code OverlayService}).
   *
   * <p>그래서 <b>행이 왔는데 빈 판이 남는다</b>. 대개는 조회의 {@code loading} 이 true↔false
   * 로 흔들리면서 판이 그 자리에서 다시 판정해 조용히 지나갔는데, <b>{@code read} 가 없는
   * 그리드</b>는 그 흔들림이 아예 없다 — 개인 예외 목록(screen/admin/system/user/requirements.md)이 그것이고, <i>"이 사용자에게
   * 개인 예외가 없습니다"</i> 가 <b>행 위에 그대로 남았다</b>(2026-08-31 실물).
   *
   * <p><b>우리가 세운 것만 내린다.</b> 필터가 전부를 걸러 판이 스스로 띄운 <i>일치하는 행이
   * 없다</i> 는 다른 오버레이이고(frontend/grid.md), 그것을 여기서 내리면 <b>필터가 걸린 그리드가
   * 아무 말도 하지 않는다</b>.
   */
  const forcedEmpty = useRef(false);
  useEffect(() => {
    const api = gridApi.current;
    if (!api || controller.loading) return;

    if (controller.rows.length === 0) {
      api.hideOverlay();
      api.showNoRowsOverlay();
      forcedEmpty.current = true;
      return;
    }
    if (!forcedEmpty.current) return;
    forcedEmpty.current = false;
    api.hideOverlay();
  }, [props.emptyText, controller.rows.length, controller.loading]);

  const onGridReady = useCallback(
    (event: GridReadyEvent<GridRow<T>>) => {
      gridApi.current = event.api;
      // 판의 필터를 지우는 길을 컨트롤러에 걸어 둔다 — 검색 폼의 초기화가 그것을 부른다.
      controller.registerFilterReset(() => event.api.setFilterModel(null));
    },
    [controller],
  );

  const refreshShown = useCallback(() => {
    setShown(gridApi.current?.getDisplayedRowCount() ?? 0);
  }, []);

  useEffect(refreshShown, [controller.rows, refreshShown]);

  /**
   * 선언이 바뀌면 <b>판의 컬럼 상태를 그것에 맞춘다</b> — frontend/grid.md.
   *
   * <p>새 {@code columnDefs} 를 넘기는 것만으로는 모자랐다. 판은 사용자가 UI 로 만든 상태
   * (순서·표시·고정)를 <b>선언보다 우선</b>해 들고 있어서, 설정 창에서 바꾼 것이 화면에
   * 나타나지 않았다(2026-08-30 실물).
   *
   * <p><b>상태를 {@code colDefs} 에서 그대로 만든다.</b> 시스템 컬럼(번호·선택·상태·삭제)이
   * 이미 그 배열에 올바른 자리로 들어 있으므로, 여기서 그 id 를 다시 나열하면 <b>같은
   * 지식이 두 곳</b>에 생긴다.
   *
   * <p><b>정렬은 담지 않는다</b> — 담지 않은 속성은 판이 그대로 두며, 정렬은 헤더가 가진
   * 상태다.
   *
   * <p><b>폭도 담는다 — 사람이 끈 것만.</b> 다만 여기는 <b>둘째 층</b>이다: 선언이 이미
   * 그 폭을 담고 있으므로(위 {@code colDefs}) 보통은 같은 값을 다시 적는 셈이고, 판이
   * 어떤 경로로 선언의 폭을 놓쳤을 때만 실제로 되돌린다. <b>여기서만 되돌리면 폭이
   * 떤다</b> — 판이 선언대로 한 번 그린 <i>뒤</i> 고치는 자리라, 선언이 초당 한 번
   * 계산되는 화면에서는 그 두 걸음이 그대로 보인다(2026-08-30 실물).
   */
  useEffect(() => {
    gridApi.current?.applyColumnState({
      state: colDefs.map((def) => {
        const colId = String(def.colId);
        const width = widths[colId];
        return {
          colId,
          hide: def.hide === true,
          pinned: def.pinned === 'left' || def.pinned === 'right' ? def.pinned : null,
          // 사람이 끈 폭은 <b>선언을 이긴다</b> — 선언이 이미 담고 있으니 대개 같은 값이다.
          // {@code flex} 를 함께 끄지 않으면 다음 계산이 이 칸을 다시 늘린다.
          ...(width === undefined ? {} : { width, flex: null }),
        };
      }),
      applyOrder: true,
    });
  }, [colDefs, widths]);

  /**
   * 페이지가 바뀌면 번호를 다시 계산시킨다 — frontend/grid.md.
   *
   * <p>{@code context} 만 갈아 끼우면 판이 이미 그려 둔 셀을 다시 묻지 않는다. 행과 페이지
   * <b>둘 다 정해진 뒤</b> 부르는 것이 요점이며, 그래서 의존성이 둘이다.
   */
  useEffect(() => {
    gridApi.current?.refreshCells({ columns: [ROW_NO_COL_ID], force: true });
  }, [rowOffset, controller.rows]);

  /**
   * <b>행을 추가하면 첫 입력 칸이 열린다</b> — frontend/grid.md(2026-08-31).
   *
   * <p>행을 더한 다음에 사람이 하는 일은 <b>언제나 첫 칸을 채우는 것</b>이다. 그것을 클릭
   * 두 번(행 선택 · 셀 더블클릭)으로 두면 <i>행 추가</i> 가 세 동작이 되고, 새 행이 판
   * 위쪽에 붙으므로(frontend/grid.md) 스크롤이 아래에 있으면 <b>추가된 행이 보이지도 않는다</b>.
   *
   * <p><b>편집기를 여는 것은 일반 텍스트 칸일 때만이다.</b> 나머지는 <b>포커스만</b> 준다 —
   * 스위치는 편집 모드가 없고(frontend/grid.md — 한 번 누르면 그것이 값이다), 달력·콤보는 열린 채로
   * 시작하면 <b>판을 덮는 판</b>이 사람이 요청하지도 않은 상태로 떠 있게 된다. 텍스트는
   * 열려 있어도 그냥 빈 칸이라 그 부담이 없고, 바로 타이핑할 수 있다는 이득만 남는다.
   *
   * <p><b>판의 자리를 우리가 세지 않는다</b>(`getRowNode`) — 정렬·필터가 행을 옮겼으면 0번이
   * 그 행이 아니다. 컬럼도 판에게 묻는다: 사용자가 끈 컬럼과 옮긴 순서가 판의 상태이므로
   * (frontend/grid.md) 선언의 첫 컬럼이 <b>화면의 첫 컬럼이 아닐 수 있다</b>.
   */
  useEffect(() => {
    const api = gridApi.current;
    const rowId = controller.added;
    if (!api || rowId === null || !editable) return;

    const node = api.getRowNode(rowId);
    const data = node?.data;
    if (node?.rowIndex === null || node?.rowIndex === undefined || !data) return;

    const first = api
      .getAllDisplayedColumns()
      .map((displayed) => columns.find((column) => column.field === displayed.getColId()))
      .find((column) => column !== undefined && isEditable(column, data.data, { state: 'C' }));
    if (!first) return;

    api.ensureIndexVisible(node.rowIndex);
    api.setFocusedCell(node.rowIndex, first.field);
    if (isPlainText(first)) {
      api.startEditingCell({ rowIndex: node.rowIndex, colKey: first.field });
    }
  }, [controller.added, columns, editable]);

  /**
   * <b>첫 실패 행으로 스크롤하고 그 셀에 포커스를 준다</b> — frontend/grid.md.
   *
   * <p>건수만 알리면 사용자가 <b>157행에서 그 행을 찾아야 한다.</b> 여기까지가 규약이며,
   * <i>필터로 숨겨진 행은 필터를 풀고</i> · <i>꺼진 컬럼은 켜고</i> 이동하는 두 예외는
   * 그 상황을 만드는 화면이 아직 없어 두지 않았다.
   */
  useEffect(() => {
    const first = controller.issues[0];
    const api = gridApi.current;
    if (!first || !api) return;

    const node = api.getRowNode(first.rowId);
    if (node?.rowIndex === null || node?.rowIndex === undefined) return;
    api.ensureIndexVisible(node.rowIndex);
    if (first.field) {
      api.setFocusedCell(node.rowIndex, first.field);
    }
  }, [controller.issues]);

  /**
   * ②가 파일에 실을 컬럼 — <b>지금 보이는 것만, 보이는 순서로</b>다(frontend/grid.md).
   *
   * <p>판에게 묻는다. 사용자가 끈 컬럼과 옮긴 순서가 <b>판의 상태</b>이기 때문이며,
   * 우리 선언만 보면 *"화면에 없던 컬럼이 파일에 있다"* 가 된다. 시스템 컬럼(번호·상태·
   * 삭제)은 우리 선언에 없으므로 자연히 빠진다.
   */
  const excelColumns = useCallback((): ExcelColumn<T>[] => {
    const declared = new Map(pref.columns.map((column) => [column.field, column]));
    const displayed = (gridApi.current?.getAllDisplayedColumns() ?? [])
      .map((column) => declared.get(column.getColId()))
      .filter((column): column is ColumnDef<T> => column !== undefined);

    // 판이 아직 준비되지 않았으면 선언으로 떨어진다 — 파일이 안 나오는 것보다 낫다.
    const source = displayed.length > 0
      ? displayed
      : pref.columns.filter((column) => !column.hidden && column.visible !== false);

    return source.map((column) => ({ column, header: headerText(column) }));
  }, [pref.columns]);

  /** 필터·정렬을 통과한 <b>지금 보이는 행</b>이다 — frontend/grid.md. */
  const excelCurrentPage = useCallback((): T[] => {
    const api = gridApi.current;
    if (!api) return controller.rows.map((row) => row.data);

    const rows: T[] = [];
    api.forEachNodeAfterFilterAndSort((node) => {
      if (node.data) rows.push(node.data.data);
    });
    return rows;
  }, [controller.rows]);

  /**
   * 고른 셀을 클립보드에 — frontend/grid.md.
   *
   * <p><b>판의 클립보드 기능은 Enterprise 다.</b> 우리가 쓰는 것은 Community 이므로
   * (frontend/grid.md) 여기서 직접 한다 — 어차피 <b>보이는 글자</b>를 복사해야 하니 값을 다시
   * 포맷하는 것보다 <b>그려진 셀을 읽는</b> 편이 정확하다.
   *
   * <p><b>키다운을 감싸는 div 에서 듣는다.</b> 셀이 포커스를 갖고 있으므로 이벤트가
   * 여기까지 올라온다 — {@code window} 에서 들으면 <b>화면에 그리드가 둘일 때</b>
   * 둘 다 반응한다.
   */
  const copyFocusedCell = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      const api = gridApi.current;
      if (!api) return;

      const selected = window.getSelection()?.toString() ?? '';
      if (!isCopyShortcut(event, selected, api.getEditingCells().length > 0)) return;

      const cell = api.getFocusedCell();
      const node = cell ? api.getDisplayedRowAtIndex(cell.rowIndex) : null;
      if (!cell || !node) return;

      const colId = cell.column.getColId();
      // 그려진 셀의 글자 — <b>곁들인 배지는 뺀다</b>(frontend/grid.md). 어느 조각이 값이 아닌지는
      // 그린 쪽이 표시해 두었고, 여기서 태그 모양으로 짐작하지 않는다.
      const rendered = renderedText(
        event.currentTarget.querySelector(
          `.ag-row[row-index="${String(cell.rowIndex)}"] .ag-cell[col-id="${colId}"]`,
        ),
      );

      const text = cellText(rendered, api.getCellValue({ rowNode: node, colKey: colId }));
      // 빈 칸을 눌렀으면 클립보드를 비우지 않는다 — 갖고 있던 것을 잃는 쪽이 더 나쁘다.
      if (text === '') return;

      event.preventDefault();
      void navigator.clipboard?.writeText(text);
    },
    [],
  );

  return (
    <div
      className="bo-grid"
      onKeyDown={copyFocusedCell}
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: props.height ?? '100%',
        minHeight: 0,
        // 바깥 테두리도 배경도 두르지 않는다 — AG Grid 가 이미 자기 테두리를 그리므로
        // 제목 줄과 푸터까지 감싸면 **선이 두 겹**으로 보이고(2026-08-29 실물), 배경을
        // 깔면 그 둘이 **본문과 다른 판**처럼 떠 보였다(2026-08-30 실물).
        background: 'transparent',
        // frontend/grid.md — Primary 에서 파생하고 직접 색을 정하지 않는다.
        ['--bo-grid-readonly' as string]: token.colorFillQuaternary,
        ['--bo-grid-required' as string]: token.colorWarningBg,
        ['--bo-grid-changed' as string]: token.colorInfoBg,
        ['--bo-grid-selected' as string]: token.colorPrimaryBg,
        ['--bo-grid-invalid' as string]: token.colorErrorBg,
        ['--bo-grid-invalid-border' as string]: token.colorError,
      }}
    >
      <GridHeader
        {...props}
        permissions={permissions}
        menuCd={menuCd}
        excelColumns={excelColumns}
        excelCurrentPage={excelCurrentPage}
        onSettings={() => setSettingsOpen(true)}
      />

      {/* 접힌 그리드의 설정을 열 이유가 없다 — 버튼이 함께 접혔으므로 열 길도 없다. */}
      {/*
        펼쳐 읽기 — frontend/grid.md. <b>읽기 전용이라 버튼이 <i>닫기</i> 하나</b>다.
        확인/취소를 두면 고칠 것이 있는 줄 알고, 취소가 무엇을 되돌리는지 물어야 한다.
      */}
      <Modal
        open={viewing !== null}
        title={viewing?.title}
        onCancel={() => setViewing(null)}
        footer={[
          <Button key="close" onClick={() => setViewing(null)}>
            {label(MSG.COMMON_CLOSE)}
          </Button>,
        ]}
        width={720}
      >
        <Input.TextArea value={viewing?.text ?? ''} readOnly autoSize={{ minRows: 8, maxRows: 20 }} />
      </Modal>

      <GridSettingsModal<T>
        open={settingsOpen && settingsEnabled}
        title={props.title}
        columns={pref.columns}
        // 초기화가 이것으로 켜지고 꺼진다 — 지울 행이 있는가다(frontend/grid.md).
        saved={pref.saved}
        onApply={pref.apply}
        onKeep={pref.keep}
        onCancel={pref.cancel}
        onSave={pref.save}
        onReset={pref.reset}
        onClose={() => setSettingsOpen(false)}
      />

      {/* 접힌 것과 없는 것은 다르다 — 제목 줄을 남겨야 그 둘이 구분된다(frontend/grid.md). */}
      {props.collapsed ? null : (
      <div style={{ flex: 1, minHeight: 0 }}>
        <AgGridReact<GridRow<T>>
          theme={agTheme}
          /*
            번호 컬럼이 읽는 값 — 선언에 박으면 페이지가 한 장씩 늦는다(columnDefs 참조).

            <b>범위 선택의 앵커도 여기로 간다.</b> 상자 자체는 렌더마다 새로 만들어지는
            이 객체 <i>안</i> 에 있지만 {@code selectAnchor} 는 <b>한 번 만들어진 그것</b>
            이라, 담는 그릇이 바뀌어도 손이 누른 자리는 남는다.
          */
          context={{ rowOffset, selectAnchor } satisfies GridContext}
          localeText={gridLocaleText()}
          /*
            <b>검증 실패의 이유가 툴팁에 있다</b>(frontend/grid.md ④ · `columnDefs` 의
            {@code tooltipValueGetter}). 판의 기본 지연이 <b>2초</b>라 붉은 셀에 마우스를
            올려도 <b>2초를 버텨야</b> 나왔고, 그래서 사실상 없는 문구였다
            (2026-08-31 실물).

            0 으로 두지 않는다 — 셀 사이로 마우스가 지나갈 때마다 툴팁이 <b>따라다닌다</b>.
            겨냥해서 멈춘 것과 지나간 것을 가르는 만큼만 기다린다.
          */
          tooltipShowDelay={300}
          rowData={controller.rows}
          columnDefs={colDefs}
          /*
            <b>설정이 없는 그리드는 컬럼 순서도 사용자의 것이 아니다</b>(2026-09-02 — ⑥이
            요구했다). {@code settings={false}} 는 설정 창과 개인화 저장을 끄는데, 헤더를
            끄는 것은 그대로 열려 있어 <b>판만 아는 순서</b>가 생긴다.

            그 순서가 조용하지 않은 자리가 <b>엑셀 양식</b>이다: 내려받기는 <i>지금 보이는
            것을, 보이는 순서로</i> 싣고(frontend/grid.md) 업로드는 <b>선언한 순서</b>를 기대하므로
            (playbook/INDEX.md — 두 탭의 컬럼이 같다), 한 번 끌어 놓으면 <b>자기가 방금 내려받은
            파일이 양식이 아니게</b> 된다.
          */
          suppressMovableColumns={!settingsEnabled}
          getRowId={(params) => params.data.id}
          onGridReady={onGridReady}
          onSortChanged={onSortChanged}
          /*
            헤더를 끌어 바꾼 순서를 우리 쪽으로 가져온다 — 판만 아는 순서가 생기면
            설정 창이 <b>처음 순서</b>를 보이고, 창에서 바꿔도 판이 자기 것을 고집한다.
          */
          onColumnMoved={(event) => {
            // 사람이 끈 것만 받는다 — 아래 `applyColumnState` 가 낸 이동을 되받으면
            // 상태 → 선언 → 상태로 도는 고리가 된다.
            if (!event.finished || !event.source.startsWith('ui')) return;
            const declared = new Set(pref.columns.map((column) => column.field));
            pref.reorder(
              event.api
                .getAllGridColumns()
                .map((column) => column.getColId())
                .filter((colId) => declared.has(colId)),
            );
          }}
          /*
            <b>사람이 끈 것만 기억한다.</b> 판이 스스로 맞춘 폭(창 크기 변화·flex 재계산)까지
            굳히면, 창을 줄였다 늘렸을 때 그 순간의 폭이 <b>사용자가 고른 값인 것처럼</b>
            눌러앉는다.
          */
          onColumnResized={(event) => {
            if (!event.finished || event.source !== 'uiColumnResized') return;
            setWidths((current) => {
              let next = current;
              event.columns?.forEach((column) => {
                const colId = column.getColId();
                const width = column.getActualWidth();
                if (current[colId] === width) return;
                // 바뀐 것이 없으면 <b>같은 객체</b>를 돌려준다 — 리액트가 렌더를 건너뛴다.
                if (next === current) next = { ...current };
                next[colId] = width;
              });
              return next;
            });
          }}
          /*
            <b>후보가 늦게 오면 이미 그려진 셀은 옛 선언으로 남는다.</b> 판은 바뀐
            {@code valueFormatter}·{@code cellRenderer} 를 <i>다음에 그릴 셀</i> 에만 쓰므로,
            첫 렌더에서 코드값 그대로 나온 칸이 이름으로 바뀌지 않는다(2026-08-30 실물).
            <b>캐시가 빈 첫 진입이 정확히 그 경우</b>다 — 목록이 왕복 하나 뒤에 온다.

            효과(useEffect)가 아니라 <b>이 이벤트</b>인 것이 요점이다. 효과는 판이 새 선언을
            받아들이기 <i>전에</i> 돌 수 있고, 그러면 옛 선언으로 다시 그린 뒤 아무도 다시
            묻지 않는다.
          */
          onNewColumnsLoaded={(event) => {
            if (codeColumnIds.length === 0) return;
            event.api.refreshCells({ columns: codeColumnIds, force: true });
          }}
          onFilterChanged={refreshShown}
          onModelUpdated={refreshShown}
          rowSelection={
            multiSelect
              ? { mode: 'multiRow', checkboxes: false, headerCheckbox: false,
                  enableClickSelection: false }
              : { mode: 'singleRow', checkboxes: false, enableClickSelection: true }
          }
          onSelectionChanged={(event) => {
            if (multiSelect) {
              // 체크박스 셀은 행에 매여 있지만 <b>다른 행을 눌러도</b> 전체 선택이 바뀐다 —
              // 판이 알아서 다시 그리지 않으므로 그 컬럼만 새로 그린다.
              event.api.refreshCells({ force: true, columns: [SELECT_COL_ID] });
              const picked = event.api
                .getSelectedNodes()
                .map((node) => node.data)
                .filter((row) => row !== undefined);
              // 실행의 대상이다 — frontend/grid.md. 화면이 따로 세지 않아도 버튼이 스스로 켜지고 꺼진다.
              controller.setSelected(picked);
              props.onSelectionChange?.(picked);
              return;
            }
            // 우리가 만든 선택 이벤트다 — 화면에 다시 알리면 물음이 두 번 뜬다.
            if (restoring.current) return;

            const next = event.api.getSelectedNodes()[0]?.data ?? null;
            const verdict = props.onSelect?.(next);
            if (verdict === undefined || verdict === true) {
              selectedRowId.current = next?.id ?? null;
              controller.setSelected(next ? [next] : []);
              return;
            }
            void Promise.resolve(verdict).then((allowed) => {
              if (allowed) {
                selectedRowId.current = next?.id ?? null;
                controller.setSelected(next ? [next] : []);
                return;
              }
              /*
                <b>거부 — 판의 선택을 되돌린다.</b> 화면이 거부권을 갖지 못하면 <i>보고 있는
                것</i>과 <i>고른 것</i>이 갈리고, 그 상태로 저장하면 <b>A 그룹의 행이 B 그룹
                으로 나간다</b>(frontend/grid.md).
              */
              restoring.current = true;
              const previous = selectedRowId.current;
              event.api.forEachNode((node) => {
                const want = node.data?.id === previous;
                if (node.isSelected() !== want) node.setSelected(want);
              });
              // 판이 선택 이벤트를 같은 틱에 내는지가 버전에 달려 있다 — 다음 틱에 푼다.
              window.setTimeout(() => {
                restoring.current = false;
              }, 0);
            });
          }}
          /*
            <b>읽기 전용인데 셀에서 다 볼 수 없는 칸</b>을 펼친다 — frontend/grid.md.

            편집기를 여는 대신 여기서 받는 이유는, 편집기가 열리면 <b>고칠 수 있는 것처럼</b>
            보이기 때문이다. 값은 그대로이고 <b>보는 방법만</b> 바뀐다.
          */
          onCellDoubleClicked={(event) => {
            const column = pref.columns.find((candidate) => candidate.field === event.column.getColId());
            if (column?.viewer && event.data) {
              setViewing({
                title: label(column.header),
                text: String(event.value ?? ''),
              });
              return;
            }
            /*
              <b>넘겨주기</b> — screen/menu/requirements.md · screen/admin/system/log/access/requirements.md 가 같은 제스처를 쓴다.

              펼쳐 읽는 칸이 <b>먼저다</b>. 같은 더블 클릭에 뜻이 둘일 수 있는데, 그 컬럼에서
              사람이 기대하는 것은 <i>이 긴 글을 읽겠다</i> 이지 <i>다른 화면으로 가겠다</i> 가
              아니다 — 화면을 여는 것이 이겨 버리면 <b>읽을 방법이 사라진다</b>.

              편집이 열린 그리드에서는 부르지 않는다. 거기서 더블 클릭은 <b>편집기를 여는
              제스처</b>이고(위의 {@code singleClickEdit={false}}) 둘을 겹치면 화면이 열리면서
              편집이 시작된다.
            */
            if (!event.data || editable) return;

            /*
              <b>칸이 행보다 먼저다</b> — screen/admin/system/log/access/requirements.md. 컬럼이 자기 더블 클릭을 선언했으면
              그것이 이긴다: 축이 좁은 쪽이 사람이 실제로 겨눈 것이다. 행으로 흘려보내면
              `요청 URI` 를 노려 누른 것이 <i>이 행을 연다</i> 로 처리된다.
            */
            if (column?.onDoubleClick) {
              column.onDoubleClick(event.data.data);
              return;
            }
            props.onRowDoubleClick?.(event.data);
          }}
          // 더블 클릭으로 진입한다 — switch 만 예외이며 그것은 렌더러가 받는다(frontend/grid.md).
          singleClickEdit={false}
          stopEditingWhenCellsLoseFocus
          rowClassRules={{
            'bo-row-added': (params: RowClassParams<GridRow<T>>) => params.data?.state === 'C',
            'bo-row-updated': (params: RowClassParams<GridRow<T>>) => params.data?.state === 'U',
            /*
              선언이 <b>한 칸만 긋겠다</b> 고 하면 행 전체에는 긋지 않는다 — screen/admin/system/code/requirements.md.
              둘 다 걸면 그 칸만 진하게 그어질 뿐 <i>행이 사라진다</i> 는 인상은 그대로다.
            */
            'bo-row-removed': (params: RowClassParams<GridRow<T>>) =>
              !strikeField && params.data?.state === 'D',
          }}
          loading={controller.loading}
          noRowsOverlayComponent={() => (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={label(props.emptyText ?? MSG.GRID_EMPTY)}
            />
          )}
        />
      </div>
      )}

      {props.collapsed ? null : (
      <GridFooter
        pageSizes={props.pageSizes}
        mode={controller.mode}
        page={controller.page}
        size={controller.size}
        total={controller.total}
        totalPages={controller.totalPages}
        loaded={controller.rows.length}
        shown={shown}
        note={props.footerNote}
        hasNext={controller.hasNext}
        hasPrev={controller.hasPrev}
        onPage={(page) => void controller.goToPage(page)}
        onSize={(size) => void controller.changeSize(size)}
        onNext={() => void controller.goNextPage()}
        onPrev={() => void controller.goPrevPage()}
      />
      )}
    </div>
  );
}

/**
 * 부모 행의 이름 — <b>선언한 컬럼을 슬래시로 잇는다</b>(frontend/grid.md의 `titleSuffix`).
 *
 * <p><b>빈 칸은 빠진다.</b> 미저장 신규 행처럼 아직 값이 없는 경우가 있고, 그때 `/` 만
 * 남으면 그것이 값처럼 보인다. 고른 행이 없거나 값이 하나도 없으면 <b>빈 문자열</b>이며
 * 그때는 아무것도 그리지 않는다 — <i>(없음)</i> 같은 자리표를 만들지 않는다.
 *
 * <p><b>여럿을 고르면 첫 행이다.</b> 다중 선택 그리드가 부모인 경우이며, 그 상태에서
 * 디테일이 무엇을 보여주는지는 <b>화면이 정하는 일</b>이라 여기서 건수를 세지 않는다.
 */
function titleSuffixOf(spec: TitleSuffixSpec | string | undefined): string {
  // 트리가 부모인 경우 — 이미 완성된 글자다(위 {@code titleSuffix} 의 주석).
  if (typeof spec === 'string') return spec;
  const row = spec?.parent.selected[0];
  if (!spec || !row) return '';

  const data = row.data as Record<string, unknown>;
  return spec.fields
    .map((field) => data[field])
    .filter((value) => value !== null && value !== undefined && value !== '')
    .map((value) => String(value))
    .join(' / ');
}

/**
 * 이 칸이 <b>일반 텍스트</b>인가 — 행 추가가 편집기를 여는 조건이다(frontend/grid.md).
 *
 * <p>타입이 `text` 이고 편집기가 기본인 칸이다. 숫자·날짜·콤보·스위치·긴 글은 <b>제 편집기가
 * 따로</b> 있고, 그것을 <i>사람이 열라고 하지 않았는데</i> 띄우면 판 위에 판이 덮인다.
 */
function isPlainText<T>(column: ColumnDef<T>): boolean {
  const type = column.type ?? 'text';
  const editor = column.editor ?? 'text';
  return type === 'text' && editor === 'text';
}

/** 그리드가 보는 설정만 추린다 — frontend/tabs.md 의 경계에서 <b>전역 표시 속성</b> 쪽이다. */
function useGridSettings() {
  const primaryColor = useUiStore((state) => state.primaryColor);
  const colorTone = useUiStore((state) => state.colorTone);
  const bgDensity = useUiStore((state) => state.bgDensity);
  const compact = useUiStore((state) => state.compact);
  const fontFamily = useUiStore((state) => state.fontFamily);
  const fontSize = useUiStore((state) => state.fontSize);
  const fontWeight = useUiStore((state) => state.fontWeight);
  const gridHeaderHeight = useUiStore((state) => state.gridHeaderHeight);
  const gridRowHeight = useUiStore((state) => state.gridRowHeight);
  const gridFontSize = useUiStore((state) => state.gridFontSize);

  return useMemo(
    () => ({
      primaryColor,
      colorTone,
      bgDensity,
      compact,
      fontFamily,
      fontSize,
      fontWeight,
      gridHeaderHeight,
      gridRowHeight,
      gridFontSize,
    }),
    [
      primaryColor,
      colorTone,
      bgDensity,
      compact,
      fontFamily,
      fontSize,
      fontWeight,
      gridHeaderHeight,
      gridRowHeight,
      gridFontSize,
    ],
  );
}

/**
 * 컨트롤 하나 — frontend/grid.md.
 *
 * <p><b>비활성이면 사유를 붙인다</b>(screen/admin/system/code/requirements.md). 눌리지 않는 버튼은 눌러도 아무 일이 없고
 * 스스로 이유를 말하지 못한다 — frontend/grid.md 이 <i>행 추가</i>에 대해 *"비활성으로 막지 말고
 * 눌렀을 때 이유를 말하라"* 고 정한 것과 방향이 갈리는 자리이며, 여기는 <b>막는 것 자체가
 * 신호</b>여야 한다: 정의를 고치면 하단 재조회가 미저장 편집을 버린다.
 *
 * <p><b>{@code locked} 에는 사유를 붙이지 않는다</b> — 조회 중인 것은 곧 풀리고, 그 짧은
 * 사이의 툴팁은 설명이 아니라 잡음이다.
 */
function renderControl(control: GridControl, locked: boolean) {
  /*
    <b>`select` 는 툴팁을 쓰지 않는다.</b> 비활성 버튼은 눌러도 아무 일이 없어 사유를
    말할 통로가 없지만, 셀렉트는 <b>지금 고른 값이 곧 사유</b>다 — 무엇을 보고 있는지가
    칸에 적혀 있다(frontend/grid.md).
  */
  if (control.type === 'select') {
    return (
      <Space key={control.key} size={4}>
        {control.showLabel !== false && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {label(control.label)}
          </Typography.Text>
        )}
        <Select
          size="middle"
          style={{ width: control.width ?? 140 }}
          disabled={locked || control.disabled}
          value={control.value}
          onChange={control.onChange}
          options={control.options.map((option) => ({
            value: option.value,
            label: option.text,
          }))}
        />
      </Space>
    );
  }

  /*
    켜고 끄는 것 — playbook/INDEX.md. <b>셀렉트와 같은 자리</b>이고 툴팁을 쓰지 않는 이유도 같다:
    지금 어느 쪽인지가 <b>컨트롤에 그려져</b> 있다.

    <b>{@code ynFlag} 셀과 같은 스위치다</b>(frontend/grid.md) — 값이 둘인 것은 화면 어디에 있든
    같은 모양이어야 하고, 헤더에서만 체크박스를 쓰면 <i>이 둘은 다른 것인가</i> 가 된다.
    폭을 넓히지 않는 것은 여기 <b>라벨이 옆에 붙기</b> 때문이다 — 셀에서 폭을 키운 근거가
    *손잡이 위치만이 신호* 였는데(SWITCH_WIDTH), 라벨이 있으면 그 신호가 혼자가 아니다.
  */
  if (control.type === 'toggle') {
    return (
      <Space key={control.key} size={4}>
        {control.showLabel !== false && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {label(control.label)}
          </Typography.Text>
        )}
        <Switch
          size="small"
          checked={control.value}
          disabled={locked || control.disabled}
          // 판의 {@code onChange} 는 {@code (checked, event)} 둘을 준다 — 선언한 시그니처는
          // 하나이므로 여기서 자른다. 그대로 넘기면 화면의 콜백이 <b>받은 적 없는 둘째
          // 인자</b>를 받고, 그것이 상태 설정 함수면 앞으로 어떤 오버로드에 걸릴지 모른다.
          onChange={(checked) => control.onChange(checked)}
        />
      </Space>
    );
  }

  const button = (
    <Button
      key={control.key}
      disabled={locked || control.disabled}
      onClick={control.onClick}
    >
      {label(control.label)}
    </Button>
  );
  return control.disabled && control.hint && !locked ? (
    <Tooltip key={control.key} title={label(control.hint)}>
      {/* 비활성 버튼은 마우스 이벤트를 내지 않는다 — 툴팁이 붙을 자리를 감싸 준다. */}
      <span style={{ display: 'inline-block' }}>{button}</span>
    </Tooltip>
  ) : (
    button
  );
}

/**
 * ①·Ⓐ·Ⓑ — frontend/grid.md 의 배열 하나.
 *
 * <p>순서는 <b>조회 → 행 추가 → 저장</b> 이다. 행 추가가 저장 앞인 것은 일의 순서다.
 * 모든 컨트롤이 같은 상태({@code inFlight})를 본다 — 그것이 선언형으로 닫는 값어치다.
 */
function GridHeader<T>(
  props: DataGridProps<T> & {
    permissions: string[];
    menuCd: string;
    excelColumns: () => ExcelColumn<T>[];
    excelCurrentPage: () => T[];
    onSettings: () => void;
  },
) {
  const { controller, controls = [], permissions, menuCd } = props;
  const { token } = theme.useToken();
  const excelMaxRows = useAuthStore((state) => state.excelMaxRows);
  // 조회 중이거나 <b>대상이 없다</b> — screen/admin/system/user/requirements.md. 둘 다 "지금은 아무것도 누르지 말라" 다.
  const locked = controller.loading || props.disabled === true;
  const collapsed = props.collapsed === true;
  /*
    <b>세로로 서는 것은 좌우로 접혔을 때뿐이다</b>(frontend/grid.md). 상하로 접히면 제목 줄이 그대로
    남으므로 가로가 맞다 — 축이 그것을 정한다.
  */
  const upright = collapsed && props.collapsedAxis === 'horizontal';
  const suffix = titleSuffixOf(props.titleSuffix);

  const visibleControls = controls.filter(
    (control) =>
      !control.actionCd || hasPermission(permissions, control.menuCd ?? menuCd, control.actionCd),
  );

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
        // 위아래 4px 을 더 준다 — 버튼이 그리드 선에 붙어 보였다(2026-08-30 실물).
        padding: '8px 4px',
        /*
          버튼이 없어도 높이가 같다 — 하단 그리드처럼 버튼이 하나도 없는 그리드는 제목
          줄만큼만 높아져 <b>같은 화면의 그리드 둘이 다른 크롬</b>을 갖게 됐다
          (2026-08-30 실물). 버튼 한 줄의 높이를 바닥값으로 둔다.
        */
        minHeight: token.controlHeight + 16,
        // 세로 띠에서는 <b>제목이 위에서 시작</b>한다 — 가운데 정렬은 띠가 길수록 어색하다.
        ...(upright ? { flexDirection: 'column', height: '100%', paddingInline: 0 } : {}),
      }}
    >
      <Space size={6} style={{ minWidth: 0 }} orientation={upright ? 'vertical' : 'horizontal'}>
        <Typography.Text
          strong
          style={{
            cursor: collapsed ? 'pointer' : 'default',
            /*
              <b>제목은 줄어들지 않는다.</b> 좁은 pane 에서 툴바와 접미가 자리를 다 가져가면
              제목 상자가 0 으로 눌리고, 그러면 글자가 <b>한 자씩 세로로</b> 쌓여 접힘의
              세로쓰기와 구별되지 않는다 — playbook/INDEX.md 의 좌우 분할이 그것을 처음 만났다.

              접힌 띠에서는 반대다: 거기서는 세로로 <b>흘러야</b> 하므로 이 규칙을 끈다.
            */
            ...(upright ? {} : { flex: '0 0 auto', whiteSpace: 'nowrap' }),
            /*
              <b>세로쓰기다 — 돌린 것이 아니다.</b> {@code rotate} 로 눕히면 자리를 잡는 것이
              변환 전 상자라 띠 밖으로 삐져나가고, 한글이 <b>옆으로 누워</b> 읽기 어렵다.
              {@code vertical-rl} 은 한글을 <b>세운 채</b> 위에서 아래로 흘린다.

              <b>{@code inline-block} 이 함께 있어야 한다</b> — 판이 제목을 {@code span} 으로
              그리는데 {@code writing-mode} 는 <b>인라인 상자에서 무시된다</b>(CSS Writing
              Modes §3.1). 그것만으로는 아무 일도 일어나지 않는다.
            */
            ...(upright ? { display: 'inline-block', writingMode: 'vertical-rl' } : {}),
          }}
          onClick={collapsed ? props.onExpand : undefined}
        >
          {label(props.title)}
        </Typography.Text>
        {/*
          <b>부모 행의 이름</b> — frontend/grid.md. 제목과 굵기·색이 갈린다: 이것은 제목이 아니라
          <i>지금 무엇을 보고 있나</i> 이고, 같은 굵기로 두면 그리드 이름이 행마다 바뀌는
          것처럼 보인다. 고른 행이 없으면 <b>아무것도 붙지 않는다</b>.
        */}
        {suffix !== '' && (
          <Typography.Text type="secondary" ellipsis>
            {suffix}
          </Typography.Text>
        )}
      </Space>

      {/* 보이지 않는 그리드의 저장 버튼을 누를 이유가 없다 — 버튼도 함께 접힌다. */}
      {collapsed ? null : (
      <Space size={8}>
        {visibleControls
          .filter((control) => control.slot === 'beforeRead')
          .map((control) => renderControl(control, locked))}

        {/*
          조회의 원천이 둘이다 — frontend/grid.md. 보통은 `api.read` 선언이고, 행이 다른 조회에서
          오는 그리드는 화면이 `onRead` 로 건다. **버튼은 하나이며 자리도 하나다.**
        */}
        {props.onRead ? (
          <Button icon={<SearchOutlined />} disabled={locked} onClick={props.onRead}>
            {label(MSG.GRID_READ)}
          </Button>
        ) : (
          controller.api?.read &&
          controller.canRead && (
            <Button
              icon={<SearchOutlined />}
              disabled={locked}
              onClick={() => void controller.read()}
            >
              {label(MSG.GRID_READ)}
            </Button>
          )
        )}
        {controller.canAddRow && (
          <Button icon={<PlusOutlined />} disabled={locked} onClick={controller.addRow}>
            {label(MSG.GRID_ADD_ROW)}
          </Button>
        )}
        {controller.canSave && controller.savesItself && (
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={locked}
            onClick={() => void controller.save()}
          >
            {label(MSG.GRID_SAVE)}
          </Button>
        )}

        {/*
          ① 의 넷째 — <b>선언 순서가 곧 화면 순서</b>다(frontend/grid.md).

          <b>권한을 손으로 두르지 않는다.</b> 버튼과 URL 이 한 선언에 있으므로 판정에
          필요한 것이 이미 다 있고(`actionOfUrl` — backend/api.md), 손으로 적게 하면 <b>버튼을
          추가하고 권한을 빠뜨리는</b> 경로가 열린다. 없으면 <b>감춘다</b>(frontend/tabs.md).

          <b>대상이 없으면 비활성이다</b>(frontend/grid.md) — 눌렀는데 *"선택하세요"* 가 뜨는 것보다
          누를 수 없는 것이 먼저 보이는 편이 낫다. 행 추가가 <i>눌렀을 때 이유를 말하는</i>
          것과 갈리는 이유는, 여기서는 <b>무엇이 모자란지가 화면에 이미 보이기</b> 때문이다.
        */}
        {(controller.api?.actions ?? [])
          .filter((action) => hasPermission(permissions, menuCd, actionOfUrl(action.url)))
          .map((action) => {
            const target = action.target ?? 'selected';
            /*
              <b>컨트롤러와 같은 함수로 센다</b>. 여기서만 따로 세면 <i>버튼은 켜졌는데
              눌러도 아무 일이 없는</i> 상태가 생기고, 그 버튼은 이유도 말하지 못한다.
            */
            const count = actionTargets(
              action, target, controller.selected, controller.rows,
            ).length;
            /*
              <b>{@code 'all'} 도 0건이면 끈다</b>(2026-09-02). 그 풀도 {@code eligible} 로
              걸러지므로 <b>조회 결과에 대상이 하나도 없는</b> 상태가 실재하고, 켜 둔 채
              누르면 서버가 400 으로 답한다 — 그 문구는 <i>입력값을 확인하세요</i> 이며
              사용자가 고칠 입력이 아무 데도 없다. 끄면 아래 {@code hint} 가 <b>왜 없는지</b>
              를 말한다.

              {@code 'none'} 은 그대로 켜 둔다 — 그 실행의 대상은 화면이 든 행이 아니다.
            */
            const off = locked || (target !== 'none' && count === 0);
            const button = (
              <Button
                key={action.key}
                disabled={off}
                onClick={() => void controller.runAction(action.key)}
              >
                {label(action.label)}
              </Button>
            );
            /*
              <b>사유를 붙인다</b>(screen/admin/system/user/requirements.md). 대상 제약으로 막힌 실행은 <i>선택이 0건</i>
              과 겉모습이 같아 <b>고를 것이 없어서인지 고를 수 없어서인지</b> 가 갈리지
              않는다 — 비활성 버튼은 마우스 이벤트를 내지 않으므로 자리를 감싸 준다.
            */
            return off && action.hint && !locked ? (
              <Tooltip key={action.key} title={label(action.hint)}>
                <span style={{ display: 'inline-block' }}>{button}</span>
              </Tooltip>
            ) : (
              button
            );
          })}

        {visibleControls
          .filter((control) => control.slot === 'beforeTools')
          .map((control) => renderControl(control, locked))}

        {/*
          ② — 도구는 언제나 맨 오른쪽이다(frontend/grid.md). 권한이 없으면 아예 없다(frontend/tabs.md).

          <b>{@code read} 가 아니라 {@code api} 를 본다</b>(2026-08-30 — screen/admin/system/user/requirements.md). 조회를
          다른 응답이 먹이는 저장 그리드가 생겼고(개인 예외 목록), 그 그리드도 내려받을
          것이 있다 — {@code fetchAll} 이 그때 <b>화면이 든 행 전부</b>를 돌려준다.
          {@code api} 가 아예 없는 그리드는 여전히 ②가 없고, <b>{@code excel={false}} 로
          명시해 끈 그리드</b>도 없다 — 뒤쪽이 2026-09-05 에 생겼다.
        */}
        {props.excel !== false && controller.api && hasPermission(permissions, menuCd, 'EXPORT') && (
          <ExcelButton<T>
            fileName={label(props.title)}
            columns={props.excelColumns}
            currentPage={props.excelCurrentPage}
            all={() => controller.fetchAll(excelMaxRows)}
            spec={props.excel}
            permissions={permissions}
            menuCd={menuCd}
            disabled={locked}
          />
        )}

        {/*
          ③ — <b>권한이 없다</b>(frontend/grid.md). 내 화면을 내가 어떻게 보는가는 업무 권한과
          무관하며, 액션을 붙이면 메뉴마다 하나씩 더 만들어야 한다.
        */}
        {props.settings !== false && (
          <Tooltip title={label(MSG.GRID_SETTINGS)}>
            <Button
              icon={<SettingOutlined />}
              aria-label={label(MSG.GRID_SETTINGS)}
              disabled={locked}
              onClick={props.onSettings}
            />
          </Tooltip>
        )}
      </Space>
      )}
    </div>
  );
}
