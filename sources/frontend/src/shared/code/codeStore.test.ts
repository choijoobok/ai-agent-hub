import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CodeListResponse } from '@/shared/code/api/codeApi';
import type { useCodeStore as CodeStore } from '@/shared/code/codeStore';

const post = vi.fn();

vi.mock('@/shared/api/request', () => ({
  post: (url: string, body?: unknown) => post(url, body) as Promise<unknown>,
  get: vi.fn(),
  request: vi.fn(),
}));

/**
 * 공통코드 프론트 캐시 — sys/code.md · backend/caching.md.
 *
 * <p>여기서 지키는 것 넷이다.
 *
 * <ol>
 *   <li><b>같은 그룹을 두 번 묻지 않는다</b> — 캐시의 존재 이유가 그것이다
 * <li><b>같은 틱의 그룹들이 요청 하나로 모인다</b> — sys/code.md 이 캐싱의 근거로 든
 *       <i>"한 화면이 콤보를 여러 개 띄울 때의 왕복"</i> 은 <b>첫 진입</b>의 문제이고,
 *       캐시만으로는 그때가 풀리지 않는다
 * <li><b>버전이 다르면 통째로 버린다</b>(sys/code.md) — 그룹 단위 버전이 없으므로
 *   <li><b>실패를 굳히지 않는다</b> — 빈 목록으로 눌러앉으면 콤보가 영영 비고 원인이
 *       화면에 드러나지 않는다
 * </ol>
 *
 * <p>스토어가 모듈 단위 싱글턴이고 `localStorage` 에서 시작하므로, 케이스마다
 * {@code resetModules} 로 <b>다시 만든다</b> — 그러지 않으면 앞 케이스의 캐시가 뒤로 샌다.
 */
