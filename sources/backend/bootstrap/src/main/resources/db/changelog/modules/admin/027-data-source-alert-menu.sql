--liquibase formatted sql
-- @feat FEAT-002
--changeset codex:admin-027-data-source-alert-menu
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_DATA_SOURCE','/api/admin/service/integration/getConfigurationAlerts');
--rollback DELETE FROM SYS_MENU_API WHERE MENU_CD='HUB_DATA_SOURCE' AND API_URL='/api/admin/service/integration/getConfigurationAlerts';
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('data_source.unspecified','FRONT','미지정');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('data_source.unspecified','ko','미지정');
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY='data_source.unspecified';
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY='data_source.unspecified';
