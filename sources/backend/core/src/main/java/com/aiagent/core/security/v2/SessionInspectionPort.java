// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.PrincipalRef;
import java.time.Instant;
import java.util.List;

/** 선택 store 읽기는 보호 진입 전, 결과 공개/회수는 현재 권한 보호 안에서 수행한다. */
public interface SessionInspectionPort {
    interface Plan {}
    record View(String sessionId,boolean current,Instant createdAt,Instant lastAccessedAt,Instant expiresAt) {}
    Plan prepare(PrincipalRef principal);
    interface Authorized {
        List<View> inspect(Plan plan);
        int revoke(Plan plan,List<String> managementRefs);
    }
}
