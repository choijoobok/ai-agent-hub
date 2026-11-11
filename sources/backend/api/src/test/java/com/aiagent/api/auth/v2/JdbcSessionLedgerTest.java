// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.*;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcSessionLedgerTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private JdbcSessionAdmissionLedger ledger;
    private java.sql.Connection schemaConnection;
    private final UUID epoch=UUID.randomUUID();
    @BeforeEach void setup()throws Exception {
        source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME VARCHAR(255) PRIMARY KEY,NEXT_VAL BIGINT NOT NULL)");
        schemaConnection=source.getConnection();
        ScriptUtils.executeSqlScript(schemaConnection,new FileSystemResource("../bootstrap/src/main/resources/db/changelog/modules/platform/002-auth-session-v2.sql"));
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);ledger=new JdbcSessionAdmissionLedger(source,manager);
        jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE VALUES('ADMIN','JDBC',?,'fixture','ACTIVE',CURRENT_TIMESTAMP)",epoch);
        jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE VALUES('PORTAL','JDBC',?,'fixture','ACTIVE',CURRENT_TIMESTAMP)",epoch);
    }
    @AfterEach void close()throws Exception{if(schemaConnection!=null)schemaConnection.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    private Grant grant(long id,AuthDomain domain,Status status,AuthLevel level) {
        Instant now=ledger.now();
        return new Grant(new Ref(domain,id,1,epoch),Provider.JDBC,String.format("%064d",id),String.format("%043d",id),level==AuthLevel.ANONYMOUS?null:"owner",level,
                status,level==AuthLevel.ANONYMOUS?null:now,now,now,now.plusSeconds(level==AuthLevel.ANONYMOUS?600:1800),
                status==Status.PENDING?now.plusSeconds(60):null,null,level==AuthLevel.ANONYMOUS?null:now.plusSeconds(60),null);
    }
    @Test @DisplayName("TC-30127 DB수락원장 쓰기는 REQUIRED에참여하고rollback시현재세대가보존된다")
    void rollbackAndGenerationCas() {
        var first=grant(ledger.nextId(),AuthDomain.ADMIN,Status.ACTIVE,AuthLevel.AUTHENTICATED);
        tx.executeWithoutResult(status->ledger.insert(first));
        var next=new Grant(new Ref(AuthDomain.ADMIN,first.ref().grantId(),2,epoch),first.provider(),"c".repeat(64),"d".repeat(43),first.subjectId(),first.level(),
                first.status(),first.authenticatedAt(),first.createdAt(),first.lastAccessAt(),first.expiresAt(),null,null,first.confirmDeadline(),null);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{assertThat(ledger.replace(first,next)).isTrue();throw new IllegalStateException("시험 롤백");})).isInstanceOf(IllegalStateException.class);
        assertThat(ledger.find(first.ref(),false)).contains(first);
        tx.executeWithoutResult(status->assertThat(ledger.replace(first,next)).isTrue());
        tx.executeWithoutResult(status->assertThat(ledger.replace(first,next)).isFalse());
        assertThat(ledger.find(first.ref(),false)).isEmpty();
        assertThat(ledger.find(next.ref(),false)).contains(next);
    }
    @Test @DisplayName("TC-30122 같은문자열주체라도영역·쿠키지문은교차조회되지않는다")
    void domainIsolation() {
        var admin=grant(ledger.nextId(),AuthDomain.ADMIN,Status.ACTIVE,AuthLevel.AUTHENTICATED);
        var portal=grant(ledger.nextId(),AuthDomain.PORTAL,Status.ACTIVE,AuthLevel.AUTHENTICATED);
        tx.executeWithoutResult(status->{ledger.insert(admin);ledger.insert(portal);});
        assertThat(ledger.principal(new PrincipalRef(AuthDomain.ADMIN,"owner"),false)).containsExactly(admin);
        assertThat(ledger.byCookie(AuthDomain.PORTAL,portal.cookieHash(),false)).contains(portal);
        assertThat(ledger.find(new Ref(AuthDomain.PORTAL,admin.ref().grantId(),1,epoch),false)).isEmpty();
    }
    @Test @DisplayName("TC-30121 DB설정판은기동조회로자동변경되지않는다")
    void settingsAreReadOnlyAndSequenceDoesNotReuseRollbackId() {
        var expected=new Settings(Provider.JDBC,epoch,"fixture");assertThat(ledger.settings(AuthDomain.ADMIN,false)).isEqualTo(expected);
        long before=ledger.nextId();
        tx.executeWithoutResult(status->{ledger.nextId();status.setRollbackOnly();});
        assertThat(ledger.nextId()).isGreaterThan(before+1);
        assertThat(ledger.settings(AuthDomain.ADMIN,false)).isEqualTo(expected);
    }
    @Test @DisplayName("TC-30126 최근회수는오래된expiry보다terminal보존을우선하고deadline만으로정리하지않는다")
    void cleanupUsesTerminalOrExpiryOnly() {
        var one=grant(ledger.nextId(),AuthDomain.ADMIN,Status.REVOKED,AuthLevel.AUTHENTICATED);
        var two=grant(ledger.nextId(),AuthDomain.ADMIN,Status.PENDING,AuthLevel.AUTHENTICATED);
        tx.executeWithoutResult(status->{ledger.insert(one);ledger.insert(two);});
        jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET EXPIRES_AT=?,TERMINAL_AT=? WHERE GRANT_ID=?",java.sql.Timestamp.from(ledger.now().minusSeconds(25*3600)),java.sql.Timestamp.from(ledger.now().minusSeconds(3600)),one.ref().grantId());
        jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET COOKIE_HASH=?,MANAGEMENT_REF=?,PUBLISH_DEADLINE_AT=?,CONFIRM_DEADLINE_AT=? WHERE GRANT_ID=?","c".repeat(64),"d".repeat(43),java.sql.Timestamp.from(ledger.now().minusSeconds(25*3600)),java.sql.Timestamp.from(ledger.now().minusSeconds(25*3600)),two.ref().grantId());
        assertThat(ledger.cleanup()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT",Integer.class)).isEqualTo(2);
    }
    @Test @DisplayName("TC-30126 원장정리는24시간지난행만1000행청크로제거한다")
    void cleanupIsBounded() {
        Instant before=ledger.now().minusSeconds(25*3600);
        tx.executeWithoutResult(status->{for(long id=1;id<=1001;id++)ledger.insert(new Grant(new Ref(AuthDomain.ADMIN,id,1,epoch),Provider.JDBC,String.format("%064d",id),String.format("%043d",id),"owner",AuthLevel.AUTHENTICATED,
                Status.REVOKED,before,before,before,before,null,null,null,before));});
        assertThat(ledger.cleanup()).isEqualTo(1000);assertThat(ledger.cleanup()).isEqualTo(1);
    }
    @Test @DisplayName("TC-30121 DRAINED설정판과미지원원장schema는닫힌실패다")
    void rejectDrainedAndUnknownSchema() {
        jdbc.update("UPDATE AI_AUTH_DOMAIN_STATE SET STATE_CD='DRAINED' WHERE AUTH_DOMAIN='ADMIN'");
        assertThatThrownBy(()->ledger.settings(AuthDomain.ADMIN,false)).isInstanceOf(Failure.class);
        var one=grant(ledger.nextId(),AuthDomain.PORTAL,Status.ACTIVE,AuthLevel.AUTHENTICATED);
        tx.executeWithoutResult(status->ledger.insert(one));jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET SCHEMA_VERSION=999");
        assertThatThrownBy(()->ledger.find(one.ref(),false)).isInstanceOf(Failure.class);
    }
}
