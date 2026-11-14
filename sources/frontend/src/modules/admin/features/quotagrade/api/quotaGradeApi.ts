// @feat FEAT-042
import { post, ManagedCommands } from '@/shared/api';
import { MSG } from '@/shared/i18n/messageKeys';
import { QUOTA_GRADE_ENDPOINTS } from './endpoints';
import type { QuotaBasis, QuotaDetail } from '../types';
export function fetchQuotaBasis(targetId: string): Promise<QuotaDetail> { return post(QUOTA_GRADE_ENDPOINTS.getOne, { targetId }); }
export function copyPreviousBasis(beforeMonth: string): Promise<QuotaDetail | null> { return post(QUOTA_GRADE_ENDPOINTS.getOne, { beforeMonth }); }
export class QuotaGradeCommands extends ManagedCommands<QuotaBasis> {
  constructor(key: string, storage: Storage, send: typeof post = post) {
    super(key, storage, { ...QUOTA_GRADE_ENDPOINTS, feature: 'FEAT-042', field: 'startMonth',
      changedMessage: MSG.GROUP_PENDING_CHANGED, pendingMessage: MSG.GROUP_PENDING,
      encode: (row, reason) => ({ targetId: row.data.gradeSetId, expectedRevision: row.data.revision, scope: 'definition', reason,
        values: row.state === 'D' ? {} : {
          startMonth: row.data.startMonth, memo: row.data.memo,
          grades: (row.data.grades ?? []).filter((grade) => grade.state !== 'D').map(({ data }) => ({
            gradeId: data.gradeId, clientKey: data.clientKey, gradeNm: data.gradeNm, rankNo: Number(data.rankNo), weight: String(data.weight), description: data.description,
          })),
          transitionMappings: (row.data.grades ?? []).filter((grade) => grade.state === 'D' && grade.data.transferGradeId).map(({ data }) => data.transferGradeId!.startsWith('new:')
            ? { fromGradeId: data.gradeId, toClientKey: data.transferGradeId!.slice(4) } : { fromGradeId: data.gradeId, toGradeId: data.transferGradeId }),
        },
      }),
    }, send);
  }
}
