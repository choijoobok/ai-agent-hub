// @feat FEAT-010
package com.aiagent.admin.chathelp;
import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.support.content.SafeContentRenderer;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
@Configuration
public class ChatHelpConfiguration {
    private static final String HANDLER="com.aiagent.admin.api.chathelp.ChatHelpController#";
    @Bean SafeContentRenderer safeContentRenderer(){return new SafeContentRenderer();}
    @Bean ChatHelpService chatHelpService(DataSource source,EntityManagerFactory emf,ManagedIds ids,SafeContentRenderer renderer){return new ChatHelpService(source,SharedEntityManagerCreator.createSharedEntityManager(emf),ids,renderer);}
    @Bean ManagedRegistry.Operation chatHelpSave(ChatHelpService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-141",ChatHelpService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean InstalledAdminRoutes chatHelpReads(){return new InstalledAdminRoutes(List.of(
        new InstalledAdminRoutes.Route("API-139","POST",ChatHelpService.BASE+"/getList",HANDLER+"getList","READ"),
        new InstalledAdminRoutes.Route("API-140","POST",ChatHelpService.BASE+"/getOne",HANDLER+"getOne","READ"),
        new InstalledAdminRoutes.Route("API-144","POST",ChatHelpService.BASE+"/getPreview",HANDLER+"getPreview","READ")));}
}
