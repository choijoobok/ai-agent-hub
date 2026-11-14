// @feat FEAT-005
package com.aiagent.admin.portal;

import java.util.*;

/** 001/002 소유 생산자가 현재 후보와 기술 유효성을 제공한다. 실패를 빈 정상으로 반환하지 않는다. */
public interface PortalIdentityTargets {
    record Target(String targetId,String name,boolean available,String reason) {}
    record Candidates(String state,List<Target> rows) {public Candidates {rows=List.copyOf(rows);}}
    Candidates list(String kind);
    /** 화면 후보 페이지가 아니라 정확 ID의 현재 열람/변경/기술 유효성을 검사한다. */
    void requireWritable(String kind,String targetId);
    /** 신원 저장 전에 대상의 소유 경계를 함께 잠근다. 삭제/설정 전환과 참조 추가를 직렬화한다. */
    default List<com.aiagent.admin.managed.ManagedLocks.Key> locks(String kind,List<String> targetIds){return List.of();}
}
