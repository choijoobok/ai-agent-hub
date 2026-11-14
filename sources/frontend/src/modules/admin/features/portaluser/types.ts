// @feat FEAT-005
import type { GridRow } from '@/shared/grid/rows';
export interface PortalUserRow {
  portalUserId: string | null; loginId: string; userNm: string; organizationNm: string;
  authModeCd: string; sourceCd: string; useStartDt: string; useEndDt: string | null;
  lockedYn?: string | null; mustChange?: boolean | null; passwordChangedDtm?: string | null;
  activeYn: string; revision: string | null; regId?: string; regDtm?: string; modId?: string; modDtm?: string;
  externalSource?: string; externalSubject?: string;
}
export type PortalScope = 'memberships' | 'grades' | 'extraTokens' | 'identitySystem' | 'identityDataSource';
export type PortalDetailRow = Record<string, unknown>;
export interface PortalDetail {
  user: PortalUserRow; scope: string; rows: PortalDetailRow[]; choices: PortalDetailRow[]; state: string; today: string;
  allocation: { state: string; tokenLimit: string | null; usedTokens: string | null; remainingTokens: string | null } | null;
  gradeChoices: { gradeId: string; gradeNm: string }[]; credential: Record<string, unknown>;
}
export interface PortalScopeDraft { portalUserId: string; revision: string; scope: PortalScope; rows: GridRow<PortalDetailRow>[]; deactivateUser: boolean }
export interface PortalSaved { targetId: string; revision: string; outcome: string }
