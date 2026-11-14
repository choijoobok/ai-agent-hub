--liquibase formatted sql
-- @feat FEAT-305
--changeset codex:portal-004-external-change-message
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.sourceChange','BACK','외부 사건을 처리할 수 없습니다. 원천과 처리 결과를 확인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.sourceChange','ko','외부 사건을 처리할 수 없습니다. 원천과 처리 결과를 확인해 주세요');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.sourceChange','en','The source event could not be processed. Check the source and receipt status.');
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY='err.sourceChange';
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY='err.sourceChange';
