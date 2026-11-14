// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.v2.SessionAuthority;
import com.aiagent.domain.authz.service.AdminAuthorityProtection;
import java.util.function.UnaryOperator;

/** 원 명령 잠금을 현재 권한 보호 앞에 배치하고 같은 요청/물리 트랜잭션에만 증거를 발급한다. */
public final class ManagedAdmission implements UnaryOperator<SessionAuthority> {
    public record Prepared(String operationId,String key,ManagedCommand command,String ownerFeature) {}
    private record Proof(Prepared input,String actor,Object transaction,Object authority,Thread thread) {}
    private final ThreadLocal<Prepared> request=new ThreadLocal<>();
    private final ThreadLocal<Proof> current=new ThreadLocal<>();
    private final ManagedLocks locks;
    private final ManagedRegistry registry;
    public ManagedAdmission(ManagedLocks locks,ManagedRegistry registry) { this.locks=locks;this.registry=registry; }
    public void bind(Prepared input) { if(request.get()!=null)throw new IllegalStateException("관리 요청을 중첩할 수 없다");request.set(input); }
    public void clear() { request.remove(); current.remove(); }
    public SessionAuthority apply(SessionAuthority delegate) {
        return (context,exclusive,command)->{
            var input=request.get();
            boolean managed=context.operationId().equals("API-082")||registry.operations().stream().anyMatch(op->op.id().equals(context.operationId()))||registry.resultRoutes().stream().anyMatch(route->route.id().equals(context.operationId()));
            // 인증 런타임의 불가 세션 회수는 원 업무 callback을 실행하지 않는 별도 보호다.
            if(!managed||context.principal()==null) { delegate.protect(context,exclusive,command); return; }
            if(input==null||!context.operationId().equals(input.operationId())||context.domain()!=AuthDomain.ADMIN)
                throw ManagedErrorCode.FORBIDDEN.failure();
            locks.command(input.key());
            delegate.protect(context,exclusive,()->{
                Object protection=AdminAuthorityProtection.currentToken();
                if(protection==null||current.get()!=null)throw ManagedErrorCode.FORBIDDEN.failure();
                current.set(new Proof(input,context.principal().subjectId(),locks.transaction(),protection,Thread.currentThread()));
                try { command.run(); } finally { current.remove(); }
            });
        };
    }
    private Proof proof(String operationId,String key) {
        var proof=current.get();
        if(proof==null||!proof.input().operationId().equals(operationId)||!proof.input().key().equals(key)
                ||proof.thread()!=Thread.currentThread()||proof.transaction()!=locks.transaction()
                ||proof.authority()!=AdminAuthorityProtection.currentToken())throw ManagedErrorCode.FORBIDDEN.failure();
        return proof;
    }
    public String saveActor(String operationId,ManagedCommand input) {
        var proof=proof(operationId,input.commandKey());
        if(!input.equals(proof.input().command()))throw ManagedErrorCode.FORBIDDEN.failure();
        return proof.actor();
    }
    public String queryActor(String key,String feature) {
        return queryActor("API-082",key,feature);
    }
    public String queryActor(String operationId,String key,String feature) {
        var proof=proof(operationId,key);
        if(!java.util.Objects.equals(feature,proof.input().ownerFeature()))throw ManagedErrorCode.FORBIDDEN.failure();
        return proof.actor();
    }
}
