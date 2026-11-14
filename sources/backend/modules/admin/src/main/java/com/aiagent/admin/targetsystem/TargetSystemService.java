// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationCleanup;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.support.deployment.TargetConnections;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public class TargetSystemService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/targetSystem";
    static final String ROOT="AI_ADMIN_TARGET_SYSTEM",CHILD="AI_ADMIN_TARGET_SYSTEM_CONN";
    static final List<String> ROOT_COLUMNS=List.of("TARGET_SYSTEM_ID","SYSTEM_NM","DESCRIPTION","MANAGEMENT_VERSION","REG_ID","REG_DTM","MOD_ID","MOD_DTM","CHANGE_REASON","COMMAND_KEY","SYSTEM_KEY","YAML_SYSTEM_NM","YAML_DESCRIPTION","SYNC_CONFIG_ID","REG_SOURCE_CD","MOD_SOURCE_CD","ACCEPTED_DTM");
    static final List<String> CHILD_COLUMNS=List.of("TARGET_SYSTEM_ID","CONNECTION_ID","CONNECTION_NM","DESCRIPTION","YAML_CONNECTION_NM","YAML_DESCRIPTION","SYNC_CONFIG_ID","REG_ID","REG_DTM","MOD_ID","MOD_DTM","REG_SOURCE_CD","MOD_SOURCE_CD","CONNECTION_RECORD_ID");
    private final JdbcTemplate jdbc;private final TypedHistoryStore history;private final TargetConnections loaded;private final ConfigurationDeploymentService deployment;
    public TargetSystemService(DataSource source,ManagedIds ids,TargetConnections loaded,ConfigurationDeploymentService deployment) {
        jdbc=new JdbcTemplate(source);this.loaded=loaded;this.deployment=deployment;
        history=new TypedHistoryStore(source,ids,kind(),"MANAGEMENT_VERSION",List.of(new TypedHistoryStore.Table(ROOT,"TARGET_SYSTEM_ID",ROOT_COLUMNS),new TypedHistoryStore.Table(CHILD,"TARGET_SYSTEM_ID",CHILD_COLUMNS)),rows->true,this::historyReferences);
    }
    public DataSource dataSource(){return jdbc.getDataSource();}
    public String feature(){return "FEAT-001";}public String kind(){return "TARGET_SYSTEM";}
    public String readPath(){return BASE+"/getList";}public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("display","cleanup");}
    public List<ManagedLocks.Key> locks(ManagedCommand command){return List.of(ConfigurationDeploymentService.boundary(loaded.environment()));}
    public Map<String,Object> normalize(ManagedCommand command){if(command.scope().equals("cleanup"))return ConfigurationCleanup.normalizeFor(command,Set.of("SYSTEM","CONNECTION"));if(command.targetId()==null)throw ManagedErrorCode.VALIDATION.failure();return TargetSystemDefinition.parse(command.values()).values();}
    public Current current(ManagedCommand command){var rows=jdbc.query("SELECT TARGET_SYSTEM_ID,MANAGEMENT_VERSION FROM "+ROOT+" WHERE TARGET_SYSTEM_ID=?",(r,n)->new Current(r.getString(1),r.getString(2)),ManagedCommand.positive(command.targetId()));return rows.isEmpty()?null:rows.getFirst();}
    public void validate(ManagedCommand command,Current before) {
        deployment.requireReady(loaded);var value=TargetSystemDefinition.parse(command.values());
        if(before==null)throw ManagedErrorCode.VALIDATION.failure();
        var existing=new HashSet<>(jdbc.queryForList("SELECT CONNECTION_ID FROM "+CHILD+" WHERE TARGET_SYSTEM_ID=?",String.class,Long.valueOf(before.targetId())));
        if(!existing.equals(new HashSet<>(value.connections().stream().map(TargetSystemDefinition.Connection::id).toList())))throw ManagedErrorCode.VALIDATION.failure();
    }
    public boolean changed(ManagedCommand command,Current before) {
        var value=TargetSystemDefinition.parse(command.values());long id=Long.parseLong(before.targetId());var root=jdbc.queryForMap("SELECT SYSTEM_NM,DESCRIPTION FROM "+ROOT+" WHERE TARGET_SYSTEM_ID=?",id);
        var children=jdbc.query("SELECT CONNECTION_ID,CONNECTION_NM,DESCRIPTION FROM "+CHILD+" WHERE TARGET_SYSTEM_ID=? ORDER BY CONNECTION_ID",(r,n)->new TargetSystemDefinition.Connection(r.getString(1),r.getString(2),r.getString(3)),id);
        return !Objects.equals(value.name(),root.get("SYSTEM_NM"))||!Objects.equals(value.description(),root.get("DESCRIPTION"))||!value.connections().equals(children);
    }
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        long id=Long.parseLong(before.targetId());var value=TargetSystemDefinition.parse(command.values());var at=Timestamp.from(stamp.at());
        jdbc.update("UPDATE "+ROOT+" SET SYSTEM_NM=?,DESCRIPTION=?,MANAGEMENT_VERSION=?,MOD_ID=?,MOD_DTM=?,MOD_SOURCE_CD='ADMIN',CHANGE_REASON=?,COMMAND_KEY=?,ACCEPTED_DTM=? WHERE TARGET_SYSTEM_ID=?",value.name(),value.description(),Long.valueOf(stamp.revision()),stamp.actor(),at,stamp.reason(),stamp.commandKey(),at,id);
        for(var child:value.connections())jdbc.update("UPDATE "+CHILD+" SET CONNECTION_NM=?,DESCRIPTION=?,MOD_ID=?,MOD_DTM=?,MOD_SOURCE_CD='ADMIN' WHERE TARGET_SYSTEM_ID=? AND CONNECTION_ID=? AND (CONNECTION_NM IS DISTINCT FROM ? OR DESCRIPTION IS DISTINCT FROM ?)",child.name(),child.description(),stamp.actor(),at,id,child.id(),child.name(),child.description());
        return before.targetId();
    }
    public void delete(ManagedCommand command,Current before){throw ManagedErrorCode.VALIDATION.failure();}
    public TypedHistoryStore history(){return history;}
    private List<ManagedHistoryStore.Ref> historyReferences(Map<String,List<Map<String,Object>>> rows){
        var root=rows.get(ROOT).getFirst();
        if(!"CONFIGURATION_CLEANUP".equals(root.get("HIST_SUMMARY")))return List.of();
        var previous=jdbc.queryForList("SELECT TARGET_SYSTEM_ID,RESULT_REVISION FROM AI_ADMIN_TARGET_SYSTEM_HIST WHERE HISTORY_NO=? AND HIST_ACTION<>'DELETE'",root.get("BEFORE_HISTORY_NO"));
        if(previous.size()!=1)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        return List.of(new ManagedHistoryStore.Ref(kind(),previous.getFirst().get("TARGET_SYSTEM_ID").toString(),previous.getFirst().get("RESULT_REVISION").toString()));
    }
}
