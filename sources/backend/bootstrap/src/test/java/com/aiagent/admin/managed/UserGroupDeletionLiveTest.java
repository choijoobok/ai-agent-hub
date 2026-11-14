// @feat FEAT-006
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** 소유 포트 대역 없이 실제 제품 조립과 DB에서 삭제 가능 여부를 검증한다. */
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserGroupDeletionLiveTest {
    private ManagedLiveFixture f;
    @BeforeAll void start() throws Exception {f=new ManagedLiveFixture();}
    @AfterAll void stop(){if(f!=null)f.close();}
    private String create() throws Exception {
        return f.groupCall("save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",
                Map.of("groupNm","삭제 검토","description","참조 없는 그룹"),"시험 등록"),200).path("targetId").asText();
    }
    private ManagedCommand remove(String id) {
        return new ManagedCommand(UUID.randomUUID().toString(),id,"1","definition",Map.of(),"참조 없는 그룹 정리");
    }
    @Test @DisplayName("TC-00611 실제 미참조 그룹 삭제·현재 목록·이력 보존")
    void unreferencedGroupCanBeDeletedWithoutProjectionProviders() throws Exception {
        String id=create();
        assertThat(f.groupCall("getMembers",Map.of("targetId",id),200).path("state").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(f.groupCall("getOne",Map.of("targetId",id),200).path("useState").asText()).isEqualTo("UNUSED");
        var page=Map.of("conditions",List.of(),"sorts",List.of(),"page",Map.of("size",20,"page",0));
        var rows=f.groupCall("getList",page,200).path("content");
        for(var row:rows)assertThat(row.path("useState").asText()).isEqualTo("UNKNOWN");
        var command=remove(id);var result=f.groupCall("remove",command,200);
        assertThat(f.groupCall("remove",command,200)).isEqualTo(result);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_group WHERE group_id=?",Integer.class,Long.parseLong(id))).isZero();
        assertThat(f.database().queryForList("SELECT hist_action FROM ai_admin_user_group_hist WHERE group_id=? ORDER BY result_revision",String.class,Long.parseLong(id))).containsExactly("CREATE","DELETE");
    }
    @Test @DisplayName("TC-00611 검사 기능 없는 실제 비활성 연결 자동 삭제 금지")
    void existingStorageWithoutInspectorBlocksDeletionAndPreservesLinks() throws Exception {
        String id=create();
        f.database().execute("CREATE TABLE ai_admin_user_membership (group_id bigint REFERENCES ai_admin_user_group(group_id) ON DELETE CASCADE, active_yn character(1))");
        try {
            f.database().update("INSERT INTO ai_admin_user_membership VALUES (?,'N')",Long.parseLong(id));
            assertThat(f.groupCall("getOne",Map.of("targetId",id),200).path("useState").asText()).isEqualTo("UNKNOWN");
            f.groupCall("remove",remove(id),503);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_membership WHERE group_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_user_group_hist WHERE group_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        } finally {f.database().execute("DROP TABLE ai_admin_user_membership");}
    }
}
