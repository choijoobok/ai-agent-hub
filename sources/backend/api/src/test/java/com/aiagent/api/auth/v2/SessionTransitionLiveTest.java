// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("authV2Live")
class SessionTransitionLiveTest {
    @Test @DisplayName("TC-30125 전환두번째SQL실패는회수까지rollback하며운영도구init은중복덮어쓰기0이다")
    void transitionFailureAndExplicitToolInitialization()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.JDBC)){
            var runtime=fixture.runtime();var user=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var initial=fixture.selected.get(AuthDomain.ADMIN);var tool=new SessionProviderTransition(fixture.source,fixture.manager);var replacement=new Settings(Provider.REDIS,UUID.randomUUID(),"blocked");tool.drain(AuthDomain.ADMIN,initial);
            fixture.jdbc.execute("ALTER TABLE AI_AUTH_DOMAIN_STATE ADD CONSTRAINT FAIL_SECOND CHECK(PROVIDER_TYPE='JDBC')");assertThatThrownBy(()->tool.switchDrained(AuthDomain.ADMIN,initial,replacement)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(fixture.ledger.find(user.ref(),false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.ACTIVE);assertThat(fixture.jdbc.queryForObject("SELECT STATE_CD FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN='ADMIN'",String.class)).isEqualTo("DRAINED");
            fixture.jdbc.execute("ALTER TABLE AI_AUTH_DOMAIN_STATE DROP CONSTRAINT FAIL_SECOND");tool.switchDrained(AuthDomain.ADMIN,initial,replacement);assertThat(fixture.ledger.find(user.ref(),false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.REVOKED);
            fixture.jdbc.update("DELETE FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN='PORTAL'");String epoch=UUID.randomUUID().toString();String[] initialize={"init","PORTAL","jdbc",epoch,"tool-instance"};SessionProviderTool.execute(fixture.source,initialize);assertThat(fixture.ledger.settings(AuthDomain.PORTAL,false).epoch()).isEqualTo(UUID.fromString(epoch));assertThatThrownBy(()->SessionProviderTool.execute(fixture.source,initialize)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThatThrownBy(()->SessionProviderTool.execute(fixture.source,new String[]{"unknown","PORTAL","jdbc",epoch,"tool-instance"})).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->SessionProviderTool.execute(fixture.source,new String[]{"init"})).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test @DisplayName("TC-30125 양영역DB/payload논리스냅샷동시복구뒤새epoch와전체재로그인")
    void matchingPastDatabaseAndRedisSnapshotsRemainClosedUntilNewEpochs()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var oldNode=fixture.runtime();var users=new EnumMap<AuthDomain,V2LiveFixture.Issued>(AuthDomain.class);var grants=new EnumMap<AuthDomain,SessionAdmissionLedger.Grant>(AuthDomain.class);var payloads=new EnumMap<AuthDomain,Map<Object,Object>>(AuthDomain.class);var redis=new org.springframework.data.redis.core.StringRedisTemplate(fixture.redis);
            for(AuthDomain domain:AuthDomain.values()){var user=fixture.login(oldNode,domain,fixture.anonymous(oldNode,domain));users.put(domain,user);grants.put(domain,fixture.ledger.find(user.ref(),false).orElseThrow());payloads.put(domain,redis.opsForHash().entries(key(user.ref())));}
            var tool=new SessionProviderTransition(fixture.source,fixture.manager);Map<AuthDomain,Settings> deployment=new EnumMap<>(AuthDomain.class);
            for(AuthDomain domain:AuthDomain.values()){var selected=new Settings(Provider.JDBC,UUID.randomUUID(),"deployment-"+domain);tool.drain(domain,fixture.selected.get(domain));tool.switchDrained(domain,fixture.selected.get(domain),selected);deployment.put(domain,selected);}
            var deployed=new SessionRuntime(fixture.ledger,new JdbcSessionStore(fixture.source,fixture.manager,Set.of(AuthDomain.values())),fixture.transactions,deployment,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),java.time.Duration.ofMinutes(30));
            // 백업 복구의 논리행/payload를 재주입한다. 이 보수창 동안 외부수락은 닫고 구노드를 재시작하지 않는다.
            for(AuthDomain domain:AuthDomain.values()){
                var previous=fixture.selected.get(domain);fixture.jdbc.update("UPDATE AI_AUTH_DOMAIN_STATE SET PROVIDER_TYPE=?,PROVIDER_EPOCH=?,STORE_INSTANCE=?,STATE_CD='ACTIVE' WHERE AUTH_DOMAIN=?",previous.provider().name(),previous.epoch(),previous.storeInstance(),domain.name());
                fixture.transactions.required(()->{var current=fixture.ledger.find(users.get(domain).ref(),true).orElseThrow();assertThat(fixture.ledger.replace(current,grants.get(domain))).isTrue();return null;});redis.opsForHash().putAll(key(users.get(domain).ref()),payloads.get(domain));
                assertThatThrownBy(()->fixture.scope(deployed,domain,Capability.READ,users.get(domain).cookie())).isInstanceOf(Failure.class);
            }
            Map<AuthDomain,Settings> restored=new EnumMap<>(AuthDomain.class);for(AuthDomain domain:AuthDomain.values()){var settings=new Settings(Provider.REDIS,UUID.randomUUID(),"restored-"+domain);tool.drain(domain,fixture.selected.get(domain));tool.switchDrained(domain,fixture.selected.get(domain),settings);restored.put(domain,settings);}
            var recovered=new SessionRuntime(fixture.ledger,new RedisSessionStore(fixture.redis),fixture.transactions,restored,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),java.time.Duration.ofMinutes(30));var secondNode=new SessionRuntime(fixture.ledger,new RedisSessionStore(fixture.redis),fixture.transactions,restored,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),java.time.Duration.ofMinutes(30));
            for(AuthDomain domain:AuthDomain.values()){assertThatThrownBy(()->fixture.scope(recovered,domain,Capability.READ,users.get(domain).cookie())).isInstanceOf(Failure.class);var fresh=fixture.login(recovered,domain,fixture.anonymous(recovered,domain));assertThat(fixture.scope(secondNode,domain,Capability.READ,fresh.cookie()).current()).isPresent();}
        }
    }
    private static String key(Ref ref){return "aihub:auth:v2:"+ref.domain()+":"+ref.epoch()+":"+ref.grantId()+":"+ref.generation();}
    @Test @DisplayName("TC-30125 실제두provider왕복·두노드epoch불일치·과거DB/Redis복구는수락닫힘이다")
    void controlledRoundTripAndPastDatabaseRestore()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.JDBC)){
            var jdbcNode=fixture.runtime();var otherNode=fixture.runtime();var old=fixture.login(jdbcNode,AuthDomain.ADMIN,fixture.anonymous(jdbcNode,AuthDomain.ADMIN));var initial=fixture.selected.get(AuthDomain.ADMIN);
            assertThat(fixture.ledger.settings(AuthDomain.ADMIN,false)).isEqualTo(initial);fixture.runtime();assertThat(fixture.ledger.settings(AuthDomain.ADMIN,false)).isEqualTo(initial);
            var tool=new SessionProviderTransition(fixture.source,fixture.manager);var redisSettings=new Settings(Provider.REDIS,UUID.randomUUID(),"new-redis");
            assertThatThrownBy(()->tool.switchDrained(AuthDomain.ADMIN,initial,redisSettings)).isInstanceOf(IllegalStateException.class);
            tool.drain(AuthDomain.ADMIN,initial);assertThatThrownBy(()->fixture.scope(otherNode,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);assertThat(tool.switchDrained(AuthDomain.ADMIN,initial,redisSettings)).isGreaterThanOrEqualTo(1);
            assertThatThrownBy(()->fixture.runtime()).isInstanceOf(Failure.class);
            var redisNode=new SessionRuntime(fixture.ledger,new RedisSessionStore(fixture.redis),fixture.transactions,Map.of(AuthDomain.ADMIN,redisSettings),new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),java.time.Duration.ofMinutes(30));
            var current=fixture.login(redisNode,AuthDomain.ADMIN,fixture.anonymous(redisNode,AuthDomain.ADMIN));assertThatThrownBy(()->fixture.scope(redisNode,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);
            var returned=new Settings(Provider.JDBC,UUID.randomUUID(),"return-jdbc");tool.drain(AuthDomain.ADMIN,redisSettings);tool.switchDrained(AuthDomain.ADMIN,redisSettings,returned);
            var returnedNode=new SessionRuntime(fixture.ledger,new JdbcSessionStore(fixture.source,fixture.manager,Set.of(AuthDomain.ADMIN)),fixture.transactions,Map.of(AuthDomain.ADMIN,returned),new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),java.time.Duration.ofMinutes(30));
            for(String cookie:List.of(old.cookie(),current.cookie()))assertThatThrownBy(()->fixture.scope(returnedNode,AuthDomain.ADMIN,Capability.READ,cookie)).isInstanceOf(Failure.class);
            // 과거DB설정/원장복구에도 현재배포설정은 되돌리지 않고 외부수락을 닫은 보수창에서 새epoch로 바꾼다.
            fixture.jdbc.update("UPDATE AI_AUTH_DOMAIN_STATE SET PROVIDER_TYPE=?,PROVIDER_EPOCH=?,STORE_INSTANCE=? WHERE AUTH_DOMAIN='ADMIN'",initial.provider().name(),initial.epoch(),initial.storeInstance());fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET STATE_CD='ACTIVE',TERMINAL_AT=null,FIRST_USED_AT=clock_timestamp() WHERE GRANT_ID=?",old.ref().grantId());
            assertThatThrownBy(()->fixture.scope(returnedNode,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);
            var afterRestore=new Settings(Provider.JDBC,UUID.randomUUID(),"after-restore");tool.drain(AuthDomain.ADMIN,initial);tool.switchDrained(AuthDomain.ADMIN,initial,afterRestore);
            assertThat(fixture.ledger.settings(AuthDomain.ADMIN,false).epoch()).isNotEqualTo(initial.epoch()).isNotEqualTo(returned.epoch());
            assertThatThrownBy(()->tool.drain(AuthDomain.ADMIN,returned)).isInstanceOf(IllegalStateException.class);
        }
    }
}
