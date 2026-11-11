// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionV2.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** 고정 필드 binary schema. Redis/JDBC payload를 임의 Java 객체로 역직렬화하지 않는다. */
final class SessionPayloadCodec {
    record Decoded(Payload payload,long revision) {}
    private SessionPayloadCodec(){}
    static byte[] encode(Payload payload,long revision){
        try(var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer)){
            out.writeInt(2);out.writeUTF(payload.ref().domain().name());out.writeLong(payload.ref().grantId());out.writeLong(payload.ref().generation());out.writeUTF(payload.ref().epoch().toString());out.writeLong(revision);
            Identity identity=payload.identity();out.writeBoolean(identity!=null);
            if(identity!=null){
                out.writeUTF(identity.principal().subjectId());out.writeUTF(identity.authLevel().name());out.writeUTF(identity.authMode());
                out.writeBoolean(identity.credentialVersion()!=null);if(identity.credentialVersion()!=null)out.writeLong(identity.credentialVersion());
                out.writeLong(identity.authenticatedAt().getEpochSecond());out.writeInt(identity.authenticatedAt().getNano());
            }
            nullable(out,payload.displayName());nullable(out,payload.locale());payload.csrf().writeTo(out);
            if(payload.authoritySnapshot().size()>10000)throw new IllegalArgumentException("세션 표시 권한 캐시가 너무 크다");
            out.writeInt(payload.authoritySnapshot().size());for(String value:new TreeSet<>(payload.authoritySnapshot()))out.writeUTF(value);
            return buffer.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("세션 payload를 기록할 수 없다",failure);}
    }
    private static void nullable(DataOutput out,String value)throws IOException{out.writeBoolean(value!=null);if(value!=null)out.writeUTF(value);}
    private static String nullable(DataInput in)throws IOException{return in.readBoolean()?in.readUTF():null;}
    static Decoded decode(byte[] bytes){
        if(bytes==null||bytes.length>1_000_000)throw new Failure(Outcome.INVALID_SESSION);
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=2)throw new Failure(Outcome.INVALID_SESSION);
            var domain=AuthDomain.valueOf(in.readUTF());var ref=new Ref(domain,in.readLong(),in.readLong(),UUID.fromString(in.readUTF()));long revision=in.readLong();
            String subject=null,mode=null;AuthLevel level=null;Long credential=null;Instant authenticated=null;
            if(in.readBoolean()){
                subject=in.readUTF();level=AuthLevel.valueOf(in.readUTF());mode=in.readUTF();if(in.readBoolean())credential=in.readLong();authenticated=Instant.ofEpochSecond(in.readLong(),in.readInt());
            }
            String name=nullable(in),locale=nullable(in);var csrf=new Secret(in.readUTF());int count=in.readInt();
            if(count<0||count>10000||revision<1)throw new Failure(Outcome.INVALID_SESSION);
            var permissions=new HashSet<String>();for(int i=0;i<count;i++)permissions.add(in.readUTF());if(in.available()!=0)throw new Failure(Outcome.INVALID_SESSION);
            Identity identity=subject==null?null:new Identity(new PrincipalRef(domain,subject),level,mode,credential,authenticated,name,locale);
            return new Decoded(new Payload(ref,identity,csrf,name,locale,permissions),revision);
        }catch(IOException|IllegalArgumentException|java.time.DateTimeException|ArithmeticException failure){throw new Failure(Outcome.INVALID_SESSION);}
    }
    static boolean mutableOnly(Payload before,Payload after){
        if(!before.ref().equals(after.ref())||!before.csrf().equals(after.csrf()))return false;
        Identity a=before.identity(),b=after.identity();if(a==null||b==null)return a==b;
        return a.principal().equals(b.principal())&&a.authLevel()==b.authLevel()&&a.authMode().equals(b.authMode())
                &&Objects.equals(a.credentialVersion(),b.credentialVersion())&&a.authenticatedAt().equals(b.authenticatedAt());
    }
}
