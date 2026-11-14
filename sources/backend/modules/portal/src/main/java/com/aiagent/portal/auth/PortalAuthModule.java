// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.api.auth.v2.ModuleApiCatalog;
import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.SessionAdmissionsV2;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class PortalAuthModule {
    private final SessionAdmissionsV2.Producer producer=SessionAdmissionsV2.producer(AuthDomain.PORTAL,"portal-credentials/v2");
    public SessionAdmissionsV2.Producer producer(){return producer;}
    public Collection<ModuleApiCatalog.Operation> operations() {
        var all=Set.of(AuthLevel.values());var authenticated=Set.of(AuthLevel.PASSWORD_CHANGE_REQUIRED,AuthLevel.AUTHENTICATED);
        return List.of(route(301,"GET","public","getCsrf",Capability.PREPARE_CSRF,all),
            route(302,"POST","public","login",Capability.LOGIN,all),
            route(303,"GET","common","getMe",Capability.READ,authenticated),
            route(304,"POST","common","changePassword",Capability.PASSWORD_CHANGE,authenticated),
            route(305,"POST","common","logout",Capability.LOGOUT,authenticated),
            route(306,"POST","common","heartbeat",Capability.READ,authenticated));
    }
    private ModuleApiCatalog.Operation route(int id,String method,String layer,String name,Capability capability,Set<AuthLevel> levels) {
        return new ModuleApiCatalog.Operation("API-"+id,method,"/api/portal/"+layer+"/auth/"+name,ModuleApiCatalog.Domain.PORTAL,levels,null,true,capability,null,null);
    }
    public Map<String,String> handlers(){var result=new HashMap<String,String>();operations().forEach(op->result.put(op.id(),"com.aiagent.portal.api.auth.PortalAuthController#"+op.path().substring(op.path().lastIndexOf('/')+1)));return Map.copyOf(result);}
}
