// @feat FEAT-504
import { useCallback, useEffect, useMemo } from 'react';
import { codeRefKey, completeCodeRef, useCodeStore } from './codeStore';
import { codeLabel } from '@/shared/i18n/codeLabel';
export interface CodeRefFilter { refCd: string; refVal: string }

export function useCodeOptionsByRef(grpCd: string, filter?: CodeRefFilter) {
  const refCd = filter?.refCd; const refVal = filter?.refVal;
  const request = useMemo(() => refCd !== undefined && refVal !== undefined ? { grpCd, refCd, refVal } : null, [grpCd, refCd, refVal]);
  const key = request && completeCodeRef(request) ? codeRefKey(request) : null;
  const codes = useCodeStore((state) => key ? state.refs[key] : undefined);
  const pending = useCodeStore((state) => key ? state.refStatus[key] : undefined);
  const version = useCodeStore((state) => state.version);
  useEffect(() => { if (key && request) useCodeStore.getState().ensureByRef(request); }, [key, request, version]);
  const retry = useCallback(() => { if (key && request) useCodeStore.getState().ensureByRef(request, true); }, [key, request]);
  const options = useMemo(() => (codes ?? []).map((value) => ({ value, text: codeLabel(grpCd, value) })), [grpCd, codes]);
  const status = !key ? 'incomplete' : codes !== undefined ? 'ready' : pending === 'error' ? 'error' : 'loading';
  return { options, status, retry };
}
