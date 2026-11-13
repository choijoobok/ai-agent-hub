// @feat FEAT-510
import { describe, expect, it } from 'vitest';
import { FAVORITE_ENDPOINTS } from '@/shared/favorite/api/endpoints';
import { GRID_PREF_ENDPOINTS } from '@/shared/grid/pref/api/endpoints';
import { SEARCH_COND_ENDPOINTS } from '@/shared/searchform/api/endpoints';
import { MENU_STAT_ENDPOINTS } from '@/shared/menustat/api/endpoints';
import { screenFor } from '@/shared/routes/screenRegistry';
import { DASHBOARD_TAB } from '@/shared/stores/tabStore';

describe('TC-5101 공통 사용자 상태의 system 정식 API 경로', () => {
  it('즐겨찾기·그리드·저장 조회조건은 구 common 경로를 남기지 않는다', () => {
    expect(FAVORITE_ENDPOINTS).toEqual({
      getList: '/system/common/favorite/getList',
      save: '/system/common/favorite/save',
      delete: '/system/common/favorite/delete',
    });
    expect(GRID_PREF_ENDPOINTS).toEqual({
      getOne: '/system/common/gridPref/getOne',
      save: '/system/common/gridPref/save',
      delete: '/system/common/gridPref/delete',
    });
    expect(SEARCH_COND_ENDPOINTS).toEqual({
      getList: '/system/common/searchCond/getList',
      save: '/system/common/searchCond/save',
      delete: '/system/common/searchCond/delete',
    });
    expect(MENU_STAT_ENDPOINTS).toEqual({
      saveSelect: '/system/common/menu/saveSelect',
      getFrequent: '/system/common/menu/getFrequent',
    });
  });

  it('TC-5105 대시보드는 system 경로 하나로 열리고 구 경로를 남기지 않는다', () => {
    expect(DASHBOARD_TAB.pathname).toBe('/admin/system/dashboard');
    expect(screenFor('/admin/system/dashboard')).not.toBeNull();
    expect(screenFor('/dashboard')).toBeNull();
  });
});
