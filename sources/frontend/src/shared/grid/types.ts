import type { FieldError } from '@/shared/api';
import type { Action } from '@/shared/authz/permission';
import type { DatePrecision } from '@/shared/grid/dateFormat';
import type { CellIcon } from '@/shared/grid/cellIcons';
import type { GridRow, RowState } from '@/shared/grid/rows';

/**
 * 그리드 어휘 — 54(컬럼) · 55(API) · 56(편집·검증).
 *
 * <p><b>어휘는 우리 것이고 판은 어댑터가 고른다</b>(frontend/grid.md). 화면은 여기 있는 이름만 쓰고
 * AG Grid 의 타입을 직접 만지지 않는다 — 그래야 Community ↔ Enterprise 교체나 판 자체의
 * 교체가 어댑터 한 파일에서 끝난다.
 *
 * <p><b>구현된 것만 담는다.</b> frontend/grid.md 는 타입 열셋을 적었지만 여기 있는 것은 지금 화면이
 * 쓰는 것뿐이다 — 선언할 수 있는데 어댑터가 모르는 값이 있으면 <b>조용히 무시</b>되고,
 * 화면 개발자는 자기가 오타를 냈는지 미구현인지 알 수 없다. 늘리는 자리는 그것을 처음
 * 요구하는 화면이다({@code generateApiSeed} 가 첫 업무 API 를 기다린 것과 같다).
 */

/**
 * frontend/grid.md 의 어휘 중 구현된 것. 타입 열셋 중 아홉이며 나머지는 §5 가 <i>쓰지 않는다</i> 로
 * 닫았거나(`boolean`·`object`) 같은 컬럼의 전송 형태 변형이다(`dateString` 계열).
 *
 * <p><b>`popupRef` 는 <i>골라서 채우는 칸</i> 이다</b>(frontend/grid.md · playbook/INDEX.md). 상품 아이디와
 * 상품명이 각각 컬럼일 때 둘을 따로 입력받으면 <b>불일치가 저장된다</b> — 아이디는
 * `P-001` 인데 이름은 다른 상품인 행이 만들어지고, 그것을 막을 제약이 화면에 없다.
 * 그래서 <b>한 번 고르면 딸려 오는 칸이 함께 채워진다</b>({@link ColumnDef.popup}).
 *
 * <p><b>`bigint` 는 <i>큰 숫자</i> 가 아니라 <i>숫자로 다루면 안 되는 숫자</i> 다</b>
 * (playbook/INDEX.md). JS 의 `number` 는 2^53 을 넘으면 정밀도를 잃는데, 그 손실이 일어나는 곳은
 * 우리 코드가 아니라 <b>{@code JSON.parse}</b> 다 — 서버가 숫자로 내리는 순간 이미 틀린
 * 값이 손에 들어오고, 화면은 자기가 틀린 값을 그리고 있다는 것을 알 방법이 없다. 그래서
 * <b>API 가 문자열로 내린다</b>(frontend/packages.md)는 것이 이 타입의 전부이며, 나머지 규칙은 전부
 * 그 하나에서 따라 나온다: 편집기가 텍스트이고, 정렬을 서버가 하고, 엑셀에 문자열로 쓴다.
 */
export type ColumnType =
  | 'text'
  | 'number'
  | 'bigint'
  | 'popupRef'
  | 'ynFlag'
  | 'code'
  | 'list'
  | 'date'
  | 'dateTime';

/**
 * frontend/grid.md. <b>`richText` 만 남았다</b> — 라이브러리가 미정이며 `D` 로 미뤄졌다
 * (screen/_standalone/README.md): 쓰는 화면이 공지 본문뿐이라 <b>Phase 6 공통 컴포넌트도 만들지
 * 않는다</b>.
 *
 * <p><b>`popupRef` 는 타입이 이미 정한다</b> — {@code type: 'popupRef'} 인 칸의 편집기는
 * 팝업 말고 있을 수가 없고, 다른 타입의 칸이 팝업으로 편집될 일도 없다. 그래서 여기
 * 적는 것은 <b>생략해도 되는 선언</b>이며 적어도 같은 뜻이다(날짜가 그런 것과 같다).
 *
 * <p><b>이름이 판의 것이어도 어휘는 우리 것이다</b>(frontend/grid.md) — `richSelect` 는 AG Grid
 * Enterprise 의 편집기 이름이고 Community 에서는 없다. <b>선언은 그대로 두고</b> 어댑터가
 * 자체 구현({@code SearchSelectCellEditor})을 붙인다: 판을 사는 조직이 화면 소스를
 * 고치지 않아도 되는 것이 그 ADR 의 요구다.
 */
export type EditorType =
  | 'text'
  | 'largeText'
  | 'number'
  | 'switch'
  | 'select'
  | 'popupRef'
  /**
   * 검색 콤보 — frontend/grid.md · playbook/INDEX.md. <b>후보가 스무 개를 넘으면</b> 이것이다.
   *
   * <p>타이핑은 <b>필터이지 입력이 아니다</b> — 목록에 없는 값이 만들어지지 않는다.
   * 그것이 열려 있으면 <i>"저장은 되는데 동작하지 않는"</i> 값이 저장된다(screen/menu/requirements.md).
   */
  | 'richSelect'
  /**
   * 배열 컬럼의 모달 — frontend/grid.md · playbook/INDEX.md. {@link ColumnDef.listModal} 이 창을 정한다.
   */
  | 'listModal'
  /**
   * 긴 텍스트의 <b>넓은 쪽</b> — frontend/grid.md · playbook/INDEX.md. `largeText` 와 <b>같은 어휘의 다른
   * 크기</b>이며 갈리는 것은 자리·크기·<b>글자 수</b> 셋이다.
   */
  | 'textModal';

/**
 * <b>읽기 전용인데 셀에서 다 볼 수 없는 칸</b> — 더블클릭하면 펼쳐 읽는다.
 *
 * <p>{@link EditorType} 과 짝이 아니라 <b>다른 축</b>이다. 저것은 *고치는 방법* 이고
 * 이것은 *읽는 방법* 이며, 둘을 한 이름으로 묶으면 <i>"읽기 전용인데 편집기가 열린다"</i>
 * 가 된다 — 사용자는 고칠 수 있는 줄 알고 타이핑한다.
 *
 * <p>`largeText` 는 frontend/grid.md 의 <b>셀 위 팝업</b>과 같은 모양이되 입력이 잠긴다.
 */
export type ViewerType = 'largeText';

/**
 * frontend/grid.md 의 일곱 중 구현된 여섯.
 *
 * <p><b>`progress` 와 `icon` 이 playbook/INDEX.md 로 왔다.</b> 둘 다 <b>값을 바꾸지 않는다</b> —
 * 막대는 원본 숫자를 그리고 아이콘은 원본 코드를 그린다. 이 규칙이 렌더러 축 전체를
 * 관통하는 것이며(frontend/grid.md), 어기는 순간 정렬·필터·엑셀이 <b>화면과 다른 것</b>을 본다.
 *
 * <p>`duration` 이 screen/admin/system/batch/schedule/requirements.md 가 낸 <b>첫 사례</b>다 — frontend/grid.md 의 <i>"렌더러가 충분한가"</i> 에
 * 답하는 자리이며, <b>숫자를 사람이 읽는 단위로 바꾸는 것</b>이 렌더러의 일이라는
 * 규칙은 이미 있었고 <b>시간만 빠져</b> 있었다.
 *
 * <p><b>값은 원본 그대로다</b>(frontend/grid.md) — 정렬·필터·엑셀이 전부 ms 를 본다.
 * 서버가 포맷한 문자열 컬럼을 따로 주면 그 컬럼의 정렬이 <b>문자열 정렬</b>이 되어
 * `9분` 이 `10분` 보다 뒤에 온다(screen/admin/system/batch/schedule/requirements.md).
 */
