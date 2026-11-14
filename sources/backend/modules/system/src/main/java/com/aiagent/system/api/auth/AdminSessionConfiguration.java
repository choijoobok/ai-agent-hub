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
}
