// @feat FEAT-010
export type HelpCode = 'USAGE_HELP' | 'CHAT_START' | 'INPUT_NOTICE';
export type HelpFormat = 'MARKDOWN' | 'HTML' | 'TEXT';
export interface ChatHelp { helpCd: HelpCode; name: string; position: string; timing: string; formatCd: HelpFormat; body: string; revision: string; state: 'UNSET' | 'READY'; acceptedDtm: string; changeReason: string; modId: string | null; modDtm: string }
export interface ContentDisplay { policyVersion: string; html: string; removed: boolean; hasContent: boolean }
