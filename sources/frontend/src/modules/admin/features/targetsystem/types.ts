// @feat FEAT-001
import type { SearchCondition } from '@/shared/grid/types';
export interface Audit { regId: string | null; regDtm: string; modId: string | null; modDtm: string }
export interface ConnectionRow extends Audit {
  systemId: string; connectionId: string; connectionName: string; description: string | null;
  metadataOverrides: { nameOverride: string | null; descriptionOverride: string | null };
  yamlName: string | null; yamlDescription: string | null;
  configurationMismatch: boolean | null; mismatchReason: string; appliedSettingsId: string;
  currentDisplay: { baseUrl: string | null; authenticationType: string | null; status: string; reason: string | null };
  testConfigured: boolean | null; testAllowed: boolean; disabledReason: string | null;
}
export interface SystemRow extends Audit {
  id: string; version: string; systemKey: string; systemName: string; description: string | null;
  systemNameOverride: string | null; descriptionOverride: string | null;
  yamlSystemName: string | null; yamlDescription: string | null;
  configurationMismatch: boolean | null; systemDefinitionStatus: string; appliedSettingsId: string;
  connections: ConnectionRow[]; detailHash?: string;
}
export interface ApiRow { id: string; name: string; apiKind: string; connectionId: string; useYn: string }
export interface References { state: 'READY' | 'NOT_CONNECTED' | 'UNAVAILABLE'; rows: ApiRow[] }
export interface CleanupQuery { scope: 'SYSTEM' | 'CONNECTION'; parentId: string | null; conditions: SearchCondition[] }
export interface Confirmation { id: string; kind: string; parentId: string | null; expectedVersion: string }
export interface CleanupPreview {
  previewToken: string; query: CleanupQuery; appliedSettingsId: string; expiresAt: string; executable: boolean;
  targets: { target: Confirmation; name: string; reason: string | null }[];
}
export interface CleanupDraft { previewToken: string; query: CleanupQuery; confirmedTargets: Confirmation[] }
