// @feat FEAT-002
export const DATA_SOURCE_ENDPOINTS = {
  getList: '/admin/service/dataSource/getList',
  getOne: '/admin/service/dataSource/getOne',
  getConnectionCandidates: '/admin/service/dataSource/getConnectionCandidates',
  getReferences: '/admin/service/dataSource/getReferences',
  save: '/admin/service/dataSource/save',
  getCommandResult: '/admin/service/dataSource/getCommandResult',
  getTimezoneList: '/admin/service/dataSource/getTimezoneList',
  getQueryGuides: '/admin/service/dataSource/getQueryGuides',
  getConfigurationCleanupPreview: '/admin/service/dataSource/getConfigurationCleanupPreview',
  deleteConfigurationMismatches: '/admin/service/dataSource/deleteConfigurationMismatches',
  alerts: '/admin/service/integration/getConfigurationAlerts',
} as const;
