// @feat FEAT-006
package com.aiagent.admin.group;

import java.util.*;

/** 원 소유자가 구현한다. 표시 조회에는 현재 ADMIN 열람권한, 참조 검사는 비활성/숨김 포함 전체를 적용한다. */
public interface UserGroupLinkSource {
    enum Source { membership, knowledge, catalog, api, tool, datasource }
    enum State { READY, NOT_CONNECTED, UNAVAILABLE, FORBIDDEN }
    enum Reference { PRESENT, ABSENT, UNKNOWN }
    record Projection(State state,List<Map<String,Object>> rows) {
        public Projection {
            Objects.requireNonNull(state); rows=rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
            if(state!=State.READY&&!rows.isEmpty())throw new IllegalArgumentException("확인되지 않은 연결 행은 공개할 수 없다");
        }
    }
    Source source();
    Projection read(long groupId);
    /** 화면 필터/조회 권한으로 참조를 숨겨서는 안 된다. */
    Reference references(long groupId);
}
