// @feat FEAT-042
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.quota.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import liquibase.*;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuotaGradeLiveTest {
    private ManagedLiveFixture f;
    private String start;
    @BeforeAll void start() throws Exception {f=new ManagedLiveFixture();start=YearMonth.from(f.bean(QuotaGradeGuard.class).now().atZone(ZoneId.of("Asia/Seoul"))).plusMonths(2).toString();}
    @AfterAll void stop(){if(f!=null)f.close();}
    private Map<String,Object> grade(String id,String name,int rank,String weight) {
        var value=new HashMap<String,Object>(Map.of("gradeNm",name,"rankNo",rank,"weight",weight,"description",""));
        if(id==null)value.put("clientKey",name);else value.put("gradeId",id);return value;
    }
    private ManagedCommand command(String id,String revision,String month,List<Map<String,Object>> grades) {
        return new ManagedCommand(UUID.randomUUID().toString(),id,revision,"definition",Map.of("startMonth",month,"memo","검토 기준","grades",grades,"transitionMappings",List.of()),"등급 기준 정비");
    }
    private JsonNode call(String action,Object body,int status) throws Exception {return f.adminCall("quotaGrade",action,body,status);}
    @Test @Order(1) @DisplayName("TC-0421 TC-0172 전체 기준 원자 저장·효력 상속·안정 ID")
    void saveCopyReplayHistoryRevisionAndPermission() throws Exception {
        var cmd=command(null,null,start,List.of(grade(null,"일반",1,"1"),grade(null,"고급",2,"3")));
        var saved=call("save",cmd,200);String id=saved.path("targetId").asText();assertThat(call("save",cmd,200)).isEqualTo(saved);
        var one=call("getOne",Map.of("targetId",id),200);assertThat(one.path("referenceState").asText()).isEqualTo("READY");
        String first=one.path("grades").get(0).path("gradeId").asText(),second=one.path("grades").get(1).path("gradeId").asText();
        var rows=List.of(grade(first,"일반",1,"1"),grade(second,"고급",2,"3"));
        var search=Map.of("conditions",List.of(Map.of("field","gradeNm","op","eq","value","고급")),"page",Map.of("page",0,"size",20));
        assertThat(call("getList",search,200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(call("getList",Map.of("conditions",List.of(Map.of("field","gradeNm","op","eq","value","미등록")),"page",Map.of("page",0,"size",20)),200).path("content").size()).isZero();
        f.quotaReaderCannotSave(command(id,"1",start,rows));
        assertThat(call("save",command(id,"1",start,rows),200).path("outcome").asText()).isEqualTo("NO_CHANGE");
        var later=YearMonth.parse(start).plusMonths(3).toString();call("save",command(null,null,later,rows),200);
        assertThat(f.bean(QuotaGradeQueryService.class).effective(YearMonth.parse(start).minusMonths(1).toString())).isNull();
        assertThat(f.bean(QuotaGradeQueryService.class).effective(YearMonth.parse(start).plusMonths(2).toString()).basis().gradeSetId()).isEqualTo(id);
        assertThat(f.bean(QuotaGradeQueryService.class).effective(later).grades().getFirst().gradeId()).isEqualTo(first);
        call("save",command(id,"1",start,List.of(grade(first,"표준",1,"1"),grade(second,"고급",2,"5"))),200);
        call("save",command(id,"1",start,rows),409);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_set_hist WHERE grade_set_id=?",Integer.class,Long.parseLong(id))).isEqualTo(2);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_item_hist WHERE grade_set_id=?",Integer.class,Long.parseLong(id))).isEqualTo(4);
        assertThat(f.database().queryForObject("SELECT COUNT(DISTINCT reg_dtm) FROM ai_admin_quota_grade_set_hist WHERE grade_set_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        call("save",command(null,null,start,rows),409);
        call("save",command(null,null,YearMonth.parse(start).plusMonths(1).toString(),List.of(grade("999999","가장",1,"1"))),409);
    }
    @Test @Order(2) @DisplayName("TC-0421 자식 HIST 실패 전체 롤백")
    void childHistoryFailureLeavesNoCurrentIdentityOrResult() throws Exception {
        String month=YearMonth.parse(start).plusMonths(1).toString();var cmd=command(null,null,month,List.of(grade(null,"실패",1,"1")));
        long count=f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade",Long.class);
        f.database().execute("ALTER TABLE ai_admin_quota_grade_item_hist ADD CONSTRAINT quota042_failure CHECK (grade_nm<>'실패')");
        try {call("save",cmd,503);assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_set WHERE start_month=?",Integer.class,month)).isZero();assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade",Long.class)).isEqualTo(count);assertThat(f.result(cmd.commandKey()).path("status").asText()).isEqualTo("UNKNOWN");}
        finally {f.database().execute("ALTER TABLE ai_admin_quota_grade_item_hist DROP CONSTRAINT quota042_failure");}
        call("save",cmd,200);
    }
    @Test @Order(3) @DisplayName("TC-0173 미설치와 검사 누락 구분·삭제 이력·현재월 잠금")
    void deletionPreservesIdentityAndHistoryAndBlocksUnknownOrCurrentMonth() throws Exception {
        String month=YearMonth.parse(start).plusMonths(2).toString();var saved=call("save",command(null,null,month,List.of(grade(null,"삭제",1,"1"))),200);String id=saved.path("targetId").asText();
        var remove=new ManagedCommand(UUID.randomUUID().toString(),id,"1","definition",Map.of(),"미래 기준 취소");
        f.database().execute("CREATE TABLE ai_admin_quota_plan (plan_id bigint)");
        try {call("remove",remove,503);assertThat(call("getOne",Map.of("targetId",id),200).path("referenceState").asText()).isEqualTo("UNAVAILABLE");}
        finally {f.database().execute("DROP TABLE ai_admin_quota_plan");}
        var deleted=call("remove",remove,200);assertThat(call("remove",remove,200)).isEqualTo(deleted);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_set WHERE grade_set_id=?",Integer.class,Long.parseLong(id))).isZero();
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_item_hist WHERE grade_set_id=?",Integer.class,Long.parseLong(id))).isEqualTo(2);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade WHERE grade_id IN (SELECT grade_id FROM ai_admin_quota_grade_item_hist WHERE grade_set_id=?)",Integer.class,Long.parseLong(id))).isEqualTo(1);
        String current=YearMonth.parse(start).minusMonths(2).toString();call("save",command(null,null,current,List.of(grade(null,"당월 금지",1,"1"))),409);
        var guard=f.bean(QuotaGradeGuard.class);Instant boundary=YearMonth.parse(start).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(guard.future(start,boundary.minusNanos(1))).isTrue();assertThat(guard.future(start,boundary)).isFalse();
    }
    @Test @Order(4) @DisplayName("TC-0421 등급 기준 신규 마이그레이션 왕복")
    void migrationRollbackAndReapply() throws Exception {
        try(var connection=f.bean(DataSource.class).getConnection();var migration=new Liquibase("db/changelog/db.changelog-master.yaml",new ClassLoaderResourceAccessor(),new JdbcConnection(connection))) {
            int count=f.database().queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE orderexecuted >= (SELECT orderexecuted FROM databasechangelog WHERE id='admin-007-quota-grade')",Integer.class);
            migration.rollback(count,"");
            try(var r=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_quota_grade_set')")){r.next();assertThat(r.getString(1)).isNull();}
            migration.update(new Contexts());
            try(var r=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu_api WHERE menu_cd='HUB_QUOTA_GRADE'")){r.next();assertThat(r.getInt(1)).isEqualTo(5);}
        }
    }
}
