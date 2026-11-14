// @feat FEAT-005
import { MSG } from '@/shared/i18n/messageKeys';
import type { PortalScope } from '../types';
export const PORTAL_TABS: Record<PortalScope, string> = { memberships: MSG.GROUP_TITLE, grades: MSG.QUOTA_GRADE_DETAIL, extraTokens: MSG.PORTAL_EXTRA, identityDataSource: MSG.PORTAL_DS_IDENTITY, identitySystem: MSG.PORTAL_SYSTEM_IDENTITY };
