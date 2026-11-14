// @feat FEAT-301
package com.aiagent.core.security.portal;

import com.aiagent.core.security.PrincipalRef;
import java.time.Instant;
import java.util.Set;

/** 소유 원장을 호출하는 중립 계약. bootstrap이 admin 공개 포트와 연결한다. */
public interface PortalAccounts {
    record Verification(String outcome,PrincipalRef principal,Long credentialVersion,String displayName) {}
    record Account(PrincipalRef principal,String displayName,String mode,Long credentialVersion,boolean restricted) {}
    record PasswordChange(String outcome,Long credentialVersion,Instant changedAt) {}
    record Access(boolean eligible,Set<String> reasons) {public Access {reasons=Set.copyOf(reasons);}}
    record ExternalIdentity(String sourceKey,String subject,String loginId,String displayName,String organization,String sourceRevision) {}
    void protect(boolean exclusive,Runnable command);
    Verification verifyLocal(String loginId,String password);
    Account current(PrincipalRef principal,Long credentialVersion);
    PasswordChange changePassword(PrincipalRef principal,Long version,String currentPassword,String newPassword);
    Account resolveExternal(ExternalIdentity identity);
    Access access(PrincipalRef principal);
}
