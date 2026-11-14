// @feat FEAT-301
package com.aiagent.bootstrap.config;

import com.aiagent.admin.portal.*;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.security.*;
import com.aiagent.core.security.portal.PortalAccounts;
import com.aiagent.core.security.v2.SessionAuthority;
import com.aiagent.portal.auth.PortalAuthEventService;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/** 제품 모듈은 서로 의존하지 않고 공개 포트만 여기서 조립한다. */
@Configuration
public class PortalAccountConfiguration {
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> portalAuthEventContext(PortalAuthEventService events) {
        jakarta.servlet.Filter binding=(request,response,chain)->{events.beginExecution();try{chain.doFilter(request,response);}finally{events.endExecution();}};
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(binding);registration.setName("portalAuthEventContext");registration.setOrder(0);return registration;
    }
    @Bean org.springframework.boot.ApplicationRunner verifyPortalAccountMode(PortalAccountService owner) {return args->owner.verifyDeploymentMode();}
    @Bean PortalAccounts portalAccounts(PortalAccountService owner,PortalAccessService access,PortalPolicy policy) {
        return new PortalAccounts() {
            public void protect(boolean exclusive,Runnable command){policy.protect(exclusive);command.run();}
            public Verification verifyLocal(String loginId,String password){var v=owner.verifyLocalCredentials(loginId,password);return new Verification(v.outcome(),v.principal(),v.credentialVersion()==null?null:Long.valueOf(v.credentialVersion()),v.userNm());}
            public Account current(PrincipalRef principal,Long version) {
                try {return account(owner.currentAccount(principal,version==null?null:version.toString()));}
                catch(BusinessException failure){if(failure.errorCode().type().status()==403)throw new SessionAuthority.Unusable();throw failure;}
            }
            public PasswordChange changePassword(PrincipalRef p,Long v,String oldPassword,String nextPassword){var result=owner.changeOwnLocalPassword(p,v.toString(),oldPassword,nextPassword);return new PasswordChange(result.outcome(),result.credentialVersion()==null?null:Long.valueOf(result.credentialVersion()),result.changedAt());}
            public Account resolveExternal(ExternalIdentity v){return account(owner.resolveVerifiedExternalIdentity(new PortalExternalDirectory.Identity(v.sourceKey(),v.subject(),v.loginId(),v.displayName(),v.organization(),v.sourceRevision())));}
            public Access access(PrincipalRef principal) {
                var result=access.resolveCurrentPortalAccess(principal,null);
                if(result.outcome().equals("UNAVAILABLE"))throw new com.aiagent.core.security.v2.SessionV2.Failure(com.aiagent.core.security.v2.SessionV2.Outcome.UNAVAILABLE);
                if(!result.outcome().equals("ALLOW"))return new Access(false,Set.of(result.reason()));
                return result.allocation()!=null&&result.allocation().state().equals("READY")?new Access(true,Set.of()):new Access(false,Set.of("QUOTA_NOT_READY"));
            }
            private Account account(PortalAccountService.Account a){return new Account(a.principal(),a.userNm(),a.authMode(),a.credentialVersion()==null?null:Long.valueOf(a.credentialVersion()),a.restricted());}
        };
    }
    @Bean @ConditionalOnMissingBean(PortalSecurityEvents.class)
    PortalSecurityEvents portalSecurityEvents(DataSource source,PortalAuthEventService sink) {
        return new PortalSecurityEvents() {
            public DataSource dataSource(){return source;}
            public void record(Event event){sink.record(switch(event.kind()){case LOGIN_ACCEPTED->"LOGIN_SUCCESS";case LOGIN_REJECTED->"LOGIN_FAILURE";case PASSWORD_CHANGED->"PASSWORD_CHANGED";},event.principal(),event.result(),event.at());}
        };
    }
}
