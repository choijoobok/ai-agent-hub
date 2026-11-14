// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.core.security.*;
import com.aiagent.core.security.portal.PortalAccounts;
import com.aiagent.core.security.v2.SessionAuthority;
import org.springframework.stereotype.Component;

@Component
public final class PortalAuthorityGuard implements SessionAuthority {
    private final PortalAccounts accounts;
    public PortalAuthorityGuard(PortalAccounts accounts){this.accounts=accounts;}
    @Override public void protect(Context context,boolean exclusive,Runnable command) {
        if(context.domain()!=AuthDomain.PORTAL)throw new Rejected(401);
        accounts.protect(exclusive,()->{
            if(context.principal()!=null) {
                var identity=context.identity();
                if(identity==null||identity.principal().domain()!=AuthDomain.PORTAL)throw new Unusable();
                var current=accounts.current(identity.principal(),identity.credentialVersion());
                if(!current.mode().equals(identity.authMode()))throw new Unusable();
                if(current.restricted()&&identity.authLevel()!=SessionPersistencePort.AuthLevel.PASSWORD_CHANGE_REQUIRED)throw new Unusable();
                if(!context.selfScope()) {
                    // 소유 업무의 대상/쿼터 판정은 각 기능에서 추가한다. 미등록 업무는 열지 않는다.
                    throw new Rejected(403);
                }
            } else if(!context.selfScope())throw new Rejected(401);
            command.run();
        });
    }
}
