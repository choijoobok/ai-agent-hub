// @feat FEAT-504
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { CodeSelect } from './CodeSelect';
import { useCodeStore } from './codeStore';
import { useMessageStore } from '@/shared/i18n/messageStore';
const post = vi.fn();
vi.mock('@/shared/api/request', () => ({ post: (url: string, body: unknown) => post(url, body) as Promise<unknown>, get: vi.fn(), request: vi.fn() }));
beforeEach(() => {
  post.mockReset(); localStorage.clear(); useCodeStore.setState({ version: null, groups: {}, refs: {}, refStatus: {} });
  useMessageStore.setState({ status: 'ready', messages: { 'code.G.A': '후보 A', 'code.G.B': '후보 B', 'code.G.OLD': '기존 값', 'codeCombo.parent': '상위 조건 선택', 'codeCombo.loading': '조회 중', 'codeCombo.empty': '후보 없음', 'codeCombo.retry': '조회 실패 다시 조회', 'codeCombo.unsupported': '현재 조건 미지원' } });
});
it('TC-5046 미완성은 전체 조회0·첫 후보 자동선택0·기존 미지원 값 보존', async () => {
  const change = vi.fn(); const view = render(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: '' }} value="OLD" onChange={change} />);
  fireEvent.mouseDown(screen.getByRole('combobox')); expect(await screen.findByText('상위 조건 선택')).toBeTruthy(); expect(post).not.toHaveBeenCalled();
  post.mockResolvedValueOnce({ version: 1, codes: ['A'] }); view.rerender(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'X' }} value="OLD" onChange={change} />);
  await waitFor(() => expect(screen.getAllByText('기존 값 (현재 조건 미지원)').length).toBeGreaterThan(0));
  expect(post).toHaveBeenCalledWith('/system/common/code/getListByRef', { grpCd: 'G', refCd: 'P', refVal: 'X' }); expect(change).not.toHaveBeenCalled();
  post.mockResolvedValueOnce({ version: 1, codes: [] }); view.rerender(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'NONE' }} value="OLD" onChange={change} />);
  expect(await screen.findByText('후보 없음')).toBeTruthy(); expect(change).not.toHaveBeenCalled();
});
it('TC-5046 이전 조건의 늦은 응답은 현재 후보를 덮지 않는다', async () => {
  let old!: (value: unknown) => void; post.mockImplementationOnce(() => new Promise((resolve) => { old = resolve; }));
  const view = render(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'X' }} />);
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1)); post.mockResolvedValueOnce({ version: 1, codes: ['B'] });
  view.rerender(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'Y' }} />); fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(await screen.findByText('후보 B')).toBeTruthy(); old({ version: 1, codes: ['A'] });
  await waitFor(() => expect(useCodeStore.getState().refs['["G","P","X"]']).toEqual(['A']));
  expect(screen.queryByText('후보 A')).toBeNull(); expect(screen.getByText('후보 B')).toBeTruthy();
});
it('TC-5046 로딩·실패·빈 성공을 구분하고 실패만 명시 재조회한다', async () => {
  let reject!: (error: Error) => void; post.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; }));
  render(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'X' }} />); fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(await screen.findByText('조회 중')).toBeTruthy(); reject(new Error('시험 장애'));
  const retry = await screen.findByRole('button', { name: '조회 실패 다시 조회' }); post.mockResolvedValueOnce({ version: 1, codes: [] }); fireEvent.click(retry);
  expect(await screen.findByText('후보 없음')).toBeTruthy(); expect(post).toHaveBeenCalledTimes(2);
});
it('TC-5046 열린 콤보도 CODE 버전 통지 뒤 후보를 다시 읽는다', async () => {
  post.mockResolvedValueOnce({ version: 1, codes: ['A'] });
  render(<CodeSelect grpCd="G" refFilter={{ refCd: 'P', refVal: 'X' }} />); fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(await screen.findByText('후보 A')).toBeTruthy(); post.mockResolvedValueOnce({ version: 2, codes: ['B'] });
  useCodeStore.getState().syncWithSession(2);
  expect(await screen.findByText('후보 B')).toBeTruthy(); expect(screen.queryByText('후보 A')).toBeNull(); expect(post).toHaveBeenCalledTimes(2);
});
