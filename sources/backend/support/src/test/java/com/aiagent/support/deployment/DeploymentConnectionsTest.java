// @feat FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.deployment.ConnectionRegistry.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.*;

class DeploymentConnectionsTest {
    @Test @DisplayName("TC-3021 TC-3025 고정된 연결·세대·호출자/현재 권한·콜백 수명·선택 비밀 오류")
    void immutableAuthorizedConnection() {
        var allowed=new AtomicBoolean(true);byte[] key=new byte[32];
        String encrypted=SecretEnvelope.encrypt(key,new SecretEnvelope.Binding("fixture","test","app.model.token"),"token-value".toCharArray());
        var settings=new HashMap<String,Object>(Map.of("app.secrets.tenant","fixture","app.secrets.environment","test","app.secrets.properties[0]","app.model.token","app.model.token",encrypted));
        settings.putAll(Map.of("app.deployment.connections.primary.kind","MODEL","app.deployment.connections.primary.display-name","주 연결","app.deployment.connections.primary.endpoint","https://model.invalid/v1","app.deployment.connections.primary.authentication","BEARER","app.deployment.connections.primary.secret-property","app.model.token"));
        settings.putAll(Map.of("app.deployment.connections.optional.kind","BUSINESS_API","app.deployment.connections.optional.display-name","선택 오류","app.deployment.connections.optional.endpoint","https://unused.invalid","app.deployment.connections.optional.authentication","BASIC","app.deployment.connections.optional.secret-property","spring.mail.password","spring.mail.password","ENC(bad)"));
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("test",settings));SecretEnvironment.install(env,new SpringApplication(Object.class),()->key.clone());
        Consumer owner=new Consumer(){public String id(){return "fixture";}public Set<Kind> kinds(){return Set.of(Kind.MODEL,Kind.BUSINESS_API);}public void authorize(Kind kind,Operation op){if(!allowed.get())throw new Rejected(Status.FORBIDDEN);}};
        var registry=new DeploymentConnections(env,Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"),ZoneOffset.UTC),List.of(owner));
        var ref=new Ref(Kind.MODEL,"primary",registry.generation().id());
        assertThat(registry.listPublicKinds("fixture").toString()).doesNotContain("token-value").doesNotContain("https:").doesNotContain("ENC(");
        assertThat(registry.describe("fixture",ref).status()).isEqualTo(Status.SECRET_NOT_VERIFIED);
        var retained=new AtomicReference<Resolved>();var chars=new AtomicReference<char[]>();
        String endpoint=registry.withResolvedConnection("fixture",ref,c->{retained.set(c);c.withSecret(value->{chars.set(value);assertThat(value).isEqualTo("token-value".toCharArray());return null;});return c.endpoint();});
        assertThat(endpoint).isEqualTo("https://model.invalid/v1");assertThat(chars.get()).containsOnly('\0');assertThatThrownBy(()->retained.get().endpoint()).isInstanceOf(Rejected.class);
        env.getPropertySources().addFirst(new MapPropertySource("replacement",Map.of("app.deployment.connections.primary.endpoint","https://changed.invalid","app.model.token","changed")));
        assertThat(registry.withResolvedConnection("fixture",ref,Resolved::endpoint)).isEqualTo(endpoint);
        assertThatThrownBy(()->registry.describe("unknown",ref)).isInstanceOf(Rejected.class);
        assertThatThrownBy(()->registry.withResolvedConnection("fixture",new Ref(Kind.MODEL,"primary",UUID.randomUUID()),Resolved::endpoint)).isInstanceOf(Rejected.class);
        assertThatThrownBy(()->registry.withResolvedConnection("fixture",new Ref(Kind.BUSINESS_API,"optional",registry.generation().id()),Resolved::endpoint)).isInstanceOf(Rejected.class).hasMessageContaining("SECRET_UNAVAILABLE");
        allowed.set(false);assertThatThrownBy(()->registry.describe("fixture",ref)).isInstanceOf(Rejected.class);
    }
}
