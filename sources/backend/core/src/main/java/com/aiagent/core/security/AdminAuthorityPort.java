// @feat FEAT-301
package com.aiagent.core.security;

import java.util.function.Supplier;

/** 구현은 system 소유. callback은 같은 REQUIRED 트랜잭션·현재 권한 보호 안에서만 실행한다. */
public interface AdminAuthorityPort {
    record CurrentAdminContext(PrincipalRef principal, SessionRef sessionRef, String operationId,
                               String requiredAction, String requestTraceId) {}
    <T> T withShared(CurrentAdminContext context, Supplier<T> callback);
    <T> T withExclusive(CurrentAdminContext context, Supplier<T> callback);
}
