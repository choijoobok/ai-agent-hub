// @feat FEAT-301
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.api.auth.v2.*;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.security.*;
import com.aiagent.core.security.portal.PortalAccounts;
import com.aiagent.core.security.v2.*;
import com.aiagent.portal.auth.*;
import com.aiagent.support.config.ConfigLookup;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;

@Tag("managedStorageLive")
class PortalExternalSessionLiveTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Provider {
        @Bean ExternalAuthProvider externalProvider(){return new ExternalAuthProvider(){
            public String sourceKey(){return "test-hr";}public String issuer(){return "https://issuer.fixture";}public String audience(){return "hub-fixture";}public boolean testOnly(){return true;}
            public Proof verify(Object evidence){return (Proof)evidence;}
        };}
    }
    private ManagedCommand command(String id,String revision,String scope,Map<String,Object> values){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,scope,values,"외부 인증 수락 경계 시험");}
    private SessionRuntime.Scope scope(ManagedLiveFixture f,ManagedLiveFixture.Browser browser) {
        var module=f.bean(PortalAuthModule.class);var operation=module.operations().stream().filter(o->o.id().equals("API-302")).findFirst().orElseThrow();
        return f.bean(SessionV2Assembly.class).runtime().open(new SessionExchange(AuthDomain.PORTAL,UUID.randomUUID().toString(),RequestPersistenceState.Capability.LOGIN,module.producer()),operation,browser.cookies.get("AIHUB_PORTAL_SESSION"));
    }
    private ManagedLiveFixture.Browser anonymous(ManagedLiveFixture f)throws Exception {var browser=f.new Browser();assertThat(browser.call("GET","/api/portal/public/auth/getCsrf",null).statusCode()).isEqualTo(200);return browser;}
    private ExternalAuthProvider.Proof proof(PortalExternalAuthService.Challenge challenge,String issuer,String nonce,Instant expiry) {
        var identity=PortalExternalLiveTest.Directory.VALID;
        return new ExternalAuthProvider.Proof(new PortalAccounts.ExternalIdentity(identity.sourceKey(),identity.subject(),identity.loginId(),identity.userNm(),identity.organizationNm(),identity.sourceRevision()),issuer,"hub-fixture","assertion-301",nonce,Instant.now().minusSeconds(1),expiry);
    }
    @Test @DisplayName("TC-3017 TC-30111 검증 대역·실제 증거 저장 단회 수락·만료/위조/재생 거부")
    void externalEvidenceCannotReplayOrDowngrade()throws Exception {
        try(var f=new ManagedLiveFixture("EXTERNAL",true,PortalExternalLiveTest.Directory.class,Provider.class)) {
            String month=f.database().queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')",String.class);
            f.database().update("INSERT INTO ai_admin_quota_grade VALUES(900306,CURRENT_TIMESTAMP)");f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES(900306,?,'외부 인증',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES(900306,900306,'기본',1,1,NULL,'')");
            var values=new HashMap<String,Object>(Map.of("externalSource","test-hr","externalSubject","subject-1","useStartDt",month+"-01","activeYn","N"));values.put("useEndDt",null);
            String id=f.adminCall("portalUser","save",command(null,null,"profile",values),200).path("targetId").asText();
            String group=f.groupCall("save",command(null,null,"definition",Map.of("groupNm","외부 인증","description","외부 인증 담당")),200).path("targetId").asText();
            f.adminCall("portalUser","save",command(id,"1","memberships",Map.of("memberships",List.of(Map.of("groupId",group,"activeYn","Y","memo","인증 시험 연결")),"deactivateUser",false)),200);
            values=new HashMap<>(Map.of("loginId","admin","userNm","원천 이용자","organizationNm","원천 조직","useStartDt",month+"-01","activeYn","Y"));values.put("useEndDt",null);f.adminCall("portalUser","save",command(id,"2","profile",values),200);
            var browser=anonymous(f);assertThat(browser.call("POST","/api/portal/public/auth/login",Map.of("loginId","admin","password","anything")).statusCode()).isEqualTo(422);
            var external=f.bean(PortalExternalAuthService.class);var runtime=f.bean(SessionV2Assembly.class).runtime();var configs=f.bean(ConfigLookup.class);var context=scope(f,browser);
            var challenge=external.begin(context,"test-hr","//evil.invalid");assertThat(challenge.returnPath()).isEqualTo("/");
            assertThatThrownBy(()->external.verify("test-hr",proof(challenge,"wrong-issuer",challenge.nonce(),Instant.now().plusSeconds(60)))).isInstanceOf(Rejected.class);
            var wrongNonce=external.verify("test-hr",proof(challenge,"https://issuer.fixture","wrong-nonce",Instant.now().plusSeconds(60)));
            assertThatThrownBy(()->configs.inNewSnapshot(()->external.accept(runtime,scope(f,browser),challenge.state(),challenge.browserBinding(),wrongNonce))).isInstanceOf(Rejected.class);
            var verified=external.verify("test-hr",proof(challenge,"https://issuer.fixture",challenge.nonce(),Instant.now().plusSeconds(60)));
            assertThatThrownBy(()->configs.inNewSnapshot(()->external.accept(runtime,scope(f,browser),challenge.state(),"other-browser",verified))).isInstanceOf(Rejected.class);
            var first=scope(f,browser);var second=scope(f,browser);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var gate=new CountDownLatch(1);
                var a=pool.submit(()->{gate.await();try {return configs.inNewSnapshot(()->external.accept(runtime,first,challenge.state(),challenge.browserBinding(),verified)).subjectId();}catch(BusinessException conflict){assertThat(conflict.errorCode().type().status()).isEqualTo(409);return "409";}});
                var b=pool.submit(()->{gate.await();try {return configs.inNewSnapshot(()->external.accept(runtime,second,challenge.state(),challenge.browserBinding(),verified)).subjectId();}catch(BusinessException conflict){assertThat(conflict.errorCode().type().status()).isEqualTo(409);return "409";}});gate.countDown();
                String left=a.get(15,TimeUnit.SECONDS),right=b.get(15,TimeUnit.SECONDS);assertThat(List.of(left,right)).containsExactlyInAnyOrder(id,"409");
                var accepted=left.equals(id)?first:second;assertThat(runtime.finish(accepted)).isEqualTo(SessionV2.Outcome.DELIVERABLE);accepted.emitCookies((domain,cookie,csrf)->{browser.cookies.put("AIHUB_PORTAL_SESSION",cookie);browser.cookies.put("AIHUB_PORTAL_CSRF",csrf);});
            }
            assertThat(browser.call("GET","/api/portal/common/auth/getMe",null).statusCode()).isEqualTo(200);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVIDENCE",Integer.class)).isEqualTo(1);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT WHERE EVENT_TYPE='LOGIN_SUCCESS'",Integer.class)).isEqualTo(1);
            var retry=anonymous(f);assertThatThrownBy(()->configs.inNewSnapshot(()->external.accept(runtime,scope(f,retry),challenge.state(),challenge.browserBinding(),verified))).isInstanceOf(BusinessException.class);
            f.database().update("UPDATE AI_PORTAL_LOGIN_CHALLENGE SET CREATED_DTM=clock_timestamp()-interval '2 minutes',EXPIRES_DTM=clock_timestamp()-interval '1 minute'");
            f.database().update("UPDATE AI_PORTAL_AUTH_EVIDENCE SET CONSUMED_DTM=clock_timestamp()-interval '2 minutes',EXPIRES_DTM=clock_timestamp()-interval '1 minute'");
            assertThat(f.bean(PortalSessionCleanup.class).sweep()).isGreaterThanOrEqualTo(2);
            assertThatThrownBy(()->configs.inNewSnapshot(()->external.accept(runtime,scope(f,retry),challenge.state(),challenge.browserBinding(),verified))).isInstanceOf(Rejected.class);
            assertThat(challenge.toString()).doesNotContain(challenge.state(),challenge.nonce(),challenge.browserBinding());assertThat(verified.toString()).doesNotContain(challenge.nonce());
        }
    }
}