export type RendererType = 'badge' | 'tags' | 'link' | 'duration' | 'progress' | 'icon';

/** frontend/grid.md — 색 이름은 닫혀 있다. Hex 를 받지 않는다. */
export type Tone = 'none' | 'ok' | 'warn' | 'err' | 'muted';

/**
 * `badge` 의 색을 정하는 <b>축이 둘</b>이다 — frontend/grid.md.
 *
 * <p>맵은 <b>값이 무엇인가</b>로 고르고, 범위는 <b>값이 어디에 드는가</b>로 고른다.
 * 후자를 맵으로 쓸 수 없는 것이 사용 이력의 `HTTP_STAT` 이다 — 200·201·204·400·403·404·
 * 409·500·503 … 을 전부 적어야 하고, <b>처음 보는 상태 코드는 조용히 색이 없다</b>.
 *
 * <p>범위는 <b>`max` 오름차순으로 훑어 첫 칸</b>이 이긴다. 어느 칸에도 안 들면 칠하지
 * 않는다 — 맵과 같은 규칙이다.
 *
 * <p><b>함수를 열지 않는다</b>(frontend/grid.md). 선언이면 색을 미리 읽을 수 있고, 함수면 그 셀을
 * 그려 봐야 안다.
 */
export type ToneRange = { max: number; tone: Tone }[];

/** 편집 거부에는 메시지가 필수다 — frontend/grid.md. */
export type EditableResult = boolean | { ok: false; message: string };

/**
 * 편집·필수 판단이 보는 문맥 — frontend/grid.md 의 `(row, ctx)`.
 *
 * <p>지금 담긴 것은 <b>행 상태 하나</b>다. *"PK 는 신규 행에서만 열린다"* (screen/admin/system/code/requirements.md)를
 * 표현하려면 그 행이 저장된 것인지 알아야 하는데, 업무 데이터에는 그 사실이 없다.
 *
 * <p>frontend/grid.md 가 *"`ctx` 에 무엇이 들어가나"* 를 미결로 남겼다 — 유효 권한·공통코드·현재
 * 사용자가 후보이며, <b>필요해지는 화면이 하나씩 더한다.</b>
 */
export interface EditContext {
  state: RowState;
}

export interface SelectOption {
  value: string;
  /** 메시지 키가 아니라 <b>완성된 라벨</b>이다 — 화면이 `label()` 로 만들어 넣는다. */
  text: string;
}

/**
 * 참조 팝업 컬럼의 선언 — frontend/grid.md.
 *
 * <p><b>{@code apply} 가 부분 객체를 돌려주는 것이 계약의 중심이다</b>(frontend/grid.md). 여러
 * 컬럼을 한 번에 채우는 것이 <b>기본</b>이고 하나만 채우는 것이 특수한 경우이며, 반환값을
 * 어댑터가 행에 머지하므로 <b>화면 코드가 그리드 API 를 만지지 않는다</b>.
 */
export interface PopupRefSpec<T> {
  /** 팝업 레지스트리의 키 — <b>네임스페이스를 갖는다</b>(`sample.customer` · playbook/INDEX.md). */
  key: string;
  /**
   * 팝업에 넘길 조회 조건을 <b>현재 행에서</b> 만든다 — 창고가 정해졌으면 그 창고의 상품만.
   *
   * <p>없으면 조건 없이 연다.
   */
  param?: (row: T) => unknown;
  /**
   * 고른 행 → <b>이 행에 머지할 부분 객체</b>.
   *
   * <p><b>{@code picked === null} 은 지우는 것이다</b>(frontend/grid.md) — 값을 비웠는데 상품명이
   * 남으면 <b>그것이 곧 불일치</b>이고, 채울 때와 지울 때가 대칭이어야 한다. 팝업의
   * <i>지우기</i> 버튼이 이 경로로 온다(창을 그냥 닫는 것은 여기까지 오지 않는다).
   *
   * <p>머지한 값에도 <b>{@code afterEdit} 가 이어서 돈다</b>(frontend/grid.md) — 단가가 들어왔으면
   * 금액이 계산된다. 셀 편집기 밖에서 온 값이라고 다른 순서를 타면 <b>같은 컬럼이 어떻게
   * 채워졌는가에 따라 다른 행이 된다</b>.
   */
  apply: (row: T, picked: Record<string, unknown> | null) => Partial<T>;
}

