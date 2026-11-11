// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcSessionStoreTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private JdbcSessionStore first,second;
    private final UUID epoch=UUID.randomUUID();
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(source);
        for(String table:List.of("SPRING_SESSION","AI_PORTAL_SESSION")){
            jdbc.execute("CREATE TABLE "+table+"(PRIMARY_ID CHAR(36) PRIMARY KEY,SESSION_ID CHAR(36) UNIQUE,CREATION_TIME BIGINT NOT NULL,LAST_ACCESS_TIME BIGINT NOT NULL,MAX_INACTIVE_INTERVAL INT NOT NULL,EXPIRY_TIME BIGINT NOT NULL,PRINCIPAL_NAME VARCHAR(100))");
            jdbc.execute("CREATE TABLE "+table+"_ATTRIBUTES(SESSION_PRIMARY_ID CHAR(36) REFERENCES "+table+"(PRIMARY_ID) ON DELETE CASCADE,ATTRIBUTE_NAME VARCHAR(200),ATTRIBUTE_BYTES VARBINARY NOT NULL,PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME))");
        }
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
        first=new JdbcSessionStore(source,manager,Set.of(AuthDomain.ADMIN,AuthDomain.PORTAL));second=new JdbcSessionStore(source,manager,Set.of(AuthDomain.ADMIN,AuthDomain.PORTAL));
    }
    @AfterEach void close(){if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    private Payload payload(AuthDomain domain,long id,long generation){
        var identity=new Identity(new PrincipalRef(domain,"owner"),AuthLevel.AUTHENTICATED,"LOCAL",domain==AuthDomain.PORTAL?1L:null,Instant.parse("2026-11-11T00:00:00Z"),"이름","ko");
        return new Payload(new Ref(domain,id,generation,epoch),identity,new Secret("c".repeat(43)),"이름","ko",Set.of("MENU:READ"));
    }
    private Instant expiry(){return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",(r,i)->r.getTimestamp(1).toInstant()).plusSeconds(1800);}
    @Test @DisplayName("TC-30127 JDBC표준스키마/속성·ADMINraw인덱스/PORTALprefix·외부쿠키비종속")
    void standardSchemaAndTwoDomains(){
        for(var domain:AuthDomain.values()){
            var payload=payload(domain,1,1);var saved=tx.execute(status->first.create(payload,expiry()));
            assertThat(second.load(payload.ref(),payload.identity().principal()).orElseThrow().payload()).isEqualTo(payload);
            String table=domain==AuthDomain.ADMIN?"SPRING_SESSION":"AI_PORTAL_SESSION";
            assertThat(jdbc.queryForObject("SELECT PRINCIPAL_NAME FROM "+table,String.class)).isEqualTo(domain==AuthDomain.ADMIN?"owner":"PORTAL:owner");
            assertThat(jdbc.queryForObject("SELECT LENGTH(SESSION_ID) FROM "+table,Integer.class)).isEqualTo(36);
            assertThat(saved.toString()).doesNotContain(saved.version());
        }
    }
    @Test @DisplayName("TC-30124 두instance늦은save/delete는새세대나새revision을덮지않는다")
    void staleFlushCannotUpsertOrOverwrite(){
        var payload=payload(AuthDomain.ADMIN,1,1);var old=tx.execute(status->first.create(payload,expiry()));
        assertThat(tx.<Outcome>execute(status->second.updateExisting(old,payload,expiry()))).isEqualTo(Outcome.APPLIED);
        assertThat(tx.<Outcome>execute(status->first.updateExisting(old,payload,expiry()))).isEqualTo(Outcome.STALE_REVISION);
        var current=second.load(payload.ref(),payload.identity().principal()).orElseThrow();
        tx.executeWithoutResult(status->assertThat(second.delete(current)).isEqualTo(Outcome.REVOKED));
        assertThat(tx.<Outcome>execute(status->first.updateExisting(old,payload,expiry()))).isEqualTo(Outcome.INVALID_SESSION);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isZero();
    }
    @Test @DisplayName("TC-30127 부모/속성저장실패는같은TXrollback·중복grant매핑은503")
    void attributesRollbackAndDuplicateMappingFailClosed(){
        var payload=payload(AuthDomain.ADMIN,1,1);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{first.create(payload,expiry());throw new IllegalStateException("시험 롤백");})).isInstanceOf(IllegalStateException.class);
        assertThat(first.load(payload.ref(),payload.identity().principal())).isEmpty();
        tx.executeWithoutResult(status->first.create(payload,expiry()));
        String primary=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO SPRING_SESSION SELECT ?,?,CREATION_TIME,LAST_ACCESS_TIME,MAX_INACTIVE_INTERVAL,EXPIRY_TIME,PRINCIPAL_NAME FROM SPRING_SESSION",primary,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO SPRING_SESSION_ATTRIBUTES SELECT ?,ATTRIBUTE_NAME,ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES",primary);
        assertThatThrownBy(()->first.load(payload.ref(),payload.identity().principal())).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.UNAVAILABLE));
    }
    @Test @DisplayName("TC-30124 호출자expected/changed동시위조도실제저장불변값을바꾸지못한다")
    void forgedExpectedCannotReplaceStoredIdentityOrSecret(){
        var original=payload(AuthDomain.ADMIN,1,1);var saved=tx.execute(status->first.create(original,expiry()));
        var fake=new Payload(original.ref(),original.identity(),new Secret("x".repeat(43)),original.displayName(),original.locale(),original.authoritySnapshot());
        var forged=new Stored(fake,saved.revision(),saved.version());
        assertThat(tx.<Outcome>execute(status->first.updateExisting(forged,fake,expiry()))).isEqualTo(Outcome.INVALID_SESSION);
        assertThat(second.load(original.ref(),original.identity().principal()).orElseThrow().payload()).isEqualTo(original);
    }
    @Test @DisplayName("TC-30126 JDBC만료부모/속성정리는유효행을보존한다")
    void cleansOnlyExpiredPayloads(){
        var old=payload(AuthDomain.ADMIN,1,1);var live=payload(AuthDomain.ADMIN,2,1);tx.executeWithoutResult(status->{first.create(old,expiry());first.create(live,expiry());});
        String primary=first.load(old.ref(),old.identity().principal()).orElseThrow().version().split("\\|")[0];jdbc.update("UPDATE SPRING_SESSION SET EXPIRY_TIME=0 WHERE PRIMARY_ID=?",primary);
        assertThat(first.cleanupExpired()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID=?",Integer.class,primary)).isZero();assertThat(first.load(live.ref(),live.identity().principal())).isPresent();assertThat(first.cleanupExpired()).isZero();
    }
}
