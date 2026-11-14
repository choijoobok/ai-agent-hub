// @feat FEAT-011
import type {GridRow} from '@/shared/grid/rows';
import type {ValueRow} from '../catalog/types';
export type RelationRow = ValueRow & {relationId?: string; clientKey?: string; ownerCatalogId?: string; revision?: string; pairs?: ValueRow[]};
export interface OwnerDetail {row: ValueRow & {id: string; revision: string}; values: {master: ValueRow; relations: RelationRow[]; columns: ValueRow[]}; dependencies: Record<string, unknown>}
export interface RelationDraft {ownerId: string; revision: string; relations: RelationRow[]; rows: GridRow<RelationRow>[]; selectedId: string}
export interface CatalogCandidate extends ValueRow {id: string; dataSourceId: string; revision: string; objectName: string}