export interface ColumnDef<T> {
  /** 판의 {@code colId} 이기도 하다 — 동적 컬럼은 `ref.{REF_CD}` 를 쓴다(frontend/grid.md). */
  field: string;
  /** 메시지 키 — sys/code.md. <b>{@link ColumnDef.dynamic} 만 예외</b>이며 완성된 문자열이다(frontend/grid.md). */
  header: string;
  /**
   * 헤더에 붙는 한 줄 — <b>이 컬럼을 어디까지 믿을 수 있나</b>를 말하는 자리다.
   *
   * <p>screen/admin/system/log/access/requirements.md 이 처음 요구했다. 사용 이력의 `메뉴` 는 <b>클라이언트가 밝힌 값</b>이고
   * (backend/packages.md) 인가에 쓰이지 않는데, 그 사실을 화면이 말하지 않으면 <b>메뉴별 집계가 감사
   * 근거로 인용된다</b>.
   *
   * <p>메시지 키다. 셀 툴팁이 아니라 <b>헤더</b>이며, 값에 대한 설명이 아니라
   * <b>컬럼에 대한 설명</b>이 여기 온다.
   */
  headerTooltip?: string;
  /**
   * 셀에 <b>값 전체</b>를 툴팁으로 붙인다 — screen/admin/system/log/access/requirements.md 가 처음 요구했다.
   *
   * <p>{@link headerTooltip} 과 축이 다르다. 저것은 <i>컬럼이 무엇인가</i> 를 한 번
   * 말하고, 이것은 <b>이 칸에 지금 담긴 값</b>을 그대로 보여준다 — 폭보다 긴 값이라
   * 셀에서는 잘려 있기 때문이다.
   *
   * <p><b>`viewer` 와 갈리는 자리다.</b> 저것은 값이 셀에 <i>없을 때</i>(`nclob`) 눌러서
   * 여는 것이고(frontend/grid.md), 이것은 값이 <b>이미 손에 있을 때</b> 폭만 모자란 경우다 —
   * 로그인 이력의 `User-Agent` 가 그렇다: `VARCHAR(500)` 이라 목록에 실려 있다.
   *
   * <p>검증 오류가 있는 칸에서는 <b>오류 문구가 이긴다</b>(frontend/grid.md) — 무엇이 틀렸는지가
   * 값을 다시 읽는 것보다 급하다.
   */
  cellTooltip?: boolean;
  /**
   * 컬럼이 <b>데이터에서 온다</b> — frontend/grid.md. `CODE_MGMT` 의 참조 코드가 첫 실물이다(screen/admin/system/code/requirements.md).
   *
   * <p>셋이 달라진다.
   *
   * <ul>
   *   <li>{@link ColumnDef.header} 가 <b>메시지 키가 아니다</b> — 정의 행의 `REF_NM` 이며
   * 관리 화면 전용 명칭이라 다국어가 아니다(sys/code.md)
   * <li>그리드 설정 창과 <b>시그니처에서 빠진다</b>(frontend/grid.md) — 그룹을 옮길 때마다 컬럼
   *       집합이 바뀌는데, 그것으로 사용자의 저장된 설정을 폐기하면 <b>설정이 저장되지
   *       않는 화면</b>이 된다
   *   <li>값이 컬럼 하나가 아니라 <b>맵의 한 칸</b>이라 {@link ColumnDef.derive} 로 읽고
   *       {@link ColumnDef.patch} 로 쓴다
   * </ul>
   */
  dynamic?: boolean;
  type?: ColumnType;
  width?: number;
  pinned?: 'left' | 'right';
  /** 초기 표시. 끄면 그리드 설정에서 켤 수 있다 — frontend/grid.md. */
  visible?: boolean;
  /**
   * <b>남는 가로 폭을 이 컬럼이 채운다</b> — frontend/grid.md.
   *
   * <p>컬럼 폭의 합이 그리드보다 좁으면 오른쪽에 <b>빈 띠</b>가 남고, 그것이 마지막 컬럼과
   * 판의 경계 사이에 끼면 <i>컬럼 사이가 벌어진 것</i>처럼 보인다. 긴 글이 들어가는 칸
   * 하나가 그 자리를 먹는 것이 자연스럽다.
   *
   * <p>{@link width} 는 <b>최소 폭</b>이 된다 — 좁은 화면에서 이 컬럼만 뭉개지지 않는다.
   */
  fill?: boolean;
  /** 켤 수단이 없다. 설정 창 목록에도 없다 — frontend/grid.md. */
  hidden?: boolean;
  /** 서버 화이트리스트에 `.sortable()` 이 있어야 실제로 열린다 — frontend/grid.md. */
  sortable?: boolean;
  /** 컬럼 필터. 생략하면 타입이 정한다 — frontend/grid.md. */
  filter?: boolean;
  editor?: EditorType;
  /**
   * 날짜 계열의 <b>자릿수</b> — frontend/grid.md. `date` · `dateTime` 컬럼에만 뜻이 있다.
   *
   * <p>없으면 <b>타입의 기본</b>이다(`date` → `day`, `dateTime` → `second`). 년월은 타입으로
   * 표현되지 않으므로 선언해야 나온다 — 타입 이름이 이미 <i>값의 형태</i> 와 <i>자릿수</i> 를
   * 섞고 있어(`date` vs `dateTime`) 이 필드가 그 축을 밖으로 뺀다.
   *
   * <p><b>표시·필터·달력이 함께 따라간다.</b> `month` 면 년월 달력이 뜨고 일을 고를 수
   * 없으며, 셀에 그려지는 것도 `YYYY-MM` 이다. 형식 문자열 자체는 시스템 설정이 정한다
   * (`shared/grid/dateFormat`).
   */
  precision?: DatePrecision;
  /**
   * 셀에서 다 볼 수 없는 <b>읽기 전용</b> 칸을 펼쳐 읽는 방법 — frontend/grid.md.
   *
   * <p>{@link editor} 를 쓸 수 없는 자리다. 편집기를 열면 <b>고칠 수 있는 것처럼</b>
   * 보이고, screen/admin/system/code/requirements.md 의 설명 컬럼처럼 <i>편집하면 값을 안 바꿨는데 행이 생기는</i> 칸에서는
   * 그것이 곧 사고다.
   */
  viewer?: ViewerType;
  /**
   * 이 컬럼이 <b>PK 인가</b> — 셋을 한 번에 정한다(frontend/grid.md · screen/admin/system/code/requirements.md).
   *
   * <ul>
   *   <li><b>신규 행에서만</b> 열린다 — 저장된 PK 를 고치는 것은 <i>다른 행을 만드는 일</i>
   *       이며, 그 저장은 서버에서 <b>없는 행 수정</b>이 된다(신규도 아니라 만들어지지도
   *       않는다)
   *   <li><b>필수</b>다
   *   <li>헤더에 <b>필수 표시</b>가 붙는다
   * </ul>
   *
   * <p><b>{@link ColumnDef.editable} · {@link ColumnDef.required} 를 함께 쓰지 않는다.</b>
   * 덮어쓸 수 있으면 기본이 기본이 아니고, 그 셋을 화면마다 손으로 적으면 <b>빠뜨려도
   * 조용하다</b>. 예외적으로 PK 를 고치는 화면은 `{ renamable: true }` 로 <b>명시해서</b>
   * 연다 — 기본이 잠그는 쪽이라 빠뜨렸을 때 덜 위험한 쪽으로 떨어진다(backend/security.md 와 같은 방향).
   *
   * <p><b>편집이 아예 없는 PK 는 이것이 아니다.</b> screen/admin/system/code/requirements.md 의 `msgKey` 처럼 신규 행조차
   * 없는 화면은 `editable: false` 로 적는다 — 채울 수 없는 칸에 필수 표시를 붙이면
   * <i>왜 안 되나</i> 를 묻게 된다.
   */
  pk?: boolean | { renamable: boolean };
  /** <b>기본은 `false`</b> — 편집은 명시해야 열린다(frontend/grid.md). */
  editable?: boolean | ((row: T, ctx: EditContext) => EditableResult);
  /**
   * 헤더의 `*` 와 셀 배경과 검증이 <b>이 하나</b>에서 나온다 — frontend/grid.md.
   *
   * <p><b>헤더의 `*` 는 `true` 일 때만 붙는다.</b> 함수는 행마다 갈리므로 헤더가 말하면
   * <b>거짓말이 된다</b> — 필수가 아닌 행에서도 별표가 보인다.
   */
  required?: boolean | ((row: T, ctx: EditContext) => boolean);
  renderer?: RendererType;
  /**
   * `badge` · `progress` · `icon` 의 색 — <b>맵이거나 범위이지 함수가 아니다</b>(frontend/grid.md).
   *
   * <p>렌더러가 축을 고른다: 값이 <b>무엇인가</b>로 고르는 것(`badge` · `icon`)은 맵을 쓰고,
   * 값이 <b>어디에 드는가</b>로 고르는 것(`progress`)은 범위를 쓴다. 반대로 적으면
   * 조용히 칠해지지 않는다 — 맵에 없는 값과 어느 칸에도 안 드는 값이 같은 결과다.
   */
  tone?: Record<string, Tone> | ToneRange;
  /**
   * `renderer: 'icon'` 의 <b>값 → 아이콘</b> — playbook/INDEX.md.
   *
   * <p><b>{@link tone} 과 같은 축이되 다른 것을 정한다</b>: 저것이 색이고 이것이 모양이다.
   * 하나로 묶지 않은 것은 <i>같은 아이콘에 다른 색</i>(대기 = 회색 시계, 지연 = 붉은 시계)이
   * 실제로 흔하기 때문이며, 묶으면 그 조합마다 이름을 새로 지어야 한다.
   *
   * <p><b>맵에 없는 값은 아이콘이 없다</b> — 그 셀은 값을 글자로 그린다(frontend/grid.md 의
   * <i>맵에 없는 값은 칠하지 않는다</i> 와 같은 규칙). 물음표를 그리지 않는 이유는
   * {@code cellIcons.tsx} 머리말에 있다.
   *
   * <p>툴팁은 <b>화면에 보이던 글자</b>다 — {@link display} 가 있으면 그것, 없으면
   * {@link options} 의 이름, 그것도 없으면 값 자체다. 아이콘만 남으면 <b>색맹·흑백
   * 인쇄·모르는 아이콘</b> 셋 다에서 그 칸이 읽히지 않는다.
   */
  icons?: Record<string, CellIcon>;
  /**
   * 참조 팝업 — <b>한 번 골라 여러 칸을 채운다</b>(frontend/grid.md · playbook/INDEX.md).
   *
   * <p>세 조각이 <b>한 객체</b>인 것이 결정이다. {@code apply} 는 <i>어느 팝업에서 고른
   * 행인가</i> 를 알아야 뜻이 있고, {@code param} 은 그 팝업의 조회 조건이다 — 따로 두면
   * <b>{@code apply} 만 적은 컬럼</b>이 컴파일을 통과하고, 그 칸은 눌러도 아무 일이 없다.
   */
  popup?: PopupRefSpec<T>;
  /**
   * `editor: 'listModal'` 이 여는 창 — frontend/grid.md · playbook/INDEX.md.
   *
   * <p><b>{@code key} 가 가리키는 것은 항목을 <i>고르는</i> 팝업</b>이며 §9.4 와
   * <b>같은 레지스트리</b>다. frontend/grid.md 초안은 <i>모달 자체</i> 를 레지스트리에서 고르라고
   * 적었는데(바코드 스캔 창과 검사 항목 창은 생긴 것이 다르다), 갈아 낸 결과 <b>다른 것은
   * 고르는 창</b>이고 <i>쌓고 순서를 바꾸는</i> 부분은 어느 배열에서나 같았다 — 껍데기까지
   * 화면이 만들면 playbook/INDEX.md 이 요구한 <b>순서 변경</b>이 창마다 다시 만들어진다.
   *
   * <p>키 하나인데 객체인 것은 frontend/grid.md 의 선언 모양 그대로이며, {@link popup} 과 <b>같은
   * 자리에 같은 모양</b>으로 서기 위해서다.
   */
  listModal?: { key: string };
  /**
   * `editor: 'textModal'` 의 <b>글자 수 상한</b> — frontend/grid.md · playbook/INDEX.md.
   *
   * <p><b>검증이 아니라 선언인 이유</b>는 frontend/grid.md 이 정한 기준 그대로다: <i>표시에
   * 나타나면 선언</i>이다. 이 값은 창 아래 <b>{@code n / 상한}</b> 으로 그려지며, 없으면
   * `showCount` 가 센 수만 보여 <b>얼마나 남았는지</b> 를 말하지 못한다 — 그 카운터가
   * `largeText` 와 갈리는 유일한 지점이다.
   *
   * <p>업무 규칙(<i>"채널이 A 면 200자"</i>)은 여전히 {@link validate} 다. 여기 있는 것은
   * <b>더 칠 수 없는 자리</b>이지 무엇이 옳은가가 아니다.
   */
  maxLength?: number;
  /**
   * `renderer: 'link'` 의 클릭 — frontend/grid.md.
   *
   * <p><b>링크가 곧 값은 아니다.</b> screen/admin/system/log/access/requirements.md 의 <i>전문</i> 컬럼이 그 사례로, 값은
   * `Y`/`N`(전문이 있는가)이고 셀에 그려지는 것은 `[보기]` 다 — 그래서 {@link display} 가
   * 함께 온다. 엑셀은 <b>원본 값</b>을 쓴다(frontend/grid.md).
   *
   * <p>선언하지 않으면 <b>누를 수 없는 글자</b>다 — 링크처럼 보이는데 아무 일도 안 하는
   * 셀을 만들지 않으려고, 어댑터가 `onClick` 없는 `link` 를 보통 텍스트로 그린다.
   */
  onClick?: (row: T) => void;
  /** 행의 실행 조건이 충족되지 않으면 링크를 비활성화한다. 현재 서버 권한 검사는 별도다. */
  clickEnabled?: (row: T) => boolean;
  /**
   * 이 <b>칸</b>을 더블 클릭했을 때 — screen/admin/system/log/access/requirements.md.
   *
   * <p>`<DataGrid>` 의 `onRowDoubleClick` 과 <b>다른 축</b>이다. 저것은 *이 행이 가리키는
   * 것을 연다* 이고(screen/menu/requirements.md — 행 하나가 곧 메뉴다), 이것은 *이 칸의 값이 가리키는 것을
   * 연다* 이다.
   *
   * <p><b>축을 가르는 것이 규약이다.</b> screen/admin/system/log/access/requirements.md 가 `요청 URI` 를 셀로 둔 이유가 그것인데,
   * 그 화면들에서 <b>행 더블 클릭은 이미 상세 모달의 제스처</b>라 같은 자리에 둘을 겹치면
   * 하나가 다른 하나를 가린다. 행으로 붙여 두면 `ERROR_LOG` 가 서는 날 그 컬럼만 셀로
   * 옮겨야 하고, 그 순간 **같은 컬럼이 화면마다 다르게 동작한다**.
   *
   * <p>우선순위는 {@link viewer} 가 먼저다 — 펼쳐 읽는 칸에서 사람이 기대하는 것은
   * *이 긴 글을 읽겠다* 이지 *다른 화면으로 가겠다* 가 아니다.
   */
  onDoubleClick?: (row: T) => void;
  /**
   * 이 행에서 <b>값을 굵게</b> 낼 것인가 — screen/admin/system/code/requirements.md 이 처음 요구했다.
   *
   * <p>{@link tone} 과 다른 축이다. 저것은 <b>값이 무엇인가</b>로 색을 고르는 맵이고,
   * 이것은 <i>"이 행에서 그 값이 특별한가"</i> 를 묻는다 — *적용값이 기본값과 다른가* 는
   * 같은 값이라도 행마다 답이 갈리므로 맵으로 표현할 수 없다.
   */
  emphasis?: (row: T) => boolean;
  /**
   * 값 <b>뒤에</b> 붙는 배지 — 값이 아니라 그 값의 <b>내력</b>을 말한다(screen/admin/system/code/requirements.md).
   *
   * <p>{@link renderer}`: 'badge'` 는 값 <i>대신</i> 배지를 그리므로 이 자리를 대신하지
   * 못한다. *적용값* 셀은 값과 <span>변경됨</span> 을 <b>함께</b> 내야 하며, 값이 기본값과
   * 같아도 배지가 붙는 경우가 있어 <b>배지가 유일한 신호</b>다.
   *
   * <p><b>둘을 함께 선언할 수는 있다</b>(screen/admin/system/batch/schedule/requirements.md). 실행 이력의 *결과* 셀이 그것이며,
   * 거기서는 배지가 곧 값이고(`진행 중`) 이 자리가 그 값의 <b>내력</b>을 덧붙인다
   * (`유령` — 락 만료를 넘겼다). 값이 사라지지 않으므로 위 문단의 금지가 걸리지 않는다.
   *
   * <p>`tip` 은 <b>왜 그 배지가 붙었는지</b>다 — screen/admin/system/log/access/requirements.md 이 *꺼진 것에는 이유를 붙인다*
   * 로 적은 규칙의 같은 축이다. `유령` 세 글자만으로는 <i>무엇을 하면 되는지</i> 가
   * 읽히지 않는다.
   */
  badge?: (row: T) => { text: string; tone: Tone; tip?: string } | null;
  /** `select` 에디터와 `code` 타입의 후보. */
  options?: SelectOption[];
  /**
   * 후보를 <b>공통코드에서</b> 받는다 — sys/code.md. `options` 를 함께 적으면 그쪽이 이긴다.
   *
   * <p>배지·셀 에디터·컬럼 필터가 <b>같은 목록</b>을 본다 — 판에 닿기 전에 후보가 채워지므로
   * 셋이 따로 물을 자리가 없다. 조회조건의 `SearchField.grpCd` 와 같은 축이다(frontend/search-form.md 의
   * *어휘를 새로 만들지 않는다*).
   */
  grpCd?: string;
  /**
   * `list` 항목 하나 → 표시 문자열 — frontend/grid.md.
   *
   * <p><b>`link` 도 이것을 쓴다</b> — 거기서는 항목이 아니라 <b>셀의 값</b>이 들어온다.
   * 어휘를 하나 더 만들지 않는 것이 규칙이며(frontend/grid.md), 두 렌더러 다 <i>값을 글자로
   * 바꾸는 방법</i> 을 묻는다.
   */
  display?: (item: unknown) => string;
  /**
   * 파생 컬럼의 값 — 저장되는 컬럼이 아니다(frontend/grid.md 의 *정렬 끄는 대상*).
   *
   * <p>screen/admin/system/code/requirements.md 의 <i>번역</i> 컬럼처럼 <b>`list` 에서 계산</b>하는 칸이 여기 온다. 서버가
   * 내려주면 같은 규칙이 두 곳에 생기고, 셀에서 계산하면 렌더 함수를 여는 것이다.
   */
  derive?: (row: T) => unknown;
  /**
   * 편집한 값을 <b>행에 되쓰는 방법</b> — frontend/grid.md 의 `valueSetter`. 없으면 {@link
   * ColumnDef.field} 에 그대로 쓴다.
   *
   * <p>동적 컬럼이 이것을 요구했다 — 참조값은 저장되는 컬럼 하나가 아니라 `refs` <b>맵의
   * 한 칸</b>이고, 그 맵이 곧 저장 페이로드다.
   *
   * <p><b>입력 시점의 재검증은 패치의 키를 따라간다</b>(frontend/grid.md) — 맵 하나를 통째로 바꾸는
   * 컬럼은 그 맵 이름이 바뀐 칸이 되므로, `validate`·`required` 를 함께 선언하지 않는다.
   */
  patch?: (row: T, value: unknown) => Partial<T>;
  /** 형식·범위·다른 컬럼에 따른 분기 — frontend/grid.md 의 1층. */
  validate?: (value: unknown, row: T) => string | null;
  /** 계산 컬럼. <b>연쇄는 한 단계에서 멈춘다</b> — frontend/grid.md. */
  afterEdit?: (row: T) => Partial<T> | null;
}

