// @feat FEAT-003 FEAT-002
package com.aiagent.admin.catalog;
import com.aiagent.admin.datasource.DataSourceReferencePort;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public final class CatalogDataSourceReferences implements DataSourceReferencePort {
 private final JdbcTemplate jdbc;private final CatalogAccess access;
 public CatalogDataSourceReferences(DataSource db,CatalogAccess access){jdbc=new JdbcTemplate(db);this.access=access;}
 public Set<String> tables(){return Set.of("AI_ADMIN_CATALOG","AI_ADMIN_CATALOG_HIST");}
 public Reference references(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CATALOG WHERE DATA_SOURCE_ID=?",Long.class,Long.valueOf(id))>0?Reference.PRESENT:Reference.ABSENT;}
 public Reference groupReferences(String id,String group,boolean activeOnly){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CATALOG C JOIN AI_ADMIN_CATALOG_GROUP G ON C.CATALOG_ID=G.CATALOG_ID WHERE C.DATA_SOURCE_ID=? AND G.GROUP_ID=?"+(activeOnly?" AND G.ACTIVE_YN='Y'":""),Long.class,Long.valueOf(id),Long.valueOf(group))>0?Reference.PRESENT:Reference.ABSENT;}
 public List<Map<String,Object>> readableCatalogs(String id){try{access.require(com.aiagent.domain.authz.ActionType.READ);}catch(com.aiagent.core.error.BusinessException failure){throw new org.springframework.dao.DataAccessResourceFailureException("카탈로그 열람 권한을 확인할 수 없습니다.");}return jdbc.queryForList("SELECT C.CATALOG_ID::text AS \"catalogId\",D.BUSINESS_NAME AS \"name\" FROM AI_ADMIN_CATALOG C JOIN AI_ADMIN_CATALOG_CONTENT D ON D.CATALOG_ID=C.CATALOG_ID WHERE C.DATA_SOURCE_ID=? ORDER BY C.CATALOG_ID",Long.valueOf(id));}
}
