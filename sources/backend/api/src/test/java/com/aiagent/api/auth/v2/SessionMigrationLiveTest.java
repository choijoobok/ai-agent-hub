// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.v2.SessionV2.Provider;
import java.nio.file.Path;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.Contexts;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("authV2Live")
class SessionMigrationLiveTest {
    private void migrate(DriverManagerDataSource source,String file,boolean rollback)throws Exception{
        try(var resources=new DirectoryResourceAccessor(Path.of("../bootstrap/src/main/resources").toAbsolutePath());var liquibase=new Liquibase(file,resources,new JdbcConnection(source.getConnection()))){if(rollback)liquibase.rollback(1,"");else liquibase.update(new Contexts());}
    }
    @Test @DisplayName("TC-30127 실제Liquibase새원장/오류메시지apply→rollback→재apply는독립완결이다")
    void appliesAndRollsBackOnlyNewChangesets()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.JDBC)){
            String schema="migration_"+UUID.randomUUID().toString().replace("-","");fixture.jdbc.execute("CREATE SCHEMA "+schema);var source=new DriverManagerDataSource(System.getenv("AUTH_V2_TEST_JDBC")+"?currentSchema="+schema,System.getenv("AUTH_V2_TEST_USER"),System.getenv("AUTH_V2_TEST_PASSWORD"));var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME VARCHAR(255) PRIMARY KEY,NEXT_VAL BIGINT NOT NULL)");jdbc.execute("CREATE TABLE SYS_MESSAGE_KEY(MSG_KEY VARCHAR(100) PRIMARY KEY,USE_SCOPE VARCHAR(10),MSG_DESC VARCHAR(1000))");jdbc.execute("CREATE TABLE SYS_MESSAGE(MSG_KEY VARCHAR(100) REFERENCES SYS_MESSAGE_KEY(MSG_KEY),LANG_CD VARCHAR(10),MSG_VAL VARCHAR(1000),PRIMARY KEY(MSG_KEY,LANG_CD))");jdbc.execute("CREATE TABLE SYS_CACHE_VERSION(CACHE_NAME VARCHAR(100) PRIMARY KEY,VERSION BIGINT,MOD_DTM TIMESTAMPTZ)");jdbc.update("INSERT INTO SYS_CACHE_VERSION VALUES('message',1,clock_timestamp())");
            String platform="db/changelog/modules/platform/002-auth-session-v2.sql",message="db/changelog/modules/system/007-auth-session-v2-message.sql";
            migrate(source,platform,false);migrate(source,message,false);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_MESSAGE WHERE MSG_KEY='PASSWORD_CHANGED_REAUTH_REQUIRED'",Integer.class)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT VERSION FROM SYS_CACHE_VERSION",Long.class)).isEqualTo(2L);
            migrate(source,message,true);migrate(source,platform,true);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_MESSAGE",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name LIKE 'ai_auth_%'",Integer.class,schema)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_SEQUENCE",Integer.class)).isZero();
            migrate(source,platform,false);migrate(source,message,false);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SYS_MESSAGE",Integer.class)).isEqualTo(2);
        }
    }
}
