// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import com.aiagent.support.time.BusinessCalendar;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

@Configuration
public class QuotaGradeConfiguration {
    private static final String HANDLER="com.aiagent.admin.api.quota.QuotaGradeController#";
    @Bean QuotaGradeGuard quotaGradeGuard(DataSource source,BusinessCalendar calendar,ObjectProvider<QuotaGradeUsage> usages){return new QuotaGradeGuard(source,calendar,usages);}
    @Bean QuotaGradeService quotaGradeService(DataSource source,ManagedIds ids,QuotaGradeGuard guard,ManagedLocks locks,jakarta.persistence.EntityManagerFactory ready){return new QuotaGradeService(source,ids,guard,locks);}
    @Bean ManagedRegistry.Operation quotaGradeSave(QuotaGradeService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-083",QuotaGradeService.BASE+"/save",HANDLER+"save",false,owner,journal);}
    @Bean ManagedRegistry.Operation quotaGradeRemove(QuotaGradeService owner,JdbcManagedJournal journal){return new ManagedRegistry.Operation("API-143",QuotaGradeService.BASE+"/remove",HANDLER+"remove",true,owner,journal);}
    @Bean InstalledAdminRoutes quotaGradeReads(){return new InstalledAdminRoutes(List.of(
            new InstalledAdminRoutes.Route("API-097","POST",QuotaGradeService.BASE+"/getList",HANDLER+"getList","READ"),
            new InstalledAdminRoutes.Route("API-098","POST",QuotaGradeService.BASE+"/getOne",HANDLER+"getOne","READ")));}
}
