// @feat FEAT-501 FEAT-509
package com.aiagent.domain.authz.service;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ADMIN_AUTHORITY_V1. 관리자 명령과 외부 사건 수신자 판정이 공유하는 트랜잭션 잠금. */
@Component
public class AdminAuthorityProtection {
    private static final Object KEY = new Object();
    private record Held(boolean exclusive) {}
    private final JdbcTemplate jdbc;
    private final EntityManager entities;

    public AdminAuthorityProtection(DataSource source, EntityManager entities) {
        this.jdbc = new JdbcTemplate(source);
        this.entities = entities;
    }

    /** 업무 행을 읽거나 바꾸기 전에 호출한다. 잠금은 최외곽 commit/rollback까지 유지된다. */
    public boolean acquire(boolean exclusive) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("관리자 보호는 쓰기 가능한 같은 REQUIRED 트랜잭션이어야 한다");
        Held held = (Held) TransactionSynchronizationManager.getResource(KEY);
        if (held != null) {
            if (exclusive && !held.exclusive()) throw new IllegalStateException("공유 보호를 배타 보호로 승격할 수 없다");
            return false;
        }
        jdbc.execute("SELECT " + (exclusive ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared") + "(1095321685,1)");
        Held acquired = new Held(exclusive);
        TransactionSynchronizationManager.bindResource(KEY, acquired);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
            @Override public void resume() { TransactionSynchronizationManager.bindResource(KEY, acquired); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
        });
        // 잠금 대기 전에 읽은 관리 엔티티를 현재 자격으로 재사용하지 않는다.
        entities.clear();
        return true;
    }

    public static Object currentToken() { return TransactionSynchronizationManager.getResource(KEY); }
}
