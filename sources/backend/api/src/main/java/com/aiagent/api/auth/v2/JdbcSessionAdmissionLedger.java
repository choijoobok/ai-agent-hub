// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionV2.*;

/** 자격과 같은 DB REQUIRED에 참여하는 v2 현재 수락 원장. */
public final class JdbcSessionAdmissionLedger implements SessionAdmissionLedger {
    private static final String COLUMNS="GRANT_ID,AUTH_DOMAIN,PROVIDER_TYPE,PROVIDER_EPOCH,GENERATION,COOKIE_HASH,MANAGEMENT_REF,SUBJECT_ID,AUTH_LEVEL,STATE_CD,AUTHENTICATED_AT,CREATED_AT,LAST_ACCESS_AT,EXPIRES_AT,PUBLISH_DEADLINE_AT,FIRST_USED_AT,CONFIRM_DEADLINE_AT,TERMINAL_AT,SCHEMA_VERSION";
    private final JdbcTemplate jdbc;
    private final SessionTransactions transactions;
    private final boolean postgres;
    public JdbcSessionAdmissionLedger(DataSource source,PlatformTransactionManager manager){
        jdbc=new JdbcTemplate(source);transactions=new SessionTransactions(source,manager);
        postgres=Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c)->c.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
    }
    public Instant now(){return jdbc.queryForObject(postgres?"SELECT clock_timestamp()":"SELECT CURRENT_TIMESTAMP",(r,i)->r.getTimestamp(1).toInstant());}
    public Settings settings(AuthDomain domain,boolean lock){
        if(lock)transactions.requireCurrent();
        var found=jdbc.query("SELECT PROVIDER_TYPE,PROVIDER_EPOCH,STORE_INSTANCE,STATE_CD FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN=?"+(lock?(postgres?" FOR SHARE":" FOR UPDATE"):""),
                (r,i)->{if(!r.getString(4).equals("ACTIVE"))throw new Failure(Outcome.UNAVAILABLE);return new Settings(Provider.valueOf(r.getString(1)),UUID.fromString(r.getString(2)),r.getString(3));},domain.name());
        if(found.size()!=1)throw new Failure(Outcome.UNAVAILABLE);return found.getFirst();
    }
    public long nextId(){return transactions.reserveIdentifier(()->{
        Long value=jdbc.queryForObject("SELECT NEXT_VAL FROM SYS_SEQUENCE WHERE SEQ_NAME='AI_AUTH_SESSION_GRANT' FOR UPDATE",Long.class);
        if(value==null||value<1||value==Long.MAX_VALUE)throw new IllegalStateException("인증 수락 ID를 예약할 수 없다");
        jdbc.update("UPDATE SYS_SEQUENCE SET NEXT_VAL=? WHERE SEQ_NAME='AI_AUTH_SESSION_GRANT'",value+1);return value;
    });}
    private static Instant instant(ResultSet r,String name)throws SQLException{Timestamp value=r.getTimestamp(name);return value==null?null:value.toInstant();}
    private static final RowMapper<Grant> MAPPER=(r,i)->{
        if(r.getInt("SCHEMA_VERSION")!=2)throw new Failure(Outcome.INVALID_SESSION);
        return new Grant(new Ref(AuthDomain.valueOf(r.getString("AUTH_DOMAIN")),r.getLong("GRANT_ID"),r.getLong("GENERATION"),UUID.fromString(r.getString("PROVIDER_EPOCH"))),
                Provider.valueOf(r.getString("PROVIDER_TYPE")),r.getString("COOKIE_HASH"),r.getString("MANAGEMENT_REF"),r.getString("SUBJECT_ID"),AuthLevel.valueOf(r.getString("AUTH_LEVEL")),Status.valueOf(r.getString("STATE_CD")),
                instant(r,"AUTHENTICATED_AT"),instant(r,"CREATED_AT"),instant(r,"LAST_ACCESS_AT"),instant(r,"EXPIRES_AT"),instant(r,"PUBLISH_DEADLINE_AT"),instant(r,"FIRST_USED_AT"),instant(r,"CONFIRM_DEADLINE_AT"),instant(r,"TERMINAL_AT"));
    };
    private List<Grant> query(String predicate,boolean lock,Object...args){
        if(lock)transactions.requireCurrent();
        return jdbc.query("SELECT "+COLUMNS+" FROM AI_AUTH_SESSION_GRANT WHERE "+predicate+" ORDER BY GRANT_ID"+(lock?" FOR UPDATE":""),MAPPER,args);
    }
    public Optional<Grant> byCookie(AuthDomain domain,String hash,boolean lock){return query("AUTH_DOMAIN=? AND COOKIE_HASH=?",lock,domain.name(),hash).stream().findFirst();}
    public Optional<Grant> find(Ref ref,boolean lock){return query("AUTH_DOMAIN=? AND GRANT_ID=? AND GENERATION=? AND PROVIDER_EPOCH=?",lock,ref.domain().name(),ref.grantId(),ref.generation(),ref.epoch()).stream().findFirst();}
    public List<Grant> principal(PrincipalRef principal,boolean lock){return query("AUTH_DOMAIN=? AND SUBJECT_ID=?",lock,principal.domain().name(),principal.subjectId());}
    private static Object timestamp(Instant value){return value==null?null:Timestamp.from(value);}
    private static Object[] values(Grant g){return new Object[]{g.ref().grantId(),g.ref().domain().name(),g.provider().name(),g.ref().epoch(),g.ref().generation(),g.cookieHash(),g.managementRef(),g.subjectId(),g.level().name(),g.status().name(),
            timestamp(g.authenticatedAt()),timestamp(g.createdAt()),timestamp(g.lastAccessAt()),timestamp(g.expiresAt()),timestamp(g.publishDeadline()),timestamp(g.firstUsedAt()),timestamp(g.confirmDeadline()),timestamp(g.terminalAt()),2};}
    public void insert(Grant grant){
        transactions.requireCurrent();jdbc.update("INSERT INTO AI_AUTH_SESSION_GRANT ("+COLUMNS+") VALUES ("+String.join(",",java.util.Collections.nCopies(19,"?"))+")",values(grant));
    }
    public boolean replace(Grant expected,Grant changed){
        transactions.requireCurrent();
        if(expected.ref().domain()!=changed.ref().domain()||expected.ref().grantId()!=changed.ref().grantId())throw new IllegalArgumentException("다른 수락 원장으로 변경할 수 없다");
        var current=find(expected.ref(),true);
        if(current.isEmpty()||!current.get().equals(expected))return false;
        String assignments=java.util.Arrays.stream(COLUMNS.split(",")).skip(1).map(name->name+"=?").collect(java.util.stream.Collectors.joining(","));
        Object[] row=values(changed),args=new Object[22];System.arraycopy(row,1,args,0,18);
        args[18]=expected.ref().grantId();args[19]=expected.ref().domain().name();args[20]=expected.ref().generation();args[21]=expected.ref().epoch();
        return jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET "+assignments+" WHERE GRANT_ID=? AND AUTH_DOMAIN=? AND GENERATION=? AND PROVIDER_EPOCH=?",args)==1;
    }
    public int cleanup(){return transactions.required(()->{
        Instant cutoff=now().minusSeconds(86400);
        var ids=jdbc.queryForList("SELECT GRANT_ID FROM AI_AUTH_SESSION_GRANT WHERE COALESCE(TERMINAL_AT,EXPIRES_AT)<? ORDER BY GRANT_ID LIMIT 1000 FOR UPDATE SKIP LOCKED",Long.class,timestamp(cutoff));
        if(ids.isEmpty())return 0;
        var arguments=new java.util.ArrayList<Object>(ids);arguments.add(timestamp(cutoff));
        return jdbc.update("DELETE FROM AI_AUTH_SESSION_GRANT WHERE GRANT_ID IN ("+String.join(",",java.util.Collections.nCopies(ids.size(),"?"))+") AND COALESCE(TERMINAL_AT,EXPIRES_AT)<?",arguments.toArray());
    });}
}
