// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.core.security.v2.SessionAuthority;
import com.aiagent.domain.authz.service.*;
import com.aiagent.support.time.BusinessCalendar;
import java.time.Duration;
import java.util.*;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ManagedConfiguration {
    @Bean public ManagedLocks managedLocks(DataSource source) { return new ManagedLocks(source); }
    @Bean public ManagedIds managedIds(DataSource source,PlatformTransactionManager manager) { return new ManagedIds(source,manager); }
    @Bean public JdbcManagedJournal managedJournal(DataSource source) { return new JdbcManagedJournal(source); }
    @Bean public ManagedCommandGuard managedCommandGuard(DataSource source) { return new ManagedCommandGuard(source); }
    @Bean public ManagedRegistry managedRegistry(DataSource source,ObjectProvider<ManagedRegistry.Operation> operations,ObjectProvider<ManagedJournal> journals) {
        return new ManagedRegistry(source,operations.orderedStream().toList(),journals.orderedStream().toList());
    }
    @Bean public ManagedAdmission managedAdmission(ManagedLocks locks,ManagedRegistry registry) { return new ManagedAdmission(locks,registry); }
    @Bean("administratorAuthorityDecorator") public UnaryOperator<SessionAuthority> administratorAuthorityDecorator(ManagedAdmission admission) { return delegate->admission.apply(delegate); }
    @Bean public ManagedAuthorization managedAuthorization(EffectiveAuthorityService authority,ApiAuthorityRegistry apis) { return new ManagedAuthorization(authority,apis); }
    @Bean public ManagedCommandService managedCommandService(ManagedRegistry registry,ManagedAdmission admission,ManagedAuthorization authorization,
            ManagedLocks locks,ManagedCommandGuard guard,ManagedIds ids,BusinessCalendar calendar,ManagedHistoryService history) {
        return new ManagedCommandService(registry,admission,authorization,locks,guard,ids,calendar,history);
    }
    @Bean public ManagedCommandQueryService managedCommandQueryService(ManagedRegistry registry,ManagedAdmission admission,ManagedAuthorization authorization,ManagedCommandGuard guard) {
        return new ManagedCommandQueryService(registry,admission,authorization,guard);
    }
    @Bean public ManagedHistoryService managedHistoryService(ManagedRegistry registry,ManagedLocks locks,ManagedIds ids,DataSource source,BusinessCalendar calendar,ObjectProvider<ManagedHistoryService.Consumer> consumers) {
        return new ManagedHistoryService(registry,locks,ids,source,calendar,consumers.orderedStream().toList());
    }
    @Bean public ManagedRetentionService managedRetentionService(ManagedRegistry registry,ManagedLocks locks,ManagedHistoryService history,ManagedCommandGuard guard,BusinessCalendar calendar,Environment environment) {
        Long days=environment.getProperty("app.admin.history.retention-days",Long.class);
        return new ManagedRetentionService(registry,locks,history,guard,calendar,days==null?null:Duration.ofDays(days));
    }
    @Bean public FilterRegistrationBean<ManagedRequestFilter> managedRequestFilter(ManagedRegistry registry,ManagedAdmission admission,ObjectMapper mapper,ApiErrorWriter errors) {
        var registration=new FilterRegistrationBean<>(new ManagedRequestFilter(registry,admission,mapper,errors));
        registration.setName("managedRequestPreparation"); registration.setOrder(-1);return registration;
    }
    @Bean public InstalledAdminRoutes managedRoutes(ManagedRegistry registry) {
        var routes=new ArrayList<InstalledAdminRoutes.Route>();
        routes.add(new InstalledAdminRoutes.Route("API-082","POST",ManagedRequestFilter.RESULT_PATH,
                "com.aiagent.admin.api.management.ManagementCommandController#getResult","READ"));
        for(var op:registry.operations())routes.add(new InstalledAdminRoutes.Route(op.id(),"POST",op.path(),op.handler(),"SAVE"));
        return new InstalledAdminRoutes(routes);
    }
}
