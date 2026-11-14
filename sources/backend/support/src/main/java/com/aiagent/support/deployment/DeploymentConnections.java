// @feat FEAT-302
package com.aiagent.support.deployment;

import com.aiagent.core.deployment.ConnectionRegistry;
import java.time.Clock;
import java.net.URI;
import java.util.*;
import java.util.function.*;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/** 비밀을 열거 복호화하지 않는 불변 연결 세대. 실제 전송/현재 정책은 소유 어댑터가 수행한다. */
@Component
public final class DeploymentConnections implements ConnectionRegistry {
    public record Definition(Kind kind,String displayName,String endpoint,String authentication,String principal,String secretProperty,Map<String,String> headers,Set<String> capabilities,boolean required) {
        @Override public String toString(){return "배포 연결 정의[보호됨]";}
    }
    private record Entry(Definition definition,Supplier<String> secret,Status status) {}
    private final Generation generation;private final Map<String,Entry> entries;private final Map<String,Consumer> consumers;
    public DeploymentConnections(ConfigurableEnvironment environment,Clock clock,List<Consumer> registered) {
        String deployment=environment.getProperty("app.deployment.id","unconfigured");
        if(!deployment.matches("[A-Za-z0-9_-]{1,100}"))throw new SecretFailure("DEPLOYMENT_ID",null);
        generation=new Generation(UUID.randomUUID(),deployment,"1",clock.instant());
        var accepted=new HashMap<String,Consumer>();
        for(var consumer:registered)if(consumer.id()==null||consumer.id().isBlank()||consumer.kinds()==null||accepted.putIfAbsent(consumer.id(),consumer)!=null)throw new SecretFailure("CALLER_REGISTRATION",null);
        consumers=Map.copyOf(accepted);
        Map<String,Definition> definitions;
        try {definitions=SecretEnvironment.binder(environment).bind("app.deployment.connections",Bindable.mapOf(String.class,Definition.class)).orElse(Map.of());}
        catch(RuntimeException failure){throw new SecretFailure("CONNECTION_DEFINITION","app.deployment.connections");}
        boolean allowPlain=environment.getProperty("app.secrets.allow-plaintext",Boolean.class,false);
        var loaded=new LinkedHashMap<String,Entry>();
        definitions.forEach((name,definition)->{
            if(!name.matches("[a-z][a-z0-9-]{0,99}")||definition.kind()==null||definition.displayName()==null||definition.displayName().isBlank()||definition.displayName().length()>100)throw new SecretFailure("CONNECTION_ID","app.deployment.connections");
            Status status=valid(definition)?Status.AVAILABLE:Status.UNSUPPORTED;
            Object raw=definition.secretProperty()==null?null:environment.getProperty(definition.secretProperty(),Object.class);
            Supplier<String> secret=()->{
                if(raw instanceof SecretEnvironment.ProtectedValue value)return value.consume();
                if(raw instanceof String text&&allowPlain)return text;
                throw new SecretFailure("MISSING",definition.secretProperty());
            };
            loaded.put(name,new Entry(definition,secret,status));
        });entries=Collections.unmodifiableMap(loaded);validateBoot();
    }
    private boolean valid(Definition value) {
        if(value.endpoint()==null||value.authentication()==null||!Set.of("NONE","BASIC","BEARER","API_KEY","EXTERNAL").contains(value.authentication()))return false;
        if(value.authentication().equals("NONE")&&value.secretProperty()!=null||!value.authentication().equals("NONE")&&!value.authentication().equals("EXTERNAL")&&!SecretEnvelope.canonicalProperty(value.secretProperty()))return false;
        try {
            String endpoint=value.endpoint();
            if(endpoint.matches("(?i).*(password|passwd|pwd|token|secret|api[_-]?key)[=:/].*")||endpoint.contains("\n")||endpoint.contains("\r"))return false;
            var uri=URI.create(endpoint.startsWith("jdbc:")?endpoint.substring(5):endpoint);
            if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)return false;
            if(value.kind()!=Kind.DATASOURCE&&!Set.of("https","http").contains(uri.getScheme()))return false;
            if(value.headers()!=null&&value.headers().entrySet().stream().anyMatch(e->e.getKey().matches("(?i).*(authorization|cookie|token|secret|key).*")))return false;
            return true;
        }catch(RuntimeException failure){return false;}
    }
    private Consumer caller(String id){var consumer=consumers.get(id);if(consumer==null)throw new Rejected(Status.FORBIDDEN);return consumer;}
    private void authorize(String id,Kind kind,Operation operation){var consumer=caller(id);if(!consumer.kinds().contains(kind))throw new Rejected(Status.FORBIDDEN);consumer.authorize(kind,operation);}
    @Override public Generation generation(){return generation;}
    private PublicView view(String key,Entry entry){return new PublicView(new Ref(entry.definition.kind(),key,generation.id()),entry.definition.displayName(),generation,entry.status==Status.AVAILABLE&&!entry.definition.required()&&entry.definition.secretProperty()!=null?Status.SECRET_NOT_VERIFIED:entry.status);}
    @Override public List<PublicView> listPublicKinds(String id) {
        var consumer=caller(id);var values=new ArrayList<PublicView>();
        for(var kind:consumer.kinds()){authorize(id,kind,Operation.READ);entries.forEach((key,entry)->{if(entry.definition.kind()==kind)values.add(view(key,entry));});}return List.copyOf(values);
    }
    private Entry find(Ref ref) {
        if(ref==null||!generation.id().equals(ref.expectedGeneration()))throw new Rejected(Status.GENERATION_MISMATCH);
        var entry=entries.get(ref.key());if(entry==null||entry.definition.kind()!=ref.kind())throw new Rejected(Status.MISSING);return entry;
    }
    @Override public PublicView describe(String id,Ref ref){if(ref==null)throw new Rejected(Status.MISSING);authorize(id,ref.kind(),Operation.READ);return view(ref.key(),find(ref));}
    @Override public void validateBoot(){entries.values().stream().filter(e->e.definition.required()).forEach(e->{if(e.status!=Status.AVAILABLE)throw new Rejected(e.status);consume(e);});}
    private String consume(Entry entry) {
        if(entry.definition.authentication().equals("NONE")||entry.definition.authentication().equals("EXTERNAL")&&entry.definition.secretProperty()==null)return null;
        try {String secret=entry.secret.get();if(secret.isEmpty())throw new SecretFailure("EMPTY",entry.definition.secretProperty());return secret;}
        catch(RuntimeException failure){throw new Rejected(Status.SECRET_UNAVAILABLE);}
    }
    @Override public <T>T withResolvedConnection(String id,Ref ref,Function<Resolved,T> action) {
        if(ref==null)throw new Rejected(Status.MISSING);authorize(id,ref.kind(),Operation.CONNECT);var entry=find(ref);if(entry.status!=Status.AVAILABLE)throw new Rejected(entry.status);
        String secret=consume(entry);var connection=new ScopedConnection(ref,entry.definition,secret);
        try{return action.apply(connection);}finally{connection.close();}
    }
    private static final class ScopedConnection implements Resolved {
        private final Ref ref;private final Definition definition;private final Thread owner=Thread.currentThread();private char[] secret;private boolean active=true;
        ScopedConnection(Ref ref,Definition definition,String secret){this.ref=ref;this.definition=definition;this.secret=secret==null?null:secret.toCharArray();}
        private void check(){if(!active||Thread.currentThread()!=owner)throw new Rejected(Status.FORBIDDEN);}
        public Ref ref(){check();return ref;}public String endpoint(){check();return definition.endpoint();}public String authentication(){check();return definition.authentication();}public String principal(){check();return definition.principal();}
        public Map<String,String> headers(){check();return definition.headers()==null?Map.of():Map.copyOf(definition.headers());}
        public Set<String> capabilities(){check();return definition.capabilities()==null?Set.of():Set.copyOf(definition.capabilities());}
        public <T>T withSecret(Function<char[],T> action){check();char[] copy=secret==null?new char[0]:secret.clone();try{return action.apply(copy);}finally{Arrays.fill(copy,'\0');}}
        void close(){active=false;if(secret!=null)Arrays.fill(secret,'\0');secret=null;}
        @Override public String toString(){return "해석된 배포 연결[보호됨]";}
    }
}
