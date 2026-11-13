// @feat FEAT-040
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.AiAgentApplication;
import com.aiagent.domain.authz.service.AdminAuthorityProtection;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import liquibase.*;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import static com.aiagent.admin.managed.ManagedLiveConfiguration.*;

final class ManagedLiveFixture implements AutoCloseable {
    private static final String ORIGIN="http://127.0.0.1:18040",PASSWORD="Managed040!Fixture9";
    private final ObjectMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConfigurableApplicationContext app;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FixtureOwner owner;
    private final ManagedLocks locks;
    private final ManagedHistoryService history;
    private final ManagedRetentionService retention;
    private final ManagedCommandService commands;
    private final String url,user,password;
    private final int port;
    private final List<Browser> administrators;
    private final AtomicInteger round=new AtomicInteger();
    ManagedLiveFixture() throws Exception {
        url=System.getenv("MANAGED_TEST_JDBC"); user=System.getenv("MANAGED_TEST_USER"); password=System.getenv().getOrDefault("MANAGED_TEST_PASSWORD","");
        if(url==null||!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/managed040_[A-Za-z0-9_]+"))throw new IllegalStateException("전용 localhost managed040_ 시험 DB가 필요하다");
        String schema="managed040_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("CREATE SCHEMA "+schema);
        app=new SpringApplication(AiAgentApplication.class,ManagedLiveConfiguration.class).run("--spring.profiles.active=system-auth-test,managed-test",
                "--server.port=0","--spring.datasource.url="+url+"?currentSchema="+schema,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--app.auth.session.origin="+ORIGIN,"--app.auth.session.local-http=true","--app.auth.session.provider=jdbc",
                "--app.auth.session.epoch="+UUID.randomUUID(),"--app.auth.session.store-instance=managed040-test","--app.admin.history.retention-days=1",
                "--app.security.concurrent-session.max-sessions=4","--app.logging.sql.enabled=false","--logging.level.root=WARN");
        port=app.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
        jdbc=new JdbcTemplate(app.getBean(DataSource.class)); tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        owner=app.getBean(FixtureOwner.class);locks=app.getBean(ManagedLocks.class);history=app.getBean(ManagedHistoryService.class);
        retention=app.getBean(ManagedRetentionService.class);commands=app.getBean(ManagedCommandService.class);
        jdbc.update("UPDATE sys_user SET pwd=?,pwd_init_yn='N',pwd_chg_dtm=CURRENT_TIMESTAMP WHERE user_id='admin'",app.getBean(PasswordEncoder.class).encode(PASSWORD));
        for(String id:List.of("other040","revoked040","savefirst040","reader040")) {
            jdbc.update("INSERT INTO sys_user(user_id,user_nm,pwd,pwd_init_yn,pwd_chg_dtm,locale_cd) SELECT ?,?,pwd,'N',CURRENT_TIMESTAMP,'ko' FROM sys_user WHERE user_id='admin'",id,id);
            if(!id.equals("reader040"))jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,role_id FROM sys_user_role WHERE user_id='admin'",id);
        }
        jdbc.update("INSERT INTO sys_role(role_id,role_nm,use_yn) VALUES ('READER040','시험 조회 권한','Y')");
        jdbc.update("INSERT INTO sys_role_menu_auth(role_id,menu_cd,action_cd) VALUES ('READER040','USER_MGMT','READ')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES ('reader040','READER040')");
        administrators=List.of(login("admin"),login("admin"));
    }
    public void close(){app.close();}
    final class Browser {
        final Map<String,String> cookies=new HashMap<>();
        HttpResponse<String> call(String method,String path,Object body) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
            if(!cookies.isEmpty())request.header("Cookie",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
            if(!method.equals("GET")) {
                request.header("Origin",ORIGIN);
                if(cookies.containsKey("AIHUB_ADMIN_CSRF"))request.header("X-AIHUB-ADMIN-CSRF",cookies.get("AIHUB_ADMIN_CSRF"));
            }
            if(body!=null)request.header("Content-Type","application/json");
            var response=http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            for(String header:response.headers().allValues("Set-Cookie")){var pair=header.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}
            return response;
        }
    }
    private Browser login(String id) throws Exception {
        var browser=new Browser();data(browser.call("GET","/api/system/public/auth/getCsrf",null),200);
        data(browser.call("POST","/api/system/public/auth/login",Map.of("loginId",id,"password",PASSWORD)),200);return browser;
    }
    private JsonNode data(HttpResponse<String> response,int expected) {
        assertThat(response.statusCode()).as("상태/응답: %s",response.body()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
    ManagedCommand command(String target,String revision,String name,List<String> children) {
        return new ManagedCommand(UUID.randomUUID().toString(),target,revision,"main",Map.of("name",name,"children",children),"사유");
    }
    JsonNode save(ManagedCommand c,int status) throws Exception { return data(administrators.get(Math.floorMod(round.getAndIncrement(),2)).call("POST",SAVE,c),status); }
    JsonNode result(String key) throws Exception { return data(administrators.getFirst().call("POST",ManagedRequestFilter.RESULT_PATH,Map.of("commandKey",key,"ownerFeature","FEAT-006")),200); }
    private JsonNode remove(ManagedCommand c,int status) throws Exception {return data(administrators.getFirst().call("POST",REMOVE,c),status);}
    long currentCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM fx_asset",Long.class);}
    int children(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM fx_child WHERE asset_id=?",Integer.class,Long.parseLong(id));}
    int historyChildren(String no){return jdbc.queryForObject("SELECT COUNT(*) FROM fx_child_hist WHERE history_no=?",Integer.class,Long.parseLong(no));}
    String currentName(String id){return jdbc.queryForObject("SELECT name FROM fx_asset WHERE asset_id=?",String.class,Long.parseLong(id));}
    void failNextHistory(){owner.failHistory.set(true);}
    void assertConcurrentRevisions() throws Exception {
        var initial=save(command(null,null,"같은 기준본",List.of()),200);
        var a=command(initial.path("targetId").asText(),"1","동시 A",List.of());
        var b=command(initial.path("targetId").asText(),"1","동시 B",List.of());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(()->{await(start);return administrators.get(0).call("POST",SAVE,a).statusCode();});
            var second=pool.submit(()->{await(start);return administrators.get(1).call("POST",SAVE,b).statusCode();});
            start.countDown();assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fx_asset_hist WHERE asset_id=?",Integer.class,initial.path("targetId").asLong())).isEqualTo(2);
    }
    void assertNoChangeRetention(JsonNode result) throws Exception {
        String key=result.path("commandKey").asText(),id=result.path("targetId").asText();
        age(result);
        var disabled=new ManagedRetentionService(app.getBean(ManagedRegistry.class),locks,history,app.getBean(ManagedCommandGuard.class),app.getBean(com.aiagent.support.time.BusinessCalendar.class),null);
        Boolean forgotten=tx.execute(status->disabled.forgetResult(key));assertThat(forgotten).isFalse();
        assertThat(retention.forgetResult(key)).isTrue();
        assertThat(currentName(id)).isEqualTo("그대로");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fx_asset_hist WHERE asset_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        data(administrators.getFirst().call("POST",ManagedRequestFilter.RESULT_PATH,Map.of("commandKey",key,"ownerFeature","FEAT-006")),409);
    }
    void assertTypedHistoryShapeAndOriginalTime(String id) {
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT reg_dtm) FROM fx_asset_hist WHERE asset_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='fx_child_hist' AND column_name='hist_dtm'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='fx_asset_hist' AND column_name IN ('secret','observed')",Integer.class)).isZero();
    }
    void assertResultFailureRollback() throws Exception {
        var command=command(null,null,"결과 원장 실패",List.of("원자 자식"));long before=currentCount();
        jdbc.execute("ALTER TABLE ai_admin_managed_command ADD CONSTRAINT fixture_result_failure CHECK (command_key <> '"+command.commandKey()+"')");
        try {save(command,503);assertThat(currentCount()).isEqualTo(before);assertThat(result(command.commandKey()).path("status").asText()).isEqualTo("UNKNOWN");}
        finally {jdbc.execute("ALTER TABLE ai_admin_managed_command DROP CONSTRAINT fixture_result_failure");}
        save(command,200);
    }
    void assertOtherActorUnknown(String key) throws Exception {
        assertThat(data(login("other040").call("POST",ManagedRequestFilter.RESULT_PATH,Map.of("commandKey",key,"ownerFeature","FEAT-006")),200).path("status").asText()).isEqualTo("UNKNOWN");
        data(login("reader040").call("POST",ManagedRequestFilter.RESULT_PATH,Map.of("commandKey",key,"ownerFeature","FEAT-006")),403);
    }
    void assertOtherJournalConflictAndFailure() throws Exception {
        var command=command(null,null,"원장 충돌",List.of());jdbc.update("INSERT INTO fx_other_command VALUES (?)",command.commandKey());save(command,409);
        var journal=app.getBean(FixtureJournal.class);journal.unavailable.set(true);
        try { save(command(null,null,"원장 장애",List.of()),503); } finally { journal.unavailable.set(false); }
    }
    void assertSecretAndForeignOwnerRejected() throws Exception {
        for(String field:List.of("password","observed","actor","apiFieldId","serverExtractionDigest")) {
            var values=new HashMap<String,Object>();values.put("name","차단");values.put("children",List.of());values.put(field,"위조");
            save(new ManagedCommand(UUID.randomUUID().toString(),null,null,"main",values,"사유"),400);
        }
    }
    void assertOwnerValidation() throws Exception {
        long before=currentCount();
        var values=Map.<String,Object>of("name","자식 재귀속","children",List.of(Map.of("childId","1","ownerId","2")));
        save(new ManagedCommand(UUID.randomUUID().toString(),null,null,"main",values,"사유"),400);
        assertThat(currentCount()).isEqualTo(before);
        var original=save(command(null,null,"소유 정상",List.of("소유 자식")),200);
        assertThat(historyChildren(original.path("historyNo").asText())).isEqualTo(1);
    }
    private ManagedHistoryStore.Ref ref(JsonNode result){return new ManagedHistoryStore.Ref("FIXTURE",result.path("targetId").asText(),result.path("revision").asText());}
    private ManagedHistoryService.Use use(String key) {
        jdbc.update("INSERT INTO fx_consumer VALUES (?,false)",key);return new ManagedHistoryService.Use("agent","fixture",key,"EXECUTION");
    }
    private void age(JsonNode value) {
        jdbc.update("UPDATE fx_asset_hist SET hist_dtm=CURRENT_TIMESTAMP-INTERVAL '3 days' WHERE history_no=?",value.path("historyNo").asLong());
        jdbc.update("UPDATE ai_admin_managed_command SET recorded_dtm=CURRENT_TIMESTAMP-INTERVAL '3 days' WHERE command_key=?",value.path("commandKey").asText());
    }
    private JsonNode changed(JsonNode value,String name) throws Exception {return save(command(value.path("targetId").asText(),value.path("revision").asText(),name,List.of()),200);}
    private JsonNode linked(String name,JsonNode target) throws Exception {
        return save(new ManagedCommand(UUID.randomUUID().toString(),null,null,"main",Map.of("name",name,"children",List.of(),"reference",Map.of("id",target.path("targetId").asText(),"revision",target.path("revision").asText())),"사유"),200);
    }
    void assertHistoryProtectionAndPurge() throws Exception {
        var leaf=save(command(null,null,"과거 하위",List.of()),200);changed(leaf,"새 하위");
        var a=linked("상위A",leaf);var b=linked("상위B",leaf);changed(a,"새 A");changed(b,"새 B");
        for(var v:List.of(leaf,a,b))age(v);
        var use=use("diamond");history.protect(use,List.of(ref(a),ref(b)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_admin_history_ref WHERE consumer_key='diamond'",Integer.class)).isEqualTo(3);
        assertThat(history.readExact(use,ref(leaf)).ref()).isEqualTo(ref(leaf));
        assertThat(retention.forgetResult(leaf.path("commandKey").asText())).isFalse();
        assertThatThrownBy(()->history.release(use));
        jdbc.update("UPDATE fx_consumer SET ended=true WHERE consumer_key='diamond'");history.release(use);
        // 보관 중인 상위 관리 이력 자체도 하위 판을 보호한다.
        assertThat(retention.forgetResult(leaf.path("commandKey").asText())).isFalse();
        for(var v:List.of(a,b)) {assertThat(retention.forgetResult(v.path("commandKey").asText())).isTrue();assertThat(retention.purge(ref(v))).isTrue();}
        assertThat(retention.forgetResult(leaf.path("commandKey").asText())).isTrue();
        assertThat(retention.purge(ref(leaf))).isTrue();
        assertThatThrownBy(()->history.protect(use("gone"),List.of(ref(leaf))));
        data(administrators.getFirst().call("POST",ManagedRequestFilter.RESULT_PATH,Map.of("commandKey",leaf.path("commandKey").asText(),"ownerFeature","FEAT-006")),409);
        var deleted=save(command(null,null,"삭제 직전",List.of("기존 자식")),200);
        var removal=remove(command(deleted.path("targetId").asText(),"1","삭제 직전",List.of()),200);
        assertThat(removal.path("revision").asText()).isEqualTo("2");
        assertThat(owner.typed.exact(ref(deleted)).action()).isEqualTo("CREATE");
        var event=owner.typed.event(removal.path("historyNo").asLong());assertThat(event.action()).isEqualTo("DELETE");
        assertThat(event.rows().get("FX_ASSET").getFirst().get("REVISION")).isEqualTo(1L);
        assertThat(historyChildren(removal.path("historyNo").asText())).isEqualTo(1);
        assertHistoryPurgeRaces();
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("경합 동기화 시간 초과"); }
        catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
    }
    private void awaitBlocked(int namespace) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<deadline) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE locktype='advisory' AND classid=?::oid AND NOT granted",Long.class,namespace)>0)return;
            try {Thread.sleep(10);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
        }
        throw new AssertionError("예상한 트랜잭션 잠금 대기를 관측하지 못했다");
    }
    private void assertHistoryPurgeRaces() throws Exception {
        for(boolean protectionFirst:List.of(true,false)) {
            var v=save(command(null,null,"파기 경합"+protectionFirst,List.of()),200);changed(v,"새 경합"+protectionFirst);age(v);
            assertThat(retention.forgetResult(v.path("commandKey").asText())).isTrue();
            var use=use("race"+protectionFirst);var held=new CountDownLatch(1);var release=new CountDownLatch(1);
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                var first=pool.submit(()->tx.execute(status->{if(protectionFirst)history.protect(use,List.of(ref(v)));else assertThat(retention.purge(ref(v))).isTrue();held.countDown();await(release);return true;}));
                await(held);
                var second=pool.submit(()->{if(protectionFirst)return retention.purge(ref(v));history.protect(use,List.of(ref(v)));return true;});
                awaitBlocked(1296519734);
                release.countDown();assertThat(first.get(12,TimeUnit.SECONDS)).isTrue();
                if(protectionFirst)assertThat(second.get(12,TimeUnit.SECONDS)).isFalse();
                else assertThatThrownBy(()->second.get(12,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            } finally {release.countDown();}
        }
    }
    void assertReferenceRaces() throws Exception {
        var v=save(command(null,null,"참조 보호",List.of()),200);long id=v.path("targetId").asLong();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var create=pool.submit(()->tx.execute(status->{locks.ordered(List.of(new ManagedLocks.Key(3,"FIXTURE",Long.toString(id))));jdbc.update("INSERT INTO fx_reference VALUES (?)",id);held.countDown();await(release);return true;}));
            await(held);var delete=pool.submit(()->remove(command(Long.toString(id),"1","참조 보호",List.of()),409));
            release.countDown();create.get(12,TimeUnit.SECONDS);delete.get(12,TimeUnit.SECONDS);
        } finally {release.countDown();}
        jdbc.update("DELETE FROM fx_reference WHERE asset_id=?",id);
        var deleting=new CountDownLatch(1);var finish=new CountDownLatch(1);
        owner.beforeApply.set(()->{deleting.countDown();await(finish);});
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var delete=pool.submit(()->remove(command(Long.toString(id),"1","참조 보호",List.of()),200));await(deleting);
            var create=pool.submit(()->tx.execute(status->{locks.ordered(List.of(new ManagedLocks.Key(3,"FIXTURE",Long.toString(id))));jdbc.update("INSERT INTO fx_reference VALUES (?)",id);return true;}));
            awaitBlocked(1296519733);finish.countDown();delete.get(12,TimeUnit.SECONDS);
            assertThatThrownBy(()->create.get(12,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        } finally {finish.countDown();}
    }
    private void revoke(String id) {
        tx.executeWithoutResult(status->{app.getBean(AdminAuthorityProtection.class).acquire(true);jdbc.update("UPDATE sys_user SET use_yn='N' WHERE user_id=?",id);});
    }
    void assertAuthorityBoundary() throws Exception {
        var command=command(null,null,"우회 차단",List.of());
        assertThatThrownBy(()->tx.execute(status->commands.save("API-990",command)));
        data(login("reader040").call("POST",SAVE,command),403);
        var browser=login("revoked040");var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var blocker=pool.submit(()->tx.execute(status->{locks.command(command.commandKey());held.countDown();await(release);return true;}));await(held);
            var saving=pool.submit(()->browser.call("POST",SAVE,command));
            awaitBlocked(1296519732);
            // 대기 관리 요청이 ADMIN 공유 잠금을 먼저 잡지 않아야 회수가 완료된다.
            pool.submit(()->revoke("revoked040")).get(8,TimeUnit.SECONDS);
            release.countDown();blocker.get(12,TimeUnit.SECONDS);data(saving.get(12,TimeUnit.SECONDS),401);
        } finally {release.countDown();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_admin_managed_command WHERE command_key=?",Integer.class,command.commandKey())).isZero();
        var original=command(null,null,"원 트랜잭션만",List.of());
        owner.beforeApply.set(()->{
            var inner=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(()->inner.execute(status->commands.save("API-990",original)));
        });
        save(original,200);
        assertThatThrownBy(()->tx.execute(status->commands.save("API-990",original)));
        var savingBrowser=login("savefirst040");var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var saved=command(null,null,"저장 선행",List.of());owner.beforeApply.set(()->{entered.countDown();await(finish);});
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var saving=pool.submit(()->savingBrowser.call("POST",SAVE,saved));await(entered);
            var revoking=pool.submit(()->revoke("savefirst040"));awaitBlocked(1095321685);
            finish.countDown();data(saving.get(12,TimeUnit.SECONDS),200);revoking.get(12,TimeUnit.SECONDS);
        } finally {finish.countDown();}
        data(savingBrowser.call("POST",SAVE,saved),401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_admin_managed_command WHERE command_key=?",Integer.class,saved.commandKey())).isEqualTo(1);
    }
    void assertMigrationAndIds() throws Exception {
        long first=app.getBean(ManagedIds.class).next("AI_ADMIN_FIXTURE_ROW");
        tx.executeWithoutResult(status->{app.getBean(ManagedIds.class).next("AI_ADMIN_FIXTURE_ROW");status.setRollbackOnly();});
        assertThat(app.getBean(ManagedIds.class).next("AI_ADMIN_FIXTURE_ROW")).isGreaterThan(first+1);
        String schema="migration040_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("CREATE SCHEMA "+schema);
        try(var connection=DriverManager.getConnection(url+"?currentSchema="+schema,user,password)) {
            connection.createStatement().execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME varchar(255) PRIMARY KEY,NEXT_VAL bigint NOT NULL)");
            connection.createStatement().execute("CREATE TABLE SYS_API(API_URL varchar PRIMARY KEY,API_NM varchar NOT NULL,API_DESC varchar NOT NULL,ACTION_CD varchar NOT NULL); CREATE TABLE SYS_MESSAGE_KEY(MSG_KEY varchar PRIMARY KEY,USE_SCOPE varchar,MSG_DESC varchar); CREATE TABLE SYS_MESSAGE(MSG_KEY varchar,LANG_CD varchar,MSG_VAL varchar,PRIMARY KEY(MSG_KEY,LANG_CD)); CREATE TABLE SYS_CACHE_VERSION(CACHE_NAME varchar PRIMARY KEY,VERSION bigint,MOD_DTM timestamptz)");
            try(var migration=new Liquibase("db/changelog/modules/admin/db.changelog-admin.yaml",new ClassLoaderResourceAccessor(),new JdbcConnection(connection))) {
                migration.update(new Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+schema+"' AND table_name LIKE 'ai_admin_%'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(3);}
                migration.rollback(3,"");
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+schema+"' AND table_name LIKE 'ai_admin_%'")){rows.next();assertThat(rows.getInt(1)).isZero();}
                migration.update(new Contexts());
            }
        }
    }
}
