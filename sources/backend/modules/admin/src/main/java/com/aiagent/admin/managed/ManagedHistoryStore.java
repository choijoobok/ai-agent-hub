// @feat FEAT-040
package com.aiagent.admin.managed;

import java.time.Instant;
import java.util.*;

/** 원본별 동형 HIST 어댑터. 중첩 소유 관계도 소유 전체 세트로 읽고 쓴다. */
public interface ManagedHistoryStore {
    javax.sql.DataSource dataSource();
    record Ref(String kind,String targetId,String revision) {
        public Ref { Objects.requireNonNull(kind); ManagedTargetKeys.wire(targetId); ManagedCommand.positive(revision); }
    }
    record Event(Ref ref,long historyNo,Instant at,String action,Map<String,List<Map<String,Object>>> rows,List<Ref> references) {
        public Event { rows=Map.copyOf(rows); references=List.copyOf(references); }
    }
    String kind();
    void capture(String targetId,ManagedAssetAdapter.Stamp stamp);
    default void captureExternal(String targetId,ManagedAssetAdapter.Stamp stamp){throw new IllegalStateException("외부 원천 이력은 소유 지원이 필요하다");}
    Event exact(Ref ref);
    Event event(long historyNo);
    boolean current(Event event);
    void purge(Event event);
    /** 현재 적용판 변경과 파기가 공유해야 하는 소유 부모 잠금. */
    List<ManagedLocks.Key> locks(Ref ref);
}
