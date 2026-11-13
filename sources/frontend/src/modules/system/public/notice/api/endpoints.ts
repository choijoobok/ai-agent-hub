// @feat FEAT-509
/** 사용자 공지와 팝업은 메뉴가 없는 system 공개 계약이다. */
export const SYSTEM_NOTICE_ENDPOINTS = {
  getList: '/system/common/notice/getList',
  getOne: '/system/common/notice/getOne',
  getPopup: '/system/common/notice/getPopup',
} as const;
