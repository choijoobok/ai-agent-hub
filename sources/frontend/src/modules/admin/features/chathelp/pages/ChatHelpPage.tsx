// @feat FEAT-010
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Input, Modal, Select, Space, Spin, Typography } from 'antd';
import { FillColumn } from '@/shared/components/FillColumn';
import { useAuthStore } from '@/shared/stores/authStore';
import { hasPermission } from '@/shared/authz/permission';
import { useTabIdentity } from '@/layouts/ShellLayout/tabContext';
import { useTabDirty } from '@/shared/grid/useTabDirty';
import { useConfirmDiscard } from '@/shared/grid/useConfirmDiscard';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { ChatHelpCommands, fetchChatHelp, fetchChatHelpList, previewChatHelp } from '../api/chatHelpApi';
import type { ChatHelp, ContentDisplay, HelpCode, HelpFormat } from '../types';
import { ContentPreview } from './ContentPreview';
import './chatHelp.css';
interface Draft { current: ChatHelp; original: ChatHelp }
interface Dialog { kind: 'save' | 'edit' | 'preview'; target: ChatHelp; buffer: string }
const changed = (draft: Draft) => draft.current.formatCd !== draft.original.formatCd || draft.current.body !== draft.original.body;
export function ChatHelpPage() {
  const { message } = App.useApp();
  const actor = useAuthStore((s) => s.user?.userId ?? '');
  const permissions = useAuthStore((s) => s.permissions);
  const canRead = hasPermission(permissions, 'HUB_CHAT_HELP', 'READ');
  const canSave = canRead && hasPermission(permissions, 'HUB_CHAT_HELP', 'SAVE');
  const identity = useTabIdentity();
  const commands = useMemo(() => new ChatHelpCommands(`chat-help/${actor}/${identity?.tabKey ?? 'standalone'}`, sessionStorage), [actor, identity?.tabKey]);
  const [drafts, setDrafts] = useState<Partial<Record<HelpCode, Draft>>>({});
  const [selected, setSelected] = useState<HelpCode>('USAGE_HELP');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [dialog, setDialog] = useState<Dialog | null>(null); const [reason, setReason] = useState('');
  const [display, setDisplay] = useState<ContentDisplay | null>(null);
  const serial = useRef(0); const draft = drafts[selected];
  const pending = commands.hasPending();
  const dirty = Object.values(drafts).some(changed) || pending;
  useTabDirty(dirty);
  const confirmDiscard = useConfirmDiscard();
  const failed = (value: unknown) => setError(value instanceof Error ? value.message : label(MSG.CHAT_HELP_FAILED));
  useEffect(() => {
    const ticket = ++serial.current; let active = true;
    if (!canRead) return;
    void fetchChatHelpList().then((rows) => {
      if (!active || ticket !== serial.current) return;
      const next: Partial<Record<HelpCode, Draft>> = {};
      for (const row of rows) next[row.helpCd] = { current: row, original: row };
      for (const row of commands.restoredRows()) if (next[row.data.helpCd]) next[row.data.helpCd] = { current: row.data, original: next[row.data.helpCd]!.original };
      setDrafts(next);
    }).catch((e: unknown) => { if (active && ticket === serial.current) failed(e); });
    return () => { active = false; };
  }, [canRead, commands]);
  const refresh = async () => {
    if (!canRead || busy || pending || (draft && changed(draft) && !(await confirmDiscard()))) return;
    const code = selected; const ticket = ++serial.current; setBusy(true); setError('');
    try { const row = await fetchChatHelp(code); if (ticket === serial.current) setDrafts((old) => ({ ...old, [code]: { current: row, original: row } })); }
    catch (e) { failed(e); } finally { setBusy(false); }
  };
  const edit = (patch: Partial<ChatHelp>) => {
    if (!canSave || busy || pending || !draft) return;
    setDrafts((old) => ({ ...old, [selected]: { ...draft, current: { ...draft.current, ...patch } } }));
  };
  const close = () => { if (!busy) { serial.current++; setDialog(null); setDisplay(null); setReason(''); } };
  const open = async (kind: Dialog['kind']) => {
    if (!draft || busy || (kind !== 'preview' && !canSave)) return;
    const target = structuredClone(draft.current); setDialog({ kind, target, buffer: target.body }); setReason(''); setError('');
    if (kind === 'preview') {
      const ticket = ++serial.current; setDisplay(null); setBusy(true);
      try { const result = await previewChatHelp(target); if (ticket === serial.current) setDisplay(result); }
      catch (e) { failed(e); } finally { setBusy(false); }
    }
  };
  const save = async () => {
    const target = dialog?.target ?? commands.restoredRows()[0]?.data;
    if (!target || !canSave || busy || (!pending && !reason.trim())) return;
    setBusy(true); setError('');
    try {
      const report = await commands.save([{ id: target.helpCd, state: 'U', data: target, original: drafts[target.helpCd]?.original ?? target }], reason);
      if (report.failed) { setError(report.rows[0]?.fields?.[0]?.message ?? label(MSG.CHAT_HELP_PENDING)); return; }
      const row = await fetchChatHelp(target.helpCd);
      setDrafts((old) => ({ ...old, [target.helpCd]: { current: row, original: row } }));
      setDialog(null); setReason(''); void message.success(label(MSG.CHAT_HELP_SAVED));
    } catch (e) { failed(e); } finally { setBusy(false); }
  };
  if (!canRead) return <Alert type="warning" title={label(MSG.CHAT_HELP_DENIED)} />;
  return <FillColumn><div className="chat-help-page">
    {error && <Alert type="error" title={error} showIcon />}
    {pending && <Alert type="warning" title={label(MSG.CHAT_HELP_PENDING)} action={<Button disabled={!canSave || busy} onClick={() => void save()}>{label(MSG.CHAT_HELP_RECOVER)}</Button>} />}
    {!canSave && <Alert type="info" title={label(MSG.CHAT_HELP_READONLY)} />}
    <Space wrap align="end">
      <label>{label(MSG.CHAT_HELP_KIND)}<Select aria-label={label(MSG.CHAT_HELP_KIND)} value={selected} disabled={busy || !!dialog} onChange={setSelected}
        options={Object.values(drafts).map((d) => ({ value: d.current.helpCd, label: `${d.current.helpCd} · ${d.current.name}${changed(d) ? ' *' : ''}` }))} style={{ minWidth: 340 }} /></label>
      <label>{label(MSG.CHAT_HELP_FORMAT)}<Select aria-label={label(MSG.CHAT_HELP_FORMAT)} value={draft?.current.formatCd ?? 'MARKDOWN'} disabled={!canSave || busy || pending || !!dialog || !draft}
        options={['MARKDOWN', 'HTML', 'TEXT'].map((value) => ({ value, label: value }))} onChange={(formatCd: HelpFormat) => edit({ formatCd })} style={{ minWidth: 150 }} /></label>
      <Button onClick={() => void refresh()} disabled={busy || pending || !!dialog}>{label(MSG.CHAT_HELP_READ)}</Button>
      <Button type="primary" disabled={!canSave || !draft || (!changed(draft) && !pending) || busy || pending || !!dialog} onClick={() => void open('save')}>{label(MSG.GRID_SAVE)}</Button>
      <Button disabled={!canSave || !draft || busy || pending || !!dialog} onClick={() => void open('edit')}>{label(MSG.CHAT_HELP_ENLARGE)}</Button>
      <Button disabled={!draft || busy || !!dialog} onClick={() => void open('preview')}>{label(MSG.CHAT_HELP_PREVIEW)}</Button>
    </Space>
    {draft ? <>
      <Typography.Text>{draft.current.position} · {draft.current.timing} · {label(MSG.CHAT_HELP_REVISION)} {draft.current.revision}</Typography.Text>
      {draft.original.state === 'UNSET' && <Alert type="info" title={label(MSG.CHAT_HELP_UNSET)} />}
      <label className="chat-help-body">{label(MSG.CHAT_HELP_BODY)}<Input.TextArea aria-label={label(MSG.CHAT_HELP_BODY)} value={draft.current.body}
        readOnly={!canSave || busy || pending || !!dialog} onChange={(event) => edit({ body: event.target.value })} /></label>
      <Typography.Text type="secondary">{label(MSG.CHAT_HELP_HINT)}</Typography.Text>
    </> : <Spin description={label(MSG.CHAT_HELP_LOADING)}><div style={{ height: 80 }} /></Spin>}
    <Modal open={dialog?.kind === 'save'} title={label(MSG.CHAT_HELP_SAVE_SCOPE)} onCancel={close} onOk={() => void save()} confirmLoading={busy}
      closable={!busy} mask={{ closable: !busy }} keyboard={!busy} okText={label(MSG.GRID_SAVE)} cancelText={label(MSG.COMMON_CANCEL)} okButtonProps={{ disabled: !canSave || !reason.trim() || pending }} destroyOnHidden>
      <p>{dialog?.target.helpCd} · {dialog?.target.name} · {label(MSG.CHAT_HELP_REVISION)} {dialog?.target.revision}</p>
      <Input.TextArea aria-label={label(MSG.CHAT_HELP_REASON)} placeholder={label(MSG.CHAT_HELP_REASON)} value={reason} disabled={busy} onChange={(e) => setReason(e.target.value)} />
    </Modal>
    <Modal open={dialog?.kind === 'edit' || dialog?.kind === 'preview'} title={label(dialog?.kind === 'edit' ? MSG.CHAT_HELP_ENLARGE : MSG.CHAT_HELP_PREVIEW)}
      width="min(90vw, 1100px)" style={{ top: 20 }} onCancel={close} closable={!busy} keyboard={!busy} mask={{ closable: !busy }} destroyOnHidden
      modalRender={(node) => <div className="chat-help-resize">{node}</div>}
      footer={dialog?.kind === 'edit' ? <Space><Button onClick={close}>{label(MSG.COMMON_CANCEL)}</Button><Button type="primary" disabled={!canSave} onClick={() => { if (dialog && dialog.target.helpCd === selected && dialog.target.revision === draft?.current.revision) edit({ body: dialog.buffer }); close(); }}>{label(MSG.CHAT_HELP_APPLY)}</Button></Space> : <Button disabled={busy} onClick={close}>{label(MSG.COMMON_CLOSE)}</Button>}>
      {dialog?.kind === 'edit' ? <Input.TextArea aria-label={label(MSG.CHAT_HELP_ENLARGE)} value={dialog.buffer} readOnly={!canSave} onChange={(e) => setDialog({ ...dialog, buffer: e.target.value })} />
        : display ? <ContentPreview display={display} /> : busy ? <Spin /> : <Alert type="error" title={error || label(MSG.CHAT_HELP_FAILED)} />}
    </Modal>
  </div></FillColumn>;
}
