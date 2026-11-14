// @feat FEAT-002 FEAT-302
package com.aiagent.support.deployment;
import com.aiagent.core.deployment.ConnectionRegistry;
import java.util.Set;
import org.springframework.context.annotation.*;
@Configuration
public class DataSourceConnectionConfiguration {
    @Bean static ConnectionRegistry.Consumer dataSourceSettingsConsumer(){return new ConnectionRegistry.Consumer(){
        public String id(){return "datasource-settings";}public Set<ConnectionRegistry.Kind> kinds(){return Set.of(ConnectionRegistry.Kind.DATASOURCE);}
        public void authorize(ConnectionRegistry.Kind kind,ConnectionRegistry.Operation operation){}
    };}
}
