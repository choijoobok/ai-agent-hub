// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("authV2Live")
class RedisSessionStoreLiveTest {
    private LettuceConnectionFactory connection;
    private RedisSessionStore first,second;
    private final UUID epoch=UUID.randomUUID();
    @BeforeEach void setup(){
        String port=System.getenv("AUTH_V2_REDIS_PORT"),password=System.getenv("AUTH_V2_REDIS_PASSWORD");
        if(port==null||password==null)throw new IllegalStateException("작업 전용 실제 Redis 환경이 필요하다");
        var config=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(port));config.setPassword(RedisPassword.of(password));
        connection=new LettuceConnectionFactory(config);connection.afterPropertiesSet();connection.start();
        first=new RedisSessionStore(connection);second=new RedisSessionStore(connection);first.verifyAvailable();
    }
    @AfterEach void close(){if(connection!=null)connection.destroy();}
    private Payload payload(AuthDomain domain,long generation){
        var identity=new Identity(new PrincipalRef(domain,"owner"),AuthLevel.AUTHENTICATED,"LOCAL",domain==AuthDomain.PORTAL?1L:null,Instant.parse("2026-11-11T00:00:00Z"),"이름","ko");
        return new Payload(new Ref(domain,1,generation,epoch),identity,new Secret("c".repeat(43)),"이름","ko",Set.of("MENU:READ"));
    }
    @Test @DisplayName("TC-30124 실제Redis 두instance의NX/현재revision CAS·삭제뒤늦은쓰기0")
    void actualRedisCasAndNoUpsert(){
        var payload=payload(AuthDomain.ADMIN,1);var old=first.create(payload,Instant.now().plusSeconds(300));
        assertThat(second.load(payload.ref(),payload.identity().principal()).orElseThrow().payload()).isEqualTo(payload);
        assertThatThrownBy(()->second.create(payload,Instant.now().plusSeconds(300))).isInstanceOf(Failure.class);
        assertThat(second.updateExisting(old,payload,Instant.now().plusSeconds(300))).isEqualTo(Outcome.APPLIED);
        assertThat(first.updateExisting(old,payload,Instant.now().plusSeconds(300))).isEqualTo(Outcome.STALE_REVISION);
        var current=second.load(payload.ref(),payload.identity().principal()).orElseThrow();
        assertThat(second.delete(current)).isEqualTo(Outcome.REVOKED);
        assertThat(first.updateExisting(old,payload,Instant.now().plusSeconds(300))).isEqualTo(Outcome.INVALID_SESSION);
        assertThat(first.load(payload.ref(),payload.identity().principal())).isEmpty();
    }
    @Test @DisplayName("TC-30122 실제RedisADMIN/PORTALnamespace와현재주체는분리된다")
    void actualRedisNamespaces(){
        for(var domain:AuthDomain.values()){
            var payload=payload(domain,1);first.create(payload,Instant.now().plusSeconds(300));
            assertThat(second.load(payload.ref(),payload.identity().principal())).isPresent();
            assertThat(second.load(payload.ref(),new PrincipalRef(domain,"other"))).isEmpty();
        }
    }
    @Test @DisplayName("TC-30123 실제Redis네트워크는DB트랜잭션안에서호출할수없다")
    void rejectsNetworkInsideTransaction(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status->assertThatThrownBy(()->first.load(payload(AuthDomain.ADMIN,1).ref(),new PrincipalRef(AuthDomain.ADMIN,"owner"))).isInstanceOf(IllegalStateException.class));
    }
    @Test @DisplayName("TC-30124 실제Redis위조expected의update/delete는저장payload를덮거나삭제하지않는다")
    void actualRedisRejectsForgedExpected(){
        var original=payload(AuthDomain.ADMIN,1);var saved=first.create(original,Instant.now().plusSeconds(300));
        var fake=new Payload(original.ref(),original.identity(),new Secret("x".repeat(43)),original.displayName(),original.locale(),original.authoritySnapshot());
        var forged=new Stored(fake,saved.revision(),saved.version());
        assertThat(second.updateExisting(forged,fake,Instant.now().plusSeconds(300))).isEqualTo(Outcome.INVALID_SESSION);
        assertThat(second.delete(forged)).isEqualTo(Outcome.ALREADY_ABSENT);
        assertThat(first.load(original.ref(),original.identity().principal()).orElseThrow().payload()).isEqualTo(original);
    }
    @Test @DisplayName("TC-30124 실제Redis갱신TTL이이미지나키가사라지면APPLIED가아니다")
    void expiredUpdateCannotReportApplied(){
        var original=payload(AuthDomain.ADMIN,1);var saved=first.create(original,Instant.now().plusSeconds(300));
        assertThat(second.updateExisting(saved,original,Instant.EPOCH)).isEqualTo(Outcome.INVALID_SESSION);
        assertThat(first.load(original.ref(),original.identity().principal())).isEmpty();
    }
}
