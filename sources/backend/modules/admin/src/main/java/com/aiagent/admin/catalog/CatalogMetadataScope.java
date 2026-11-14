// @feat FEAT-003 FEAT-002 FEAT-303
package com.aiagent.admin.catalog;
import com.aiagent.admin.datasource.DataSourceCatalogScope;
import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.*;
import com.aiagent.core.access.*;
import com.aiagent.core.access.GroupAccess.*;
import com.aiagent.core.security.AuthDomain;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** 내부 가이드의 입력은 현재 Portal 선택 카탈로그다. 관리자 메뉴 권한이나 같은 문자열 ID로 대체하지 않는다. */
@Component
public final class CatalogMetadataScope implements DataSourceCatalogScope {
 private final HttpServletRequest request;private final CurrentDataAccess access;private final JdbcTemplate jdbc;
 public CatalogMetadataScope(HttpServletRequest request,CurrentDataAccess access,DataSource db){this.request=request;this.access=access;jdbc=new JdbcTemplate(db);}
 public Map<String,String> authorizedDataSources(Set<String> ids){
  if(!(request.getAttribute(SessionV2Filter.SCOPE) instanceof SessionRuntime.Scope scope))throw ManagedErrorCode.FORBIDDEN.failure();
  var identity=scope.current().orElseThrow(ManagedErrorCode.FORBIDDEN::failure).identity();if(identity==null||identity.principal().domain()!=AuthDomain.PORTAL)throw ManagedErrorCode.FORBIDDEN.failure();
  var result=new LinkedHashMap<String,String>();for(String id:new TreeSet<>(ids)){ManagedCommand.positive(id);var rows=jdbc.queryForList("SELECT DATA_SOURCE_ID,REVISION FROM AI_ADMIN_CATALOG WHERE CATALOG_ID=? AND USE_YN='Y'",Long.valueOf(id));if(rows.size()!=1)throw ManagedErrorCode.FORBIDDEN.failure();var row=rows.getFirst();var checked=access.withCurrentAccess(new Request(identity.principal(),Purpose.MODEL_INPUT,new Target("CATALOG",id,row.get("REVISION").toString()),Set.of(),UUID.randomUUID().toString()),use->row.get("DATA_SOURCE_ID").toString());if(checked.status()!=CurrentDataAccess.OutcomeStatus.COMPLETED)throw (checked.status()==CurrentDataAccess.OutcomeStatus.DENIED?ManagedErrorCode.FORBIDDEN:ManagedErrorCode.UNAVAILABLE).failure();result.put(id,checked.value());}return result;
 }
 public void requireSqlSupport(String id,String dbms,String version){throw ManagedErrorCode.UNAVAILABLE.failure();}
}
