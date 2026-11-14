// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 모든 Portal 판정/회수의 공유 경계. 관리자 권한 잠금과 다른 예약 키를 사용한다. */
public final class PortalPolicy {
    public static final ManagedLocks.Key KEY=new ManagedLocks.Key(2,"PORTAL_POLICY","GLOBAL");
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public PortalPolicy(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public Instant now(){return jdbc.queryForObject("SELECT clock_timestamp()",java.sql.Timestamp.class).toInstant();}
    public void protect(boolean exclusive) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.getResource(source)==null)throw new IllegalStateException("Portal 정책 판정은 호출자 트랜잭션에 참여해야 한다");
        if(exclusive&&TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Portal 회수에는 쓰기 트랜잭션이 필요하다");
        String hash=ManagedDigest.hash(KEY.kind()+"/"+KEY.id());
        jdbc.queryForObject(exclusive?"SELECT pg_advisory_xact_lock(?,?)":"SELECT pg_advisory_xact_lock_shared(?,?)",Object.class,1296519733,(int)Long.parseLong(hash.substring(0,8),16));
    }
    public static ManagedLocks.Key user(String id){return new ManagedLocks.Key(2,"PORTAL_USER",id);}
}
