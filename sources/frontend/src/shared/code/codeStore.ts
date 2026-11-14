// @feat FEAT-504
import { create } from 'zustand';
import { fetchCodes, fetchCodesByRef, type CodeListResponse, type CodeRefRequest, type CodeRefListResponse } from '@/shared/code/api/codeApi';
import { readStorage, removeStorage, writeStorage } from '@/shared/i18n/lang';

interface CodeCache { version: number; groups: Record<string, string[]> }
interface CodeState {
  /** 데이터가 없어도 이미 관측한 버전보다 늦은 응답을 수락하지 않는 하한이다. */
  version: number | null;
  groups: Record<string, string[]>;
  refs: Record<string, string[]>;
  refStatus: Record<string, 'loading' | 'error'>;
  ensure: (groups: string[]) => void;
  ensureByRef: (request: CodeRefRequest, retry?: boolean) => void;
  accept: (response: CodeListResponse) => void;
  acceptRef: (key: string, response: CodeRefListResponse) => boolean;
  syncWithSession: (version?: number) => void;
}
const CACHE_KEY = 'backoffice.code';
const pending = new Set<string>();
const inFlight = new Map<string, { version: number | null }>();
const refFlight = new Map<string, { version: number | null }>();
let scheduled = false;
const validVersion = (value: number): boolean => Number.isSafeInteger(value) && value >= 0;
export const codeRefKey = (request: CodeRefRequest): string => JSON.stringify([request.grpCd, request.refCd, request.refVal]);
export const completeCodeRef = (request: CodeRefRequest): boolean =>
  !!request.grpCd.trim() && request.grpCd.length <= 30 && !!request.refCd.trim() && request.refCd.length <= 30 && request.refVal.length > 0 && request.refVal.length <= 200;

/** 전체 그룹만 영속 보관한다. 참조 조건/후보는 같은 CODE 버전의 별도 메모리 캐시다. */
export const useCodeStore = create<CodeState>((set, get) => ({
  ...hydrate(), refs: {}, refStatus: {},
  ensure: (grpCds) => {
    const { groups, version } = get();
    for (const group of grpCds) {
      if (!group || Object.hasOwn(groups, group) || (inFlight.has(group) && inFlight.get(group)?.version === version)) continue;
      pending.add(group);
    }
    if (pending.size && !scheduled) { scheduled = true; queueMicrotask(() => { scheduled = false; void flush(); }); }
  },
  ensureByRef: (request, retry = false) => {
    if (!completeCodeRef(request)) return;
    const key = codeRefKey(request); const state = get();
    if (Object.hasOwn(state.refs, key) || (refFlight.has(key) && refFlight.get(key)?.version === state.version) || (!retry && state.refStatus[key] === 'error')) return;
    const token = { version: state.version }; refFlight.set(key, token);
    set({ refStatus: { ...state.refStatus, [key]: 'loading' } });
    void fetchCodesByRef(request).then((response) => {
      if (refFlight.get(key) !== token) return;
      if (!get().acceptRef(key, response)) set({ refStatus: { ...get().refStatus, [key]: 'error' } });
    }).catch(() => {
      // 실패는 빈 성공이 아니다. 명시 재조회로 복구한다.
      if (refFlight.get(key) === token) set({ refStatus: { ...get().refStatus, [key]: 'error' } });
    }).finally(() => { if (refFlight.get(key) === token) refFlight.delete(key); });
  },
  accept: ({ version, groups }) => {
    const state = get(); if (!validVersion(version) || (state.version !== null && version < state.version)) return;
    const same = state.version === version; const next = { ...(same ? state.groups : {}), ...groups };
    writeStorage(CACHE_KEY, JSON.stringify({ version, groups: next }));
    set({ version, groups: next, refs: same ? state.refs : {}, refStatus: same ? state.refStatus : {} });
  },
  acceptRef: (key, { version, codes }) => {
    const state = get(); if (!validVersion(version) || (state.version !== null && version < state.version)) return false;
    const same = state.version === version; const refStatus = { ...(same ? state.refStatus : {}) }; delete refStatus[key];
    if (!same) removeStorage(CACHE_KEY);
    set({ version, groups: same ? state.groups : {}, refs: { ...(same ? state.refs : {}), [key]: codes }, refStatus }); return true;
  },
  syncWithSession: (version) => {
    if (version === undefined || !validVersion(version) || (get().version !== null && version <= get().version!)) return;
    removeStorage(CACHE_KEY); set({ version, groups: {}, refs: {}, refStatus: {} });
  },
}));

/** 같은 렌더의 전체 그룹 요청은 하나로 모으되 새 버전의 요청을 이전 요청이 지우지 않는다. */
async function flush(): Promise<void> {
  const grpCds = [...pending]; pending.clear(); if (!grpCds.length) return;
  const token = { version: useCodeStore.getState().version }; grpCds.forEach((group) => inFlight.set(group, token));
  try { useCodeStore.getState().accept(await fetchCodes(grpCds)); }
  catch { /* 전체 그룹의 기존 재진입 재조회 규약: 실패를 빈 목록으로 저장하지 않는다. */ }
  finally { grpCds.forEach((group) => { if (inFlight.get(group) === token) inFlight.delete(group); }); }
}
function hydrate(): Pick<CodeState, 'version' | 'groups'> {
  const empty = { version: null, groups: {} }; const raw = readStorage(CACHE_KEY); if (!raw) return empty;
  try {
    const value = JSON.parse(raw) as CodeCache;
    return validVersion(value.version) && value.groups && typeof value.groups === 'object' && !Array.isArray(value.groups)
      && Object.values(value.groups).every((codes) => Array.isArray(codes) && codes.every((code) => typeof code === 'string'))
      ? { version: value.version, groups: value.groups } : empty;
  } catch { return empty; }
}
