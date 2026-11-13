// @feat FEAT-006
package com.aiagent.admin.group;

import com.aiagent.admin.managed.*;
import com.aiagent.api.auth.v2.InstalledAdminRoutes;
import jakarta.persistence.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

@Configuration
public class UserGroupConfiguration {
    @Bean UserGroupLinks userGroupLinks(ObjectProvider<UserGroupLinkSource> sources){return new UserGroupLinks(sources.orderedStream().toList());}
    @Bean UserGroupService userGroupService(DataSource source,EntityManagerFactory emf,ManagedIds ids,ManagedLocks locks,UserGroupLinks links) {
        return new UserGroupService(source,SharedEntityManagerCreator.createSharedEntityManager(emf),ids,locks,links);
    }
    private static final String HANDLER="com.aiagent.admin.api.group.UserGroupController#";
    @Bean ManagedRegistry.Operation groupSave(UserGroupService owner,JdbcManagedJournal journal) {
        return new ManagedRegistry.Operation("API-085",UserGroupService.BASE+"/save",HANDLER+"save",false,owner,journal);
    }
    @Bean ManagedRegistry.Operation groupRemove(UserGroupService owner,JdbcManagedJournal journal) {
        return new ManagedRegistry.Operation("API-109",UserGroupService.BASE+"/remove",HANDLER+"remove",true,owner,journal);
    }
    @Bean InstalledAdminRoutes groupReads() {
        var actions=List.of("getList","getOne","getMembers","getAssets");var routes=new ArrayList<InstalledAdminRoutes.Route>();
        for(int i=0;i<actions.size();i++)routes.add(new InstalledAdminRoutes.Route("API-"+(105+i),"POST",UserGroupService.BASE+"/"+actions.get(i),HANDLER+actions.get(i),"READ"));
        return new InstalledAdminRoutes(routes);
    }
}
