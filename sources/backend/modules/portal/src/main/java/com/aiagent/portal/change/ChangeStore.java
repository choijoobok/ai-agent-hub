// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.security.portal.PortalChangeOwner.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Portal 수신/순서/통지 참조만 소유한다. 원천 증명·전체 본문·subject 원값을 저장하지 않는다. */
@Component
public class ChangeStore {
    public record Receipt(String receiptId,String sourceEventId,String outcome,String safeCode,Instant appliedAt,String notificationState,boolean duplicate) {}
    public record Stored(Receipt receipt,String commandKey,String eventDigest,String businessDigest,String sourceKey,String epoch,Kind kind,String portalUserId,String revision,Long historyNo,UUID bindingKey,Instant receivedAt) {}
    private final DataSource source;private final JdbcTemplate jdbc;
    private final ThreadLocal<String> ending=new ThreadLocal<>();
    public ChangeStore(DataSource source){this.source=source;jdbc=new JdbcTemplate(source);}
    public DataSource dataSource(){return source;}
    public Instant now(){return jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();}
    private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("수신 원장에는 같은 DB 트랜잭션이 필요하다");}
    public void lock(String eventDigest){transaction();jdbc.queryForObject("SELECT pg_advisory_xact_lock(?,?)",Object.class,1296519736,(int)Long.parseLong(eventDigest.substring(0,8),16));}
    public long next(String table){transaction();if(!Set.of("AI_PORTAL_USER_CHANGE","AI_PORTAL_SOURCE_CHANGE_CURSOR","AI_PORTAL_CHANGE_NOTIFICATION").contains(table))throw new ChangeFailure(503,"STORE_UNAVAILABLE");return jdbc.queryForObject("UPDATE SYS_SEQUENCE SET NEXT_VAL=NEXT_VAL+1 WHERE SEQ_NAME=? RETURNING NEXT_VAL-1",Long.class,table);}
    private static String string(Map<String,Object> row,String key){return row.get(key)==null?null:row.get(key).toString();}
    private static Instant instant(Map<String,Object> row,String key){return row.get(key)==null?null:((Timestamp)row.get(key)).toInstant();}
    private Stored stored(Map<String,Object> row) {
        var receipt=new Receipt(string(row,"RECEIPT_ID"),string(row,"SOURCE_EVENT_ID"),string(row,"OUTCOME_CD"),string(row,"SAFE_CODE"),instant(row,"APPLIED_DTM"),string(row,"NOTIFICATION_STATE"),false);
        return new Stored(receipt,string(row,"COMMAND_KEY"),string(row,"EVENT_KEY_DIGEST"),string(row,"REQUEST_DIGEST"),string(row,"SOURCE_KEY"),string(row,"SOURCE_EPOCH"),Kind.valueOf(string(row,"EVENT_KIND")),string(row,"PORTAL_USER_ID"),string(row,"RESULT_REVISION"),(Long)row.get("HISTORY_NO"),(UUID)row.get("BINDING_KEY"),instant(row,"RECEIVED_DTM"));
    }
    public Stored findEvent(String digest){var rows=jdbc.queryForList("SELECT * FROM AI_PORTAL_USER_CHANGE WHERE EVENT_KEY_DIGEST=?",digest);return rows.isEmpty()?null:stored(rows.getFirst());}
    public Stored findReceipt(String id){var rows=jdbc.queryForList("SELECT * FROM AI_PORTAL_USER_CHANGE WHERE RECEIPT_ID=?",Long.parseLong(id));return rows.isEmpty()?null:stored(rows.getFirst());}
    public JournalEntry findCommand(String key){var rows=jdbc.queryForList("SELECT * FROM AI_PORTAL_USER_CHANGE WHERE COMMAND_KEY=?",UUID.fromString(key));if(rows.isEmpty())return null;var row=stored(rows.getFirst());return new JournalEntry(row.commandKey(),row.businessDigest(),row.portalUserId(),row.revision(),row.historyNo(),row.receipt().outcome(),row.receipt().appliedAt(),row.receivedAt());}
    public boolean references(long history){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_PORTAL_USER_CHANGE WHERE HISTORY_NO=?",Long.class,history)>0;}
    public SourceChangeVerifier.Cursor cursor(SourceChangeVerifier.Configuration config,String subjectDigest,UUID binding) {
        if(binding==null)return new SourceChangeVerifier.Cursor(0,null,null);
        var rows=jdbc.queryForList("SELECT VERSION,SOURCE_VERSION,ORDER_MODE FROM AI_PORTAL_SOURCE_CHANGE_CURSOR WHERE SOURCE_KEY=? AND SOURCE_EPOCH=? AND SUBJECT_DIGEST=? AND BINDING_KEY=?",config.sourceKey(),config.epoch(),subjectDigest,binding);
        if(rows.isEmpty())return new SourceChangeVerifier.Cursor(0,null,null);var row=rows.getFirst();
        return new SourceChangeVerifier.Cursor(((Number)row.get("VERSION")).longValue(),string(row,"SOURCE_VERSION"),string(row,"ORDER_MODE"));
    }
    public void advance(SourceChangeVerifier.Configuration config,String subjectDigest,SourceChangeVerifier.Freshness freshness,long receipt,Instant at) {
        transaction();
        if(freshness.previousCursorVersion()==0)jdbc.update("INSERT INTO AI_PORTAL_SOURCE_CHANGE_CURSOR VALUES (?,?,?,?,?,?,?,1,?,?)",next("AI_PORTAL_SOURCE_CHANGE_CURSOR"),config.sourceKey(),config.epoch(),subjectDigest,freshness.bindingKey(),freshness.orderMode(),freshness.sourceVersion(),Timestamp.from(at),receipt);
        else if(jdbc.update("UPDATE AI_PORTAL_SOURCE_CHANGE_CURSOR SET SOURCE_VERSION=?,ORDER_MODE=?,VERSION=VERSION+1,VERIFIED_DTM=?,LAST_RECEIPT_ID=? WHERE SOURCE_KEY=? AND SOURCE_EPOCH=? AND SUBJECT_DIGEST=? AND BINDING_KEY=? AND VERSION=?",freshness.sourceVersion(),freshness.orderMode(),Timestamp.from(at),receipt,config.sourceKey(),config.epoch(),subjectDigest,freshness.bindingKey(),freshness.previousCursorVersion())!=1)throw new ChangeFailure(409,"CURSOR_CHANGED");
    }
    public Stored insert(long id,String command,SourceChangeVerifier.Configuration config,ExternalUserChange event,SourceChangeVerifier.Envelope envelope,SourceChangeVerifier.Freshness freshness,Applied applied,String safeCode,Instant now) {
        transaction();var v=new LinkedHashMap<String,Object>();
        v.put("RECEIPT_ID",id);v.put("COMMAND_KEY",UUID.fromString(command));v.put("SOURCE_KEY",config.sourceKey());v.put("SOURCE_EVENT_ID",event.eventId());v.put("REQUEST_DIGEST",event.businessDigest());v.put("DIGEST_VERSION","change-body/v1");v.put("SUBJECT_DIGEST",ExternalUserChange.digest("subject/v1",config.stableNamespace(),event.subject()));v.put("EVENT_KIND",event.kind().name());v.put("SOURCE_REVISION",event.sourceVersion());v.put("OCCURRED_DTM",Timestamp.from(event.occurredAt()));v.put("VERIFIED_DTM",Timestamp.from(envelope.verifiedAt()));v.put("RECEIVED_DTM",Timestamp.from(now));v.put("PORTAL_USER_ID",applied.portalUserId()==null?null:Long.valueOf(applied.portalUserId()));v.put("RESULT_REVISION",applied.revision()==null?null:Long.valueOf(applied.revision()));v.put("HISTORY_NO",applied.historyNo());v.put("OUTCOME_CD",applied.outcome());v.put("SAFE_CODE",safeCode);v.put("APPLIED_DTM",applied.at()==null?null:Timestamp.from(applied.at()));v.put("EVENT_KEY_DIGEST",event.eventKey(config.stableNamespace(),config.epoch()));v.put("SOURCE_EPOCH",config.epoch());v.put("BINDING_KEY",envelope.bindingKey());v.put("ORDER_MODE",freshness==null?null:freshness.orderMode());v.put("VERIFIER_GENERATION",config.generation());v.put("FRESHNESS_DIGEST",freshness==null?ExternalUserChange.digest("no-effect/v1"):freshness.digest());v.put("NOTIFICATION_STATE",applied.outcome().equals("REJECTED")?"NOT_REQUIRED":"PENDING_NO_RECIPIENT");v.put("NOTIFICATION_REVISION",1);
        jdbc.update("INSERT INTO AI_PORTAL_USER_CHANGE("+String.join(",",v.keySet())+") VALUES ("+String.join(",",Collections.nCopies(v.size(),"?"))+")",v.values().toArray());return findReceipt(Long.toString(id));
    }
    public void notifications(Stored receipt,List<com.aiagent.domain.notification.source.SourceNotification.StoredNotification> notices,String version,Instant now) {
        transaction();for(var n:notices)jdbc.update("INSERT INTO AI_PORTAL_CHANGE_NOTIFICATION VALUES (?,?,?,?,?,?)",next("AI_PORTAL_CHANGE_NOTIFICATION"),Long.parseLong(receipt.receipt().receiptId()),n.adminUserId(),n.notiId(),Timestamp.from(now),version);
        jdbc.update("UPDATE AI_PORTAL_USER_CHANGE SET NOTIFICATION_STATE='STORED',NOTIFICATION_REVISION=NOTIFICATION_REVISION+1,RECIPIENT_POLICY_VERSION=?,NOTIFIED_DTM=? WHERE RECEIPT_ID=?",version,Timestamp.from(now),Long.parseLong(receipt.receipt().receiptId()));
    }
    public void ending(String receipt,Runnable purge){transaction();if(ending.get()!=null)throw new IllegalStateException("수신 파기는 중첩할 수 없다");ending.set(receipt);try{purge.run();}finally{ending.remove();}}
    public boolean isEnding(String receipt){return receipt.equals(ending.get());}
    public void delete(String receipt){transaction();if(!isEnding(receipt))throw new IllegalStateException("소유 보존 검수 없이 수신 기록을 파기할 수 없다");long id=Long.parseLong(receipt);jdbc.update("DELETE FROM AI_PORTAL_CHANGE_NOTIFICATION WHERE RECEIPT_ID=?",id);jdbc.update("DELETE FROM AI_PORTAL_USER_CHANGE WHERE RECEIPT_ID=?",id);}
}
