// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.SerializationUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import java.time.Duration;
import java.util.*;
import java.io.*;

/** 표준 Spring Session 부모/속성 형식을 사용하는 JDBC payload provider. */
public final class JdbcSessionStore implements SessionStoreAdapter {
    private final JdbcTemplate jdbc;
    private final SessionTransactions transactions;
    private final Set<AuthDomain> enabled;
    private final boolean postgres;
    public JdbcSessionStore(DataSource source,PlatformTransactionManager manager,Set<AuthDomain> enabledDomains){
        jdbc=new JdbcTemplate(source);transactions=new SessionTransactions(source,manager);enabled=Set.copyOf(enabledDomains);
        postgres=Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c)->c.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
    }
    public Provider provider(){return Provider.JDBC;}
    private String table(AuthDomain domain){if(!enabled.contains(domain))throw new Failure(Outcome.INVALID_SESSION);return domain==AuthDomain.ADMIN?"SPRING_SESSION":"AI_PORTAL_SESSION";}
    private Instant now(){return jdbc.queryForObject(postgres?"SELECT clock_timestamp()":"SELECT CURRENT_TIMESTAMP",(r,i)->r.getTimestamp(1).toInstant());}
    public void verifyAvailable(){for(var domain:enabled){jdbc.queryForObject("SELECT COUNT(*) FROM "+table(domain),Long.class);jdbc.queryForObject("SELECT COUNT(*) FROM "+table(domain)+"_ATTRIBUTES",Long.class);}}
    private record Row(String primary,String session,Instant expiry) {String version(){return primary+"|"+session;}}
    private Row row(Ref ref,String version,boolean lock){
        String[] key=version.split("\\|",-1);if(key.length!=2)return null;
        var rows=jdbc.query("SELECT PRIMARY_ID,SESSION_ID,EXPIRY_TIME FROM "+table(ref.domain())+" WHERE PRIMARY_ID=? AND SESSION_ID=?"+(lock?" FOR UPDATE":""),
                (r,i)->new Row(r.getString(1).trim(),r.getString(2).trim(),Instant.ofEpochMilli(r.getLong(3))),key[0],key[1]);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private static byte[] serialized(Object value){return Objects.requireNonNull(SerializationUtils.serialize(value));}
    private byte[] payloadBytes(AuthDomain domain,String primary){
        var values=jdbc.queryForList("SELECT ATTRIBUTE_BYTES FROM "+table(domain)+"_ATTRIBUTES WHERE SESSION_PRIMARY_ID=? AND ATTRIBUTE_NAME='AUTH_V2_PAYLOAD'",byte[].class,primary);
        if(values.size()!=1)throw new Failure(Outcome.INVALID_SESSION);
        try(var input=new ObjectInputStream(new ByteArrayInputStream(values.getFirst()))){
            input.setObjectInputFilter(info->info.serialClass()==null?ObjectInputFilter.Status.UNDECIDED:info.serialClass()==byte[].class&&info.arrayLength()<=1_000_000?ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED);
            Object value=input.readObject();if(!(value instanceof byte[] bytes))throw new Failure(Outcome.INVALID_SESSION);return bytes;
        }catch(IOException|ClassNotFoundException failure){throw new Failure(Outcome.INVALID_SESSION);}
    }
    private Stored stored(Ref ref,Row row){
        var decoded=SessionPayloadCodec.decode(payloadBytes(ref.domain(),row.primary));
        if(!ref.equals(decoded.payload().ref()))throw new Failure(Outcome.INVALID_SESSION);
        return new Stored(decoded.payload(),decoded.revision(),row.version());
    }
    public Optional<Stored> load(Ref ref,PrincipalRef principal){return transactions.required(()->{
        String table=table(ref.domain());
        var rows=jdbc.query("SELECT S.PRIMARY_ID,S.SESSION_ID,S.EXPIRY_TIME FROM "+table+" S JOIN "+table+"_ATTRIBUTES G ON G.SESSION_PRIMARY_ID=S.PRIMARY_ID JOIN "+table+"_ATTRIBUTES V ON V.SESSION_PRIMARY_ID=S.PRIMARY_ID JOIN "+table+"_ATTRIBUTES E ON E.SESSION_PRIMARY_ID=S.PRIMARY_ID WHERE G.ATTRIBUTE_NAME='AUTH_GRANT_ID' AND G.ATTRIBUTE_BYTES=? AND V.ATTRIBUTE_NAME='AUTH_GENERATION' AND V.ATTRIBUTE_BYTES=? AND E.ATTRIBUTE_NAME='AUTH_EPOCH' AND E.ATTRIBUTE_BYTES=? AND "+(principal==null?"S.PRINCIPAL_NAME IS NULL":"S.PRINCIPAL_NAME=?"),
                (r,i)->new Row(r.getString(1).trim(),r.getString(2).trim(),Instant.ofEpochMilli(r.getLong(3))),
                principal==null?new Object[]{serialized(ref.grantId()),serialized(ref.generation()),serialized(ref.epoch().toString())}:new Object[]{serialized(ref.grantId()),serialized(ref.generation()),serialized(ref.epoch().toString()),principal.indexKey()});
        if(rows.size()>1)throw new Failure(Outcome.UNAVAILABLE);if(rows.isEmpty()||!now().isBefore(rows.getFirst().expiry))return Optional.empty();
        var found=stored(ref,rows.getFirst());
        if(principal==null?found.payload().identity()!=null:found.payload().identity()==null||!principal.equals(found.payload().identity().principal()))throw new Failure(Outcome.INVALID_SESSION);
        return Optional.of(found);
    });}
    private Map<String,Object> attributes(Payload payload,long revision){
        var attrs=new LinkedHashMap<String,Object>();attrs.put("AUTH_GRANT_ID",payload.ref().grantId());attrs.put("AUTH_GENERATION",payload.ref().generation());attrs.put("AUTH_EPOCH",payload.ref().epoch().toString());attrs.put("schemaVersion",2);attrs.put("AUTH_V2_PAYLOAD",SessionPayloadCodec.encode(payload,revision));
        try(var raw=new ByteArrayOutputStream();var output=new DataOutputStream(raw)){
            payload.csrf().writeTo(output);String csrf=new DataInputStream(new ByteArrayInputStream(raw.toByteArray())).readUTF();attrs.put(payload.ref().domain()==AuthDomain.ADMIN?"ADMIN_CSRF":"PORTAL_CSRF",csrf);
        }catch(IOException failure){throw new IllegalStateException("세션 CSRF를 저장할 수 없다",failure);}
        if(payload.identity()!=null){
            if(payload.ref().domain()==AuthDomain.ADMIN){var auth=UsernamePasswordAuthenticationToken.authenticated(payload.identity().principal().subjectId(),null,List.of());auth.setDetails(payload.identity());attrs.put("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(auth));attrs.put("ADMIN_AUTHORITY_CACHE",new HashSet<>(payload.authoritySnapshot()));}
            else {attrs.put("PORTAL_SECURITY_CONTEXT",payload.identity());if(payload.identity().credentialVersion()!=null)attrs.put("PORTAL_CREDENTIAL_VERSION",payload.identity().credentialVersion());}
        }
        return attrs;
    }
    private void writeAttributes(Payload payload,long revision,String primary,boolean creating){
        String table=table(payload.ref().domain())+"_ATTRIBUTES";
        attributes(payload,revision).forEach((key,value)->{
            byte[] bytes=serialized(value);int updated=creating?0:jdbc.update("UPDATE "+table+" SET ATTRIBUTE_BYTES=? WHERE SESSION_PRIMARY_ID=? AND ATTRIBUTE_NAME=?",bytes,primary,key);
            if(updated==0)jdbc.update("INSERT INTO "+table+"(SESSION_PRIMARY_ID,ATTRIBUTE_NAME,ATTRIBUTE_BYTES) VALUES(?,?,?)",primary,key,bytes);
        });
    }
    public Stored create(Payload payload,Instant expiresAt){
        transactions.requireCurrent();Instant now=now();if(!now.isBefore(expiresAt))throw new Failure(Outcome.INVALID_SESSION);
        var principal=payload.identity()==null?null:payload.identity().principal();if(load(payload.ref(),principal).isPresent())throw new Failure(Outcome.INVALID_SESSION);
        String primary=UUID.randomUUID().toString(),session=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO "+table(payload.ref().domain())+"(PRIMARY_ID,SESSION_ID,CREATION_TIME,LAST_ACCESS_TIME,MAX_INACTIVE_INTERVAL,EXPIRY_TIME,PRINCIPAL_NAME) VALUES(?,?,?,?,?,?,?)",primary,session,now.toEpochMilli(),now.toEpochMilli(),Math.max(1,Duration.between(now,expiresAt).toSeconds()),expiresAt.toEpochMilli(),principal==null?null:principal.indexKey());
        writeAttributes(payload,1,primary,true);return new Stored(payload,1,primary+"|"+session);
    }
    public Outcome updateExisting(Stored expected,Payload changed,Instant expiresAt){
        transactions.requireCurrent();if(!SessionPayloadCodec.mutableOnly(expected.payload(),changed))return Outcome.INVALID_SESSION;
        Ref ref=expected.payload().ref();Row row=row(ref,expected.version(),true);Instant now=now();if(row==null||!now.isBefore(row.expiry)||!now.isBefore(expiresAt))return Outcome.INVALID_SESSION;
        Stored current=stored(ref,row);if(!SessionPayloadCodec.mutableOnly(current.payload(),changed))return Outcome.INVALID_SESSION;
        if(current.revision()!=expected.revision())return Outcome.STALE_REVISION;
        int count=jdbc.update("UPDATE "+table(ref.domain())+" SET LAST_ACCESS_TIME=?,MAX_INACTIVE_INTERVAL=?,EXPIRY_TIME=? WHERE PRIMARY_ID=? AND SESSION_ID=? AND EXPIRY_TIME>?",now.toEpochMilli(),Math.max(1,Duration.between(now,expiresAt).toSeconds()),expiresAt.toEpochMilli(),row.primary,row.session,now.toEpochMilli());
        if(count==0)return Outcome.INVALID_SESSION;writeAttributes(changed,expected.revision()+1,row.primary,false);return Outcome.APPLIED;
    }
    public Outcome delete(Stored expected){
        transactions.requireCurrent();Ref ref=expected.payload().ref();Row row=row(ref,expected.version(),true);if(row==null)return Outcome.ALREADY_ABSENT;
        if(stored(ref,row).revision()!=expected.revision())return Outcome.STALE_REVISION;
        return jdbc.update("DELETE FROM "+table(ref.domain())+" WHERE PRIMARY_ID=? AND SESSION_ID=?",row.primary,row.session)==1?Outcome.REVOKED:Outcome.ALREADY_ABSENT;
    }
    public int cleanupExpired(){return transactions.required(()->{
        int removed=0;long cutoff=now().toEpochMilli();
        for(AuthDomain domain:enabled){String table=table(domain);var ids=jdbc.queryForList("SELECT PRIMARY_ID FROM "+table+" WHERE EXPIRY_TIME<=? ORDER BY PRIMARY_ID LIMIT 1000 FOR UPDATE SKIP LOCKED",String.class,cutoff);
            for(String id:ids)removed+=jdbc.update("DELETE FROM "+table+" WHERE PRIMARY_ID=? AND EXPIRY_TIME<=?",id,cutoff);
        }return removed;
    });}
}
