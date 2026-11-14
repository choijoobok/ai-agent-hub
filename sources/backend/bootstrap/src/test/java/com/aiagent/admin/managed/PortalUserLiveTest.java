// @feat FEAT-005
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.portal.*;
import com.aiagent.core.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalUserLiveTest {
    private ManagedLiveFixture f;
    private String today,month,user,group;
    @BeforeAll void start() throws Exception {
        f=new ManagedLiveFixture(Ports.class);today=LocalDate.now(ZoneId.of("Asia/Seoul")).toString();month=today.substring(0,7);
        f.database().execute("CREATE TABLE fx_portal_security(kind varchar(50),result varchar(80),subject varchar(100))");
        // 당월 기준은 전용 시험 스키마의 과거 운영 기준 fixture다. 운영 API의 미래월 제한을 바꾸지 않는다.
        f.database().update("INSERT INTO ai_admin_quota_grade VALUES (900001,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES (900001,?,'시험 당월',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES (900001,900001,'기본',1,1,NULL,'')");
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class Ports {
        static boolean failAudit;
        @Bean PortalSecurityEvents portalTestSecurity(DataSource source){return new PortalSecurityEvents() {
            public DataSource dataSource(){return source;}
            public void record(Event event){if(failAudit)throw new IllegalStateException("시험 보안 감사 장애");new JdbcTemplate(source).update("INSERT INTO fx_portal_security VALUES (?,?,?)",event.kind().name(),event.result(),event.principal()==null?null:event.principal().subjectId());}
        };}
        @Bean PortalIdentityTargets portalTestTargets(){return new PortalIdentityTargets() {
            public Candidates list(String kind){return new Candidates("READY",List.of(new Target("700001","시험 업무 대상",true,"")));}
            public void requireWritable(String kind,String id){if(!id.equals("700001"))throw ManagedErrorCode.FORBIDDEN.failure();}
        };}
    }
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String id,String revision,String scope,Map<String,Object> values) {return new ManagedCommand(UUID.randomUUID().toString(),id,revision,scope,values,"Portal 운영 검토");}
    private Map<String,Object> profile(String name,String active){var values=new LinkedHashMap<String,Object>();values.put("loginId","admin");values.put("userNm",name);values.put("organizationNm","시험부");values.put("useStartDt",today);values.put("useEndDt",null);values.put("activeYn",active);return values;}
    private JsonNode call(String action,Object body,int status) throws Exception {return f.adminCall("portalUser",action,body,status);}
    private Map<String,Object> member(String active,boolean deactivate){return Map.of("memberships",List.of(Map.of("groupId",group,"activeYn",active,"memo","소속 정비")),"deactivateUser",deactivate);}
    @Test @Order(1) @DisplayName("TC-0061 TC-0063 TC-0064 TC-0065 TC-0066 TC-0051 TC-0053 TC-0406 독립 Portal 원장·부모판·소속·원자 비활성")
    void independentAccountMembershipAndWholeHistory() throws Exception {
        var create=command(null,null,"profile",profile("Portal 관리자 동명이인","N"));
        var saved=call("save",create,200);user=saved.path("targetId").asText();assertThat(call("save",create,200)).isEqualTo(saved);
        call("getOne",Map.of("targetId",user),400);
        f.portalReaderCannotSave(command(user,"1","profile",profile("조회자 변경 금지","N")));
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id='admin'",Integer.class)).isEqualTo(1);
        assertThat(f.database().queryForObject("SELECT grade_id FROM ai_admin_user_quota_grade WHERE portal_user_id=?",Long.class,Long.parseLong(user))).isEqualTo(900001L);
        call("save",command(user,"1","profile",profile("Portal 관리자 동명이인","Y")),409);
        var groupResult=f.groupCall("save",command(null,null,"definition",Map.of("groupNm","Portal 시험 그룹","description","시험 담당")),200);group=groupResult.path("targetId").asText();
        call("save",command(user,"1","memberships",member("Y",false)),200);
        call("save",command(user,"1","profile",profile("낡은 탭","N")),409);
        call("save",command(user,"2","profile",profile("Portal 관리자 동명이인","Y")),200);
        call("save",command(user,"3","memberships",member("N",false)),409);
        var removed=call("save",command(user,"3","memberships",member("N",true)),200);assertThat(removed.path("revision").asText()).isEqualTo("4");
        assertThat(f.database().queryForObject("SELECT active_yn FROM ai_admin_portal_user WHERE portal_user_id=?",String.class,Long.parseLong(user))).isEqualTo("N");
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_membership_hist WHERE portal_user_id=?",Integer.class,Long.parseLong(user))).isEqualTo(3);
        var projection=f.groupCall("getMembers",Map.of("targetId",group),200);
        assertThat(projection.path("state").asText()).isEqualTo("READY");assertThat(projection.path("rows").get(0).path("activeYn").asText()).isEqualTo("N");
        assertThat(f.groupCall("getOne",Map.of("targetId",group),200).path("revision").asText()).isEqualTo("1");
        f.groupOnlyCannotReadPortalMembers(group);
        f.groupCall("remove",command(group,"1","definition",Map.of()),409);
        assertThat(call("getOne",Map.of("targetId",user,"scope","grades"),200).path("allocation").path("state").asText()).isEqualTo("NOT_INSTALLED");
    }
    @Test @Order(2) @DisplayName("TC-0051 TC-0058 비밀 단회 응답·재발급 금지·전체 이력 롤백")
    void oneTimeSecretAndHistoryFailure() throws Exception {
        var reset=command(user,"4","resetCredential",Map.of());
        var first=call("resetCredential",reset,200);assertThat(first.path("password").asText()).isNotBlank();
        var replay=call("resetCredential",reset,200);assertThat(replay.path("secretState").asText()).isEqualTo("SECRET_NOT_REPLAYABLE");assertThat(replay.path("password").isNull()).isTrue();
        assertThat(f.database().queryForObject("SELECT credential_version FROM ai_admin_portal_credential WHERE portal_user_id=?",Long.class,Long.parseLong(user))).isEqualTo(2L);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name LIKE 'ai_admin%hist' AND column_name='password_hash'",Integer.class)).isZero();
        var failed=command(user,"5","profile",profile("이력 실패","N"));
        f.database().execute("ALTER TABLE ai_admin_portal_user_hist ADD CONSTRAINT portal005_failure CHECK(user_nm<>'이력 실패')");
        try {call("save",failed,503);assertThat(f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",Long.class,Long.parseLong(user))).isEqualTo(5L);}
        finally {f.database().execute("ALTER TABLE ai_admin_portal_user_hist DROP CONSTRAINT portal005_failure");}
        call("save",failed,200);
    }
    @Test @Order(3) @DisplayName("TC-0055 TC-0059 TC-0069 TC-0407 미래 등급 실제 이전·탭 경계·미상 차단")
    void gradeTransferAndIdentityScopes() throws Exception {
        String future=YearMonth.parse(month).plusMonths(1).toString();
        var definition=new LinkedHashMap<String,Object>();definition.put("startMonth",future);definition.put("memo","등급 개편");definition.put("grades",List.of(Map.of("clientKey","새기본","gradeNm","새기본","rankNo",1,"weight","2","description","")));definition.put("transitionMappings",List.of());
        f.adminCall("quotaGrade","save",command(null,null,"definition",definition),409);
        definition.put("transitionMappings",List.of(Map.of("fromGradeId","900001","toClientKey","새기본")));
        var transferred=f.adminCall("quotaGrade","save",command(null,null,"definition",definition),200);
        assertThat(f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",Long.class,Long.parseLong(user))).isEqualTo(7L);
        assertThat(f.database().queryForObject("SELECT grade_id FROM ai_admin_user_quota_grade WHERE portal_user_id=? AND effective_month=?",Long.class,Long.parseLong(user),month)).isEqualTo(900001L);
        assertThat(f.database().queryForObject("SELECT source_kind FROM ai_admin_user_quota_grade WHERE portal_user_id=? AND effective_month=?",String.class,Long.parseLong(user),future)).isEqualTo("GRADE_TRANSFER");
        String futureGrade=f.database().queryForObject("SELECT grade_id FROM ai_admin_user_quota_grade WHERE portal_user_id=? AND effective_month=?",String.class,Long.parseLong(user),future);
        call("save",command(user,"7","grades",Map.of("grades",List.of(Map.of("effectiveMonth",future,"gradeId",futureGrade,"activeYn","N")))),409);
        f.adminCall("quotaGrade","remove",command(transferred.path("targetId").asText(),"1","definition",Map.of()),409);
        var mapping=Map.<String,Object>of("identities",List.of(Map.of("targetId","700001","externalUserId","worker.a","mappingActiveYn","Y","identityEvidence","담당자 확인")));
        call("save",command(user,"7","identitySystem",mapping),200);
        call("save",command(user,"7","identityDataSource",mapping),409);
        call("save",command(user,"8","identityDataSource",mapping),200);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_identity WHERE portal_user_id=?",Integer.class,Long.parseLong(user))).isEqualTo(2);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_identity_hist WHERE portal_user_id=? AND history_no=(SELECT MAX(history_no) FROM ai_admin_portal_user_hist WHERE portal_user_id=?)",Integer.class,Long.parseLong(user),Long.parseLong(user))).isEqualTo(2);
        f.database().execute("CREATE TABLE ai_admin_quota_plan (plan_id bigint)");
        try {call("save",command(user,"9","profile",profile("미상 배정 우회","N")),503);}
        finally {f.database().execute("DROP TABLE ai_admin_quota_plan");}
    }
    @Test @Order(4) @DisplayName("TC-0051 TC-0059 TC-0405 TC-0406 LOCAL 자격 포트·세대·재사용·감사 롤백·영역 분리")
    void credentialOwnerAndCurrentAccess() throws Exception {
        call("save",command(user,"9","memberships",member("Y",false)),200);
        call("save",command(user,"10","profile",profile("인증 포트 검토","Y")),200);
        var reset=call("resetCredential",command(user,"11","resetCredential",Map.of()),200);String temporary=reset.path("password").asText();
        f.bean(com.aiagent.support.config.ConfigLookup.class).inNewSnapshot(()->credentialAssertions(temporary));
        long parent=f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",Long.class,Long.parseLong(user));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var writerStarted=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var accepted=workers.submit(()->f.bean(com.aiagent.support.config.ConfigLookup.class).inNewSnapshot(()->f.bean(PortalAccessService.class).acceptCurrentAccess(new PrincipalRef(AuthDomain.PORTAL,user),null,value->{
                entered.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("실행 수락 시험 시간 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}return value.outcome();
            })));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var revoked=workers.submit(()->{writerStarted.countDown();return call("save",command(user,Long.toString(parent),"memberships",member("N",true)),200);});
            try {assertThat(writerStarted.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->revoked.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            assertThat(accepted.get(10,TimeUnit.SECONDS)).isEqualTo("ALLOW");revoked.get(10,TimeUnit.SECONDS);
        }
        assertThat(f.bean(PortalAccessService.class).resolveCurrentPortalAccess(new PrincipalRef(AuthDomain.PORTAL,user),null).outcome()).isEqualTo("DENY");
    }
    private void credentialAssertions(String temporary) {
        var summary=f.bean(PortalUserQueryService.class).getOne(user,"profile",null);
        assertThat(summary.user().lockedYn()).isEqualTo("N");assertThat(summary.user().mustChange()).isTrue();assertThat(summary.user().passwordChangedDtm()).isNotNull();
        var accounts=f.bean(PortalAccountService.class);var principal=new PrincipalRef(AuthDomain.PORTAL,user);
        assertThat(accounts.verifyLocalCredentials("admin","wrong").principal()).isNull();
        assertThat(f.database().queryForObject("SELECT failed_count FROM ai_admin_portal_credential WHERE portal_user_id=?",Integer.class,Long.parseLong(user))).isEqualTo(1);
        var verified=accounts.verifyLocalCredentials("admin",temporary);assertThat(verified.outcome()).isEqualTo("PASSWORD_CHANGE_REQUIRED");assertThat(verified.principal()).isEqualTo(principal);
        assertThat(f.bean(PortalAccessService.class).resolveCurrentPortalAccess(principal,null).outcome()).isEqualTo("DENY");
        long parent=f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",Long.class,Long.parseLong(user));
        Ports.failAudit=true;
        try {assertThatThrownBy(()->accounts.changeOwnLocalPassword(principal,verified.credentialVersion(),temporary,"Portal005!Changed9"));}
        finally {Ports.failAudit=false;}
        assertThat(accounts.currentAccount(principal,verified.credentialVersion()).restricted()).isTrue();
        var change=accounts.changeOwnLocalPassword(principal,verified.credentialVersion(),temporary,"Portal005!Changed9");assertThat(change.outcome()).isEqualTo("CHANGED");
        assertThatThrownBy(()->accounts.currentAccount(principal,verified.credentialVersion()));
        assertThat(accounts.changeOwnLocalPassword(principal,change.credentialVersion(),"Portal005!Changed9",temporary).outcome()).isEqualTo("POLICY_REJECTED");
        assertThat(f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",Long.class,Long.parseLong(user))).isEqualTo(parent);
        var access=f.bean(PortalAccessService.class);assertThat(access.resolveCurrentPortalAccess(principal,null).outcome()).isEqualTo("ALLOW");
        assertThat(access.resolveCurrentPortalAccess(principal,new PortalAssetPolicy.Request("FEAT-001","SYSTEM","700001","EXECUTE",true,null,null)).outcome()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(()->access.resolveCurrentPortalAccess(new PrincipalRef(AuthDomain.ADMIN,user),null));
    }
    @Test @Order(5) @DisplayName("TC-0051 Portal 신규 마이그레이션 왕복")
    void migrationRoundTrip() throws Exception {
        try(var connection=f.bean(DataSource.class).getConnection();var migration=new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
            int count=f.database().queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE orderexecuted >= (SELECT orderexecuted FROM databasechangelog WHERE id='admin-009-portal-user')",Integer.class);
            migration.rollback(count,"");
            try(var result=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_portal_user'),to_regclass('ai_admin_user_group')")){result.next();assertThat(result.getString(1)).isNull();assertThat(result.getString(2)).isNotNull();}
            migration.update(new liquibase.Contexts());
            try(var result=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu_api WHERE menu_cd='HUB_PORTAL_USER'")){result.next();assertThat(result.getInt(1)).isEqualTo(7);}
        }
    }
}