describe('codeStore', () => {
  const CACHE_KEY = 'backoffice.code';

  async function freshStore(): Promise<typeof CodeStore> {
    vi.resetModules();
    return (await import('@/shared/code/codeStore')).useCodeStore;
  }

  function answer(response: CodeListResponse): void {
    post.mockResolvedValueOnce(response);
  }

  /** 마이크로태스크 하나로 모아 보내므로, 나간 뒤를 보려면 그만큼 기다린다. */
  const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

  beforeEach(() => {
    post.mockReset();
    window.localStorage.clear();
  });

  it('그룹을 처음 요구하면 서버에서 받아 담는다', async () => {
    const store = await freshStore();
    answer({ version: 3, groups: { LANG_CD: ['ko', 'en'] } });

    store.getState().ensure(['LANG_CD']);
    await settle();

    expect(store.getState().groups.LANG_CD).toEqual(['ko', 'en']);
    expect(store.getState().version).toBe(3);
  });

  it('같은 틱에 뜬 콤보들의 그룹이 요청 하나로 모인다 — 첫 진입의 왕복이 콤보 수만큼 나지 않는다', async () => {
    const store = await freshStore();
    answer({ version: 1, groups: { A: ['a'], B: ['b'], C: [] } });

    // 콤보 셋이 같은 커밋에서 뜬 모양이다.
    store.getState().ensure(['A']);
    store.getState().ensure(['B']);
    store.getState().ensure(['C']);
    await settle();

    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith('/system/common/code/getList', { grpCds: ['A', 'B', 'C'] });
  });

  it('이미 들고 있는 그룹은 다시 묻지 않는다', async () => {
    const store = await freshStore();
    answer({ version: 1, groups: { LANG_CD: ['ko'] } });
    store.getState().ensure(['LANG_CD']);
    await settle();

    store.getState().ensure(['LANG_CD']);
    await settle();

    expect(post).toHaveBeenCalledTimes(1);
  });

  it('없는 그룹도 다시 묻지 않는다 — 빈 배열이 *받았는데 비어 있다* 는 답이다', async () => {
    const store = await freshStore();
    answer({ version: 1, groups: { NO_SUCH: [] } });
    store.getState().ensure(['NO_SUCH']);
    await settle();

    store.getState().ensure(['NO_SUCH']);
    await settle();

    expect(post).toHaveBeenCalledTimes(1);
    expect(store.getState().groups.NO_SUCH).toEqual([]);
  });

  it('받은 것을 localStorage 에 남긴다 — 다음 접속은 그것으로 시작한다', async () => {
    const first = await freshStore();
    answer({ version: 7, groups: { LANG_CD: ['ko'] } });
    first.getState().ensure(['LANG_CD']);
    await settle();

    // 새로 뜬 브라우저 탭이다 — 서버를 부르지 않고도 후보를 갖고 있어야 한다.
    const second = await freshStore();

    expect(second.getState().version).toBe(7);
    expect(second.getState().groups.LANG_CD).toEqual(['ko']);

    second.getState().ensure(['LANG_CD']);
    await settle();
    expect(post).toHaveBeenCalledTimes(1);
  });

  describe('버전 대조 — sys/code.md', () => {
    it('버전이 같으면 아무 일도 하지 않는다 — 로그인할 때마다 코드를 다시 받지 않는다', async () => {
      const store = await freshStore();
      answer({ version: 5, groups: { LANG_CD: ['ko'] } });
      store.getState().ensure(['LANG_CD']);
      await settle();

      store.getState().syncWithSession(5);

      expect(store.getState().groups.LANG_CD).toEqual(['ko']);
      expect(window.localStorage.getItem(CACHE_KEY)).not.toBeNull();
    });

    it('버전이 다르면 통째로 버린다 — 그룹 단위 버전이 없으므로 전부가 낡은 것이다', async () => {
      const store = await freshStore();
      answer({ version: 5, groups: { LANG_CD: ['ko'] } });
      store.getState().ensure(['LANG_CD']);
      await settle();

      store.getState().syncWithSession(6);

      expect(store.getState().groups).toEqual({});
      expect(store.getState().version).toBe(6);
      expect(window.localStorage.getItem(CACHE_KEY)).toBeNull();
    });

    it('버려진 뒤에는 다시 묻는다', async () => {
      const store = await freshStore();
      answer({ version: 5, groups: { LANG_CD: ['ko'] } });
      store.getState().ensure(['LANG_CD']);
      await settle();

      store.getState().syncWithSession(6);
      answer({ version: 6, groups: { LANG_CD: ['ko', 'en'] } });
      store.getState().ensure(['LANG_CD']);
      await settle();

      expect(post).toHaveBeenCalledTimes(2);
      expect(store.getState().groups.LANG_CD).toEqual(['ko', 'en']);
    });

    it('버전을 안 보내는 서버에는 판단하지 않는다 — 들고 있는 것을 그대로 쓴다', async () => {
      const store = await freshStore();
      answer({ version: 5, groups: { LANG_CD: ['ko'] } });
      store.getState().ensure(['LANG_CD']);
      await settle();

      store.getState().syncWithSession(undefined);

      expect(store.getState().groups.LANG_CD).toEqual(['ko']);
    });

    it('응답의 버전이 들고 있던 것과 다르면 옛 그룹을 함께 버린다', async () => {
      const store = await freshStore();
      answer({ version: 1, groups: { A: ['a'] } });
      store.getState().ensure(['A']);
      await settle();

      // 로그인 뒤 다른 사람이 코드를 저장했다 — 그 사이에 뜬 콤보가 새 버전을 물고 온다.
      answer({ version: 2, groups: { B: ['b'] } });
      store.getState().ensure(['B']);
      await settle();

      expect(store.getState().version).toBe(2);
      expect(store.getState().groups).toEqual({ B: ['b'] });
    });
  });

  it('실패를 굳히지 않는다 — 다음에 그 콤보가 뜰 때 다시 묻는다', async () => {
    const store = await freshStore();
    post.mockRejectedValueOnce(new Error('네트워크'));

    store.getState().ensure(['LANG_CD']);
    await settle();
    expect(store.getState().groups.LANG_CD).toBeUndefined();

    answer({ version: 1, groups: { LANG_CD: ['ko'] } });
    store.getState().ensure(['LANG_CD']);
    await settle();

    expect(post).toHaveBeenCalledTimes(2);
    expect(store.getState().groups.LANG_CD).toEqual(['ko']);
  });

  it('손상된 캐시는 없는 것으로 친다 — 믿으면 콤보가 조용히 빈다', async () => {
    window.localStorage.setItem(CACHE_KEY, '{ 깨진 것');
    const store = await freshStore();

    expect(store.getState().version).toBeNull();
    expect(store.getState().groups).toEqual({});
  });
});
