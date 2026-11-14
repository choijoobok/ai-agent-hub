// @feat FEAT-010
package com.aiagent.admin.managed;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.content.ChatHelpContent;
import com.aiagent.support.content.SafeContentRenderer;
import java.util.*;
import org.junit.jupiter.api.*;
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatHelpLiveTest {
    private ManagedLiveFixture f;
    @BeforeAll void start() throws Exception{f=new ManagedLiveFixture();}
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String code,String revision,String format,String body){return new ManagedCommand(UUID.randomUUID().toString(),code,revision,"content",Map.of("formatCd",format,"body",body),"검토 사유");}
    @Test @Order(1) @DisplayName("TC-0108 TC-0103 TC-0105 선택 저장·재수신·충돌·정확 과거 판 보호") void selectedCodeAtomicSaveReplayConflictAndExactProtectedVersion() throws Exception {
        var rows=f.adminCall("chatHelp","getList",Map.of(),200);assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("helpCd").asText()).isEqualTo("USAGE_HELP");
        assertThat(rows.get(0).path("state").asText()).isEqualTo("UNSET");
        var command=command("USAGE_HELP","1","HTML","  <p onclick='bad()'>첫 안내</p>  ");
        var first=f.adminCall("chatHelp","save",command,200);
        assertThat(f.adminCall("chatHelp","save",command,200)).isEqualTo(first);
        var current=f.adminCall("chatHelp","getOne",Map.of("helpCd","USAGE_HELP"),200);
        assertThat(current.path("body").asText()).isEqualTo(command.values().get("body"));
        assertThat(current.path("revision").asText()).isEqualTo("2");
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_chat_help_hist WHERE help_cd='USAGE_HELP'",Long.class)).isEqualTo(1);
        f.database().update("INSERT INTO fx_consumer VALUES ('help-use',false)");
        var use=new ChatHelpContent.Use("agent","fixture","help-use","DISPLAY");
        var port=f.bean(ChatHelpContent.class);var displayed=port.current(use,"USAGE_HELP","ko");
        assertThat(displayed.body()).isEqualTo(command.values().get("body"));assertThat(displayed.html()).doesNotContain("onclick");
        f.adminCall("chatHelp","save",command("USAGE_HELP","2","TEXT","새 안내"),200);
        assertThat(port.exact(use,"USAGE_HELP","2",displayed.policyVersion(),"ko").body()).isEqualTo(displayed.body());
        assertThatThrownBy(()->port.exact(use,"USAGE_HELP","3",displayed.policyVersion(),"ko"));
        assertThatThrownBy(()->port.exact(use,"USAGE_HELP","2","unknown","ko"));
        assertThatThrownBy(()->port.current(new ChatHelpContent.Use("unknown","unknown","x","DISPLAY"),"USAGE_HELP","ko"));
        f.adminCall("chatHelp","save",command("USAGE_HELP","2","TEXT","낡은 안내"),409);
        var unchanged=f.adminCall("chatHelp","save",command("USAGE_HELP","3","TEXT","새 안내"),200);
        assertThat(unchanged.path("outcome").asText()).isEqualTo("NO_CHANGE");
        assertThat(f.adminCall("chatHelp","getOne",Map.of("helpCd","INPUT_NOTICE"),200).path("revision").asText()).isEqualTo("1");
    }
    @Test @Order(2) @DisplayName("TC-0107 TC-0103 잘못된 구분/빈 표시/이력 실패의 무효과") void invalidCodesEmptyDisplayAndHistoryFailureHaveNoEffect() throws Exception {
        f.adminCall("chatHelp","save",command("OTHER","1","TEXT","본문"),400);
        f.adminCall("chatHelp","save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"content",Map.of("formatCd","TEXT","body","본문"),"사유"),400);
        f.adminCall("chatHelp","save",command("INPUT_NOTICE","1","HTML","<script>x()</script><br>"),400);
        f.adminCall("chatHelp","save",command("INPUT_NOTICE","1","BAD","본문"),400);
        var failed=command("INPUT_NOTICE","1","TEXT","FAIL_HISTORY");
        f.database().execute("ALTER TABLE ai_admin_chat_help_hist ADD CONSTRAINT help_fail CHECK (body<>'FAIL_HISTORY')");
        try{f.adminCall("chatHelp","save",failed,503);}finally{f.database().execute("ALTER TABLE ai_admin_chat_help_hist DROP CONSTRAINT help_fail");}
        assertThat(f.adminCall("chatHelp","getOne",Map.of("helpCd","INPUT_NOTICE"),200).path("body").asText()).isEmpty();
        assertThat(f.bean(JdbcManagedJournal.class).find(failed.commandKey())).isEmpty();
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_chat_help_hist WHERE help_cd='INPUT_NOTICE'",Long.class)).isZero();
    }
    @Test @Order(3) @DisplayName("TC-0105 조회 전용 미리보기와 권한 회수") void previewIsReadOnlyAndPermissionsAreEnforced() throws Exception {
        f.database().update("INSERT INTO sys_role_menu_auth(role_id,menu_cd,action_cd) VALUES ('READER040','HUB_CHAT_HELP','READ')");
        var reader=f.login("reader040");
        var preview=reader.call("POST","/api/admin/service/chatHelp/getPreview",Map.of("formatCd","HTML","body","<img src='https://example.com'><b>표시</b>"));
        assertThat(preview.statusCode()).isEqualTo(200);assertThat(preview.body()).contains(SafeContentRenderer.POLICY_VERSION).doesNotContain("<img");
        assertThat(reader.call("POST","/api/admin/service/chatHelp/save",command("CHAT_START","1","TEXT","저장" )).statusCode()).isEqualTo(403);
        assertThat(f.new Browser().call("POST","/api/admin/service/chatHelp/getList",Map.of()).statusCode()).isEqualTo(401);
        f.database().update("DELETE FROM sys_role_menu_auth WHERE role_id='READER040' AND menu_cd='HUB_CHAT_HELP'");
        f.database().update("UPDATE sys_cache_version SET version=version+1 WHERE cache_name='authz'");
        assertThat(reader.call("POST","/api/admin/service/chatHelp/getOne",Map.of("helpCd","CHAT_START")).statusCode()).isEqualTo(403);
    }
    @Test @Order(4) @DisplayName("TC-0108 신규 DB 변경 롤백·재적용") void migrationRollbackAndReapply() throws Exception {
        try(var connection=f.bean(javax.sql.DataSource.class).getConnection();
            var migration=new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
            int count=f.database().queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE orderexecuted >= (SELECT orderexecuted FROM databasechangelog WHERE id='admin-012-chat-help')",Integer.class);
            migration.rollback(count,"");
            try(var row=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_chat_help')")){row.next();assertThat(row.getString(1)).isNull();}
            try(var row=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_cd='HUB_CHAT_HELP'")){row.next();assertThat(row.getInt(1)).isZero();}
            migration.update(new liquibase.Contexts());
            try(var row=connection.createStatement().executeQuery("SELECT COUNT(*) FROM ai_admin_chat_help WHERE body='' AND revision=1")){row.next();assertThat(row.getInt(1)).isEqualTo(3);}
            try(var row=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu_api WHERE menu_cd='HUB_CHAT_HELP'")){row.next();assertThat(row.getInt(1)).isEqualTo(5);}
            try(var row=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_message WHERE msg_key='chat_help.kind'")){row.next();assertThat(row.getInt(1)).isEqualTo(2);}
        }
    }
}
