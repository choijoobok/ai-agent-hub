--liquibase formatted sql
--changeset codex:platform-003-batch-lease
--comment: FEAT-508 배치 실행 세대의 정확한 lease 소유 참조. 일반 잠금은 null을 유지한다.
ALTER TABLE SYS_DATA_LOCK ADD COLUMN BATCH_HIST_ID BIGINT;
COMMENT ON COLUMN SYS_DATA_LOCK.BATCH_HIST_ID IS '배치 lease 소유 HIST_ID. 일반 잠금 및 쿨다운만 남은 행은 null';
--rollback ALTER TABLE SYS_DATA_LOCK DROP COLUMN BATCH_HIST_ID;
