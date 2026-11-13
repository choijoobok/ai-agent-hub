// @feat FEAT-040
package com.aiagent.admin.managed;

import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 예약 영역은 인증의 ADMIN_AUTHORITY_V1과 분리한다. 해시 충돌은 직렬화만 늘린다. */
public final class ManagedLocks {
    public record Key(int phase,String kind,String id) implements Comparable<Key> {
        public Key { if(phase<0||phase>4||kind==null||id==null)throw new IllegalArgumentException("관리 잠금 키가 필요하다"); }
        public int compareTo(Key other) {
            int result=Integer.compare(phase,other.phase); if(result!=0)return result;
            result=kind.compareTo(other.kind); if(result!=0)return result;
            return id.matches("[0-9]+")&&other.id.matches("[0-9]+")?new BigInteger(id).compareTo(new BigInteger(other.id)):id.compareTo(other.id);
        }
    }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public ManagedLocks(DataSource source) { this.source=source; jdbc=new JdbcTemplate(source); }
    public Object transaction() {
        Object holder=TransactionSynchronizationManager.getResource(source);
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly()||holder==null)
            throw new IllegalStateException("같은 쓰기 가능한 관리 트랜잭션이 필요하다");
        return holder;
    }
    public void command(String key) { lock(1296519732,ManagedDigest.hash(ManagedCommand.requireKey(key))); }
    public void ordered(Collection<Key> keys) {
        for(var key:new TreeSet<>(keys)) lock(1296519733,ManagedDigest.hash(key.kind()+"/"+key.id()));
    }
    public void history(long number) { lock(1296519734,ManagedDigest.hash(Long.toString(number))); }
    public void consumer(String consumer) { lock(1296519735,ManagedDigest.hash(consumer)); }
    private void lock(int namespace,String hash) {
        transaction(); jdbc.queryForObject("SELECT pg_advisory_xact_lock(?,?)",Object.class,namespace,(int)Long.parseLong(hash.substring(0,8),16));
    }
}
