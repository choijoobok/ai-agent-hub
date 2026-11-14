// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.api.auth.v2.SessionRuntime;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.*;
import com.aiagent.core.security.portal.PortalAccounts;
import com.aiagent.core.security.v2.SessionV2;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PortalAuthService {
    public record State(String domain,String subjectId,String displayName,AuthLevel authLevel,Instant sessionExpiresAt,PortalAccounts.Access access) {}
    public record Attempt(State state,PortalAuthErrorCode error) {}
    private final PortalAccounts accounts;
    private final PortalAuthModule module;
    private final PortalAuthSettings settings;
    private final PortalAuthEventService events;
    public PortalAuthService(PortalAccounts accounts,PortalAuthModule module,PortalAuthSettings settings,PortalAuthEventService events){this.accounts=accounts;this.module=module;this.settings=settings;this.events=events;}
    public Attempt login(SessionRuntime.Scope scope,String loginId,String password) {
        if(!settings.mode().equals("LOCAL"))return new Attempt(null,PortalAuthErrorCode.MODE);
        var verification=accounts.verifyLocal(loginId,password);
        if(verification.principal()==null)return new Attempt(null,PortalAuthErrorCode.REJECTED);
        var identity=new Identity(verification.principal(),AuthLevel.valueOf(verification.outcome()),"LOCAL",verification.credentialVersion(),events.now(),verification.displayName(),"ko");
        scope.publish(module.producer().login(scope.exchange(),scope.current().orElseThrow().ref(),identity,events.now().plusSeconds(60)),slots());
        return new Attempt(state(scope),null);
    }
    public Attempt changePassword(SessionRuntime.Scope scope,String currentPassword,String newPassword) {
        if(!settings.mode().equals("LOCAL"))return new Attempt(null,PortalAuthErrorCode.MODE);
        var current=scope.current().orElseThrow();var identity=current.identity();
        var result=accounts.changePassword(identity.principal(),identity.credentialVersion(),currentPassword,newPassword);
        if(!result.outcome().equals("CHANGED"))return new Attempt(null,result.outcome().equals("POLICY_REJECTED")?PortalAuthErrorCode.POLICY:PortalAuthErrorCode.REJECTED);
        var changed=new Identity(identity.principal(),AuthLevel.AUTHENTICATED,"LOCAL",result.credentialVersion(),result.changedAt(),identity.displayName(),identity.locale());
        scope.stageCommittedRotation(module.producer().rotation(scope.exchange(),current.ref(),changed,events.now().plusSeconds(60)),slots());
        return new Attempt(new State("PORTAL",changed.principal().subjectId(),changed.displayName(),changed.authLevel(),events.now().plus(settings.timeout()),accounts.access(changed.principal())),null);
    }
    public State state(SessionRuntime.Scope scope) {
        var current=scope.current().orElseThrow();var identity=current.identity();
        if(identity==null)throw new SessionV2.Failure(SessionV2.Outcome.INVALID_SESSION);
        var account=accounts.current(identity.principal(),identity.credentialVersion());
        return new State("PORTAL",identity.principal().subjectId(),account.displayName(),identity.authLevel(),events.now().plus(settings.timeout()),accounts.access(identity.principal()));
    }
    public void logout(SessionRuntime.Scope scope){var identity=scope.current().orElseThrow().identity();events.record("LOGOUT",identity.principal(),"LOGOUT",events.now());scope.logout();}
    public SessionRuntime.SlotPolicy slots(){return new SessionRuntime.SlotPolicy(true,settings.maxSessions(),settings.preventLogin());}
}