/** frontend/grid.md 의 3층 — 행을 종합해야 아는 것. `field` 는 선택이다. */
export interface GridIssue {
  rowId: string;
  field?: string;
  message: string;
}

export interface ApiValidation<T> {
  /** 2층 — 그 API 호출 전에만 도는 컬럼 검증. */
  columns?: Record<string, (value: unknown, row: T) => string | null>;
  /**
   * 3층 — 합계 · 중복 키 · 최소 1건. <b>삭제 행을 뺀 결과로 계산한다</b>(frontend/grid.md).
   *
   * <p>업무 데이터가 아니라 <b>행 봉투</b>를 받는다. 결과가 `rowId` 를 나르는데 데이터에는
   * 그 값이 없기 때문이며, 없으면 *"어느 행이 잘못인가"* 에 답하지 못한다.
   */
  grid?: (rows: GridRow<T>[]) => GridIssue[];
}

export interface ReadApi {
  url: string;
  /** 0건이면 알린다. 마스터는 켜고 디테일은 끈다 — frontend/grid.md. */
  emptyNotice?: boolean;
}

/**
 * <b>행마다 커밋되는 처리</b>가 끝난 뒤 그리드를 어떻게 하나 — backend/row-transaction.md §7.
 *
 * <p><b>기본값이 없다.</b> 정답이 하나가 아니기 때문이다 — 서버가 채운 값을 보여 줘야 하는
 * 화면과 실패한 편집을 지키는 화면이 반대 방향을 원한다. 기본값을 두면 <b>아무도 고르지
 * 않고</b> 둘 중 하나가 조용히 틀린 화면이 된다.
 *
 * <p><b>전건 성공은 이 선언과 무관하다</b> — 남길 실패가 없으므로 평소의 {@code reloadAfter}
 * 규칙대로 보던 자리를 다시 읽는다.
 */
