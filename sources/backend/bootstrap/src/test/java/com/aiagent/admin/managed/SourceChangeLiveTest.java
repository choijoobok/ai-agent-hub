// @feat FEAT-305
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.portal.*;
import com.aiagent.portal.change.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SourceChangeLiveTest {
    private ManagedLiveFixture f;private String month,group;private int port;
    private final ObjectMapper json=JsonMapper.builder().build();private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String,Object> properties=new ConcurrentHashMap<>();
    @org.springframework.boot.test.context.TestConfiguration
    static class Ports {
        @Bean Change305Verifier sourceChangeTestVerifier(){return new Change305Verifier();}
        @Bean PortalExternalDirectory sourceDirectory(){return (source,subject)->{if(!source.equals("test-hr"))throw ManagedErrorCode.UNAVAILABLE.failure();return new PortalExternalDirectory.Identity(source,subject,subject,"원천 "+subject,"원천 조직","r1");};}
        static volatile boolean retentionEnabled,protectedRecord,expirePending;
        @Bean ChangeRetentionPolicy sourceRetention(){return (receipt,now)->retentionEnabled?new ChangeRetentionPolicy.Eligibility(now.minusSeconds(1),protectedRecord,expirePending,"test-retention"):null;}
    }
    private record User(String id,String subject,UUID binding) {}
    @BeforeAll void start()throws Exception {
        f=new ManagedLiveFixture("EXTERNAL",Ports.class);var env=(ConfigurableEnvironment)f.bean(Environment.class);port=env.getProperty("local.server.port",Integer.class);env.getPropertySources().addFirst(new MapPropertySource("source305",properties));
        month=f.database().queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')",String.class);
        f.database().update("INSERT INTO ai_admin_quota_grade VALUES(900305,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES(900305,?,'수신 시험',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES(900305,900305,'기본',1,1,NULL,'')");
        group=f.groupCall("save",command(null,null,"definition",Map.of("groupNm","수신 시험","description","합성 담당")),200).path("targetId").asText();
        f.database().update("INSERT INTO sys_role(role_id,role_nm,use_yn) VALUES ('CHANGE305','외부 수신 검토','Y')");
        f.database().update("INSERT INTO sys_role_menu_auth(role_id,menu_cd,action_cd) VALUES ('CHANGE305','HUB_PORTAL_USER','READ')");
        f.database().update("INSERT INTO sys_user_role(user_id,role_id) VALUES ('admin','CHANGE305')");
    }
    @AfterAll void stop(){if(f!=null)f.close();}
    @BeforeEach void defaults(){properties.put("app.portal.change.recipient-roles","");Ports.retentionEnabled=false;Ports.protectedRecord=false;Ports.expirePending=false;var v=verifier();v.entered=null;v.release=null;v.lifetime=Duration.ofMinutes(2);v.mode="MONOTONIC";}
    private Change305Verifier verifier(){return f.bean(Change305Verifier.class);}
    private ManagedCommand command(String id,String revision,String scope,Map<String,Object> values){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,scope,values,"수신 경계 검증");}
    private Map<String,Object> profile(User user,String active,String day){var values=new HashMap<String,Object>(Map.of("loginId",user.subject(),"userNm","원천 "+user.subject(),"organizationNm","원천 조직","activeYn",active,"useStartDt",month+day));values.put("useEndDt",null);return values;}
    private String revision(User user){return f.database().queryForObject("SELECT revision FROM ai_admin_portal_user WHERE portal_user_id=?",String.class,Long.valueOf(user.id()));}
    private User create(String subject)throws Exception {
        var values=new HashMap<String,Object>(Map.of("externalSource","test-hr","externalSubject",subject,"activeYn","N","useStartDt",month+"-01"));values.put("useEndDt",null);
        String id=f.adminCall("portalUser","save",command(null,null,"profile",values),200).path("targetId").asText();
        var user=new User(id,subject,f.database().queryForObject("SELECT binding_key FROM ai_admin_portal_external_id WHERE portal_user_id=?",UUID.class,Long.valueOf(id)));
        f.adminCall("portalUser","save",command(id,"1","memberships",Map.of("memberships",List.of(Map.of("groupId",group,"activeYn","Y","memo","합성 소속")),"deactivateUser",false)),200);
        activate(user,"Y","-01");return user;
    }
    private void activate(User user,String active,String day)throws Exception{f.adminCall("portalUser","save",command(user.id(),revision(user),"profile",profile(user,active,day)),200);}
    private String body(User user,String event,String kind,int version)throws Exception{return json.writeValueAsString(Map.of("schemaVersion",1,"sourceEventId",event,"subject",user.subject(),"kind",kind,"occurredAt","2026-11-14T00:00:00Z","sourceVersion","v-"+version));}
    private HttpResponse<String> post(String body,Map<String,String> headers)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+SourceChangeService.PATH)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");headers.forEach(request::header);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> signed(User user,String body)throws Exception{return post(body,verifier().signed(body,user.binding()));}
    private JsonNode data(HttpResponse<String> response,int status)throws Exception{assertThat(response.statusCode()).withFailMessage("HTTP %s: %s",response.statusCode(),response.body()).isEqualTo(status);return json.readTree(response.body()).path("data");}
    private long history(User user){return f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_portal_user_hist WHERE portal_user_id=? AND hist_source_cd='EXTERNAL'",Long.class,Long.valueOf(user.id()));}
    private long count(String table){return f.database().queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test @Order(1) @DisplayName("TC-0054 TC-3051 TC-3056 TC-3057 TC-3059 중지+pending·새 서명/키교체 통지만 복구")
    void pendingReplayDoesNotUndoManualReactivation()throws Exception {
        var user=create("admin");var body=body(user,"pending-1","ORG_MOVED",1);var first=data(signed(user,body),202);assertThat(first.path("notificationState").asText()).isEqualTo("PENDING_NO_RECIPIENT");
        assertThat(history(user)).isEqualTo(1);assertThat(f.database().queryForObject("SELECT active_yn FROM ai_admin_portal_user WHERE portal_user_id=?",String.class,Long.valueOf(user.id()))).isEqualTo("N");
        var receipt=first.path("receiptId").asText();
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_portal_user_hist H JOIN ai_portal_user_change C ON C.history_no=H.history_no AND CAST(C.command_key AS varchar)=H.history_command_key WHERE C.receipt_id=? AND H.hist_actor_id IS NULL",Long.class,Long.valueOf(receipt))).isEqualTo(1);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_history_ref WHERE consumer_key=? AND consumer_kind='EXTERNAL_USER_CHANGE'",Long.class,receipt)).isEqualTo(1);
        assertThat(f.database().queryForObject("SELECT use_yn FROM sys_user WHERE user_id='admin'",String.class)).isEqualTo("Y");
        activate(user,"Y","-01");String current=revision(user);properties.put("app.portal.change.recipient-roles","CHANGE305");verifier().generation="key-2";
        var replay=data(signed(user,body),200);assertThat(replay.path("duplicate").asBoolean()).isTrue();assertThat(replay.path("receiptId").asText()).isEqualTo(receipt);assertThat(revision(user)).isEqualTo(current);assertThat(history(user)).isEqualTo(1);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_portal_change_notification WHERE receipt_id=?",Long.class,Long.valueOf(receipt))).isEqualTo(1);
        data(signed(user,body.replace("ORG_MOVED","RETIRED")),409);
        data(signed(user,body),200);assertThat(history(user)).isEqualTo(1);
    }
    @Test @Order(2) @DisplayName("TC-3052 TC-3058 同時 수신/통지 복구와 ACK 재전송은 효과/인앱 한 세트")
    void concurrentReceiveAndPendingRepair()throws Exception {
        var user=create("concurrent305");var body=body(user,"concurrent-1","RETIRED",1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var one=workers.submit(()->signed(user,body));var two=workers.submit(()->signed(user,body));data(one.get(),202);data(two.get(),202);
            assertThat(history(user)).isEqualTo(1);properties.put("app.portal.change.recipient-roles","CHANGE305");
            one=workers.submit(()->signed(user,body));two=workers.submit(()->signed(user,body));var first=data(one.get(),200);data(two.get(),200);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_portal_change_notification WHERE receipt_id=?",Long.class,Long.valueOf(first.path("receiptId").asText()))).isEqualTo(1);
        }
    }
    @Test @Order(3) @DisplayName("TC-3053 TC-3056 TC-3058 각 필수 저장 장애 rollback과 기존 pending 보존")
    void mandatoryWritesFailAtomically()throws Exception {
        var user=create("failure305");properties.put("app.portal.change.recipient-roles","CHANGE305");
        f.database().execute("CREATE FUNCTION fx_change_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '합성 저장 장애'; END $$");
        int number=0;
        for(String table:List.of("ai_admin_portal_user_hist","ai_portal_user_change","ai_admin_history_ref","sys_noti_inbox","ai_portal_change_notification")) {
            String body=body(user,"failure-"+(++number),"RETIRED",number);long receipts=count("ai_portal_user_change"),notices=count("sys_noti_inbox");String before=revision(user);
            f.database().execute("CREATE TRIGGER fx_change_fail BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fx_change_fail()");
            try{data(signed(user,body),503);}finally{f.database().execute("DROP TRIGGER fx_change_fail ON "+table);}
            assertThat(revision(user)).isEqualTo(before);assertThat(history(user)).isZero();assertThat(count("ai_portal_user_change")).isEqualTo(receipts);assertThat(count("sys_noti_inbox")).isEqualTo(notices);
        }
        properties.put("app.portal.change.recipient-roles","");String body=body(user,"pending-fail","RETIRED",10);var pending=data(signed(user,body),202);String before=revision(user);properties.put("app.portal.change.recipient-roles","CHANGE305");
        f.database().execute("ALTER TABLE sys_user_role RENAME TO fx_source_user_role");
        try{data(signed(user,body),503);}finally{f.database().execute("ALTER TABLE fx_source_user_role RENAME TO sys_user_role");}
        assertThat(revision(user)).isEqualTo(before);assertThat(history(user)).isEqualTo(1);assertThat(f.bean(ChangeStore.class).findReceipt(pending.path("receiptId").asText()).receipt().notificationState()).isEqualTo("PENDING_NO_RECIPIENT");
        data(signed(user,body),200);
    }
    @Test @Order(4) @DisplayName("TC-3054 정확 원천 증거·쿠키 비대체·본문 변조·다른 원천/LOCAL 차단")
    void authenticatedSourceNeverAcceptsBrowserIdentity()throws Exception {
        var user=create("proof305");String body=body(user,"proof-1","RETIRED",1);long receipts=count("ai_portal_user_change");
        data(post(body,Map.of("Cookie","AIHUB_ADMIN_SESSION=fake; AIHUB_PORTAL_SESSION=fake")),401);
        var headers=verifier().signed(body,user.binding());headers.put("X-Signature","wrong");data(post(body,headers),401);
        headers=verifier().signed(body,user.binding());data(post(body.replace("RETIRED","ORG_MOVED"),headers),401);
        headers=verifier().signed(body,user.binding());headers.put("X-AIHUB-Source","unknown");data(post(body,headers),403);
        data(signed(user,body.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"sourceKey\":\"spoof\"")),400);
        assertThat(count("ai_portal_user_change")).isEqualTo(receipts);assertThat(history(user)).isZero();
        f.database().update("UPDATE ai_admin_portal_user SET auth_mode_cd='LOCAL' WHERE portal_user_id=?",Long.valueOf(user.id()));
        try{assertThat(data(signed(user,body),422).path("outcome").asText()).isEqualTo("REJECTED");}finally{f.database().update("UPDATE ai_admin_portal_user SET auth_mode_cd='EXTERNAL' WHERE portal_user_id=?",Long.valueOf(user.id()));}
        assertThat(history(user)).isZero();assertThat(f.bean(SourceChangeService.class).rejectedEnvelopes()).isGreaterThanOrEqualTo(4);
    }
    @Test @Order(5) @DisplayName("TC-3055 TC-30510 NO_CHANGE 순서 진전·과거/동일판·재연결/현재상태 방식")
    void currentBindingAndSourceCursorCannotBeRewound()throws Exception {
        var user=create("order305");data(signed(user,body(user,"order-1","RETIRED",10)),202);
        assertThat(data(signed(user,body(user,"order-2","ORG_MOVED",20)),202).path("outcome").asText()).isEqualTo("NO_CHANGE");assertThat(history(user)).isEqualTo(1);
        activate(user,"Y","-01");String before=revision(user);
        assertThat(data(signed(user,body(user,"order-old","RETIRED",15)),422).path("safeCode").asText()).isEqualTo("STALE_EVENT");
        data(signed(user,body(user,"order-equal","RETIRED",20)),409);assertThat(revision(user)).isEqualTo(before);
        UUID next=UUID.randomUUID();f.database().update("UPDATE ai_admin_portal_external_id SET binding_key=? WHERE portal_user_id=?",next,Long.valueOf(user.id()));
        data(signed(user,body(user,"old-binding","RETIRED",21)),409);assertThat(revision(user)).isEqualTo(before);
        var current=new User(user.id(),user.subject(),next);verifier().mode="CURRENT_STATE";data(signed(current,body(current,"current-state","RETIRED",30)),202);
        assertThat(history(user)).isEqualTo(2);
    }
    @Test @Order(6) @DisplayName("TC-3055 잠금 밖 최신성 확인 중 부모판 변경/기한 만료는 재시도409·결과0")
    void changedParentAndExpiredEvidenceNeedFreshRequest()throws Exception {
        var user=create("race305");String body=body(user,"race-1","RETIRED",1);var v=verifier();v.entered=new CountDownLatch(1);v.release=new CountDownLatch(1);
        try(var worker=Executors.newVirtualThreadPerTaskExecutor()) {
            var request=worker.submit(()->signed(user,body));assertThat(v.entered.await(10,TimeUnit.SECONDS)).isTrue();activate(user,"Y","-02");v.release.countDown();data(request.get(),409);
        }finally{v.entered=null;v.release=null;}
        assertThat(history(user)).isZero();data(signed(user,body),202);assertThat(history(user)).isEqualTo(1);
        activate(user,"Y","-01");String next=body(user,"race-expired","RETIRED",2);v.lifetime=Duration.ofMillis(200);v.entered=new CountDownLatch(1);v.release=new CountDownLatch(1);
        try(var worker=Executors.newVirtualThreadPerTaskExecutor()) {
            var request=worker.submit(()->signed(user,next));assertThat(v.entered.await(10,TimeUnit.SECONDS)).isTrue();Thread.sleep(250);v.release.countDown();data(request.get(),409);
        }finally{v.entered=null;v.release=null;v.lifetime=Duration.ofMinutes(2);}
        assertThat(history(user)).isEqualTo(1);data(signed(user,next),202);assertThat(history(user)).isEqualTo(2);
        activate(user,"Y","-01");String changed=body(user,"race-generation","ORG_MOVED",3);v.entered=new CountDownLatch(1);v.release=new CountDownLatch(1);
        try(var worker=Executors.newVirtualThreadPerTaskExecutor()) {
            var request=worker.submit(()->signed(user,changed));assertThat(v.entered.await(10,TimeUnit.SECONDS)).isTrue();v.generation="key-race";v.release.countDown();data(request.get(),409);
        }finally{v.entered=null;v.release=null;}
        assertThat(history(user)).isEqualTo(2);data(signed(user,changed),202);assertThat(history(user)).isEqualTo(3);
    }
    @Test @Order(7) @DisplayName("TC-30511 TC-30512 파기 최소키·pending 보존 정책·인앱 파기 후 재생성0")
    void retentionPreservesReplayBarrierAndSeparatesInboxLifetime()throws Exception {
        var user=create("retention305");String body=body(user,"retention-1","RETIRED",1);var first=data(signed(user,body),202);String id=first.path("receiptId").asText();var retention=f.bean(ChangeRetentionService.class);
        assertThat(retention.purge(id)).isFalse();Ports.retentionEnabled=true;assertThat(retention.purge(id)).isFalse();Ports.expirePending=true;Ports.protectedRecord=true;assertThat(retention.purge(id)).isFalse();Ports.protectedRecord=false;
        assertThat(retention.purge(id)).isTrue();verifier().generation="key-3";data(signed(user,body),409);assertThat(history(user)).isEqualTo(1);
        properties.put("app.portal.change.recipient-roles","CHANGE305");var another=create("inbox305");String notified=body(another,"inbox-1","ORG_MOVED",1);String stored=data(signed(another,notified),200).path("receiptId").asText();
        f.database().update("DELETE FROM sys_noti_inbox WHERE noti_id IN (SELECT noti_id FROM ai_portal_change_notification WHERE receipt_id=?)",Long.valueOf(stored));long notices=count("sys_noti_inbox");
        data(signed(another,notified),200);assertThat(count("sys_noti_inbox")).isEqualTo(notices);
    }
    @Test @Order(8) @DisplayName("TC-3056 원천 수신 신규 마이그레이션 적용·rollback·재적용")
    void migrationRoundtrip()throws Exception {
        String url=System.getenv("MANAGED_TEST_JDBC"),username=System.getenv("MANAGED_TEST_USER"),password=System.getenv().getOrDefault("MANAGED_TEST_PASSWORD","");String schema="source305_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,username,password)){connection.createStatement().execute("CREATE SCHEMA "+schema);}
        try(var connection=DriverManager.getConnection(url+"?currentSchema="+schema,username,password)) {
            connection.createStatement().execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME varchar PRIMARY KEY,NEXT_VAL bigint NOT NULL); CREATE TABLE SYS_MESSAGE_KEY(MSG_KEY varchar PRIMARY KEY,USE_SCOPE varchar,MSG_DESC varchar); CREATE TABLE SYS_MESSAGE(MSG_KEY varchar,LANG_CD varchar,MSG_VAL varchar,PRIMARY KEY(MSG_KEY,LANG_CD))");
            try(var migration=new liquibase.Liquibase("db/source305-only.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
                migration.update(new liquibase.Contexts());migration.rollback(2,"");
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+schema+"' AND table_name LIKE 'ai_portal_%'")){rows.next();assertThat(rows.getInt(1)).isZero();}
                migration.update(new liquibase.Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+schema+"' AND table_name LIKE 'ai_portal_%'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(3);}
            }
        }
    }
}
