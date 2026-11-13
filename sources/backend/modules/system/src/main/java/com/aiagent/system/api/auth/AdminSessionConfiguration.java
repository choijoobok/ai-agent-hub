// @feat FEAT-501
package com.aiagent.system.api.auth;

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

/** 공통 v2를 실제 관리자 원장·권한과 조립한다. Portal 생산자를 임의로 만들지 않는다. */
@Configuration
public class AdminSessionConfiguration {
    @Bean public SessionAdmissionsV2.Producer adminCredentialProducer() {
        return SessionAdmissionsV2.producer(AuthDomain.ADMIN, "system-credentials/v2");
    }

    @Bean public SessionV2Assembly administratorSessions(Environment environment, DataSource source,
            PlatformTransactionManager manager, AdminAuthorityGuard guard, AdminApiCatalog catalog,
            SessionAdmissionsV2.Producer producer, ObjectProvider<RedisConnectionFactory> redis,
            ObjectMapper mapper, ApiErrorWriter errors, jakarta.persistence.EntityManagerFactory entityManagerFactory,
            @Qualifier("administratorAuthorityDecorator") ObjectProvider<java.util.function.UnaryOperator<com.aiagent.core.security.v2.SessionAuthority>> decorators) {
        // EntityManagerFactory는 Liquibase가 완료된 뒤 초기화된다. 선택 판은 첫 빈 설치 때만 만든다.
        var configuration = SessionV2Assembly.Configuration.from(environment);
        if (!configuration.domains().keySet().equals(Set.of(AuthDomain.ADMIN)))
            throw new IllegalStateException("Portal 인증은 소유 구현 전 활성화할 수 없다");
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
        return SessionV2Assembly.create(configuration, source, manager,
                configuration.provider() == Provider.REDIS ? redis.getObject() : null,
                new SessionRuntime.AuthorityBinding(source, manager, decorators.getIfAvailable(java.util.function.UnaryOperator::identity).apply(guard)), catalog.catalog(), Map.of(AuthDomain.ADMIN, producer),
                new BoundaryResponseWriter(mapper, errors), Map.of("API-517", inspect, "API-518", inspect));
    }

    @Bean public FilterRegistrationBean<Filter> administratorRequestContext(com.aiagent.api.log.UserActionLogInterceptor audit) {
        Filter binding = (request, response, chain) -> {
            AdminRequestContext.bind((HttpServletRequest) request);
            try { chain.doFilter(request, response); }
            finally {
                try {
                    AdminRequestContext.runAfterCommit();
                    audit.afterCompletion((HttpServletRequest) request, (HttpServletResponse) response, null, null);
                } finally { AdminRequestContext.clear(); }
            }
        };
        var registration = new FilterRegistrationBean<>(binding);
        registration.setName("administratorRequestContext");
        registration.setOrder(0);
        return registration;
    }
    @Bean public FilterRegistrationBean<SessionV2Filter> administratorSessionFilter(SessionV2Assembly assembly) {
        var registration = new FilterRegistrationBean<>(assembly.filter());
        registration.setName("administratorSessionFilter");
        registration.setOrder(1);
        return registration;
    }
    @Bean public ApplicationRunner verifyAdministratorInstallation(SessionV2Assembly assembly, AdminApiCatalog catalog,
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
            assembly.verifyInstallation(beans, servletContext, catalog.expectedHandlers(), observed);
        };
    }
}
