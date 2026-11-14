// @feat FEAT-040
package com.aiagent.admin.managed;

/** 별도 원장을 소유한 기능의 결과 조회도 명령 잠금 → 현재 인가 순서를 공유한다. */
public record ManagedResultRoute(String id,String path,String handler,String feature) {
    public ManagedResultRoute {
        if(id==null||!id.matches("API-[0-9]+")||path==null||!path.matches("/api/admin/service/[A-Za-z0-9]+/getCommandResult")
            ||handler==null||!handler.contains("#")||feature==null||!feature.matches("FEAT-[0-9]+"))throw new IllegalArgumentException("관리 결과 조회의 정확 경로가 필요하다");
    }
}
