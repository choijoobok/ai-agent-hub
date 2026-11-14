// @feat FEAT-002
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.support.deployment.ExternalDataSources;
import com.aiagent.support.time.BusinessCalendar;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DataSourceSyncService {
    private final JdbcTemplate jdbc;private final ManagedIds ids;private final ManagedLocks locks;private final BusinessCalendar clock;
    private final ConfigurationDeploymentService deployment;private final ConfigurationDiagnosisService diagnosis;private final DataSourceService owner;
    public DataSourceSyncService(DataSource source,ManagedIds ids,ManagedLocks locks,BusinessCalendar clock,ConfigurationDeploymentService deployment,ConfigurationDiagnosisService diagnosis,DataSourceService owner){jdbc=new JdbcTemplate(source);this.ids=ids;this.locks=locks;this.clock=clock;this.deployment=deployment;this.diagnosis=diagnosis;this.owner=owner;}
    @Transactional public void synchronize(ExternalDataSources loaded){
        deployment.requireWriter(loaded);if(deployment.ready(loaded))return;
        if(deployment.synchronizedBefore(loaded)){deployment.markReady(loaded);return;}
        var roots=jdbc.queryForList("SELECT * FROM AI_ADMIN_DATA_SOURCE ORDER BY DATA_SOURCE_ID");
        locks.ordered(roots.stream().map(r->new ManagedLocks.Key(3,"DATA_SOURCE",r.get("DATA_SOURCE_ID").toString())).toList());
        var byKey=new HashMap<String,Map<String,Object>>();for(var r:roots)byKey.put(r.get("DATA_SOURCE_KEY").toString(),r);
        var now=clock.now();var at=Timestamp.from(now);
        for(var entry:loaded.entries().values()){
            var old=byKey.get(entry.key());long id=old==null?ids.next("AI_ADMIN_DATA_SOURCE_ID"):((Number)old.get("DATA_SOURCE_ID")).longValue();
            boolean change=old==null||!Objects.equals(old.get("ALIAS_NM"),entry.alias())||!Objects.equals(old.get("DBMS_CD"),entry.dbmsCode())||!Objects.equals(old.get("SYNC_CONFIG_ID"),loaded.settingsId());
            if(!change)continue;long revision=old==null?1:Math.addExact(((Number)old.get("MANAGEMENT_VERSION")).longValue(),1);String command=UUID.randomUUID().toString();
            if(old==null)jdbc.update("INSERT INTO AI_ADMIN_DATA_SOURCE(DATA_SOURCE_ID,ALIAS_NM,DBMS_CD,SOURCE_TYPE_CD,MANAGEMENT_VERSION,REG_DTM,MOD_DTM,CHANGE_REASON,COMMAND_KEY,DATA_SOURCE_KEY,SYNC_CONFIG_ID,REG_SOURCE_CD,MOD_SOURCE_CD,ACCEPTED_DTM) VALUES (?,?,?,'DB',1,?,?,'설정 동기화',?,?,?,'CONFIG_SYNC','CONFIG_SYNC',?)",id,entry.alias(),entry.dbmsCode(),at,at,command,entry.key(),loaded.settingsId(),at);
            else jdbc.update("UPDATE AI_ADMIN_DATA_SOURCE SET ALIAS_NM=?,DBMS_CD=?,MANAGEMENT_VERSION=?,MOD_ID=NULL,MOD_DTM=?,MOD_SOURCE_CD='CONFIG_SYNC',CHANGE_REASON='설정 동기화',COMMAND_KEY=?,SYNC_CONFIG_ID=?,ACCEPTED_DTM=? WHERE DATA_SOURCE_ID=?",entry.alias(),entry.dbmsCode(),revision,at,command,loaded.settingsId(),at,id);
            owner.history().captureConfiguration(Long.toString(id),new ManagedAssetAdapter.Stamp(command,null,Long.toString(revision),ids.next("AI_ADMIN_HISTORY_NO"),now,old==null?"CREATE":"CHANGE","설정 동기화"));
        }
        for(var row:jdbc.queryForList("SELECT DATA_SOURCE_ID,DATA_SOURCE_KEY FROM AI_ADMIN_DATA_SOURCE ORDER BY DATA_SOURCE_ID")){
            String key=row.get("DATA_SOURCE_KEY").toString();diagnosis.record(loaded,"DATASOURCE",key,!loaded.entries().containsKey(key)?Boolean.TRUE:loaded.entries().get(key).status().equals("AVAILABLE")?Boolean.FALSE:null,row,at);
        }
        deployment.markReady(loaded);
    }
}
