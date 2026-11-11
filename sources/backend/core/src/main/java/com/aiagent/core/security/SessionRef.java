// @feat FEAT-301
package com.aiagent.core.security;

import java.util.Objects;

/** 서버 내부 참조. 일반 DTO/로그에 직렬화하지 않는다. */
public final class SessionRef {
    private final AuthDomain domain;
    private final String primaryId;
    private final String sessionId;
    public SessionRef(AuthDomain domain, String primaryId, String sessionId) {
        this.domain = Objects.requireNonNull(domain);
        this.primaryId = Objects.requireNonNull(primaryId);
        this.sessionId = Objects.requireNonNull(sessionId);
    }
    public AuthDomain domain() { return domain; }
    public String primaryId() { return primaryId; }
    public String sessionId() { return sessionId; }
    @Override public boolean equals(Object other) {
        return other instanceof SessionRef r && domain == r.domain && primaryId.equals(r.primaryId) && sessionId.equals(r.sessionId);
    }
    @Override public int hashCode() { return Objects.hash(domain, primaryId, sessionId); }
    @Override public String toString() { return "SessionRef[" + domain + ",보호됨]"; }
}
