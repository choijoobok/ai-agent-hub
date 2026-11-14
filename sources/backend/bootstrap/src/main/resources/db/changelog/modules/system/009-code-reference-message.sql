--liquibase formatted sql
-- @feat FEAT-504
--changeset codex:system-009-code-reference-message
INSERT INTO SYS_MESSAGE_KEY(MSG_KEY,USE_SCOPE,MSG_DESC) VALUES
('codeCombo.parent','FRONT','참조 콤보 선행 조건 필요'),
('codeCombo.loading','FRONT','참조 콤보 조회 중'),
('codeCombo.empty','FRONT','참조 콤보 빈 성공'),
('codeCombo.retry','FRONT','참조 콤보 조회 실패와 재조회'),
('codeCombo.unsupported','FRONT','현재 조건에서 선택 불가한 기존 값');
INSERT INTO SYS_MESSAGE(MSG_KEY,LANG_CD,MSG_VAL) VALUES
('codeCombo.parent','ko','후보를 조회하려면 먼저 상위 조건을 선택하세요.'),('codeCombo.parent','en','Select the parent condition first.'),
('codeCombo.loading','ko','후보를 불러오는 중입니다.'),('codeCombo.loading','en','Loading options.'),
('codeCombo.empty','ko','조건에 맞는 후보가 없습니다.'),('codeCombo.empty','en','No matching options.'),
('codeCombo.retry','ko','후보 조회 실패 — 다시 조회'),('codeCombo.retry','en','Could not load options — retry'),
('codeCombo.unsupported','ko','현재 조건에서 선택할 수 없는 값'),('codeCombo.unsupported','en','Unavailable for the current condition');
UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';
--rollback DELETE FROM SYS_MESSAGE WHERE MSG_KEY IN ('codeCombo.parent','codeCombo.loading','codeCombo.empty','codeCombo.retry','codeCombo.unsupported');
--rollback DELETE FROM SYS_MESSAGE_KEY WHERE MSG_KEY IN ('codeCombo.parent','codeCombo.loading','codeCombo.empty','codeCombo.retry','codeCombo.unsupported');
--rollback UPDATE SYS_CACHE_VERSION SET VERSION=VERSION+1,MOD_DTM=CURRENT_TIMESTAMP WHERE CACHE_NAME='message';
