// @feat FEAT-024
package com.aiagent.admin.managed;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.searchtag.*;
import com.aiagent.core.content.SearchTagContent;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchTagLiveTest {
    private ManagedLiveFixture f;
    @BeforeAll void start() throws Exception {f=new ManagedLiveFixture();}
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String id,String revision,String text,String use,List<?> synonyms){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,"definition",Map.of("searchText",text,"useYn",use,"synonyms",synonyms),"태그 시험");}
    private ManagedCommand remove(String id,String revision){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,"definition",Map.of(),"태그 정리");}
    @Test @Order(1) @DisplayName("TC-0244 TC-0243 원문·전체 이력·재수신·정확 판·OFF 후보 제외")
    void saveReplayHistoryAndProtectedConsumption() throws Exception {
        var first=command(null,null," ＷＭＳ ","Y",List.of(Map.of("clientKey","new1","searchText","Stock","description","재고")));
        var result=f.adminCall("searchTag","save",first,200);String id=result.path("targetId").asText();
        assertThat(f.adminCall("searchTag","save",first,200)).isEqualTo(result);
        assertThat(result.path("createdIds").path("new1").asText()).matches("[1-9][0-9]*");
        var detail=f.adminCall("searchTag","getOne",Map.of("targetId",id),200);
        assertThat(detail.path("tag").path("searchText").asText()).isEqualTo(" ＷＭＳ ");
        String synonym=detail.path("synonyms").get(0).path("synonymId").asText();
        assertThat(synonym).isEqualTo(result.path("createdIds").path("new1").asText());
        f.adminCall("searchTag","save",command(null,null,"wms","N",List.of()),422);
        var second=f.adminCall("searchTag","save",command(null,null,"ERP","Y",List.of(Map.of("clientKey","new2","searchText","Stock"))),200);
        var page=f.adminCall("searchTag","getList",Map.of("conditions",List.of(Map.of("field","searchText","op","contains","value","ＳＴＯＣＫ")),"sorts",List.of(),"page",Map.of("page",0,"size",20)),200);
        assertThat(page.path("content").size()).isEqualTo(2);
        f.adminCall("searchTag","save",command(second.path("targetId").asText(),"1","ERP","Y",List.of(Map.of("synonymId",synonym,"searchText","Stock"))),422);
        f.database().update("INSERT INTO fx_consumer VALUES ('tag-use',false)");
        var use=new SearchTagContent.Use("agent","fixture","tag-use","SEARCH");var port=f.bean(SearchTagContent.class);
        assertThat(port.current(use,List.of(id))).hasSize(1);
        f.adminCall("searchTag","save",command(id,"1","WMS new","N",List.of(Map.of("synonymId",synonym,"searchText","Stock","description","재고"))),200);
        assertThat(port.current(use,List.of(id))).isEmpty();
        assertThat(port.exact(use,id,"1").searchText()).isEqualTo(" ＷＭＳ ");
        assertThatThrownBy(()->port.exact(use,id,"2"));
        assertThatThrownBy(()->port.current(new SearchTagContent.Use("unknown","x","x","SEARCH"),List.of()));
        f.adminCall("searchTag","save",command(id,"1","낡음","N",List.of()),409);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_tag_synonym_hist WHERE tag_id=?",Integer.class,Long.parseLong(id))).isEqualTo(2);
    }
    @Test @Order(2) @DisplayName("TC-0244 권한·이력 실패 무효과와 삭제 참조 확인 실패 차단")
    void rollbackPermissionsAndFailClosedDeletion() throws Exception {
        var failed=command(null,null,"FAIL_HISTORY","N",List.of());
        f.database().execute("ALTER TABLE ai_admin_search_tag_hist ADD CONSTRAINT tag_fail CHECK(search_text<>'FAIL_HISTORY')");
        try{f.adminCall("searchTag","save",failed,503);}finally{f.database().execute("ALTER TABLE ai_admin_search_tag_hist DROP CONSTRAINT tag_fail");}
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_search_tag WHERE search_text='FAIL_HISTORY'",Integer.class)).isZero();
        assertThat(f.bean(JdbcManagedJournal.class).find(failed.commandKey())).isEmpty();
        String id=f.adminCall("searchTag","save",command(null,null,"삭제 대상","N",List.of(Map.of("clientKey","s","searchText","다른 표현"))),200).path("targetId").asText();
        f.adminCall("searchTag","remove",remove(id,"1"),409);
        f.adminCall("searchTag","save",command(id,"1","삭제 대상","N",List.of()),200);
        var ref=f.adminCall("searchTag","getReferences",Map.of("targetId",id),200);
        assertThat(ref.path("state").asText()).isEqualTo("NOT_CONNECTED");assertThat(ref.path("reference").asText()).isEqualTo("ABSENT");
        f.database().execute("CREATE TABLE ai_admin_knowledge_tag (tag_id bigint, active_yn character(1))");
        try {
            f.database().update("INSERT INTO ai_admin_knowledge_tag VALUES (?,'N')",Long.parseLong(id));
            assertThat(f.adminCall("searchTag","getReferences",Map.of("targetId",id),200).path("reference").asText()).isEqualTo("UNKNOWN");
            f.adminCall("searchTag","remove",remove(id,"2"),503);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_knowledge_tag",Integer.class)).isEqualTo(1);
        } finally {f.database().execute("DROP TABLE ai_admin_knowledge_tag");}
        f.database().update("INSERT INTO sys_role_menu_auth(role_id,menu_cd,action_cd) VALUES ('READER040','HUB_KNOWLEDGE_TAG','READ')");
        var reader=f.login("reader040");
        assertThat(reader.call("POST",SearchTagService.BASE+"/getOne",Map.of("targetId",id)).statusCode()).isEqualTo(200);
        assertThat(reader.call("POST",SearchTagService.BASE+"/remove",remove(id,"2")).statusCode()).isEqualTo(403);
        var removal=remove(id,"2");var result=f.adminCall("searchTag","remove",removal,200);
        assertThat(f.adminCall("searchTag","remove",removal,200)).isEqualTo(result);
        assertThat(f.database().queryForList("SELECT hist_action FROM ai_admin_search_tag_hist WHERE tag_id=? ORDER BY result_revision",String.class,Long.parseLong(id))).containsExactly("CREATE","CHANGE","DELETE");
    }
    @Test @Order(3) @DisplayName("TC-0243 지식 연결 포트와 삭제 직렬화·삭제 뒤 참조 삽입 거부")
    void connectionBoundarySerializesDeletion() throws Exception {
        String id=f.adminCall("searchTag","save",command(null,null,"연결 경합","N",List.of()),200).path("targetId").asText();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var transaction=new TransactionTemplate(f.bean(PlatformTransactionManager.class));
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var linking=executor.submit(()->transaction.execute(status->{f.bean(SearchTagService.class).requireExisting(List.of(id));entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("시험 해제 시간 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}return true;}));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var deleting=executor.submit(()->f.adminCall("searchTag","remove",remove(id,"1"),200));
            try {assertThatThrownBy(()->deleting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);} finally {release.countDown();}
            assertThat(linking.get(10,TimeUnit.SECONDS)).isTrue();deleting.get(10,TimeUnit.SECONDS);
        }
        assertThatThrownBy(()->transaction.execute(status->{f.bean(SearchTagService.class).requireExisting(List.of(id));return null;}));
    }
    @Test @Order(4) @DisplayName("TC-0244 태그 마이그레이션 롤백·재적용")
    void migrationRollbackAndReapply() throws Exception {
        try(var connection=f.bean(javax.sql.DataSource.class).getConnection();var migration=new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
            int count=f.database().queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE orderexecuted >= (SELECT orderexecuted FROM databasechangelog WHERE id='admin-014-search-tag')",Integer.class);
            migration.rollback(count,"");
            assertThat(f.database().queryForObject("SELECT to_regclass('ai_admin_search_tag')",String.class)).isNull();
            migration.update(new liquibase.Contexts());
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_search_tag",Integer.class)).isZero();
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM sys_menu_api WHERE menu_cd='HUB_KNOWLEDGE_TAG'",Integer.class)).isEqualTo(6);
        }
    }
}
