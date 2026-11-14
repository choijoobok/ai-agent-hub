// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.core.deployment.ConnectionRegistry;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.support.deployment.TargetConnections;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;

@Configuration
public class TargetSystemConfiguration {
    @Bean com.aiagent.admin.shared.configuration.ConfigurationLifecycle targetSystemLifecycle(TargetConnections loaded,ConfigurationDeploymentService deployment,TargetSystemSyncService sync){return new com.aiagent.admin.shared.configuration.ConfigurationLifecycle(loaded,deployment,()->sync.synchronize(loaded));}
    private static final String HANDLER="com.aiagent.admin.api.targetsystem.TargetSystemController#";
    @Bean TargetSystemService targetSystemService(DataSource source,ManagedIds ids,TargetConnections loaded,ConfigurationDeploymentService deployment){return new TargetSystemService(source,ids,loaded,deployment);}
    @Bean AssetJournal targetAssetJournal(DataSource source){return new AssetJournal(source);}
    @Bean ManagedRegistry.Operation targetSystemSave(TargetSystemService owner,AssetJournal journal){return new ManagedRegistry.Operation("API-005",TargetSystemService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean ManagedRegistry.Operation targetSystemCleanup(TargetSystemService owner,AssetJournal journal){return new ManagedRegistry.Operation("API-074",TargetSystemService.BASE+"/deleteConfigurationMismatches",HANDLER+"deleteConfigurationMismatches",false,owner,journal);}
    @Bean ManagedResultRoute targetSystemResult(){return new ManagedResultRoute("API-009",TargetSystemService.BASE+"/getCommandResult",HANDLER+"getCommandResult","FEAT-001");}
    @Bean static InstalledAdminRoutes targetSystemAlerts(){return new InstalledAdminRoutes(List.of(new InstalledAdminRoutes.Route("API-071","POST","/api/admin/service/integration/getConfigurationAlerts","com.aiagent.admin.api.targetsystem.IntegrationAlertController#getConfigurationAlerts","READ")));}
    @Bean static InstalledAdminRoutes targetSystemReads(){return new InstalledAdminRoutes(List.of(
        new InstalledAdminRoutes.Route("API-001","POST",TargetSystemService.BASE+"/getList",HANDLER+"getList","READ"),
        new InstalledAdminRoutes.Route("API-002","POST",TargetSystemService.BASE+"/getOne",HANDLER+"getOne","READ"),
        new InstalledAdminRoutes.Route("API-003","POST",TargetSystemService.BASE+"/getConnections",HANDLER+"getConnections","READ"),
        new InstalledAdminRoutes.Route("API-004","POST",TargetSystemService.BASE+"/getUsedApis",HANDLER+"getUsedApis","READ"),
        new InstalledAdminRoutes.Route("API-073","POST",TargetSystemService.BASE+"/getConfigurationCleanupPreview",HANDLER+"getConfigurationCleanupPreview","READ"),
        new InstalledAdminRoutes.Route("API-070","POST",TargetSystemService.BASE+"/testConnection",HANDLER+"testConnection","EXECUTE")));}
    @Bean static ConnectionRegistry.Consumer targetSystemTestConsumer(TargetSystemAccess access){return new ConnectionRegistry.Consumer(){
        public String id(){return "target-system-test";}
        public Set<ConnectionRegistry.Kind> kinds(){return Set.of(ConnectionRegistry.Kind.BUSINESS_API);}
        public void authorize(ConnectionRegistry.Kind kind,ConnectionRegistry.Operation operation){access.require(ActionType.READ,ActionType.EXECUTE);}
    };}
}
