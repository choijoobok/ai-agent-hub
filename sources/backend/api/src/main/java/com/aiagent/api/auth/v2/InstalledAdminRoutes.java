// @feat FEAT-040
package com.aiagent.api.auth.v2;

import java.util.List;

/** 소유 모듈이 명시 선언한 정확 경로. 관측 handler에서 자동 생성하지 않는다. */
public record InstalledAdminRoutes(List<Route> routes) {
    public InstalledAdminRoutes { routes=List.copyOf(routes); }
    public record Route(String id,String method,String path,String handler,String action) {
        public Route {
            if(id==null||!id.matches("API-[0-9]+")||!"POST".equals(method)||path==null
                    ||!path.matches("/api/admin/service/[A-Za-z0-9]+/[A-Za-z0-9]+")
                    ||handler==null||!handler.contains("#")||!("READ".equals(action)||"SAVE".equals(action)))
                throw new IllegalArgumentException("정확한 관리자 소유 경로 선언이 필요하다");
        }
    }
}
