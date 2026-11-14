// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.api.auth.v2.ModuleApiCatalog;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class SourceChangeModule {
    private static final String VERIFIED=SourceChangeModule.class.getName()+".verified";
    private final SourceChangeService changes;
    public SourceChangeModule(SourceChangeService changes){this.changes=changes;}
    public Collection<ModuleApiCatalog.Operation> operations(){return List.of(new ModuleApiCatalog.Operation("API-307","POST",SourceChangeService.PATH,ModuleApiCatalog.Domain.NONE,Set.of(),null,false,Capability.READ,this::verify,null,false));}
    public Map<String,String> handlers(){return Map.of("API-307","com.aiagent.portal.api.change.SourceChangeController#receiveChange");}
    private void verify(HttpServletRequest request)throws java.io.IOException {
        try {
            if(request.getQueryString()!=null||request.getContentType()==null||!request.getContentType().split(";")[0].equalsIgnoreCase("application/json"))throw new ChangeFailure(400,"INVALID_BODY");
            var headers=new HashMap<String,List<String>>();Collections.list(request.getHeaderNames()).forEach(name->headers.put(name.toLowerCase(Locale.ROOT),List.copyOf(Collections.list(request.getHeaders(name)))));
            var verified=changes.verify(request.getMethod(),request.getRequestURI(),Map.copyOf(headers),request.getInputStream().readNBytes(16_385));request.setAttribute(VERIFIED,verified);
        }catch(ChangeFailure failure){throw new Rejected(Set.of(400,401,403,503).contains(failure.status())?failure.status():403);}
    }
    public SourceChangeService.Verified verified(HttpServletRequest request){var value=request.getAttribute(VERIFIED);if(!(value instanceof SourceChangeService.Verified verified))throw new ChangeFailure(401,"SOURCE_PROOF_REQUIRED");return verified;}
}