export interface PerRowSpec {
  /**
   * <b>실패가 남았을 때</b> — 둘 중 하나를 반드시 적는다.
   *
   * <table><caption></caption>
   * <tr><td>{@code 'reload'}</td><td>보던 자리를 다시 읽는다. <b>서버가 정한 값</b>(상태·번호·
   *     일시)이 화면에 없어 그것을 보여 줘야 할 때다. <b>화면의 편집은 사라진다</b> — 성공한
   *     것뿐 아니라 <i>실패한 행의 편집도</i> 함께다</td></tr>
   * <tr><td>{@code 'keepFailed'}</td><td><b>실패한 행만 남기고 목록을 비운다.</b> 성공한 행도,
   *     손대지 않은 행도 사라진다 — 남는 것이 곧 <b>고쳐서 다시 낼 것</b>이고, 사용자는 이미
   *     한 일을 다시 고르지 않아도 된다. 비웠다는 사실은 결과 모달이 말한다</td></tr>
   * </table>
   *
   * <p><b>{@code 'keepFailed'} 는 순번 대응이 서는 화면에만 쓴다.</b> 저장이
   * {@code toSavePayload} 로 배열을 직접 만들면 배관은 보낸 것과 화면의 행을 잇지 못하고,
   * 그때는 <b>비우지 않고 표만 낸다</b>(개발자 콘솔에 남는다).
   */
  after: 'reload' | 'keepFailed';
}

export interface SaveApi<T> {
  url: string;
  /** @feat FEAT-006 — 소유 API가 독립 명령을 차례로 보내는 전송. 권한은 url 선언으로 유지한다. */
  submit?: (payload: unknown) => Promise<unknown>;
  /** <b>기본 `true`</b> — 건수를 알리고 묻는다(frontend/grid.md). */
  confirm?: boolean;
  /** <b>기본 `true`</b> — 서버가 채운 값이 화면에 없다(frontend/grid.md). */
  reloadAfter?: boolean;
  /**
   * 이 저장이 <b>행마다 커밋된다</b> — backend/row-transaction.md.
   *
   * <p>서버가 {@code RowReport} 를 200 으로 돌려주고, 실패가 있으면 성공 모달 대신
   * <b>결과 표</b>가 뜬다(건수 + 사유). <b>선언이 이긴다</b> — 응답 모양을 훔쳐보고 정하면
   * 서버가 칸 하나를 바꾼 날 화면이 조용히 다른 길로 간다.
   */
  perRow?: PerRowSpec;
  validate?: ApiValidation<T>;
  /**
   * 건수 확인 <b>앞</b>에 서는 경고 — screen/admin/system/code/requirements.md 가 처음 요구했다.
   *
   * <p>건수는 <i>얼마나</i> 를 말할 뿐이라 <b>무엇이 일어나는지</b> 를 말하지 못한다.
   * *"이 키를 기본값 5 로 되돌립니다"* 와 *"끄면 잠긴 계정을 풀 수 없습니다"* 가 그런
   * 것들이며, 둘 다 <b>되돌릴 수는 있지만 되돌릴 사람이 못 들어올 수</b> 있다.
   *
   * <p>빈 배열이면 뜨지 않는다. 거절하면 저장하지 않는다.
   *
   * <p><b>비동기여도 된다</b>(2026-08-30 — screen/menu/requirements.md). 역할 화면의 확인은 <b>세 줄</b>인데
   * 셋째가 *"그중 2명은 유효 권한이 공집합이 되어 다음 로그인부터 차단됩니다"* 이며, 그
   * 계산의 입력은 <b>그리드가 들고 있지 않다</b> — 보유자 전원의 다른 역할과 개인 예외를
   * 물어야 한다. 건수만 내고 이름을 빼면 *"그 2명이 누구지"* 를 찾으러 다른 화면에 가야
   * 하므로, 여기서 묻는 것이 <b>확인 문구가 스스로 완결되게</b> 하는 유일한 길이다.
   */
  warn?: (rows: GridRow<T>[]) => string[] | Promise<string[]>;
  /**
   * 서버가 돌려준 필드 오류를 <b>어느 행의 어느 칸</b>에 붙일지 — frontend/grid.md.
   *
   * <p>서버의 `field` 는 <b>업무 식별자</b>(예: `CFG_KEY`)이고 그리드의 실패 표시는
   * <b>행 id</b> 로 붙는다. 둘을 잇는 규칙은 화면마다 다르므로 화면이 준다 — 없으면
   * 문구는 모달로만 나가고 셀은 칠해지지 않는다.
   */
  locateFieldErrors?: (fields: FieldError[], rows: GridRow<T>[]) => GridIssue[];
  /**
   * 저장이 끝난 뒤 — <b>재조회 다음</b>이다.
   *
   * <p><b>서버 응답이 인자로 온다</b>(2026-08-30 — screen/admin/system/user/requirements.md 가 처음 요구했다). 대개
   * {@code null} 이지만 <b>재조회로는 얻을 수 없는 값</b>을 저장이 만들어 내는 화면이 있다 —
   * 신규 계정의 임시 비밀번호가 그것이며, DB 에는 해시가 들어가 다시 물어볼 수 없다.
   *
   * <p>쓰지 않는 화면은 인자를 무시하면 된다.
   */
  onSuccess?: (result: unknown) => void;
}

