// @feat FEAT-002 FEAT-003
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.aiagent.admin.datasource.DataSourceReferencePort.Reference.*;
@Service @Transactional(readOnly=true)
public class DataSourceReferenceService {
    public record Projection(String state,List<Map<String,Object>> rows){}
    private final JdbcTemplate jdbc;private final List<DataSourceReferencePort> ports;
    public DataSourceReferenceService(DataSource source,ObjectProvider<DataSourceReferencePort> ports){jdbc=new JdbcTemplate(source);this.ports=ports.orderedStream().toList();}
    public DataSourceReferencePort.Reference references(String id){
        try {if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_IDENTITY WHERE TARGET_KIND='DATA_SOURCE' AND TARGET_ID=?",Long.class,ManagedCommand.positive(id))>0)return PRESENT;
            return aggregate(id,null,false);
        }catch(DataAccessException failure){return UNKNOWN;}
    }
    public DataSourceReferencePort.Reference groups(String id,String group,boolean activeOnly){try{return aggregate(id,group,activeOnly);}catch(DataAccessException failure){return UNKNOWN;}}
    private DataSourceReferencePort.Reference aggregate(String id,String group,boolean activeOnly){
        if(!covered())return UNKNOWN;var result=ABSENT;
        for(var p:ports){var value=Objects.requireNonNull(group==null?p.references(id):p.groupReferences(id,group,activeOnly));if(value==PRESENT)return PRESENT;if(value==UNKNOWN)result=UNKNOWN;}return result;
    }
    public Projection read(String id){
        try {if(!covered())return new Projection("UNAVAILABLE",List.of());return new Projection(ports.isEmpty()?"NOT_CONNECTED":"READY",ports.stream().flatMap(p->p.readableCatalogs(id).stream()).toList());}
        catch(DataAccessException failure){return new Projection("UNAVAILABLE",List.of());}
    }
    private boolean covered(){return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->{
        String schema=c.getSchema();if(schema==null||schema.isBlank())return false;
        var owned=new HashSet<>(Set.of("AI_ADMIN_DATA_SOURCE","AI_ADMIN_DATA_SOURCE_HIST","AI_ADMIN_DATA_SOURCE_GROUP","AI_ADMIN_DATA_SOURCE_GROUP_HIST","AI_ADMIN_QUERY_GUIDE","AI_ADMIN_QUERY_GUIDE_HIST"));
        for(var p:ports)for(String table:p.tables())if(!owned.add(table))throw new IllegalStateException("데이터소스 참조 소유자가 중복됐다");
        try(var rows=c.getMetaData().getColumns(c.getCatalog(),schema,null,null)){while(rows.next()){
            String table=rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT),column=rows.getString("COLUMN_NAME").toUpperCase(Locale.ROOT);
            if((column.equals("DATA_SOURCE_ID")||column.equals("EXECUTION_DATA_SOURCE_ID"))&&!owned.contains(table))return false;
        }}return true;
    }));}
}
