// @feat FEAT-507
/**
 * 사용 이력 API — screen/admin/system/log/access/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/admin/system/log/access` → `@/modules/system/features/accesslog` 를 따라와 여기를 읽는다.
 *
 * <p><b>둘뿐이고 둘 다 `get*` 이다.</b> 이력 행은 불변이라 저장도 삭제도 없다(screen/admin/system/log/access/requirements.md) —
 * `save*` 경로가 없으므로 시드 생성기가 `SAVE` 액션을 만들 수 없고, 만들 수 없으므로
 * **어느 역할에도 줄 수 없다**.
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀은 있지만(LOG-08) **프론트 생성**이다(playbook/excel-download.md 첫째).
 * 다운로드 이력은 `POST /api/system/common/excel/saveLog` 가 남기므로 여기 새로 만들 것이 없다.
 */
export const ACCESS_LOG_ENDPOINTS = {
  getList: '/system/service/accessLog/getList',
  /**
   * **전문 한 칸을 따로 가져온다** — 목록이 `nclob` 을 싣지 못하기 때문이다(screen/admin/system/log/access/requirements.md).
   *
   * <p>screen/admin/system/user/requirements.md 가 편집 폼을 없애며 `getDetail` 을 없앤 것과 정반대다. 여기서는
   * **컬럼 하나가 목록에 들어올 수 없어서** 돌아왔다.
   */
  getDetail: '/system/service/accessLog/getDetail',
} as const;
