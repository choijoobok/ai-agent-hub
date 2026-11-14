// @feat FEAT-001 FEAT-005
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.portal.PortalIdentityTargets;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.support.deployment.TargetConnections;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TargetIdentityTargets implements PortalIdentityTargets {
    private final JdbcTemplate jdbc;
    private final TargetConnections loaded;
    private final TargetDeploymentService deployment;
    private final TargetSystemAccess access;
    public TargetIdentityTargets(DataSource source,TargetConnections loaded,TargetDeploymentService deployment,TargetSystemAccess access){jdbc=new JdbcTemplate(source);this.loaded=loaded;this.deployment=deployment;this.access=access;}
    public Candidates list(String kind){
        if(!kind.equals("SYSTEM"))return new Candidates("NOT_CONNECTED",List.of());
        access.require(ActionType.READ);
        boolean ready=deployment.ready(loaded);
        var rows=jdbc.query("SELECT TARGET_SYSTEM_ID,SYSTEM_KEY,SYSTEM_NM,YAML_SYSTEM_NM FROM AI_ADMIN_TARGET_SYSTEM ORDER BY TARGET_SYSTEM_ID",(r,n)->{
            boolean available=ready&&loaded.systems().containsKey(r.getString(2));
            return new Target(r.getString(1),TargetSystemQueryService.display(r.getString(3),r.getString(4),r.getString(2)),available,available?null:ready?"MISSING":"UNKNOWN");
        });
        return new Candidates(ready?"CONNECTED":"UNKNOWN",rows);
    }
    public void requireWritable(String kind,String targetId){
        if(!kind.equals("SYSTEM"))throw ManagedErrorCode.UNAVAILABLE.failure();
        access.require(ActionType.READ);deployment.requireReady(loaded);
        var keys=jdbc.queryForList("SELECT SYSTEM_KEY FROM AI_ADMIN_TARGET_SYSTEM WHERE TARGET_SYSTEM_ID=?",String.class,ManagedCommand.positive(targetId));
        if(keys.size()!=1||!loaded.systems().containsKey(keys.getFirst()))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
    }
    public List<ManagedLocks.Key> locks(String kind,List<String> targets){
        if(!kind.equals("SYSTEM"))throw ManagedErrorCode.UNAVAILABLE.failure();
        var keys=new ArrayList<ManagedLocks.Key>();keys.add(TargetDeploymentService.boundary(loaded.environment()));
        for(String id:targets){ManagedCommand.positive(id);keys.add(new ManagedLocks.Key(3,"TARGET_SYSTEM",id));}
        return keys;
    }
}
