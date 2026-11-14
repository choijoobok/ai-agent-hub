// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.core.security.portal.PortalAccounts.ExternalIdentity;
import java.time.Instant;

/** 고객 검증기는306에서 제공한다. 검증은 DB 보호 밖에서 수행하고 원 토큰을 반환하지 않는다. */
public interface ExternalAuthProvider {
    String sourceKey();
    String issuer();
    String audience();
    default boolean testOnly(){return false;}
    Proof verify(Object protocolEvidence);
    record Proof(ExternalIdentity identity,String issuer,String audience,String assertionId,String nonce,Instant verifiedAt,Instant expiresAt) {
        @Override public String toString(){return "VerifiedExternalProof[보호됨]";}
    }
}
