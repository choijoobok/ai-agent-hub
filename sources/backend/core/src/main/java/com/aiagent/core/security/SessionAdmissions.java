// @feat FEAT-301
package com.aiagent.core.security;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aiagent.core.security.SessionPersistencePort.Identity;

/** 정확 진입부/자격 검증 생산자가 발급하는 요청 수명 증거. HTTP 입력 모델이 아니다. */
public final class SessionAdmissions {
    private SessionAdmissions() {}
    public abstract static class Admission {
        final RequestPersistenceState owner;
        final Instant expiresAt;
        private final Object attempt;
        private final AtomicBoolean consumed = new AtomicBoolean();
        Admission(RequestPersistenceState owner, Instant expiresAt) {
            this.owner = Objects.requireNonNull(owner); this.expiresAt = Objects.requireNonNull(expiresAt);
            this.attempt=Objects.requireNonNull(owner.attemptIdentity(),"검증 증거는 보호 트랜잭션 시도 안에서 발급해야 한다");
        }
        public final boolean consume(RequestPersistenceState request, Instant now) {
            return owner == request && request.attemptIdentity()==attempt && request.canFlush() && now.isBefore(expiresAt) && consumed.compareAndSet(false, true);
        }
        @Override public final String toString() { return getClass().getSimpleName() + "[보호됨]"; }
    }
    public static final class AnonymousSessionCreation extends Admission {
        private AnonymousSessionCreation(RequestPersistenceState owner, Instant expiresAt) { super(owner, expiresAt); }
        public Instant sessionExpiresAt() { return expiresAt; }
    }
    public static final class CredentialAdmission extends Admission {
        private final SessionRef entry;
        private final Identity identity;
        private CredentialAdmission(RequestPersistenceState owner, SessionRef entry, Identity identity, Instant expiresAt) {
            super(owner, expiresAt); this.entry = entry; this.identity = identity;
        }
        public SessionRef entry() { return entry; }
        public Identity identity() { return identity; }
    }
    public static final class RotationAdmission extends Admission {
        private final SessionRef expected;
        private final Identity identity;
        private RotationAdmission(RequestPersistenceState owner, SessionRef expected, Identity identity, Instant expiresAt) {
            super(owner, expiresAt); this.expected = expected; this.identity = identity;
        }
        public SessionRef expected() { return expected; }
        public Identity identity() { return identity; }
    }
    public static AnonymousSessionCreation anonymous(RequestPersistenceState request, Instant expiresAt) {
        if (request.capability() != RequestPersistenceState.Capability.PREPARE_CSRF)
            throw new IllegalArgumentException("정확 CSRF 준비 진입부만 익명 세션을 만들 수 있다");
        return new AnonymousSessionCreation(request, expiresAt);
    }
    /** bootstrap이 등록해 자격 검증 포트에만 주입한다. HTTP 카탈로그도 동일 객체를 결합한다. */
    public static Producer producer(AuthDomain domain, String id) { return new Producer(domain,id); }
    public static final class Producer {
        private final AuthDomain domain;
        private final String id;
        private Producer(AuthDomain domain, String id) { this.domain=Objects.requireNonNull(domain);this.id=Objects.requireNonNull(id); }
        public AuthDomain domain() { return domain; }
        public CredentialAdmission login(RequestPersistenceState request, SessionRef entry, Identity identity, Instant expiresAt) {
            require(request, RequestPersistenceState.Capability.LOGIN, RequestPersistenceState.Capability.RECOVERY);
            validate(request,entry,identity);
            return new CredentialAdmission(request,entry,identity,expiresAt);
        }
        public RotationAdmission rotation(RequestPersistenceState request, SessionRef expected, Identity identity, Instant expiresAt) {
            require(request, RequestPersistenceState.Capability.PASSWORD_CHANGE, RequestPersistenceState.Capability.RESET);
            validate(request,Objects.requireNonNull(expected),identity);
            return new RotationAdmission(request,expected,identity,expiresAt);
        }
        private void require(RequestPersistenceState request, RequestPersistenceState.Capability first, RequestPersistenceState.Capability second) {
            if (!request.producedBy(this) || request.domain()!=domain || request.capability()!=first && request.capability()!=second)
                throw new IllegalArgumentException("요청에 결합된 자격 검증 생산자/동작이 아니다");
        }
        @Override public String toString() { return "CredentialProducer["+domain+","+id+"]"; }
    }
    private static void validate(RequestPersistenceState request, SessionRef ref, Identity identity) {
        if (request.domain() != identity.principal().domain() || ref != null && request.domain() != ref.domain())
            throw new IllegalArgumentException("수락 증거의 인증 영역이 다르다");
    }
}
