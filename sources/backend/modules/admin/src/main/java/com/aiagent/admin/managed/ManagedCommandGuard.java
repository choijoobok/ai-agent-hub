// @feat FEAT-040
package com.aiagent.admin.managed;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ManagedCommandGuard {
    private final JdbcTemplate jdbc;
    public ManagedCommandGuard(DataSource source) { jdbc=new JdbcTemplate(source); }
    public boolean expired(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_COMMAND_GUARD WHERE COMMAND_HASH=?",Long.class,ManagedDigest.hash(key))!=0; }
    public java.util.Optional<String> expiredOwner(String key) {
        return jdbc.queryForList("SELECT OWNER_FEATURE FROM AI_ADMIN_COMMAND_GUARD WHERE COMMAND_HASH=?",String.class,ManagedDigest.hash(key)).stream().findFirst();
    }
    public void expire(ManagedJournal.Entry entry) {
        jdbc.update("INSERT INTO AI_ADMIN_COMMAND_GUARD(COMMAND_HASH,OWNER_FEATURE,TERMINAL_CD) VALUES (?,?,'EXPIRED')",ManagedDigest.hash(entry.commandKey()),entry.feature());
    }
}
