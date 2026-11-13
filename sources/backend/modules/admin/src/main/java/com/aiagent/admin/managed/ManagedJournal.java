// @feat FEAT-040
package com.aiagent.admin.managed;

import java.time.Instant;
import java.util.*;

/** 설치된 소유 원장은 모두 명령 키 검사에 참여한다. 장애를 Optional.empty로 바꾸지 않는다. */
public interface ManagedJournal {
    javax.sql.DataSource dataSource();
    record Entry(String commandKey,String feature,String actor,String digest,String digestVersion,String kind,
                 String targetId,String revision,Long historyNo,String outcome,Instant appliedAt,Instant recordedAt) {}
    String name();
    Optional<Entry> find(String key);
    void insert(Entry entry);
    void delete(String key);
    boolean references(long historyNo);
}
