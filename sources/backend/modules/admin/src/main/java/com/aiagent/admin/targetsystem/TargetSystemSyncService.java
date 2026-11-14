// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.ConfigurationDiagnosisService;
import com.aiagent.support.deployment.TargetConnections;
import com.aiagent.support.time.BusinessCalendar;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전체 로드 검증 뒤 현재 메타 작성 기동만 동기화한다. 누락 행과 수동 재정의를 보존한다. */
@Service
public class TargetSystemSyncService {
    private final JdbcTemplate jdbc;private final ManagedIds ids;private final ManagedLocks locks;private final BusinessCalendar clock;
    private final ConfigurationDeploymentService deployment;private final TargetSystemService owner;private final ConfigurationDiagnosisService diagnoses;
    public TargetSystemSyncService(DataSource source,ManagedIds ids,ManagedLocks locks,BusinessCalendar clock,ConfigurationDeploymentService deployment,TargetSystemService owner,ConfigurationDiagnosisService diagnoses){jdbc=new JdbcTemplate(source);this.ids=ids;this.locks=locks;this.clock=clock;this.deployment=deployment;this.owner=owner;this.diagnoses=diagnoses;}
    @Transactional public void synchronize(TargetConnections loaded) {
        deployment.requireWriter(loaded);
        if(deployment.ready(loaded))return;
        // 한 번 확정된 세대는 저장 판정을 재사용한다. 재기동이 구 메타를 덮거나 정리 행을 부활시키지 않는다.
        if(deployment.synchronizedBefore(loaded)){deployment.markReady(loaded);return;}
        var roots=jdbc.queryForList("SELECT * FROM AI_ADMIN_TARGET_SYSTEM ORDER BY TARGET_SYSTEM_ID");
        locks.ordered(roots.stream().map(r->new ManagedLocks.Key(3,"TARGET_SYSTEM",r.get("TARGET_SYSTEM_ID").toString())).toList());
        var byKey=new HashMap<String,Map<String,Object>>();for(var row:roots)byKey.put(row.get("SYSTEM_KEY").toString(),row);
        var now=clock.now();var at=Timestamp.from(now);
        for(var definition:loaded.systems().values()) {
            var previous=byKey.get(definition.key());long id=previous==null?ids.next("AI_ADMIN_TARGET_ID"):((Number)previous.get("TARGET_SYSTEM_ID")).longValue();
            long revision=previous==null?1:((Number)previous.get("MANAGEMENT_VERSION")).longValue();
            boolean changed=previous==null||!Objects.equals(previous.get("YAML_SYSTEM_NM"),definition.name())||!Objects.equals(previous.get("YAML_DESCRIPTION"),definition.description());
            var children=jdbc.queryForList("SELECT * FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=?",id);
            var childByKey=new HashMap<String,Map<String,Object>>();for(var child:children)childByKey.put(child.get("CONNECTION_ID").toString(),child);
            for(var connection:definition.connections().values()) {
                var old=childByKey.get(connection.id());if(old==null||!Objects.equals(old.get("YAML_CONNECTION_NM"),connection.name())||!Objects.equals(old.get("YAML_DESCRIPTION"),connection.description()))changed=true;
            }
            String command=UUID.randomUUID().toString();
            if(previous==null)jdbc.update("INSERT INTO AI_ADMIN_TARGET_SYSTEM(TARGET_SYSTEM_ID,MANAGEMENT_VERSION,REG_DTM,MOD_DTM,CHANGE_REASON,COMMAND_KEY,SYSTEM_KEY,YAML_SYSTEM_NM,YAML_DESCRIPTION,SYNC_CONFIG_ID,REG_SOURCE_CD,MOD_SOURCE_CD,ACCEPTED_DTM) VALUES (?,1,?,?,'설정 동기화',?,?,?,?,?,'CONFIG_SYNC','CONFIG_SYNC',?)",id,at,at,command,definition.key(),definition.name(),definition.description(),loaded.settingsId(),at);
            else if(changed){revision=Math.addExact(revision,1);jdbc.update("UPDATE AI_ADMIN_TARGET_SYSTEM SET MANAGEMENT_VERSION=?,MOD_ID=NULL,MOD_DTM=?,MOD_SOURCE_CD='CONFIG_SYNC',CHANGE_REASON='설정 동기화',COMMAND_KEY=?,YAML_SYSTEM_NM=?,YAML_DESCRIPTION=?,SYNC_CONFIG_ID=?,ACCEPTED_DTM=? WHERE TARGET_SYSTEM_ID=?",revision,at,command,definition.name(),definition.description(),loaded.settingsId(),at,id);}
            for(var connection:definition.connections().values()) {
                var old=childByKey.get(connection.id());
                if(old==null)jdbc.update("INSERT INTO AI_ADMIN_TARGET_SYSTEM_CONN(TARGET_SYSTEM_ID,CONNECTION_ID,YAML_CONNECTION_NM,YAML_DESCRIPTION,SYNC_CONFIG_ID,REG_DTM,MOD_DTM,REG_SOURCE_CD,MOD_SOURCE_CD,CONNECTION_RECORD_ID) VALUES (?,?,?,?,?,?,?,'CONFIG_SYNC','CONFIG_SYNC',?)",id,connection.id(),connection.name(),connection.description(),loaded.settingsId(),at,at,ids.next("AI_ADMIN_TARGET_CONNECTION_ID"));
                else if(!Objects.equals(old.get("YAML_CONNECTION_NM"),connection.name())||!Objects.equals(old.get("YAML_DESCRIPTION"),connection.description()))jdbc.update("UPDATE AI_ADMIN_TARGET_SYSTEM_CONN SET YAML_CONNECTION_NM=?,YAML_DESCRIPTION=?,SYNC_CONFIG_ID=?,MOD_ID=NULL,MOD_DTM=?,MOD_SOURCE_CD='CONFIG_SYNC' WHERE CONNECTION_RECORD_ID=?",connection.name(),connection.description(),loaded.settingsId(),at,old.get("CONNECTION_RECORD_ID"));
            }
            if(changed)owner.history().captureConfiguration(Long.toString(id),new ManagedAssetAdapter.Stamp(command,null,Long.toString(revision),ids.next("AI_ADMIN_HISTORY_NO"),now,previous==null?"CREATE":"CHANGE","설정 동기화"));
        }
        for(var root:jdbc.queryForList("SELECT * FROM AI_ADMIN_TARGET_SYSTEM ORDER BY TARGET_SYSTEM_ID")) {
            String key=root.get("SYSTEM_KEY").toString();var present=loaded.systems().get(key);
            diagnosis(loaded,"SYSTEM",key,present==null,"SYSTEM",root,at);
            for(var child:jdbc.queryForList("SELECT * FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=? ORDER BY CONNECTION_ID",root.get("TARGET_SYSTEM_ID"))) {
                String connection=child.get("CONNECTION_ID").toString();diagnosis(loaded,"CONNECTION",key+"/"+connection,present==null||!present.connections().containsKey(connection),"CONNECTION",child,at);
            }
        }
        deployment.markReady(loaded);
    }
    private void diagnosis(TargetConnections loaded,String kind,String key,boolean missing,String basisKind,Map<String,Object> row,Timestamp at){
        var basis=new TreeMap<String,Object>();basis.put("kind",basisKind);basis.put("key",key);
        for(String name:List.of("TARGET_SYSTEM_ID","CONNECTION_RECORD_ID","SYSTEM_KEY","CONNECTION_ID","YAML_SYSTEM_NM","YAML_CONNECTION_NM","YAML_DESCRIPTION"))if(row.containsKey(name))basis.put(name,row.get(name));
        diagnoses.record(loaded,kind,key,missing,basis,at);
    }
}
