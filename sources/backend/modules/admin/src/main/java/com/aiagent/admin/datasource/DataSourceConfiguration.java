// @feat FEAT-002
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.support.deployment.ExternalDataSources;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
@Configuration
public class DataSourceConfiguration {
    private static final String HANDLER="com.aiagent.admin.api.datasource.DataSourceController#";
    @Bean DataSourceService dataSourceService(DataSource source,ManagedIds ids,DataSourceReferenceService refs,ExternalDataSources loaded,ConfigurationDeploymentService deployment){return new DataSourceService(source,ids,refs,loaded,deployment);}
    @Bean ConfigurationLifecycle dataSourceLifecycle(ExternalDataSources loaded,ConfigurationDeploymentService deployment,DataSourceSyncService sync){return new ConfigurationLifecycle(loaded,deployment,()->sync.synchronize(loaded));}
    @Bean ManagedRegistry.Operation dataSourceSave(DataSourceService owner,AssetJournal journal){return new ManagedRegistry.Operation("API-014",DataSourceService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean ManagedRegistry.Operation dataSourceCleanup(DataSourceService owner,AssetJournal journal){return new ManagedRegistry.Operation("API-076",DataSourceService.BASE+"/deleteConfigurationMismatches",HANDLER+"deleteConfigurationMismatches",false,owner,journal);}
    @Bean ManagedResultRoute dataSourceResult(){return new ManagedResultRoute("API-018",DataSourceService.BASE+"/getCommandResult",HANDLER+"getCommandResult","FEAT-002");}
    @Bean static InstalledAdminRoutes dataSourceReads(){var actions=new LinkedHashMap<String,String>();actions.put("API-010","getList");actions.put("API-011","getOne");actions.put("API-012","getConnectionCandidates");actions.put("API-013","getReferences");actions.put("API-062","getTimezoneList");actions.put("API-072","getQueryGuides");actions.put("API-075","getConfigurationCleanupPreview");return new InstalledAdminRoutes(actions.entrySet().stream().map(e->new InstalledAdminRoutes.Route(e.getKey(),"POST",DataSourceService.BASE+"/"+e.getValue(),HANDLER+e.getValue(),"READ")).toList());}
}