/**
 * `read` · `save` 가 아닌 <b>그 밖의 모든 버튼</b> — frontend/grid.md. <b>screen/admin/system/user/requirements.md 가 처음 요구했다.</b>
 *
 * <p>`controls`와 갈리는 자리는 하나다 — <b>서버를 부르는가</b>. 부르면 여기다
 * (frontend/grid.md — *"그래도 서버를 부르면 `actions` 다"*).
 *
 * <p><b>액션 코드를 선언하지 않는다.</b> `controls` 는 `actionCd` 를 손으로 적지만 여기는
 * <b>`url` 이 그것을 말한다</b> — 경로 접두가 액션을 정하고(backend/api.md) 그 규칙이 시드를
 * 만든다. 선언에 또 적으면 <b>같은 사실이 두 곳</b>이 되어 시드와 어긋난다.
 *
 * <p><b>이름이 `execute` 가 아니다.</b> 액션 `EXECUTE`(`X`)와 섞이기 때문이며 —
 * *잠금 해제*는 `actions` 의 하나이지만 액션은 `SAVE` 다(screen/admin/system/user/requirements.md).
 */
export interface ActionApi<T> {
  /** 화면 안에서만 유일하면 된다 — 버튼의 `key` 다. */
  key: string;
  /** 메시지 키. */
  label: string;
  /** 액션 코드가 여기서 도출된다 — backend/api.md. */
  url: string;
  /**
   * 무엇을 보내나 — frontend/grid.md. 기본은 `'selected'` 다.
   *
   * <p>`'selected'` 이면 <b>선택 0건일 때 버튼이 비활성</b>이다 — 눌렀는데 *"선택하세요"*
   * 가 뜨는 것보다 <b>누를 수 없는 것이 먼저 보이는 편</b>이 낫다.
   *
   * <p>`'none'` 은 행과 무관한 실행이다. 확인 문구에 건수를 넣지 않는다.
   */
  target?: 'selected' | 'all' | 'none';
  /**
   * 고른 것 중 <b>실제로 대상인 행</b> — 없으면 고른 것이 전부다.
   *
   * <p>`target` 이 <i>어느 묶음을 보나</i> 라면 이것은 <b>그 안에서 무엇이 대상인가</b>다.
   * *실행 중인 배치만 중단할 수 있다* 처럼 <b>행의 값이 대상 여부를 정하는</b> 실행이 있고,
   * 그것을 `toTargets` 안에서 걸러 내면 <b>확인 문구의 건수가 실제 실행 건수와 어긋난다</b>
   * — *"3건을 실행합니다"* 라고 묻고 1건만 나간다.
   *
   * <p>그래서 <b>①(대상 확인)에서 걸린다</b>(frontend/grid.md) — 걸러진 결과가 비면 버튼이 아예
   * 비활성이고, 확인 문구도 `params` 모달의 기본값도 그 결과를 본다.
   *
   * <p><b>서버가 다시 본다.</b> 여기서 거르는 것은 UX 이고 판정은 서버다(frontend/grid.md) — 화면이
   * 고른 사이에 그 행의 상태가 바뀔 수 있다.
   */
  eligible?: (row: GridRow<T>) => boolean;
  /**
   * 대상 행 → 요청의 `targets`. 없으면 <b>행 데이터 그대로</b>다.
   *
   * <p>`toSavePayload` 와 같은 이유로 화면이 준다 — 서버가 무엇으로 행을 가리키는지는
   * 화면마다 다르고, 대개 <b>키 하나</b>면 된다.
   *
   * <p><b>거르는 자리가 아니다</b> — 그것은 `eligible` 이다. 여기서 줄이면 건수가 어긋난다.
   */
  toTargets?: (rows: GridRow<T>[]) => unknown;
  /**
   * 모달·폼이 만드는 <b>호출의 인자</b> — frontend/grid.md. `null` 이 취소다.
   *
   * <p><b>있으면 확인(`confirm`)을 겸한다</b> — 모달이 건수를 표시하고 실행/취소를 두므로
   * 둘 다 켜면 같은 것을 두 번 묻는다.
   *
   * <p>반환 타입이 `Promise<unknown>` 인 것은 `unknown` 이 `null` 을 이미 포함하기
   * 때문이다. <b>`null` 이 취소라는 규약은 그대로다</b>(frontend/grid.md) — 예외를 던지게 하면
   * 취소와 오류가 같은 통로로 오고, 취소마다 에러 모달이 뜬다.
   */
  params?: (rows: GridRow<T>[]) => Promise<unknown>;
  /**
   * <b>기본 `true`</b> — *"선택한 N건을 실행하시겠습니까?"*. `params` 가 있으면 무시된다.
   *
   * <p><b>메시지 키를 주면 그 문구가 대신 나간다</b>(`{count}` 가 대상 건수다).
   * screen/admin/system/batch/schedule/requirements.md 의 <b>중단</b>이 그 자리다 — 협조적 중단이라 확인이
   * *"중단합니다"* 가 아니라 <i>"중단을 <b>요청</b>합니다. <b>즉시 멈추지 않을 수
   * 있습니다.</b>"</i> 여야 하고, 그것을 말하지 않으면 <b>안 멈췄을 때 화면이 거짓말한
   * 것</b>이 된다.
   *
   * <p>일반 문구가 못 하는 일이라 여는 것이다 — <i>무엇을 몇 건</i> 은 틀에서 나오지만
   * <b>그 실행이 무엇을 보장하지 않는가</b> 는 그 실행만 안다.
   */
  confirm?: boolean | string;
  /**
   * <b>왜 눌리지 않는가</b> — 메시지 키. 툴팁으로 붙는다. `GridControl.hint` 와 같은 자리다.
   *
   * <p>screen/admin/system/user/requirements.md 이 요구했다. 자기 계정의 세션은 끊을 수 없는데(그 자리에서 화면 밖으로
   * 밀려난다) 그것은 <b>권한이 아니라 대상 제약</b>이라 감추면 *"왜 이 사람만 안 되나"* 가
   * 된다 — **보이되 막고 사유를 붙인다**.
   *
   * <p>비활성 사유가 <i>선택이 0건</i> 하나뿐인 실행에는 두지 않는다. 무엇이 모자란지가
   * 화면에 이미 보이기 때문이며, 그때 툴팁은 같은 말을 한 번 더 하는 것이다(frontend/grid.md).
   */
  hint?: string;
  /** <b>기본 `true`</b> — 서버가 바꾼 값이 화면에 없다(frontend/grid.md 과 같은 꼬리). */
  reloadAfter?: boolean;
  /**
   * 이 실행이 <b>행마다 커밋된다</b> — backend/row-transaction.md.
   *
   * <p>{@code SaveApi.perRow} 와 같은 칸이다. <b>저장이냐 실행이냐가 가르지 않는다</b> —
   * 가르는 것은 <i>실패했을 때 무엇이 남아야 하는가</i> 하나다.
   *
   * <p>{@code progress} 와 함께 켜지 않는다 — 진행률은 <i>접수했다</i> 로 답하고 이쪽은
   * <i>끝났고 이러이러하다</i> 로 답한다. 둘을 함께 켜면 진행률이 이긴다.
   */
  perRow?: PerRowSpec;
  /** 실행 <b>앞</b>에 도는 검증 — 미저장 변경을 막는 것도 여기다(frontend/grid.md). */
  validate?: ApiValidation<T>;
  /**
   * 이 실행이 <b>오래 걸린다</b> — backend/crosscutting.md · playbook/INDEX.md.
   *
   * <p>서버는 {@code jobId} 를 <b>즉시</b> 돌려주고 요청 스레드를 붙잡지 않는다. 그리드가
   * 그 값을 진행률 훅에 넘기므로 <b>화면은 폴링 코드를 쓰지 않는다</b> — frontend/grid.md 이 렌더
   * 함수를 열지 않은 것과 같은 이유이며, 화면마다 주기·중단 조건·실패 처리가 갈리면
   * <b>같은 기능이 화면 수만큼</b> 생긴다.
   *
   * <p><b>성공 모달이 뜨지 않는다.</b> 여기서 성공은 <i>접수됐다</i> 이지 <i>끝났다</i> 가
   * 아니며, 그것을 <i>실행이 완료되었습니다</i> 로 말하면 <b>화면이 거짓말한 것</b>이다.
   *
   * <p><b>{@code reloadAfter} 는 {@code DONE} 뒤다</b>(playbook/INDEX.md) — 도는 중에 재조회하면
   * 절반만 반영된 목록을 본다.
   */
  progress?: boolean;
  /**
   * 서버가 돌려준 것 — <b>{@code save} 에는 없는 자리</b>다.
   *
   * <p>임시 비밀번호 발급이 그 실물이다(screen/admin/system/user/requirements.md) — 발급된 값은 <b>그 응답에만</b> 있고
   * 재조회로는 다시 얻을 수 없다.
   *
   * <p>{@link progress} 인 실행에서는 <b>{@code jobId} 를 담은 응답</b>이 온다 — 진행률의
   * 끝이 아니라 <i>접수</i> 의 응답이다.
   */
  onSuccess?: (result: unknown) => void;
}

