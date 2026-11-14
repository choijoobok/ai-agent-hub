// @feat FEAT-002 FEAT-003
package com.aiagent.core.deployment;
import java.util.*;
/** 현재 허용 카탈로그에서만 도출하는 내부 논리 계약. 관리자 메뉴나 임의 DS 목록을 받지 않는다. */
public interface DataSourceMetadata {
    record Guide(String guideId,String title,String description){}
    record Context(String dataSourceId,String revision,String dbmsCode,String dbmsVersion,String sourceType,String timeZoneId,String dataBasis,List<Guide> guides){public Context{guides=List.copyOf(guides);}}
    List<Context> readSelected(Set<String> catalogIds);
    void requireSqlUsable(String catalogId);
}
