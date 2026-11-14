// @feat FEAT-002
package com.aiagent.admin.managed;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.datasource.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.support.deployment.ExternalDataSources;
import com.aiagent.core.deployment.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import tools.jackson.databind.json.JsonMapper;
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataSourceLiveTest {
    @TestConfiguration @org.springframework.context.annotation.PropertySource(value="classpath:data-source-test.properties",encoding="UTF-8") static class Settings {
        static boolean blockGroups,blockReferences;
        @Bean DataSourceReferencePort fixtureDataSourceReferences(){return new DataSourceReferencePort(){
            public Set<String> tables(){return Set.of();}
            public Reference references(String id){return blockReferences?Reference.PRESENT:Reference.ABSENT;}
            public Reference groupReferences(String id,String group,boolean activeOnly){return blockGroups?Reference.PRESENT:Reference.ABSENT;}
        };}
    }
    private ManagedLiveFixture f;
    @BeforeAll void start()throws Exception{f=new ManagedLiveFixture(Settings.class);
        String month=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().substring(0,7);
        f.database().update("INSERT INTO AI_ADMIN_QUOTA_GRADE VALUES (900001,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO AI_ADMIN_QUOTA_GRADE_SET VALUES (900001,?,'시험 당월',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO AI_ADMIN_QUOTA_GRADE_ITEM VALUES (900001,900001,'기본',1,1,NULL,'')");
    }
    @AfterAll void close(){if(f!=null)f.close();}
    private Map<String,Object> values(){var v=new LinkedHashMap<String,Object>();v.put("dbmsVersion","17");v.put("sourceType","DW");v.put("syncMethodNote","매일 마감 반영");v.put("timeZoneId","Asia/Seoul");v.put("description","주문 조회 가이드 시험");v.put("dataBasis",null);v.put("applicableGroups",List.of());v.put("queryGuides",List.of(Map.of("clientKey","draft","title","코드 확인","description","허용 원천의 실제 값만 사용한다")));return v;}
    private String id(){return f.database().queryForObject("SELECT DATA_SOURCE_ID FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);}
    @Test @Order(1) @DisplayName("TC-0021 TC-0022 TC-00218 TC-00219 TC-00225 기동 전체 동기화·무쓰기 조회·메타와 기술 상태 분리")
    void synchronization()throws Exception{
        var deployment=f.bean(ConfigurationDeploymentService.class);var loaded=f.bean(ExternalDataSources.class);var sync=f.bean(DataSourceSyncService.class);
        assertThatThrownBy(()->sync.synchronize(loaded)).isInstanceOf(RuntimeException.class);
        var old=definition("이전 별칭",true);deployment.stage(old);deployment.allow(old.environment(),old.bootId(),0,UUID.randomUUID(),"fixture",false);sync.synchronize(old);
        deployment.allow(loaded.environment(),loaded.bootId(),1,UUID.randomUUID(),"fixture",false);sync.synchronize(loaded);sync.synchronize(loaded);
        var detail=f.adminCall("dataSource","getOne",Map.of("id",id()),200);
        assertThat(detail.path("row").path("managementVersion").asText()).isEqualTo("2");
        assertThat(detail.path("row").path("alias").asText()).isEqualTo("주문 조회");assertThat(detail.toString()).doesNotContain("jdbc:","username","password");
        long count=f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_DIAGNOSIS WHERE TARGET_KIND='DATASOURCE'",Long.class);
        var list=f.adminCall("dataSource","getList",Map.of("conditions",List.of(Map.of("field","configurationMismatch","op","eq","value",true)),"page",Map.of("page",0,"size",100)),200);
        assertThat(list.path("content")).hasSize(1);assertThat(list.path("content").get(0).path("dataSourceKey").asText()).isEqualTo("retired");
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_DIAGNOSIS WHERE TARGET_KIND='DATASOURCE'",Long.class)).isEqualTo(count);
        var alerts=f.adminCall("integration","getConfigurationAlerts",Map.of("params",Map.of("causeKind","DATA_SOURCE"),"page",Map.of("page",0,"size",100)),200);
        assertThat(alerts.path("page").path("content")).hasSize(1);assertThat(alerts.path("page").path("content").get(0).path("targetKey").asText()).isEqualTo("retired");
        assertThat(f.adminCall("dataSource","getTimezoneList",Map.of(),200).toString()).contains("Asia/Seoul");
        assertThatThrownBy(()->f.bean(DataSourceMetadata.class).readSelected(Set.of("1"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->sync.synchronize(old)).isInstanceOf(RuntimeException.class);
    }
    @Test @Order(2) @DisplayName("TC-0023 TC-0025 TC-0068 TC-0026 TC-0028 TC-00210 TC-0029 TC-00212 TC-00213 TC-00216 TC-00221 TC-00224 TC-00610 전체 저장·권한·충돌·원 결과·이력 실패 원자 취소")
    void wholeSave()throws Exception{
        var group=f.groupCall("save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",Map.of("groupNm","데이터소스 그룹","description","검증"),"시험 그룹"),200).path("targetId").asText();
        var v=values();v.put("applicableGroups",List.of(Map.of("groupId",group,"mappingActive",true)));
        var c=new ManagedCommand(UUID.randomUUID().toString(),id(),"2","main",v,"전체 보완");var result=f.adminCall("dataSource","save",c,200);
        assertThat(f.adminCall("dataSource","save",c,200)).isEqualTo(result);assertThat(f.adminCall("dataSource","getCommandResult",Map.of("commandKey",c.commandKey()),200).path("result")).isEqualTo(result);
        var detail=f.adminCall("dataSource","getOne",Map.of("id",id()),200);assertThat(detail.path("values").path("timeZoneId").asText()).isEqualTo("Asia/Seoul");assertThat(detail.path("values").path("queryGuides")).hasSize(1);
        assertThat(f.groupCall("getOne",Map.of("targetId",group),200).path("useState").asText()).isEqualTo("USED");
        assertThat(f.database().queryForObject("SELECT REVISION FROM AI_ADMIN_USER_GROUP WHERE GROUP_ID=?",Long.class,Long.valueOf(group))).isEqualTo(1);
        var hist=f.bean(DataSourceService.class).history().event(Long.parseLong(result.path("historyNo").asText()));assertThat(hist.rows().get("AI_ADMIN_QUERY_GUIDE")).hasSize(1);assertThat(hist.rows().get("AI_ADMIN_DATA_SOURCE_GROUP")).hasSize(1);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_QUERY_GUIDE_HIST WHERE HISTORY_NO=?",Long.class,Long.parseLong(result.path("historyNo").asText()))).isEqualTo(1);
        f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"2","main",v,"낡은 기준"),409);
        var invalid=values();invalid.put("alias","위조");f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",invalid,"기술값 금지"),400);
        invalid=values();invalid.put("timeZoneId","Asia/Seoull");f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",invalid,"시간대 금지"),400);
        f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"main",values(),"신규 금지"),400);
        var reader=f.login("reader040");var jdbc=f.database();jdbc.update("INSERT INTO SYS_ROLE_MENU_AUTH VALUES ('READER040','HUB_DATA_SOURCE','READ')");
        assertThat(reader.call("POST",DataSourceService.BASE+"/getTimezoneList",Map.of()).statusCode()).isEqualTo(200);assertThat(reader.call("POST",DataSourceService.BASE+"/save",c).statusCode()).isEqualTo(403);
        var profile=new LinkedHashMap<String,Object>();profile.put("loginId","ds-identity-fixture");profile.put("userNm","데이터소스 신원 시험");profile.put("organizationNm","시험");profile.put("useStartDt",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());profile.put("useEndDt",null);profile.put("activeYn","N");
        var portal=f.adminCall("portalUser","save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"profile",profile,"신원 연결 시험"),200);
        var identities=Map.<String,Object>of("identities",List.of(Map.of("targetId",id(),"externalUserId","fixture","mappingActiveYn","N","identityEvidence","비활성 신원 참조")));
        f.adminCall("portalUser","save",new ManagedCommand(UUID.randomUUID().toString(),portal.path("targetId").asText(),"1","identityDataSource",identities,"현재 DS 신원"),200);
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_IDENTITY WHERE TARGET_KIND='DATA_SOURCE' AND TARGET_ID=? AND MAPPING_ACTIVE_YN='N'",Long.class,Long.valueOf(id()))).isEqualTo(1);
        assertThat(f.bean(DataSourceReferenceService.class).references(id())).isEqualTo(DataSourceReferencePort.Reference.PRESENT);
        assertThatThrownBy(()->f.bean(DataSourceQueryService.class).requireRevision(id(),"2")).isInstanceOf(RuntimeException.class);f.bean(DataSourceQueryService.class).requireRevision(id(),"3");
        Settings.blockGroups=true;
        var off=values();off.put("applicableGroups",List.of(Map.of("groupId",group,"mappingActive",false)));f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",off,"활성 하위 보호"),409);
        var remove=values();f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",remove,"비활성 포함 참조 보호"),409);Settings.blockGroups=false;
        var json=JsonMapper.builder().build();@SuppressWarnings("unchecked") Map<String,Object> next=json.convertValue(detail.path("values"),Map.class);next.put("description","실패하면 남지 않는다");
        jdbc.execute("CREATE FUNCTION fx_ds_hist_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '시험 이력 실패'; END $$");jdbc.execute("CREATE TRIGGER fx_ds_hist_fail BEFORE INSERT ON AI_ADMIN_QUERY_GUIDE_HIST FOR EACH ROW EXECUTE FUNCTION fx_ds_hist_fail()");
        var bad=new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",next,"원자성 시험");f.adminCall("dataSource","save",bad,503);
        assertThat(jdbc.queryForObject("SELECT MANAGEMENT_VERSION FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",Long.class,Long.valueOf(id()))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_ASSET_COMMAND WHERE COMMAND_KEY=?",Long.class,bad.commandKey())).isZero();jdbc.execute("DROP TRIGGER fx_ds_hist_fail ON AI_ADMIN_QUERY_GUIDE_HIST");
        var noChange=f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",json.convertValue(detail.path("values"),Map.class),"무변경"),200);assertThat(noChange.path("outcome").asText()).isEqualTo("NO_CHANGE");
        // 미설치 소유 원장과 부분 설치/고장을 구분한다. 알 수 없는 하위 참조가 있으면 부모 축소 차단.
        jdbc.execute("CREATE TABLE fx_ds_reference(DATA_SOURCE_ID bigint)");
        var shrink=new LinkedHashMap<>(next);shrink.put("applicableGroups",List.of());f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),id(),"3","main",shrink,"미확인 축소"),503);jdbc.execute("DROP TABLE fx_ds_reference");
        var aliases=definition("바뀐 별칭",false);var deployment=f.bean(ConfigurationDeploymentService.class);deployment.stage(aliases);deployment.allow(aliases.environment(),aliases.bootId(),2,UUID.randomUUID(),"fixture",false);jdbc.execute("CREATE TRIGGER fx_ds_sync_fail BEFORE INSERT ON AI_ADMIN_DATA_SOURCE_HIST FOR EACH ROW EXECUTE FUNCTION fx_ds_hist_fail()");
        assertThatThrownBy(()->f.bean(DataSourceSyncService.class).synchronize(aliases)).isInstanceOf(RuntimeException.class);
        assertThat(deployment.ready(aliases)).isFalse();assertThat(jdbc.queryForObject("SELECT ALIAS_NM FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,Long.valueOf(id()))).isEqualTo("주문 조회");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_DIAGNOSIS WHERE CONFIG_ID=? AND TARGET_KIND='DATASOURCE'",Long.class,aliases.settingsId())).isZero();
        jdbc.execute("DROP TRIGGER fx_ds_sync_fail ON AI_ADMIN_DATA_SOURCE_HIST");f.bean(DataSourceSyncService.class).synchronize(aliases);
        assertThat(jdbc.queryForObject("SELECT TIME_ZONE_ID FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,Long.valueOf(id()))).isEqualTo("Asia/Seoul");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_QUERY_GUIDE WHERE DATA_SOURCE_ID=?",Long.class,Long.valueOf(id()))).isEqualTo(1);
        // 현재 서버와 구 기동의 설정을 섞지 않도록 원 기동을 명시 재허용한다.
        var loaded=f.bean(ExternalDataSources.class);deployment.allow(loaded.environment(),loaded.bootId(),3,UUID.randomUUID(),"fixture",false);
    }
    @Test @Order(3) @DisplayName("TC-0027 TC-00226 TC-00227 TC-00228 TC-00229 누락 전체 범위 정리·미확인/참조 차단·재수신·재등장 보호")
    void cleanup()throws Exception{
        var q=Map.of("scope","DATASOURCE","conditions",List.of());var jdbc=f.database();
        jdbc.execute("CREATE TABLE fx_ds_unknown(DATA_SOURCE_ID bigint)");var blocked=f.adminCall("dataSource","getConfigurationCleanupPreview",q,200);assertThat(blocked.path("executable").asBoolean()).isFalse();jdbc.execute("DROP TABLE fx_ds_unknown");
        Settings.blockReferences=true;assertThat(f.adminCall("dataSource","getConfigurationCleanupPreview",q,200).path("executable").asBoolean()).isFalse();Settings.blockReferences=false;
        var p=f.adminCall("dataSource","getConfigurationCleanupPreview",q,200);assertThat(p.path("targets")).hasSize(1);assertThat(p.path("executable").asBoolean()).isTrue();
        var json=JsonMapper.builder().build();var v=Map.<String,Object>of("previewToken",p.path("previewToken").asText(),"query",q,"confirmedTargets",List.of(json.convertValue(p.path("targets").get(0).path("target"),Map.class)));
        var c=new ManagedCommand(UUID.randomUUID().toString(),null,null,"cleanup",v,"확정 누락 정리");
        jdbc.execute("CREATE TABLE fx_ds_late(DATA_SOURCE_ID bigint)");f.adminCall("dataSource","deleteConfigurationMismatches",c,503);jdbc.execute("DROP TABLE fx_ds_late");
        Settings.blockReferences=true;f.adminCall("dataSource","deleteConfigurationMismatches",c,409);Settings.blockReferences=false;
        jdbc.execute("CREATE FUNCTION fx_ds_cleanup_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.owner_feature='FEAT-002' AND NEW.action_cd='CONFIGURATION_CLEANUP' THEN RAISE EXCEPTION '시험 정리 원장 실패'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fx_ds_cleanup_fail BEFORE INSERT ON AI_ADMIN_ASSET_COMMAND FOR EACH ROW EXECUTE FUNCTION fx_ds_cleanup_fail()");
        f.adminCall("dataSource","deleteConfigurationMismatches",c,503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='retired'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE_HIST WHERE HIST_ACTION='DELETE'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT STATUS_CD FROM AI_ADMIN_CONFIG_ALERT WHERE CONFIG_ID=? AND TARGET_KIND='DATASOURCE' AND TARGET_KEY='retired'",String.class,f.bean(ExternalDataSources.class).settingsId())).isEqualTo("OPEN");
        jdbc.execute("DROP TRIGGER fx_ds_cleanup_fail ON AI_ADMIN_ASSET_COMMAND");
        var result=f.adminCall("dataSource","deleteConfigurationMismatches",c,200);assertThat(f.adminCall("dataSource","deleteConfigurationMismatches",c,200)).isEqualTo(result);assertThat(f.adminCall("dataSource","getCommandResult",Map.of("commandKey",c.commandKey()),200).path("result")).isEqualTo(result);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='retired'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE_HIST WHERE HIST_ACTION='DELETE'",Long.class)).isEqualTo(1);
        var same=definition("이전 별칭",true);var deployment=f.bean(ConfigurationDeploymentService.class);deployment.stage(same);deployment.allow(same.environment(),same.bootId(),4,UUID.randomUUID(),"fixture",false);f.bean(DataSourceSyncService.class).synchronize(same);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='retired'",Long.class)).isZero();
        var reappeared=definition("새 배포 별칭",true);deployment.stage(reappeared);deployment.allow(reappeared.environment(),reappeared.bootId(),5,UUID.randomUUID(),"fixture",false);f.bean(DataSourceSyncService.class).synchronize(reappeared);
        String newId=jdbc.queryForObject("SELECT DATA_SOURCE_ID FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='retired'",String.class);
        assertThat(newId).isNotEqualTo(p.path("targets").get(0).path("target").path("id").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE_GROUP WHERE DATA_SOURCE_ID=?",Long.class,Long.valueOf(newId))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE_HIST WHERE HIST_ACTION='DELETE'",Long.class)).isEqualTo(1);
    }
    @Test @Order(4) void migrationRoundTrip()throws Exception{
        String schema="migration002_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=java.sql.DriverManager.getConnection(System.getenv("MANAGED_TEST_JDBC"),System.getenv("MANAGED_TEST_USER"),System.getenv().getOrDefault("MANAGED_TEST_PASSWORD",""))){
            connection.createStatement().execute("CREATE SCHEMA "+schema);connection.setSchema(schema);
            try(var migration=new liquibase.Liquibase("db/datasource002-migration.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))){
                migration.update(new liquibase.Contexts());migration.rollback(3,"");
                try(var rows=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_data_source') IS NULL")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}
                migration.update(new liquibase.Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT data_type FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='ai_admin_data_source' AND column_name='time_zone_id'")){rows.next();assertThat(rows.getString(1)).isEqualTo("character varying");}
            }
        }
    }
    private ExternalDataSources definition(String alias,boolean retired){
        var env=new StandardEnvironment();var v=new HashMap<String,Object>();v.put("app.secrets.environment","datasource002-test");v.put("app.external-datasources.orders.alias",alias);v.put("app.external-datasources.orders.registry-key","orders");if(retired){v.put("app.external-datasources.retired.alias","과거 조회");v.put("app.external-datasources.retired.registry-key","retired");}env.getPropertySources().addFirst(new MapPropertySource("fixture",v));
        var registry=new ConnectionRegistry(){final Generation g=new Generation(UUID.randomUUID(),"fixture","1",java.time.Instant.now());public Generation generation(){return g;}public void validateBoot(){}public List<PublicView> listPublicKinds(String caller){return List.of();}public PublicView describe(String caller,Ref ref){return new PublicView(ref,"시험",g,Status.AVAILABLE);}
            public <T>T withResolvedConnection(String caller,Ref ref,java.util.function.Function<Resolved,T> work){return work.apply(new Resolved(){public Ref ref(){return ref;}public String endpoint(){return "jdbc:postgresql://127.0.0.1:55420/fixture";}public String authentication(){return "NONE";}public String principal(){return null;}public Map<String,String> headers(){return Map.of();}public Set<String> capabilities(){return Set.of();}public <R>R withSecret(java.util.function.Function<char[],R> f){throw new AssertionError("비밀 미사용");}});}
        };return new ExternalDataSources(env,registry);
    }
}
