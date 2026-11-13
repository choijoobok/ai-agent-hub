// @feat FEAT-501
package com.aiagent.system.api.user;

import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.auth.service.LoginHistoryService;
import com.aiagent.domain.auth.service.LoginHistoryService.LoginOrigin;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.aiagent.system.api.auth.AdminRequestContext;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import java.util.Map;
import org.springframework.stereotype.Service;


@Service
public class UserSessionService {


    private static final int LOGIN_SCAN = 50;


    private static final Duration MATCH_WINDOW = Duration.ofMinutes(1);


    private final LoginHistoryService history;

    public UserSessionService(
            LoginHistoryService history) {
        this.history = history;
    }


    public List<UserSessionRow> list(String userId) {
        var scope = AdminRequestContext.scope();
        var sessions = scope.inspect(scope.inspectionFor(new PrincipalRef(AuthDomain.ADMIN, userId)));
        // 세션이 없으면 이력을 읽지 않는다 — 짝지을 것이 없는데 왕복을 하나 쓰게 된다.
        List<LoginOrigin> logins = sessions.isEmpty()
                ? List.of()
                : history.findRecentSuccess(userId, LOGIN_SCAN);

        return sessions.stream()
                .map(session -> {
                    LoginOrigin origin = originOf(logins, session.createdAt());
                    return new UserSessionRow(
                            session.sessionId(),
                            session.current(),
                            session.createdAt(),
                            session.lastAccessedAt(),
                            session.expiresAt(),
                            origin == null ? null : origin.clientIp(),
                            origin == null ? null : origin.userAgent());
                })
                .sorted(Comparator.comparing(UserSessionRow::lastAccessedDtm).reversed())
                .toList();
    }


    private static LoginOrigin originOf(List<LoginOrigin> logins, Instant createdAt) {
        LoginOrigin best = null;
        Duration bestGap = null;
        for (LoginOrigin login : logins) {
            Duration gap = Duration.between(login.at(), createdAt).abs();
            if (gap.compareTo(MATCH_WINDOW) > 0) {
                continue;
            }
            if (bestGap == null || gap.compareTo(bestGap) < 0) {
                best = login;
                bestGap = gap;
            }
        }
        return best;
    }


    public int kill(String userId, List<String> sessionIds) {
        var scope = AdminRequestContext.scope();
        return scope.revoke(scope.inspectionFor(new PrincipalRef(AuthDomain.ADMIN, userId)), sessionIds);
    }
}
