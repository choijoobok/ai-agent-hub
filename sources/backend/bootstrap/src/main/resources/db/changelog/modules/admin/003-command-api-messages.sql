--liquibase formatted sql
-- @feat FEAT-040
--changeset codex:admin-003-command-api-messages
INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES ('/api/admin/service/managementCommand/getResult','공통 관리 저장 결과 확인','원 기능의 현재 READ+SAVE 권한과 원 ADMIN 주체로 저장 결과를 확인한다.','READ');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.validation','BACK','관리 저장 입력이 올바르지 않습니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.validation','ko','관리 저장 입력이 올바르지 않습니다.'),('err.managed.validation','en','Invalid management command.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.forbidden','BACK','현재 관리 변경 권한이 없습니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.forbidden','ko','현재 관리 변경 권한이 없습니다.'),('err.managed.forbidden','en','Current management permission is required.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.revisionConflict','BACK','기준 정보가 변경되었습니다. 다시 조회해 주세요.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.revisionConflict','ko','기준 정보가 변경되었습니다. 다시 조회해 주세요.'),('err.managed.revisionConflict','en','The base revision changed. Reload the data.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.referenceConflict','BACK','현재 참조 때문에 변경할 수 없습니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.referenceConflict','ko','현재 참조 때문에 변경할 수 없습니다.'),('err.managed.referenceConflict','en','A current reference prevents this change.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.commandConflict','BACK','다른 저장 의도에 사용한 명령 키입니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.commandConflict','ko','다른 저장 의도에 사용한 명령 키입니다.'),('err.managed.commandConflict','en','The command key belongs to a different request.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.commandExpired','BACK','보존 기간이 종료된 명령입니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.commandExpired','ko','보존 기간이 종료된 명령입니다.'),('err.managed.commandExpired','en','The command retention period has ended.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.historyUnavailable','BACK','정확한 과거 구성을 확인할 수 없습니다.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.historyUnavailable','ko','정확한 과거 구성을 확인할 수 없습니다.'),('err.managed.historyUnavailable','en','The exact historical configuration is unavailable.');
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES ('err.managed.unavailable','BACK','관리 저장 원천을 확인할 수 없습니다. 원 명령으로 결과를 확인해 주세요.');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES ('err.managed.unavailable','ko','관리 저장 원천을 확인할 수 없습니다. 원 명령으로 결과를 확인해 주세요.'),('err.managed.unavailable','en','Management storage is unavailable. Check the original command result.');
UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';

--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY IN ('err.managed.validation','err.managed.forbidden','err.managed.revisionConflict','err.managed.referenceConflict','err.managed.commandConflict','err.managed.commandExpired','err.managed.historyUnavailable','err.managed.unavailable');
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY IN ('err.managed.validation','err.managed.forbidden','err.managed.revisionConflict','err.managed.referenceConflict','err.managed.commandConflict','err.managed.commandExpired','err.managed.historyUnavailable','err.managed.unavailable');
--rollback DELETE FROM SYS_API WHERE API_URL='/api/admin/service/managementCommand/getResult';
--rollback UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';
