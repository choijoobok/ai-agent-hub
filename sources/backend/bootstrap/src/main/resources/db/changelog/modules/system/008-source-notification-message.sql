--liquibase formatted sql
-- @feat FEAT-509
--changeset codex:system-008-source-notification-message
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('noti.type.PORTAL_CHANGE','FRONT','외부 퇴직·조직 이동 사건 알림 — CR-034.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('noti.portal.retired','BACK','외부 퇴직 사건의 관리자 알림 제목.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('noti.portal.orgMoved','BACK','외부 조직 이동 사건의 관리자 알림 제목.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('noti.portal.content','BACK','개인정보를 담지 않는 외부 사건 알림 본문.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('noti.type.PORTAL_CHANGE','ko','Portal 이용 상태 변경'),('noti.type.PORTAL_CHANGE','en','Portal access change');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('noti.portal.retired','ko','퇴직에 따른 Portal 이용 상태 변경'),('noti.portal.retired','en','Portal access changed after retirement');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('noti.portal.orgMoved','ko','조직 이동에 따른 Portal 이용 상태 변경'),('noti.portal.orgMoved','en','Portal access changed after an organizational move');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('noti.portal.content','ko','외부 인사 변경이 반영되었습니다. 상세 화면에서 현재 상태를 확인해 주세요.'),('noti.portal.content','en','An external personnel change was applied. Open the details to review the current state.');
UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY IN ('noti.type.PORTAL_CHANGE','noti.portal.retired','noti.portal.orgMoved','noti.portal.content');
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY IN ('noti.type.PORTAL_CHANGE','noti.portal.retired','noti.portal.orgMoved','noti.portal.content');
--rollback UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';
