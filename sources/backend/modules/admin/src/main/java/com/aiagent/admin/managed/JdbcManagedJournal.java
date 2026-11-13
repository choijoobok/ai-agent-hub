// @feat FEAT-040
package com.aiagent.admin.managed;

import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcManagedJournal implements ManagedJournal {
    private final JdbcTemplate jdbc;
    public JdbcManagedJournal(DataSource source) { jdbc=new JdbcTemplate(source); }
    public DataSource dataSource() { return jdbc.getDataSource(); }
    public String name() { return "ENT-099"; }
    public Optional<Entry> find(String key) {
        return jdbc.query("SELECT * FROM AI_ADMIN_MANAGED_COMMAND WHERE COMMAND_KEY=?",(r,i)->{
            Timestamp at=r.getTimestamp("APPLIED_DTM");
            return new Entry(r.getString("COMMAND_KEY"),r.getString("OWNER_FEATURE"),r.getString("ACTOR_ID"),r.getString("REQUEST_DIGEST"),r.getString("DIGEST_VERSION"),r.getString("TARGET_KIND"),r.getString("TARGET_KEY"),r.getString("RESULT_REVISION"),(Long)r.getObject("HISTORY_NO"),r.getString("OUTCOME_CD"),at==null?null:at.toInstant(),r.getTimestamp("RECORDED_DTM").toInstant());
        },key).stream().findFirst();
    }
    public void insert(Entry entry) {
        jdbc.update("INSERT INTO AI_ADMIN_MANAGED_COMMAND(COMMAND_KEY,OWNER_FEATURE,ACTOR_ID,REQUEST_DIGEST,DIGEST_VERSION,TARGET_KIND,TARGET_KEY,RESULT_REVISION,HISTORY_NO,OUTCOME_CD,APPLIED_DTM,RECORDED_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            entry.commandKey(),entry.feature(),entry.actor(),entry.digest(),entry.digestVersion(),entry.kind(),entry.targetId(),Long.valueOf(entry.revision()),entry.historyNo(),entry.outcome(),entry.appliedAt()==null?null:Timestamp.from(entry.appliedAt()),java.sql.Timestamp.from(entry.recordedAt()));
    }
    public void delete(String key) { jdbc.update("DELETE FROM AI_ADMIN_MANAGED_COMMAND WHERE COMMAND_KEY=?",key); }
    public boolean references(long historyNo) { return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_MANAGED_COMMAND WHERE HISTORY_NO=?",Long.class,historyNo)!=0; }
}
