// @feat FEAT-301
package com.aiagent.bootstrap.config;

import com.aiagent.api.auth.boundary.BoundaryResponseWriter;
import com.aiagent.api.auth.v2.*;
import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.v2.SessionAdmissionsV2;
import com.aiagent.core.security.v2.SessionV2.Provider;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import com.aiagent.system.api.auth.*;
import com.aiagent.portal.auth.*;
import com.aiagent.core.security.v2.SessionAuthority;

/** 두 영역을 하나의 공통 필터에 연결하는 제품 조립 루트. */
@Configuration
public class AuthSessionConfiguration {
    @Bean public SessionV2Assembly administratorSessions(Environment environment, DataSource source,
            PlatformTransactionManager manager, AdminAuthorityGuard guard, AdminApiCatalog catalog,
            SessionAdmissionsV2.Producer producer, PortalAuthModule portal, PortalAuthorityGuard portalGuard, ObjectProvider<RedisConnectionFactory> redis,
            ObjectMapper mapper, ApiErrorWriter errors, jakarta.persistence.EntityManagerFactory entityManagerFactory,
            @Qualifier("administratorAuthorityDecorator") ObjectProvider<java.util.function.UnaryOperator<com.aiagent.core.security.v2.SessionAuthority>> decorators) {
        // EntityManagerFactory는 Liquibase가 완료된 뒤 초기화된다. 선택 판은 첫 빈 설치 때만 만든다.
        var configuration = SessionV2Assembly.Configuration.from(environment);
        if (!configuration.domains().containsKey(AuthDomain.ADMIN)) throw new IllegalStateException("관리자 인증 영역은 필수다");
        var jdbc = new JdbcTemplate(source);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(1095321685,1)");
            configuration.domains().forEach((domain, settings) -> {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN=?", Long.class, domain.name()) == 0) {
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT WHERE AUTH_DOMAIN=?", Long.class, domain.name()) != 0)
                        throw new IllegalStateException("인증 설정판 없는 기존 수락 원장을 초기화할 수 없다");
                    jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE(AUTH_DOMAIN,PROVIDER_TYPE,PROVIDER_EPOCH,STORE_INSTANCE,STATE_CD,CHANGED_AT) VALUES (?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP)",
                            domain.name(), settings.provider().name(), settings.epoch(), settings.storeInstance());
                }
            });
        });
        SessionV2Filter.Preflight inspect = (request, port) -> {
            var body = mapper.readTree(request.getInputStream());
            var target = body.path("params").path("userId");
            if (target.isMissingNode()) target = body.path("userId");
            if (!target.isTextual() || target.asText().isBlank()) throw new com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected(400);
            return port.prepare(new PrincipalRef(AuthDomain.ADMIN, target.asText()));
        };
        boolean portalEnabled=configuration.domains().containsKey(AuthDomain.PORTAL);
        var operations=new ArrayList<>(catalog.catalog().entries());
        if(portalEnabled)operations.addAll(portal.operations());
        var combined=new ModuleApiCatalog(operations,Arrays.asList(environment.getActiveProfiles()).contains("local"));
        SessionAuthority administrator=decorators.getIfAvailable(java.util.function.UnaryOperator::identity).apply(guard);
        SessionAuthority authority=(context,exclusive,command)->{
            if(context.domain()==AuthDomain.ADMIN)administrator.protect(context,exclusive,command);
            else if(portalEnabled)portalGuard.protect(context,exclusive,command);
            else throw new com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected(403);
        };
        Map<AuthDomain,SessionAdmissionsV2.Producer> producers=new EnumMap<>(AuthDomain.class);
        producers.put(AuthDomain.ADMIN,producer);if(portalEnabled)producers.put(AuthDomain.PORTAL,portal.producer());
        return SessionV2Assembly.create(configuration, source, manager,
                configuration.provider() == Provider.REDIS ? redis.getObject() : null,
                new SessionRuntime.AuthorityBinding(source, manager, authority), combined, producers,
                new BoundaryResponseWriter(mapper, errors), Map.of("API-517", inspect, "API-518", inspect));
    }

    @Bean public FilterRegistrationBean<SessionV2Filter> administratorSessionFilter(SessionV2Assembly assembly) {
        var registration = new FilterRegistrationBean<>(assembly.filter());
        registration.setName("administratorSessionFilter");
        registration.setOrder(1);
        return registration;
    }
    @Bean public ApplicationRunner verifyAdministratorInstallation(SessionV2Assembly assembly, AdminApiCatalog catalog, PortalAuthModule portal, Environment environment,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers,
            ConfigurableListableBeanFactory beans, ServletContext servletContext) {
        return args -> {
            List<ModuleApiCatalog.Handler> observed = new ArrayList<>();
            handlers.getHandlerMethods().forEach((mapping, handler) -> {
                for (String path : mapping.getPatternValues()) {
                    if (!path.startsWith("/api/")) continue;
                    for (var method : mapping.getMethodsCondition().getMethods())
                        observed.add(new ModuleApiCatalog.Handler(method.name(), path,
                                handler.getBeanType().getName() + "#" + handler.getMethod().getName()));
                }
            });
            var expected=new HashMap<>(catalog.expectedHandlers());
            if(environment.getProperty("app.auth.session.domains.portal.enabled",Boolean.class,false))expected.putAll(portal.handlers());
            assembly.verifyInstallation(beans, servletContext, expected, observed);
        };
    }
}
