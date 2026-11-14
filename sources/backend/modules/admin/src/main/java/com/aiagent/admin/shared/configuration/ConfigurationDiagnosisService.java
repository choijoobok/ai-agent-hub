// @feat FEAT-001 FEAT-002
package com.aiagent.admin.shared.configuration;
import com.aiagent.admin.managed.*;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 비밀 없는 기동 판정과 원인 경고를 같은 트랜잭션에 기록한다. */
@Service
public class ConfigurationDiagnosisService {
    private final JdbcTemplate jdbc;private final ManagedIds ids;
    public ConfigurationDiagnosisService(DataSource source,ManagedIds ids){jdbc=new JdbcTemplate(source);this.ids=ids;}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(com.aiagent.core.deployment.ConfigurationSnapshot loaded,String kind,String key,Boolean missing,Map<String,Object> basis,Timestamp at) {
        var existing=jdbc.queryForList("SELECT DIAGNOSIS_ID FROM AI_ADMIN_CONFIG_DIAGNOSIS WHERE ENVIRONMENT_ID=? AND CONFIG_ID=? AND TARGET_KIND=? AND TARGET_KEY=?",Long.class,loaded.environment(),loaded.settingsId(),kind,key);
        if(existing.size()>1)throw ManagedErrorCode.UNAVAILABLE.failure();
        if(existing.isEmpty()) {
            String serialized=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(basis);
            jdbc.update("INSERT INTO AI_ADMIN_CONFIG_DIAGNOSIS(DIAGNOSIS_ID,ENVIRONMENT_ID,CONFIG_ID,TARGET_KIND,TARGET_KEY,MISMATCH_YN,SAFE_REASON,COMPARISON_BASIS,RULE_VERSION,CHECKED_DTM,REG_DTM,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",ids.next("AI_ADMIN_CONFIG_ID"),loaded.environment(),loaded.settingsId(),kind,key,missing==null?null:missing?"Y":"N",missing==null?"UNKNOWN":missing?"MISSING":"MATCHED",serialized,"asset-settings/v1",at,at,at);
        }
        var alerts=jdbc.queryForList("SELECT ALERT_ID,STATUS_CD FROM AI_ADMIN_CONFIG_ALERT WHERE ENVIRONMENT_ID=? AND CONFIG_ID=? AND TARGET_KIND=? AND TARGET_KEY=?",loaded.environment(),loaded.settingsId(),kind,key);
        if(missing==null&&alerts.isEmpty()){
            long alert=ids.next("AI_ADMIN_CONFIG_ID");jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT(ALERT_ID,ENVIRONMENT_ID,TARGET_KIND,TARGET_KEY,CONFIG_ID,STATUS_CD,FIRST_SEEN_DTM,LAST_CHECKED_DTM,COVERAGE_CD,REG_DTM,MOD_DTM) VALUES (?,?,?,?,?,'UNKNOWN',?,?,'PARTIAL',?,?)",alert,loaded.environment(),kind,key,loaded.settingsId(),at,at,at,at);
            jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT_EVENT(EVENT_ID,ALERT_ID,EVENT_CD,CONFIG_ID,EVIDENCE,REG_DTM) VALUES (?,?,'UNKNOWN',?,'CONNECTION_UNCONFIRMED',?)",ids.next("AI_ADMIN_CONFIG_ID"),alert,loaded.settingsId(),at);
        }
        if(Boolean.TRUE.equals(missing)&&alerts.isEmpty()) {
            long alert=ids.next("AI_ADMIN_CONFIG_ID");jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT(ALERT_ID,ENVIRONMENT_ID,TARGET_KIND,TARGET_KEY,CONFIG_ID,STATUS_CD,FIRST_SEEN_DTM,LAST_CHECKED_DTM,COVERAGE_CD,REG_DTM,MOD_DTM) VALUES (?,?,?,?,?,'OPEN',?,?,'PARTIAL',?,?)",alert,loaded.environment(),kind,key,loaded.settingsId(),at,at,at,at);
            jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT_EVENT(EVENT_ID,ALERT_ID,EVENT_CD,CONFIG_ID,EVIDENCE,REG_DTM) VALUES (?,?,'OPEN',?,'MISSING',?)",ids.next("AI_ADMIN_CONFIG_ID"),alert,loaded.settingsId(),at);
        }else if(Boolean.FALSE.equals(missing)&&alerts.isEmpty()){
            var previous=jdbc.queryForList("SELECT ALERT_ID FROM AI_ADMIN_CONFIG_ALERT WHERE ENVIRONMENT_ID=? AND TARGET_KIND=? AND TARGET_KEY=? AND CONFIG_ID<>? AND STATUS_CD IN ('OPEN','UNKNOWN') ORDER BY LAST_CHECKED_DTM DESC LIMIT 1",Long.class,loaded.environment(),kind,key,loaded.settingsId());
            if(!previous.isEmpty()){
                long alert=ids.next("AI_ADMIN_CONFIG_ID");
                jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT(ALERT_ID,ENVIRONMENT_ID,TARGET_KIND,TARGET_KEY,CONFIG_ID,STATUS_CD,FIRST_SEEN_DTM,LAST_CHECKED_DTM,RECOVERED_DTM,COVERAGE_CD,REG_DTM,MOD_DTM) VALUES (?,?,?,?,?,'RECOVERED',?,?,?,'PARTIAL',?,?)",alert,loaded.environment(),kind,key,loaded.settingsId(),at,at,at,at,at);
                jdbc.update("INSERT INTO AI_ADMIN_CONFIG_ALERT_EVENT(EVENT_ID,ALERT_ID,PREVIOUS_ALERT_ID,EVENT_CD,CONFIG_ID,EVIDENCE,REG_DTM) VALUES (?,?,?,'RECOVERED',?,'VERIFIED_MATCH',?)",ids.next("AI_ADMIN_CONFIG_ID"),alert,previous.getFirst(),loaded.settingsId(),at);
            }
        }
    }
}
