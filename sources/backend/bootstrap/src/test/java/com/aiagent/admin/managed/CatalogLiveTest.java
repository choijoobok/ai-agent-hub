// @feat FEAT-003
package com.aiagent.admin.managed;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.datasource.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.support.deployment.ExternalDataSources;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogLiveTest {
 @TestConfiguration @PropertySource(value={"classpath:data-source-test.properties","classpath:catalog-test.properties"},encoding="UTF-8") static class Settings{}
 ManagedLiveFixture f;
 @BeforeAll void start()throws Exception{f=new ManagedLiveFixture(Settings.class);var loaded=f.bean(ExternalDataSources.class);f.bean(ConfigurationDeploymentService.class).allow(loaded.environment(),loaded.bootId(),0,UUID.randomUUID(),"fixture",false);f.bean(DataSourceSyncService.class).synchronize(loaded);}
 @AfterAll void close(){if(f!=null)f.close();}
 Map<String,Object> master(String ds){var m=new LinkedHashMap<String,Object>();m.put("dataSourceId",ds);m.put("databaseName","managed040_slice");m.put("schemaName","public");m.put("objectName","orders");m.put("objectType","TABLE");m.put("businessName","주문");m.put("description","업무 주문");m.put("useYn",false);return m;}
 ManagedCommand command(String id,String rev,String scope,Map<String,Object> v){return new ManagedCommand(UUID.randomUUID().toString(),id,rev,scope,v,"카탈로그 실제 저장 시험");}
 @Test @DisplayName("TC-0031 TC-0033 TC-0037 TC-00313 TC-00322 TC-00323 영역 저장·기준판·전체HIST·원명령·실패원자취소")
 void savesAreasAndHistoryAtomically()throws Exception{
  String ds=f.database().queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);
  var options=f.adminCall("catalog","getReferenceCandidates",Map.of(),200);assertThat(options.path("dataSources").get(0).path("dbmsVersion").asText()).isEmpty();assertThat(options.path("timezones").size()).isGreaterThan(0);
  var create=command(null,null,"MASTER",master(ds));var first=f.adminCall("catalog","saveCreate",create,200);String id=first.path("targets").get(0).path("targetId").asText();assertThat(id).matches("[1-9][0-9]*");assertThat(first.path("targets").get(0).path("appliedRevision").asText()).isEqualTo("1");
  assertThat(f.adminCall("catalog","saveCreate",create,200)).isEqualTo(first);var list=f.adminCall("catalog","getList",Map.of("conditions",List.of(),"page",Map.of("page",0,"size",20)),200);assertThat(list.path("content").size()).isEqualTo(1);assertThat(f.adminCall("catalog","getReferences",Map.of("id",id),200).path("state").asText()).isEqualTo("NOT_CONNECTED");
  var column=new LinkedHashMap<String,Object>();column.put("clientKey","draft-column");column.put("columnName","order_id");column.put("detailDescription","  SQL 예시\n    업무 설명");
  var columns=command(id,"1","COLUMNS",Map.of("columns",List.of(column)));var stored=f.adminCall("catalog","saveChange",columns,200);assertThat(stored.path("targets").get(0).path("appliedRevision").asText()).isEqualTo("2");
  assertThat(stored.path("memberMappings").get(0).path("clientKey").asText()).isEqualTo("draft-column");
  var detail=f.adminCall("catalog","getOne",Map.of("id",id),200);assertThat(detail.path("values").path("columns").get(0).path("detailDescription").asText()).startsWith("  SQL");assertThat(detail.path("values").path("master").path("description").asText()).isEqualTo("업무 주문");
  var next=master(ds);next.put("businessName","업무 주문 변경");f.adminCall("catalog","saveChange",command(id,"2","MASTER",next),200);assertThat(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("columns").size()).isEqualTo(1);
  f.adminCall("catalog","saveChange",command(id,"1","MASTER",next),409);
  var bad=master(ds);bad.put("objectName","different");f.adminCall("catalog","saveChange",command(id,"3","MASTER",bad),409);
  f.database().execute("CREATE FUNCTION fx_catalog_hist_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '시험 이력 실패'; END $$");f.database().execute("CREATE TRIGGER fx_catalog_hist_fail BEFORE INSERT ON AI_ADMIN_CATALOG_COLUMN_HIST FOR EACH ROW EXECUTE FUNCTION fx_catalog_hist_fail()");
  next.put("description","실패하면 남지 않는다");f.adminCall("catalog","saveChange",command(id,"3","MASTER",next),503);assertThat(f.database().queryForObject("SELECT REVISION FROM AI_ADMIN_CATALOG WHERE CATALOG_ID=?",Long.class,Long.valueOf(id))).isEqualTo(3);assertThat(f.database().queryForObject("SELECT DESCRIPTION FROM AI_ADMIN_CATALOG_CONTENT WHERE CATALOG_ID=?",String.class,Long.valueOf(id))).isEqualTo("업무 주문");f.database().execute("DROP TRIGGER fx_catalog_hist_fail ON AI_ADMIN_CATALOG_COLUMN_HIST");
  assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CATALOG_HIST WHERE CATALOG_ID=?",Long.class,Long.valueOf(id))).isEqualTo(3);
  f.adminCall("catalog","saveCreate",command(null,null,"MASTER",master(ds)),409);
  var reader=f.login("reader040");assertThat(reader.call("POST","/api/admin/service/catalog/saveChange",command(id,"3","MASTER",next)).statusCode()).isEqualTo(403);
 }
 @Test @DisplayName("TC-0032 TC-0035 등록 연결의 실제 관측·동일 객체 이름 정정·동명 재생성 거부")
 void registeredSourceObservationAndRename()throws Exception{
  String ds=f.database().queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);String schema=f.database().queryForObject("SELECT current_schema()",String.class);
  f.database().execute("CREATE TABLE source_catalog003(id bigint NOT NULL, label varchar(30))");var m=master(ds);m.put("schemaName",schema);m.put("objectName","source_catalog003");
  var observed=f.adminCall("catalog","getResolvedObject",m,200);assertThat(observed.path("state").asText()).isEqualTo("COMPLETE");assertThat(observed.path("columns").size()).isEqualTo(2);System.out.println("SOURCE_POSTGRESQL_VERSION="+observed.path("productVersion").asText());
  String id=f.adminCall("catalog","saveCreate",command(null,null,"MASTER",m),200).path("targets").get(0).path("targetId").asText();
  f.database().execute("ALTER TABLE source_catalog003 RENAME TO source_catalog003_renamed");m.put("objectName","source_catalog003_renamed");m.put("identityEvidence","동일 원천 OID와 고객사 이름 정정 확인");f.adminCall("catalog","saveChange",command(id,"1","MASTER",m),200);
  assertThat(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("master").path("objectName").asText()).isEqualTo("source_catalog003_renamed");
  f.database().execute("CREATE TABLE source_catalog003_other(id bigint)");m.put("objectName","source_catalog003_other");f.adminCall("catalog","saveChange",command(id,"2","MASTER",m),409);
  f.adminCall("catalog","saveChange",command(id,"2","COLUMNS",Map.of("columns",List.of(Map.of("clientKey","source-id","columnName","id")))),200);
  var column=tools.jackson.databind.json.JsonMapper.builder().build().convertValue(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("columns").get(0),Map.class);
  f.database().execute("ALTER TABLE source_catalog003_renamed RENAME COLUMN id TO renamed_id");column.put("columnName","renamed_id");column.put("columnIdentityEvidence","동일 원천 attnum 확인");
  f.adminCall("catalog","saveChange",command(id,"3","COLUMNS",Map.of("columns",List.of(column))),200);
  f.database().execute("ALTER TABLE source_catalog003_renamed DROP COLUMN renamed_id");f.database().execute("ALTER TABLE source_catalog003_renamed ADD COLUMN other_id bigint");column.put("columnName","other_id");
  f.adminCall("catalog","saveChange",command(id,"4","COLUMNS",Map.of("columns",List.of(column))),409);
 }

 @Test @DisplayName("TC-0036 TC-00317 TC-00318 TC-00320 PK 안정번호·유일키/인덱스 분리·새 매핑·참조 보호")
 void keysIndexesAndReferences()throws Exception{
  String ds=f.database().queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);var m=master(ds);m.put("objectName","catalog_key003");m.put("useYn",true);m.put("pkName","pk_catalog");m.put("pkEvidence","설계서");String id=f.adminCall("catalog","saveCreate",command(null,null,"MASTER",m),200).path("targets").get(0).path("targetId").asText();
  var onList=f.adminCall("catalog","getList",Map.of("conditions",List.of(Map.of("field","objectName","op","eq","value","catalog_key003")),"page",Map.of("page",0,"size",20)),200);assertThat(onList.path("content").get(0).path("useYn").asBoolean()).isTrue();
  var cols=List.of(Map.of("clientKey","pk-col","columnName","id_a","primaryKeySummary","YES","pkPosition",1),Map.of("clientKey","other-col","columnName","id_b"));
  var first=f.adminCall("catalog","saveChange",command(id,"1","COLUMNS",Map.of("columns",cols)),200);assertThat(first.path("memberMappings").size()).isEqualTo(2);
  long pk=f.database().queryForObject("SELECT KEY_NO FROM AI_ADMIN_CATALOG_KEY WHERE CATALOG_ID=? AND KEY_KIND='PRIMARY'",Long.class,Long.valueOf(id));
  var json=tools.jackson.databind.json.JsonMapper.builder().build();var detail=f.adminCall("catalog","getOne",Map.of("id",id),200);var stored=json.convertValue(detail.path("values").path("columns"),List.class);
  var noChange=f.adminCall("catalog","saveChange",command(id,"2","COLUMNS",Map.of("columns",stored)),200);assertThat(noChange.path("status").asText()).isEqualTo("NO_CHANGE");
  ((Map)stored.get(1)).put("detailDescription","업무 정의 보완");f.adminCall("catalog","saveChange",command(id,"2","COLUMNS",Map.of("columns",stored)),200);
  assertThat(f.database().queryForObject("SELECT KEY_NO FROM AI_ADMIN_CATALOG_KEY WHERE CATALOG_ID=? AND KEY_KIND='PRIMARY'",Long.class,Long.valueOf(id))).isEqualTo(pk);
  m.put("pkName","pk_catalog_v2");m.put("pkEvidence","담당자 확인");f.adminCall("catalog","saveChange",command(id,"3","MASTER",m),200);assertThat(f.database().queryForObject("SELECT SOURCE_NAME FROM AI_ADMIN_CATALOG_KEY WHERE KEY_NO=?",String.class,pk)).isEqualTo("pk_catalog_v2");
  String column=detail.path("values").path("columns").get(0).path("columnId").asText();
  var regular=Map.of("clientKey","index-draft","name","same_name","definitionKind","INDEX","unique","UNKNOWN","columns",List.of(Map.of("columnId",column,"direction","DESC")),"supportStatus","UNSUPPORTED");var unique=Map.of("clientKey","key-draft","name","same_name","definitionKind","UNIQUE_KEY","unique","YES","columns",List.of(Map.of("columnId",column,"direction","UNKNOWN")));var unnamed=Map.of("clientKey","unnamed","definitionKind","INDEX","unique","UNKNOWN","columns",List.of(Map.of("columnId",column,"direction","UNKNOWN")));
  var indexResult=f.adminCall("catalog","saveChange",command(id,"4","INDEXES",Map.of("indexes",List.of(regular,unique,unnamed))),200);assertThat(indexResult.path("memberMappings").size()).isEqualTo(3);
  assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CATALOG_KEY WHERE CATALOG_ID=? AND KEY_KIND='UNIQUE'",Long.class,Long.valueOf(id))).isEqualTo(1);assertThat(f.database().queryForObject("SELECT COUNT(*) FROM AI_ADMIN_CATALOG_INDEX WHERE CATALOG_ID=?",Long.class,Long.valueOf(id))).isEqualTo(2);
  var indexes=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("indexes");assertThat(indexes.toString()).contains("UNSUPPORTED","UNIQUE_KEY");
  f.adminCall("catalog","saveChange",command(id,"5","COLUMNS",Map.of("columns",List.of())),409);
  assertThat(f.bean(com.aiagent.admin.datasource.DataSourceReferenceService.class).references(ds)).isEqualTo(com.aiagent.admin.datasource.DataSourceReferencePort.Reference.PRESENT);
  assertThatThrownBy(()->f.database().update("UPDATE AI_ADMIN_CATALOG_INDEX_PART_HIST SET CATALOG_ID=0 WHERE CATALOG_ID=?",Long.valueOf(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->f.database().update("UPDATE AI_ADMIN_CATALOG_INDEX_HIST SET HISTORY_NO=0 WHERE CATALOG_ID=?",Long.valueOf(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test @DisplayName("TC-0031 카탈로그 빈 스키마 적용·전체 신규 변경 롤백·재적용")
 void migrationRoundTrip()throws Exception{
  String schema="catalog_migration003_"+UUID.randomUUID().toString().replace("-","");try(var c=java.sql.DriverManager.getConnection(System.getenv("MANAGED_TEST_JDBC"),System.getenv("MANAGED_TEST_USER"),System.getenv().getOrDefault("MANAGED_TEST_PASSWORD",""))){c.createStatement().execute("CREATE SCHEMA "+schema);c.setSchema(schema);try(var l=new liquibase.Liquibase("db/catalog003-migration.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(c))){l.update(new liquibase.Contexts());l.rollback(10,"");try(var rows=c.createStatement().executeQuery("SELECT to_regclass('ai_admin_catalog') IS NULL")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}l.update(new liquibase.Contexts());try(var rows=c.createStatement().executeQuery("SELECT to_regclass('ai_admin_catalog_column_hist') IS NOT NULL")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}}}
 }

 @Test @DisplayName("TC-0072 TC-0073 TC-0034 TC-0039 TC-00310 TC-00311 TC-00325 통합 SAVE·그룹 부분집합·예외 연쇄 정리·원자성과 현재 권한")
 void policyUsesCatalogSaveAndRemovesOnlyOwnedExceptions()throws Exception{
  var json=tools.jackson.databind.json.JsonMapper.builder().build();String ds=f.database().queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);
  String a=f.groupCall("save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",Map.of("groupNm","카탈로그 A","description","정책 시험"),"그룹 시험"),200).path("targetId").asText();
  String b=f.groupCall("save",new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",Map.of("groupNm","카탈로그 B","description","정책 시험"),"그룹 시험"),200).path("targetId").asText();
  var groups=List.of(Map.of("groupId",a,"mappingActive",true),Map.of("groupId",b,"mappingActive",true));
  var dsDetail=f.adminCall("dataSource","getOne",Map.of("id",ds),200);var dsValues=json.convertValue(dsDetail.path("values"),Map.class);dsValues.put("applicableGroups",groups);
  String dsRevision=f.database().queryForObject("SELECT MANAGEMENT_VERSION::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,Long.valueOf(ds));
  f.adminCall("dataSource","save",new ManagedCommand(UUID.randomUUID().toString(),ds,dsRevision,"main",dsValues,"상위 그룹 허용"),200);
  var m=master(ds);m.put("objectName","catalog_policy003");String id=f.adminCall("catalog","saveCreate",command(null,null,"MASTER",m),200).path("targets").get(0).path("targetId").asText();
  f.adminCall("catalog","saveChange",command(id,"1","COLUMNS",Map.of("columns",List.of(Map.of("clientKey","policy-column","columnName","id")))),200);
  String column=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("columns").get(0).path("columnId").asText();
  f.adminCall("catalog","saveChange",command(id,"2","ACCESS_GROUPS",Map.of("groups",groups)),200);
  var exceptions=List.of(Map.of("columnId",column,"groupId",a,"mode","HIDE","reason","A 제한"),Map.of("columnId",column,"groupId",b,"mode","HIDE","reason","B 제한"));
  f.adminCall("catalog","saveChange",command(id,"3","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",exceptions)),200);
  var registered=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").get(0);
  assertThat(registered.path("regId").asText()).isEqualTo("admin");assertThat(registered.path("regDtm").asText()).isNotBlank();
  var forged=new LinkedHashMap<String,Object>(exceptions.get(0));forged.put("regId","forged");
  f.adminCall("catalog","saveChange",command(id,"4","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(forged))),400);
  f.database().update("INSERT INTO SYS_ROLE_MENU_AUTH VALUES ('READER040','HUB_CATALOG','READ') ON CONFLICT DO NOTHING");var reader=f.login("reader040");
  assertThat(reader.call("POST","/api/admin/service/catalog/getOne",Map.of("id",id)).statusCode()).isEqualTo(200);
  assertThat(reader.call("POST","/api/admin/service/catalog/saveChange",command(id,"4","ACCESS_GROUPS",Map.of("groups",List.of()))).statusCode()).isEqualTo(403);
  f.adminCall("catalog","saveChange",command(id,"4","ACCESS_GROUPS",Map.of("groups",List.of(Map.of("groupId",a,"mappingActive",false),groups.get(1)))),200);
  assertThat(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").size()).isEqualTo(2);
  var remove=command(id,"5","ACCESS_GROUPS",Map.of("groups",List.of(groups.get(1))));
  f.database().execute("CREATE FUNCTION fx_catalog_policy_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '정책 이력 실패 시험'; END $$");f.database().execute("CREATE TRIGGER fx_catalog_policy_fail BEFORE INSERT ON AI_ADMIN_CATALOG_GROUP_HIST FOR EACH ROW EXECUTE FUNCTION fx_catalog_policy_fail()");
  f.adminCall("catalog","saveChange",remove,503);assertThat(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").size()).isEqualTo(2);f.database().execute("DROP TRIGGER fx_catalog_policy_fail ON AI_ADMIN_CATALOG_GROUP_HIST");
  var result=f.adminCall("catalog","saveChange",remove,200);assertThat(f.adminCall("catalog","saveChange",remove,200)).isEqualTo(result);
  var after=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values");assertThat(after.path("exceptions").size()).isEqualTo(1);assertThat(after.path("exceptions").get(0).path("groupId").asText()).isEqualTo(b);
  f.adminCall("catalog","saveChange",command(id,"6","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(exceptions.get(0)))),409);
  f.adminCall("catalog","saveChange",command(id,"6","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(Map.of("columnId",column,"groupId",b,"mode","MASK","reason","미설치 마스킹")))),503);
  f.adminCall("catalog","saveChange",command(id,"6","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of())),200);assertThat(f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("groups").size()).isEqualTo(1);
  f.adminCall("catalog","saveChange",command(id,"7","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(exceptions.get(1)))),200);
  var original=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").get(0);
  var revised=new LinkedHashMap<String,Object>(exceptions.get(1));revised.put("reason","사유 수정");
  f.adminCall("catalog","saveChange",command(id,"8","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(revised))),200);
  var saved=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").get(0);
  assertThat(saved.path("regId")).isEqualTo(original.path("regId"));assertThat(saved.path("regDtm")).isEqualTo(original.path("regDtm"));
  assertThat(f.database().queryForObject("SELECT REG_ID FROM AI_ADMIN_COLUMN_ACCESS_POLICY_HIST WHERE CATALOG_ID=? ORDER BY HISTORY_NO DESC LIMIT 1",String.class,Long.valueOf(id))).isEqualTo("admin");
  assertThat(f.adminCall("catalog","saveChange",command(id,"9","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(revised))),200).path("status").asText()).isEqualTo("NO_CHANGE");
  f.database().update("UPDATE AI_ADMIN_COLUMN_ACCESS_POLICY SET REG_ID=NULL,REG_DTM=NULL WHERE CATALOG_ID=?",Long.valueOf(id));revised.put("reason","과거 미확인 근거 보존");
  f.adminCall("catalog","saveChange",command(id,"9","COLUMN_GROUP_EXCEPTIONS",Map.of("exceptions",List.of(revised))),200);
  var legacy=f.adminCall("catalog","getOne",Map.of("id",id),200).path("values").path("exceptions").get(0);
  assertThat(legacy.path("regId").isNull()).isTrue();assertThat(legacy.path("regDtm").isNull()).isTrue();

 }
 @Test @DisplayName("TC-0033 검수 주체·시각은 서버 소유이며 의미 수정은 이전 검수를 승계하지 않는다")
 void reviewEvidenceDoesNotSurviveMeaningChange()throws Exception{
  String ds=f.database().queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'",String.class);var m=master(ds);m.put("objectName","review003");m.put("reviewStatus","VERIFIED");m.put("reviewEvidence","담당자 구조/의미 확인");String id=f.adminCall("catalog","saveCreate",command(null,null,"MASTER",m),200).path("targets").get(0).path("targetId").asText();var before=f.adminCall("catalog","getOne",Map.of("id",id),200);assertThat(before.path("dependencies").path("review").path("reviewerId").asText()).isEqualTo("admin");assertThat(before.path("dependencies").path("review").path("reviewedAt").isNull()).isFalse();
  m.put("description","바뀐 업무 의미");f.adminCall("catalog","saveChange",command(id,"1","MASTER",m),200);var after=f.adminCall("catalog","getOne",Map.of("id",id),200);assertThat(after.path("values").path("master").path("reviewStatus").asText()).isEqualTo("UNKNOWN");assertThat(after.path("dependencies").path("review").path("reviewedAt").isNull()).isTrue();assertThat(f.database().queryForObject("SELECT REVIEWER_ID FROM AI_ADMIN_CATALOG_CONTENT_HIST WHERE CATALOG_ID=? ORDER BY HISTORY_NO LIMIT 1",String.class,Long.valueOf(id))).isEqualTo("admin");
 }

}
