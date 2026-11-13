// @feat FEAT-503
package com.aiagent.bootstrap.config;

import com.aiagent.system.api.authz.AuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 인증 필터가 보호한 API에 현재 권한 버전만 제공한다. 이력은 필터 종료 뒤 한 번 기록한다. */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    private final AuthorizationInterceptor authorization;
    public InterceptorConfig(AuthorizationInterceptor authorization) { this.authorization = authorization; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorization).addPathPatterns("/api/**");
    }
}
