// @feat FEAT-002 FEAT-003
package com.aiagent.admin.datasource;
import java.util.*;
/** 카탈로그/실행 소유자가 현재 비활성·진행 참조까지 같은 부모 잠금 아래 검사한다. */
public interface DataSourceReferencePort {
    enum Reference {PRESENT,ABSENT,UNKNOWN}
    Set<String> tables();
    Reference references(String id);
    Reference groupReferences(String id,String group,boolean activeOnly);
    default List<Map<String,Object>> readableCatalogs(String id){return List.of();}
}
