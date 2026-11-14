--liquibase formatted sql
-- @feat FEAT-301
--changeset codex:portal-002-authentication-messages
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.portal.authMode','BACK','현재 인증 모드에서 사용할 수 없습니다');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.authMode','ko','현재 인증 모드에서 사용할 수 없습니다');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.authMode','en','This action is unavailable in the configured authentication mode.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.portal.passwordPolicy','BACK','비밀번호 정책을 확인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.passwordPolicy','ko','비밀번호 정책을 확인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.passwordPolicy','en','Check the password policy.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.portal.authEvidenceUsed','BACK','이미 사용한 인증 요청입니다. 새로 로그인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.authEvidenceUsed','ko','이미 사용한 인증 요청입니다. 새로 로그인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.portal.authEvidenceUsed','en','This authentication request has already been used. Sign in again.');
INSERT INTO SYS_SEQUENCE(SEQ_NAME,NEXT_VAL) VALUES ('AI_PORTAL_AUTH_EVENT',1);
--rollback DELETE FROM SYS_SEQUENCE WHERE SEQ_NAME='AI_PORTAL_AUTH_EVENT';
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY='err.portal.authEvidenceUsed';
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY='err.portal.authEvidenceUsed';
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY='err.portal.passwordPolicy';
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY='err.portal.passwordPolicy';
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY='err.portal.authMode';
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY='err.portal.authMode';
