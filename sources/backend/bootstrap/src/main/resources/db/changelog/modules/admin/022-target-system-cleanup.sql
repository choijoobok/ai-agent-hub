--liquibase formatted sql
-- @feat FEAT-001
--changeset codex:admin-022-target-system-cleanup
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/targetSystem/getUsedApis','사용 API 연결 현황 조회','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_TARGET_SYSTEM','/api/admin/service/targetSystem/getUsedApis');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/targetSystem/getConfigurationCleanupPreview','설정 누락 시스템·연결 정리 미리보기','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_TARGET_SYSTEM','/api/admin/service/targetSystem/getConfigurationCleanupPreview');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/targetSystem/deleteConfigurationMismatches','확인한 설정 누락 항목 일괄 정리','','SAVE');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_TARGET_SYSTEM','/api/admin/service/targetSystem/deleteConfigurationMismatches');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/targetSystem/getCommandResult','대상 시스템 관리 명령 결과 확인','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_TARGET_SYSTEM','/api/admin/service/targetSystem/getCommandResult');
--rollback DELETE FROM SYS_MENU_API WHERE API_URL='/api/admin/service/targetSystem/getUsedApis';
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/targetSystem/getUsedApis';
--rollback DELETE FROM SYS_MENU_API WHERE API_URL='/api/admin/service/targetSystem/getConfigurationCleanupPreview';
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/targetSystem/getConfigurationCleanupPreview';
--rollback DELETE FROM SYS_MENU_API WHERE API_URL='/api/admin/service/targetSystem/deleteConfigurationMismatches';
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/targetSystem/deleteConfigurationMismatches';
--rollback DELETE FROM SYS_MENU_API WHERE API_URL='/api/admin/service/targetSystem/getCommandResult';
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/targetSystem/getCommandResult';
