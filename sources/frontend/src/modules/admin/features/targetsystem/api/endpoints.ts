// @feat FEAT-001
export const TARGET_ENDPOINTS = {
  getList: '/admin/service/targetSystem/getList',
  getOne: '/admin/service/targetSystem/getOne',
  getConnections: '/admin/service/targetSystem/getConnections',
  getUsedApis: '/admin/service/targetSystem/getUsedApis',
  save: '/admin/service/targetSystem/save',
  getResult: '/admin/service/targetSystem/getCommandResult',
  testConnection: '/admin/service/targetSystem/testConnection',
  previewCleanup: '/admin/service/targetSystem/getConfigurationCleanupPreview',
  cleanup: '/admin/service/targetSystem/deleteConfigurationMismatches',
  alerts: '/admin/service/integration/getConfigurationAlerts',
} as const;
