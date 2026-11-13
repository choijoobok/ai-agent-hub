// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.Identity;

/** 소유 모듈의 현재 계정/권한 보호. 같은 DB REQUIRED에서 callback을 동기 실행한다. */
@FunctionalInterface
public interface SessionAuthority {
    /** 현재 계정이 불가하다. 같은 요청의 원장 세대만 영구 회수한다. */
    final class Unusable extends RuntimeException {
        public Unusable() { super("현재 계정으로 세션을 계속 사용할 수 없다"); }
    }
    record Context(AuthDomain domain,PrincipalRef principal,SessionV2.Ref ref,Identity identity,String operationId,
                   String requiredAction,boolean selfScope,Capability capability,String traceId) {}
    void protect(Context context,boolean exclusive,Runnable command);
}
