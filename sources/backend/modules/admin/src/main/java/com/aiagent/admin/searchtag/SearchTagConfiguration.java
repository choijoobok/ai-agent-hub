// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
@Configuration
public class SearchTagConfiguration {
    private static final String HANDLER="com.aiagent.admin.api.searchtag.SearchTagController#";
    @Bean SearchTagService searchTagService(DataSource source,ManagedIds ids,ManagedLocks locks,SearchTagReferenceQueryService references){return new SearchTagService(source,ids,locks,references);}
    @Bean ManagedRegistry.Operation searchTagSave(SearchTagService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-112",SearchTagService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean ManagedRegistry.Operation searchTagRemove(SearchTagService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-113",SearchTagService.BASE+"/remove",HANDLER+"remove",true,owner,journal);}
    @Bean InstalledAdminRoutes searchTagReads(){return new InstalledAdminRoutes(List.of(
        new InstalledAdminRoutes.Route("API-110","POST",SearchTagService.BASE+"/getList",HANDLER+"getList","READ"),
        new InstalledAdminRoutes.Route("API-111","POST",SearchTagService.BASE+"/getOne",HANDLER+"getOne","READ"),
        new InstalledAdminRoutes.Route("API-114","POST",SearchTagService.BASE+"/getReferences",HANDLER+"getReferences","READ")));}
}
