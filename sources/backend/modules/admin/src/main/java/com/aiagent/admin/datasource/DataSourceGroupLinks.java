// @feat FEAT-002 FEAT-006
package com.aiagent.admin.datasource;
import com.aiagent.admin.group.UserGroupLinkSource;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public final class DataSourceGroupLinks implements UserGroupLinkSource {
    private final JdbcTemplate jdbc;private final HttpServletRequest request;private final EffectiveAuthorityService authorities;
    public DataSourceGroupLinks(DataSource source,HttpServletRequest request,EffectiveAuthorityService authorities){jdbc=new JdbcTemplate(source);this.request=request;this.authorities=authorities;}
    public Source source(){return Source.datasource;}
    public Projection read(long id){var actor=request.getUserPrincipal();if(actor==null||AdminAuthorityProtection.currentToken()==null||!authorities.calculate(actor.getName()).allows("HUB_DATA_SOURCE",ActionType.READ))return new Projection(State.FORBIDDEN,List.of());
        return new Projection(State.READY,jdbc.queryForList("SELECT CAST(D.DATA_SOURCE_ID AS varchar) AS \"dataSourceId\",D.ALIAS_NM AS \"alias\",G.ACTIVE_YN AS \"mappingActiveYn\" FROM AI_ADMIN_DATA_SOURCE_GROUP G JOIN AI_ADMIN_DATA_SOURCE D ON D.DATA_SOURCE_ID=G.DATA_SOURCE_ID WHERE G.GROUP_ID=? ORDER BY D.DATA_SOURCE_ID",id));}
    public Reference references(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE_GROUP WHERE GROUP_ID=?",Long.class,id)>0?Reference.PRESENT:Reference.ABSENT;}
}
