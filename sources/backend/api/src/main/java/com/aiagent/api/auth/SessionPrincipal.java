// @feat FEAT-501
package com.aiagent.api.auth;

import com.aiagent.api.auth.v2.SessionRuntime;
import com.aiagent.api.auth.v2.SessionV2Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 화면 표시용 이름·언어 스냅샷만 같은 요청의 v2 저장 계획에 반영한다. */
@Component
public class SessionPrincipal {
    public void changeLocale(String locale, HttpServletRequest request, HttpServletResponse response) {
        var scope = scope(request);
        var identity = scope.current().orElseThrow().identity();
        scope.updateSnapshot(identity.displayName(), locale, Set.of());
    }
    public void changeUserNm(String name, HttpServletRequest request, HttpServletResponse response) {
        var scope = scope(request);
        var identity = scope.current().orElseThrow().identity();
        scope.updateSnapshot(name, identity.locale(), Set.of());
    }
    private SessionRuntime.Scope scope(HttpServletRequest request) {
        if (!(request.getAttribute(SessionV2Filter.SCOPE) instanceof SessionRuntime.Scope scope))
            throw new IllegalStateException("공통 인증 경계를 거쳐야 한다");
        return scope;
    }
}
