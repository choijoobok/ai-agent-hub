// @feat FEAT-508
package com.aiagent.domain.batch.run;

import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 같은 행을 먼저 읽었어도 DB 잠금 뒤 최신 값을 다시 받아 결과/최초 중단 시각을 보존한다. */
@Component
public class BatchRowLock {
    private final EntityManager entities;

    public BatchRowLock(EntityManager entities) { this.entities = entities; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SysBatchHistory> history(Long id) {
        return current(SysBatchHistory.class, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SysScheduleBatch> schedule(String id) {
        return current(SysScheduleBatch.class, id);
    }

    private <T> Optional<T> current(Class<T> type, Object id) {
        T entity = entities.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(entity);
    }
}
