// @feat FEAT-040 FEAT-010
package com.aiagent.admin.managed;

import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@TestConfiguration(proxyBeanMethods=false)
public class ManagedNaturalKeyConfiguration {
    static final String BASE="/api/admin/service/fixedKey";
    @Bean FixedOwner fixedOwner(DataSource source,ManagedIds ids,jakarta.persistence.EntityManagerFactory ready){return new FixedOwner(source,ids);}
    @Bean ManagedRegistry.Operation fixedSave(FixedOwner owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-994",BASE+"/save",FixedController.class.getName()+"#save",false,owner,journal);}
    @Bean InstalledAdminRoutes fixedRead(){return new InstalledAdminRoutes(List.of(new InstalledAdminRoutes.Route("API-995","POST",BASE+"/getOne",FixedController.class.getName()+"#getOne","READ")));}
    @Bean FixedController fixedController(ManagedCommandService service){return new FixedController(service);}
    @RestController @Profile("fixed-key-test")
    static class FixedController {
        private final ManagedCommandService service;
        FixedController(ManagedCommandService service){this.service=service;}
        @PostMapping(BASE+"/save") @Operation(summary="고정 코드 시험 저장")
        public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand command){return ApiResponse.success(service.save("API-994",command),TraceId.current());}
        @PostMapping(BASE+"/getOne") @Operation(summary="고정 코드 시험 읽기")
        public ApiResponse<String> getOne(){return ApiResponse.success("시험",TraceId.current());}
    }
    static final class FixedOwner implements ManagedAssetAdapter {
        private final JdbcTemplate jdbc;
        private final TypedHistoryStore history;
        private final ManagedTargetKeys keys=ManagedTargetKeys.fixed(Set.of("USAGE_HELP","CHAT_START","INPUT_NOTICE"));
        FixedOwner(DataSource source,ManagedIds ids) {
            jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE fx_fixed(help_cd varchar(30) PRIMARY KEY,revision bigint NOT NULL,body text NOT NULL)");
        jdbc.execute("""
            CREATE TABLE fx_fixed_hist(help_cd varchar(30) NOT NULL,revision bigint NOT NULL,body text NOT NULL,
                hist_id bigint PRIMARY KEY,history_no bigint NOT NULL UNIQUE,hist_dtm timestamptz NOT NULL,
                hist_action varchar(20) NOT NULL,hist_actor_id varchar(50),hist_source_cd varchar(20) NOT NULL,
                history_command_key char(36) NOT NULL,result_revision bigint NOT NULL,before_history_no bigint,
                hist_reason text NOT NULL,hist_summary text NOT NULL,schema_version integer NOT NULL,
                UNIQUE(help_cd,result_revision))
            """);

            for(String action:List.of("save","getOne")) {
                jdbc.update("INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES (?,'고정 코드 시험','시험 전용',?)",BASE+"/"+action,action.equals("save")?"SAVE":"READ");
                jdbc.update("INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('USER_MGMT',?)",BASE+"/"+action);
            }
            history=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(new TypedHistoryStore.Table("FX_FIXED","HELP_CD",List.of("HELP_CD","REVISION","BODY"))),rows->true,rows->List.of(),keys);
        }
        public DataSource dataSource(){return jdbc.getDataSource();}
        public ManagedTargetKeys targetKeys(){return keys;}
        public String feature(){return "FEAT-010";} public String kind(){return "FIXED_HELP";}
        public String readPath(){return BASE+"/getOne";} public String writePath(){return BASE+"/save";}
        public Set<String> scopes(){return Set.of("content");}
        public Map<String,Object> normalize(ManagedCommand c){if(c.targetId()==null||!c.values().keySet().equals(Set.of("body"))||!(c.values().get("body") instanceof String))throw ManagedErrorCode.VALIDATION.failure();return c.values();}
        public List<ManagedLocks.Key> locks(ManagedCommand c){return List.of();}
        public Current current(ManagedCommand c){return jdbc.query("SELECT help_cd,revision FROM fx_fixed WHERE help_cd=?",(rs,n)->new Current(rs.getString(1),rs.getString(2)),keys.bind(c.targetId())).stream().findFirst().orElse(null);}
        public void validate(ManagedCommand c,Current before){}
        public boolean changed(ManagedCommand c,Current before){return !jdbc.queryForObject("SELECT body FROM fx_fixed WHERE help_cd=?",String.class,keys.bind(c.targetId())).equals(c.values().get("body"));}
        public String apply(ManagedCommand c,Current before,Stamp stamp){jdbc.update("UPDATE fx_fixed SET body=?,revision=? WHERE help_cd=?",c.values().get("body"),Long.parseLong(stamp.revision()),keys.bind(c.targetId()));return c.targetId();}
        public void delete(ManagedCommand c,Current before){throw ManagedErrorCode.VALIDATION.failure();}
        public ManagedHistoryStore history(){return history;}
    }
}
