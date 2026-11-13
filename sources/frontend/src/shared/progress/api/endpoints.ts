/**
 * 진행률 폴링 — <b>공통 API</b>다(backend/security.md · backend/crosscutting.md).
 *
 * <p>메뉴에 매이지 않는다 — 오래 걸리는 작업을 가진 화면이 늘 때마다 엔드포인트가 늘면
 * 인가 시드가 그것을 전부 나열하게 된다.
 *
 * <p><b>`/api` 로 시작하지 않는다</b>(2026-09-02 에 고쳤다). 클라이언트의 `baseURL` 이
 * 이미 `/api` 라 여기에 또 적으면 `/api/api/...` 로 나가고, 그 경로는 어떤 컨트롤러에도
 * 닿지 않아 **403** 이 온다 — 그러면 화면은 *진행률을 확인할 수 없습니다* 를 보인다.
 * 그 문구는 서버가 내려갔을 때를 위한 것이므로 **원인과 정반대의 사실**을 말한 셈이고,
 * 작업 자체는 멀쩡히 끝나므로 아무도 그것을 고장으로 읽지 않는다.
 */
export const PROGRESS_ENDPOINTS = {
  getOne: (jobId: string) => `/system/common/progress/getProgress/${encodeURIComponent(jobId)}`,
} as const;
