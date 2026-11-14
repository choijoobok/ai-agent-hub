// @feat FEAT-010
import { Alert } from 'antd';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import type { ContentDisplay } from '../types';
/** 서버의 동일 정책 결과만 표시한다. 원문을 iframe에 넣지 않는다. */
export function ContentPreview({ display }: { display: ContentDisplay }) {
  if (display.policyVersion !== 'chat-content/1') return <Alert type="error" title={label(MSG.CHAT_HELP_FAILED)} />;
  const document = `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; connect-src 'none'; base-uri 'none'; form-action 'none'"><style>body{font:16px/1.6 sans-serif;overflow-wrap:anywhere;padding:12px}pre{white-space:pre-wrap}table{border-collapse:collapse}td,th{border:1px solid #888;padding:6px}blockquote{border-left:3px solid #888;padding-left:12px}</style></head><body>${display.html}</body></html>`;
  return <div className="chat-help-preview">
    {display.removed && <Alert type="warning" showIcon title={label(MSG.CHAT_HELP_REMOVED)} />}
    {!display.hasContent && <Alert type="warning" showIcon title={label(MSG.CHAT_HELP_EMPTY)} />}
    <iframe title={label(MSG.CHAT_HELP_PREVIEW)} sandbox="allow-popups allow-popups-to-escape-sandbox" referrerPolicy="no-referrer" srcDoc={document} />
    <small>{label(MSG.CHAT_HELP_POLICY)}: {display.policyVersion}</small>
  </div>;
}
