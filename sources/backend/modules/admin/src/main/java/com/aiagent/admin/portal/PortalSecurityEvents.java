// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.core.security.PrincipalRef;
import java.time.Instant;
import javax.sql.DataSource;

/** 301 보안 감사 저장 연결. 같은 물리 트랜잭션으로 기록하고 비밀/원 로그인 입력을 받지 않는다. */
public interface PortalSecurityEvents {
    enum Kind { LOGIN_ACCEPTED, LOGIN_REJECTED, PASSWORD_CHANGED }
    record Event(Kind kind,PrincipalRef principal,String result,Instant at) {}
    DataSource dataSource();
    void record(Event event);
}
