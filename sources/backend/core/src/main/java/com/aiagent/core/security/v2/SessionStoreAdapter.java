// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.PrincipalRef;
import java.time.Instant;
import java.util.Optional;
import com.aiagent.core.security.v2.SessionV2.*;

/** payload 저장만 소유한다. 인증 허용은 DB 원장/소유 guard가 별도로 판정한다. */
public interface SessionStoreAdapter {
    Provider provider();
    void verifyAvailable();
    Optional<Stored> load(Ref ref,PrincipalRef principal);
    Stored create(Payload payload,Instant expiresAt);
    Outcome updateExisting(Stored expected,Payload changed,Instant expiresAt);
    Outcome delete(Stored expected);
    int cleanupExpired();
}
