// @feat FEAT-302
package com.aiagent.deployment;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.AiAgentApplication;
import com.aiagent.support.deployment.SecretEnvelope;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("managedStorageLive")
class DeploymentSecretLiveTest {
    @TempDir Path directory;
    @Test @DisplayName("TC-3021 TC-3022 TC-3024 실제 Boot/DB 이전 실패·외부 설정/환경 우선순위·키 부분 교체와 복구")
    void productBootAndRotation()throws Exception {
        String url=System.getenv("MANAGED_TEST_JDBC"),user=System.getenv("MANAGED_TEST_USER"),password=System.getenv().getOrDefault("MANAGED_TEST_PASSWORD","");
        if(url==null||!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/managed040_[A-Za-z0-9_]+"))throw new IllegalStateException("전용 localhost managed040_ 시험 DB가 필요하다");
        String schema="managed040_"+UUID.randomUUID().toString().replace("-","");
        var control=new JdbcTemplate(new DriverManagerDataSource(url,user,password));control.execute("CREATE SCHEMA "+schema);
        byte[] old=new byte[32],next=new byte[32];new java.security.SecureRandom().nextBytes(old);new java.security.SecureRandom().nextBytes(next);
        var binding=new SecretEnvelope.Binding("synthetic","deployment-test","spring.datasource.password");
        String secret="Synthetic302-"+UUID.randomUUID();String encrypted=SecretEnvelope.encrypt(old,binding,secret.toCharArray()),fresh=SecretEnvelope.encrypt(next,binding,secret.toCharArray());
        Path config=directory.resolve("deployment.properties");
        Files.writeString(config,"spring.datasource.url="+url+"?currentSchema="+schema+"\nspring.datasource.username="+user+"\nspring.datasource.password="+encrypted+"\napp.secrets.tenant=synthetic\napp.secrets.environment=deployment-test\napp.secrets.allow-plaintext=false\nspring.mail.password=ENC(unused-invalid)\nspring.data.redis.password=ENC(unused-redis-invalid)\n",StandardCharsets.UTF_8);
        Path invalidBootstrap=directory.resolve("invalid-bootstrap.properties");Files.writeString(invalidBootstrap,"spring.config.import=ENC(synthetic-invalid-location)\n");
        run(invalidBootstrap,old,null,secret,false,"ENC(synthetic-invalid-location)");
        run(config,next,null,secret,false,encrypted,fresh);
        assertThat(control.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?",Integer.class,schema)).isZero();
        run(config,old,null,secret,true,encrypted,fresh);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema,user,password));
        int changes=jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog",Integer.class);
        jdbc.execute("CREATE TABLE deployment_sentinel(marker text)");jdbc.update("INSERT INTO deployment_sentinel VALUES ('preserved')");
        run(config,old,fresh,secret,false,encrypted,fresh);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog",Integer.class)).isEqualTo(changes);
        run(config,next,fresh,secret,true,encrypted,fresh);
        run(config,old,null,secret,true,encrypted,fresh);
        assertThat(jdbc.queryForObject("SELECT marker FROM deployment_sentinel",String.class)).isEqualTo("preserved");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog",Integer.class)).isEqualTo(changes);
    }
    private void run(Path config,byte[] key,String override,String secret,boolean success,String... ciphers)throws Exception {
        String keyValue=Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        Path arguments=directory.resolve("java.args"),log=directory.resolve("boot-"+UUID.randomUUID()+".log");
        String cp=System.getProperty("deployment.test.classpath");assertThat(cp).isNotBlank();
        Files.writeString(arguments,"-cp\n\""+cp.replace("\\","/")+"\"\n"+Probe.class.getName()+"\n--spring.config.additional-location="+config.toUri()+"\n",StandardCharsets.UTF_8);
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"@"+arguments).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("AIHUB_CONFIG_MASTER_KEY",keyValue);builder.environment().remove("AIHUB_CONFIG_MASTER_KEY_FILE");builder.environment().put("DEPLOYMENT_TEST_EXPECTED",secret);
        if(override==null)builder.environment().remove("SPRING_DATASOURCE_PASSWORD");else builder.environment().put("SPRING_DATASOURCE_PASSWORD",override);
        Process process=builder.start();if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("시험 기동 시간 초과");}
        String output=Files.readString(log);assertThat(output).doesNotContain(secret,keyValue);for(String cipher:ciphers)assertThat(output).doesNotContain(cipher);
        if(success){assertThat(process.exitValue()).as("안전 기동 로그 %s",output).isZero();assertThat(output).contains("DEPLOYMENT_BOOT_OK");}
        else{assertThat(process.exitValue()).isNotZero();assertThat(output).contains("설정 비밀 검증 실패").doesNotContain("HikariPool-1 - Start","Running Changeset:","DEPLOYMENT_BOOT_OK");}
    }
    public static final class Probe {
        public static void main(String[] args)throws Exception {
            var arguments=new ArrayList<>(List.of(args));arguments.addAll(List.of("--spring.profiles.active=system-auth-test","--server.port=0","--app.auth.session.origin=http://127.0.0.1:18040","--app.auth.session.local-http=true","--app.auth.session.epoch=00000000-0000-0000-0000-000000000302","--app.auth.session.store-instance=deployment302-test","--app.logging.sql.enabled=false","--logging.level.root=WARN"));
            try(var context=new SpringApplication(AiAgentApplication.class).run(arguments.toArray(String[]::new))) {
                String expected=System.getenv("DEPLOYMENT_TEST_EXPECTED");
                if(!expected.equals(context.getEnvironment().getProperty("spring.datasource.password")))throw new IllegalStateException("설정 선택 시험 불일치");
                try(var connection=context.getBean(DataSource.class).getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")){if(!rows.next()||rows.getInt(1)<1)throw new IllegalStateException("실제 스키마 기동 실패");}
                System.out.println("DEPLOYMENT_BOOT_OK");
            }
        }
    }
}
