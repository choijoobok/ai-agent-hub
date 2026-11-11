// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.v2.SessionExchange;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** v2 기술 조립의 같은 DB REQUIRED와 요청 시도 수명. */
public final class SessionTransactions {
    private final DataSource source;
    private final PlatformTransactionManager manager;
    private final TransactionTemplate required;
    public SessionTransactions(DataSource source,PlatformTransactionManager manager){
        this.source=Objects.requireNonNull(source);this.manager=Objects.requireNonNull(manager);
        DataSource bound=switch(manager){case DataSourceTransactionManager m->m.getDataSource();case JpaTransactionManager m->m.getDataSource();default->null;};
        if(bound!=source)throw new IllegalArgumentException("인증 원장과 소유 업무는 같은 JDBC/JPA 데이터소스를 사용해야 한다");
        required=new TransactionTemplate(manager);
    }
    public boolean boundTo(DataSource source,PlatformTransactionManager manager){return this.source==source&&this.manager==manager;}
    public <T>T required(Supplier<T> command){return required.execute(status->command.get());}
    public <T>T required(SessionExchange request,Supplier<T> command){return required(()->{attach(request);return command.get();});}
    public void attach(SessionExchange request){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("동일 DB 보호 트랜잭션이 필요하다");
        if(!request.beginAttempt(TransactionSynchronizationManager.getResource(source)))return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){request.completeDatabase(status==STATUS_COMMITTED);}
        });
    }
    /** ID 예약만 독립 확정한다. 인증/원장/payload 쓰기는 이 경로에 넣지 않는다. */
    long reserveIdentifier(Supplier<Long> allocator){
        var reserve=new TransactionTemplate(manager);reserve.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Objects.requireNonNull(reserve.execute(status->allocator.get()));
    }
    void requireCurrent(){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("원장 변경은 같은 DB 트랜잭션 안에서만 허용한다");
    }
    public static void requireOutsideTransaction(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("외부 세션 저장은 DB 보호 트랜잭션 밖에서 수행해야 한다");}
}
