--liquibase formatted sql
-- @feat FEAT-001
--changeset codex:admin-021-target-system-execute
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/targetSystem/testConnection','설정에 정의된 연결 시험 실행','','EXECUTE');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_TARGET_SYSTEM','/api/admin/service/targetSystem/testConnection');
INSERT INTO SYS_ROLE_MENU_AUTH(ROLE_ID,MENU_CD,ACTION_CD) VALUES ('ADMIN','HUB_TARGET_SYSTEM','EXECUTE');
--rollback DELETE FROM SYS_ROLE_MENU_AUTH WHERE MENU_CD='HUB_TARGET_SYSTEM' AND ACTION_CD='EXECUTE';
--rollback DELETE FROM SYS_MENU_API WHERE API_URL='/api/admin/service/targetSystem/testConnection';
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/targetSystem/testConnection';
