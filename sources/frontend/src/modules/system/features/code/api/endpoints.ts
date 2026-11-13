// @feat FEAT-504
/**
 * 공통 코드 관리 API — screen/admin/system/code/requirements.md.
 *
 * <p>호출 URL 은 이 파일에만 둔다(frontend/packages.md). <b>이 선언이 `SYS_MENU_API` 시드의 입력</b>이므로
 * (backend/api.md) *선언 = 사용* 이 구조적으로 보장돼야 한다 — `generateApiSeed` 가
 * `screenRegistry` 의 `/code` → `@/modules/system/features/code` 를 따라와 여기를 읽는다.
 *
 * <p>선언하지 않고 호출하면 **403** 이다(fail-closed 가 잡는다). 반대로 선언해 놓고 쓰지
 * 않으면 **접근 범위가 조용히 넓어진다**.
 *
 * <p><b>여섯인 것이 이 화면의 모양이다</b> — 그리드가 셋이고 각각 조회와 저장을 갖는다.
 * screen/admin/system/code/requirements.md 의 메시지 화면이 하단을 마스터의 `list` 컬럼으로 접어 둘로 끝낸 것과 갈리는
 * 자리이며, 여기 하단은 **자기 테이블에 붙어 있다**(FK 도 걸린다).
 *
 * <p><b>`exportExcel` 이 없다.</b> 엑셀은 있지만(MASTER-07) 그것은 **프론트 생성**이다
 * (frontend/grid.md · playbook/excel-download.md 첫째) — 서버가 낼 것이 없으므로 경로도 없다. screen/admin/system/code/requirements.md 의 메시지
 * 다운로드가 서버 POI 인 것과 갈린다.
 */
export const CODE_ENDPOINTS = {
  getGroupList: '/system/service/code/getGroupList',
  saveGroup: '/system/service/code/saveGroup',
  getList: '/system/service/code/getList',
  save: '/system/service/code/save',
  /**
   * **두 자리에서 부른다**(screen/admin/system/code/requirements.md) — 정의 모달의 조회와, 하단 조회에 **함께** 나가는 쪽.
   * 같은 응답을 쓰므로 정의가 바뀌면 컬럼 집합과 모달 목록이 같은 순간에 같아진다.
   */
  getRefDefList: '/system/service/code/getRefDefList',
  saveRefDef: '/system/service/code/saveRefDef',
} as const;
