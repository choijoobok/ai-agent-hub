// @feat FEAT-501
package com.aiagent.system.api.auth;

import com.aiagent.api.auth.v2.SessionRuntime;
import com.aiagent.api.auth.v2.SessionV2Filter;
import com.aiagent.domain.authz.EffectiveAuthority;
import jakarta.servlet.http.HttpServletRequest;

/** 동기 보호 요청의 값만 공유한다. 스레드가 반환되기 전에 반드시 비운다. */
public final class AdminRequestContext {
    private static final ThreadLocal<java.util.List<Runnable>> AFTER = ThreadLocal.withInitial(java.util.ArrayList::new);
    private static final ThreadLocal<HttpServletRequest> REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<EffectiveAuthority> AUTHORITY = new ThreadLocal<>();
    private AdminRequestContext() {}
    public static void bind(HttpServletRequest request) { REQUEST.set(request); }
    public static void clear() { AFTER.remove(); REQUEST.remove(); AUTHORITY.remove(); }
    public static HttpServletRequest request() {
        var request = REQUEST.get();
        if (request == null) throw new IllegalStateException("관리자 요청 문맥이 없다");
        return request;
    }
    public static SessionRuntime.Scope scope(HttpServletRequest request) {
        if (!(request.getAttribute(SessionV2Filter.SCOPE) instanceof SessionRuntime.Scope scope))
            throw new IllegalStateException("공통 인증 보호가 적용되지 않았다");
        return scope;
    }
    public static SessionRuntime.Scope scope() { return scope(request()); }
    public static EffectiveAuthority authority() {
        var authority = AUTHORITY.get();
        if (authority == null) throw new IllegalStateException("현재 관리자 권한을 확인하지 않았다");
        return authority;
    }
    public static void afterCommit(Runnable work) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { AFTER.get().add(work); }
                });
    }
    public static void runAfterCommit() {
        var works = java.util.List.copyOf(AFTER.get()); AFTER.remove();
        for (Runnable work : works) work.run();
    }
    public static void withAuthority(EffectiveAuthority authority, Runnable command) {
        var before = AUTHORITY.get();
        try { if (authority == null) AUTHORITY.remove(); else AUTHORITY.set(authority); command.run(); }
        finally { if (before == null) AUTHORITY.remove(); else AUTHORITY.set(before); }
    }
}
