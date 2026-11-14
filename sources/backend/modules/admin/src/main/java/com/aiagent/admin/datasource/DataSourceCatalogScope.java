// @feat FEAT-002 FEAT-003
package com.aiagent.admin.datasource;
import java.util.*;
/** 현재 Portal 주체의 객체/컬럼/신원 정책을 소유 카탈로그가 검사하고 정확 DS ID를 돌려준다. */
public interface DataSourceCatalogScope {
    Map<String,String> authorizedDataSources(Set<String> selectedCatalogIds);
    void requireSqlSupport(String dataSourceId,String dbmsCode,String manualVersion);
}
