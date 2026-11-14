import { post } from '@/shared/api';
import { CODE_COMMON_ENDPOINTS } from '@/shared/code/api/endpoints';

/**
 * 그룹별 코드값 — sys/code.md.
 *
 * <p><b>이름이 없다.</b> 서버가 주는 것은 코드값뿐이며 화면에 보일 글자는 메시지 번들과
 * 메모리에서 조인한다(`codeLabel`). 그래서 언어를 늘려도 이 응답은 그대로다.
 */
export interface CodeListResponse {
  /** 이 데이터를 뜬 시점의 `SYS_CACHE_VERSION('code')` — 캐시 항목이 자기 버전을 안다. */
  version: number;
  /** `GRP_CD → DTL_CD 목록`. <b>요청한 그룹은 없는 것도 키로 들어 있다</b>. */
  groups: Record<string, string[]>;
}

export function fetchCodes(grpCds: string[]): Promise<CodeListResponse> {
  return post<CodeListResponse>(CODE_COMMON_ENDPOINTS.getList, { grpCds });
}

export interface CodeRefRequest { grpCd: string; refCd: string; refVal: string }
export interface CodeRefListResponse { version: number; codes: string[] }
export function fetchCodesByRef(request: CodeRefRequest): Promise<CodeRefListResponse> {
  return post<CodeRefListResponse>(CODE_COMMON_ENDPOINTS.getListByRef, request);
}
