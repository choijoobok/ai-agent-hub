import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { postDownload } from '@/shared/api/download';
import { ApiError } from '@/shared/api/types';

const requestFn = vi.hoisted(() => vi.fn());
vi.mock('@/shared/api/client', () => ({ apiClient: { request: requestFn } }));

/** 축약 — 실패 응답의 body 도 Blob 이다(그것이 이 파일의 주제다). */
function failure(status: number, envelope: unknown): AxiosError {
  const error = new AxiosError('failed');
  error.response = {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: new Blob([JSON.stringify(envelope)], { type: 'application/json' }),
  };
  return error;
}

/**
 * 파일 받기 — playbook/excel-download.md 의 <b>세 번째 갈래</b>가 쓰는 길.
 *
 * <p><b>여기서 세는 것은 실패 쪽이다.</b> 성공은 바이트를 그대로 넘기면 끝이고, 조용히
 * 틀리는 것은 실패다: {@code responseType: 'blob'} 이면 <b>실패 응답의 body 도 Blob</b> 이라
 * {@code error.response.data.message} 가 언제나 {@code undefined} 다 — 서버가 보낸
 * <i>"조회 조건을 좁혀 주세요"</i> 가 사라지고 화면에는 <b>네트워크 오류</b>만 뜬다.
 * 원인이 4xx 인데 사용자는 서버가 죽었나 를 의심하게 된다.
 */
describe('파일 받기', () => {
  beforeEach(() => vi.clearAllMocks());

  it('성공하면 바이트와 서버가 지은 이름을 준다', async () => {
    requestFn.mockResolvedValue({
      status: 200,
      data: new Blob(['x'], { type: 'application/octet-stream' }),
      headers: { 'content-disposition': "attachment; filename*=UTF-8''%EC%A3%BC%EB%AC%B8.xlsx" },
    });

    const file = await postDownload('/sample/service/excel');

    expect(file.fileName).toBe('주문.xlsx');
  });

  /*
    <b>{@code filename*} 이 먼저다</b>(RFC 5987). 옛 {@code filename=} 은 ASCII 로 뭉개진
    대체 이름이라, 둘 다 있을 때 뒤를 고르면 <b>한글 이름이 깨진다</b>.
  */
  it('둘 다 있으면 UTF-8 쪽을 고른다', async () => {
    requestFn.mockResolvedValue({
      status: 200,
      data: new Blob(['x']),
      headers: {
        'content-disposition':
          "attachment; filename=\"order.xlsx\"; filename*=UTF-8''%EC%A3%BC%EB%AC%B8.xlsx",
      },
    });

    expect((await postDownload('/u')).fileName).toBe('주문.xlsx');
  });

  it('이름을 말하지 않으면 null 이다 — 부르는 쪽이 짓는다', async () => {
    requestFn.mockResolvedValue({ status: 200, data: new Blob(['x']), headers: {} });

    expect((await postDownload('/u')).fileName).toBeNull();
  });

  it('실패하면 Blob 을 되읽어 서버의 문구를 살린다', async () => {
    requestFn.mockRejectedValue(
      failure(400, { success: false, code: 'EXCEL_TOO_MANY', message: '조회 조건을 좁혀 주세요' }));

    await expect(postDownload('/u')).rejects.toMatchObject({
      code: 'EXCEL_TOO_MANY',
      message: '조회 조건을 좁혀 주세요',
      status: 400,
    });
  });

  /*
    <b>200 인데 봉투면 파일이 아니다.</b> 그대로 저장하면 사용자는 {@code .xlsx} 라는 이름의
    <b>열리지 않는 파일</b>을 얻고, 원인은 파일 안에 있어 화면에서는 보이지 않는다.
  */
  it('200 인데 봉투가 오면 저장하지 않고 실패로 만든다', async () => {
    requestFn.mockResolvedValue({
      status: 200,
      data: new Blob([JSON.stringify({ success: false, message: '권한이 없습니다' })], {
        type: 'application/json',
      }),
      headers: {},
    });

    await expect(postDownload('/u')).rejects.toMatchObject({ message: '권한이 없습니다' });
  });

  it('봉투가 아닌 실패는 전송 오류다 — 프록시의 HTML 이 그렇다', async () => {
    const error = new AxiosError('boom');
    error.response = {
      status: 502,
      statusText: '',
      headers: {},
      config: { headers: new AxiosHeaders() },
      data: new Blob(['<html>bad gateway</html>'], { type: 'text/html' }),
    };
    requestFn.mockRejectedValue(error);

    await expect(postDownload('/u')).rejects.toBeInstanceOf(ApiError);
  });
});
