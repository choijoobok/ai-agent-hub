// @feat FEAT-501
import { NOTIFICATION_ENDPOINTS } from '@/modules/system/public/notification/api/endpoints';
import { PROGRESS_ENDPOINTS } from '@/shared/progress/api/endpoints';
import { describe, expect, it } from 'vitest';

/**
 * 엔드포인트 선언에 <b>`/api` 를 또 적지 않는다</b> — frontend/packages.md.
 *
 * <p>클라이언트의 {@code baseURL} 이 이미 {@code /api} 다. 여기에 또 적으면
 * {@code /api/api/...} 로 나가고 그 경로는 <b>어떤 컨트롤러에도 닿지 않는다</b>.
 *
 * <p><b>그 고장은 조용하다.</b> 2026-09-02 에 진행률 폴링에서 겪었다 —
 * {@code /api/common/progress/…} 로 적혀 있어 매 폴링이 403 을 받았고, 화면은
 * <i>진행률을 확인할 수 없습니다</i> 를 보였다. 그 문구는 <b>서버가 내려갔을 때</b>를 위한
 * 것이라 원인과 정반대를 말했고, 작업 자체는 멀쩡히 끝나므로 <b>고장으로 읽을 근거가
 * 아무 데도 없었다</b>.
 *
 * <p>한 파일을 고치는 것으로는 다시 나지 않게 할 수 없다 — 엔드포인트 선언은 화면마다
 * 늘고, 새로 적는 사람은 <b>`/api` 로 시작하는 URL</b>을 자연스럽게 여긴다. 그래서
 * <b>전부를 훑는다</b>.
 */
describe('엔드포인트 선언 — baseURL 을 두 번 적지 않는다', () => {
  /*
    `eager` 로 읽는다 — 검사가 파일 목록을 <b>빌드 시각에</b> 알아야 새로 생긴 선언 파일이
    자동으로 딸려 온다. 목록을 손으로 적으면 다음에 만든 화면이 이 검사를 지나간다.
  */
  const files: Record<string, string> = import.meta.glob('/src/**/api/endpoints.ts', {
    eager: true,
    query: '?raw',
    import: 'default',
  });

  it('선언 파일을 실제로 찾는다', () => {
    // 이 줄이 없으면 glob 이 빗나갔을 때 아래 검사가 **0건을 통과**한다.
    expect(Object.keys(files).length).toBeGreaterThan(5);
  });

  it('어떤 경로도 /api 로 시작하지 않는다', () => {
    const offenders: string[] = [];
    for (const [path, source] of Object.entries(files)) {
      for (const [, url] of code(source).matchAll(/['"`](\/api\/[^'"`]*)['"`]/g)) {
        offenders.push(`${path} → ${url}`);
      }
    }

    expect(offenders).toEqual([]);
  });

  it('모든 실제 선언은 모듈 우선이며 플랫폼 기술 조회도 목표 경로를 쓴다', () => {
    const offenders = Object.entries(files).flatMap(([path, source]) =>
      [...code(source).matchAll(/['"`](\/(?:public|common|service)\/[^'"`]*)['"`]/g)]
        .map(([, url]) => `${path} → ${url}`));
    expect(offenders).toEqual([]);
    expect(NOTIFICATION_ENDPOINTS.getSystemInfo).toBe('/system/common/system/getSystemInfo');
    expect(PROGRESS_ENDPOINTS.getOne('job/1')).toBe('/system/common/progress/getProgress/job%2F1');
  });

  /*
    <b>주석을 뺀다.</b> 이 선언 파일들은 설명이 길고 거기에 `/api/common/**` 같은 경로가
    <b>글로 적혀</b> 있다 — 그것까지 잡으면 검사가 늘 붉고, 늘 붉은 검사는 곧 꺼진다.
  */
  function code(source: string): string {
    return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*/g, '');
  }
});
