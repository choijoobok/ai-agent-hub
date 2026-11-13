// @feat FEAT-006
export interface UserGroupRow {
  groupId: string | null;
  groupNm: string;
  description: string;
  revision: string | null;
  useState: 'USED' | 'UNUSED' | 'UNKNOWN';
  regId?: string;
  regDtm?: string;
  modId?: string;
  modDtm?: string;
}
export type GroupScope = 'membership' | 'knowledge' | 'catalog' | 'api' | 'tool';
export type ConnectionState = 'READY' | 'NOT_CONNECTED' | 'UNAVAILABLE' | 'FORBIDDEN';
export interface GroupProjection { state: ConnectionState; rows: Record<string, unknown>[] }
