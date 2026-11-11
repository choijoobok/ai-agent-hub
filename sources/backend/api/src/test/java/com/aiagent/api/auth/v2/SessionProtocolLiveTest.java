// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("authV2Live")
class SessionProtocolLiveTest {
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30122/27/28 회전의타주체증거는실제DB이력/새grant효과0으로rollback한다")
    void rotationCannotReplaceTheCurrentSubject(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            fixture.jdbc.update("INSERT INTO AUTH_OWNER VALUES('ADMIN','other',true,true)");var runtime=fixture.runtime();var old=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var change=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PASSWORD_CHANGE,old.cookie());var wrong=new com.aiagent.core.security.SessionPersistencePort.Identity(new PrincipalRef(AuthDomain.ADMIN,"other"),AuthLevel.AUTHENTICATED,"LOCAL",null,fixture.ledger.now(),"다른주체","ko");
            assertThatThrownBy(()->runtime.execute(change,()->{fixture.effect("wrong-subject");change.publish(fixture.producers.get(AuthDomain.ADMIN).rotation(change.exchange(),old.ref(),wrong,fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;})).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.INVALID_SESSION));assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isZero();assertThat(fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,old.cookie()).current().orElseThrow().identity().principal().subjectId()).isEqualTo("owner");assertThat(fixture.ledger.principal(new PrincipalRef(AuthDomain.ADMIN,"other"),false)).isEmpty();assertThatThrownBy(()->change.emitCookies((d,c,t)->fail("타주체쿠키"))).isInstanceOf(IllegalStateException.class);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30122/23 기존유효getCsrf는세대/주체/CSRF를보존하고첫사용으로세지않는다")
    void existingCsrfPreparationPreservesGenerationAndDoesNotConfirmUse(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var runtime=fixture.runtime();var anonymous=fixture.anonymous(runtime,AuthDomain.ADMIN);var prepare=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PREPARE_CSRF,anonymous.cookie());runtime.execute(prepare,()->{prepare.prepareCsrf();return null;});var same=fixture.emit(runtime,prepare);assertThat(same).isEqualTo(anonymous);assertThat(fixture.ledger.find(same.ref(),false).orElseThrow().firstUsedAt()).isNull();
            var user=fixture.login(runtime,AuthDomain.ADMIN,same);var before=fixture.ledger.find(user.ref(),false).orElseThrow();var current=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PREPARE_CSRF,user.cookie());runtime.execute(current,()->{current.prepareCsrf();return null;});var kept=fixture.emit(runtime,current);assertThat(kept).isEqualTo(user);var after=fixture.ledger.find(user.ref(),false).orElseThrow();assertThat(after.subjectId()).isEqualTo(before.subjectId());assertThat(after.level()).isEqualTo(before.level());assertThat(after.firstUsedAt()).isNull();assertThat(after.confirmDeadline()).isEqualTo(before.confirmDeadline());
        }
    }
    @Test @DisplayName("TC-30123 두노드동시로그인은배타진입과PENDING예약으로한도1을넘지않는다")
    void simultaneousLoginsReserveOneSlot()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS);var executor=Executors.newFixedThreadPool(2)){
            var normal=fixture.runtime();var a=fixture.anonymous(normal,AuthDomain.ADMIN);var b=fixture.anonymous(normal,AuthDomain.ADMIN);var entered=new CountDownLatch(2);SessionAuthority owner=(context,exclusive,command)->{if(context.capability()==Capability.LOGIN&&context.identity()==null){assertThat(exclusive).isTrue();entered.countDown();await(entered);}fixture.authority.protect(context,exclusive,command);};var one=fixture.runtime(fixture.store,owner);var two=fixture.runtime(fixture.store,owner);
            Callable<SessionRuntime.Scope> first=()->{try{return fixture.stageLogin(one,AuthDomain.ADMIN,a,new SessionRuntime.SlotPolicy(true,1,true));}catch(Failure denied){return null;}};Callable<SessionRuntime.Scope> second=()->{try{return fixture.stageLogin(two,AuthDomain.ADMIN,b,new SessionRuntime.SlotPolicy(true,1,true));}catch(Failure denied){return null;}};var fa=executor.submit(first);var fb=executor.submit(second);var resultA=fa.get(8,TimeUnit.SECONDS);var resultB=fb.get(8,TimeUnit.SECONDS);assertThat((resultA==null?0:1)+(resultB==null?0:1)).isEqualTo(1);if(resultA!=null)fixture.emit(one,resultA);else fixture.emit(two,resultB);assertThat(fixture.ledger.principal(new PrincipalRef(AuthDomain.ADMIN,"owner"),false).stream().filter(grant->grant.reservesSlot(fixture.ledger.now())).count()).isEqualTo(1);
        }
    }
    @Test @DisplayName("TC-30123 publication만료PENDING은슬롯을놓고늦은발행으로부활하지않는다")
    void timedOutPendingReservationCanBeReused()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var runtime=fixture.runtime();var old=fixture.stageLogin(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN),new SessionRuntime.SlotPolicy(true,1,true));fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET PUBLISH_DEADLINE_AT=clock_timestamp()-interval '1 second' WHERE GRANT_ID=?",old.current().orElseThrow().ref().grantId());var fresh=fixture.emit(runtime,fixture.stageLogin(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN),new SessionRuntime.SlotPolicy(true,1,true)));assertThat(fresh.cookie()).hasSize(43);assertThatThrownBy(()->runtime.finish(old)).isInstanceOf(Failure.class);
        }
    }
    @Test @DisplayName("TC-30123 Redis실제저장뒤응답확인유실은PENDING회수·단일증거·쿠키0이다")
    void lostRedisWriteAcknowledgementDoesNotActivateOrReplay()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var ordinary=fixture.runtime();var entry=fixture.anonymous(ordinary,AuthDomain.ADMIN);var calls=new AtomicInteger();var failed=new WrappedStore(fixture.store){@Override public Stored create(Payload payload,Instant expires){super.create(payload,expires);calls.incrementAndGet();throw new org.springframework.data.redis.RedisConnectionFailureException("시험 저장확인 유실");}};var runtime=fixture.runtime(failed,fixture.authority);var pending=fixture.stageLogin(runtime,AuthDomain.ADMIN,entry,new SessionRuntime.SlotPolicy(true,1,true));assertThatThrownBy(()->runtime.finish(pending)).isInstanceOf(Failure.class);var ref=pending.current().orElseThrow().ref();assertThat(fixture.store.load(ref,new PrincipalRef(AuthDomain.ADMIN,"owner"))).isPresent();assertThat(fixture.ledger.find(ref,false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.REVOKED);assertThatThrownBy(()->pending.emitCookies((d,c,t)->fail("확인유실쿠키"))).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->runtime.finish(pending)).isInstanceOf(IllegalStateException.class);assertThat(calls).hasValue(1);
        }
    }
    @Test @DisplayName("TC-30123 관리회수후갱신확인유실만정리미완료이며일반쓰기실패는성공으로숨기지않는다")
    void committedRevocationAndOrdinaryRenewalFailureRemainDistinct()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var normal=fixture.runtime();var actor=fixture.login(normal,AuthDomain.ADMIN,fixture.anonymous(normal,AuthDomain.ADMIN));var target=fixture.login(normal,AuthDomain.ADMIN,fixture.anonymous(normal,AuthDomain.ADMIN));var failed=new WrappedStore(fixture.store){@Override public Outcome updateExisting(Stored expected,Payload changed,Instant expires){super.updateExisting(expected,changed,expires);throw new org.springframework.data.redis.RedisConnectionFailureException("시험 갱신확인 유실");}};var runtime=fixture.runtime(failed,fixture.authority);
            var op=new ModuleApiCatalog.Operation("kill","POST","/api/system/service/user/killSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"EXECUTE",false,Capability.READ,null,null,true);var scope=runtime.open(new SessionExchange(AuthDomain.ADMIN,"kill",Capability.READ),op,actor.cookie());var plan=runtime.prepare(new PrincipalRef(AuthDomain.ADMIN,"owner"));scope.bindInspection(plan);String management=fixture.ledger.find(target.ref(),false).orElseThrow().managementRef();assertThat(runtime.execute(scope,()->scope.revoke(plan,List.of(management)))).isEqualTo(1);assertThat(runtime.finish(scope)).isEqualTo(Outcome.APPLIED);assertThat(scope.cleanupFailures()).isEqualTo(1);assertThat(fixture.ledger.find(target.ref(),false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.REVOKED);
            var ordinary=fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,actor.cookie());var effects=new AtomicInteger();runtime.execute(ordinary,()->effects.incrementAndGet());assertThatThrownBy(()->runtime.finish(ordinary)).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.UNAVAILABLE));assertThat(effects).hasValue(1);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30123 응답writer실패는DB를보상/재실행하지않고쿠키증거를단일종료한다")
    void deliveryWriterFailureDoesNotReexecuteDatabase(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var runtime=fixture.runtime();var scope=fixture.stageLogin(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN),new SessionRuntime.SlotPolicy(true,1,true));assertThat(runtime.finish(scope)).isEqualTo(Outcome.DELIVERABLE);var writes=new AtomicInteger();assertThatThrownBy(()->scope.emitCookies((domain,cookie,csrf)->{writes.incrementAndGet();throw new java.io.UncheckedIOException(new java.io.IOException("시험 출력 유실"));})).isInstanceOf(java.io.UncheckedIOException.class);assertThatThrownBy(()->scope.emitCookies((d,c,t)->writes.incrementAndGet())).isInstanceOf(IllegalStateException.class);assertThat(writes).hasValue(1);assertThat(fixture.ledger.find(scope.current().orElseThrow().ref(),false).orElseThrow().firstUsedAt()).isNull();assertThat(scope.exchange().state()).isEqualTo(SessionExchange.State.CLOSED);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30122/28 delta는두provider의표시/언어/캐시만바꾸고다른DTO대상은403이다")
    void snapshotDeltaAndInspectionTargetAreBound(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var runtime=fixture.runtime();var user=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var before=fixture.store.load(user.ref(),new PrincipalRef(AuthDomain.ADMIN,"owner")).orElseThrow();var scope=fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie());runtime.execute(scope,()->{scope.updateSnapshot("바뀐표시명","en",Set.of("CALCULATED:READ"));return null;});runtime.finish(scope);var after=fixture.store.load(user.ref(),new PrincipalRef(AuthDomain.ADMIN,"owner")).orElseThrow();
            assertThat(after.payload().identity().displayName()).isEqualTo("바뀐표시명");assertThat(after.payload().identity().locale()).isEqualTo("en");assertThat(after.payload().authoritySnapshot()).containsExactly("CALCULATED:READ");assertThat(SessionPayloadCodec.mutableOnly(before.payload(),after.payload())).isTrue();assertThat(after.payload().csrf()).isEqualTo(before.payload().csrf());
            var op=new ModuleApiCatalog.Operation("sessions","POST","/api/system/service/user/getSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"READ",false,Capability.READ,null,null);var guarded=runtime.open(new SessionExchange(AuthDomain.ADMIN,"wrong-target",Capability.READ),op,user.cookie());guarded.bindInspection(runtime.prepare(new PrincipalRef(AuthDomain.ADMIN,"owner")));
            assertThatThrownBy(()->runtime.execute(guarded,()->{guarded.inspectionFor(new PrincipalRef(AuthDomain.ADMIN,"different"));fixture.effect("wrong-target");return null;})).isInstanceOfSatisfying(com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected.class,failure->assertThat(failure.status()).isEqualTo(403));assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isZero();
        }
    }
    @Test @DisplayName("TC-30126 원장청크정리는보호중행을skip하며정렬잠금과교착하지않는다")
    void ledgerCleanupSkipsLockedExpiredRows()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.JDBC);var executor=Executors.newFixedThreadPool(2)){
            var runtime=fixture.runtime();var first=fixture.anonymous(runtime,AuthDomain.ADMIN);fixture.anonymous(runtime,AuthDomain.ADMIN);fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET EXPIRES_AT=clock_timestamp()-interval '25 hour'");var entered=new CountDownLatch(1);var resume=new CountDownLatch(1);
            var guard=executor.submit(()->fixture.transactions.required(()->{fixture.ledger.find(first.ref(),true);entered.countDown();await(resume);return null;}));await(entered);
            try{assertThat(executor.submit(()->fixture.ledger.cleanup()).get(3,TimeUnit.SECONDS)).isEqualTo(1);}finally{resume.countDown();}guard.get(8,TimeUnit.SECONDS);assertThat(fixture.ledger.cleanup()).isEqualTo(1);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30126 새쿠키의service첫사용과미래DB최종접근/기한은보존된다")
    void serviceFirstUseAndMonotonicDatabaseTimes(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var runtime=fixture.runtime();var user=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var original=fixture.ledger.find(user.ref(),false).orElseThrow();
            var op=new ModuleApiCatalog.Operation("first-service","POST","/api/system/service/item/save",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"WRITE",false,Capability.READ,null,null);
            var scope=runtime.open(new SessionExchange(AuthDomain.ADMIN,"first-service",Capability.READ),op,user.cookie());runtime.execute(scope,()->{fixture.effect("first-service");return null;});runtime.finish(scope);assertThat(fixture.ledger.find(user.ref(),false).orElseThrow().firstUsedAt()).isNotNull();
            fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET LAST_ACCESS_AT=clock_timestamp()+interval '1 hour',EXPIRES_AT=clock_timestamp()+interval '2 hour',CONFIRM_DEADLINE_AT=clock_timestamp()-interval '1 second' WHERE GRANT_ID=?",user.ref().grantId());var future=fixture.ledger.find(user.ref(),false).orElseThrow();
            var read=fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie());runtime.execute(read,()->null);runtime.finish(read);var after=fixture.ledger.find(user.ref(),false).orElseThrow();assertThat(after.lastAccessAt()).isAfterOrEqualTo(future.lastAccessAt());assertThat(after.expiresAt()).isAfterOrEqualTo(future.expiresAt());assertThat(after.createdAt()).isEqualTo(original.createdAt());
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30123 LOGINguard대기동안DB만료된entry는callback전에거부한다")
    void expiredLoginEntryCannotRunOrRenew(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider);var executor=Executors.newSingleThreadExecutor()){
            var ordinary=fixture.runtime();var entry=fixture.anonymous(ordinary,AuthDomain.ADMIN);var entered=new CountDownLatch(1);var resume=new CountDownLatch(1);var effects=new AtomicInteger();
            var runtime=fixture.runtime(fixture.store,(context,exclusive,command)->fixture.authority.protect(context,exclusive,()->{assertThat(exclusive).isTrue();entered.countDown();await(resume);command.run();}));var scope=fixture.scope(runtime,AuthDomain.ADMIN,Capability.LOGIN,entry.cookie());
            var work=executor.submit(()->runtime.execute(scope,()->effects.incrementAndGet()));await(entered);fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET EXPIRES_AT=clock_timestamp()-interval '1 second' WHERE GRANT_ID=?",entry.ref().grantId());resume.countDown();assertThatThrownBy(()->work.get(8,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);assertThat(effects).hasValue(0);assertThat(fixture.ledger.find(entry.ref(),false).orElseThrow().expiresAt()).isBefore(fixture.ledger.now());
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30127 서로다른관리자의상대목록조회는두TX에서전체grant정렬잠금으로교착0이다")
    void twoAdministratorsInspectEachOtherWithoutLockCycle(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider);var executor=Executors.newFixedThreadPool(2)){
            fixture.jdbc.update("INSERT INTO AUTH_OWNER VALUES('ADMIN','other',true,true)");var normal=fixture.runtime();var a=fixture.login(normal,AuthDomain.ADMIN,fixture.anonymous(normal,AuthDomain.ADMIN));var bEntry=fixture.anonymous(normal,AuthDomain.ADMIN);var bScope=fixture.scope(normal,AuthDomain.ADMIN,Capability.LOGIN,bEntry.cookie());
            normal.execute(bScope,()->{var identity=new com.aiagent.core.security.SessionPersistencePort.Identity(new PrincipalRef(AuthDomain.ADMIN,"other"),AuthLevel.AUTHENTICATED,"LOCAL",null,fixture.ledger.now(),"다른관리자","ko");bScope.publish(fixture.producers.get(AuthDomain.ADMIN).login(bScope.exchange(),bEntry.ref(),identity,fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;});var b=fixture.emit(normal,bScope);
            var entered=new CountDownLatch(2);SessionAuthority guarded=(context,exclusive,command)->fixture.authority.protect(context,exclusive,()->{entered.countDown();await(entered);command.run();});var one=fixture.runtime(fixture.store,guarded);var two=fixture.runtime(fixture.store,guarded);
            var op=new ModuleApiCatalog.Operation("sessions","POST","/api/system/service/user/getSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"READ",false,Capability.READ,null,null);
            var first=one.open(new SessionExchange(AuthDomain.ADMIN,"inspect-A",Capability.READ),op,a.cookie());var second=two.open(new SessionExchange(AuthDomain.ADMIN,"inspect-B",Capability.READ),op,b.cookie());var targetB=one.prepare(new PrincipalRef(AuthDomain.ADMIN,"other"));var targetA=two.prepare(new PrincipalRef(AuthDomain.ADMIN,"owner"));first.bindInspection(targetB);second.bindInspection(targetA);
            var futureA=executor.submit(()->{var rows=one.execute(first,()->first.inspect(targetB));one.finish(first);return rows;});var futureB=executor.submit(()->{var rows=two.execute(second,()->second.inspect(targetA));two.finish(second);return rows;});assertThat(futureA.get(8,TimeUnit.SECONDS)).hasSize(1);assertThat(futureB.get(8,TimeUnit.SECONDS)).hasSize(1);
        }
    }
    @Test @DisplayName("TC-30123 ACTIVE최종commitUNKNOWN은60초슬롯예약후새검증으로복구한다")
    void finalActivationUnknownDoesNotLockOutForever()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var ordinary=fixture.runtime();var entry=fixture.anonymous(ordinary,AuthDomain.ADMIN);
            var uncertain=new WrappedStore(fixture.store){@Override public Stored create(Payload payload,Instant expires){var stored=super.create(payload,expires);fixture.manager.unknown=true;return stored;}};var runtime=fixture.runtime(uncertain,fixture.authority);var pending=fixture.stageLogin(runtime,AuthDomain.ADMIN,entry,new SessionRuntime.SlotPolicy(true,1,true));
            assertThatThrownBy(()->runtime.finish(pending)).isInstanceOf(Failure.class);assertThatThrownBy(()->pending.emitCookies((d,c,t)->fail("불명ACTIVE쿠키"))).isInstanceOf(IllegalStateException.class);var grant=fixture.ledger.find(pending.current().orElseThrow().ref(),false).orElseThrow();assertThat(grant.status()).isEqualTo(SessionAdmissionLedger.Status.ACTIVE);assertThat(grant.firstUsedAt()).isNull();
            var next=fixture.anonymous(ordinary,AuthDomain.ADMIN);assertThatThrownBy(()->fixture.stageLogin(ordinary,AuthDomain.ADMIN,next,new SessionRuntime.SlotPolicy(true,1,true))).isInstanceOf(Failure.class);
            fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET CONFIRM_DEADLINE_AT=clock_timestamp()-interval '1 second' WHERE GRANT_ID=?",grant.ref().grantId());assertThat(fixture.emit(ordinary,fixture.stageLogin(ordinary,AuthDomain.ADMIN,next,new SessionRuntime.SlotPolicy(true,1,true))).cookie()).hasSize(43);
        }
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(8,TimeUnit.SECONDS))throw new AssertionError("시험 barrier 기한 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("시험 중단",failure);}}
    abstract static class WrappedStore implements SessionStoreAdapter {
        final SessionStoreAdapter actual;WrappedStore(SessionStoreAdapter actual){this.actual=actual;}
        public Provider provider(){return actual.provider();}public void verifyAvailable(){actual.verifyAvailable();}public Optional<Stored> load(Ref ref,PrincipalRef principal){return actual.load(ref,principal);}public Stored create(Payload payload,Instant expires){return actual.create(payload,expires);}public Outcome updateExisting(Stored expected,Payload changed,Instant expires){return actual.updateExisting(expected,changed,expires);}public Outcome delete(Stored expected){return actual.delete(expected);}
        public int cleanupExpired(){return actual.cleanupExpired();}
    }
    @Test @DisplayName("TC-30127 실제PG속성중간SQL실패는JPA이력/부모/속성/원장을함께rollback한다")
    void middleAttributeInsertFailureRollsBackJpaAndLedger()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.JDBC)){
            fixture.jdbc.execute("ALTER TABLE SPRING_SESSION_ATTRIBUTES ADD CONSTRAINT FAIL_MIDDLE CHECK(ATTRIBUTE_NAME<>'AUTH_V2_PAYLOAD')");var runtime=fixture.runtime();var scope=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PREPARE_CSRF,null);
            assertThatThrownBy(()->runtime.execute(scope,()->{fixture.effect("rollback");scope.prepareCsrf();return null;})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            for(String table:List.of("AUTH307_CREDENTIAL","AI_AUTH_SESSION_GRANT","SPRING_SESSION","SPRING_SESSION_ATTRIBUTES"))assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).as(table).isZero();
            assertThatThrownBy(()->scope.emitCookies((d,c,t)->fail("커밋전쿠키"))).isInstanceOf(IllegalStateException.class);
            fixture.jdbc.execute("ALTER TABLE SPRING_SESSION_ATTRIBUTES DROP CONSTRAINT FAIL_MIDDLE");var issued=fixture.anonymous(runtime,AuthDomain.ADMIN);assertThat(issued.cookie()).hasSize(43);
            String physical=fixture.jdbc.queryForObject("SELECT PRIMARY_ID FROM SPRING_SESSION",String.class);fixture.jdbc.update("UPDATE SPRING_SESSION SET EXPIRY_TIME=0 WHERE PRIMARY_ID=?",physical);assertThat(fixture.store.cleanupExpired()).isEqualTo(1);assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES",Integer.class)).isZero();
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30123/27 실제JPA DBrollback/최종commitUNKNOWN은쿠키0·증거재사용0이다")
    void physicalJpaRollbackAndUnknownCommit(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var runtime=fixture.runtime();var old=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));
            var scope=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PASSWORD_CHANGE,old.cookie());
            assertThatThrownBy(()->runtime.execute(scope,()->{fixture.effect("rollback");scope.publish(fixture.producers.get(AuthDomain.ADMIN).rotation(scope.exchange(),old.ref(),fixture.identity(AuthDomain.ADMIN,AuthLevel.AUTHENTICATED),fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));throw new IllegalStateException("시험 이력 뒤 실패");})).isInstanceOf(IllegalStateException.class);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isZero();assertThat(fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,old.cookie()).current()).isPresent();
            var uncertain=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PASSWORD_CHANGE,old.cookie());fixture.manager.unknown=true;
            assertThatThrownBy(()->runtime.execute(uncertain,()->{fixture.effect("committed-unknown");uncertain.publish(fixture.producers.get(AuthDomain.ADMIN).rotation(uncertain.exchange(),old.ref(),fixture.identity(AuthDomain.ADMIN,AuthLevel.AUTHENTICATED),fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;})).isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(1);assertThatThrownBy(()->uncertain.emitCookies((d,c,t)->fail("불명쿠키"))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"account","revoke","epoch"}) @DisplayName("TC-30123 Redis발행대기중별도권한회수TX가끝나며늦은ACTIVE/쿠키0이다")
    void publicationDoesNotHoldAdminOrDatabaseLock(String change)throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS);var executor=Executors.newFixedThreadPool(2)){
            var ordinary=fixture.runtime();var entry=fixture.anonymous(ordinary,AuthDomain.ADMIN);var reached=new CountDownLatch(1);var resume=new CountDownLatch(1);
            var delayed=new WrappedStore(fixture.store){@Override public Stored create(Payload payload,Instant expires){reached.countDown();await(resume);return super.create(payload,expires);}};
            var runtime=fixture.runtime(delayed,fixture.authority);var pending=fixture.stageLogin(runtime,AuthDomain.ADMIN,entry,new SessionRuntime.SlotPolicy(true,1,true));
            Future<?> publish=executor.submit(()->runtime.finish(pending));await(reached);
            try{executor.submit(()->fixture.transactions.required(()->{fixture.jdbc.execute("SELECT pg_advisory_xact_lock(308000)");if(change.equals("account"))fixture.jdbc.update("UPDATE AUTH_OWNER SET ENABLED=false WHERE AUTH_DOMAIN='ADMIN' AND SUBJECT_ID='owner'");else if(change.equals("revoke"))fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET STATE_CD='REVOKED',TERMINAL_AT=clock_timestamp() WHERE GRANT_ID=?",pending.current().orElseThrow().ref().grantId());else{var tool=new SessionProviderTransition(fixture.source,fixture.manager);tool.drain(AuthDomain.ADMIN,fixture.selected.get(AuthDomain.ADMIN));tool.switchDrained(AuthDomain.ADMIN,fixture.selected.get(AuthDomain.ADMIN),new SessionAdmissionLedger.Settings(Provider.REDIS,UUID.randomUUID(),"after-epoch"));}return null;})).get(3,TimeUnit.SECONDS);}finally{resume.countDown();}
            assertThatThrownBy(()->publish.get(8,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);assertThatThrownBy(()->pending.emitCookies((d,c,t)->fail("회수뒤쿠키"))).isInstanceOf(IllegalStateException.class);
            assertThat(fixture.ledger.find(pending.current().orElseThrow().ref(),false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.REVOKED);
        }
    }
    @Test @DisplayName("TC-30123 PENDING한도/역순publication/실패예약해제는실제두TX에서보존된다")
    void pendingSlotsPreventLoginAndCannotPublishInReverse()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS);var executor=Executors.newFixedThreadPool(2)){
            var runtime=fixture.runtime();var first=fixture.anonymous(runtime,AuthDomain.ADMIN);var second=fixture.anonymous(runtime,AuthDomain.ADMIN);
            var a=fixture.stageLogin(runtime,AuthDomain.ADMIN,first,new SessionRuntime.SlotPolicy(true,1,true));
            assertThatThrownBy(()->fixture.stageLogin(runtime,AuthDomain.ADMIN,second,new SessionRuntime.SlotPolicy(true,1,true))).isInstanceOf(Failure.class);
            var b=fixture.stageLogin(runtime,AuthDomain.ADMIN,second,new SessionRuntime.SlotPolicy(true,1,false));var issued=fixture.emit(runtime,b);
            assertThatThrownBy(()->runtime.finish(a)).isInstanceOf(Failure.class);assertThatThrownBy(()->a.emitCookies((d,c,t)->fail("구예약쿠키"))).isInstanceOf(IllegalStateException.class);
            var read=fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,issued.cookie());runtime.execute(read,()->null);runtime.finish(read);
            fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET CONFIRM_DEADLINE_AT=clock_timestamp()-interval '1 second' WHERE GRANT_ID=?",issued.ref().grantId());assertThat(fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,issued.cookie()).current()).isPresent();
            // 정상 first-use 접속은 예약기한 경과로 밀어내지 않는다.
            assertThatThrownBy(()->fixture.stageLogin(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN),new SessionRuntime.SlotPolicy(true,1,true))).isInstanceOf(Failure.class);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30124/26 두노드동일generation revision경합은flush만폐기하고업무를재실행하지않는다")
    void twoNodesStaleRevisionAndRevocation(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var first=fixture.runtime();var second=fixture.runtime();var user=fixture.login(first,AuthDomain.ADMIN,fixture.anonymous(first,AuthDomain.ADMIN));var a=fixture.scope(first,AuthDomain.ADMIN,Capability.READ,user.cookie());var b=fixture.scope(second,AuthDomain.ADMIN,Capability.READ,user.cookie());var effects=new AtomicInteger();
            first.execute(a,()->effects.incrementAndGet());second.execute(b,()->effects.incrementAndGet());assertThat(second.finish(b)).isEqualTo(Outcome.APPLIED);assertThat(first.finish(a)).isEqualTo(Outcome.APPLIED);assertThat(effects).hasValue(2);
            var stale=fixture.scope(first,AuthDomain.ADMIN,Capability.READ,user.cookie());first.execute(stale,()->effects.incrementAndGet());var logout=fixture.scope(second,AuthDomain.ADMIN,Capability.LOGOUT,user.cookie());second.execute(logout,()->{logout.logout();return null;});fixture.emit(second,logout);
            if(provider==Provider.REDIS)assertThatThrownBy(()->first.finish(stale)).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.INVALID_SESSION));
            else assertThat(first.finish(stale)).isEqualTo(Outcome.APPLIED);
            assertThat(effects).hasValue(3);assertThatThrownBy(()->fixture.scope(first,AuthDomain.ADMIN,Capability.READ,user.cookie())).isInstanceOf(Failure.class);
        }
    }
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30127 guard및command중숨은REQUIRES_NEW는저장전에거부하고효과0이다")
    void rejectsTransactionSwitchBeforeEffects(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            var normal=fixture.runtime();var user=fixture.login(normal,AuthDomain.ADMIN,fixture.anonymous(normal,AuthDomain.ADMIN));
            var requiresNew=new TransactionTemplate(fixture.manager);requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var wrong=fixture.runtime(fixture.store,(context,exclusive,command)->requiresNew.executeWithoutResult(status->command.run()));var entry=fixture.scope(wrong,AuthDomain.ADMIN,Capability.READ,user.cookie());
            assertThatThrownBy(()->wrong.execute(entry,()->{fixture.effect("guard-wrong");return null;})).isInstanceOf(IllegalStateException.class);
            var inside=fixture.scope(normal,AuthDomain.ADMIN,Capability.LOGOUT,user.cookie());assertThatThrownBy(()->normal.execute(inside,()->requiresNew.execute(status->{inside.logout();fixture.effect("command-wrong");return null;}))).isInstanceOf(IllegalStateException.class);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isZero();assertThat(fixture.scope(normal,AuthDomain.ADMIN,Capability.READ,user.cookie()).current()).isPresent();
        }
    }
    @Test @DisplayName("TC-30124 실제Redis과거snapshot복구·원장정리·DB원천장애에접속부활/fallback0")
    void restoredPayloadCannotResurrectRevokedGrant()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var runtime=fixture.runtime();var user=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));String key="aihub:auth:v2:ADMIN:"+user.ref().epoch()+":"+user.ref().grantId()+":"+user.ref().generation();var redis=new StringRedisTemplate(fixture.redis);var snapshot=redis.opsForHash().entries(key);
            var logout=fixture.scope(runtime,AuthDomain.ADMIN,Capability.LOGOUT,user.cookie());runtime.execute(logout,()->{logout.logout();return null;});fixture.emit(runtime,logout);redis.opsForHash().putAll(key,snapshot);redis.expire(key,Duration.ofMinutes(30));assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie())).isInstanceOf(Failure.class);
            fixture.jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET TERMINAL_AT=clock_timestamp()-interval '25 hour' WHERE GRANT_ID=?",user.ref().grantId());assertThat(fixture.ledger.cleanup()).isEqualTo(1);assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie())).isInstanceOf(Failure.class);
            fixture.jdbc.execute("ALTER TABLE AI_AUTH_SESSION_GRANT RENAME TO LOST_LEDGER");assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(redis.hasKey(key)).isTrue();
        }
    }
}
