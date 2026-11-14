// @feat FEAT-301
package com.aiagent.portal.auth;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public record PortalAuthSettings(String mode,Duration timeout,int maxSessions,boolean preventLogin,Duration anonymousTimeout,Duration challengeTimeout) {
    @org.springframework.beans.factory.annotation.Autowired
    public PortalAuthSettings(Environment environment) {
        this(environment.getRequiredProperty("app.portal.auth-mode"),
             environment.getProperty("app.auth.portal.session.timeout",Duration.class,Duration.ofMinutes(30)),
             environment.getProperty("app.auth.portal.session.max-sessions",Integer.class,1),
             environment.getProperty("app.auth.portal.session.prevent-login",Boolean.class,false),
             environment.getProperty("app.auth.portal.anonymous-timeout",Duration.class,Duration.ofMinutes(10)),
             environment.getProperty("app.auth.portal.external.challenge-timeout",Duration.class,Duration.ofMinutes(5)));
    }
    public PortalAuthSettings {
        if(!java.util.Set.of("LOCAL","EXTERNAL").contains(mode)||timeout.isNegative()||timeout.isZero()||maxSessions<1||anonymousTimeout.isNegative()||anonymousTimeout.isZero()||challengeTimeout.isNegative()||challengeTimeout.isZero()||challengeTimeout.compareTo(Duration.ofMinutes(5))>0||challengeTimeout.compareTo(anonymousTimeout)>0)
            throw new IllegalArgumentException("Portal 인증 모드·기간·동시 접속 설정이 올바르지 않다");
    }
}
