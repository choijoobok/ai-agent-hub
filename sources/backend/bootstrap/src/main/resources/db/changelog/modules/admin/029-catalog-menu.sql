--liquibase formatted sql
-- @feat FEAT-003
--changeset codex:admin-029-catalog-menu
INSERT INTO SYS_MENU(MENU_CD,UP_MENU_CD,MENU_NM,MENU_URL,EXPORT_YN,SRC_CD) VALUES ('HUB_CATALOG','HUB_INTEGRATION','menu.HUB_CATALOG','/admin/hub/catalog','Y','SEED');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getList','카탈로그 getList','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getList');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getOne','카탈로그 getOne','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getOne');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getRevision','카탈로그 getRevision','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getRevision');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/saveCreate','카탈로그 saveCreate','','SAVE');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/saveCreate');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/saveChange','카탈로그 saveChange','','SAVE');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/saveChange');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getReferences','카탈로그 getReferences','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getReferences');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getRelatedJoins','카탈로그 getRelatedJoins','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getRelatedJoins');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getResolvedObject','카탈로그 getResolvedObject','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getResolvedObject');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getStructure','카탈로그 getStructure','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getStructure');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getCommandResult','카탈로그 getCommandResult','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getCommandResult');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getValidation','카탈로그 getValidation','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getValidation');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/catalog/getReferenceCandidates','카탈로그 getReferenceCandidates','','READ');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_CATALOG','/api/admin/service/catalog/getReferenceCandidates');
INSERT INTO SYS_ROLE_MENU_AUTH(ROLE_ID,MENU_CD,ACTION_CD) SELECT ROLE_ID,'HUB_CATALOG',ACTION_CD FROM SYS_ROLE_MENU_AUTH WHERE MENU_CD='USER_MGMT' AND ACTION_CD IN ('READ','SAVE','EXPORT');
--rollback DELETE FROM SYS_ROLE_MENU_AUTH WHERE MENU_CD='HUB_CATALOG';
--rollback DELETE FROM SYS_MENU_API WHERE MENU_CD='HUB_CATALOG';
--rollback DELETE FROM SYS_API WHERE API_URL LIKE '/api/admin/service/catalog/%';
--rollback DELETE FROM SYS_MENU WHERE MENU_CD='HUB_CATALOG';
