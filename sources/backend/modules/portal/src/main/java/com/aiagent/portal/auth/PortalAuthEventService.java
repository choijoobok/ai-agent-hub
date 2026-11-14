// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.security.*;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 필수 사건은 호출자의 물리 트랜잭션에만 append한다. 원문 입력을 받지 않는다. */
@Service
public class PortalAuthEventService {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final PortalAuthSettings settings;
    private final ThreadLocal<UUID> execution=new ThreadLocal<>();
    public PortalAuthEventService(DataSource source,PortalAuthSettings settings){this.source=source;jdbc=new JdbcTemplate(source);this.settings=settings;}
    public void beginExecution(){if(execution.get()!=null)throw new IllegalStateException("인증 사건 실행을 중첩할 수 없다");execution.set(UUID.randomUUID());}
    public void endExecution(){execution.remove();}
    private UUID executionKey() {
        if(execution.get()!=null)return execution.get();
        var existing=TransactionSynchronizationManager.getResource(this);if(existing instanceof UUID key)return key;
        var key=UUID.randomUUID();TransactionSynchronizationManager.bindResource(this,key);
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){TransactionSynchronizationManager.unbindResourceIfPossible(PortalAuthEventService.this);}
        });return key;
    }
    public void record(String type,PrincipalRef principal,String result,Instant at) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Portal 보안 사건은 같은 원장 트랜잭션이 필요하다");
        if(!Set.of("LOGIN_SUCCESS","LOGIN_FAILURE","LOGOUT","PASSWORD_CHANGED","SESSION_REJECTED").contains(type)||!result.matches("[A-Z_]{1,60}")||principal!=null&&principal.domain()!=AuthDomain.PORTAL)throw new IllegalArgumentException("Portal 보안 사건의 안전한 분류가 필요하다");
        Long id=jdbc.queryForObject("UPDATE SYS_SEQUENCE SET NEXT_VAL=NEXT_VAL+1 WHERE SEQ_NAME='AI_PORTAL_AUTH_EVENT' RETURNING NEXT_VAL-1",Long.class);
        String trace=TraceId.current();if(trace==null||!trace.matches("[A-Za-z0-9_-]{1,100}"))trace=UUID.randomUUID().toString();
        String eventKey=UUID.nameUUIDFromBytes((executionKey()+":"+type+":"+(principal==null?"":principal.subjectId())+":"+result).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        jdbc.update("INSERT INTO AI_PORTAL_AUTH_EVENT(AUTH_EVENT_ID,EVENT_KEY,EVENT_TYPE,PORTAL_USER_ID,AUTH_MODE,RESULT_CODE,REQUEST_TRACE_ID,OCCURRED_DTM,RECORDED_DTM) VALUES (?,?,?,?,?,?,?,?,clock_timestamp()) ON CONFLICT(EVENT_KEY) DO NOTHING",
            id,eventKey,type,principal==null?null:Long.valueOf(principal.subjectId()),settings.mode(),result,trace,Timestamp.from(at));
    }
    public Instant now(){return jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();}
}
