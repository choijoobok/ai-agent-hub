// @feat FEAT-040
package com.aiagent.admin.managed;

import java.time.Instant;
import java.util.*;

/** 실제 업무의 입력/참조/현재값 소유권. 구현은 명령 잠금과 ADMIN 보호 안에서만 호출된다. */
public interface ManagedAssetAdapter {
    javax.sql.DataSource dataSource();
    record Current(String targetId,String revision) {}
    record Stamp(String commandKey,String actor,String revision,long historyNo,Instant at,String action,String reason) {}
    String feature();
    String kind();
    String readPath();
    String writePath();
    Set<String> scopes();
    Map<String,Object> normalize(ManagedCommand command);
    List<ManagedLocks.Key> locks(ManagedCommand command);
    Current current(ManagedCommand command);
    void validate(ManagedCommand command,Current before);
    /** 달력 경계를 소유한 업무는 잠금 뒤 공유 DB 시각을 반환한다. 일반 관리는 공통 시각을 쓴다. */
    default Instant acceptedAt(Instant fallback) { return fallback; }
    boolean changed(ManagedCommand command,Current before);
    /** 서버가 생성한 안정 ID를 반환한다. 기존 ID 재귀속·교체는 허용하지 않는다. */
    String apply(ManagedCommand command,Current before,Stamp stamp);
    void delete(ManagedCommand command,Current before);
    ManagedHistoryStore history();
}
