// @feat FEAT-003
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.*;
import com.aiagent.domain.authz.*;
import com.aiagent.domain.authz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
@Component
public final class CatalogAccess {
 private final HttpServletRequest request;private final EffectiveAuthorityService authorities;
 public CatalogAccess(HttpServletRequest request,EffectiveAuthorityService authorities){this.request=request;this.authorities=authorities;}
 public String actor(){if(request.getUserPrincipal()==null||AdminAuthorityProtection.currentToken()==null)throw ManagedErrorCode.FORBIDDEN.failure();return request.getUserPrincipal().getName();}
 public void require(ActionType... actions){var a=authorities.calculate(actor());for(var action:actions)if(!a.allows("HUB_CATALOG",action))throw ManagedErrorCode.FORBIDDEN.failure();}
 public void requirePolicy(){require(ActionType.READ,ActionType.SAVE);}
}
