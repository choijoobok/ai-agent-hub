// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aiagent.core.security.RequestPersistenceState.Capability;

/** 소유 자격 검증 생산자에만 결합하는 v2 단일 DB시도 증거. */
public final class SessionAdmissionsV2 {
    private SessionAdmissionsV2(){}
    public static Producer producer(AuthDomain domain,String name){return new Producer(domain,name);}
    public enum Kind { ANONYMOUS,LOGIN,ROTATION }
    public static final class Proof {
        private final SessionExchange owner;
        private final Object attempt;
        private final Instant expires;
        private final Kind kind;
        private final SessionV2.Ref entry;
        private final Identity identity;
        private final AtomicBoolean consumed=new AtomicBoolean();
        private Proof(SessionExchange owner,Instant expires,Kind kind,SessionV2.Ref entry,Identity identity){
            this.owner=owner;this.attempt=Objects.requireNonNull(owner.attempt(),"현재 DB시도 안에서만 증거를 발급한다");
            this.expires=Objects.requireNonNull(expires);this.kind=kind;this.entry=entry;this.identity=identity;
        }
        public boolean consume(SessionExchange request,Instant now){
            return owner==request && request.state()==SessionExchange.State.OPEN && attempt==request.attempt()
                    && now.isBefore(expires) && consumed.compareAndSet(false,true);
        }
        public Kind kind(){return kind;}
        public SessionV2.Ref entry(){return entry;}
        public Identity identity(){return identity;}
        @Override public String toString(){return "SessionAdmission[보호됨]";}
    }
    public static Proof anonymous(SessionExchange request,Instant expires){
        if(request.capability()!=Capability.PREPARE_CSRF)throw new IllegalArgumentException("정확 CSRF 준비 요청이 아니다");
        return new Proof(request,expires,Kind.ANONYMOUS,null,null);
    }
    /** 소유자가 자격 변경을 검증한 같은 DB 시도에만 결합하는 회수 증거. */
    public static final class CredentialChange {
        private final Proof proof;
        private final java.util.List<com.aiagent.core.security.PrincipalRef> principals;
        private CredentialChange(Proof proof, java.util.List<com.aiagent.core.security.PrincipalRef> principals) {
            this.proof = proof; this.principals = java.util.List.copyOf(principals);
        }
        public boolean consume(SessionExchange request, Instant now) { return proof.consume(request, now); }
        public java.util.List<com.aiagent.core.security.PrincipalRef> principals() { return principals; }
    }
    public static final class Producer {
        private final AuthDomain domain;
        private final String name;
        private Producer(AuthDomain domain,String name){this.domain=Objects.requireNonNull(domain);this.name=Objects.requireNonNull(name);if(name.isBlank())throw new IllegalArgumentException("검증 생산자 이름이 필요하다");}
        public AuthDomain domain(){return domain;}
        private void validate(SessionExchange request,SessionV2.Ref entry,Identity identity,Capability first,Capability second){
            if(!request.producedBy(this) || request.domain()!=domain || request.capability()!=first&&request.capability()!=second
                    || identity.principal().domain()!=domain || entry!=null&&entry.domain()!=domain)
                throw new IllegalArgumentException("검증 생산자·동작·영역이 일치하지 않는다");
        }
        public Proof login(SessionExchange request,SessionV2.Ref entry,Identity identity,Instant expires){
            validate(request,entry,Objects.requireNonNull(identity),Capability.LOGIN,Capability.RECOVERY);return new Proof(request,expires,Kind.LOGIN,entry,identity);
        }
        public Proof rotation(SessionExchange request,SessionV2.Ref expected,Identity identity,Instant expires){
            validate(request,Objects.requireNonNull(expected),Objects.requireNonNull(identity),Capability.PASSWORD_CHANGE,Capability.RESET);return new Proof(request,expires,Kind.ROTATION,expected,identity);
        }
        public CredentialChange credentialsChanged(SessionExchange request,
                java.util.List<com.aiagent.core.security.PrincipalRef> principals, Instant expires) {
            if (!request.producedBy(this) || request.domain() != domain
                    || request.capability() != Capability.RECOVERY && request.capability() != Capability.RESET
                    || principals.isEmpty() || principals.stream().anyMatch(p -> p.domain() != domain))
                throw new IllegalArgumentException("자격 변경 생산자·동작·영역이 일치하지 않는다");
            return new CredentialChange(new Proof(request, expires, Kind.ROTATION, null, null), principals);
        }
        @Override public String toString(){return "CredentialProducer["+domain+","+name+"]";}
    }
}
