// @feat FEAT-003
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.core.deployment.*;
import com.aiagent.support.deployment.ExternalDataSources;
import java.util.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
@Configuration
public class CatalogConfiguration {
 private static final String HANDLER="com.aiagent.admin.api.catalog.CatalogController#";
 @Bean CatalogService catalogService(DataSource db,ManagedIds ids,ExternalDataSources loaded,ConfigurationDeploymentService deployment,CatalogSource source,CatalogAccess access,EntityManagerFactory ready){return new CatalogService(db,ids,loaded,deployment,source,access);}
 @Bean ManagedRegistry.Operation catalogCreate(CatalogService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-023",CatalogService.BASE+"/saveCreate",HANDLER+"saveCreate",false,owner,journal);}
 @Bean ManagedRegistry.Operation catalogChange(CatalogService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-024",CatalogService.BASE+"/saveChange",HANDLER+"saveChange",false,owner,journal);}
 @Bean ManagedResultRoute catalogResult(){return new ManagedResultRoute("API-034",CatalogService.BASE+"/getCommandResult",HANDLER+"getCommandResult","FEAT-003");}
 @Bean static InstalledAdminRoutes catalogReads(){var m=new LinkedHashMap<String,String>();m.put("API-019","getList");m.put("API-020","getOne");m.put("API-022","getRevision");m.put("API-026","getReferences");m.put("API-027","getRelatedJoins");m.put("API-028","getResolvedObject");m.put("API-029","getStructure");m.put("API-035","getValidation");m.put("API-036","getReferenceCandidates");return new InstalledAdminRoutes(m.entrySet().stream().map(e->new InstalledAdminRoutes.Route(e.getKey(),"POST",CatalogService.BASE+"/"+e.getValue(),HANDLER+e.getValue(),"READ")).toList());}
 @Bean ConnectionRegistry.Consumer catalogMetadataConsumer(CatalogAccess access){return new ConnectionRegistry.Consumer(){public String id(){return "catalog-metadata";}public Set<ConnectionRegistry.Kind> kinds(){return Set.of(ConnectionRegistry.Kind.DATASOURCE);}public void authorize(ConnectionRegistry.Kind kind,ConnectionRegistry.Operation operation){access.require(com.aiagent.domain.authz.ActionType.READ);}};}
}
