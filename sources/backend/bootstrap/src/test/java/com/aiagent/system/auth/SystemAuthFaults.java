// @feat FEAT-501
package com.aiagent.system.auth;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

/** 제품 Redis 연결의 발행 시점만 시험한다. 실제 서버·다른 namespace를 중단하지 않는다. */
@TestConfiguration(proxyBeanMethods=false)
class SystemAuthFaults {
    @Bean com.aiagent.domain.batch.BatchJob system520Probe(){return context->com.aiagent.domain.batch.BatchJobResult.of("시험 완료",null);}
    static volatile boolean holdBatch;
    static final java.util.List<Runnable> BATCH_QUEUE=new java.util.concurrent.CopyOnWriteArrayList<>();
    static final AtomicReference<String> MAIL=new AtomicReference<>();
    @Bean @org.springframework.context.annotation.Primary com.aiagent.support.mail.Mailer system520Mailer() {
        var mailer=mock(com.aiagent.support.mail.Mailer.class);
        when(mailer.send(anyString(),anyString(),anyString())).thenAnswer(call->{
            com.aiagent.api.auth.v2.SessionTransactions.requireOutsideTransaction();MAIL.set(call.getArgument(2));return true;
        });return mailer;
    }
    private record Fault(JdbcTemplate jdbc,Runnable action) {}
    private static final AtomicReference<Fault> NEXT=new AtomicReference<>();
    static void arm(JdbcTemplate jdbc,Runnable action){NEXT.set(new Fault(jdbc,action));}
    static void reset(){NEXT.set(null);}
    @Bean static BeanPostProcessor redisPublicationFault() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(name.equals("batchTaskExecutor") && bean instanceof java.util.concurrent.Executor executor) {
                    var wrapped=(java.util.concurrent.Executor)mock(bean.getClass(),org.mockito.AdditionalAnswers.delegatesTo(bean));
                    doAnswer(call->{Runnable task=call.getArgument(0);if(holdBatch)BATCH_QUEUE.add(task);else executor.execute(task);return null;}).when(wrapped).execute(any(Runnable.class));
                    return wrapped;
                }
                if(!(bean instanceof RedisConnectionFactory factory))return bean;
                var wrapped=(RedisConnectionFactory)mock(bean.getClass(),org.mockito.AdditionalAnswers.delegatesTo(factory));
                doAnswer(call->{
                    var fault=NEXT.get();
                    if(fault!=null && fault.jdbc().queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT WHERE SUBJECT_ID='other' AND STATE_CD='PENDING' AND GENERATION>1",Integer.class)>0 && NEXT.compareAndSet(fault,null))fault.action().run();
                    return factory.getConnection();
                }).when(wrapped).getConnection();
                return wrapped;
            }
        };
    }
}
