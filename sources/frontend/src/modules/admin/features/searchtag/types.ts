// @feat FEAT-024
import type { GridRow } from '@/shared/grid/rows';
export interface TagRow { tagId: string | null; searchText: string; useYn: 'Y' | 'N'; revision: string | null; detailHash?: string; synonyms?: GridRow<Synonym>[]; regId?: string; regDtm?: string; modId?: string; modDtm?: string }
export interface Synonym { synonymId: string | null; clientKey?: string; searchText: string; description: string | null }
export interface Knowledge { knowledgeId: string; knowledgeNm: string; revision: string; activeYn: string | null }
export interface TagDetail { tag: TagRow; synonyms: Synonym[] }
export interface TagReferences { revision: string; state: 'READY' | 'NOT_CONNECTED' | 'UNAVAILABLE' | 'FORBIDDEN'; reference: 'PRESENT' | 'ABSENT' | 'UNKNOWN'; rows: Knowledge[] }
