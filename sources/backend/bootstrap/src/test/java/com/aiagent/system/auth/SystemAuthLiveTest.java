// @feat FEAT-501
package com.aiagent.system.auth;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.AiAgentApplication;
import com.aiagent.system.api.auth.*;
import com.aiagent.api.auth.v2.SessionV2Filter;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.*;
import com.aiagent.core.security.v2.SessionAuthority.Context;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** 실제 제품 컨트롤러/필터/JPA/Liquibase를 전용 DB에서 검증한다. 자격값은 출력하지 않는다. */
@Tag("systemAuthLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemAuthLiveTest {
    private static final String ORIGIN="http://127.0.0.1:18520";
    private static final String PASSWORD="System520!Pass9";
    private final ObjectMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ConfigurableApplicationContext first,second;
    private JdbcTemplate jdbc;
    private String url,user,password,schema;
    private final String epoch=UUID.randomUUID().toString();
    private int port;
    private boolean sample;
    @BeforeAll void start() {
        url=System.getenv("SYSTEM_AUTH_TEST_JDBC"); user=System.getenv("SYSTEM_AUTH_TEST_USER"); password=System.getenv("SYSTEM_AUTH_TEST_PASSWORD");
        if(url==null || !url.matches("jdbc:postgresql://(?:127[.]0[.]0[.]1|localhost):[0-9]+/system520_[A-Za-z0-9_]+"))
            throw new IllegalStateException("전용 localhost system520_ 시험 DB가 필요하다");
        schema="system520_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("CREATE SCHEMA "+schema);
        sample=Boolean.getBoolean("includeSample");
        first=boot(); jdbc=new JdbcTemplate(first.getBean(DataSource.class));
        port=first.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
    }
    private ConfigurableApplicationContext boot() {
        var application=new SpringApplication(AiAgentApplication.class, SystemAuthFaults.class);
        return application.run("--spring.profiles.active="+System.getProperty("systemAuth.profile"),"--server.port=0",
                "--spring.datasource.url="+url+"?currentSchema="+schema,"--spring.datasource.username="+user,
                "--spring.datasource.password="+(password==null?"":password),"--app.auth.session.origin="+ORIGIN,
                "--app.auth.session.epoch="+epoch,"--app.auth.session.store-instance=system520-test",
                "--app.auth.session.local-http=true","--app.auth.session.provider="+System.getenv().getOrDefault("SYSTEM_AUTH_TEST_PROVIDER","jdbc"),"--app.logging.sql.enabled=false","--logging.level.root=WARN");
    }
    @AfterAll void stop() { SystemAuthFaults.reset();if(second!=null)second.close();if(first!=null)first.close(); }
    private final class Browser {
        final Map<String,String> cookies=new LinkedHashMap<>();
        int endpoint=port;
        HttpResponse<String> call(String method,String path,Object body) throws Exception { return call(method,path,body,ORIGIN,true); }
        HttpResponse<String> call(String method,String path,Object body,String origin,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+endpoint+path)).timeout(Duration.ofSeconds(15));
            if(!cookies.isEmpty())request.header("Cookie",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
            if(!method.equals("GET")) {
                if(origin!=null)request.header("Origin",origin);
                if(csrf&&cookies.containsKey("AIHUB_ADMIN_CSRF"))request.header("X-AIHUB-ADMIN-CSRF",cookies.get("AIHUB_ADMIN_CSRF"));
            }
            if(body!=null)request.header("Content-Type","application/json");
            var response=http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            for(String header:response.headers().allValues("Set-Cookie")){String[] pair=header.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}
            return response;
        }
        Browser copy(){var copy=new Browser();copy.endpoint=endpoint;copy.cookies.putAll(cookies);return copy;}
    }
    private JsonNode data(HttpResponse<String> response,int status) {
        assertThat(response.statusCode()).as("HTTP 상태, trace="+response.headers().firstValue("X-Request-Id").orElse("없음")).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
    private Browser login(String id,String secret) throws Exception {
        var browser=new Browser(); data(browser.call("GET","/api/system/public/auth/getCsrf",null),200);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId",id,"password",secret)),200);
        return browser;
    }
    private Browser administrator() throws Exception { var browser=login("admin",PASSWORD);data(browser.call("GET","/api/system/common/auth/getMe",null),200);return browser; }
    @Test @Order(1) @DisplayName("TC-50115 TC-50117 실제 빈 DB 기동은 자동 세션 필터0·정확 handler·CSV/SQL 집합을 대조한다")
    void bootstrapAndSeedCollections() throws Exception {
        assertThat(first.getBeanNamesForType(Class.forName("org.springframework.session.SessionRepository"))).isEmpty();
        var catalog=first.getBean(AdminApiCatalog.class).catalog();assertThat(catalog.entries()).hasSize(sample?122:102);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN='PORTAL'",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT API_URL FROM SYS_API",String.class)).allMatch(path->path.matches("/api/(system|sample)/service/.+"));
        assertThat(rows("006-init-api.csv",0,3)).containsExactlyInAnyOrderElementsOf(jdbc.query("SELECT API_URL,ACTION_CD FROM SYS_API",(r,i)->r.getString(1)+"|"+r.getString(2)));
        assertThat(rows("007-init-menu-api.csv",0,1)).containsExactlyInAnyOrderElementsOf(jdbc.query("SELECT MENU_CD,API_URL FROM SYS_MENU_API",(r,i)->r.getString(1)+"|"+r.getString(2)));
    }
    private List<String> rows(String file,int left,int right)throws Exception {
        var values=new ArrayList<String>();
        for(String line:Files.readAllLines(Path.of("src/main/resources/db/changelog/data",file)).subList(1,Files.readAllLines(Path.of("src/main/resources/db/changelog/data",file)).size())) {
            if(line.isBlank())continue;
            String[] fields=line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)",-1);
            if(!sample&&(fields[0].startsWith("/api/sample/")||fields[right].startsWith("/api/sample/")))continue;
            values.add(fields[left]+"|"+fields[right]);
        }
        return values;
    }
    @Test @Order(2) @DisplayName("TC-5017 TC-5019 TC-50113 제한 로그인→첫 사용→암호·이력·세대 회전, 구쿠키 거부")
    void restrictedLoginAndAtomicPasswordRotation() throws Exception {
        var browser=login("admin","admin");
        assertThat(data(browser.call("GET","/api/system/common/auth/getMe",null),200).path("mustChangePassword").asBoolean()).isTrue();
        data(browser.call("POST","/api/system/service/user/getList",Map.of()),403);
        data(browser.call("GET","/api/system/public/message/getBundle?lang=ko",null),200);
        var prior=browser.copy();
        data(browser.call("POST","/api/system/common/auth/changePassword",Map.of("currentPwd","wrong","newPwd",PASSWORD)),400);
        assertThat(data(browser.call("GET","/api/system/common/auth/getMe",null),200).path("mustChangePassword").asBoolean()).isTrue();
        data(browser.call("POST","/api/system/common/auth/changePassword",Map.of("currentPwd","admin","newPwd",PASSWORD)),200);
        assertThat(first.getBean(PasswordEncoder.class).matches(PASSWORD,jdbc.queryForObject("SELECT PWD FROM SYS_USER WHERE USER_ID='admin'",String.class))).isTrue();
        data(prior.call("GET","/api/system/common/auth/getMe",null),401);
        assertThat(data(browser.call("GET","/api/system/common/auth/getMe",null),200).path("mustChangePassword").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT WHERE SUBJECT_ID='admin' AND GENERATION=2 AND FIRST_USED_AT IS NOT NULL",Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO SYS_USER(USER_ID,USER_NM,PWD,LOCALE_CD) SELECT 'other','다른 관리자',PWD,'ko' FROM SYS_USER WHERE USER_ID='admin'");
        jdbc.update("INSERT INTO SYS_USER_ROLE(USER_ID,ROLE_ID,VALID_FROM_DT,VALID_TO_DT) SELECT 'other',ROLE_ID,VALID_FROM_DT,VALID_TO_DT FROM SYS_USER_ROLE WHERE USER_ID='admin'");
    }
    @Test @Order(3) @DisplayName("TC-50110 TC-5038 실패 횟수 커밋 뒤401·비밀 수집0·구주소 및 public 위장 거부")
    void failedLoginAndExactBoundary() throws Exception {
        var browser=new Browser();data(browser.call("GET","/api/system/public/auth/getCsrf",null),200);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId","missing-user","password","wrong-secret")),401);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId","other","password","wrong-secret")),401);
        assertThat(jdbc.queryForObject("SELECT LOGIN_FAIL_CNT FROM SYS_USER WHERE USER_ID='other'",Integer.class)).isEqualTo(1);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId","other","password",PASSWORD),null,true),403);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId","other","password",PASSWORD),ORIGIN,false),403);
        for(String path:List.of("/api/public/system/auth/getPasswordPolicy","/api/system/public/user/save","/api/system/public/auth/getCsrf/","/api/portal/common/auth/getMe")) data(browser.call("GET",path,null),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_LO_ACCESS WHERE REQUEST_URI='/api/portal/common/auth/getMe'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_LO_ACCESS WHERE REQUEST_URI='/api/system/public/auth/login' AND PARAMETERS IS NOT NULL",Integer.class)).isZero();
    }
    @Test @Order(4) @DisplayName("TC-5014 TC-50116 TC-50117 같은 DB 재기동0중복·두 노드 현재 계정 거부·관리 참조 회수")
    void secondNodeCurrentAuthorityAndManagementReferences() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG",Integer.class);
        second=boot();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG",Integer.class)).isEqualTo(before);
        var admin=administrator();var other=login("other",PASSWORD);data(other.call("GET","/api/system/common/auth/getMe",null),200);
        var response=data(admin.call("POST","/api/system/service/user/getSessionList",Map.of("params",Map.of("userId","other"),"conditions",List.of(),"page",Map.of("size",20))),200);
        var list=response.path("rows"); if(list.isMissingNode())list=response.path("content");
        assertThat(list.size()).isEqualTo(1);String ref=list.get(0).path("sessionId").asText();assertThat(ref).hasSize(43);
        var forgery=other.copy();forgery.cookies.put("AIHUB_ADMIN_SESSION",ref);data(forgery.call("GET","/api/system/common/auth/getMe",null),401);
        assertThat(data(admin.call("POST","/api/system/service/user/killSession",Map.of("userId","other","targets",List.of(ref,ref))),200).asInt()).isEqualTo(1);
        assertThat(data(admin.call("POST","/api/system/service/user/killSession",Map.of("userId","other","targets",List.of(ref))),200).asInt()).isZero();
        other.endpoint=second.getEnvironment().getRequiredProperty("local.server.port",Integer.class);data(other.call("GET","/api/system/common/auth/getMe",null),401);
        other=login("other",PASSWORD);data(other.call("GET","/api/system/common/auth/getMe",null),200);
        jdbc.update("UPDATE SYS_USER SET USE_YN='N' WHERE USER_ID='other'");
        var unusable=other.copy();
        other.endpoint=second.getEnvironment().getRequiredProperty("local.server.port",Integer.class);data(other.call("GET","/api/system/common/auth/getMe",null),401);
        jdbc.update("UPDATE SYS_USER SET USE_YN='Y' WHERE USER_ID='other'");
        data(unusable.call("GET","/api/system/common/auth/getMe",null),401);
        other=login("other",PASSWORD);data(other.call("GET","/api/system/common/auth/getMe",null),200);
        jdbc.update("UPDATE SYS_USER SET USE_YN='N' WHERE USER_ID='other'");
        data(other.call("GET","/api/system/public/auth/getCsrf",null),401);
        assertThat(other.cookies.get("AIHUB_ADMIN_SESSION")).isEmpty();
        other.cookies.entrySet().removeIf(entry->entry.getValue().isEmpty());
        data(other.call("GET","/api/system/public/auth/getCsrf",null),200);
        jdbc.update("UPDATE SYS_USER SET USE_YN='Y' WHERE USER_ID='other'");
    }
    @Test @Order(5) @DisplayName("TC-50110 TC-50111 TC-5037 실제PG 공유 접수→배타 회수 양순서와 업무 효과 경계")
    void postgresAuthorityAndWriteShareCommitBoundary() throws Exception {
        var guard=first.getBean(AdminAuthorityGuard.class);var tx=new TransactionTemplate(first.getBean(PlatformTransactionManager.class));
        var configs=first.getBean(com.aiagent.support.config.ConfigLookup.class);
        var principal=new PrincipalRef(AuthDomain.ADMIN,"other");var identity=new Identity(principal,AuthLevel.AUTHENTICATED,"LOCAL",null,Instant.now(),"시험","ko");
        var context=new Context(AuthDomain.ADMIN,principal,null,identity,"API-506",null,true,Capability.READ,"system520-concurrency");
        jdbc.execute("CREATE TABLE SYSTEM520_EFFECT(V INT)");
        var admitted=new CountDownLatch(1);var release=new CountDownLatch(1);var revoking=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var write=pool.submit(()->{AdminRequestContext.bind(new MockHttpServletRequest());try{configs.inNewSnapshot(()->tx.executeWithoutResult(status->guard.protect(context,false,()->{jdbc.update("INSERT INTO SYSTEM520_EFFECT VALUES(1)");admitted.countDown();await(release);})));}finally{AdminRequestContext.clear();}});
            if(!admitted.await(5,TimeUnit.SECONDS)) { if(write.isDone()) write.get(); fail("보호 callback 접수가 시간 안에 완료되지 않았다"); }
            var revoke=pool.submit(()->tx.executeWithoutResult(status->{revoking.countDown();jdbc.execute("SELECT pg_advisory_xact_lock(1095321685,1)");jdbc.update("UPDATE SYS_USER SET USE_YN='N' WHERE USER_ID='other'");}));
            assertThat(revoking.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->revoke.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();write.get(5,TimeUnit.SECONDS);revoke.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYSTEM520_EFFECT",Integer.class)).isEqualTo(1);
            AdminRequestContext.bind(new MockHttpServletRequest());
            assertThatThrownBy(()->configs.inNewSnapshot(()->tx.executeWithoutResult(status->guard.protect(context,false,()->jdbc.update("INSERT INTO SYSTEM520_EFFECT VALUES(2)"))))).isInstanceOf(com.aiagent.core.security.v2.SessionAuthority.Unusable.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYSTEM520_EFFECT",Integer.class)).isEqualTo(1);
        } finally {release.countDown();AdminRequestContext.clear();jdbc.update("UPDATE SYS_USER SET USE_YN='Y' WHERE USER_ID='other'");}
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("시험 barrier 시간 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}

    @Test @Order(10) @DisplayName("TC-5086 TC-5087 TC-5089 실제 HTTP 다중 접수 확정 뒤 회수·대기 작업 종결·새 접수/중단 차단")
    void batchAdmissionAndCurrentPermissionShareBoundary() throws Exception {
        var schedules=first.getBean(com.aiagent.domain.batch.repository.SysScheduleBatchRepository.class);
        for(String id:List.of("SYSTEM520_A","SYSTEM520_B","SYSTEM520_C"))schedules.save(com.aiagent.domain.batch.entity.SysScheduleBatch.created(id,id,"시험","system520Probe","0 0 2 * * *","Asia/Seoul",null,300,0,false,0));
        var browser=login("recover","Recovery520!Pass8");data(browser.call("GET","/api/system/common/auth/getMe",null),200);
        SystemAuthFaults.BATCH_QUEUE.clear();SystemAuthFaults.holdBatch=true;
        try {
            var accepted=data(browser.call("POST","/api/system/service/batchSchedule/runNow",Map.of("targets",List.of("SYSTEM520_B","SYSTEM520_A"))),200);
            assertThat(accepted.path("items").get(0).path("batchId").asText()).isEqualTo("SYSTEM520_B");
            assertThat(SystemAuthFaults.BATCH_QUEUE).hasSize(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_BATCH_HISTORY WHERE BATCH_ID LIKE 'SYSTEM520_%' AND RSLT_CD='RUNNING'",Integer.class)).isEqualTo(2);
            jdbc.update("DELETE FROM SYS_USER_ROLE WHERE USER_ID='recover'");
            data(browser.call("POST","/api/system/service/batchSchedule/runNow",Map.of("targets",List.of("SYSTEM520_C"))),403);
            var ids=jdbc.queryForList("SELECT HIST_ID FROM SYS_BATCH_HISTORY WHERE BATCH_ID LIKE 'SYSTEM520_%'",Long.class);
            data(browser.call("POST","/api/system/service/batchHistory/cancel",Map.of("targets",ids)),403);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_BATCH_HISTORY WHERE BATCH_ID LIKE 'SYSTEM520_%' AND CANCEL_REQ_DTM IS NOT NULL",Integer.class)).isZero();
            for(var work:List.copyOf(SystemAuthFaults.BATCH_QUEUE))work.run();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_BATCH_HISTORY WHERE BATCH_ID LIKE 'SYSTEM520_%' AND RSLT_CD='SUCCESS'",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_BATCH_HISTORY WHERE BATCH_ID='SYSTEM520_C'",Integer.class)).isZero();
        } finally {SystemAuthFaults.holdBatch=false;SystemAuthFaults.BATCH_QUEUE.clear();}
    }

    @Test @Order(9) @DisplayName("TC-50112 TC-50114 TC-50115 실제 복구 코드·메일 잠금 밖 발송·암호 확정과 구 접속 회수")
    void recoveryCommitsCredentialsAndRevokesExistingGrants() throws Exception {
        jdbc.update("INSERT INTO SYS_USER(USER_ID,USER_NM,PWD,LOCALE_CD,EMAIL) SELECT 'recover','복구 관리자',PWD,'ko','system520@example.invalid' FROM SYS_USER WHERE USER_ID='admin'");
        jdbc.update("INSERT INTO SYS_USER_ROLE(USER_ID,ROLE_ID,VALID_FROM_DT,VALID_TO_DT) SELECT 'recover',ROLE_ID,VALID_FROM_DT,VALID_TO_DT FROM SYS_USER_ROLE WHERE USER_ID='admin'");
        var old=login("recover",PASSWORD);data(old.call("GET","/api/system/common/auth/getMe",null),200);
        var recovery=new Browser();data(recovery.call("GET","/api/system/public/auth/getCsrf",null),200);
        SystemAuthFaults.MAIL.set(null);data(recovery.call("POST","/api/system/public/auth/sendResetCode",Map.of("loginId","recover")),200);
        // HTTP 응답 수신 뒤 필터의 afterCommit 발송 완료를 짧게 기다린다.
        for(int i=0;i<50&&SystemAuthFaults.MAIL.get()==null;i++)Thread.sleep(20);
        String body=SystemAuthFaults.MAIL.get();assertThat(body).isNotNull();
        var code=java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(body);assertThat(code.find()).isTrue();String value=code.group();
        data(recovery.call("POST","/api/system/public/auth/verifyResetCode",Map.of("loginId","recover","resetCode",value.equals("000000")?"111111":"000000")),400);
        data(recovery.call("POST","/api/system/public/auth/verifyResetCode",Map.of("loginId","recover","resetCode",value)),200);
        data(recovery.call("POST","/api/system/public/auth/saveResetPassword",Map.of("loginId","recover","resetCode",value,"newPwd","Recovery520!Pass8")),200);
        data(old.call("GET","/api/system/common/auth/getMe",null),401);
        data(login("recover","Recovery520!Pass8").call("GET","/api/system/common/auth/getMe",null),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_USER_PWD_HIST WHERE USER_ID='recover'",Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_LO_ACCESS WHERE REQUEST_URI LIKE '/api/system/public/auth/%' AND PARAMETERS IS NOT NULL",Integer.class)).isZero();
    }

    @Test @Order(6) @DisplayName("TC-50110 TC-50116 TC-50117 현재 역할 회수의403 버전·관리 참조 소유·진행률·첫사용 기한")
    void authorityOwnershipAndDeliveryDeadline() throws Exception {
        var admin=administrator();var other=login("other",PASSWORD);
        var prior=data(other.call("GET","/api/system/common/auth/getMe",null),200);
        String version=other.call("GET","/api/system/common/auth/getMe",null).headers().firstValue("X-Authz-Version").orElseThrow();
        jdbc.update("DELETE FROM SYS_USER_ROLE WHERE USER_ID='other'");
        var denied=other.call("POST","/api/system/service/user/getList",Map.of());data(denied,403);
        assertThat(denied.headers().firstValue("X-Authz-Version")).isPresent().get().isNotEqualTo(version);
        jdbc.update("INSERT INTO SYS_USER_ROLE(USER_ID,ROLE_ID,VALID_FROM_DT,VALID_TO_DT) SELECT 'other',ROLE_ID,VALID_FROM_DT,VALID_TO_DT FROM SYS_USER_ROLE WHERE USER_ID='admin'");
        var progress=first.getBean(com.aiagent.domain.progress.JobProgressStore.class);String job=UUID.randomUUID().toString();progress.start(job,"admin",1L);
        data(admin.call("GET","/api/system/common/progress/getProgress/"+job,null),200);
        data(other.call("GET","/api/system/common/progress/getProgress/"+job,null),400);
        data(other.call("GET","/api/system/common/progress/getProgress/"+UUID.randomUUID(),null),400);
        String otherRef=jdbc.queryForObject("SELECT MANAGEMENT_REF FROM AI_AUTH_SESSION_GRANT WHERE SUBJECT_ID='other' AND STATE_CD='ACTIVE' ORDER BY GRANT_ID DESC LIMIT 1",String.class);
        assertThat(data(admin.call("POST","/api/system/service/user/killSession",Map.of("userId","missing-user","targets",List.of(otherRef))),200).asInt()).isZero();
        data(other.call("GET","/api/system/common/auth/getMe",null),200);
        var undelivered=login("other",PASSWORD);
        jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET CONFIRM_DEADLINE_AT=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE SUBJECT_ID='other' AND FIRST_USED_AT IS NULL AND STATE_CD='ACTIVE'");
        data(undelivered.call("GET","/api/system/common/auth/getMe",null),401);
        data(login("other",PASSWORD).call("GET","/api/system/common/auth/getMe",null),200);
        var anonymous=new Browser();var bundle=anonymous.call("GET","/api/system/public/message/getBundle?lang=ko",null);data(bundle,200);assertThat(bundle.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test @Order(7) @DisplayName("TC-50114 TC-50115 실제 암호·이력·원장/payload SQL 실패는 전체 롤백·쿠키0")
    void databaseFailureRollsBackCredentialsHistoryAndGeneration() throws Exception {
        var admin=administrator();String hash=jdbc.queryForObject("SELECT PWD FROM SYS_USER WHERE USER_ID='admin'",String.class);
        int histories=jdbc.queryForObject("SELECT COUNT(*) FROM SYS_USER_PWD_HIST WHERE USER_ID='admin'",Integer.class);
        jdbc.execute("CREATE TABLE SYSTEM520_FAULT(FLAG BOOLEAN)");jdbc.update("INSERT INTO SYSTEM520_FAULT VALUES(TRUE)");
        jdbc.execute("CREATE FUNCTION system520_reject_session() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF EXISTS(SELECT 1 FROM SYSTEM520_FAULT WHERE FLAG) THEN RAISE EXCEPTION 'system520 injected session write failure'; END IF; RETURN NEW; END $$");
        String target="redis".equals(System.getenv("SYSTEM_AUTH_TEST_PROVIDER"))?"AI_AUTH_SESSION_GRANT":"SPRING_SESSION";
        jdbc.execute("CREATE TRIGGER system520_reject_session BEFORE INSERT OR UPDATE ON "+target+" FOR EACH ROW EXECUTE FUNCTION system520_reject_session()");
        try {
            var response=admin.call("POST","/api/system/common/auth/changePassword",Map.of("currentPwd",PASSWORD,"newPwd","Next520!Password7"));
            assertThat(response.statusCode()).isIn(500,503);assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(jdbc.queryForObject("SELECT PWD FROM SYS_USER WHERE USER_ID='admin'",String.class)).isEqualTo(hash);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_USER_PWD_HIST WHERE USER_ID='admin'",Integer.class)).isEqualTo(histories);
        } finally {jdbc.update("UPDATE SYSTEM520_FAULT SET FLAG=FALSE");}
        data(admin.call("GET","/api/system/common/auth/getMe",null),200);
    }

    @Test @Order(8) @DisplayName("TC-50115 Redis 실제 연결의 DB확정 뒤 발행 실패·권한 회수 경합·새 암호 복구")
    void redisPublicationFailureAndRevocation() throws Exception {
        if(!"redis".equals(System.getenv("SYSTEM_AUTH_TEST_PROVIDER")))return;
        var other=login("other",PASSWORD);data(other.call("GET","/api/system/common/auth/getMe",null),200);var old=other.copy();
        SystemAuthFaults.arm(jdbc,()->{throw new org.springframework.data.redis.RedisConnectionFailureException("시험 발행 연결 실패");});
        var failure=other.call("POST","/api/system/common/auth/changePassword",Map.of("currentPwd",PASSWORD,"newPwd","Changed520!Pass8"));
        data(failure,503);assertThat(json.readTree(failure.body()).path("code").asText()).isEqualTo("PASSWORD_CHANGED_REAUTH_REQUIRED");
        assertThat(failure.headers().allValues("Set-Cookie")).isEmpty();SystemAuthFaults.reset();data(old.call("GET","/api/system/common/auth/getMe",null),401);
        other=login("other","Changed520!Pass8");data(other.call("GET","/api/system/common/auth/getMe",null),200);
        var publishing=new CountDownLatch(1);var proceed=new CountDownLatch(1);var changing=other;
        SystemAuthFaults.arm(jdbc,()->{publishing.countDown();await(proceed);});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var request=pool.submit(()->changing.call("POST","/api/system/common/auth/changePassword",Map.of("currentPwd","Changed520!Pass8","newPwd","ChangedAgain520!Pass6")));
            assertThat(publishing.await(5,TimeUnit.SECONDS)).isTrue();
            pool.submit(()->new TransactionTemplate(first.getBean(PlatformTransactionManager.class)).executeWithoutResult(s->{jdbc.execute("SELECT pg_advisory_xact_lock(1095321685,1)");jdbc.update("UPDATE SYS_USER SET USE_YN='N' WHERE USER_ID='other'");})).get(3,TimeUnit.SECONDS);
            proceed.countDown();var rejected=request.get(5,TimeUnit.SECONDS);data(rejected,503);assertThat(json.readTree(rejected.body()).path("code").asText()).isEqualTo("PASSWORD_CHANGED_REAUTH_REQUIRED");
            assertThat(rejected.headers().allValues("Set-Cookie")).isEmpty();
        } finally {proceed.countDown();SystemAuthFaults.reset();jdbc.update("UPDATE SYS_USER SET USE_YN='Y' WHERE USER_ID='other'");}
        data(login("other","ChangedAgain520!Pass6").call("GET","/api/system/common/auth/getMe",null),200);
    }
}
