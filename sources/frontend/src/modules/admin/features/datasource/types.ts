// @feat FEAT-002
export interface Group { groupId: string; mappingActive: boolean; groupName?: string }
export interface Guide { guideId?: string; clientKey?: string; title: string; description: string }
export interface Values { dbmsVersion: string | null; sourceType: 'DB' | 'DW'; syncMethodNote: string | null; timeZoneId: string | null; description: string | null; dataBasis: string | null; applicableGroups: Group[]; queryGuides: Guide[] }
export interface SourceRow { id: string; dataSourceKey: string; alias: string; dbmsCode: string; dbmsVersion: string | null; sourceType: 'DB' | 'DW'; syncMethodNote: string | null; timeZoneId: string | null; description: string | null; dataBasis: string | null; managementVersion: string; configurationMismatch: boolean | null; reason: string; connectionStatus: string; parserSupport: string; comparisonSummary: string; appliedSettingsId: string; checkedAt: string | null; regId: string | null; regDtm: string; modId: string | null; modDtm: string; values?: Values; detailHash?: string }
export interface Projection { state: string; rows: Record<string, unknown>[] }
export interface Detail { row: SourceRow; values: Values; groups: {groupId: string; groupName: string}[]; guides: Guide[]; groupCandidates: {groupId: string; groupName: string}[]; catalogReferences: Projection }
export interface CleanupDraft { previewToken: string; query: {scope: 'DATASOURCE'; parentId: null; conditions: unknown[]}; confirmedTargets: {id: string; kind: string; parentId: null; expectedVersion: string}[] }
export interface Preview { previewToken: string; query: CleanupDraft['query']; targets: {target: CleanupDraft['confirmedTargets'][number]; name: string; reason: string | null}[]; executable: boolean }
