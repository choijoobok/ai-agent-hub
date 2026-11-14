// @feat FEAT-024
package com.aiagent.admin.searchtag;
import java.util.List;
/** 지식 소유 구현은 현재 읽기 권한을 투영에 적용하고 참조 존재는 권한 필터 없이 검사한다. */
public interface SearchTagReferences {
    enum Reference { PRESENT,ABSENT,UNKNOWN }
    enum State { READY,NOT_CONNECTED,UNAVAILABLE,FORBIDDEN }
    record Knowledge(String knowledgeId,String knowledgeNm,String revision,String activeYn) {}
    record Projection(State state,List<Knowledge> rows) { public Projection {rows=List.copyOf(rows);} }
    Reference references(String tagId);
    Projection read(String tagId);
}
