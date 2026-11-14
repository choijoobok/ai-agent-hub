import {
  createElement,
  Fragment,
  useCallback,
  useMemo,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { App, Button, theme } from 'antd';
import {
  post,
  ApiError,
  type FieldError,
  type RowError,
  type RowReport,
} from '@/shared/api';
import { hasPermission } from '@/shared/authz/permission';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { useAuthStore } from '@/shared/stores/authStore';
import { requireProgress, useOptionalProgress } from '@/shared/progress/progressContext';
import {
  addRow as addRowTo,
  countChanges,
  changedRows,
  editRow,
  hasChanges,
  revertRow,
  toRows,
  toggleDelete as toggleDeleteIn,
  type GridRow,
} from '@/shared/grid/rows';
import { cellValue } from '@/shared/grid/cell';
import { downloadFailures, stamp } from '@/shared/grid/excel/workbook';
import { entriesOfIssues, issueTable, serverIssues } from '@/shared/grid/saveIssues';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { useTabDirty } from '@/shared/grid/useTabDirty';
import { validateAll, validateCell } from '@/shared/grid/validate';

/**
 * 전량 조회가 한 번에 받는 크기 — 서버의 {@code PageSpec.MAX_SIZE} 와 같다.
 *
 * <p>더 크게 부르면 <b>요청 검증에서 400</b> 이다. 이 값이 서버와 갈리면 엑셀 전체가
 * 조용히 실패하므로 <b>바꿀 때 둘을 함께 본다</b>.
 */
const FETCH_ALL_PAGE_SIZE = 1000;

/**
 * 실패 표 모달의 폭 — AntD 기본은 416px 이다.
 *
 * <p><b>세 칸짜리 표에는 좁다.</b> 그 폭에서 사유 칸은 200px 이 채 안 되고, *이미 있는
 * 코드 그룹입니다* 한 줄이 <b>세 줄로 접힌다</b> — 열 건이면 표가 서른 줄이 된다.
 *
 * <p><b>더 넓히지도 않는다.</b> 오류 모달은 화면을 덮는 것이 목적이 아니고, 뒤에 있는
 * <b>붉게 칠해진 판</b>이 함께 보여야 사용자가 표와 판을 오가며 고칠 수 있다.
 */
const ISSUE_MODAL_WIDTH = 640;

import type {
  ActionApi,
  AddRowSpec,
  ColumnDef,
  GridApi,
  GridIssue,
  PageMode,
  PerRowSpec,
  PageResponse,
  SearchCondition,
  SortRequest,
} from '@/shared/grid/types';

/**
 * 그리드 하나의 상태와 흐름 — frontend/grid.md.
 *
 * <p><b>{@code api} 가 없는 그리드가 있다</b>(screen/admin/system/code/requirements.md 의 하단). 그때는 조회도 저장도 없고
 * 행을 화면이 넣어 준다 — 버튼이 <b>감춰지는 것이 아니라 선언이 없는 것</b>이며(frontend/grid.md)
 * 이 훅은 그 경우에도 같은 모양을 낸다. 두 컴포넌트로 가르면 크롬이 둘이 된다.
 */

export interface GridControllerOptions<T> {
  menuCd: string;
  columns: ColumnDef<T>[];
  api?: GridApi<T>;
  addRow?: AddRowSpec<T>;
  /** 조건은 폼이 검증해서 준다. {@code null} 이면 조회하지 않는다 — frontend/grid.md. */
  params?: () => Promise<SearchCondition[] | null>;
  /**
   * 조회 봉투의 {@code params} — backend/api.md. <b>screen/admin/system/code/requirements.md 가 처음 요구했다.</b>
   *
   * <p>{@link GridControllerOptions.params} 와 <b>다른 축</b>이다. 저것은 사용자가 좁힌
   * 것이고 이것은 <b>화면의 구조</b>다 — 마스터-디테일의 <b>부모 키</b>가 그것이며, 조건
   * 목록에 두면 화이트리스트를 지나며 <b>선택적</b>이 되어 <i>부모를 빠뜨린 요청</i> 이
   * 전 그룹의 자식을 한 판에 쏟는다.
   *
   * <p>매 렌더 새 함수를 넘기지 않는다 — 조회·저장 콜백이 전부 새것이 된다.
   */
  searchParams?: () => unknown;
  /**
   * 저장 페이로드를 만드는 것은 화면이다 — 행 모양이 화면마다 다르다.
   *
   * @deprecated {@link toSaveRow} 를 쓴다. <b>이 통로로는 서버 오류를 셀에 꽂지 못한다.</b>
   *   서버는 <i>요청 배열의 몇 번째</i> 로 행을 가리키는데, 배열을 화면이 만들면 배관은
   *   그 순번이 화면의 어느 행인지 알 수 없다. 오늘의 구현이 전부 `rows.map()` 이라는
   *   것은 <b>관행이지 규약이 아니다</b> — `filter` 하나가 끼면 <b>엉뚱한 행이 붉어지고
   *   아무도 그것을 모른다</b>(frontend/grid.md).
   */
  toSavePayload?: (rows: GridRow<T>[]) => unknown;
  /**
   * 행 <b>하나</b>를 요청 모양으로 바꾼다. 배열을 만드는 것은 배관이다.
   *
   * <p><b>순서를 화면에서 뺏는 것이 목적이다.</b> 배관이 `changed.map(toSaveRow)` 를 하므로
   * 보낸 배열과 화면이 든 배열이 <b>구조적으로</b> 같고, 그래서 서버가 준 순번으로 행을
   * 되찾아 셀을 칠할 수 있다(frontend/grid.md).
   */
  toSaveRow?: (row: GridRow<T>) => unknown;
  /**
   * {@link toSaveRow} 가 만든 배열에 봉투를 씌운다. <b>기본은 `{ rows }`</b> 다.
   *
   * <p>부모 키가 필요한 디테일 그리드만 쓴다 — `(rows) => ({ ordId, rows })`.
   * <b>배열을 손대지 않는다</b>: 여기서 걸러 내거나 정렬하면 순번이 다시 어긋난다.
   */
  saveEnvelope?: (rows: unknown[]) => unknown;
  /** 화면 기본값. 시스템 기본값과 개인 설정 사이의 층이다 — frontend/grid.md. */
  pageSize?: number;
  /**
   * 셀이 열리는가. <b>기본은 이 그리드의 저장 권한</b>이다.
   *
   * <p>넘기는 자리가 하나 있다 — screen/admin/system/code/requirements.md 의 하단처럼 <b>저장 단위가 다른 그리드</b>인 경우다.
   * 자기 `api.save` 가 없으므로 기본값으로는 영영 읽기 전용이 되는데, 그 화면은 하단이
   * 편집되는 것이 설계다. 마스터의 저장 권한을 넘겨받는다(screen/admin/system/code/requirements.md).
   */
  editable?: boolean;
  /** 전체 구성 저장의 소유 그리드. 자식은 편집/추가/삭제만 제공하고 독립 저장 버튼은 만들지 않는다. */
  saveOwner?: { canSave: boolean };
}

export interface GridController<T> {
  rows: GridRow<T>[];
  setRows: (rows: GridRow<T>[]) => void;
  mode: PageMode;
  page: number;
  size: number;
  total: number | null;
  totalPages: number | null;
  hasNext: boolean;
  /**
   * 커서형에만 있다 — 앞 장이 있는가(backend/api.md).
   *
   * <p>{@code page > 0} 으로 알 수 없다. 커서형에는 <b>페이지 번호가 없고</b>, 첫 장인지는
   * <b>서버가 {@code prevCursor} 를 주었는가</b> 로만 안다.
   */
  hasPrev: boolean;
  loading: boolean;
  issues: GridIssue[];
  /**
   * 검증 결과를 <b>밖에서 넣는다</b> — frontend/grid.md 의 3층에 생산자가 하나 더 생겼다.
   *
   * <p>엑셀 업로드가 그 자리다(playbook/INDEX.md). 파일에서 읽은 값이 컬럼의 어휘로 풀리지 않는
   * 경우(코드명이 두 코드에 걸린다 · 숫자가 아니다)가 있고, 그것은 <b>저장 직전이 아니라
   * 파일을 읽은 그 자리에서</b> 보여야 한다 — playbook/INDEX.md 이 <i>미리보기가 곧 행 상태</i> 라고
   * 적은 화면이며, 오류를 저장까지 미루면 사용자는 <b>412행을 다 훑고 나서</b> 처음 듣는다.
   *
   * <p><b>조회·저장이 이것을 덮는다</b>(그 경로들이 자기 결과로 다시 채운다) — 새 데이터에
   * 옛 오류가 붙어 있는 것보다 낫다.
   */
  setIssues: (issues: GridIssue[]) => void;
  dirty: boolean;
  canRead: boolean;
  canSave: boolean;
  canAddRow: boolean;
  /** 셀 편집이 열리는가 — 저장 버튼이 있는가와 다른 축이다. */
  canEdit: boolean;
  /**
   * 이 그리드가 <b>저장 단위</b>인가 — 상태 컬럼과 삭제 컬럼이 여기 달린다(frontend/grid.md).
   *
   * <p>저장 단위가 아닌 그리드에 상태 컬럼을 두면 <b>영영 빈 칸</b>이 남는다. 고친 사실은
   * 마스터 행의 `✎` 가 말하고 있으므로, 빈 컬럼은 <i>"고쳤는데 표시가 안 된다"</i> 로 읽힌다.
   */
  savesItself: boolean;
  api?: GridApi<T>;
  columns: ColumnDef<T>[];
  /**
   * 조회. <b>미저장 편집이 있으면 묻는다</b>(frontend/grid.md).
   *
   * @param confirmed 부르는 쪽이 <b>이미 물었을 때</b> {@code true}. 마스터-디테일에서
   * 마스터 선택이 바뀔 때가 그 자리다(screen/admin/system/code/requirements.md) — 화면이 선택을 거부할지 판단하려고
   *     먼저 묻는데, 여기서 또 물으면 <b>같은 질문이 두 번</b> 뜬다
   */
  read: (confirmed?: boolean) => Promise<void>;
  /**
   * 조회조건 그대로 <b>페이징 없이</b> 전량 — 엑셀 *전체* 가 부른다(playbook/excel-download.md).
   *
   * <p>거절되거나 실패하면 {@code null} 이며 <b>이유는 여기서 이미 말했다</b>.
   */
  fetchAll: (limit: number) => Promise<T[] | null>;
  save: () => Promise<void>;
  /**
   * 지금 고른 행 — <b>실행의 대상</b>이다(frontend/grid.md).
   *
   * <p>단일 선택이면 0 또는 1건이고 다중이면 여럿이다. 화면이 아니라 컨트롤러가 드는 것은
   * <b>버튼의 활성 여부가 여기서 나오기</b> 때문이다 — {@code target: 'selected'} 인 실행은
   * 선택 0건일 때 눌리지 않는다.
   */
  selected: GridRow<T>[];
  /** {@code DataGrid} 가 판의 선택을 여기 옮긴다 — 화면은 이것을 부르지 않는다. */
  setSelected: (rows: GridRow<T>[]) => void;
  /**
   * 실행 하나 — frontend/grid.md 의 다섯 단계다.
   *
   * <p>선언에 없는 {@code key} 는 조용히 아무 일도 하지 않는다 — 버튼이 그 배열에서
   * 나오므로 정상 경로로는 있을 수 없다.
   */
  runAction: (key: string) => Promise<void>;
  addRow: () => void;
  /**
   * 방금 추가한 행의 id — <b>{@code DataGrid} 가 그 행의 첫 칸을 연다</b>(frontend/grid.md).
   *
   * <p>행을 더한 다음에 사람이 하는 일은 <b>언제나 첫 칸을 채우는 것</b>이다. 그것을
   * 클릭 두 번(행 선택 · 셀 더블클릭)으로 두면 <i>행 추가</i> 가 세 동작이 되고, 새 행이
   * 판 위쪽에 붙으므로(frontend/grid.md) 스크롤이 아래에 있으면 <b>추가된 행이 보이지도 않는다</b>.
   *
   * <p><b>id 인 것이 요점이다.</b> 행 번호로 두면 정렬·필터가 행을 옮겼을 때 <b>엉뚱한
   * 행의 셀</b>이 열린다. 값이 바뀔 때마다(추가마다 새 id) 효과가 다시 돌고, 같은 행을
   * 두 번 열지 않는다.
   */
  added: string | null;
  edit: (rowId: string, patch: Partial<T>) => void;
  /** 편집 취소 — 수정한 행을 원본으로 되돌린다(frontend/grid.md). */
  revert: (rowId: string) => void;
  toggleDelete: (rowId: string) => void;
  goToPage: (page: number) => Promise<void>;
  /**
   * 커서형의 이동 — {@code ‹ 이전} · {@code 다음 ›}(backend/api.md · screen/admin/system/log/access/requirements.md).
   *
   * <p><b>페이지 번호를 만들 수 없어</b> {@link goToPage} 를 쓸 수 없다. 토큰은
   * <b>불투명</b>하므로 화면도 이 훅도 열어 보지 않는다 — 받은 것을 그대로 되돌려준다.
   */
  goNextPage: () => Promise<void>;
  goPrevPage: () => Promise<void>;
  changeSize: (size: number) => Promise<void>;
  changeSorts: (sorts: SortRequest[]) => Promise<void>;
  denyEdit: (message: string) => void;
  /**
   * 방금 고친 칸이 <b>규칙을 어겼다</b> — frontend/grid.md ④. {@link denyEdit} 와 같은 통로다.
   *
   * <p><b>축이 다르다.</b> 저것은 <i>고칠 수 없다</i> 이고 이것은 <b>고쳤는데 그대로
   * 저장되지 않는다</b> 다 — 값은 행에 들어가고 편집도 막지 않는다(frontend/grid.md).
   *
   * <p><b>{@code DataGrid} 가 어댑터에 걸어 둔다</b> — 화면은 이것을 부르지 않는다.
   */
  warnInvalid: (message: string) => void;
  /**
   * 컬럼 필터를 지운다 — <b>검색 폼의 초기화가 부른다</b>(frontend/grid.md).
   *
   * <p>조건만 지우고 필터를 남기면 <i>"왜 데이터가 안 나오지"</i> 의 원인이 <b>접힌 헤더
   * 메뉴 안의 잊힌 필터</b>로 남는다 — 초기화가 둘을 함께 지우는 이유다.
   */
  clearFilters: () => void;
  /** {@code DataGrid} 가 자기 판의 필터 초기화를 여기 걸어 둔다 — 화면은 이것을 부르지 않는다. */
  registerFilterReset: (reset: () => void) => void;
}

/**
 * 첫 조회의 크기 — 쪽수 콤보의 첫 후보와 같다({@code DEFAULT_PAGE_SIZES}).
 *
 * <p>다르게 두면 <b>콤보에 없는 값이 골라진 채로</b> 화면이 뜬다.
 */
const DEFAULT_PAGE_SIZE = 100;

/** 고친 칸의 실패를 걷어내고, 여전히 틀렸으면 다시 붙인다. 다른 칸의 실패는 건드리지 않는다. */
function recheck<T>(
  issues: GridIssue[],
  row: GridRow<T>,
  columns: ColumnDef<T>[],
  fields: string[],
): GridIssue[] {
  const touched = new Set(fields);
  const kept = issues.filter(
    (issue) => issue.rowId !== row.id || !issue.field || !touched.has(issue.field),
  );

  const added: GridIssue[] = [];
  for (const column of columns) {
    if (!touched.has(column.field) || column.hidden) continue;
    const value = cellValue(column, row.data);
    const message = validateCell(column, value, row.data, { state: row.state });
    if (message) {
      added.push({ rowId: row.id, field: column.field, message });
    }
  }
  return [...kept, ...added];
}

/**
 * 실행 하나의 대상 — <b>{@code DataGrid} 의 버튼 활성 판정도 이것을 부른다</b>(frontend/grid.md).
 *
 * <p>두 곳이 같은 답을 내야 한다. 버튼이 켜져 있는데 눌렀을 때 대상이 0건이면
 * <b>아무 일도 일어나지 않고 이유도 말하지 않는</b> 버튼이 되고, 반대면 <i>왜 눌리지
 * 않나</i> 가 된다.
 */
export function actionTargets<T>(
  action: ActionApi<T>,
  target: 'selected' | 'all' | 'none',
  selected: GridRow<T>[],
  rows: GridRow<T>[],
): GridRow<T>[] {
  const pool = target === 'selected' ? selected : target === 'all' ? rows : [];
  return action.eligible ? pool.filter(action.eligible) : pool;
}

/**
 * 실행 응답에서 {@code jobId} 를 꺼낸다 — backend/crosscutting.md 의 <code>{ jobId }</code>.
 *
 * <p><b>타입을 믿지 않는다.</b> 응답은 {@code unknown} 이고 서버가 무엇을 주는지는
 * 런타임에만 안다 — 캐스팅으로 지나가면 {@code undefined} 가 {@code jobId} 인 척
 * 흘러가 <b>없는 작업을 폴링</b>한다.
 */
/**
 * 응답에서 <b>건별 결과</b>를 꺼낸다 — backend/row-transaction.md.
 *
 * <p><b>네 칸을 다 본다.</b> 하나라도 없으면 `null` 이고 부르는 쪽이 평소의 성공 모달로
 * 떨어뜨린다 — `progress` 가 {@code jobId} 를 못 찾았을 때와 같은 처리다. <b>선언과 응답이
 * 어긋난 것</b>은 사용자에게 숨길 일이 아니라 개발자가 볼 일이다.
 */
function reportOf(result: unknown): RowReport | null {
  if (result === null || typeof result !== 'object') return null;
  const value = result as Record<string, unknown>;
  const counts = ['total', 'done', 'failed'].every((key) => typeof value[key] === 'number');
  if (!counts || !Array.isArray(value.rows)) return null;
  return value as unknown as RowReport;
}

/**
 * 표 아래에 붙일 것 둘을 잇는다 — <b>안내</b>와 <b>내려받기</b>.
 *
 * <p>둘 다 없으면 `undefined` 라 표만 남는다. 빈 {@code div} 를 만들면 모달 아래에 <b>이유
 * 없는 여백</b>이 생기고, 그것은 무언가가 안 떴다는 신호처럼 보인다.
 */
/**
 * 표 아래 <b>첫 줄</b> — 배관이 그리드에 <i>한 일</i>, 또는 <i>곧 할 일</i>.
 *
 * <p><b>뒤처리마다 다르고 함께 뜨지 않는다</b>(backend/row-transaction.md §7). {@code 'keepFailed'} 는
 * *목록을 비웠다* 이고 {@code 'reload'} 는 *목록을 새로 읽는다* 라 <b>서로를 부정한다</b> —
 * 뒤처리가 둘 중 하나뿐이므로 문구도 하나만 선다.
 *
 * <p><b>{@code 'reload'} 는 조건 없이 말한다.</b> 닫는 순간 표가 사라지는 것은 실패가 몇
 * 건이든 같으며, <b>닫기 전에</b> 말하지 않으면 사용자가 <b>아무 일도 하지 못한다</b> —
 * 이미 사라진 뒤라 그 안내는 알림이 아니라 부고다.
 *
 * <p><b>{@code 'keepFailed'} 는 비운 것이 있을 때만 말한다.</b> 전건이 실패하면 지울 성공
 * 행이 없어 목록이 그대로인데, 그때 비웠다고 하면 <b>사실이 아닌 안내</b>가 된다.
 */
function partialNotice(after: PerRowSpec['after'], cleared: number, failed: number): string | null {
  if (after === 'reload') return label(MSG.GRID_PARTIAL_GONE);
  return cleared > 0 ? label(MSG.GRID_PARTIAL_KEPT, { count: cleared, failed }) : null;
}

function tail(notice: string | null, file: ReactElement | null): ReactNode {
  if (notice === null && file === null) return undefined;
  return createElement(
    Fragment,
    null,
    notice === null ? null : createElement('div', { key: 'notice' }, notice),
    file,
  );
}

function jobIdOf(result: unknown): string | null {
  if (result === null || typeof result !== 'object') return null;
  const value = (result as Record<string, unknown>).jobId;
  return typeof value === 'string' && value !== '' ? value : null;
}

export function useGridController<T>(options: GridControllerOptions<T>): GridController<T> {
  const { menuCd, columns, api, params, searchParams, toSavePayload, toSaveRow, saveEnvelope } =
    options;
  const { modal, message } = App.useApp();
  const askDiscard = useConfirmDiscard();
  /*
    실패 표의 선 색 — `rgba(0,0,0,…)` 을 박으면 <b>다크 모드에서 검정 위의 검정</b>이라
    표가 줄 없는 글 뭉치가 된다(theme.md). 머리 아래는 글자에 가깝게, 행 사이는 한 단계
    옅게 둔다. 토큰은 훅으로만 얻을 수 있어 여기서 받아 넘긴다.
  */
  const { token } = theme.useToken();
  const issueColors = useMemo(
    () => ({
      head: token.colorTextTertiary,
      row: token.colorBorder,
      // 모달의 바닥색이다 — 머리글이 그 위에 붙어 있어야 행이 아래로 지나간다.
      bg: token.colorBgElevated,
    }),
    [token.colorBgElevated, token.colorBorder, token.colorTextTertiary],
  );
  /*
    <b>진행률 창을 여는 통로</b> — backend/crosscutting.md · playbook/INDEX.md. 화면이 아니라 여기가 든다: 화면마다
    폴링을 쓰면 주기·중단 조건·실패 처리가 갈리고, 같은 기능이 <b>화면 수만큼</b> 생긴다.

    <b>없어도 된다.</b> 훅은 조건부로 부를 수 없는데 진행률을 쓰는 실행은 드물어
    ({@code progress: true} 를 적은 것만), 여기서 요구하면 <b>진행률과 아무 상관 없는
    그리드가 호스트를 요구</b>하게 된다. 없는 채로 쓰려 하면 그 자리에서 죽는다
    ({@code requireProgress}).
  */
  const open = useOptionalProgress();
  const permissions = useAuthStore((state) => state.permissions);

  const [rows, setRowsState] = useState<GridRow<T>[]>([]);
  const [mode, setMode] = useState<PageMode>(api?.read ? 'offset' : 'all');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(options.pageSize ?? DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState<number | null>(null);
  const [totalPages, setTotalPages] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);
  /*
    커서 두 장 — backend/api.md. <b>불투명 토큰</b>이라 열어 보지 않고 그대로 되돌려준다.
    조회·정렬·쪽수가 바뀌면 함께 버려진다: 정렬이 바뀐 커서는 무효이며(backend/api.md) 그것을
    되돌려주면 서버가 400 을 낸다.
  */
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [prevCursor, setPrevCursor] = useState<string | null>(null);
  /*
    <b>지금 이 페이지를 만든 커서.</b> 앞뒤 두 장과 달리 <i>어디로 갈까</i> 가 아니라
    <i>어디에 있나</i> 를 말한다 — 저장·실행 뒤 <b>보던 자리를 다시 읽으려면</b> 그것이
    필요하다. 커서형에는 페이지 번호가 없어 {@code page} 가 늘 0 이므로, 이 값이 없으면
    재조회가 <b>맨 앞으로 튄다</b>.
  */
  const [pageCursor, setPageCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [issues, setIssues] = useState<GridIssue[]>([]);
  const [sorts, setSorts] = useState<SortRequest[]>([]);
  const [selected, setSelected] = useState<GridRow<T>[]>([]);
  /** 방금 추가한 행 — 판이 그 행의 첫 칸을 연다(frontend/grid.md). */
  const [added, setAdded] = useState<string | null>(null);

  /**
   * 진행 중이면 새 호출을 거부한다 — frontend/grid.md. <b>버튼만 막지 않는다</b>: 키보드·단축키·프로그램
   * 경로가 남는다. 상태가 아니라 ref 인 것은 <b>같은 틱 안의 두 번째 호출</b>을 막아야 하기
   * 때문이다 — 리렌더를 기다리면 이미 둘 다 나간 뒤다.
   */
  const inFlight = useRef(false);

  const canRead = !api?.read || hasPermission(permissions, menuCd, 'READ');
  const canSave = (Boolean(api?.save) || Boolean(options.saveOwner?.canSave)) && hasPermission(permissions, menuCd, 'SAVE');
  // 추가한 행은 저장으로만 서버에 가므로, 저장할 수 없는 사람에게 추가를 허용하면
  // 버릴 것을 만들게 한다 — frontend/grid.md.
  const canAddRow = Boolean(options.addRow) && canSave;
  const canEdit = options.editable ?? canSave;
  const savesItself = Boolean(api?.save);

  const dirty = useMemo(() => hasChanges(rows), [rows]);

  /*
    셸의 미저장 보호가 여는 문이 여기다 — 탭 닫기·로그아웃·새로고침이 이 값을 본다
    (shared/grid/useTabDirty). 화면은 아무것도 하지 않는다.
  */
  useTabDirty(dirty);

  const setRows = useCallback((next: GridRow<T>[]) => {
    setRowsState(next);
    setIssues([]);
    /*
      <b>선택도 함께 버린다.</b> 목록이 통째로 바뀌었는데 고른 행이 남아 있으면 실행 버튼이
      <b>화면에 없는 행</b>을 대상으로 켜져 있게 된다 — frontend/grid.md 이 마스터 조회에서 디테일을
      리셋하는 것과 같은 이유이며, 여기서는 그 대상이 실행이다.
    */
    setSelected([]);
  }, []);

  /**
   * 재조회를 유발하는 넷이 같은 규칙을 쓴다 — frontend/grid.md. 하나만 적으면 나머지 셋에서 사고가 난다.
   *
   * <p><b>{@code mode: 'all'} 에서는 정렬이 재조회가 아니다</b> — 페이지가 없어 갈아 끼울
   * 일이 없으므로 그쪽 경로는 애초에 이 문을 지나지 않는다(backend/api.md).
   *
   * <p><b>문지기가 안에 있는 것은 여기뿐이다.</b> 그리드는 자기 {@code dirty} 를 알지만
   * 화면은 <i>무엇이</i> 더러운지를 스스로 정해야 한다(마스터-디테일은 아래쪽만이다) —
   * 그래서 {@code useConfirmDiscard} 는 판정하지 않고 <b>묻기만 한다</b>.
   */
  const confirmDiscard = useCallback(
    async (): Promise<boolean> => (dirty ? askDiscard() : true),
    [askDiscard, dirty],
  );

  const showError = useCallback(
    (error: unknown) => {
      // 문구는 서버가 완성해 내려준다 — 화면은 code 로 분기하지 않는다(backend/packages.md · frontend/grid.md).
      const text = error instanceof ApiError ? error.message : String(error);
      modal.error({ content: text, okText: label(MSG.GRID_CONFIRM_OK) });
    },
    [modal],
  );

  const fetchPage = useCallback(
    async (
      nextPage: number,
      nextSize: number,
      nextSorts: SortRequest[],
      cursor?: string | null,
    ) => {
      if (!api?.read || inFlight.current) return;
      const conditions = params ? await params() : [];
      // 검증 실패면 조회 자체가 일어나지 않는다 — 오류 표시는 폼의 몫이다(frontend/grid.md).
      if (conditions === null) return;

      inFlight.current = true;
      setLoading(true);
      try {
        const response = await post<PageResponse<T>>(api.read.url, {
          conditions,
          sorts: nextSorts,
          /*
            <b>모드가 요청에 없다</b>(backend/api.md) — 엔드포인트가 정하고 응답이 나른다.
            커서를 실어도 오프셋 화면은 그것을 무시하므로 분기가 없다.
          */
          page: { page: nextPage, size: nextSize, cursor: cursor ?? undefined },
          // 부모 키처럼 **화면의 구조인 값** — 조건과 다른 축이다(backend/api.md).
          params: searchParams ? searchParams() : undefined,
        });
        setRows(toRows(response.content));
        setMode(response.mode);
        setTotal(response.totalElements);
        setTotalPages(response.totalPages);
        setHasNext(response.hasNext);
        setNextCursor(response.nextCursor ?? null);
        setPrevCursor(response.prevCursor ?? null);
        setPage(nextPage);
        setPageCursor(cursor ?? null);
        setSize(nextSize);
        setSorts(nextSorts);
        if (response.content.length === 0 && api.read.emptyNotice) {
          modal.info({ content: label(MSG.GRID_READ_EMPTY), okText: label(MSG.GRID_CONFIRM_OK) });
        }
      } catch (error) {
        showError(error);
      } finally {
        // 실패해도 반드시 푼다 — 잠금을 그대로 두면 화면이 죽는다(frontend/grid.md).
        inFlight.current = false;
        setLoading(false);
      }
    },
    [api, modal, params, searchParams, setRows, showError],
  );

  /**
   * <b>보던 자리</b>를 다시 읽는다 — 저장·실행이 끝난 뒤의 재조회다.
   *
   * <p><b>모드로 갈라지지 않는다.</b> 오프셋형은 {@code pageCursor} 가 `null` 이라 쪽수로
   * 가고, 커서형은 {@code page} 가 0 이라 커서로 간다 — 서버가 <b>커서가 있으면 쪽수를
   * 읽지 않기</b> 때문에 둘을 함께 실어도 분기가 필요 없다(backend/api.md).
   *
   * <p><b>같은 목록을 약속하지는 않는다.</b> 커서는 {@code WHERE (키) < (기준점)} 이라
   * 기준점 행이 지워져도 동작하지만, <b>저장이 정렬 키를 바꾼 행은 자리를 옮긴다</b> —
   * 커서의 성질이지 결함이 아니다.
   */
  const reloadHere = useCallback(
    () => fetchPage(page, size, sorts, pageCursor),
    [fetchPage, page, pageCursor, size, sorts],
  );

  const read = useCallback(async (confirmed?: boolean) => {
    if (!confirmed && !(await confirmDiscard())) return;
    await fetchPage(0, size, sorts);
  }, [confirmDiscard, fetchPage, size, sorts]);

  const goToPage = useCallback(
    async (nextPage: number) => {
      if (!(await confirmDiscard())) return;
      await fetchPage(nextPage, size, sorts);
    },
    [confirmDiscard, fetchPage, size, sorts],
  );

  const changeSize = useCallback(
    async (nextSize: number) => {
      if (!(await confirmDiscard())) return;
      await fetchPage(0, nextSize, sorts);
    },
    [confirmDiscard, fetchPage, sorts],
  );

  /*
    커서형의 이동 — screen/admin/system/log/access/requirements.md. <b>페이지 번호가 없다.</b>

    {@code page} 를 0 으로 고정해 보내는 것은, 서버가 커서가 있으면 {@code page} 를 읽지
    않기 때문이다(커서가 곧 {@code WHERE} 다). 숫자를 늘려 보내면 <b>화면에 없는 페이지
    번호를 화면이 만들어 내는 것</b>이 되고, 그 숫자가 어디에도 쓰이지 않는다.
  */
  const goNextPage = useCallback(async () => {
    if (!nextCursor || !(await confirmDiscard())) return;
    await fetchPage(0, size, sorts, nextCursor);
  }, [confirmDiscard, fetchPage, nextCursor, size, sorts]);

  const goPrevPage = useCallback(async () => {
    if (!prevCursor || !(await confirmDiscard())) return;
    await fetchPage(0, size, sorts, prevCursor);
  }, [confirmDiscard, fetchPage, prevCursor, size, sorts]);

  const changeSorts = useCallback(
    async (nextSorts: SortRequest[]) => {
      if (!(await confirmDiscard())) return;
      // 정렬이 바뀌면 페이지를 1로 되돌린다 — 3페이지를 보다 정렬을 바꾸면 그 3페이지는
      // 다른 데이터다(frontend/grid.md).
      await fetchPage(0, size, nextSorts);
    },
    [confirmDiscard, fetchPage, size],
  );

  /**
   * 표 아래의 <b>실패분 내려받기</b> — backend/row-transaction.md §7.
   *
   * <p><b>표는 읽을 수는 있어도 고쳐서 다시 낼 수는 없다.</b> 오백 행을 올려 열둘이 걸린
   * 자리에서 남는 일은 화면에서 원본 파일로 <b>옮겨 적는 것</b>이고, 그 옮겨 적기가 새로운
   * 오타를 만든다.
   *
   * <p><b>전건 롤백에도 단다.</b> 오히려 그쪽이 더 필요하다 — 되돌아간 오백 행 전부가
   * 다시 낼 것이므로, 사용자가 고쳐야 하는 것은 <i>그중 어느 열둘인가</i> 하나다.
   *
   * <p><b>순번을 못 이으면 달지 않는다</b>({@code sent} 가 `null` — {@code toSavePayload}).
   * 행을 되찾을 수 없으면 파일에 실을 것이 사유뿐이고, 그것은 표가 이미 보여 주고 있다.
   */
  const failureFile = useCallback(
    (rowErrors: RowError[], sent: GridRow<T>[] | null): ReactElement | null => {
      if (sent === null) return null;
      const failures = rowErrors
        .map((one) => ({
          row: sent[one.index],
          // 한 행에 사유가 여럿일 수 있다 — 칸마다 하나씩이다.
          reason: one.fields.map((field) => field.message).join(' / '),
        }))
        .filter((one) => one.row !== undefined)
        .map((one) => ({ data: one.row.data, reason: one.reason }));
      if (failures.length === 0) return null;

      return createElement(
        Button,
        {
          type: 'link',
          size: 'small',
          style: { padding: 0 },
          onClick: () => {
            void downloadFailures(
              `${options.menuCd}_${label(MSG.GRID_ISSUE_EXCEL_FILE)}`,
              options.columns,
              failures,
              stamp(),
              {
                sheet: label(MSG.GRID_ISSUE_EXCEL_SHEET),
                row: label(MSG.GRID_ISSUE_ROW),
                reason: label(MSG.GRID_ISSUE_REASON),
              },
            );
          },
        },
        label(MSG.GRID_ISSUE_EXCEL),
      );
    },
    [options.columns, options.menuCd],
  );

  /**
   * <b>서버가 행을 짚은 실패</b>를 셀과 표로 되돌린다 — frontend/grid.md.
   *
   * <p>문구 한 줄만 띄우면 <i>어느 줄의 무슨 값이 문제인가</i> 를 사용자가 읽어 낼 수 없고,
   * 일괄 처리는 화면에 안 보이는 행까지 나가므로 그 줄이 지금 화면에 없을 수도 있다.
   *
   * <p><b>저장과 실행이 같은 함수를 쓴다.</b> 전건 롤백에서 사용자에게 벌어진 일은 둘이
   * 같다 — <i>아무것도 안 됐고, 화면에 있는 것이 곧 다시 낼 입력이다</i>. 실행만 문구 한
   * 줄로 닫으면 서버가 짚어 준 행을 <b>화면이 버리는 것</b>이 된다.
   *
   * <p>{@code locate} 는 <b>행을 짚지 못하는 옛 응답</b>의 통로다 — 저장에만 있고, 서버가
   * {@code rows} 를 보내기 시작한 화면에서는 필요 없다.
   *
   * @return 짚어 냈으면 {@code true} — 아니면 부르는 쪽이 평소의 오류 모달로 떨어뜨린다
   */
  const showRowFailure = useCallback(
    (
      error: unknown,
      sent: GridRow<T>[] | null,
      locate?: (fields: FieldError[]) => GridIssue[],
    ): boolean => {
      if (!(error instanceof ApiError) || (error.rows.length === 0 && error.fields.length === 0)) {
        return false;
      }
      const located = serverIssues(error.rows, error.fields, sent, options.columns);
      if (located.issues.length > 0) {
        setIssues(located.issues);
      } else if (locate && error.fields.length > 0) {
        setIssues(locate(error.fields));
      }
      // 제목은 <b>서버가 완성한 문구</b>다 — 표는 상세이고 이것이 요약이다(backend/packages.md).
      modal.error({
        width: ISSUE_MODAL_WIDTH,
        title: error.message,
        content: issueTable(located.entries, issueColors, {
          footer: failureFile(error.rows, sent) ?? undefined,
        }),
        okText: label(MSG.GRID_CONFIRM_OK),
      });
      return true;
    },
    [failureFile, issueColors, modal, options.columns],
  );

  /**
   * <b>건별 처리의 결과</b>를 낸다 — backend/row-transaction.md §7.
   *
   * <p><b>저장과 실행이 같은 함수를 쓴다.</b> 사용자에게 둘은 구분되지 않는 사건이며
   * (<i>일부는 됐고 일부는 안 됐다</i>), 표시가 갈리면 같은 일이 두 얼굴로 보인다 —
   * 검증 실패와 서버 실패가 이미 같은 표를 쓰는 것과 같은 이유다(saveIssues.ts).
   *
   * <p><b>성공 모달을 쓰지 않는다.</b> 12건이 안 된 저장을 <i>저장했습니다</i> 로 닫으면
   * 화면이 거짓말한 것이 된다.
   *
   * <p><b>셀도 칠한다.</b> {@code 'reload'} 를 고른 화면에서는 뒤이은 재조회가
   * {@code setRows} 로 이 표시를 지운다 — 남는 것은 {@code 'keepFailed'} 쪽이고, 거기서는
   * 사용자가 <b>실패한 행을 그 자리에서 고친다</b>.
   *
   * <p><b>{@code 'keepFailed'} 는 여기서 목록을 비운다.</b> 모달이 뜨기 전에 비우는 것은,
   * 사용자가 <i>확인</i> 을 누르고 나서 목록이 바뀌면 <b>모달이 가린 사이에 무슨 일이
   * 있었는지</b> 를 못 보기 때문이다 — 비운 사실은 표 아래 한 줄이 말한다.
   *
   * <p>{@code setRows} 가 표시를 지우므로 <b>비운 다음에 칠한다</b>. 순서가 뒤집히면
   * 붉은 셀이 하나도 남지 않는다.
   */
  const showReport = useCallback(
    (report: RowReport, sent: GridRow<T>[] | null, after: PerRowSpec['after']) =>
      new Promise<void>((resolve) => {
        const located = serverIssues(report.rows, [], sent, options.columns);
        /*
          <b>순번 대응이 서지 않으면 비우지 않는다.</b> {@code toSavePayload} 로 배열을 직접
          만든 저장은 보낸 것과 화면의 행을 이을 수 없고({@code sent} 가 `null` 이다), 그때
          비우면 <b>엉뚱한 행을 지운다</b> — 표만 내고 목록은 그대로 둔다.
        */
        let cleared = 0;
        if (after === 'keepFailed') {
          if (sent === null) {
            // 개발자용이라 영어다 — 화면에 나가는 문구가 아니다(sys/code.md).
            console.error('Declared keepFailed but the sent rows cannot be located');
          } else {
            const failed = new Set(report.rows.map((one) => one.index));
            const keep = sent.filter((_, index) => failed.has(index));
            cleared = rows.length - keep.length;
            setRows(keep);
          }
        }
        setIssues(located.issues);
        modal.warning({
          width: ISSUE_MODAL_WIDTH,
          title: label(MSG.GRID_PARTIAL_TITLE),
          content: issueTable(located.entries, issueColors, {
            heading: label(MSG.GRID_PARTIAL_SUMMARY, {
              total: report.total,
              done: report.done,
              failed: report.failed,
            }),
            // 안내는 뒤처리마다 다르다 — partialNotice 가 고른다.
            footer: tail(partialNotice(after, cleared, report.failed), failureFile(report.rows, sent)),
          }),
          okText: label(MSG.GRID_CONFIRM_OK),
          onOk: () => resolve(),
        });
      }),
    [failureFile, issueColors, modal, options.columns, rows, setRows],
  );

  /**
   * 선언대로 왔나 — 아니면 <b>개발자에게 말하고</b> 평소의 성공 모달로 떨어진다.
   *
   * <p>{@code progress} 가 {@code jobId} 를 못 찾았을 때와 같은 처리다. 사용자에게 빈 표를
   * 띄우는 것보다 <b>아무 일 없었던 것처럼 성공을 말하는 편</b>이 낫다 — 어긋난 것은
   * 응답이지 사용자의 입력이 아니다.
   */
  const reportIfDeclared = useCallback((declared: PerRowSpec | undefined, result: unknown, url: string) => {
    if (!declared) return null;
    const report = reportOf(result);
    // 개발자용이라 영어다 — 화면에 나가는 문구가 아니다(sys/code.md).
    if (report === null) console.error(`Declared perRow but returned no report: ${url}`);
    return report;
  }, []);

  const save = useCallback(async () => {
    const saveApi = api?.save;
    if (!saveApi || (!toSavePayload && !toSaveRow) || inFlight.current) return;

    const changed = changedRows(rows);
    if (changed.length === 0) {
      message.info(label(MSG.GRID_SAVE_NOTHING));
      return;
    }

    // ① 검증 — 실패하면 서버를 부르지 않는다(frontend/grid.md).
    const found = validateAll(rows, options.columns, saveApi.validate);
    setIssues(found);
    if (found.length > 0) {
      /*
        <b>건수만으로는 무엇이 틀렸는지 알 수 없다</b>(2026-08-31 — frontend/grid.md ④가 적어 둔
        <i>여러 건이면 목록</i>). 붉은 셀은 <b>어디</b>를 말하고 이 목록이 <b>무엇</b>을
        말한다.

        <b>행 번호를 적지 않는다.</b> 화면의 번호는 <i>보이는 자리</i>(판의 {@code rowIndex})
        에서 나오므로 클라이언트 정렬이 걸린 판에서는 우리가 센 배열 순서와 <b>어긋난다</b> —
        틀린 번호는 없는 번호보다 나쁘다. 어느 셀인지는 판이 이미 첫 실패로 데려간다
        (frontend/grid.md ②).

        <b>같은 줄은 한 번만</b> 낸다. 값이 비어 있는 실패(필수값 누락)는 열 행이 같은 줄을
        만들어 하나로 접히고, 값이 다른 실패(중복·형식)는 <b>행마다 펴진다</b> — 앞은 규칙
        하나를 고치면 다 낫지만 뒤는 <b>값 하나하나가 별개의 사건</b>이기 때문이다(saveIssues.ts).
      */
      modal.error({
        width: ISSUE_MODAL_WIDTH,
        content: issueTable(entriesOfIssues(found, rows, options.columns), issueColors),
        okText: label(MSG.GRID_CONFIRM_OK),
      });
      return;
    }

    // ② 경고 — 건수보다 <b>먼저</b> 묻는다(screen/admin/system/code/requirements.md).
    //
    // 건수는 얼마나를 말할 뿐이라 무엇이 일어나는지를 말하지 못한다. *"기본값 5 로
    // 되돌립니다"* 와 *"끄면 잠긴 계정을 풀 수 없습니다"* 가 그런 것들이며, 건수 뒤에
    // 두면 사용자는 이미 한 번 동의한 뒤에 진짜 질문을 받는다.
    // <b>{@code await} 가 붙어 있다</b>(2026-08-30 — screen/menu/requirements.md). 역할 화면의 셋째 줄
    // (*"그중 2명은 유효 권한이 공집합이 되어 다음 로그인부터 차단됩니다"*)은 그리드가
    // 들고 있지 않은 데이터를 물어야 나온다 — 보유자 전원의 다른 역할·개인 예외다.
    // 동기 배열도 그대로 통과하므로 이미 있는 화면들은 달라지는 것이 없다.
    const warnings = saveApi.warn ? await saveApi.warn(changed) : [];
    if (warnings.length > 0) {
      const agreed = await new Promise<boolean>((resolve) => {
        modal.confirm({
          title: label(MSG.GRID_SAVE_WARN_TITLE),
          // 여러 줄이다 — 문자열로 넘기면 판이 한 줄로 이어 붙인다.
          content: createElement('div', { style: { whiteSpace: 'pre-line' } }, warnings.join('\n')),
          okText: label(MSG.GRID_CONFIRM_OK),
          cancelText: label(MSG.COMMON_CANCEL),
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!agreed) return;
    }

    // ③ 확인 — 일괄 저장은 화면에 안 보이는 행까지 나가므로 건수가 유일한 요약이다.
    if (saveApi.confirm !== false) {
      const counts = countChanges(rows);
      const agreed = await new Promise<boolean>((resolve) => {
        modal.confirm({
          title: label(MSG.GRID_SAVE_CONFIRM_TITLE),
          content: label(MSG.GRID_SAVE_CONFIRM_BODY, {
            added: counts.added,
            updated: counts.updated,
            removed: counts.removed,
          }),
          okText: label(MSG.GRID_CONFIRM_OK),
          cancelText: label(MSG.COMMON_CANCEL),
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!agreed) return;
    }

    /*
      <b>순번을 보장할 수 있으면 배관이 배열을 만든다</b>(frontend/grid.md).

      서버는 <i>요청 배열의 몇 번째</i> 로 거부된 행을 가리킨다. 그 배열을 화면이
      만들었다면({@code toSavePayload}) 걸러 냈는지 정렬했는지 배관은 알 수 없으므로 순번을
      <b>믿지 않는다</b> — {@code sent} 가 `null` 이 되고 아래 {@code catch} 가 셀을 칠하지 않는다.
    */
    const sent = toSaveRow ? changed : null;
    const payload = toSaveRow
      ? (saveEnvelope ?? ((made: unknown[]) => ({ rows: made })))(changed.map(toSaveRow))
      : toSavePayload?.(changed);

    inFlight.current = true;
    setLoading(true);
    try {
      const result = await (saveApi.submit ? saveApi.submit(payload) : post(saveApi.url, payload));
      // ⑤ 성공 모달 — 확인이 다음 단계(재조회)를 트리거한다(frontend/grid.md).
      //    <b>건별 저장은 실패가 있으면 결과 표가 그 자리를 대신한다</b>(row-transaction.md).
      const perRow = saveApi.perRow;
      const report = reportIfDeclared(perRow, result, saveApi.url);
      if (perRow && report !== null && report.failed > 0) {
        await showReport(report, sent, perRow.after);
        inFlight.current = false;
        setLoading(false);
        /*
          <b>{@code 'keepFailed'} 는 읽지 않는다.</b> 방금 남긴 실패 행이 재조회로 지워지면
          비운 의미가 사라진다 — 사용자가 고칠 것이 화면에서 없어진다.

          <b>{@code onSuccess} 도 부르지 않는다.</b> 그 콜백은 <i>저장이 만들어 낸 값</i>을
          받는 자리인데(신규 계정의 임시 비밀번호) 여기서 오는 것은 결과표다.
        */
        if (perRow.after === 'reload') {
          await reloadHere();
        }
        return;
      }
      await new Promise<void>((resolve) => {
        modal.success({
          content: label(MSG.GRID_SAVE_DONE),
          okText: label(MSG.GRID_CONFIRM_OK),
          onOk: () => resolve(),
        });
      });
      inFlight.current = false;
      setLoading(false);
      if (saveApi.reloadAfter !== false) {
        await reloadHere();
      }
      /*
        <b>응답을 함께 넘긴다</b>(screen/admin/system/user/requirements.md). 대개 {@code null} 이지만 <b>재조회로는 얻을 수
        없는 값</b>을 저장이 만들어 내는 화면이 있다 — 신규 계정의 임시 비밀번호다.
      */
      saveApi.onSuccess?.(result);
    } catch (error) {
      inFlight.current = false;
      setLoading(false);
      /*
        <b>전건 롤백이다 — 그리드를 건드리지 않는다.</b> 아무것도 커밋되지 않았으므로 지울
        성공 행이 없고, 화면에 있는 편집이 곧 <b>다시 낼 입력</b>이다. 여기서 재조회하면
        그것을 지워 재시도를 불가능하게 만든다(backend/row-transaction.md §7).
      */
      const locate = saveApi.locateFieldErrors;
      if (!showRowFailure(error, sent, locate && ((fields) => locate(fields, changed)))) {
        showError(error);
      }
    }
  }, [
    api,
    message,
    issueColors,
    modal,
    options.columns,
    reloadHere,
    rows,
    reportIfDeclared,
    showRowFailure,
    saveEnvelope,
    showError,
    showReport,
    toSavePayload,
    toSaveRow,
  ]);

  /**
   * 실행 — frontend/grid.md 의 다섯 단계다.
   *
   * <pre>
   * ① 대상 확인   ── 선택 0건이면 애초에 버튼이 비활성이다
   * ② 검증        ── 실패 → 중단
   * ③ params()    ── 모달이 있으면 열고 기다린다. 취소 → 중단
   * ④ 확인        ── ③ 이 있으면 모달이 겸한다
   * ⑤ 호출 → 성공 모달 → 재조회 → 콜백
   * </pre>
   *
   * <p><b>검증이 파라미터보다 앞이다</b>(frontend/grid.md). 데이터가 틀렸는데 사유를 다 쓰고 나서
   * <i>"3건의 오류가 있습니다"</i> 를 보는 것이 가장 나쁜 순서다.
   *
   * <p><b>{@code save} 와 갈리는 자리가 둘이다.</b> 여기는 <b>미변경 행도 대상</b>이고
   * (고른 것이 곧 대상이다) <b>응답을 화면에 준다</b>({@code onSuccess}) — 임시 비밀번호처럼
   * 재조회로는 다시 얻을 수 없는 값이 있기 때문이다(screen/admin/system/user/requirements.md).
   */
  const runAction = useCallback(
    async (key: string) => {
      const action: ActionApi<T> | undefined = api?.actions?.find(
        (candidate) => candidate.key === key,
      );
      if (!action || inFlight.current) return;

      // ① 대상 — frontend/grid.md. <b>고른 뒤에 거른다</b>: 건수가 어긋나면 확인 문구가 거짓이 된다.
      const target = action.target ?? 'selected';
      const targets = actionTargets(action, target, selected, rows);
      /*
        <b>대상이 0건이면 나가지 않는다.</b> 버튼이 이미 비활성이라 정상 경로로는 오지
        않지만 키보드·프로그램 경로가 남는다.

        <b>{@code 'all'} 도 같다</b>(2026-09-02). {@code 'selected'} 만 보고 있었는데,
        {@code 'all'} 의 풀도 {@code eligible} 로 걸러지므로 <b>조회 결과에 대상이 하나도
        없는</b> 상태가 실재한다 — 그때 빈 배열을 보내면 서버의 {@code @NotEmpty} 가 400 을
        내고 화면에는 <i>입력값을 확인하세요</i> 만 뜬다. <b>고를 것이 없다는 사실을 그렇게
        말하면 안 된다.</b>

        {@code 'none'} 은 제외다 — 그 실행의 대상은 화면이 든 행이 아니므로(①의 일괄
        재계산) 0건인 것이 정상이다.
      */
      if (target !== 'none' && targets.length === 0) return;

      // ② 검증 — 파라미터를 받기 <b>전에</b> 본다(frontend/grid.md).
      const found = validateAll(rows, options.columns, action.validate);
      setIssues(found);
      if (found.length > 0) {
        // 저장과 <b>같은 표</b>다 — 사용자에게 둘은 구분되지 않는 사건이다(saveIssues.ts).
        modal.error({
          width: ISSUE_MODAL_WIDTH,
          content: issueTable(entriesOfIssues(found, rows, options.columns), issueColors),
          okText: label(MSG.GRID_CONFIRM_OK),
        });
        return;
      }

      // ③ 파라미터 — <b>있으면 확인을 겸한다</b>(frontend/grid.md). 없으면 ④ 확인 다이얼로그다.
      let params: unknown;
      if (action.params) {
        const value = await action.params(targets);
        if (value === null) return;
        params = value;
      } else if (action.confirm !== false) {
        const agreed = await new Promise<boolean>((resolve) => {
          modal.confirm({
            title: label(MSG.GRID_ACTION_CONFIRM_TITLE),
            /*
              화면이 <b>자기 문구</b>를 줄 수 있다(frontend/grid.md). 일반 문구는 *무엇을 몇 건* 을
              말하지만, <b>그 실행이 무엇을 보장하지 않는가</b> 는 그 실행만 안다 —
              screen/admin/system/batch/schedule/requirements.md 의 중단이 그 자리다(*"즉시 멈추지 않을 수 있습니다"*).
            */
            content:
              typeof action.confirm === 'string'
                ? label(action.confirm, { count: targets.length })
                : target === 'none'
                  ? label(MSG.GRID_ACTION_CONFIRM_NONE, { name: label(action.label) })
                  : label(MSG.GRID_ACTION_CONFIRM_BODY, {
                      name: label(action.label),
                      count: targets.length,
                    }),
            okText: label(MSG.GRID_CONFIRM_OK),
            cancelText: label(MSG.COMMON_CANCEL),
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
        if (!agreed) return;
      }

      inFlight.current = true;
      setLoading(true);
      try {
        /*
          <b>페이로드가 둘로 나뉜다</b>(frontend/grid.md) — 대상 행과 파라미터다. 섞으면 서버가
          *"이 필드가 행의 것인가 파라미터인가"* 를 이름으로 추측하게 된다.
        */
        const result = await post(action.url, {
          targets: action.toTargets
            ? action.toTargets(targets)
            : targets.map((row) => row.data),
          params,
        });
        /*
          <b>오래 걸리는 실행은 여기서 갈린다</b>(backend/crosscutting.md · playbook/INDEX.md). 서버가 돌려준 것은
          <i>끝났다</i> 가 아니라 <b>접수됐다</b>이며, 그것을 성공 모달로 말하면
          <b>화면이 거짓말한 것</b>이 된다 — 사용자는 목록을 보러 갔다가 아무것도 안
          바뀐 것을 본다.

          <b>화면이 폴링 코드를 쓰지 않는다</b>(playbook/INDEX.md) — 여기가 그 약속의 자리다.
        */
        if (action.progress === true) {
          /*
            <b>둘을 함께 켜면 진행률이 이긴다</b>(types.ts). 여기서 리턴하므로 아래의
            건별 분기까지 오지 않으며, 그러면 {@code perRow} 가 <b>조용히 무시된다</b> —
            선언한 사람은 결과 모달을 기다리는데 영영 오지 않는다.

            <b>막지 않고 말한다.</b> 셋이 다 정당한 선언이라 하나를 틀렸다고 할 수 없고,
            어느 쪽을 지울지는 화면의 결정이다. 조용한 것만이 문제다.
          */
          if (action.perRow) {
            // 개발자용이라 영어다 — 화면에 나가는 문구가 아니다(sys/code.md).
            console.error(`Declared both progress and perRow: ${action.key} — progress wins`);
          }
          inFlight.current = false;
          setLoading(false);
          const jobId = jobIdOf(result);
          if (jobId === null) {
            /*
              <b>선언과 응답이 어긋났다.</b> 진행률을 켰는데 {@code jobId} 가 없으면 볼
              것이 없다 — 빈 창을 띄우면 <i>0% 에서 멈춘 작업</i>으로 읽히므로, 평소의
              성공 모달로 떨어뜨리고 재조회한다.
            */
            // 개발자용이라 영어다 — 화면에 나가는 문구가 아니다(sys/code.md).
            console.error(`Action declared progress but returned no jobId: ${action.key}`);
          } else {
            await requireProgress(open)(jobId, {
              title: action.label,
              /*
                <b>{@code DONE} 뒤에 재조회한다</b>(playbook/INDEX.md) — 도는 중에 부르면 절반만
                반영된 목록을 본다. 창을 닫아도 이 콜백은 온다: 닫는 것은 <i>보는 것</i>을
                그만두는 일이지 작업을 멈추는 일이 아니다.
              */
              onDone: () => {
                if (action.reloadAfter !== false) void reloadHere();
                action.onSuccess?.(result);
              },
            });
            return;
          }
        }
        /*
          성공 모달 — 확인이 다음 단계(재조회)를 트리거한다(frontend/grid.md).

          <b>건별 실행은 여기서 갈린다</b>(backend/row-transaction.md). 대상 배열이 곧
          {@code targets} 이므로 순번으로 행을 되찾을 수 있다 — {@code toTargets} 는
          <b>거르는 자리가 아니라</b> 모양을 바꾸는 자리라 순서가 유지된다.
        */
        const perRow = action.perRow;
        const report = reportIfDeclared(perRow, result, action.url);
        if (perRow && report !== null && report.failed > 0) {
          await showReport(report, targets, perRow.after);
          inFlight.current = false;
          setLoading(false);
          // 저장과 같은 규칙이다 — 남긴 것을 지우지 않고, onSuccess 는 성공의 자리다.
          if (perRow.after === 'reload') {
            await reloadHere();
          }
          return;
        }
        await new Promise<void>((resolve) => {
          modal.success({
            content: label(MSG.GRID_ACTION_DONE),
            okText: label(MSG.GRID_CONFIRM_OK),
            onOk: () => resolve(),
          });
        });
        inFlight.current = false;
        setLoading(false);
        if (action.reloadAfter !== false) {
          await reloadHere();
        }
        /*
          <b>재조회 뒤에 부른다.</b> 콜백이 여는 모달(발급된 임시 비밀번호 — screen/admin/system/user/requirements.md)이 떠
          있는 동안 목록이 갈리는 것보다, 갈리고 난 다음에 뜨는 편이 낫다.
        */
        action.onSuccess?.(result);
      } catch (error) {
        inFlight.current = false;
        setLoading(false);
        /*
          <b>실행도 서버가 행을 짚는다.</b> 500건 확정이 12건 때문에 전건 롤백됐을 때
          문구 한 줄만 띄우면 사용자는 <b>어느 12건인지 알 방법이 없다</b>.

          {@code targets} 가 곧 보낸 배열이라 순번으로 행을 되찾는다 — {@code toTargets} 는
          거르는 자리가 아니라 모양을 바꾸는 자리라 순서가 유지된다.
        */
        if (!showRowFailure(error, targets)) {
          showError(error);
        }
      }
    },
    [
      api,
      issueColors,
      modal,
      open,
      options.columns,
      reloadHere,
      reportIfDeclared,
      showRowFailure,
      rows,
      selected,
      showError,
      showReport,
    ],
  );

  const addRow = useCallback(() => {
    const spec = options.addRow;
    if (!spec) return;
    const data = rows.map((row) => row.data);
    const verdict = spec.canAdd ? spec.canAdd(data) : true;
    if (verdict !== true) {
      // 비활성으로 막지 않는다 — 회색 버튼은 왜 안 되는지를 말하지 못한다(frontend/grid.md).
      message.warning(verdict.message);
      return;
    }
    const next = addRowTo(rows, (spec.defaults ? spec.defaults(data) : {}) as T);
    setRowsState(next);
    /*
      <b>새 행은 맨 앞이다</b>(`rows.addRow`) — 그 사실을 여기서 읽는다. 판에게 어느 행을
      열지 알려 주는 것은 <b>id</b> 이며(위 {@link GridController.added}), 자리를 알려 주면
      정렬이 걸린 판에서 엉뚱한 행이 열린다.
    */
    setAdded(next[0]?.id ?? null);
  }, [message, options.addRow, rows]);

  /**
   * 셀 편집 — <b>입력 시점에는 1층만 돈다</b>(frontend/grid.md).
   *
   * <p>그때는 사용자가 어느 버튼을 누를지 모르므로 저장 규칙으로 검사하면 어긋난다.
   * <b>편집을 막지도 않는다</b> — 표시만 갱신한다.
   *
   * <p>고친 칸의 실패 표시를 지우는 것이 요점이다. 저장 검증이 칠한 붉은 셀이 값을 고친
   * 뒤에도 남아 있으면 <i>"고쳤는데 여전히 오류"</i> 로 읽히고, 사용자는 저장을 한 번 더
   * 눌러 보는 수밖에 없다.
   */
  const edit = useCallback(
    (rowId: string, patch: Partial<T>) => {
      setRowsState((current) => {
        const next = editRow(current, rowId, patch);
        const row = next.find((candidate) => candidate.id === rowId);
        if (row) {
          setIssues((found) => recheck(found, row, columns, Object.keys(patch)));
        }
        return next;
      });
    },
    [columns],
  );

  const revert = useCallback((rowId: string) => {
    setRowsState((current) => revertRow(current, rowId));
    // 되돌린 행의 실패 표시도 함께 지운다 — 원본은 저장에 실리지 않으므로 잴 것이 없다.
    setIssues((found) => found.filter((issue) => issue.rowId !== rowId));
  }, []);

  const toggleDelete = useCallback((rowId: string) => {
    setRowsState((current) => toggleDeleteIn(current, rowId));
  }, []);

  /**
   * 전량 조회 — <b>안에서는 페이징한다</b>.
   *
   * <p>{@code PageSpec.MAX_SIZE} 가 1000 이라 한 번에 다 받을 수 없다. 서버에 전용
   * 경로를 내지 않는 이유는 <b>같은 화이트리스트·같은 파생 조건</b>을 두 곳에서 유지하게
   * 되기 때문이다 — 여기서 여러 장을 읽는 것은 <b>구현 세부</b>이고 사용자가 받는 것은
   * 여전히 전량이다.
   *
   * <p><b>첫 응답이 총 건수를 안다.</b> 상한을 넘으면 한 장만 읽고 그만둔다 — 5만 건을
   * 다 받아 놓고 거절하면 상한이 지키려던 것을 이미 다 치른 뒤다.
   */
  const fetchAll = useCallback(
    async (limit: number): Promise<T[] | null> => {
      /*
        <b>조회가 없는 그리드의 전량은 화면이 든 것 전부</b>다(frontend/grid.md · screen/admin/system/user/requirements.md). 서버
        페이징이 없으므로 그것이 실제로 전량이며, {@code null} 을 돌려주면 엑셀 <i>전체</i>
        가 <b>아무 말 없이 아무 일도 하지 않는다</b>.
      */
      if (!api?.read) {
        return rows.map((row) => row.data);
      }
      if (inFlight.current) return null;
      const conditions = params ? await params() : [];
      if (conditions === null) return null;

      const tooMany = () => {
        modal.info({ content: label(MSG.GRID_EXCEL_TOO_MANY), okText: label(MSG.GRID_CONFIRM_OK) });
        return null;
      };

      inFlight.current = true;
      setLoading(true);
      try {
        const collected: T[] = [];
        /*
          <b>커서형은 페이지 번호로 넘길 수 없다</b>(backend/api.md) — 앞 응답이 준 토큰을 그대로
          되돌려주며 따라간다. 오프셋형은 이 값이 끝까지 {@code null} 이고 번호가 움직인다.
        */
        let cursor: string | null = null;
        for (let pageNo = 0; ; pageNo += 1) {
          const response: PageResponse<T> = await post<PageResponse<T>>(api.read.url, {
            conditions,
            sorts,
            page: { page: pageNo, size: FETCH_ALL_PAGE_SIZE, cursor: cursor ?? undefined },
            params: searchParams ? searchParams() : undefined,
          });
          /*
            <b>커서형은 총 건수를 모른다</b>(screen/admin/system/log/access/requirements.md) — 첫 장에서 미리 막을 수 없고 아래의
            누적 검사가 대신한다. 5만 건까지 읽고 나서 거절하는 것이 그 대가이며, 그것이
            총 건수를 세지 않기로 한 선택의 뒷면이다.
          */
          if (pageNo === 0 && (response.totalElements ?? 0) > limit) return tooMany();

          collected.push(...response.content);
          if (collected.length > limit) return tooMany();
          // `all` 은 한 장이 전부이고, 마지막 장은 다음이 없다.
          if (response.mode === 'all' || !response.hasNext || response.content.length === 0) break;
          cursor = response.nextCursor ?? null;
          // 커서형인데 다음 토큰이 없다 — 더 읽을 방법이 없으므로 멈춘다(무한 루프 방지).
          if (response.mode === 'cursor' && cursor === null) break;
        }
        return collected;
      } catch (error) {
        showError(error);
        return null;
      } finally {
        inFlight.current = false;
        setLoading(false);
      }
    },
    [api, modal, params, rows, searchParams, showError, sorts],
  );

  // antd 의 message 는 thenable 을 돌려준다. 그대로 흘리면 이 콜백이 Promise 를 내는
  // 것이 되어, 부르는 쪽(어댑터)이 기다려야 하는 값처럼 보인다 — 기다릴 것이 없다.
  const filterReset = useRef<(() => void) | null>(null);
  const registerFilterReset = useCallback((reset: () => void) => {
    filterReset.current = reset;
  }, []);
  const clearFilters = useCallback(() => filterReset.current?.(), []);

  const denyEdit = useCallback(
    (text: string) => {
      message.warning(text);
    },
    [message],
  );

  /*
    <b>같은 모양으로 낸다</b> — 사용자가 알아야 하는 것이 같다(<i>지금 한 입력이 그대로
    남지 않는다</i>). 함수를 따로 두는 것은 뜻이 다르기 때문이며(frontend/grid.md 대 §3.3),
    한쪽의 표현을 바꿀 날이 오면 그때 갈라진다.
  */
  const warnInvalid = useCallback(
    (text: string) => {
      message.warning(text);
    },
    [message],
  );

  /*
    <b>봉투를 매 렌더 새로 만들지 않는다.</b>

    셸은 세션 잔여 시간을 <b>1초마다</b> 다시 그린다({@code useSessionExpiry}) — 그 자체는
    정상이지만, 그때마다 이 객체가 새것이면 화면이 만드는 {@code controller} 파생물도
    새것이 되고, {@code <DataGrid>} 의 컬럼 선언이 다시 계산돼 <b>판에 새 columnDefs 가
    초당 한 번 밀린다</b>. 판은 그것을 선언의 갱신으로 받아 <b>사용자가 끌어 놓은 폭을
    선언값으로 되돌린다</b> — <i>"폭을 조절해도 바로 돌아간다"</i> 가 그것이었다
    (2026-08-30 실측: {@code newColumnsLoaded} 가 1초 간격으로 열 번 찍혔다).

    담긴 것 대부분은 이미 {@code useCallback}·state 라 <b>바뀔 때만 바뀐다</b>.
  */
  return useMemo(
    () => ({
      rows,
      setRows,
      setIssues,
      mode,
      page,
      size,
      total,
      totalPages,
      hasNext,
      hasPrev: prevCursor !== null,
      loading,
      issues,
      dirty,
      canRead,
      canSave,
      canAddRow,
      canEdit,
      savesItself,
      api,
      columns,
      read,
      fetchAll,
      save,
      selected,
      setSelected,
      runAction,
      addRow,
      added,
      edit,
      revert,
      toggleDelete,
      goToPage,
      goNextPage,
      goPrevPage,
      changeSize,
      changeSorts,
      denyEdit,
      warnInvalid,
      clearFilters,
      registerFilterReset,
    }),
    [rows, setRows, mode, page, size, total, totalPages, hasNext, prevCursor, loading, issues,
      dirty, canRead, canSave, canAddRow, canEdit, savesItself, api, columns, read, fetchAll,
      save, selected, runAction, addRow, added, edit, revert, toggleDelete, goToPage, goNextPage,
      goPrevPage, changeSize, changeSorts, denyEdit, warnInvalid, clearFilters,
      registerFilterReset],
  );
}
