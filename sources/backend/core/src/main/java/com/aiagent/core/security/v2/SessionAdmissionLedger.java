// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.aiagent.core.security.v2.SessionV2.*;

/** 현재 수락 메타만 소유하는 DB 포트. payload/암호/CSRF를 저장하지 않는다. */
public interface SessionAdmissionLedger {
    record Settings(Provider provider,UUID epoch,String storeInstance) {}
    enum Status { PENDING,ACTIVE,REVOKED }
    record Grant(Ref ref,Provider provider,@com.fasterxml.jackson.annotation.JsonIgnore String cookieHash,@com.fasterxml.jackson.annotation.JsonIgnore String managementRef,String subjectId,AuthLevel level,
                 Status status,Instant authenticatedAt,Instant createdAt,Instant lastAccessAt,Instant expiresAt,
                 Instant publishDeadline,Instant firstUsedAt,Instant confirmDeadline,Instant terminalAt) {
        public boolean current(Instant now){return status==Status.ACTIVE&&now.isBefore(expiresAt)
                &&(level==AuthLevel.ANONYMOUS||firstUsedAt!=null||confirmDeadline!=null&&now.isBefore(confirmDeadline));}
        public boolean reservesSlot(Instant now){return level!=AuthLevel.ANONYMOUS&&now.isBefore(expiresAt)
                &&(current(now)||status==Status.PENDING&&publishDeadline!=null&&now.isBefore(publishDeadline));}
        @Override public String toString(){return "SessionGrant[보호됨]";}
    }
    Instant now();
    Settings settings(AuthDomain domain,boolean lock);
    long nextId();
    Optional<Grant> byCookie(AuthDomain domain,String hash,boolean lock);
    Optional<Grant> find(Ref ref,boolean lock);
    List<Grant> principal(PrincipalRef principal,boolean lock);
    void insert(Grant grant);
    boolean replace(Grant expected,Grant changed);
    int cleanup();
}
