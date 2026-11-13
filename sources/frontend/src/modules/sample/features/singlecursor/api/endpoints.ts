// @feat FEAT-503
/**
 * 샘플 단일 그리드·커서 API — playbook/INDEX.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). **이 선언이 `SYS_MENU_API` 시드의 입력**이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/sample/single-cursor` → `@/modules/sample/features/singlecursor` 를 따라와
 * 여기를 읽는다.
 *
 * <p>**줄이 하나뿐이다.** 이 화면의 액션은 `R` 과 `E` 이고 저장이 없으며, 엑셀 둘은
 * 프론트가 만든다(화면이 그리는 표가 곧 내려받을 표다) — `EXPORT` 권한은 메뉴가
 * 선언한다(sys/menu.md).
 *
 * <p>**①의 `getOrderOffsetList` 와 같은 테이블을 읽는다.** 테이블을 하나 더 만들지 않은
 * 것이 playbook/INDEX.md 의 결정이며, 그렇게 하면 **모드가 데이터의 성질처럼** 읽힌다 — 모드는
 * 엔드포인트가 정한다(backend/api.md).
 */
export const SAMPLE_CURSOR_ENDPOINTS = {
  getOrderCursorList: '/sample/service/getOrderCursorList',
} as const;
