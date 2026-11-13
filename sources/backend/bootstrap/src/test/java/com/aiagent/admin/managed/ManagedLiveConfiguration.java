// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@TestConfiguration(proxyBeanMethods=false)
public class ManagedLiveConfiguration {
    static final String SAVE="/api/admin/service/fixture/save", REMOVE="/api/admin/service/fixture/remove", READ="/api/admin/service/fixture/getOne";
    @Bean FixtureOwner fixtureOwner(DataSource source,ManagedIds ids,jakarta.persistence.EntityManagerFactory ready) { return new FixtureOwner(source,ids); }
    @Bean ManagedRegistry.Operation fixtureSave(FixtureOwner owner,JdbcManagedJournal journal) { return new ManagedRegistry.Operation("API-990",SAVE,FixtureController.class.getName()+"#save",false,owner,journal); }
    @Bean ManagedRegistry.Operation fixtureRemove(FixtureOwner owner,JdbcManagedJournal journal) { return new ManagedRegistry.Operation("API-991",REMOVE,FixtureController.class.getName()+"#remove",true,owner,journal); }
    @Bean InstalledAdminRoutes fixtureRead() { return new InstalledAdminRoutes(List.of(new InstalledAdminRoutes.Route("API-992","POST",READ,FixtureController.class.getName()+"#getOne","READ"))); }
    @Bean FixtureJournal fixtureJournal(DataSource source) { return new FixtureJournal(source); }
    @Bean ManagedHistoryService.Consumer fixtureConsumer(DataSource source) {
        var jdbc=new JdbcTemplate(source);
        return new ManagedHistoryService.Consumer() {
            public String module(){return "agent";} public String kind(){return "fixture";}
            private boolean ended(String key) {
                var rows=jdbc.queryForList("SELECT ended FROM fx_consumer WHERE consumer_key=? FOR UPDATE",Boolean.class,key);
                if(rows.size()!=1)throw ManagedErrorCode.FORBIDDEN.failure();return rows.getFirst();
            }
            public void requireActive(String key,String purpose){if(ended(key))throw ManagedErrorCode.FORBIDDEN.failure();}
            public void requireReadable(String key,String purpose){ended(key);}
            public void requireEnded(String key,String purpose){if(!ended(key))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();}
        };
    }

