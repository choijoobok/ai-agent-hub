// @feat FEAT-002 FEAT-005
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.portal.PortalIdentityTargets;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.support.deployment.ExternalDataSources;
import com.aiagent.domain.authz.ActionType;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class DataSourceIdentityTargets implements PortalIdentityTargets {
    private final JdbcTemplate jdbc;private final ExternalDataSources loaded;private final ConfigurationDeploymentService deployment;private final DataSourceAccess access;
    public DataSourceIdentityTargets(DataSource source,ExternalDataSources loaded,ConfigurationDeploymentService deployment,DataSourceAccess access){jdbc=new JdbcTemplate(source);this.loaded=loaded;this.deployment=deployment;this.access=access;}
    public boolean supports(String kind){return kind.equals("DATA_SOURCE");}
    public Candidates list(String kind){if(!supports(kind))return new Candidates("NOT_CONNECTED",List.of());access.require(ActionType.READ);boolean ready=deployment.ready(loaded);
        return new Candidates(ready?"CONNECTED":"UNKNOWN",jdbc.query("SELECT DATA_SOURCE_ID,ALIAS_NM,DATA_SOURCE_KEY FROM AI_ADMIN_DATA_SOURCE ORDER BY DATA_SOURCE_ID",(rs,n)->{var e=loaded.entries().get(rs.getString(3));boolean available=ready&&e!=null&&e.status().equals("AVAILABLE");return new Target(rs.getString(1),rs.getString(2),available,available?null:ready?"UNAVAILABLE":"UNKNOWN");}));}
    public void requireWritable(String kind,String id){if(!supports(kind))throw ManagedErrorCode.VALIDATION.failure();access.require(ActionType.READ);deployment.requireReady(loaded);
        var keys=jdbc.queryForList("SELECT DATA_SOURCE_KEY FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,ManagedCommand.positive(id));if(keys.size()!=1||!loaded.entries().containsKey(keys.getFirst())||!loaded.entries().get(keys.getFirst()).status().equals("AVAILABLE"))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();}
    public List<ManagedLocks.Key> locks(String kind,List<String> ids){if(!supports(kind))throw ManagedErrorCode.VALIDATION.failure();var result=new ArrayList<ManagedLocks.Key>();result.add(ConfigurationDeploymentService.boundary(loaded.environment()));for(String id:ids){ManagedCommand.positive(id);result.add(new ManagedLocks.Key(3,"DATA_SOURCE",id));}return result;}
}
