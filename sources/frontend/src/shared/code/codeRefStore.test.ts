// @feat FEAT-504
import { beforeEach, expect, it, vi } from 'vitest';
const post = vi.fn();
vi.mock('@/shared/api/request', () => ({ post: (url: string, body: unknown) => post(url, body) as Promise<unknown>, get: vi.fn(), request: vi.fn() }));
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
beforeEach(() => { vi.resetModules(); post.mockReset(); localStorage.clear(); });
it('TC-5046 참조 튜플은 그룹 전체·다른 공백/부모와 섞이지 않는다', async () => {
  const { useCodeStore: store, codeRefKey } = await import('./codeStore');
  const a = { grpCd: 'G', refCd: 'PARENT', refVal: ' A ' };
  const b = { ...a, refVal: 'A' };
  store.getState().accept({ version: 2, groups: { G: ['ALL'] } });
  post.mockResolvedValueOnce({ version: 2, codes: ['SPACED'] }).mockResolvedValueOnce({ version: 2, codes: [] });
  store.getState().ensureByRef(a); store.getState().ensureByRef(b); await settle();
  expect(store.getState().groups.G).toEqual(['ALL']);
  expect(store.getState().refs[codeRefKey(a)]).toEqual(['SPACED']);
  expect(store.getState().refs[codeRefKey(b)]).toEqual([]);
  store.getState().ensureByRef(b); await settle(); expect(post).toHaveBeenCalledTimes(2);
  expect(codeRefKey({ grpCd: 'A B', refCd: 'C', refVal: 'D' })).not.toBe(codeRefKey({ grpCd: 'A', refCd: 'B C', refVal: 'D' }));
});
it('TC-5046 새 CODE 버전은 양쪽 캐시를 지우고 늦은 전체/참조 응답을 버린다', async () => {
  const { useCodeStore: store, codeRefKey } = await import('./codeStore');
  const request = { grpCd: 'G', refCd: 'PARENT', refVal: 'A' };
  let old!: (value: unknown) => void;
  post.mockImplementationOnce(() => new Promise((resolve) => { old = resolve; }));
  store.getState().accept({ version: 1, groups: { G: ['OLD'] } }); store.getState().ensureByRef(request);
  store.getState().syncWithSession(2);
  post.mockResolvedValueOnce({ version: 2, codes: ['NEW'] }); store.getState().ensureByRef(request); await settle();
  old({ version: 1, codes: ['OLD'] }); await settle();
  store.getState().accept({ version: 1, groups: { G: ['OLD'] } }); store.getState().syncWithSession(1);
  expect(store.getState().version).toBe(2); expect(store.getState().groups).toEqual({});
  expect(store.getState().refs[codeRefKey(request)]).toEqual(['NEW']);
  store.getState().accept({ version: 3, groups: { H: ['H'] } }); expect(store.getState().refs).toEqual({});
});
it('TC-5046 미완성 조건은 요청하지 않고 실패를 빈 성공으로 캐시하지 않는다', async () => {
  const { useCodeStore: store, codeRefKey } = await import('./codeStore');
  const request = { grpCd: 'G', refCd: 'PARENT', refVal: 'A' };
  store.getState().ensureByRef({ ...request, refVal: '' }); expect(post).not.toHaveBeenCalled();
  post.mockRejectedValueOnce(new Error('시험 실패')); store.getState().ensureByRef(request); await settle();
  expect(store.getState().refs[codeRefKey(request)]).toBeUndefined(); expect(store.getState().refStatus[codeRefKey(request)]).toBe('error');
  post.mockResolvedValueOnce({ version: 2, codes: ['A'] }); store.getState().ensureByRef(request, true); await settle();
  expect(store.getState().refs[codeRefKey(request)]).toEqual(['A']);
});