    @RestController @Profile("managed-test")
    public static class FixtureController {
        private final ManagedCommandService service;
        public FixtureController(ManagedCommandService service){this.service=service;}
        @PostMapping(SAVE) @Operation(summary="시험 부모 전체 저장")
        public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand command){return ApiResponse.success(service.save("API-990",command),TraceId.current());}
        @PostMapping(REMOVE) @Operation(summary="시험 부모 삭제")
        public ApiResponse<ManagedResult> remove(@RequestBody ManagedCommand command){return ApiResponse.success(service.save("API-991",command),TraceId.current());}
        @PostMapping(READ) @Operation(summary="시험 현재 조회 권한")
        public ApiResponse<String> getOne(){return ApiResponse.success("fixture",TraceId.current());}
    }
    static final class FixtureJournal implements ManagedJournal {
        final AtomicBoolean unavailable=new AtomicBoolean(); private final JdbcTemplate jdbc;
        FixtureJournal(DataSource source){jdbc=new JdbcTemplate(source);}
        public DataSource dataSource(){return jdbc.getDataSource();}
        public String name(){return "fixture-other-owner";}
        public Optional<Entry> find(String key){
            if(unavailable.get())throw new DataAccessResourceFailureException("시험 원장 장애");
            return jdbc.query("SELECT command_key FROM fx_other_command WHERE command_key=?",(r,i)->new Entry(r.getString(1),"FEAT-001","admin","a".repeat(64),"managed/v1","OTHER","1","1",null,"NO_CHANGE",null,java.time.Instant.EPOCH),key).stream().findFirst();
        }
        public void insert(Entry entry){jdbc.update("INSERT INTO fx_other_command VALUES (?)",entry.commandKey());}
        public void delete(String key){jdbc.update("DELETE FROM fx_other_command WHERE command_key=?",key);}
        public boolean references(long number){return false;}
    }
    static final class FixtureOwner implements ManagedAssetAdapter {
        public DataSource dataSource(){return jdbc.getDataSource();}
        final JdbcTemplate jdbc; final ManagedIds ids; final TypedHistoryStore typed;
        final AtomicBoolean failHistory=new AtomicBoolean(); final AtomicReference<Runnable> beforeApply=new AtomicReference<>();
        FixtureOwner(DataSource source,ManagedIds ids) {
            this.jdbc=new JdbcTemplate(source);this.ids=ids;
            jdbc.execute("CREATE TABLE fx_asset(asset_id bigint PRIMARY KEY,name varchar(100) NOT NULL,revision bigint NOT NULL,child_count integer NOT NULL,reg_dtm timestamptz NOT NULL,mod_dtm timestamptz NOT NULL,ref_target bigint,ref_revision bigint,secret varchar(100),observed integer)");
            jdbc.execute("CREATE TABLE fx_child(child_id bigint PRIMARY KEY,asset_id bigint NOT NULL REFERENCES fx_asset(asset_id),name varchar(100) NOT NULL,reg_dtm timestamptz NOT NULL)");
            jdbc.execute("CREATE TABLE fx_asset_hist(asset_id bigint NOT NULL,name varchar(100) NOT NULL,revision bigint NOT NULL,child_count integer NOT NULL,reg_dtm timestamptz NOT NULL,mod_dtm timestamptz NOT NULL,ref_target bigint,ref_revision bigint,hist_id bigint PRIMARY KEY,history_no bigint NOT NULL,hist_dtm timestamptz NOT NULL,hist_action varchar(20) NOT NULL,hist_actor_id varchar(50) NOT NULL,hist_source_cd varchar(20) NOT NULL,history_command_key char(36) NOT NULL,result_revision bigint NOT NULL,before_history_no bigint,hist_reason varchar(2000) NOT NULL,hist_summary text,schema_version integer NOT NULL,UNIQUE(asset_id,result_revision),UNIQUE(history_no,asset_id))");
            jdbc.execute("CREATE TABLE fx_child_hist(child_id bigint NOT NULL,asset_id bigint NOT NULL,name varchar(100) NOT NULL,reg_dtm timestamptz NOT NULL,hist_id bigint PRIMARY KEY,history_no bigint NOT NULL,UNIQUE(history_no,child_id),FOREIGN KEY(history_no,asset_id) REFERENCES fx_asset_hist(history_no,asset_id))");
            jdbc.execute("CREATE TABLE fx_reference(asset_id bigint PRIMARY KEY REFERENCES fx_asset(asset_id))");
            jdbc.execute("CREATE TABLE fx_consumer(consumer_key varchar(128) PRIMARY KEY,ended boolean NOT NULL)");
            jdbc.execute("CREATE TABLE fx_other_command(command_key char(36) PRIMARY KEY)");
            jdbc.update("INSERT INTO SYS_SEQUENCE VALUES ('AI_ADMIN_FIXTURE_ROW',9007199254740993),('AI_ADMIN_FIXTURE_CHILD',1)");
            for(var path:List.of(SAVE,REMOVE,READ)) {
                jdbc.update("INSERT INTO SYS_API(API_URL,API_NM,API_DESC,ACTION_CD) VALUES (?,'관리 저장 시험','실제 인증 연결용 시험 경로',?)",path,path.equals(READ)?"READ":"SAVE");
                jdbc.update("INSERT INTO SYS_MENU_API(MENU_CD,API_URL) VALUES ('USER_MGMT',?)",path);
            }
            typed=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(
                new TypedHistoryStore.Table("FX_ASSET","ASSET_ID",List.of("ASSET_ID","NAME","REVISION","CHILD_COUNT","REG_DTM","MOD_DTM","REF_TARGET","REF_REVISION")),
                new TypedHistoryStore.Table("FX_CHILD","ASSET_ID",List.of("CHILD_ID","ASSET_ID","NAME","REG_DTM"))),
                rows->rows.get("FX_ASSET").size()==1&&((Number)rows.get("FX_ASSET").getFirst().get("CHILD_COUNT")).intValue()==rows.get("FX_CHILD").size(),
                rows->{var root=rows.get("FX_ASSET").getFirst();return root.get("REF_TARGET")==null?List.of():List.of(new ManagedHistoryStore.Ref(kind(),root.get("REF_TARGET").toString(),root.get("REF_REVISION").toString()));});
        }
        public String feature(){return "FEAT-006";} public String kind(){return "FIXTURE";}
        public String readPath(){return READ;}public String writePath(){return SAVE;}
        public Set<String> scopes(){return Set.of("main");}
        public Map<String,Object> normalize(ManagedCommand c) {
            if(!Set.of("name","children","reference").containsAll(c.values().keySet())||!(c.values().get("name") instanceof String name)||name.isBlank()||name.length()>100
                    ||!(c.values().get("children") instanceof List<?> children)||children.stream().anyMatch(v->!(v instanceof String s)||s.isBlank()||s.length()>100))throw ManagedErrorCode.VALIDATION.failure();
            if(c.values().containsKey("reference")) {
                if(!(c.values().get("reference") instanceof Map<?,?> ref)||!ref.keySet().equals(Set.of("id","revision")))throw ManagedErrorCode.VALIDATION.failure();
                ManagedCommand.positive((String)ref.get("id"));ManagedCommand.positive((String)ref.get("revision"));
            }
            return c.values();
        }
        public List<ManagedLocks.Key> locks(ManagedCommand c) {
            var keys=new ArrayList<ManagedLocks.Key>();
            if(c.values().get("reference") instanceof Map<?,?> ref)keys.add(new ManagedLocks.Key(3,kind(),ref.get("id").toString()));
            return keys;
        }
        public Current current(ManagedCommand c) {
            if(c.targetId()==null)return null;
            return jdbc.query("SELECT asset_id,revision FROM fx_asset WHERE asset_id=?",(r,i)->new Current(r.getString(1),r.getString(2)),Long.parseLong(c.targetId())).stream().findFirst().orElse(null);
        }
        public void validate(ManagedCommand c,Current current) {
            if(c.values().get("reference") instanceof Map<?,?> ref)typed.exact(new ManagedHistoryStore.Ref(kind(),ref.get("id").toString(),ref.get("revision").toString()));
        }
        public boolean changed(ManagedCommand c,Current before) {
            if(before==null)return true;
            var row=jdbc.queryForMap("SELECT name,ref_target,ref_revision FROM fx_asset WHERE asset_id=?",Long.parseLong(before.targetId()));
            return !Objects.equals(row.get("name"),c.values().get("name"))||!jdbc.queryForList("SELECT name FROM fx_child WHERE asset_id=? ORDER BY child_id",String.class,Long.parseLong(before.targetId())).equals(c.values().get("children"))||c.values().containsKey("reference");
        }
        private void hook(){var action=beforeApply.getAndSet(null);if(action!=null)action.run();}
        public String apply(ManagedCommand c,Current before,Stamp stamp) {
            hook(); long id=before==null?ids.next("AI_ADMIN_FIXTURE_ROW"):Long.parseLong(before.targetId());
            var children=(List<?>)c.values().get("children"); Timestamp time=Timestamp.from(stamp.at());
            Map<?,?> ref=c.values().get("reference") instanceof Map<?,?> value?value:Map.of();
            Object target=ref.isEmpty()?null:Long.parseLong(ref.get("id").toString()),revision=ref.isEmpty()?null:Long.parseLong(ref.get("revision").toString());
            if(before==null)jdbc.update("INSERT INTO fx_asset VALUES (?,?,?,?,?,?,?,?,?,?)",id,c.values().get("name"),Long.parseLong(stamp.revision()),children.size(),time,time,target,revision,"제외할 비밀",71);
            else jdbc.update("UPDATE fx_asset SET name=?,revision=?,child_count=?,mod_dtm=?,ref_target=?,ref_revision=? WHERE asset_id=?",c.values().get("name"),Long.parseLong(stamp.revision()),children.size(),time,target,revision,id);
            jdbc.update("DELETE FROM fx_child WHERE asset_id=?",id);
            for(var child:children)jdbc.update("INSERT INTO fx_child VALUES (?,?,?,?)",ids.next("AI_ADMIN_FIXTURE_CHILD"),id,child,time);
            return Long.toString(id);
        }
        public void delete(ManagedCommand c,Current before) {
            hook(); long id=Long.parseLong(before.targetId());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM fx_reference WHERE asset_id=?",Long.class,id)!=0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            jdbc.update("DELETE FROM fx_child WHERE asset_id=?",id);jdbc.update("DELETE FROM fx_asset WHERE asset_id=?",id);
        }
        public ManagedHistoryStore history() {
            return new ManagedHistoryStore() {
                public DataSource dataSource(){return typed.dataSource();}
                public String kind(){return typed.kind();}
                public void capture(String id,Stamp stamp){typed.capture(id,stamp);if(failHistory.getAndSet(false))jdbc.execute("SELECT 1/0");}
                public Event exact(Ref ref){return typed.exact(ref);} public Event event(long no){return typed.event(no);}
                public boolean current(Event event){return typed.current(event);} public void purge(Event event){typed.purge(event);}
                public List<ManagedLocks.Key> locks(Ref ref){return typed.locks(ref);}
            };
        }
    }
}
