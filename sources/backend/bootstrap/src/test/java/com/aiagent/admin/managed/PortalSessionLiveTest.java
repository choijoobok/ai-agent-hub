// @feat FEAT-301
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.portal.auth.PortalSessionCleanup;
import java.util.*;
import java.util.concurrent.*;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalSessionLiveTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class DeliveryFault {
        static final java.util.concurrent.atomic.AtomicBoolean fail=new java.util.concurrent.atomic.AtomicBoolean();
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> portalDeliveryFault() {
            jakarta.servlet.Filter filter=(request,response,chain)->{
                if(((jakarta.servlet.http.HttpServletRequest)request).getRequestURI().equals("/api/portal/common/auth/changePassword")&&fail.compareAndSet(true,false)) {
                    chain.doFilter(request,new jakarta.servlet.http.HttpServletResponseWrapper((jakarta.servlet.http.HttpServletResponse)response){
                        @Override public jakarta.servlet.ServletOutputStream getOutputStream(){return new jakarta.servlet.ServletOutputStream(){
                            public boolean isReady(){return true;}public void setWriteListener(jakarta.servlet.WriteListener listener){}
                            public void write(int value)throws java.io.IOException{throw new java.io.IOException("시험 응답 전달 실패");}
                        };}
                    });
                }else chain.doFilter(request,response);
            };
            var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);registration.setOrder(-1);return registration;
        }
    }
    private ManagedLiveFixture f;private String month,group;private final ObjectMapper json=JsonMapper.builder().build();
    private static final String PASSWORD="Portal301!Newpass9";
    @BeforeAll void start()throws Exception {start("jdbc");}
    private void start(String provider)throws Exception {
        f=new ManagedLiveFixture("LOCAL",true,provider,DeliveryFault.class);month=f.database().queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')",String.class);
        f.database().update("INSERT INTO ai_admin_quota_grade VALUES(900301,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES(900301,?,'인증 시험',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES(900301,900301,'기본',1,1,NULL,'')");
        group=f.groupCall("save",command(null,null,"definition",Map.of("groupNm","Portal 인증 시험","description","인증 시험 담당")),200).path("targetId").asText();
    }
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String id,String revision,String scope,Map<String,Object> values){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,scope,values,"인증 경계 검증");}
    private Map<String,Object> profile(String login,String active){var values=new HashMap<String,Object>(Map.of("loginId",login,"userNm","Portal 시험","organizationNm","시험 조직","activeYn",active,"useStartDt",month+"-01"));values.put("useEndDt",null);return values;}
    private record User(String id,String login,String secret) {public String toString(){return "시험 이용자[보호됨]";}}
    private User create(String login)throws Exception {
        String id=f.adminCall("portalUser","save",command(null,null,"profile",profile(login,"N")),200).path("targetId").asText();
        f.adminCall("portalUser","save",command(id,"1","memberships",Map.of("memberships",List.of(Map.of("groupId",group,"activeYn","Y","memo","인증 시험 연결")),"deactivateUser",false)),200);
        f.adminCall("portalUser","save",command(id,"2","profile",profile(login,"Y")),200);
        String secret=f.adminCall("portalUser","resetCredential",command(id,"3","resetCredential",Map.of()),200).path("password").asText();assertThat(secret).isNotBlank();return new User(id,login,secret);
    }
    private JsonNode data(HttpResponse<String> response,int status){assertThat(response.statusCode()).as("안전 응답 %s",response.body()).isEqualTo(status);return json.readTree(response.body()).path("data");}
    private ManagedLiveFixture.Browser anonymous()throws Exception {var b=f.new Browser();data(b.call("GET","/api/portal/public/auth/getCsrf",null),200);return b;}
    private void login(ManagedLiveFixture.Browser b,User user,String secret)throws Exception {data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",secret)),200);}
    private ManagedLiveFixture.Browser authenticated(User user)throws Exception {var b=anonymous();login(b,user,user.secret());data(b.call("POST","/api/portal/common/auth/changePassword",Map.of("currentPassword",user.secret(),"newPassword",PASSWORD)),200);data(b.call("GET","/api/portal/common/auth/getMe",null),200);return b;}
    @Test @DisplayName("TC-3011 TC-3012 TC-3014 TC-30118 관리자와 독립 LOCAL 로그인·제한·회전·로그아웃")
    @Order(1)
    void independentLocalLifecycle()throws Exception {assertLocalLifecycle(true);}
    private void assertLocalLifecycle(boolean jdbc)throws Exception {
        var user=create("admin");var b=f.login("admin");String admin=b.cookies.get("AIHUB_ADMIN_SESSION");
        data(b.call("GET","/api/portal/common/auth/getMe",null),401);data(b.call("GET","/api/portal/public/auth/getCsrf",null),200);
        data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId","admin","password","Managed040!Fixture9")),401);
        var restricted=data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",user.secret())),200);
        assertThat(restricted.path("authLevel").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");assertThat(restricted.path("access").path("eligible").asBoolean()).isFalse();
        var old=f.new Browser();old.cookies.putAll(b.cookies);String csrf=b.cookies.get("AIHUB_PORTAL_CSRF");
        data(b.call("GET","/api/portal/public/auth/getCsrf",null),200);assertThat(b.cookies.get("AIHUB_PORTAL_CSRF")).isEqualTo(csrf);
        var changed=data(b.call("POST","/api/portal/common/auth/changePassword",Map.of("currentPassword",user.secret(),"newPassword",PASSWORD)),200);
        assertThat(changed.path("authLevel").asText()).isEqualTo("AUTHENTICATED");assertThat(b.cookies.get("AIHUB_PORTAL_CSRF")).isNotEqualTo(csrf);
        data(old.call("GET","/api/portal/common/auth/getMe",null),401);data(b.call("GET","/api/portal/common/auth/getMe",null),200);
        assertThat(b.cookies.get("AIHUB_ADMIN_SESSION")).isEqualTo(admin);data(b.call("GET","/api/system/common/auth/getMe",null),200);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_SESSION WHERE PRINCIPAL_NAME=?",Integer.class,"PORTAL:"+user.id())).isEqualTo(jdbc?1:0);
        data(b.call("POST","/api/portal/common/auth/logout",null),200);data(b.call("GET","/api/portal/common/auth/getMe",null),401);data(b.call("GET","/api/system/common/auth/getMe",null),200);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT WHERE PORTAL_USER_ID=? AND EVENT_TYPE='PASSWORD_CHANGED'",Integer.class,Long.valueOf(user.id()))).isEqualTo(1);
    }
    @Test @Order(7) @DisplayName("TC-3014 TC-3016 실제 Redis 선택의 Portal 로그인·자격 확정 후 회전·영역 독립")
    void redisLocalLifecycle()throws Exception {
        f.close();start("redis");assertLocalLifecycle(false);
    }
    @Test @DisplayName("TC-3013 TC-3015 TC-3019 TC-30114 실패횟수/감사 원자성·교차 CSRF·현재 계정 철회")
    @Order(2)
    void failuresAndCurrentRevocation()throws Exception {
        var user=create("failure301");var b=anonymous();String own=b.cookies.get("AIHUB_PORTAL_CSRF");b.cookies.put("AIHUB_PORTAL_CSRF","wrong-realm-token");
        data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",user.secret())),403);b.cookies.put("AIHUB_PORTAL_CSRF",own);
        data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password","wrong")),401);
        assertThat(f.database().queryForObject("SELECT FAILED_COUNT FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",Integer.class,Long.valueOf(user.id()))).isEqualTo(1);
        f.database().execute("CREATE FUNCTION fx_auth_audit_fail() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''시험 감사 장애''; END'");
        f.database().execute("CREATE TRIGGER fx_auth_audit_fail BEFORE INSERT ON AI_PORTAL_AUTH_EVENT FOR EACH ROW EXECUTE FUNCTION fx_auth_audit_fail()");
        try {data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password","wrong")),503);}
        finally {f.database().execute("DROP TRIGGER fx_auth_audit_fail ON AI_PORTAL_AUTH_EVENT");f.database().execute("DROP FUNCTION fx_auth_audit_fail()");}
        assertThat(f.database().queryForObject("SELECT FAILED_COUNT FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",Integer.class,Long.valueOf(user.id()))).isEqualTo(1);
        data(b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",user.secret(),"portalUserId","999")),400);
        f.database().update("UPDATE AI_ADMIN_PORTAL_CREDENTIAL SET LOCKED_YN='Y',LOCKED_DTM=clock_timestamp() WHERE PORTAL_USER_ID=?",Long.valueOf(user.id()));
        var locked=b.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",user.secret()));
        var unknown=b.call("POST","/api/portal/public/auth/login",Map.of("loginId","unknown301","password",user.secret()));
        data(locked,401);data(unknown,401);assertThat(json.readTree(locked.body()).path("code")).isEqualTo(json.readTree(unknown.body()).path("code"));
        f.database().update("UPDATE AI_ADMIN_PORTAL_CREDENTIAL SET LOCKED_YN='N',LOCKED_DTM=NULL WHERE PORTAL_USER_ID=?",Long.valueOf(user.id()));
        b=authenticated(user);
        f.adminCall("portalUser","save",command(user.id(),"4","profile",profile(user.login(),"N")),200);
        data(b.call("POST","/api/portal/common/auth/heartbeat",null),401);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM SYS_LO_ACCESS WHERE REQUEST_URI LIKE '/api/portal/%'",Integer.class)).isZero();
    }
    @Test @DisplayName("TC-3016 TC-3018 TC-30113 TC-30119 자격 확정 뒤 JDBC 회전 실패는 새 암호로 복구한다")
    @Order(3)
    void passwordCommitSurvivesRotationFailure()throws Exception {
        var user=create("rotation301");var b=anonymous();login(b,user,user.secret());
        f.database().execute("CREATE FUNCTION fx_portal_store_fail() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''시험 세션 저장 장애''; END'");
        f.database().execute("CREATE TRIGGER fx_portal_store_fail BEFORE INSERT ON AI_PORTAL_SESSION FOR EACH ROW EXECUTE FUNCTION fx_portal_store_fail()");
        HttpResponse<String> response;
        try {response=b.call("POST","/api/portal/common/auth/changePassword",Map.of("currentPassword",user.secret(),"newPassword",PASSWORD));}
        finally {f.database().execute("DROP TRIGGER fx_portal_store_fail ON AI_PORTAL_SESSION");f.database().execute("DROP FUNCTION fx_portal_store_fail()");}
        data(response,503);assertThat(response.body()).contains("PASSWORD_CHANGED_REAUTH_REQUIRED");assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(f.database().queryForObject("SELECT CREDENTIAL_VERSION FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",Long.class,Long.valueOf(user.id()))).isEqualTo(3L);
        data(b.call("GET","/api/portal/common/auth/getMe",null),401);var fresh=anonymous();login(fresh,user,PASSWORD);data(fresh.call("GET","/api/portal/common/auth/getMe",null),200);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT WHERE PORTAL_USER_ID=? AND EVENT_TYPE='PASSWORD_CHANGED'",Integer.class,Long.valueOf(user.id()))).isEqualTo(1);
    }
    @Test @DisplayName("TC-30110 TC-30112 동시 로그인 한도·만료 청소는 ADMIN/감사를 보존한다")
    @Order(4)
    void concurrentLoginAndExpiry()throws Exception {
        var user=create("parallel301");authenticated(user);var first=anonymous();var second=anonymous();
        try(var peer=f.portalPeer();var pool=Executors.newFixedThreadPool(2)) {
            second.endpoint=peer.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return first.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",PASSWORD));});
            var b=pool.submit(()->{gate.await();return second.call("POST","/api/portal/public/auth/login",Map.of("loginId",user.login(),"password",PASSWORD));});gate.countDown();data(a.get(15,TimeUnit.SECONDS),200);data(b.get(15,TimeUnit.SECONDS),200);
        }
        second.endpoint=first.endpoint;
        try(var restarted=f.portalPeer()) {
            first.endpoint=restarted.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
            var statuses=List.of(first.call("GET","/api/portal/common/auth/getMe",null).statusCode(),second.call("GET","/api/portal/common/auth/getMe",null).statusCode());assertThat(statuses).containsExactlyInAnyOrder(200,401);
        }
        first.endpoint=second.endpoint;
        int admin=f.database().queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class),events=f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT",Integer.class);
        f.database().update("UPDATE AI_PORTAL_SESSION SET EXPIRY_TIME=0 WHERE PRINCIPAL_NAME=?","PORTAL:"+user.id());
        data(first.call("GET","/api/portal/common/auth/getMe",null),401);data(second.call("GET","/api/portal/common/auth/getMe",null),401);
        assertThat(f.bean(PortalSessionCleanup.class).sweep()).isGreaterThanOrEqualTo(1);assertThat(f.bean(PortalSessionCleanup.class).sweep()).isZero();
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(admin);assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT",Integer.class)).isEqualTo(events);
    }
    @Test @Order(4) @DisplayName("TC-30120 실제 자격/회전 확정 뒤 응답 유실은 재실행·보상 롤백하지 않는다")
    void committedRotationDeliveryFailure()throws Exception {
        var user=create("delivery301");var browser=anonymous();login(browser,user,user.secret());
        DeliveryFault.fail.set(true);
        int status;
        try {status=browser.call("POST","/api/portal/common/auth/changePassword",Map.of("currentPassword",user.secret(),"newPassword",PASSWORD)).statusCode();}
        catch(java.io.IOException transportFailure){status=599;}
        finally {DeliveryFault.fail.set(false);}
        assertThat(status).isIn(500,599);
        assertThat(f.database().queryForObject("SELECT CREDENTIAL_VERSION FROM AI_ADMIN_PORTAL_CREDENTIAL WHERE PORTAL_USER_ID=?",Long.class,Long.valueOf(user.id()))).isEqualTo(3L);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_AUTH_EVENT WHERE PORTAL_USER_ID=? AND EVENT_TYPE='PASSWORD_CHANGED'",Integer.class,Long.valueOf(user.id()))).isEqualTo(1);
        var recovered=anonymous();login(recovered,user,PASSWORD);data(recovered.call("GET","/api/portal/common/auth/getMe",null),200);
    }
    @Test @Order(5) @DisplayName("TC-30115 Portal 스키마 왕복은 ADMIN·원장 자격을 보존한다")
    void migrationRoundTrip()throws Exception {
        int users=f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_PORTAL_USER",Integer.class);
        try(var connection=f.bean(javax.sql.DataSource.class).getConnection();var migration=new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
            int count=f.database().queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE orderexecuted >= (SELECT orderexecuted FROM databasechangelog WHERE id='portal-001-authentication')",Integer.class);
            migration.rollback(count,"");
            assertThat(f.database().queryForObject("SELECT to_regclass('ai_portal_session')::text",String.class)).isNull();
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_PORTAL_USER",Integer.class)).isEqualTo(users);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM SYS_USER WHERE USER_ID='admin'",Integer.class)).isEqualTo(1);
            migration.update(new liquibase.Contexts());
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_PORTAL_SESSION",Integer.class)).isZero();
            var anonymous=anonymous();assertThat(anonymous.cookies).containsKey("AIHUB_PORTAL_SESSION");
        }
    }
}
