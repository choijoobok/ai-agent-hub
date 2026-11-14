// @feat FEAT-042
import type { GridRow } from '@/shared/grid/rows';
export interface QuotaGrade {
  gradeId: string | null; clientKey?: string; gradeNm: string; rankNo: number; weight: string;
  description: string; referenceUsers: number | null; transferGradeId?: string;
}
export interface QuotaBasis {
  gradeSetId: string | null; startMonth: string; memo: string; revision: string | null; editable: boolean;
  regId?: string; regDtm?: string; modId?: string; modDtm?: string;
  detailHash?: string;
  grades?: GridRow<QuotaGrade>[];
}
export interface QuotaDetail { basis: QuotaBasis; grades: QuotaGrade[]; referenceState: 'READY' | 'NOT_INSTALLED' | 'UNAVAILABLE' }
