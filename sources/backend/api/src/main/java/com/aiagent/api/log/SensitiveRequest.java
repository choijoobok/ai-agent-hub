// @feat FEAT-501
package com.aiagent.api.log;

import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

/** 인증 비밀과 Portal 전문은 마스킹 설정에 관계없이 수집하지 않는다. */
public final class SensitiveRequest {
    private SensitiveRequest() {}
    private static final Set<String> COMMANDS = Set.of(
            "/api/system/public/auth/login", "/api/system/public/auth/sendResetCode",
            "/api/system/public/auth/verifyResetCode", "/api/system/public/auth/saveResetPassword",
            "/api/system/common/auth/changePassword", "/api/system/service/user/savePasswordReset",
            "/api/admin/service/portalUser/resetCredential");
    public static boolean excluded(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/portal/") || path.contains("/auth/") || COMMANDS.contains(path);
    }
    public static boolean secretHeader(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return key.equals("cookie") || key.equals("authorization") || key.equals("proxy-authorization")
                || key.contains("csrf") || key.contains("token") || key.contains("secret");
    }
}
