// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import com.aiagent.admin.managed.ManagedErrorCode;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public final class TargetSystemAccess {
    private final HttpServletRequest request;private final EffectiveAuthorityService authorities;
    public TargetSystemAccess(HttpServletRequest request,EffectiveAuthorityService authorities){this.request=request;this.authorities=authorities;}
    String actor(){var principal=request.getUserPrincipal();if(principal==null||AdminAuthorityProtection.currentToken()==null)throw ManagedErrorCode.FORBIDDEN.failure();return principal.getName();}
    boolean allows(ActionType action){return authorities.calculate(actor()).allows("HUB_TARGET_SYSTEM",action);}
    void require(ActionType... actions){var authority=authorities.calculate(actor());for(var action:actions)if(!authority.allows("HUB_TARGET_SYSTEM",action))throw ManagedErrorCode.FORBIDDEN.failure();}
}
