// @feat FEAT-001 FEAT-005
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import static com.aiagent.admin.targetsystem.TargetReferencePort.*;

/** 생산자가 없는 미래 테이블과 실패한 참조 검사를 정상 0건으로 취급하지 않는다. */
@Service
public class TargetReferenceService {
    public record Projection(String state,List<Api> rows) {}
    private final JdbcTemplate jdbc;
    private final List<TargetReferencePort> providers;
    public TargetReferenceService(DataSource source,ObjectProvider<TargetReferencePort> providers){jdbc=new JdbcTemplate(source);this.providers=providers.orderedStream().toList();}
    public Reference reference(String systemId,String connectionId){
        try {
            if(connectionId==null&&jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_IDENTITY WHERE TARGET_KIND='SYSTEM' AND TARGET_ID=?",Long.class,ManagedCommand.positive(systemId))>0)return Reference.PRESENT;
            if(!covered())return Reference.UNKNOWN;
            var result=Reference.ABSENT;
            for(var provider:providers){var found=Objects.requireNonNull(provider.reference(systemId,connectionId));if(found==Reference.PRESENT)return found;if(found==Reference.UNKNOWN)result=found;}
            return result;
        }catch(DataAccessException failure){return Reference.UNKNOWN;}
    }
    public Projection read(String id){
        try {
            if(!covered())return new Projection("UNAVAILABLE",List.of());
            return new Projection(providers.isEmpty()?"NOT_CONNECTED":"READY",providers.stream().flatMap(p->p.readableApis(id).stream()).toList());
        }catch(DataAccessException failure){return new Projection("UNAVAILABLE",List.of());}
    }
    private boolean covered(){return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->{
        String schema=connection.getSchema();if(schema==null||schema.isBlank())return false;
        var covered=new HashSet<>(Set.of("AI_ADMIN_TARGET_SYSTEM","AI_ADMIN_TARGET_SYSTEM_HIST","AI_ADMIN_TARGET_SYSTEM_CONN","AI_ADMIN_TARGET_SYSTEM_CONN_HIST"));
        for(var provider:providers)for(String table:provider.tables())if(!covered.add(table))throw new IllegalStateException("대상 참조 테이블의 소유자가 중복됐다");
        // 누락·부분 설치도 확인 불가. 새로운 참조 테이블은 소유 포트가 설치되어야 한다.
        try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),schema,null,null)){
            while(columns.next()){
                String table=columns.getString("TABLE_NAME").toUpperCase(Locale.ROOT),column=columns.getString("COLUMN_NAME").toUpperCase(Locale.ROOT);
                if(Set.of("TARGET_SYSTEM_ID","TARGET_CONNECTION_ID","CONNECTION_RECORD_ID").contains(column)&&!covered.contains(table))return false;
                if(table.startsWith("AI_ADMIN_BUSINESS_API")&&!covered.contains(table))return false;
            }
        }
        return true;
    }));}
}
