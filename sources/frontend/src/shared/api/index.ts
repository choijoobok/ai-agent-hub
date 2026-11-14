/**
 * HTTP 층의 <b>유일한 입구</b>다 — frontend/packages.md.
 *
 * <p><b>바깥은 이 파일만 부른다.</b> `shared/api/*` 를 직접 가리키면 같은 헬퍼가 두 경로로
 * 들어오고(`@/shared/api` 와 `@/shared/api/request`), 그러면 *어디서 가져오는가* 가 파일마다
 * 달라져 새 화면이 무엇을 따라야 할지 알 수 없다. 규칙은 ESLint 가 지킨다
 * (`no-restricted-imports` — `@/shared/api/*`).
 *
 * <p><b>안쪽은 서로를 직접 부른다.</b> `request.ts` 가 이 배럴을 부르면 순환이 된다 —
 * 그래서 금지의 대상은 `src/shared/api/**` 밖이다.
 */
export { apiClient, setActiveMenuCode } from '@/shared/api/client';
export { postDownload } from '@/shared/api/download';
export type { DownloadedFile } from '@/shared/api/download';
export { newRequestId } from '@/shared/api/requestId';
export { get, post, request } from '@/shared/api/request';
export { emitResponseSignal, onResponseSignal } from '@/shared/api/responseSignals';
export type { ResponseSignal } from '@/shared/api/responseSignals';
export { ApiError, TRANSPORT_ERROR_CODE } from '@/shared/api/types';
export type { ApiEnvelope, FieldError, RowError, RowReport } from '@/shared/api/types';
export { ManagedCommands } from '@/shared/api/ManagedCommands';
export type { ManagedCommand } from '@/shared/api/ManagedCommands';
