// @feat FEAT-006
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.group.*;
import java.util.*;
import javax.sql.DataSource;
import liquibase.*;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;

/** 제품 JPA·HTTP·인증·전체 HIST·SQL을 한 부팅에서 검증한다. 실제 원장 미연결은 별도 검증 경계다. */
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserGroupLiveTest {
    private ManagedLiveFixture f;
    @BeforeAll void start() throws Exception {f=new ManagedLiveFixture(Ports.class);}
    @AfterAll void stop(){if(f!=null)f.close();}
    private ManagedCommand command(String id,String revision,String name) {
        return new ManagedCommand(UUID.randomUUID().toString(),id,revision,"definition",Map.of("groupNm",name,"description","업무 담당"),"담당 범위 정비");
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class Ports {
        static boolean unreferenced;
        @Bean @Primary UserGroupLinks connectedTestLinks() {
            return new UserGroupLinks(Arrays.stream(UserGroupLinkSource.Source.values()).map(source->new UserGroupLinkSource() {
                public Source source(){return source;}
                public Projection read(long id){return new Projection(State.NOT_CONNECTED,List.of());}
                public Reference references(long id){return unreferenced?Reference.ABSENT:Reference.UNKNOWN;}
            }).map(UserGroupLinkSource.class::cast).toList());
        }
    }
    @Test @DisplayName("TC-00611 그룹 정의·이력·연결 경계") @Order(1) void currentHistoryReplayStaleAndReadOnlyPermission() throws Exception {
        var c=command(null,null,"물류");var result=f.groupCall("save",c,200);
        String id=result.path("targetId").asText();
        assertThat(id).matches("[1-9][0-9]*");assertThat(result.path("revision").asText()).isEqualTo("1");
        assertThat(f.groupCall("save",c,200)).isEqualTo(result);
        assertThat(f.result(c.commandKey()).path("result")).isEqualTo(result);
        var one=f.groupCall("getOne",Map.of("targetId",id),200);
        assertThat(one.path("useState").asText()).isEqualTo("UNKNOWN");
        f.groupReaderCannotSave(command(id,"1","금지"));
        var next=f.groupCall("save",command(id,"1","수정"),200);
        assertThat(next.path("revision").asText()).isEqualTo("2");
        f.groupCall("save",command(id,"1","오래된 화면"),409);
        var unchanged=f.groupCall("save",command(id,"2","수정"),200);
        assertThat(unchanged.path("outcome").asText()).isEqualTo("NO_CHANGE");
        var history=f.database().queryForList("SELECT * FROM ai_admin_user_group_hist WHERE group_id=? ORDER BY result_revision",Long.parseLong(id));
        assertThat(history).hasSize(2);assertThat(history.get(0).get("group_nm")).isEqualTo("물류");
        assertThat(history.get(1).get("reg_dtm")).isEqualTo(history.get(0).get("reg_dtm"));
        assertThat(history.get(1).get("hist_actor_id")).isEqualTo("admin");assertThat(history.get(1).get("hist_summary")).isEqualTo("CHANGE");
        var page=Map.of("conditions",List.of(Map.of("field","groupNm","op","contains","value","수정")),"sorts",List.of(),"page",Map.of("size",20,"page",0));
        assertThat(f.groupCall("getList",page,200).path("content").get(0).path("groupId").asText()).isEqualTo(id);
    }
    @Test @DisplayName("TC-00611 그룹 정의·이력·연결 경계") @Order(2) void unconnectedIsNotEmptyOrUnusedAndDeleteFailsClosed() throws Exception {
        var created=f.groupCall("save",command(null,null,"연결 경계"),200);String id=created.path("targetId").asText();
        assertThat(f.groupCall("getMembers",Map.of("targetId",id),200).path("state").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(f.groupCall("getAssets",Map.of("targetId",id,"scope","knowledge"),200).path("state").asText()).isEqualTo("NOT_CONNECTED");
        var remove=new ManagedCommand(UUID.randomUUID().toString(),id,"1","definition",Map.of(),"삭제");
        f.groupCall("remove",remove,503);
        assertThat(f.groupCall("getOne",Map.of("targetId",id),200).path("revision").asText()).isEqualTo("1");
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_group_hist WHERE group_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        assertThat(f.result(remove.commandKey()).path("status").asText()).isEqualTo("UNKNOWN");
    }
    @Test @DisplayName("TC-00611 그룹 정의·이력·연결 경계") @Order(3) void historyFailureRollsBackAndOtherIndependentRowCanSucceed() throws Exception {
        var c=command(null,null,"실패 행");long before=f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_group",Long.class);
        f.database().execute("ALTER TABLE ai_admin_user_group_hist ADD CONSTRAINT group006_failure CHECK (history_command_key <> '"+c.commandKey()+"')");
        try {f.groupCall("save",c,503);assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_group",Long.class)).isEqualTo(before);}
        finally {f.database().execute("ALTER TABLE ai_admin_user_group_hist DROP CONSTRAINT group006_failure");}
        f.groupCall("save",command(null,null,"독립 성공"),200);f.groupCall("save",c,200);
        var forged=new HashMap<String,Object>(Map.of("groupNm","위조","description","시험","members",List.of()));
        f.groupCall("save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",forged,"위조"),400);
    }
    @Test @DisplayName("TC-00611 그룹 정의·이력·연결 경계") @Order(4) void deletionPreservesBeforeSnapshotAndLinkPortRejectsDeletedGroup() throws Exception {
        var saved=f.groupCall("save",command(null,null,"참조 검사 완료"),200);String id=saved.path("targetId").asText();
        Ports.unreferenced=true;
        try {
            var deleted=f.groupCall("remove",new ManagedCommand(UUID.randomUUID().toString(),id,"1","definition",Map.of(),"참조 없음 확인"),200);
            assertThat(deleted.path("revision").asText()).isEqualTo("2");
            var history=f.database().queryForMap("SELECT * FROM ai_admin_user_group_hist WHERE history_no=?",deleted.path("historyNo").asLong());
            assertThat(history.get("revision")).isEqualTo(1L);assertThat(history.get("hist_action")).isEqualTo("DELETE");
            var tx=new org.springframework.transaction.support.TransactionTemplate(f.bean(org.springframework.transaction.PlatformTransactionManager.class));
            assertThatThrownBy(()->tx.executeWithoutResult(status->f.bean(UserGroupService.class).requireExisting(List.of(id))));
        } finally {Ports.unreferenced=false;}
    }
    @Test @DisplayName("TC-00611 그룹 정의·이력·연결 경계") @Order(5) void groupMigrationRollbackAndReapply() throws Exception {
        try(var connection=f.bean(DataSource.class).getConnection();var migration=new Liquibase("db/changelog/db.changelog-master.yaml",new ClassLoaderResourceAccessor(),new JdbcConnection(connection))) {
            migration.rollback(3,"");
            try(var r=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_user_group')")){r.next();assertThat(r.getString(1)).isNull();}
            migration.update(new Contexts());
            try(var r=connection.createStatement().executeQuery("SELECT string_agg(menu_cd, ',' ORDER BY sort_no,menu_cd) FROM sys_menu WHERE up_menu_cd IS NULL AND menu_cd IN ('DASHBOARD','SYSTEM','HUB')")){r.next();assertThat(r.getString(1)).isEqualTo("DASHBOARD,SYSTEM,HUB");}
            try(var r=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu_api WHERE menu_cd='HUB_USER_GROUP'")){r.next();assertThat(r.getInt(1)).isEqualTo(7);}
        }
    }
}
