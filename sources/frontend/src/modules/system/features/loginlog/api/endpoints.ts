// @feat FEAT-507
/**
 * 로그인 이력 API — screen/admin/system/log/login/requirements.md.
 *
 * <p><b>하나뿐이다.</b> 이력 행은 불변이라 `save*` 가 없고(screen/admin/system/log/login/requirements.md), `getDetail` 마저
 * 없는 것은 이 테이블에 `nclob` 컬럼이 하나도 없어 <b>목록이 곧 전부</b>이기 때문이다 —
 * 사용 이력에서 전문 하나 때문에 두 번째 호출이 생겼던 것과 정반대다(screen/admin/system/log/login/requirements.md).
 *
 * <p><b>폴더가 화면마다 하나다.</b> 사용 이력과 한 폴더에 있었는데 그러면
 * `generateApiSeed` 가 <b>둘의 API 를 서로에게 준다</b> — 사슬의 가운데 마디가
 * `MENU_URL → @/modules/system/features/{도메인}` 이고 도메인마다 `api/endpoints.ts` 가 하나이기 때문이다
 * (seed.md). 실제로 그렇게 만들어졌었다(2026-08-30): `LOGIN_LOG` 에 `accessLog/getList` 가,
 * `ACCESS_LOG` 에 `loginLog/getList` 가 붙었다. <b>화면에 없는 권한이 시드에서 생기는</b>
 * 것이라 인가의 문제이며, screen/admin/system/log/login/requirements.md 이 문서 하나에 화면 셋을 둔 것과는 층이 다르다.
 *
 * <p><b>`exportExcel` 이 없는 것도 같다.</b> 엑셀은 있지만(LOG-08) 프론트 생성이며
 * (playbook/excel-download.md 첫째) 다운로드 이력은 `POST /api/system/common/excel/saveLog` 가 남긴다.
 */
export const LOGIN_LOG_ENDPOINTS = {
  getList: '/system/service/loginLog/getList',
} as const;
