// @feat FEAT-503
package com.aiagent.api.authz;

/** 모듈 우선 경로의 이력 분류. 수락 여부는 정확 API 목록에서 별도로 판정한다. */
public enum ApiLayer {
    PUBLIC, COMMON, SERVICE, UNCLASSIFIED;
    public static ApiLayer of(String path) {
        if (path == null || !path.startsWith("/api/")) return null;
        String[] parts = path.split("/", -1);
        if (parts.length < 5 || !java.util.Set.of("system", "sample", "portal", "admin", "agent").contains(parts[2])) return UNCLASSIFIED;
        return switch (parts[3]) {
            case "public" -> PUBLIC;
            case "common" -> COMMON;
            case "service" -> SERVICE;
            default -> UNCLASSIFIED;
        };
    }
}
