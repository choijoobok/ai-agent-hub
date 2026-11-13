import type { Action } from '@/shared/authz/permission';

/**
 * ②가 부를 수 있는 <b>세 번째 갈래</b> — playbook/excel-download.md.
 *
 * <p>ADR 은 갈래를 셋으로 갈랐고(현재 페이지 · 전체 · <b>임의 API</b>) *"사용자에게는 셋이
 * 같은 버튼 묶음으로 보일 수 있다 — 임의 API 경로도 현재 페이지·전체 라는 이름을 그대로
 * 쓸 수 있다"* 고 적었다. <b>구현이 그 문장을 따라가지 못하고 있었다</b>: 두 항목이
 * 프론트 생성에 박혀 있어, 화면 구조와 엑셀 구조가 다른 화면은 ②를 쓸 수 없었다.
 *
 * <h2>왜 화면 고유 버튼(①)으로 미루면 안 되는가</h2>
 *
 * <p>frontend/grid.md 는 세 번째를 <i>"①의 화면 고유 버튼으로 둔다"</i> 로 넘겼다. 그러면 상단-하단
 * 그리드 화면에서 사용자는 <b>같은 뜻의 버튼을 두 자리에서</b> 만난다 — ②의 아이콘과 ①의
 * <i>엑셀 다운로드</i>. 어느 쪽이 무엇을 주는지는 눌러 봐야 알고, 그 차이는 <b>파일을
 * 열고 나서</b> 드러난다.
 *
 * <h2>판을 바꿔도 이 선언은 그대로다</h2>
 *
 * <p>frontend/grid.md 의 요구다. 여기 있는 것은 <b>URL 과 메시지 키</b>뿐이라 AG Grid 의 판본과
 * 아무 접점이 없고, Enterprise 로 갈아타도 화면이 적은 것은 한 글자도 바뀌지 않는다.
 * Enterprise 의 Excel Export 를 쓰지 않는 이유도 playbook/excel-download.md 가 이미 적었다 — <b>그것은
 * 그리드가 가진 것만 내보내므로</b> 이 갈래를 대신하지 못한다.
 */
export interface ExcelServer {
  /** {@code /service/...} — {@code endpoints.ts} 가 이름을 갖는다(backend/api.md). */
  url: string;
  /**
   * 그 요청에 실을 것 — <b>화면이 만든다</b>.
   *
   * <p>컨트롤러의 조회조건을 자동으로 싣지 않는다. 이 갈래의 정의가 <i>그리드와 다른
   * 조회</i> 이므로(playbook/excel-download.md) 무엇이 조건인지는 <b>그 API 만 안다</b> — 짐작해서 실으면
   * 서버가 모르는 필드가 가고, 그 요청은 <b>조용히 조건 없이</b> 처리된다.
   */
  body?: () => unknown;
}

/**
 * ②의 드롭다운에 <b>더 붙는</b> 항목 — 화면 고유의 엑셀이다.
 *
 * <p><b>서버 생성만 받는다.</b> 프론트에서 만들 수 있는 것은 이미 두 기본 항목이 덮으며,
 * 여기에 프론트 갈래를 열면 <i>고른 행만</i> 같은 항목이 생기는데 그것은 이력의
 * {@code scope} 가 표현하지 못한다(playbook/excel-download.md — {@code PAGE}·{@code ALL} 둘이다). 서버 생성은
 * <b>요청이 도착하는 것 자체가 기록</b>이라 그 문제가 없다.
 */
export interface ExcelExtraItem {
  key: string;
  /** 메시지 키 — 드롭다운에 그대로 나온다. */
  label: string;
  server: ExcelServer;
  /**
   * 없으면 {@code EXPORT} — ②를 여는 권한과 같다.
   *
   * <p>따로 받는 이유는 <b>가공이 곧 실행인 항목</b>이 있기 때문이다(집계를 돌려 내리는
   * 등). 그때 권한은 {@code EXECUTE} 이며, {@code EXPORT} 로 묶으면 <i>내려받기만
   * 되는 사람</i>이 그 계산을 돌린다.
   */
  actionCd?: Action;
}

/**
 * ②가 무엇을 주는가 — 선언하지 않으면 <b>지금까지와 같다</b>(프론트 생성 둘).
 *
 * <p>{@code page}·{@code all} 을 주면 <b>이름은 그대로 두고 경로만</b> 서버로 돌린다.
 * 사용자에게 보이는 것은 여전히 <i>현재 페이지</i>·<i>전체</i> 이며, 어느 경로인지는
 * <b>알 필요가 없다</b>(playbook/excel-download.md).
 */
export interface ExcelSpec {
  /** @feat FEAT-006 — 현재 조회 페이지로 내보내기를 제한한다. */
  pageOnly?: boolean;
  /**
   * <i>현재 페이지</i> 를 서버가 만든다.
   *
   * <p><b>이쪽을 돌리는 일은 드물다.</b> 현재 페이지는 이미 브라우저에 있어 왕복이 0
   * 이기 때문이다(playbook/excel-download.md). 그럼에도 자리를 두는 것은 <b>화면의 표와 파일의 표가 다른</b>
   * 화면 때문이다 — 상단-하단을 조인해 한 장으로 내리는 화면에서는 <i>현재 페이지</i> 도
   * 서버가 조인해야 나온다.
   */
  page?: ExcelServer;
  /** <i>전체</i> 를 서버가 만든다 — 상한(`ui.excel.max-rows`)의 판정도 그쪽으로 넘어간다. */
  all?: ExcelServer;
  /**
   * 항목을 더 붙인다.
   *
   * <p>구분선 아래에 선언한 순서로 선다 — 기본 둘과 <b>섞이지 않는다</b>. 섞이면
   * 사용자가 <i>어느 것이 이 화면만의 것인가</i> 를 눌러 봐야 안다.
   */
  extra?: ExcelExtraItem[];
}
