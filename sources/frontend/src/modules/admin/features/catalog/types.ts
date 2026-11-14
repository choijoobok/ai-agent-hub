// @feat FEAT-003
import type {GridRow} from '@/shared/grid/rows';
export type Scope = 'MASTER' | 'COLUMNS' | 'ACCESS_GROUPS' | 'COLUMN_GROUP_EXCEPTIONS' | 'INDEXES';
export type ValueRow = Record<string, unknown>;
export type Workspace = {master: ValueRow[]; columns: ValueRow[]; groups: ValueRow[]; exceptions: ValueRow[]; indexes: ValueRow[]};
export interface CatalogRow extends ValueRow {id: string; catalogId: string; dataSourceId: string; revision: string; objectName: string; businessName: string | null}
export interface CatalogDetail {row: CatalogRow; values: {master: ValueRow; columns: ValueRow[]; groups: ValueRow[]; exceptions: ValueRow[]; indexes: ValueRow[]}; dependencies: {groupCandidates?: {groupId: string; groupName: string}[]; [key: string]: unknown}}
export interface CatalogOptions {dataSources: {id: string; alias: string; dbmsCode: string; dbmsVersion?: string | null}[]; timezones: string[]; maskingTypes: {value: string; text: string}[]; maskingState: string}
export interface CommandDraft {id: string | null; revision: string | null; scope: Scope; values: Record<string, unknown>; workspace: Workspace; snapshots?: Record<keyof Workspace, GridRow<ValueRow>[]>}
export interface CommandResult {targetId: string; revision: string; historyNo: string | null; outcome: string}
export interface Projection {state: string; rows: ValueRow[]}
