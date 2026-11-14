// @feat FEAT-001
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.targetsystem.*;
import com.aiagent.support.deployment.TargetConnections;
import com.aiagent.support.deployment.BusinessApiInvokers;
import com.aiagent.core.deployment.ConnectionRegistry;
import org.springframework.core.env.*;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;

/** 전용 DB에서 실제 조립·전체 동기화·표시 저장·이력·명령 재수신을 함께 검증한다. */
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TargetSystemLiveTest {
    @TestConfiguration @PropertySource(value="classpath:target-system-test.properties",encoding="UTF-8") static class Settings {
        static TargetReferencePort.Reference reference=TargetReferencePort.Reference.ABSENT;
        @Bean TargetReferencePort fixtureReference(){return new TargetReferencePort(){
            public Set<String> tables(){return Set.of();}
            public Reference reference(String system,String connection){return Settings.reference;}
        };}
    }
    private ManagedLiveFixture f;
    private TargetConnections previousBoot;
    @BeforeAll void start()throws Exception{
        f=new ManagedLiveFixture(Settings.class);
        String month=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().substring(0,7);
        f.database().update("INSERT INTO ai_admin_quota_grade VALUES (900001,CURRENT_TIMESTAMP)");
        f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES (900001,?,'시험 당월',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
        f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES (900001,900001,'기본',1,1,NULL,'')");
    }
    @AfterAll void close(){if(f!=null)f.close();}
    @Test @Order(1) @DisplayName("TC-0012 TC-0013 TC-0014 TC-0015 TC-0016 TC-0017 TC-00114 TC-00120 TC-00121 TC-00123 TC-00125 TC-00126 기동·표시 소유·현재 권한·낡은 버전·쓰기 없는 조회")
    void syncAndOverride() throws Exception {
        var loaded=f.bean(TargetConnections.class);var deployment=f.bean(TargetDeploymentService.class);var sync=f.bean(TargetSystemSyncService.class);
        assertThatThrownBy(()->sync.synchronize(loaded)).isInstanceOf(RuntimeException.class);
        var old=oldSettings();previousBoot=old;deployment.stage(old);deployment.allow(old.environment(),old.bootId(),0,UUID.randomUUID(),"fixture-operator",false);sync.synchronize(old);
        deployment.allow(loaded.environment(),loaded.bootId(),1,UUID.randomUUID(),"fixture-operator",false);
        sync.synchronize(loaded);sync.synchronize(loaded);
        var jdbc=f.database();
        Long id=jdbc.queryForObject("SELECT target_system_id FROM ai_admin_target_system WHERE system_key='orders'",Long.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_admin_target_system_hist WHERE target_system_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT hist_source_cd FROM ai_admin_target_system_hist WHERE target_system_id=?",String.class,id)).containsOnly("CONFIG_SYNC");
        var row=f.adminCall("targetSystem","getOne",Map.of("id",id.toString()),200);
        assertThat(row.path("systemName").asText()).isEqualTo("주문 시스템");
        var values=new LinkedHashMap<String,Object>();values.put("systemNameOverride","관리자 표시명");values.put("descriptionOverride","");
        var children=new ArrayList<Map<String,Object>>();
        for(String key:List.of("main","obsolete")){var child=new LinkedHashMap<String,Object>();child.put("connectionId",key);child.put("nameOverride",null);child.put("descriptionOverride",null);children.add(child);}values.put("connectionOverrides",children);
        var command=new ManagedCommand(UUID.randomUUID().toString(),id.toString(),"2","display",values,"표시 보완 검증");
        var saved=f.adminCall("targetSystem","save",command,200);
        assertThat(f.adminCall("targetSystem","save",command,200)).isEqualTo(saved);
        f.adminCall("targetSystem","save",new ManagedCommand(UUID.randomUUID().toString(),id.toString(),"2","display",values,"낡은 판"),409);
        var technical=new LinkedHashMap<>(values);technical.put("baseUrl","https://not-allowed.invalid");
        f.adminCall("targetSystem","save",new ManagedCommand(UUID.randomUUID().toString(),id.toString(),"3","display",technical,"기술값 변조"),400);
        f.adminCall("targetSystem","save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"display",values,"신규 금지"),400);
        var missing=f.adminCall("targetSystem","getOne",Map.of("id",id.toString()),200).path("connections").get(1);
        assertThat(missing.path("connectionId").asText()).isEqualTo("obsolete");
        assertThat(missing.path("currentDisplay").path("baseUrl").isNull()).isTrue();assertThat(missing.path("testAllowed").asBoolean()).isFalse();
        assertThatThrownBy(()->sync.synchronize(old)).isInstanceOf(RuntimeException.class);
        sync.synchronize(loaded);
        assertThat(f.adminCall("targetSystem","getOne",Map.of("id",id.toString()),200).path("systemName").asText()).isEqualTo("관리자 표시명");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_admin_target_system_hist WHERE target_system_id=?",Integer.class,id)).isEqualTo(3);
        assertThat(f.adminCall("targetSystem","getCommandResult",Map.of("commandKey",command.commandKey()),200).path("result")).isEqualTo(saved);
        jdbc.update("INSERT INTO SYS_ROLE_MENU_AUTH(ROLE_ID,MENU_CD,ACTION_CD) VALUES ('READER040','HUB_TARGET_SYSTEM','READ')");
        var reader=f.login("reader040");
        assertThat(reader.call("POST",TargetSystemService.BASE+"/testConnection",Map.of("systemId",id.toString(),"connectionId","main","appliedSettingsId",loaded.settingsId())).statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO SYS_ROLE_MENU_AUTH(ROLE_ID,MENU_CD,ACTION_CD) VALUES ('READER040','HUB_TARGET_SYSTEM','EXECUTE')");
        assertThat(reader.call("POST",TargetSystemService.BASE+"/testConnection",Map.of("systemId",id.toString(),"connectionId","main","appliedSettingsId",loaded.settingsId())).statusCode()).isEqualTo(200);
        assertThat(reader.call("POST",TargetSystemService.BASE+"/save",command).statusCode()).isEqualTo(403);
        jdbc.update("DELETE FROM SYS_ROLE_MENU_AUTH WHERE ROLE_ID='READER040' AND MENU_CD='HUB_TARGET_SYSTEM' AND ACTION_CD='READ'");
        assertThat(reader.call("POST",TargetSystemService.BASE+"/getOne",Map.of("id",id.toString())).statusCode()).isEqualTo(403);
        assertThat(f.adminCall("targetSystem","testConnection",Map.of("systemId",id.toString(),"connectionId","main","appliedSettingsId",loaded.settingsId()),200).path("reasonCode").asText()).isEqualTo("NOT_CONFIGURED");
        f.adminCall("targetSystem","testConnection",Map.of("systemId",id.toString(),"connectionId","main","appliedSettingsId","stale"),409);
        var list=f.adminCall("targetSystem","getList",Map.of("conditions",List.of(Map.of("field","configurationMismatch","op","eq","value",true)),"page",Map.of("page",0,"size",100),"sorts",List.of()),200);
        assertThat(list.path("content")).hasSize(2);
        var alerts=f.adminCall("integration","getConfigurationAlerts",Map.of("params",Map.of("systemKey","orders","status","OPEN"),"page",Map.of("page",0,"size",100)),200);
        assertThat(alerts.path("page").path("content")).hasSize(1);
        assertThat(alerts.path("summary").path("OPEN").asInt()).isEqualTo(1);
        assertThat(alerts.path("page").path("content").get(0).path("impactCounts").path("api").isNull()).isTrue();
        assertThat(f.adminCall("integration","getConfigurationAlerts",Map.of("params",Map.of("systemKey","order"),"page",Map.of("page",0,"size",100)),200).path("page").path("content")).isEmpty();
        long diagnosisCount=jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_DIAGNOSIS",Long.class);
        f.adminCall("targetSystem","getList",Map.of("conditions",List.of(),"page",Map.of("page",0,"size",100),"sorts",List.of(Map.of("field","modDtm","dir","desc"))),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_DIAGNOSIS",Long.class)).isEqualTo(diagnosisCount);
        var profile=new LinkedHashMap<String,Object>();profile.put("loginId","target-identity-fixture");profile.put("userNm","대상 신원 시험");profile.put("organizationNm","시험");profile.put("useStartDt",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());profile.put("useEndDt",null);profile.put("activeYn","N");
        var portal=f.adminCall("portalUser","save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"profile",profile,"신원 연계 시험"),200);
        var identities=Map.<String,Object>of("identities",List.of(Map.of("targetId",id.toString(),"externalUserId","fixture","mappingActiveYn","N","identityEvidence","시험 매핑")));
        f.adminCall("portalUser","save",new ManagedCommand(UUID.randomUUID().toString(),portal.path("targetId").asText(),"1","identitySystem",identities,"현재 대상 연결"),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_IDENTITY WHERE TARGET_KIND='SYSTEM' AND TARGET_ID=?",Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->deployment.allow(loaded.environment(),UUID.randomUUID(),1,UUID.randomUUID(),"fixture-operator",false)).isInstanceOf(RuntimeException.class);
    }
    @Test @Order(2) @DisplayName("TC-0019 TC-00111 TC-00127 TC-00128 TC-00129 TC-00130 생산 이력·누락 정리: 참조 실패 차단·부모 존속·전체 롤백·재수신")
    void cleanup()throws Exception{
        var jdbc=f.database();String parent=jdbc.queryForObject("SELECT TARGET_SYSTEM_ID FROM AI_ADMIN_TARGET_SYSTEM WHERE SYSTEM_KEY='orders'",String.class);
        var query=Map.of("scope","CONNECTION","parentId",parent,"conditions",List.of());
        var deployment=f.bean(TargetDeploymentService.class);var current=f.bean(TargetConnections.class);
        deployment.allow(previousBoot.environment(),previousBoot.bootId(),2,UUID.randomUUID(),"fixture-operator",true);
        assertThat(f.adminCall("targetSystem","getConfigurationCleanupPreview",query,200).path("executable").asBoolean()).isFalse();
        deployment.allow(current.environment(),current.bootId(),3,UUID.randomUUID(),"fixture-operator",false);
        Settings.reference=TargetReferencePort.Reference.UNKNOWN;
        assertThat(f.adminCall("targetSystem","getConfigurationCleanupPreview",query,200).path("executable").asBoolean()).isFalse();
        Settings.reference=TargetReferencePort.Reference.ABSENT;
        var preview=f.adminCall("targetSystem","getConfigurationCleanupPreview",query,200);
        assertThat(preview.path("targets")).hasSize(1);assertThat(preview.path("executable").asBoolean()).isTrue();
        var json=JsonMapper.builder().build();
        var values=new LinkedHashMap<String,Object>();values.put("previewToken",preview.path("previewToken").asText());values.put("query",query);
        values.put("confirmedTargets",List.of(json.convertValue(preview.path("targets").get(0).path("target"),Map.class)));
        var command=new ManagedCommand(UUID.randomUUID().toString(),null,null,"cleanup",values,"누락 연결 정리");
        Settings.reference=TargetReferencePort.Reference.PRESENT;
        f.adminCall("targetSystem","deleteConfigurationMismatches",command,409);
        Settings.reference=TargetReferencePort.Reference.ABSENT;
        jdbc.execute("CREATE TABLE fx_target_failure(enabled boolean NOT NULL)");jdbc.update("INSERT INTO fx_target_failure VALUES (true)");
        jdbc.execute("CREATE FUNCTION fx_target_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action_cd='CONFIGURATION_CLEANUP' AND (SELECT enabled FROM fx_target_failure) THEN RAISE EXCEPTION '시험 원장 쓰기 실패'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fx_target_guard BEFORE INSERT ON ai_admin_asset_command FOR EACH ROW EXECUTE FUNCTION fx_target_guard()");
        f.adminCall("targetSystem","deleteConfigurationMismatches",command,503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=?",Integer.class,Long.valueOf(parent))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT MANAGEMENT_VERSION FROM AI_ADMIN_TARGET_SYSTEM WHERE TARGET_SYSTEM_ID=?",Integer.class,Long.valueOf(parent))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT STATUS_CD FROM AI_ADMIN_CONFIG_ALERT WHERE CONFIG_ID=? AND TARGET_KEY='orders/obsolete'",String.class,current.settingsId())).isEqualTo("OPEN");
        jdbc.update("UPDATE fx_target_failure SET enabled=false");
        var result=f.adminCall("targetSystem","deleteConfigurationMismatches",command,200);
        assertThat(f.adminCall("targetSystem","deleteConfigurationMismatches",command,200)).isEqualTo(result);
        assertThat(f.adminCall("targetSystem","getCommandResult",Map.of("commandKey",command.commandKey()),200).path("result")).isEqualTo(result);
        assertThat(jdbc.queryForList("SELECT CONNECTION_ID FROM AI_ADMIN_TARGET_SYSTEM_CONN WHERE TARGET_SYSTEM_ID=?",String.class,Long.valueOf(parent))).containsExactly("main");
        assertThat(jdbc.queryForObject("SELECT STATUS_CD FROM AI_ADMIN_CONFIG_ALERT WHERE CONFIG_ID=? AND TARGET_KEY='orders/obsolete'",String.class,current.settingsId())).isEqualTo("RECOVERED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CONFIG_ALERT_EVENT WHERE CONFIG_ID=? AND EVIDENCE='METADATA_REMOVED'",Integer.class,current.settingsId())).isEqualTo(1);
        var remaining=f.adminCall("targetSystem","getList",Map.of("conditions",List.of(Map.of("field","configurationMismatch","op","eq","value",true)),"page",Map.of("page",0,"size",100)),200);
        assertThat(remaining.path("content")).hasSize(1);assertThat(remaining.path("content").get(0).path("systemKey").asText()).isEqualTo("retired");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_IDENTITY WHERE TARGET_KIND='SYSTEM' AND TARGET_ID=?",Integer.class,Long.valueOf(parent))).isEqualTo(1);
        var history=f.bean(TargetSystemService.class).history().event(Long.parseLong(result.path("targets").get(0).path("historyNo").asText()));
        assertThat(history.references()).hasSize(1);
        assertThat(f.bean(TargetSystemService.class).history().exact(history.references().getFirst()).rows().get("AI_ADMIN_TARGET_SYSTEM_CONN")).hasSize(2);
        var systemQuery=new LinkedHashMap<String,Object>();systemQuery.put("scope","SYSTEM");systemQuery.put("parentId",null);systemQuery.put("conditions",List.of());
        var systems=f.adminCall("targetSystem","getConfigurationCleanupPreview",systemQuery,200);
        assertThat(systems.path("targets")).hasSize(1);
        values=new LinkedHashMap<>();values.put("previewToken",systems.path("previewToken").asText());values.put("query",systemQuery);values.put("confirmedTargets",List.of(json.convertValue(systems.path("targets").get(0).path("target"),Map.class)));
        f.adminCall("targetSystem","deleteConfigurationMismatches",new ManagedCommand(UUID.randomUUID().toString(),null,null,"cleanup",values,"누락 시스템 정리"),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_SYSTEM WHERE SYSTEM_KEY='retired'",Integer.class)).isZero();
    }
    private TargetConnections oldSettings(){
        var env=new StandardEnvironment();var properties=new HashMap<String,Object>();
        properties.put("app.secrets.environment","target-system-test");properties.put("app.deployment.target-systems.retired.name","폐기 시스템");
        properties.put("app.deployment.target-systems.orders.name","주문 시스템");
        String base="app.deployment.target-systems.orders.connections.obsolete.";
        properties.put(base+"registry-key","old-http");properties.put(base+"invoker-bean","restJsonInvoker");
        env.getPropertySources().addFirst(new MapPropertySource("fixture-old",properties));
        var registry=new ConnectionRegistry(){
            final Generation generation=new Generation(UUID.randomUUID(),"fixture-old","1",java.time.Instant.now());
            public Generation generation(){return generation;}
            public List<PublicView> listPublicKinds(String caller){return List.of();}
            public PublicView describe(String caller,Ref ref){return new PublicView(ref,"이전 연결",generation,Status.AVAILABLE);}
            public <T>T withResolvedConnection(String caller,Ref ref,java.util.function.Function<Resolved,T> action){return action.apply(new Resolved(){
                public Ref ref(){return ref;}public String endpoint(){return "https://fixture.invalid/api";}public String authentication(){return "NONE";}public String principal(){return null;}
                public Map<String,String> headers(){return Map.of();}public Set<String> capabilities(){return Set.of();}public <R>R withSecret(java.util.function.Function<char[],R> work){throw new AssertionError("비밀을 사용하지 않는 시험");}
            });}
            public void validateBoot(){}
        };
        return new TargetConnections(env,registry,f.bean(BusinessApiInvokers.class));
    }
    @Test @Order(3) @DisplayName("기동 재허용·원 운영 명령 재수신이 삭제 메타를 부활시키거나 현재 허용을 되돌리지 않는다")
    void deploymentReplay(){
        var first=oldSettings();var second=oldSettings();var deployment=f.bean(TargetDeploymentService.class);var sync=f.bean(TargetSystemSyncService.class);
        deployment.stage(first);deployment.stage(second);UUID key=UUID.randomUUID();
        assertThat(deployment.allow(first.environment(),first.bootId(),4,key,"fixture-operator",false)).isEqualTo(5);sync.synchronize(first);
        assertThat(deployment.allow(second.environment(),second.bootId(),5,UUID.randomUUID(),"fixture-operator",false)).isEqualTo(6);sync.synchronize(second);
        assertThat(deployment.allow(first.environment(),first.bootId(),4,key,"fixture-operator",false)).isEqualTo(5);
        assertThat(deployment.allowed(first)).isFalse();assertThat(deployment.ready(second)).isTrue();
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_TARGET_SYSTEM WHERE SYSTEM_KEY='retired'",Integer.class)).isZero();
    }

    @Test @Order(4) @DisplayName("SLICE-001의 일곱 체인지셋을 별도 빈 스키마에 적용·롤백·재적용한다")
    void migrationRoundTrip() throws Exception {
        String url=System.getenv("MANAGED_TEST_JDBC"),user=System.getenv("MANAGED_TEST_USER"),password=System.getenv().getOrDefault("MANAGED_TEST_PASSWORD","");
        String schema="migration001_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=java.sql.DriverManager.getConnection(url,user,password)){
            connection.createStatement().execute("CREATE SCHEMA "+schema);
            connection.setSchema(schema);
            try(var migration=new liquibase.Liquibase("db/target001-migration.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))){
                migration.update(new liquibase.Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_target_system') IS NOT NULL")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}
                migration.rollback(7,"");
                try(var rows=connection.createStatement().executeQuery("SELECT to_regclass('ai_admin_target_system') IS NULL")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}
                migration.update(new liquibase.Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_cd='HUB_TARGET_SYSTEM'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
            }
        }
    }
}
