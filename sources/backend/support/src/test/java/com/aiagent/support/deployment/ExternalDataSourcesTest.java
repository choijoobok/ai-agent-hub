// @feat FEAT-002
package com.aiagent.support.deployment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.core.deployment.ConnectionRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
class ExternalDataSourcesTest {
    private StandardEnvironment env(Map<String,Object> values){var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("fixture",values));return env;}
    @Test void rejectsWholeMalformedInputAndSeparatesUnsupportedConnectionObservation(){
        for(String db:List.of("postgresql","mysql","mariadb","oracle","sqlserver"))assertThat(ExternalDataSources.dbms("jdbc:"+db+":fixture")).isEqualTo(db.toUpperCase(Locale.ROOT));
        assertThat(ExternalDataSources.dbms("jdbc:unknown:fixture")).isEqualTo("UNKNOWN");
        var registry=mock(ConnectionRegistry.class);when(registry.generation()).thenReturn(new ConnectionRegistry.Generation(UUID.randomUUID(),"fixture","1",java.time.Instant.EPOCH));
        var input=new HashMap<String,Object>();input.put("app.external-datasources.good.alias","조회");input.put("app.external-datasources.good.registry-key","good");input.put("app.external-datasources.bad.alias","잘못된 항목");
        assertThatThrownBy(()->new ExternalDataSources(env(input),registry)).isInstanceOf(RuntimeException.class);
        input.remove("app.external-datasources.bad.alias");input.put("app.external-datasources.good.parser-id","위조");assertThatThrownBy(()->new ExternalDataSources(env(input),registry)).isInstanceOf(RuntimeException.class);
        input.remove("app.external-datasources.good.parser-id");when(registry.withResolvedConnection(anyString(),any(),any())).thenThrow(new ConnectionRegistry.Rejected(ConnectionRegistry.Status.MISSING));
        var loaded=new ExternalDataSources(env(input),registry);assertThat(loaded.entries().get("good").status()).isEqualTo("MISSING");assertThat(loaded.entries().get("good").dbmsCode()).isEqualTo("UNKNOWN");assertThat(loaded.toString()).doesNotContain("jdbc:");
    }
}
