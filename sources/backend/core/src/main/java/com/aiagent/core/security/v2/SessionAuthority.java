// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.Identity;

/** 소유 모듈의 현재 계정/권한 보호. 같은 DB REQUIRED에서 callback을 동기 실행한다. */
@FunctionalInterface
public interface SessionAuthority {
    record Context(AuthDomain domain,PrincipalRef principal,SessionV2.Ref ref,Identity identity,String operationId,
                   String requiredAction,boolean selfScope,Capability capability,String traceId) {}
    void protect(Context context,boolean exclusive,Runnable command);
}
