// @feat FEAT-005
export const PORTAL_USER_ENDPOINTS = {
  getList: '/admin/service/portalUser/getList',
  getOne: '/admin/service/portalUser/getOne',
  getIdentityTargets: '/admin/service/portalUser/getIdentityTargets',
  save: '/admin/service/portalUser/save',
  resetCredential: '/admin/service/portalUser/resetCredential',
  unlockCredential: '/admin/service/portalUser/unlockCredential',
  getResult: '/admin/service/managementCommand/getResult',
} as const;
