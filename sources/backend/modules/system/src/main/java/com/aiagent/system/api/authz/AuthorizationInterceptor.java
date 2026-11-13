// @feat FEAT-503
package com.aiagent.system.api.authz;

import com.aiagent.api.auth.v2.SessionV2Filter;
import com.aiagent.api.authz.AuthzVersionHeader;
import com.aiagent.system.api.auth.AdminRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 판정은 ADMIN 보호에서 끝낸다. 여기서는 현재 계산값의 응답 버전만 제공한다. */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(SessionV2Filter.SCOPE) == null)
            throw new IllegalStateException("공통 인증 경계를 거치지 않은 API 요청이다");
        if (!request.getRequestURI().contains("/public/"))
            AuthzVersionHeader.stamp(response, AdminRequestContext.authority());
        return true;
    }
}