/**
 * 그리드가 하는 일은 전부 여기 있다 — frontend/grid.md.
 */
export interface GridApi<T> {
  /**
   * <b>없을 수 있다</b>(2026-08-30 — screen/admin/system/user/requirements.md 가 처음 요구했다).
   *
   * <p>조회를 <b>다른 응답이 대신 먹이는</b> 그리드가 있다. 개인 예외 목록이 그것으로,
   * 트리와 <b>같은 데이터의 두 표시</b>라 원본이 하나여야 하고(screen/admin/system/user/requirements.md) 그 하나는 트리
   * 조회다 — 목록에 자기 조회를 두면 <b>같은 질문에 답이 둘</b>이 된다.
   *
   * <p>screen/admin/system/code/requirements.md 의 <i>{@code api} 가 아예 없는 그리드</i> 와 <b>다른 자리</b>다. 저기는 조회도
   * 저장도 없어 크롬이 통째로 비지만, 여기는 <b>저장 단위</b>라 저장 버튼과 행 상태 컬럼이
   * 있어야 한다(frontend/grid.md).
   *
   * <p>없으면 조회 버튼이 나오지 않고, 엑셀 <i>전체</i> 는 <b>화면이 든 행 전부</b>가 된다 —
   * 서버 페이징이 없으므로 그것이 실제로 전량이다.
   */
  read?: ReadApi;
  save?: SaveApi<T>;
  /** 그 밖의 모든 버튼 — <b>선언 순서가 곧 화면 순서</b>다(frontend/grid.md). */
  actions?: ActionApi<T>[];
}

/**
 * 삭제 컬럼의 어휘와 게이트 — frontend/grid.md. <b>screen/admin/system/code/requirements.md 가 처음 요구했다.</b>
 *
 * <p>거기서는 헤더가 <i>되돌리기</i> 다. 행을 지우면 값이 사라지는 것이 아니라 `yml` 이
 * 드러나며, 라벨이 <i>삭제</i> 면 관리자는 값이 사라질 것을 기대하고 <b>목록에 같은 키가
 * 남아 있으면 버그로 읽는다</b> — 기대를 라벨이 만든다.
 */
export interface DeleteColumnSpec<T> {
  /** 헤더와 아이콘의 이름. 메시지 키이며 기본은 `GRID_COL_DELETE`. */
  header?: string;
  /**
   * 체크할 수 있는 행. 기본은 <b>전 행</b>이다.
   *
   * <p>지울 것이 없는 행을 체크하게 두면 저장이 <i>없는 것을 지운다</i> 로 나간다 —
   * screen/admin/system/code/requirements.md 의 <i>변경됨 인 행만</i> 이 그것이다.
   */
  enabled?: (row: GridRow<T>) => boolean;
  /**
   * <b>왜 못 지우는가</b> — {@link DeleteColumnSpec.enabled} 가 막은 행에 붙는 툴팁이다.
   * 완성된 문구이며 {@code null} 이면 붙지 않는다.
   *
   * <p>screen/admin/system/code/requirements.md 가 요구했다 — *"코드 4건이 있어 지울 수 없습니다"*. 회색 체크박스(여기서는
   * `—`)만 두면 <b>버그로 읽힌다</b>. 반대로 <b>사유가 없어도 되는 경우</b>가 있다:
   * 시스템 행은 자기 배지가 이미 말하고 있으므로 {@code null} 을 돌려준다.
   */
  reason?: (row: GridRow<T>) => string | null;
  /** 컬럼 폭. 라벨이 <i>삭제</i> 보다 길면 넓혀야 글자가 잘리지 않는다. */
  width?: number;
  /**
   * 취소선을 <b>이 컬럼에만</b> 건다. 없으면 frontend/grid.md 대로 <b>행 전체</b>다.
   *
   * <p>screen/admin/system/code/requirements.md 가 그 예외다 — 거기서는 행이 사라지지 않고 <b>오버라이드 한 줄</b>만
   * 지워진다. 전 셀에 취소선을 그으면 <i>"이 행이 통째로 없어진다"</i> 로 읽히고, 그
   * 기대는 저장 뒤에 <b>같은 키가 그대로 남아 있는 것</b>과 어긋난다.
   */
  strikeField?: string;
}

/** frontend/grid.md — <b>선언하지 않으면 버튼이 없다.</b> */
export interface AddRowSpec<T> {
  /** 누른 순간 판단하고, 안 되면 이유를 말한다. 비활성으로 막지 않는다. */
  canAdd?: (rows: T[]) => true | { ok: false; message: string };
  /** 빈 행으로 시작하지 않는다 — frontend/grid.md. */
  defaults?: (rows: T[]) => Partial<T>;
}

