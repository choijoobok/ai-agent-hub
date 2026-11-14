// @feat FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.*;
import org.springframework.core.env.*;

class SecretEnvironmentTest {
    record InvalidPassword(String password){InvalidPassword{throw new IllegalArgumentException(password);}}
    private final byte[] key=new byte[32];
    private String encrypt(String property,String value){return SecretEnvelope.encrypt(key,new SecretEnvelope.Binding("fixture","test",property),value.toCharArray());}
    private StandardEnvironment environment(Map<String,Object> high) {
        var env=new StandardEnvironment();env.getPropertySources().remove("systemEnvironment");env.getPropertySources().remove("systemProperties");
        env.getPropertySources().addLast(new MapPropertySource("settings",Map.of("app.secrets.tenant","fixture","app.secrets.environment","test","app.secrets.properties[0]","app.integrations[0].access-token")));
        env.getPropertySources().addFirst(new MapPropertySource("high",high));return env;
    }
    private String trace(Throwable failure){var out=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(out));return out.toString();}
    private void install(StandardEnvironment env){SecretEnvironment.install(env,new SpringApplication(Object.class),()->key.clone());}
    @Test @DisplayName("TC-3021 TC-3025 선택 우선순위·파일 Origin·중첩/배열 canonical·원천 스냅샷")
    void selectedOriginAndBinding() {
        String secret="synthetic password";var high=new HashMap<String,Object>();high.put("spring.datasource.password",encrypt("spring.datasource.password",secret));
        var env=environment(high);var origin=new Origin(){public String toString(){return "fixture.yml:3";}};
        env.getPropertySources().replace("high",new OriginTrackedMapPropertySource("high",Map.of("spring.datasource.password",OriginTrackedValue.of(high.get("spring.datasource.password"),origin),"app.integrations[0].access-token",encrypt("app.integrations[0].access-token","nested"))));
        env.getPropertySources().addLast(new MapPropertySource("low",Map.of("spring.datasource.password",encrypt("spring.datasource.password","low"))));install(env);
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo(secret);
        assertThat(SecretEnvironment.binder(env).bind("spring.datasource.password",String.class).get()).isEqualTo(secret);
        assertThat(SecretEnvironment.binder(env).bind("app.integrations[0].access-token",String.class).get()).isEqualTo("nested");
        assertThat(OriginLookup.getOrigin(env.getPropertySources().get("high"),"spring.datasource.password").toString()).isEqualTo("fixture.yml:3");
        assertThat(env.getPropertySources().get("high").getSource().toString()).doesNotContain(secret).doesNotContain("ENC(");
        assertThat(env.getPropertySources().get("high").getProperty("spring.datasource.password").toString()).isEqualTo("[보호된 설정]");
        assertThatThrownBy(()->SecretEnvironment.binder(env).bind("spring.datasource",Bindable.of(InvalidPassword.class))).satisfies(failure->assertThat(trace(failure)).doesNotContain(secret));
    }
    @Test @DisplayName("TC-3022 TC-3025 잘못된 선택값 하위 폴백0·미사용 지연·평문/등록 밖 비밀 거부")
    void selectedFailureIsClosed() {
        var env=environment(Map.of("spring.datasource.password","ENC(v1.A256GCM.bad)","spring.mail.password","ENC(unsupported)","app.unregistered","ENC(unknown)"));
        env.getPropertySources().addLast(new MapPropertySource("low",Map.of("spring.datasource.password",encrypt("spring.datasource.password","low"))));install(env);
        assertThat(env.getProperty("app.secrets.tenant")).isEqualTo("fixture");
        assertThatThrownBy(()->env.getProperty("spring.datasource.password")).hasStackTraceContaining("설정 비밀 검증 실패").satisfies(failure->assertThat(trace(failure)).doesNotContain("ENC(v1.A256GCM.bad)"));
        assertThatThrownBy(()->SecretEnvironment.binder(env).bind("spring.datasource.password",String.class)).hasStackTraceContaining("설정 비밀 검증 실패").satisfies(failure->assertThat(trace(failure)).doesNotContain("ENC(v1.A256GCM.bad)"));
        assertThatThrownBy(()->env.getProperty("spring.mail.password")).hasStackTraceContaining("설정 비밀 검증 실패");
        assertThatThrownBy(()->env.getProperty("app.unregistered")).hasStackTraceContaining("UNREGISTERED");
        var plain=environment(Map.of("spring.datasource.password","unsafe-synthetic"));install(plain);
        assertThatThrownBy(()->plain.getProperty("spring.datasource.password")).hasStackTraceContaining("PLAINTEXT").satisfies(failure->assertThat(trace(failure)).doesNotContain("unsafe-synthetic"));
    }
    @Test @DisplayName("TC-3021 TC-3023 환경 변수 mapper와 entrySet도 보호·부트스트랩 암호화 거부")
    void environmentMapperAndBootstrap() {
        var env=environment(Map.of("spring.datasource.password",encrypt("spring.datasource.password","lower")));
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("systemEnvironment",Map.of("SPRING_DATASOURCE_PASSWORD",encrypt("spring.datasource.password","selected"),SecretEnvironment.MASTER,"synthetic-key-not-shown","APP_INTEGRATIONS_0_ACCESSTOKEN",encrypt("app.integrations[0].access-token","environment-nested"))));
        install(env);assertThat(SecretEnvironment.binder(env).bind("spring.datasource.password",String.class).get()).isEqualTo("selected");assertThat(env.getProperty("spring.datasource.password")).isEqualTo("selected");
        assertThat(SecretEnvironment.binder(env).bind("app.integrations[0].access-token",String.class).get()).isEqualTo("environment-nested");
        var analysis=new SecretFailureAnalyzer().analyze(new IllegalArgumentException("bad ENC(synthetic-cipher)"));
        assertThat(analysis.getDescription()+trace(analysis.getCause())).doesNotContain("ENC(synthetic-cipher)");
        var source=env.getPropertySources().get("systemEnvironment");assertThat(source.getSource().toString()).doesNotContain("synthetic-key-not-shown").doesNotContain("ENC(");
        assertThat(((Map<?,?>)source.getSource()).entrySet().toString()).doesNotContain("synthetic-key-not-shown").doesNotContain("ENC(");
        var deferred=SecretEnvironment.snapshotKey(Map.of(SecretEnvironment.MASTER_FILE,"\0"));
        assertThatThrownBy(deferred::get).isInstanceOf(SecretFailure.class).hasMessageContaining("KEY_INPUT");
        assertThatThrownBy(()->SecretEnvironment.snapshotKey(Map.of(SecretEnvironment.MASTER,"key",SecretEnvironment.MASTER_FILE,"file")).get()).isInstanceOf(SecretFailure.class).hasMessageContaining("KEY_SOURCE");
        var bad=environment(Map.of("spring.config.location","ENC(bootstrap)"));assertThatThrownBy(()->SecretBootstrapGuard.check(bad)).isInstanceOf(SecretFailure.class).hasMessageNotContaining("ENC(");
    }
}
