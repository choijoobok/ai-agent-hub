// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 저장소 독립 v2 값. 인증 비밀·내부 참조는 일반 JSON/로그로 전달하지 않는다. */
public final class SessionV2 {
    private SessionV2(){}
    public enum Provider { JDBC,REDIS }
    public enum Outcome { APPLIED,STALE_REVISION,INVALID_SESSION,UNAVAILABLE,DELIVERABLE,REAUTH_REQUIRED,INDETERMINATE,REVOKED,ALREADY_ABSENT }
    public static final class Failure extends RuntimeException {
        private final Outcome outcome;
        public Failure(Outcome outcome){super("현재 인증 문맥을 확인할 수 없다");this.outcome=Objects.requireNonNull(outcome);}
        public Outcome outcome(){return outcome;}
    }
    public record Ref(@JsonIgnore AuthDomain domain,@JsonIgnore long grantId,@JsonIgnore long generation,@JsonIgnore UUID epoch) implements Serializable {
        public Ref {Objects.requireNonNull(domain);Objects.requireNonNull(epoch);if(grantId<1||generation<1)throw new IllegalArgumentException("세션 참조의 ID/세대가 올바르지 않다");}
        @Override public String toString(){return "SessionRef[보호됨]";}
    }
    public static final class Secret implements Serializable {
        private static final long serialVersionUID=1L;
        private final String value;
        public Secret(String value){this.value=Objects.requireNonNull(value);if(!value.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("인증 비밀 형식이 올바르지 않다");}
        public boolean matches(String supplied){return supplied!=null&&MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));}
        public void writeTo(DataOutput output)throws IOException{output.writeUTF(value);}
        @Override public boolean equals(Object other){return other instanceof Secret secret&&matches(secret.value);}
        @Override public int hashCode(){return 0;}
        @Override public String toString(){return "SessionSecret[보호됨]";}
    }
    public record Payload(Ref ref,Identity identity,Secret csrf,String displayName,String locale,Set<String> authoritySnapshot) {
        public Payload {
            Objects.requireNonNull(ref);Objects.requireNonNull(csrf);authoritySnapshot=Set.copyOf(authoritySnapshot);
            if(identity!=null&&identity.principal().domain()!=ref.domain())throw new IllegalArgumentException("세션 payload의 영역이 다르다");
            if(identity!=null&&(!Objects.equals(identity.displayName(),displayName)||!Objects.equals(identity.locale(),locale)))throw new IllegalArgumentException("세션 표시 스냅샷이 일치하지 않는다");
        }
        @Override public String toString(){return "SessionPayload[보호됨]";}
    }
    public record Stored(Payload payload,long revision,@JsonIgnore String version) {
        public Stored {Objects.requireNonNull(payload);if(revision<1)throw new IllegalArgumentException("저장 세대가 올바르지 않다");}
        @Override public String toString(){return "StoredSession[보호됨]";}
    }
    public record Snapshot(Ref ref,Identity identity,Instant createdAt,Instant lastAccessAt,Instant expiresAt,@JsonIgnore String managementRef) {
        @Override public String toString(){return "SessionSnapshot[보호됨]";}
    }
    @FunctionalInterface public interface CookieSink {void write(AuthDomain domain,String cookie,String csrf);}
    public static final class CookieIssue {
        private final SessionExchange owner;
        private final String cookie;
        private final Secret csrf;
        public CookieIssue(SessionExchange owner,String cookie,Secret csrf){
            this(owner,cookie,Objects.requireNonNull(csrf),false);
        }
        private CookieIssue(SessionExchange owner,String cookie,Secret csrf,boolean expiration){
            this.owner=Objects.requireNonNull(owner);this.cookie=Objects.requireNonNull(cookie);this.csrf=csrf;
            if(!expiration&&!cookie.matches("[A-Za-z0-9_-]{43}") || expiration&&owner.capability()!=com.aiagent.core.security.RequestPersistenceState.Capability.LOGOUT)
                throw new IllegalArgumentException("외부 인증 쿠키 형식/만료 동작이 올바르지 않다");
        }
        public static CookieIssue expiration(SessionExchange owner){return new CookieIssue(owner,"",null,true);}
        public void emit(SessionExchange request,CookieSink sink){if(owner!=request)throw new IllegalStateException("다른 요청의 발급 증거다");owner.claimDelivery();sink.write(owner.domain(),cookie,csrf==null?"":csrf.value);}
        @Override public String toString(){return "CookieIssue[보호됨]";}
    }
}
