// @feat FEAT-301
package com.aiagent.core.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** admin-auth-boundary/v1. 서블릿·Security·JDBC 또는 임의 속성 Map을 노출하지 않는다. */
public interface SessionPersistencePort {
    enum AuthLevel { ANONYMOUS, PASSWORD_CHANGE_REQUIRED, AUTHENTICATED }
    enum Outcome { CURRENT, ABSENT, EXPIRED, UNAVAILABLE, CREATED, PREPARED, ROTATED, APPLIED, DELETED, STALE, REJECTED }
    record Identity(PrincipalRef principal, AuthLevel authLevel, String authMode, Long credentialVersion,
                    Instant authenticatedAt, String displayName, String locale) implements Serializable {
        private static final long serialVersionUID = 1L;
        public Identity {
            Objects.requireNonNull(principal); Objects.requireNonNull(authLevel); Objects.requireNonNull(authenticatedAt);
            if (authLevel == AuthLevel.ANONYMOUS || !Set.of("LOCAL", "EXTERNAL").contains(authMode)
                    || (principal.domain() == AuthDomain.ADMIN || "EXTERNAL".equals(authMode)) && credentialVersion != null
                    || principal.domain() == AuthDomain.PORTAL && "LOCAL".equals(authMode) && (credentialVersion == null || credentialVersion < 1))
                throw new IllegalArgumentException("인증 주체의 수준 또는 자격세대가 올바르지 않다");
        }
    }
    record SessionDelta(Instant lastAccessedAt, String displayName, String locale,
                        Set<String> authoritySnapshot, boolean dirtySeen) {
        public SessionDelta { Objects.requireNonNull(lastAccessedAt); authoritySnapshot = Set.copyOf(authoritySnapshot); }
    }
    record Result(Outcome outcome, SessionRef ref, Identity identity, Instant expiresAt, CsrfIssue csrf) {
        public static Result of(Outcome outcome) { return new Result(outcome, null, null, null, null); }
        @Override public String toString() { return "SessionResult[" + outcome + "]"; }
    }
    @FunctionalInterface interface CookieWriter { void write(AuthDomain domain, String sessionId, String csrf); }
    /** 비밀을 일반 getter·record·Serializable로 노출하지 않고 단일 HTTP writer에 전달한다. */
    final class CsrfIssue {
        private final RequestPersistenceState owner;
        private final String sessionId;
        private final String csrf;
        public CsrfIssue(RequestPersistenceState owner, String sessionId, String csrf) {
            this.owner = Objects.requireNonNull(owner); this.sessionId = Objects.requireNonNull(sessionId); this.csrf = Objects.requireNonNull(csrf);
        }
        public void emit(RequestPersistenceState request, CookieWriter writer) {
            if (request != owner) throw new IllegalStateException("다른 요청의 응답 증거다");
            owner.claimDelivery();
            writer.write(owner.domain(), sessionId, csrf);
        }
        @Override public String toString() { return "CsrfIssue[보호됨]"; }
    }
    Result load(AuthDomain domain, String sessionId);
    Result prepareCsrf(RequestPersistenceState request, SessionRef ref);
    Result createAnonymous(RequestPersistenceState request, SessionAdmissions.AnonymousSessionCreation creation);
    Result createAuthorized(RequestPersistenceState request, SessionAdmissions.CredentialAdmission admission, Instant sessionExpiresAt);
    Result rotate(RequestPersistenceState request, SessionRef ref, SessionAdmissions.RotationAdmission admission, Instant sessionExpiresAt);
    Result updateExisting(RequestPersistenceState request, SessionRef ref, SessionDelta delta);
    Result delete(RequestPersistenceState request, SessionRef ref);
    List<SessionRef> findByPrincipal(PrincipalRef principal);
}
