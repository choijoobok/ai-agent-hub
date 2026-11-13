// @feat FEAT-040
package com.aiagent.admin.managed;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** SYS_SEQUENCE의 서버 번호 예약만 독립 확정한다. 업무/HIST 저장을 이 트랜잭션에 넣지 않는다. */
public final class ManagedIds {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate reserve;
    public ManagedIds(DataSource source,PlatformTransactionManager manager) {
        jdbc=new JdbcTemplate(source); reserve=new TransactionTemplate(manager);
        reserve.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public long next(String sequence) { return reserve(sequence,1); }
    public long reserve(String sequence,int count) {
        if(!sequence.matches("AI_ADMIN_[A-Z0-9_]+")||count<1||count>10_000)throw new IllegalArgumentException("번호 예약 범위가 올바르지 않다");
        return reserve.execute(status->{
            long value=jdbc.queryForObject("SELECT NEXT_VAL FROM SYS_SEQUENCE WHERE SEQ_NAME=? FOR UPDATE",Long.class,sequence);
            if(value<1)throw new IllegalStateException("양의 서버 번호가 필요하다");
            jdbc.update("UPDATE SYS_SEQUENCE SET NEXT_VAL=? WHERE SEQ_NAME=?",Math.addExact(value,count),sequence);
            return value;
        });
    }
}
