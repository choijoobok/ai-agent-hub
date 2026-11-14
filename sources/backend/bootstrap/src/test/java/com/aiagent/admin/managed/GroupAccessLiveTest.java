// @feat FEAT-303
package com.aiagent.admin.managed;

import static com.aiagent.core.access.GroupAccess.*;
import static com.aiagent.core.access.CurrentDataAccess.*;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.portal.*;
import com.aiagent.core.access.*;
import com.aiagent.core.security.*;
import com.aiagent.support.config.ConfigLookup;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupAccessLiveTest {
    private ManagedLiveFixture f;private String user,group,month;private Request request;private TransactionTemplate tx;
    @org.springframework.boot.test.context.TestConfiguration
    static class Ports {
        static volatile boolean failed;
        static volatile Requirements requirements=new Requirements(false,null,null,Set.of());
        @Bean PortalDataPolicySource groupAccessPolicy(DataSource source){return new PortalDataPolicySource() {
            public String assetKind(){return "TEST_CATALOG";}
            public DataSource dataSource(){return source;}
            public Policy read(Request request) {
                if(failed)throw new IllegalStateException("시험 정책 원천 장애");
                var rows=new JdbcTemplate(source).queryForList("SELECT group_id,revision FROM fx_group_policy");
                var groups=new HashSet<String>();rows.forEach(r->groups.add(r.get("group_id").toString()));
                String revision=rows.isEmpty()?"revoked":rows.getFirst().get("revision").toString();
                return new Policy(request.target(),true,List.of(new Gate("table",request.target(),revision,groups)),
                        Map.of("name",new Field("table",ValueType.STRING,null,Grant.raw(),Map.of()),"secret",new Field("table",ValueType.STRING,null,Grant.denied(),Map.of())),Set.of(),requirements);
            }
        };}
    }
    @BeforeAll void start()throws Exception {
        f=new ManagedLiveFixture("LOCAL",true,Ports.class);tx=new TransactionTemplate(f.bean(PlatformTransactionManager.class));
        month=f.database().queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')",String.class);
        f.database().update("INSERT INTO ai_admin_quota_grade VALUES(900303,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES(900303,?,'판정 시험',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES(900303,900303,'기본',1,1,NULL,'')");
        group=f.groupCall("save",command(null,null,"definition",Map.of("groupNm","접근 시험","description","합성 담당")),200).path("targetId").asText();
        user=f.adminCall("portalUser","save",command(null,null,"profile",profile("N")),200).path("targetId").asText();
        f.adminCall("portalUser","save",command(user,"1","memberships",members("Y",false)),200);
        f.adminCall("portalUser","save",command(user,"2","profile",profile("Y")),200);
        String secret=f.adminCall("portalUser","resetCredential",command(user,"3","resetCredential",Map.of()),200).path("password").asText();
        var principal=new PrincipalRef(AuthDomain.PORTAL,user);
        f.bean(ConfigLookup.class).inNewSnapshot(()->{var account=f.bean(PortalAccountService.class);var verified=account.verifyLocalCredentials("access303",secret);return account.changeOwnLocalPassword(principal,verified.credentialVersion(),secret,"Access303!Changed9");});
        request=new Request(principal,Purpose.RESULT,new Target("TEST_CATALOG","303","content-1"),Set.of("name","secret"),"trace-303");
        f.database().execute("CREATE TABLE fx_group_policy(group_id varchar(50),revision varchar(50))");
        f.database().update("INSERT INTO fx_group_policy VALUES(?,'policy-1')",group);
        f.database().execute("CREATE TABLE fx_group_acceptance(value varchar(100))");
    }
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String id,String revision,String scope,Map<String,Object> values){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,scope,values,"현재 접근 검증");}
    private Map<String,Object> profile(String active){var values=new HashMap<String,Object>(Map.of("loginId","access303","userNm","접근 시험","organizationNm","시험 조직","activeYn",active,"useStartDt",month+"-01"));values.put("useEndDt",null);return values;}
    private Map<String,Object> members(String active,boolean deactivate){return Map.of("memberships",List.of(Map.of("groupId",group,"activeYn",active,"memo","시험")),"deactivateUser",deactivate);}
    private CurrentDataAccess access(){return f.bean(CurrentDataAccess.class);}
    private long effects(){return f.database().queryForObject("SELECT COUNT(*) FROM fx_group_acceptance",Long.class);}
    private void write(Runnable change){tx.executeWithoutResult(s->{f.bean(PortalPolicy.class).protect(true);change.run();});}
    private Outcome<String> accept(){return access().withCurrentAccess(request,use->{f.database().update("INSERT INTO fx_group_acceptance VALUES('accepted')");return "accepted";});}
    @Test @Order(1) @DisplayName("TC-3031 TC-3032 TC-3034 실제 계정/그룹 현재값과 제한 투영·과거 판 차단")
    void currentOwnerProjectionAndPolicyRevision() {
        var previous=access().withCurrentAccess(request,use->use.decision().binding());assertThat(previous.status()).isEqualTo(OutcomeStatus.COMPLETED);
        write(()->f.database().update("UPDATE fx_group_policy SET revision='policy-2'"));
        var projected=access().withCurrentAccess(request,use->{
            var old=use.project(new Payload(previous.value(),List.of(Map.of("name","PRIVATE_NAME"))));assertThat(old.status()).isEqualTo(ProjectionStatus.MISMATCH);assertThat(old.rows()).isEmpty();
            return use.project(new Payload(use.decision().binding(),List.of(Map.of("name","허용 값","secret","PRIVATE_SECRET"))));
        });
        assertThat(projected.status()).isEqualTo(OutcomeStatus.COMPLETED);assertThat(projected.value().rows()).containsExactly(Map.of("name","허용 값"));
        assertThat(access().withCurrentAccess(new Request(new PrincipalRef(AuthDomain.ADMIN,user),request.purpose(),request.target(),request.fields(),request.traceId()),u->"invalid").status()).isEqualTo(OutcomeStatus.DENIED);
    }
    @Test @Order(2) @DisplayName("TC-3033 미설치/원천 장애·추가 관문 차단과 수락 이력 롤백")
    void missingFailedSourcesControlsAndRollback() {
        long before=effects();
        assertThat(access().withCurrentAccess(new Request(request.principal(),Purpose.RESULT,new Target("API","303","1"),Set.of(),"unknown"),u->"invalid").status()).isEqualTo(OutcomeStatus.UNAVAILABLE);
        Ports.failed=true;
        try{assertThat(accept().status()).isEqualTo(OutcomeStatus.UNAVAILABLE);}finally{Ports.failed=false;}
        for(var requirements:List.of(new Requirements(true,null,null,Set.of()),new Requirements(false,null,null,Set.of(Control.CONSENT)),new Requirements(false,"SYSTEM","700001",Set.of()))) {
            Ports.requirements=requirements;
            try{assertThat(accept().status()).isNotEqualTo(OutcomeStatus.COMPLETED);}finally{Ports.requirements=new Requirements(false,null,null,Set.of());}
        }
        assertThatThrownBy(()->access().withCurrentAccess(request,use->{f.database().update("INSERT INTO fx_group_acceptance VALUES('rollback')");throw new IllegalStateException("시험 수락 실패");})).isInstanceOf(IllegalStateException.class);
        assertThat(effects()).isEqualTo(before);
    }
    @Test @Order(3) @DisplayName("TC-3035 실제 두 트랜잭션 정책 회수/수락 양 순서와 현재 소속 회수")
    void revocationSerializesWithAcceptanceInBothOrders()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var accepted=workers.submit(()->access().withCurrentAccess(request,use->{entered.countDown();await(release);f.database().update("INSERT INTO fx_group_acceptance VALUES('first')");return "first";}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var revoked=workers.submit(()->{started.countDown();write(()->f.database().update("DELETE FROM fx_group_policy"));});
            try{assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->revoked.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            assertThat(accepted.get(10,TimeUnit.SECONDS).status()).isEqualTo(OutcomeStatus.COMPLETED);revoked.get(10,TimeUnit.SECONDS);
            assertThat(accept().status()).isEqualTo(OutcomeStatus.DENIED);
            write(()->f.database().update("INSERT INTO fx_group_policy VALUES(?,'policy-3')",group));
            var revokedFirst=new CountDownLatch(1);var allowCommit=new CountDownLatch(1);var readerStarted=new CountDownLatch(1);
            var writer=workers.submit(()->write(()->{f.database().update("DELETE FROM fx_group_policy");revokedFirst.countDown();await(allowCommit);}));
            assertThat(revokedFirst.await(10,TimeUnit.SECONDS)).isTrue();
            var reader=workers.submit(()->{readerStarted.countDown();return accept();});
            try{assertThat(readerStarted.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->reader.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{allowCommit.countDown();}
            writer.get(10,TimeUnit.SECONDS);assertThat(reader.get(10,TimeUnit.SECONDS).status()).isEqualTo(OutcomeStatus.DENIED);
        }
        write(()->f.database().update("INSERT INTO fx_group_policy VALUES(?,'policy-4')",group));
        var revision=f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",String.class,Long.valueOf(user));
        f.adminCall("portalUser","save",command(user,revision,"memberships",members("N",true)),200);
        assertThat(accept().status()).isEqualTo(OutcomeStatus.DENIED);assertThat(effects()).isEqualTo(1);
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("시험 보호 시간 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("시험 중단",failure);}}
}
