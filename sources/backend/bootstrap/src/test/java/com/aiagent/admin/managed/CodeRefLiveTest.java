// @feat FEAT-504
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeRefLiveTest {
    private ManagedLiveFixture f;
    private static final String PATH="/api/system/common/code/getListByRef";
    @BeforeAll void start()throws Exception {
        f=new ManagedLiveFixture();var db=f.database();
        for(String group:List.of("REF522","OTHER522")) {
            db.update("INSERT INTO sys_code_group(grp_cd,grp_nm) VALUES (?,?)",group,group);
            for(String ref:List.of("PARENT","OTHER"))db.update("INSERT INTO sys_code_grp_ref(grp_cd,ref_cd,ref_nm) VALUES (?,?,?)",group,ref,ref);
            for(String code:List.of("B","A","C","OFF","SPACE","QUOTE")) {
                db.update("INSERT INTO sys_code(grp_cd,dtl_cd,dtl_nm,use_yn,sort_no) VALUES (?,?,?,?,?)",group,code,"code."+group+"."+code,code.equals("OFF")?"N":"Y",code.equals("C")?2:1);
                String value=code.equals("SPACE")?" X ":code.equals("QUOTE")?"' OR 1=1 --":"X";
                db.update("INSERT INTO sys_code_ref(grp_cd,dtl_cd,ref_cd,ref_val) VALUES (?,?,?,?)",group,code,"PARENT",value);
            }
        }
    }
    @AfterAll void close(){if(f!=null)f.close();}
    private JsonNode data(HttpResponse<String> response,int status){assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return JsonMapper.builder().build().readTree(response.body()).path("data");}
    private Map<String,String> query(String value){return Map.of("grpCd","REF522","refCd","PARENT","refVal",value);}
    @Test @DisplayName("TC-5046 실제 조인·활성·안정 정렬·그룹/참조/공백/따옴표 값은 정확 비교")
    void exactJoinsAndOrdinaryCommonRead()throws Exception {
        var reader=f.login("reader040");var result=data(reader.call("POST",PATH,query("X")),200);
        assertThat(result.properties()).hasSize(2);assertThat(result.path("codes").toString()).isEqualTo("[\"A\",\"B\",\"C\"]");
        assertThat(data(reader.call("POST",PATH,query(" X ")),200).path("codes").toString()).isEqualTo("[\"SPACE\"]");
        assertThat(data(reader.call("POST",PATH,query("' OR 1=1 --")),200).path("codes").toString()).isEqualTo("[\"QUOTE\"]");
        assertThat(data(reader.call("POST",PATH,Map.of("grpCd","REF522","refCd","OTHER","refVal","X")),200).path("codes").isEmpty()).isTrue();
        assertThat(data(reader.call("POST",PATH,Map.of("grpCd","REF522","refCd","MISSING","refVal","X")),200).path("codes").isEmpty()).isTrue();
        var all=data(reader.call("POST","/api/system/common/code/getList",Map.of("grpCds",List.of("REF522"))),200);
        assertThat(all.path("groups").path("REF522")).hasSize(5);assertThat(all.path("version")).isEqualTo(result.path("version"));
        data(reader.call("POST","/api/system/service/code/save",Map.of()),403);
    }
    @Test @DisplayName("TC-5046 누락400·익명/제한/다른 영역 거부·현재 계정 검사")
    void inputAndAuthenticationRemainClosed()throws Exception {
        var reader=f.login("reader040");data(reader.call("POST",PATH,Map.of("grpCd","REF522","refCd","PARENT")),400);
        data(reader.call("POST",PATH,query("")),400);data(reader.call("POST",PATH,query("a".repeat(201))),400);
        var anonymous=f.new Browser();data(anonymous.call("GET","/api/system/public/auth/getCsrf",null),200);data(anonymous.call("POST",PATH,query("X")),401);
        var portal=f.new Browser();portal.cookies.put("AIHUB_PORTAL_SESSION",reader.cookies.get("AIHUB_ADMIN_SESSION"));data(portal.call("POST",PATH,query("X")),401);
        f.database().update("UPDATE sys_user SET pwd_init_yn='Y' WHERE user_id='revoked040'");
        var restricted=f.login("revoked040");data(restricted.call("POST",PATH,query("X")),403);
        f.database().update("UPDATE sys_user SET use_yn='N' WHERE user_id='reader040'");
        try{data(reader.call("POST",PATH,query("X")),401);}finally{f.database().update("UPDATE sys_user SET use_yn='Y' WHERE user_id='reader040'");}
    }
    @Test @DisplayName("TC-5046 의존 조회 장애를 빈 성공으로 바꾸지 않는다")
    void failedQueryIsNotEmptySuccess()throws Exception {
        var reader=f.login("reader040");f.database().execute("ALTER TABLE sys_code_ref RENAME TO sys_code_ref_522_unavailable");
        try{assertThat(reader.call("POST",PATH,query("X")).statusCode()).isGreaterThanOrEqualTo(500);}
        finally{f.database().execute("ALTER TABLE sys_code_ref_522_unavailable RENAME TO sys_code_ref");}
    }
    @Test @DisplayName("TC-5046 신규 콤보 메시지 적용·rollback·재적용")
    void messageMigrationRoundtrip()throws Exception {
        String schema="code522_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("MANAGED_TEST_JDBC"),user=System.getenv("MANAGED_TEST_USER"),password=System.getenv().getOrDefault("MANAGED_TEST_PASSWORD","");
        try(var connection=java.sql.DriverManager.getConnection(url,user,password)){connection.createStatement().execute("CREATE SCHEMA "+schema);}
        try(var connection=java.sql.DriverManager.getConnection(url+"?currentSchema="+schema,user,password)) {
            connection.createStatement().execute("CREATE TABLE SYS_MESSAGE_KEY(MSG_KEY varchar PRIMARY KEY,USE_SCOPE varchar,MSG_DESC varchar); CREATE TABLE SYS_MESSAGE(MSG_KEY varchar,LANG_CD varchar,MSG_VAL varchar,PRIMARY KEY(MSG_KEY,LANG_CD)); CREATE TABLE SYS_CACHE_VERSION(CACHE_NAME varchar PRIMARY KEY,VERSION bigint,MOD_DTM timestamp with time zone); INSERT INTO SYS_CACHE_VERSION(CACHE_NAME,VERSION) VALUES('message',1)");
            try(var migration=new liquibase.Liquibase("db/code522-only.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),new liquibase.database.jvm.JdbcConnection(connection))) {
                migration.update(new liquibase.Contexts());migration.rollback(1,"");
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM SYS_MESSAGE_KEY")){rows.next();assertThat(rows.getInt(1)).isZero();}
                migration.update(new liquibase.Contexts());
                try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM SYS_MESSAGE")){rows.next();assertThat(rows.getInt(1)).isEqualTo(10);}
            }
        }
    }
}
