// @feat FEAT-509
/**
 * 요청함 처리 API — screen/admin/system/noti-request/requirements.md.
 *
 * <p>공지와 폴더가 갈린 이유는 `modules/system/features/notice/api/endpoints.ts` 에 적었다 —
 * <b>폴더가 인가 경계</b>다(seed.md).
 *
 * <p><b>`updateHandle` 이 `SAVE` 다</b>(접두가 정한다 — backend/api.md). screen/admin/system/user/requirements.md 의 기준
 * (*"우리 테이블의 컬럼을 쓰는가"*)으로 봐도 그렇다: 처리는 `SYS_NOTI_REQUEST` 의 컬럼
 * 넷을 쓴다. `BATCH_HISTORY` 의 `cancel` 이 `EXECUTE` 였던 것과 <b>갈리는 자리</b>이며
 * (screen/admin/system/batch/schedule/requirements.md), 그쪽은 요청만 남기고 종결은 배치가 했지만 여기는 <b>이 호출이 끝나면
 * 상태가 끝난 상태</b>다.
 *
 * <p><b>처리와 반려가 한 경로다</b>(screen/admin/system/noti-request/requirements.md) — 쓰는 컬럼이 같고 트랜잭션이 같다.
 * 둘로 가르면 알림 INSERT 코드가 두 벌이 되고 한쪽만 고치는 날이 온다.
 *
 * <p><b>사용자 측이 여기 없다.</b> 자기 요청의 상태 확인은 알림 모달의 *내 요청* 탭이며
 * `/api/common/**` 이다(sys/menu.md).
 */
export const NOTI_REQUEST_ENDPOINTS = {
  getList: '/system/service/notiRequest/getList',
  /** 처리 · 반려 — `reqStatCd` 가 가른다(screen/admin/system/noti-request/requirements.md). */
  updateHandle: '/system/service/notiRequest/updateHandle',
} as const;
