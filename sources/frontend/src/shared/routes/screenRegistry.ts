import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import { SAMPLE_SCREENS } from '@/shared/routes/sampleScreens';

/**
 * `MENU_URL` → 화면 컴포넌트 — frontend/packages.md.
 *
 * <p><b>정적 맵인 것이 결정이다.</b> 서버가 준 문자열로 동적 `import` 경로를 만들면 번들
 * 분석이 불가능하고 보안상도 좋지 않다. <b>키가 곧 `MENU_URL`</b> 이라 라우트와 맵 키가 같은
 * 값이며 어긋날 수가 없다 — 별도의 "컴포넌트 키" 컬럼을 두지 않은 이유이기도 하다.
 *
 * <p><b>프론트는 서버 JAR 에 임베드되어 원자적으로 함께 배포된다</b>(build-deploy.md). 그래서
 * <i>"프론트 배포 없이 메뉴 추가"</i> 라는 이점이 성립하지 않고, 여기 없는 `MENU_URL` 은
 * 데이터가 앞서간 것이 아니라 <b>아직 만들지 않은 화면</b>이다.
 */
const PRODUCT_SCREENS: Record<string, LazyExoticComponent<ComponentType>> = {
  // @feat FEAT-011
  '/admin/hub/catalog-relation': lazy(async () => ({ default: (await import('@/modules/admin/features/catalogrelation/pages/CatalogRelationPage')).CatalogRelationPage })),
  // @feat FEAT-003
  '/admin/hub/catalog': lazy(async () => ({ default: (await import('@/modules/admin/features/catalog/pages/CatalogPage')).CatalogPage })),
  // @feat FEAT-002
  '/admin/hub/data-source': lazy(async () => ({ default: (await import('@/modules/admin/features/datasource/pages/DataSourcePage')).DataSourcePage })),
  // @feat FEAT-005
  // @feat FEAT-001
  '/admin/hub/target-system': lazy(async () => ({ default: (await import('@/modules/admin/features/targetsystem/pages/TargetSystemPage')).TargetSystemPage })),
  '/admin/hub/knowledge-tag': lazy(async () => ({
    default: (await import('@/modules/admin/features/searchtag/pages/SearchTagPage')).SearchTagPage,
  })),
  '/admin/hub/chat-help': lazy(async () => ({ default: (await import('@/modules/admin/features/chathelp/pages/ChatHelpPage')).ChatHelpPage })),
  '/admin/hub/portal-user': lazy(async () => ({
    default: (await import('@/modules/admin/features/portaluser/pages/PortalUserPage')).PortalUserPage,
  })),
  '/admin/hub/quota-grade': lazy(async () => ({
    default: (await import('@/modules/admin/features/quotagrade/pages/QuotaGradePage')).QuotaGradePage,
  })),
  '/admin/hub/user-group': lazy(async () => ({
    default: (await import('@/modules/admin/features/usergroup/pages/UserGroupPage')).UserGroupPage,
  })),
  '/admin/system/dashboard': lazy(async () => ({
    default: (await import('@/modules/system/features/dashboard/pages/DashboardPage')).DashboardPage,
  })),
  '/admin/system/message': lazy(async () => ({
    default: (await import('@/modules/system/features/message/pages/MessageMgmtPage')).MessageMgmtPage,
  })),
  '/admin/system/config': lazy(async () => ({
    default: (await import('@/modules/system/features/config/pages/ConfigMgmtPage')).ConfigMgmtPage,
  })),
  '/admin/system/code': lazy(async () => ({
    default: (await import('@/modules/system/features/code/pages/CodeMgmtPage')).CodeMgmtPage,
  })),
  '/admin/system/api-catalog': lazy(async () => ({
    default: (await import('@/modules/system/features/apicatalog/pages/ApiCatalogPage')).ApiCatalogPage,
  })),
  '/admin/system/menu': lazy(async () => ({
    default: (await import('@/modules/system/features/menu/pages/MenuTreePage')).MenuTreePage,
  })),
  '/admin/system/role': lazy(async () => ({
    default: (await import('@/modules/system/features/role/pages/RoleMgmtPage')).RoleMgmtPage,
  })),
  '/admin/system/role-auth': lazy(async () => ({
    default: (await import('@/modules/system/features/roleauth/pages/RoleAuthMgmtPage')).RoleAuthMgmtPage,
  })),
  '/admin/system/log/access': lazy(async () => ({
    default: (await import('@/modules/system/features/accesslog/pages/AccessLogPage')).AccessLogPage,
  })),
  '/admin/system/log/login': lazy(async () => ({
    default: (await import('@/modules/system/features/loginlog/pages/LoginLogPage')).LoginLogPage,
  })),
  '/admin/system/log/error': lazy(async () => ({
    default: (await import('@/modules/system/features/errorlog/pages/ErrorLogPage')).ErrorLogPage,
  })),
  '/admin/system/user': lazy(async () => ({
    default: (await import('@/modules/system/features/user/pages/UserMgmtPage')).UserMgmtPage,
  })),
  '/admin/system/user-auth': lazy(async () => ({
    default: (await import('@/modules/system/features/userauth/pages/UserAuthMgmtPage')).UserAuthMgmtPage,
  })),
  /*
    배치 둘 — 65. **폴더가 갈려 있는 것이 인가 경계다**(seed.md): 한 폴더에 두면 두 화면의
    엔드포인트가 서로에게 매핑되어 조회 전용 화면의 권한이 등록 저장을 연다.
    `generateApiSeed` 가 이 두 줄을 따라와 각 폴더의 `api/endpoints.ts` 를 읽는다(backend/api.md).
  */
  /*
    공지 둘 — 66. **폴더가 갈려 있는 것이 인가 경계다**(seed.md): 한 폴더에 두면 두 화면의
    엔드포인트가 서로에게 매핑되어 **공지 권한이 요청 처리를 연다**. 요청 처리는 남의
    요청을 판정하는 일이라 그 사고의 대가가 크다.
    `generateApiSeed` 가 이 두 줄을 따라와 각 폴더의 `api/endpoints.ts` 를 읽는다(backend/api.md).
  */
  '/admin/system/notice': lazy(async () => ({
    default: (await import('@/modules/system/features/notice/pages/NoticeMgmtPage')).NoticeMgmtPage,
  })),
  '/admin/system/noti-request': lazy(async () => ({
    default: (await import('@/modules/system/features/notirequest/pages/NotiRequestPage')).NotiRequestPage,
  })),
  '/admin/system/batch/schedule': lazy(async () => ({
    default: (await import('@/modules/system/features/batchschedule/pages/BatchSchedulePage'))
      .BatchSchedulePage,
  })),
  '/admin/system/batch/history': lazy(async () => ({
    default: (await import('@/modules/system/features/batchhistory/pages/BatchHistoryPage')).BatchHistoryPage,
  })),
};

export const SCREENS: Record<string, LazyExoticComponent<ComponentType>> = {
  ...PRODUCT_SCREENS,
  ...SAMPLE_SCREENS,
};

export function screenFor(pathname: string): LazyExoticComponent<ComponentType> | null {
  return SCREENS[pathname] ?? null;
}
