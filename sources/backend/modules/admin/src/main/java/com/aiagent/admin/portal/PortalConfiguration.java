// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.time.BusinessCalendar;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PortalConfiguration {
    private static final String HANDLER="com.aiagent.admin.api.portal.PortalUserController#";
    @Bean PortalPolicy portalPolicy(DataSource source){return new PortalPolicy(source);}
    @Bean PortalSecretDelivery portalSecretDelivery(){return new PortalSecretDelivery();}
    @Bean PortalDependencies portalDependencies(DataSource source,ObjectProvider<PortalQuotaPort> quota,ObjectProvider<PortalIdentityTargets> targets,ObjectProvider<PortalExternalDirectory> external){return new PortalDependencies(source,quota,targets,external);}
    @Bean PortalCredentials portalCredentials(DataSource source,PasswordEncoder encoder,ConfigLookup configs,PortalSecretDelivery secrets){return new PortalCredentials(source,encoder,configs,secrets);}
    @Bean PortalGrades portalGrades(DataSource source,QuotaGradeQueryService definitions,PortalDependencies dependencies){return new PortalGrades(source,definitions,dependencies);}
    @Bean PortalUserService portalUserService(DataSource source,ManagedIds ids,ManagedLocks locks,PortalPolicy policy,BusinessCalendar calendar,PortalCredentials credentials,PortalDependencies dependencies,PortalGrades grades,@Value("${app.portal.auth-mode}") String mode,jakarta.persistence.EntityManagerFactory ready){return new PortalUserService(source,ids,locks,policy,calendar,credentials,dependencies,grades,mode);}
    @Bean PortalGradeUsage portalGradeUsage(DataSource source,PortalDependencies dependencies,PortalGrades grades,PortalUserService users,ManagedIds ids){return new PortalGradeUsage(source,dependencies,grades,users,ids);}
    @Bean ManagedRegistry.Operation portalUserSave(PortalUserService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-084",PortalUserService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean ManagedRegistry.Operation portalUserReset(PortalUserService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-104",PortalUserService.BASE+"/resetCredential",HANDLER+"resetCredential",false,owner,journal);}
    @Bean ManagedRegistry.Operation portalUserUnlock(PortalUserService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-142",PortalUserService.BASE+"/unlockCredential",HANDLER+"unlockCredential",false,owner,journal);}
    @Bean InstalledAdminRoutes portalUserReads(){return new InstalledAdminRoutes(List.of(
            new InstalledAdminRoutes.Route("API-101","POST",PortalUserService.BASE+"/getList",HANDLER+"getList","READ"),
            new InstalledAdminRoutes.Route("API-102","POST",PortalUserService.BASE+"/getOne",HANDLER+"getOne","READ"),
            new InstalledAdminRoutes.Route("API-103","POST",PortalUserService.BASE+"/getIdentityTargets",HANDLER+"getIdentityTargets","READ")));}
}
