// @feat FEAT-501
import { describe, expect, it } from 'vitest';
import { SETTINGS_ENDPOINTS } from '@/modules/system/public/settings/api/endpoints';

describe('TC-5015 언어 설정의 system public 경로', () => {
  it('언어 저장은 구 common/user 경로를 남기지 않는다', () => {
    expect(SETTINGS_ENDPOINTS.saveLocale).toBe('/system/common/user/saveLocale');
  });
});
