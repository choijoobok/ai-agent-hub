--liquibase formatted sql
-- @feat FEAT-005
--changeset codex:admin-010-portal-user-menu
INSERT INTO SYS_MENU(MENU_CD,UP_MENU_CD,MENU_NM,MENU_URL,EXPORT_YN,SRC_CD) VALUES ('HUB_PORTAL_USER','HUB_FOUNDATION','menu.HUB_PORTAL_USER','/admin/hub/portal-user','Y','SEED');
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES
('/api/admin/service/portalUser/getList','Portal 이용자 목록 조회','','READ'),
('/api/admin/service/portalUser/getOne','Portal 이용자 영역 조회','','READ'),
('/api/admin/service/portalUser/getIdentityTargets','Portal 기술 신원 후보 조회','','READ'),
('/api/admin/service/portalUser/save','Portal 이용자 영역 저장','','SAVE'),
('/api/admin/service/portalUser/resetCredential','Portal 임시 비밀번호 단회 발급','','SAVE'),
('/api/admin/service/portalUser/unlockCredential','Portal 계정 잠금 해제','','SAVE');
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) SELECT 'HUB_PORTAL_USER',API_URL FROM SYS_API WHERE API_URL LIKE '/api/admin/service/portalUser/%';
INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('HUB_PORTAL_USER','/api/admin/service/managementCommand/getResult');
INSERT INTO SYS_ROLE_MENU_AUTH(ROLE_ID,MENU_CD,ACTION_CD) VALUES ('ADMIN','HUB_PORTAL_USER','READ'),('ADMIN','HUB_PORTAL_USER','SAVE'),('ADMIN','HUB_PORTAL_USER','EXPORT');
UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='authz';
--rollback DELETE FROM SYS_ROLE_MENU_AUTH WHERE MENU_CD='HUB_PORTAL_USER';
--rollback DELETE FROM SYS_USER_MENU_AUTH WHERE MENU_CD='HUB_PORTAL_USER';
--rollback DELETE FROM SYS_MENU_API WHERE MENU_CD='HUB_PORTAL_USER';
--rollback DELETE FROM SYS_API WHERE API_URL LIKE '/api/admin/service/portalUser/%';
--rollback DELETE FROM SYS_MENU WHERE MENU_CD='HUB_PORTAL_USER';
