// @feat FEAT-003
export const CATALOG_ENDPOINTS = {
  getList: '/admin/service/catalog/getList',
  getOne: '/admin/service/catalog/getOne',
  saveCreate: '/admin/service/catalog/saveCreate',
  saveChange: '/admin/service/catalog/saveChange',
  getCommandResult: '/admin/service/catalog/getCommandResult',
  getReferenceCandidates: '/admin/service/catalog/getReferenceCandidates',
  getReferences: '/admin/service/catalog/getReferences',
  getResolvedObject: '/admin/service/catalog/getResolvedObject',
  getStructure: '/admin/service/catalog/getStructure',
} as const;
