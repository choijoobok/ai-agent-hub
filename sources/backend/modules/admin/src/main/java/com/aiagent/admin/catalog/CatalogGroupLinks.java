// @feat FEAT-003 FEAT-006
package com.aiagent.admin.catalog;
import com.aiagent.admin.group.UserGroupLinkSource;
import com.aiagent.domain.authz.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public final class CatalogGroupLinks implements UserGroupLinkSource {
 private final JdbcTemplate jdbc;private final CatalogAccess access;
 public CatalogGroupLinks(DataSource db,CatalogAccess access){jdbc=new JdbcTemplate(db);this.access=access;}
 public Source source(){return Source.catalog;}
 public Projection read(long id){try{access.require(ActionType.READ);}catch(com.aiagent.core.error.BusinessException failure){return new Projection(State.FORBIDDEN,List.of());}return new Projection(State.READY,jdbc.queryForList("SELECT C.CATALOG_ID::text AS \"catalogId\",D.BUSINESS_NAME AS \"name\",G.ACTIVE_YN AS \"mappingActiveYn\" FROM AI_ADMIN_CATALOG_GROUP G JOIN AI_ADMIN_CATALOG C ON C.CATALOG_ID=G.CATALOG_ID JOIN AI_ADMIN_CATALOG_CONTENT D ON D.CATALOG_ID=C.CATALOG_ID WHERE G.GROUP_ID=? ORDER BY C.CATALOG_ID",id));}
 public Reference references(long id){return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM AI_ADMIN_CATALOG_GROUP WHERE GROUP_ID=?)+(SELECT COUNT(*) FROM AI_ADMIN_COLUMN_ACCESS_POLICY WHERE GROUP_ID=?)",Long.class,id,id)>0?Reference.PRESENT:Reference.ABSENT;}
}
