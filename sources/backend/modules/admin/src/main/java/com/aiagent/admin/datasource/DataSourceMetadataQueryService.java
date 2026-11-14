// @feat FEAT-002 FEAT-003
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.core.deployment.DataSourceMetadata;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class DataSourceMetadataQueryService implements DataSourceMetadata {
    private final DataSourceQueryService query;private final DataSourceService owner;private final ObjectProvider<DataSourceCatalogScope> scopes;
    public DataSourceMetadataQueryService(DataSourceQueryService query,DataSourceService owner,ObjectProvider<DataSourceCatalogScope> scopes){this.query=query;this.owner=owner;this.scopes=scopes;}
    private Map<String,String> selected(Set<String> ids){if(ids==null||ids.isEmpty()||ids.size()>1000)throw ManagedErrorCode.VALIDATION.failure();ids.forEach(ManagedCommand::positive);var scope=scopes.getIfUnique();if(scope==null)throw ManagedErrorCode.UNAVAILABLE.failure();var selected=scope.authorizedDataSources(Set.copyOf(ids));if(selected==null||!selected.keySet().equals(ids)||selected.values().stream().anyMatch(Objects::isNull))throw ManagedErrorCode.FORBIDDEN.failure();return selected;}
    public List<Context> readSelected(Set<String> ids){var selected=selected(ids);var result=new ArrayList<Context>();
        for(String id:new TreeSet<>(selected.values())){var row=query.row(query.root(id));if(!Boolean.FALSE.equals(row.configurationMismatch())||!row.connectionStatus().equals("AVAILABLE"))throw ManagedErrorCode.UNAVAILABLE.failure();
            var definition=DataSourceDefinition.parse(owner.values(id));query.requireRevision(id,row.managementVersion());result.add(new Context(id,row.managementVersion(),row.dbmsCode(),row.dbmsVersion(),row.sourceType(),row.timeZoneId(),row.dataBasis(),definition.guides().stream().map(g->new Guide(g.guideId(),g.title(),g.description())).toList()));}
        return List.copyOf(result);
    }
    public void requireSqlUsable(String catalog){var selection=selected(Set.of(catalog));var contexts=readSelected(Set.of(catalog));var context=contexts.getFirst();if(!selection.get(catalog).equals(context.dataSourceId()))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();scopes.getObject().requireSqlSupport(context.dataSourceId(),context.dbmsCode(),context.dbmsVersion());}
}
