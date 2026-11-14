// @feat FEAT-001 FEAT-040
package com.aiagent.admin.targetsystem;
import com.aiagent.admin.managed.*;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** ENT-019 소유 원장. 원 결과와 정리 대상별 ENT-168은 같은 관리 트랜잭션에 참여한다. */
public final class TargetAssetJournal implements ManagedJournal {
    private final JdbcTemplate jdbc;
    public TargetAssetJournal(DataSource source){jdbc=new JdbcTemplate(source);}
    public DataSource dataSource(){return jdbc.getDataSource();}public String name(){return "ENT-019";}
    public Optional<Entry> find(String key){return jdbc.query("SELECT * FROM AI_ADMIN_ASSET_COMMAND WHERE COMMAND_KEY=?",(r,n)->{
        var at=r.getTimestamp("APPLIED_DTM");return new Entry(r.getString("COMMAND_KEY"),r.getString("OWNER_FEATURE"),r.getString("ACTOR_ID"),r.getString("REQUEST_DIGEST"),r.getString("DIGEST_VERSION"),r.getString("TARGET_KIND"),r.getString("TARGET_ID"),r.getString("RESULT_VERSION"),(Long)r.getObject("HISTORY_NO"),r.getString("OUTCOME_CD"),at==null?null:at.toInstant(),r.getTimestamp("RECORDED_DTM").toInstant());
    },key).stream().findFirst();}
    public void insert(Entry entry){String action=entry.historyNo()==null?"CHANGE":jdbc.queryForObject("SELECT HIST_ACTION FROM AI_ADMIN_TARGET_SYSTEM_HIST WHERE HISTORY_NO=?",String.class,entry.historyNo());insert(entry,action);}
    void insert(Entry entry,String action){jdbc.update("INSERT INTO AI_ADMIN_ASSET_COMMAND(COMMAND_KEY,ACTOR_ID,REQUEST_DIGEST,TARGET_KIND,TARGET_ID,ACTION_CD,RESULT_VERSION,HISTORY_NO,APPLIED_DTM,OWNER_FEATURE,OUTCOME_CD,DIGEST_VERSION,RECORDED_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",entry.commandKey(),entry.actor(),entry.digest(),entry.kind(),entry.targetId()==null?null:Long.valueOf(entry.targetId()),action,entry.revision()==null?null:Long.valueOf(entry.revision()),entry.historyNo(),entry.appliedAt()==null?null:Timestamp.from(entry.appliedAt()),entry.feature(),entry.outcome(),entry.digestVersion(),Timestamp.from(entry.recordedAt()));}
    public void delete(String key){jdbc.update("DELETE FROM AI_ADMIN_ASSET_COMMAND_RESULT WHERE COMMAND_KEY=?",key);jdbc.update("DELETE FROM AI_ADMIN_ASSET_COMMAND WHERE COMMAND_KEY=?",key);}
    public boolean references(long history){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_ASSET_COMMAND WHERE HISTORY_NO=?",Long.class,history)>0||jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_ASSET_COMMAND_RESULT WHERE HISTORY_NO=?",Long.class,history)>0;}
    public List<Long> histories(Entry entry){var numbers=new TreeSet<>(ManagedJournal.super.histories(entry));numbers.addAll(jdbc.queryForList("SELECT HISTORY_NO FROM AI_ADMIN_ASSET_COMMAND_RESULT WHERE COMMAND_KEY=?",Long.class,entry.commandKey()));return List.copyOf(numbers);}
}
