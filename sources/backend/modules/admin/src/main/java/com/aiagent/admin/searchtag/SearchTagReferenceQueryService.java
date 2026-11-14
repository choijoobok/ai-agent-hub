// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.ManagedErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.aiagent.admin.searchtag.SearchTagReferences.*;
@Service @Transactional(readOnly=true)
public class SearchTagReferenceQueryService {
    private final JdbcTemplate jdbc;
    private final SearchTagReferences owner;
    public SearchTagReferenceQueryService(DataSource source,ObjectProvider<SearchTagReferences> providers) {
        jdbc=new JdbcTemplate(source);var installed=providers.orderedStream().toList();
        if(installed.size()>1)throw new IllegalStateException("태그 참조 소유자가 중복됐다");
        owner=installed.isEmpty()?null:installed.getFirst();
    }
    public Reference reference(String id) {
        try {return owner==null?(uninstalled()?Reference.ABSENT:Reference.UNKNOWN):Objects.requireNonNull(owner.references(id));}
        catch(DataAccessException failure){return Reference.UNKNOWN;}
    }
    public Projection read(String id) {
        if(owner==null)return new Projection(reference(id)==Reference.ABSENT?State.NOT_CONNECTED:State.UNAVAILABLE,List.of());
        try {return Objects.requireNonNull(owner.read(id));}
        catch(DataAccessException failure){return new Projection(State.UNAVAILABLE,List.of());}
    }
    public void requireUnreferenced(String id) {
        var found=reference(id);
        if(found==Reference.PRESENT)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        if(found!=Reference.ABSENT)throw ManagedErrorCode.UNAVAILABLE.failure();
    }
    private boolean uninstalled() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->{
            String schema=connection.getSchema();if(schema==null||schema.isBlank())return false;
            var names=new HashSet<String>();
            try(var tables=connection.getMetaData().getTables(connection.getCatalog(),schema,null,null)) {
                while(tables.next())names.add(tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
            }
            if(!names.contains("AI_ADMIN_SEARCH_TAG")||!names.contains("AI_ADMIN_SEARCH_TAG_HIST"))return false;
            return List.of("AI_ADMIN_KNOWLEDGE","AI_ADMIN_KNOWLEDGE_TAG").stream().noneMatch(name->names.contains(name)||names.contains(name+"_HIST"));
        }));
    }
}
