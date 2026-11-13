import { beforeEach, describe, expect, it, vi } from 'vitest';
import { recordDownload } from '@/shared/grid/excel/downloadLog';

const post = vi.fn();

vi.mock('@/shared/api/request', () => ({
  post: (url: string, body?: unknown) => post(url, body) as Promise<unknown>,
  get: vi.fn(),
  request: vi.fn(),
}));

/**
 * 다운로드 이력 — playbook/excel-download.md · LOG-08.
 *
 * <p>여기서 지키는 것 셋이다.
 *
 * <ol>
 *   <li><b>보내는 것이 셋뿐이다</b> — `menuCd` 는 `X-Menu-Code` 헤더가 이미 나르며,
 *       body 에도 담으면 같은 사실이 한 행에 두 벌 생긴다(`ExcelLogRequest`)
 *   <li><b>실패해도 던지지 않는다</b> — 부수 효과라 화면에 알릴 것이 없고, 파일은 이미
 *       사용자의 디스크에 있다
 *   <li><b>기다리지 않는다</b> — 부르는 쪽은 `void` 를 받는다
 * </ol>
 */
describe('엑셀 다운로드 이력', () => {
  beforeEach(() => {
    post.mockReset();
    post.mockResolvedValue(undefined);
  });

  it('갈래 · 행 수 · 컬럼 키를 보낸다 — menuCd 는 헤더의 것이라 담지 않는다', () => {
    recordDownload('ALL', 42, ['userId', 'userNm']);

    expect(post).toHaveBeenCalledWith('/system/common/excel/saveLog', {
      scope: 'ALL',
      rowCount: 42,
      columnKeys: ['userId', 'userNm'],
    });
    expect(post.mock.calls[0][1]).not.toHaveProperty('menuCd');
  });

  it('현재 페이지와 전체가 갈린다 — 행 수만으로는 둘이 같아 보인다', () => {
    recordDownload('PAGE', 100, ['a']);
    recordDownload('ALL', 100, ['a']);

    expect(post.mock.calls.map((call) => (call[1] as { scope: string }).scope))
      .toEqual(['PAGE', 'ALL']);
  });

  it('서버가 실패해도 던지지 않는다 — 다운로드는 이미 끝났다', async () => {
    post.mockRejectedValue(new Error('boom'));

    expect(() => recordDownload('PAGE', 1, ['a'])).not.toThrow();
    // 삼킨 거절이 처리될 틈을 준다 — 남으면 unhandled rejection 으로 터진다.
    await Promise.resolve();
    await Promise.resolve();
  });
});
