// @feat FEAT-006
export const GROUP_ENDPOINTS = {
  getList: '/admin/service/userGroup/getList',
  getOne: '/admin/service/userGroup/getOne',
  getMembers: '/admin/service/userGroup/getMembers',
  getAssets: '/admin/service/userGroup/getAssets',
  save: '/admin/service/userGroup/save',
  remove: '/admin/service/userGroup/remove',
  getResult: '/admin/service/managementCommand/getResult',
} as const;
