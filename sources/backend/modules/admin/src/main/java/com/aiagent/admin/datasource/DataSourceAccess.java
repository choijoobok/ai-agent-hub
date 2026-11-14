// @feat FEAT-002
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.ManagedErrorCode;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
@Component
public final class DataSourceAccess {
    private final HttpServletRequest request;private final EffectiveAuthorityService authority;
    public DataSourceAccess(HttpServletRequest request,EffectiveAuthorityService authority){this.request=request;this.authority=authority;}
    public String actor(){if(request.getUserPrincipal()==null||AdminAuthorityProtection.currentToken()==null)throw ManagedErrorCode.FORBIDDEN.failure();return request.getUserPrincipal().getName();}
    public boolean allows(ActionType action){return authority.calculate(actor()).allows("HUB_DATA_SOURCE",action);}
    public void require(ActionType... actions){var a=authority.calculate(actor());for(var action:actions)if(!a.allows("HUB_DATA_SOURCE",action))throw ManagedErrorCode.FORBIDDEN.failure();}
}
