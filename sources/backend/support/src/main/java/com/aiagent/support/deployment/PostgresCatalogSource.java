// @feat FEAT-003
package com.aiagent.support.deployment;
import com.aiagent.core.deployment.*;
import com.aiagent.support.time.BusinessCalendar;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
/** PostgreSQL exact-name/v1. SQL에는 식별 입력을 연결하지 않고 pg_catalog의 정확 이름을 바인딩한다. */
@Component
public final class PostgresCatalogSource implements CatalogSource {
    public static final String PROFILE="postgresql-exact/v1";
    private final ExternalDataSources loaded;private final ConnectionRegistry registry;private final BusinessCalendar clock;
    public PostgresCatalogSource(ExternalDataSources loaded,ConnectionRegistry registry,BusinessCalendar clock){this.loaded=loaded;this.registry=registry;this.clock=clock;}
    public Identity declared(String key,Names names){var entry=loaded.entries().get(key);if(entry==null||!entry.dbmsCode().equals("POSTGRESQL"))throw new ConnectionRegistry.Rejected(ConnectionRegistry.Status.UNSUPPORTED);return identity(names);}
    public static Identity identity(Names names){
        if(names==null||names.serverName()!=null)throw invalid();
        for(String n:List.of(Objects.requireNonNullElse(names.databaseName(),""),Objects.requireNonNullElse(names.schemaName(),""),Objects.requireNonNullElse(names.objectName(),"")))
            if(n.isEmpty()||n.indexOf(0)>=0||n.getBytes(StandardCharsets.UTF_8).length>63)throw invalid();
        try {var digest=MessageDigest.getInstance("SHA-256");
            for(String part:Arrays.asList(PROFILE,null,names.databaseName(),names.schemaName(),names.objectName())){
                byte[] b=part==null?new byte[0]:part.getBytes(StandardCharsets.UTF_8);digest.update((byte)(part==null?0:1));digest.update(ByteBuffer.allocate(4).putInt(b.length).array());digest.update(b);
            }return new Identity(PROFILE,names,HexFormat.of().formatHex(digest.digest()));
        }catch(NoSuchAlgorithmException failure){throw new IllegalStateException("원천 객체 식별 지문을 만들 수 없다");}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("PostgreSQL의 정확 DB·스키마·객체 이름이 필요하다");}
    public Observation observe(String caller,String key,Names names){
        var id=declared(key,names);var entry=loaded.entries().get(key);
        try {return registry.withResolvedConnection(caller,entry.ref(),r->{
            if(!ExternalDataSources.dbms(r.endpoint()).equals("POSTGRESQL")||!Set.of("NONE","BASIC").contains(r.authentication()))return failure("UNSUPPORTED","CONNECTION_KIND",id);
            return r.withSecret(secret->{var properties=new Properties();if(r.principal()!=null&&!r.principal().isEmpty())properties.setProperty("user",r.principal());if(secret!=null)properties.setProperty("password",new String(secret));properties.setProperty("connectTimeout","3");properties.setProperty("socketTimeout","5");
                try(var connection=DriverManager.getConnection(r.endpoint(),properties)){return read(connection,id,loaded.settingsId(),loaded.bootId().toString(),clock.now());}
                catch(SQLException failure){return failure("UNAVAILABLE","SOURCE_READ_FAILED",id);}finally{properties.clear();}
            });
        });}catch(ConnectionRegistry.Rejected failure){return failure(failure.status()==ConnectionRegistry.Status.FORBIDDEN?"ACCESS_DENIED":"UNAVAILABLE",failure.status().name(),id);}
    }
    private Observation failure(String state,String reason,Identity id){return new Observation(state,reason,id,null,null,null,null,null,loaded.settingsId(),loaded.bootId().toString(),List.of(),List.of(),List.of());}
    /** 트랜잭션/세션은 이 메서드 전용 연결이다. 호출자는 공유 풀 세션을 넘기지 않는다. */
    public static Observation read(Connection c,Identity id,String settings,String boot,Instant observedAt)throws SQLException{
        c.setReadOnly(true);c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try {
            String db,version;try(var q=c.prepareStatement("SELECT current_database(),current_setting('server_version')")){q.setQueryTimeout(3);try(var r=q.executeQuery()){r.next();db=r.getString(1);version=r.getString(2);}}
            if(!id.names().databaseName().equals(db))return result("UNSUPPORTED","CROSS_DATABASE",id,null,null,version,settings,boot,observedAt,List.of(),List.of(),List.of());
            long oid;String kind,type;try(var q=c.prepareStatement("SELECT C.oid,C.relkind,has_schema_privilege(N.oid,'USAGE') AND has_table_privilege(C.oid,'SELECT') FROM pg_catalog.pg_class C JOIN pg_catalog.pg_namespace N ON N.oid=C.relnamespace WHERE N.nspname=? AND C.relname=?")){
                q.setQueryTimeout(3);q.setString(1,id.names().schemaName());q.setString(2,id.names().objectName());try(var r=q.executeQuery()){
                    if(!r.next())return result("UNAVAILABLE","ABSENCE_NOT_PROVEN",id,null,null,version,settings,boot,observedAt,List.of(),List.of(),List.of());
                    oid=r.getLong(1);kind=r.getString(2);if(!r.getBoolean(3))return result("ACCESS_DENIED","SOURCE_PERMISSION",id,null,null,version,settings,boot,observedAt,List.of(),List.of(),List.of());
                }}
            type=switch(kind){case "r","p"->"TABLE";case "v","m"->"VIEW";default->null;};
            if(type==null)return result("UNSUPPORTED","OBJECT_KIND",id,null,null,version,settings,boot,observedAt,List.of(),List.of(),List.of());
            var columns=new ArrayList<Column>();var keys=new ArrayList<Key>();var indexes=new ArrayList<Index>();
            try(var q=c.prepareStatement("SELECT A.attname,A.attnum,pg_catalog.format_type(A.atttypid,NULL),pg_catalog.format_type(A.atttypid,A.atttypmod),A.attnotnull,I.character_maximum_length,I.character_octet_length,I.numeric_precision,I.numeric_scale,pg_catalog.col_description(A.attrelid,A.attnum) FROM pg_catalog.pg_attribute A LEFT JOIN information_schema.columns I ON I.table_catalog=current_database() AND I.table_schema=? AND I.table_name=? AND I.column_name=A.attname WHERE A.attrelid=? AND A.attnum>0 AND NOT A.attisdropped ORDER BY A.attnum")){
                q.setQueryTimeout(3);q.setString(1,id.names().schemaName());q.setString(2,id.names().objectName());q.setLong(3,oid);try(var r=q.executeQuery()){while(r.next())columns.add(new Column(r.getString(1),r.getInt(2),r.getString(3),r.getString(4),type.equals("VIEW")?"UNKNOWN":r.getBoolean(5)?"NO":"YES",number(r,6,Long.class),number(r,7,Long.class),number(r,8,Integer.class),number(r,9,Integer.class),r.getString(10)));}}
            try(var q=c.prepareStatement("SELECT C.contype,C.conname,array_agg(A.attname ORDER BY K.n) FROM pg_catalog.pg_constraint C CROSS JOIN LATERAL unnest(C.conkey) WITH ORDINALITY K(attnum,n) JOIN pg_catalog.pg_attribute A ON A.attrelid=C.conrelid AND A.attnum=K.attnum WHERE C.conrelid=? AND C.contype IN ('p','u') GROUP BY C.oid,C.contype,C.conname ORDER BY C.conname")){
                q.setQueryTimeout(3);q.setLong(1,oid);try(var r=q.executeQuery()){while(r.next())keys.add(new Key(r.getString(1).equals("p")?"PRIMARY":"UNIQUE",r.getString(2),strings(r.getArray(3))));}}
            try(var q=c.prepareStatement("SELECT C.relname,I.indisunique,M.amname,I.indnkeyatts,I.indnatts,pg_catalog.pg_get_expr(I.indpred,I.indrelid),pg_catalog.pg_get_indexdef(I.indexrelid),I.indisvalid,I.indexrelid FROM pg_catalog.pg_index I JOIN pg_catalog.pg_class C ON C.oid=I.indexrelid JOIN pg_catalog.pg_am M ON M.oid=C.relam WHERE I.indrelid=? ORDER BY C.relname")){
                q.setQueryTimeout(3);q.setLong(1,oid);try(var r=q.executeQuery()){while(r.next()){var expressions=new ArrayList<String>();var includes=new ArrayList<String>();for(int pos=1;pos<=r.getInt(5);pos++)try(var d=c.prepareStatement("SELECT pg_catalog.pg_get_indexdef(?::oid,?,true)")){d.setQueryTimeout(3);d.setLong(1,r.getLong(9));d.setInt(2,pos);try(var value=d.executeQuery()){value.next();(pos<=r.getInt(4)?expressions:includes).add(value.getString(1));}}indexes.add(new Index(r.getString(1),r.getBoolean(2),r.getString(3),expressions,includes,r.getString(6),r.getString(7),r.getBoolean(8)));}}}
            return result("COMPLETE",null,id,type,Long.toString(oid),version,settings,boot,observedAt,columns,keys,indexes);
        }finally{c.rollback();}
    }
    private static List<String> strings(java.sql.Array a)throws SQLException{try{return Arrays.stream((Object[])a.getArray()).map(Object::toString).toList();}finally{a.free();}}
    private static <T extends Number>T number(ResultSet r,int col,Class<T> type)throws SQLException{var n=(Number)r.getObject(col);if(n==null)return null;if(type==Long.class)return type.cast(Long.valueOf(n.longValue()));return type.cast(Integer.valueOf(n.intValue()));}
    private static Observation result(String state,String reason,Identity id,String type,String token,String version,String settings,String boot,Instant observedAt,List<Column> columns,List<Key> keys,List<Index> indexes){return new Observation(state,reason,id,type,token,"PostgreSQL",version,observedAt,settings,boot,columns,keys,indexes);}
}