/**
 * 임의 컨트롤이 공유하는 것 — frontend/grid.md. <b>자리는 둘뿐이고 중간에 끼울 수 없다.</b>
 *
 * <p><b>`type` 은 셋뿐이다</b>(`button` · `select` · `toggle`). 그 이상이 필요하면 그것은
 * 그리드 헤더가 아니라 검색 폼의 일이다(UX-15). 지금 서 있는 것은 앞의 둘이며, `toggle` 은
 * **그것을 처음 요구하는 화면이 가져온다** — `api.actions[]` 가 그랬던 것과 같은 규칙이다.
 */
interface GridControlBase {
  key: string;
  slot: 'beforeRead' | 'beforeTools';
  /** 메시지 키. */
  label: string;
  /** 없으면 항상 보인다 — frontend/grid.md. */
  actionCd?: Action;
  menuCd?: string;
  disabled?: boolean;
  /**
   * <b>왜 눌리지 않는가</b> — 메시지 키. 툴팁으로 붙는다.
   *
   * <p>screen/admin/system/code/requirements.md 가 요구했다. 비활성 버튼은 <b>눌러도 아무 일이 없고</b> 스스로 이유를 말하지
   * 못한다 — frontend/grid.md 이 <i>행 추가</i>에서 *"비활성으로 막지 말고 눌렀을 때 이유를 말하라"*
   * 고 정한 것과 방향이 갈리는데, 여기는 <b>막는 것 자체가 신호</b>여야 하는 자리다:
   * 정의를 고치면 하단 재조회가 편집을 버리므로 <b>열고 나서 묻는 것보다 누를 수 없는 것이
   * 먼저 보이는 편</b>이 낫다. 그러면 남는 일은 <b>사유를 붙이는 것</b> 하나다.
   */
  hint?: string;
}

export interface GridButtonControl extends GridControlBase {
  type: 'button';
  /** <b>서버를 부르지 않는다</b> — 부르면 `api.actions` 다(frontend/grid.md). */
  onClick: () => void;
}

/**
 * 표시 범위 전환 — **screen/admin/system/user/requirements.md 의 유효 권한 탭이 첫 실물**이다(frontend/grid.md 가 그렇게 적었다).
 *
 * <p>`select` 인데 **조회조건이 아닌 이유**가 이 컨트롤의 존재 이유다. 여기서 고르는 것은
 * *거르기* 가 아니라 **무엇을 행으로 낼지**이며 — 부여를 세느냐 메뉴를 세느냐 — 값에 따라
 * 응답의 건수 단위가 바뀐다. 조회조건 칸으로 두면 검색 폼이 접혀 있을 때 **보이지 않는
 * 곳에서 그리드의 뜻이 바뀐다**(frontend/search-form.md).
 *
 * <p><b>스스로 재조회하지 않는다.</b> `onChange` 를 받은 화면이 상태를 바꾸고, 그 상태가
 * `searchParams` 로 흘러 들어간 뒤 화면이 `read()` 를 부른다 — 컨트롤이 컨트롤러를 직접
 * 부르면 `select` 하나가 **그리드의 미저장 편집을 말없이 버릴** 수 있게 된다(frontend/grid.md 의
 * 재조회 규약은 컨트롤러의 것이다).
 */
export interface GridSelectControl extends GridControlBase {
  type: 'select';
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  /** 없으면 라벨이 칸 왼쪽에 붙는다. 좁은 헤더에서 라벨을 지우고 싶을 때만 `false` 다. */
  showLabel?: boolean;
  width?: number;
}

/**
 * 켜고 끄는 것 하나 — playbook/INDEX.md 이 처음 요구했다(②의 <i>자동 새로고침</i>).
 *
 * <p><b>{@link GridSelectControl} 과 축이 같다</b>(무엇을 볼지를 정한다). 갈리는 것은
 * 값의 모양뿐이며, 후보가 <b>둘</b>인 것을 셀렉트로 그리면 <i>켜짐/꺼짐</i> 두 줄짜리
 * 드롭다운이 되어 <b>한 번 볼 것을 두 번 누르게</b> 된다.
 *
 * <p><b>스스로 재조회하지 않는다</b> — 셀렉트와 같은 규칙이고 같은 이유다(frontend/grid.md).
 * {@code onChange} 를 받은 화면이 상태를 바꾸고 그 상태가 {@code read()} 로 이어진다.
 * 컨트롤이 컨트롤러를 직접 부르면 <b>토글 하나가 미저장 편집을 말없이 버린다</b>.
 *
 * <p><b>{@code ynFlag} 셀의 스위치와 같은 모양인 것이 의도다</b>(frontend/grid.md) — 값이 둘인
 * 것은 화면 어디에 있든 스위치이며, 헤더에서만 다른 컨트롤을 쓰면 <i>이 스위치는 왜
 * 체크박스인가</i> 를 묻게 된다.
 */
export interface GridToggleControl extends GridControlBase {
  type: 'toggle';
  value: boolean;
  onChange: (value: boolean) => void;
  /** 없으면 라벨이 스위치 왼쪽에 붙는다 — 셀렉트와 같다. */
  showLabel?: boolean;
}

export type GridControl = GridButtonControl | GridSelectControl | GridToggleControl;

/**
 * 제목 뒤에 붙는 <b>부모 행의 이름</b> — frontend/grid.md · screen/admin/system/user/requirements.md.
 *
 * <p><b>디테일 그리드는 자기가 누구의 것인지 말하지 못한다.</b> 마스터에서 행을 고르면
 * 하단 탭이 그 사람의 것으로 갈리는데, 스크롤해서 마스터의 선택 행이 화면에서 벗어나거나
 * 조회조건을 펼치면 <b>지금 보고 있는 것이 누구의 것인지가 화면 어디에도 없다</b> —
 * 그 상태로 저장하면 <i>다른 사람의 역할을 고친 것</i>이 될 수 있다.
 *
 * <p><b>화면이 문자열을 만들지 않는다.</b> 화면마다 조립하면 구분자·순서·빈 값 처리가
 * 화면 수만큼 생기고, 마스터의 선택을 <b>디테일이 또 들고</b> 있어야 한다 — 그것은 이미
 * 컨트롤러가 갖고 있는 사실이다.
 */
export interface TitleSuffixSpec {
  /**
   * <b>부모 그리드의 컨트롤러</b>를 그대로 넘긴다 — 읽는 것은 {@code selected} 하나다.
   *
   * <p>행이 아니라 컨트롤러인 것이 요점이다. 선택은 사용자가 바꾸는 값이라 <b>흐르는
   * 값</b>이며, 행을 뽑아 넘기면 그 순간의 값이 굳는다.
   */
  parent: { selected: GridRow<unknown>[] };
  /**
   * 붙일 컬럼 — <b>선언 순서가 곧 표시 순서</b>이고 슬래시로 잇는다.
   *
   * <p>부모 행의 필드 이름이다. 값이 비었거나 없는 칸은 <b>조용히 빠진다</b> — 미저장
   * 신규 행처럼 아직 이름이 없는 경우가 있고, 그때 `/` 만 남으면 그것이 값처럼 보인다.
   */
  fields: string[];
}

/** 응답이 나르는 페이징 모드 — backend/api.md. 화면이 선언하지 않는다. */
export type PageMode = 'offset' | 'cursor' | 'all';

export interface PageResponse<T> {
  mode: PageMode;
  content: T[];
  size: number | null;
  hasNext: boolean;
  totalElements: number | null;
  totalPages: number | null;
  nextCursor: string | null;
  prevCursor: string | null;
}

export interface SortRequest {
  field: string;
  dir: 'asc' | 'desc';
}

export interface SearchCondition {
  field: string;
  op: string;
  value?: unknown;
}

export interface SearchRequestBody {
  conditions: SearchCondition[];
  sorts: SortRequest[];
  page: { page: number; size: number };
}
