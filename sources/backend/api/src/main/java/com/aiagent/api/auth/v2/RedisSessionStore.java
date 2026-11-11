// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** 고정 namespace와 Lua CAS를 사용하는 외부 payload provider. */
public final class RedisSessionStore implements SessionStoreAdapter {
    private static final DefaultRedisScript<Long> CREATE=new DefaultRedisScript<>("""
            if redis.call('EXISTS',KEYS[1])==1 then return 0 end
            redis.call('HSET',KEYS[1],'payload',ARGV[1],'revision','1','domain',ARGV[3],'grant',ARGV[4],'generation',ARGV[5],'epoch',ARGV[6])
            redis.call('PEXPIREAT',KEYS[1],ARGV[2])
            return redis.call('EXISTS',KEYS[1])
            """,Long.class);
    private static final String COMPARE="""
            if redis.call('EXISTS',KEYS[1])==0 then return -1 end
            if redis.call('HGET',KEYS[1],'domain')~=ARGV[1] or redis.call('HGET',KEYS[1],'grant')~=ARGV[2] or redis.call('HGET',KEYS[1],'generation')~=ARGV[3] or redis.call('HGET',KEYS[1],'epoch')~=ARGV[4] then return -1 end
            if redis.call('HGET',KEYS[1],'revision')~=ARGV[5] then return 0 end
            if redis.call('HGET',KEYS[1],'payload')~=ARGV[6] then return -1 end
            """;
    private static final DefaultRedisScript<Long> UPDATE=new DefaultRedisScript<>(COMPARE+"""
            redis.call('HSET',KEYS[1],'payload',ARGV[7],'revision',tonumber(ARGV[5])+1)
            redis.call('PEXPIREAT',KEYS[1],ARGV[8])
            if redis.call('EXISTS',KEYS[1])==0 then return -1 end
            return 1
            """,Long.class);
    private static final DefaultRedisScript<Long> DELETE=new DefaultRedisScript<>(COMPARE+"return redis.call('DEL',KEYS[1])",Long.class);
    private final StringRedisTemplate redis;
    public RedisSessionStore(RedisConnectionFactory factory){redis=new StringRedisTemplate(Objects.requireNonNull(factory));}
    public Provider provider(){return Provider.REDIS;}
    private static String key(Ref ref){return "aihub:auth:v2:"+ref.domain()+":"+ref.epoch()+":"+ref.grantId()+":"+ref.generation();}
    public void verifyAvailable(){
        SessionTransactions.requireOutsideTransaction();
        try(var connection=Objects.requireNonNull(redis.getConnectionFactory()).getConnection()){
            if(!"PONG".equals(connection.ping()))throw new Failure(Outcome.UNAVAILABLE);
        }
    }
    private static String encoded(Payload payload,long revision){return Base64.getEncoder().encodeToString(SessionPayloadCodec.encode(payload,revision));}
    public Optional<Stored> load(Ref ref,PrincipalRef principal){
        SessionTransactions.requireOutsideTransaction();var values=redis.opsForHash().entries(key(ref));if(values.isEmpty())return Optional.empty();
        try {
            var decoded=SessionPayloadCodec.decode(Base64.getDecoder().decode(Objects.toString(values.get("payload"),"")));
            var payload=decoded.payload();
            if(!ref.equals(payload.ref())||!ref.domain().name().equals(values.get("domain"))||!Long.toString(ref.grantId()).equals(values.get("grant"))
                    ||!Long.toString(ref.generation()).equals(values.get("generation"))||!ref.epoch().toString().equals(values.get("epoch"))||!Long.toString(decoded.revision()).equals(values.get("revision")))throw new Failure(Outcome.INVALID_SESSION);
            if(principal==null?payload.identity()!=null:payload.identity()==null||!principal.equals(payload.identity().principal()))return Optional.empty();
            return Optional.of(new Stored(payload,decoded.revision(),"redis:"+decoded.revision()));
        }catch(IllegalArgumentException failure){throw new Failure(Outcome.INVALID_SESSION);}
    }
    public Stored create(Payload payload,Instant expiresAt){
        SessionTransactions.requireOutsideTransaction();Ref ref=payload.ref();
        Long saved=redis.execute(CREATE,List.of(key(ref)),encoded(payload,1),Long.toString(expiresAt.toEpochMilli()),ref.domain().name(),Long.toString(ref.grantId()),Long.toString(ref.generation()),ref.epoch().toString());
        if(!Long.valueOf(1).equals(saved))throw new Failure(Outcome.INVALID_SESSION);
        return new Stored(payload,1,"redis:1");
    }
    private static Object[] arguments(Stored expected){
        Ref ref=expected.payload().ref();return new Object[]{ref.domain().name(),Long.toString(ref.grantId()),Long.toString(ref.generation()),ref.epoch().toString(),Long.toString(expected.revision()),encoded(expected.payload(),expected.revision())};
    }
    public Outcome updateExisting(Stored expected,Payload changed,Instant expiresAt){
        SessionTransactions.requireOutsideTransaction();if(!SessionPayloadCodec.mutableOnly(expected.payload(),changed))return Outcome.INVALID_SESSION;
        Object[] args=java.util.Arrays.copyOf(arguments(expected),8);args[6]=encoded(changed,expected.revision()+1);args[7]=Long.toString(expiresAt.toEpochMilli());
        Long result=redis.execute(UPDATE,List.of(key(expected.payload().ref())),args);
        return result==null?Outcome.UNAVAILABLE:result==1?Outcome.APPLIED:result==0?Outcome.STALE_REVISION:Outcome.INVALID_SESSION;
    }
    public Outcome delete(Stored expected){
        SessionTransactions.requireOutsideTransaction();Long result=redis.execute(DELETE,List.of(key(expected.payload().ref())),arguments(expected));
        return result==null?Outcome.UNAVAILABLE:result==1?Outcome.REVOKED:result==0?Outcome.STALE_REVISION:Outcome.ALREADY_ABSENT;
    }
    /** Redis 자체 TTL 정리가 payload를 소유한다. DB 원장은 별도 cleanup으로 정리한다. */
    public int cleanupExpired(){verifyAvailable();return 0;}
}
