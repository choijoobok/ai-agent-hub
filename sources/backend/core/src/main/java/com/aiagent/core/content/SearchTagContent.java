// @feat FEAT-024
package com.aiagent.core.content;
import java.util.Collection;
import java.util.List;
/** 호출 소유자가 현재 접근 허용한 태그 ID만 전달한다. 실제 지식 검색·접근 권한을 제공하지 않는다. */
public interface SearchTagContent {
    record Use(String module,String kind,String key,String purpose) {}
    record Synonym(String synonymId,String searchText,String normalizedText) {}
    record Document(String tagId,String revision,String historyNo,String normalizationVersion,String searchText,String normalizedText,String useYn,List<Synonym> synonyms) {}
    List<Document> current(Use use,Collection<String> permittedTagIds);
    Document exact(Use use,String tagId,String revision);
}
