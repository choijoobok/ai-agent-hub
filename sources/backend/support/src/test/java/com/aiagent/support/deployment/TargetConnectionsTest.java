// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.deployment.ConnectionRegistry;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.env.*;

class TargetConnectionsTest {
    @Test @DisplayName("TC-00120 TC-00124 최종 설정 바인딩·유틸 검증·비밀 제외 지문과 설정 불변성")
    void immutableDefinitionAndFingerprint() {
        var settings=settings();var first=load(settings);
        assertThat(first.systems().get("orders").connections().get("main").display().baseUrl()).isEqualTo("https://orders.invalid/api");
        var reordered=new LinkedHashMap<String,Object>();settings.entrySet().stream().sorted(Map.Entry.<String,Object>comparingByKey().reversed()).forEach(e->reordered.put(e.getKey(),e.getValue()));
        assertThat(load(reordered).settingsId()).isEqualTo(first.settingsId());
        reordered.put("app.deployment.connections.orders.endpoint","https://other.invalid/api");
        assertThat(load(reordered).settingsId()).isNotEqualTo(first.settingsId());
        assertThat(first.systems().get("orders").name()).isEqualTo("주문");
        var secret=settings();secret.put("app.secrets.allow-plaintext",true);
        secret.put("app.deployment.connections.orders.authentication","BEARER");
        secret.put("app.deployment.connections.orders.secret-property","app.fixture.token");secret.put("app.fixture.token","first-secret");
        String secretIndependent=load(secret).settingsId();secret.put("app.fixture.token","second-secret");
        assertThat(load(secret).settingsId()).isEqualTo(secretIndependent);
        var missing=settings();missing.put("app.deployment.target-systems.orders.connections.main.invoker-bean","unknown");
        assertThatThrownBy(()->load(missing)).isInstanceOf(ConnectionRegistry.Rejected.class);
        var unsafe=settings();unsafe.put("app.deployment.target-systems.orders.connections.main.test.path","https://outside.invalid");
        assertThatThrownBy(()->load(unsafe)).isInstanceOf(ConnectionRegistry.Rejected.class);
        var misspelled=settings();misspelled.put("app.deployment.target-systems.orders.connections.main.unrecognized-option","true");
        assertThatThrownBy(()->load(misspelled)).isInstanceOf(ConnectionRegistry.Rejected.class);
    }
    static Map<String,Object> settings(){return new HashMap<>(Map.ofEntries(
        Map.entry("app.deployment.id","fixture"),Map.entry("app.secrets.environment","test"),
        Map.entry("app.deployment.connections.orders.kind","BUSINESS_API"),Map.entry("app.deployment.connections.orders.display-name","기본"),
        Map.entry("app.deployment.connections.orders.endpoint","https://orders.invalid/api"),Map.entry("app.deployment.connections.orders.authentication","NONE"),
        Map.entry("app.deployment.target-systems.orders.name","주문"),Map.entry("app.deployment.target-systems.orders.connections.main.registry-key","orders"),
        Map.entry("app.deployment.target-systems.orders.connections.main.invoker-bean","restJsonInvoker"),
        Map.entry("app.deployment.target-systems.orders.connections.main.public-path-segments[0]","api"),
        Map.entry("app.deployment.target-systems.orders.connections.main.test.method","HEAD"),Map.entry("app.deployment.target-systems.orders.connections.main.test.path","/health"),
        Map.entry("app.deployment.target-systems.orders.connections.main.test.timeout-ms",2000),Map.entry("app.deployment.target-systems.orders.connections.main.test.success-statuses[0]",200)));}
    private static TargetConnections load(Map<String,Object> values){
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("fixture",values));
        var registry=new DeploymentConnections(env,Clock.systemUTC(),List.of(TargetConnectionConfiguration.consumer()));
        return new TargetConnections(env,registry,new BusinessApiInvokers(Map.of("restJsonInvoker",new RestJsonInvoker())));
    }
}
