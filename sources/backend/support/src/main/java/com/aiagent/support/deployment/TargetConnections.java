// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;

import com.aiagent.core.deployment.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/** YAML 전체 검증 뒤에만 공개하는 비밀 없는 시스템 메타와 정확 연결 참조다. 핫 리로드하지 않는다. */
@Component
public final class TargetConnections implements ConfigurationSnapshot {
    public record Test(String method,String path,int timeoutMs,Set<Integer> successStatuses) {
        public Test {
            if(!Set.of("GET","HEAD").contains(method)||timeoutMs<1||timeoutMs>60_000||successStatuses==null||successStatuses.isEmpty()||successStatuses.stream().anyMatch(s->s<200||s>299))throw unavailable();
            successStatuses=Set.copyOf(successStatuses);
            RestJsonInvoker.target("https://validation.invalid",new BusinessApiInvoker.Request(method,path,Map.of(),Map.of(),null));
        }
    }
    public record Definition(String name,String description,Map<String,ConnectionDefinition> connections) {}
    public record ConnectionDefinition(String registryKey,String name,String description,String invokerBean,BusinessApiInvoker.Options options,Set<String> publicPathSegments,Test test) {}
    public record Connection(String id,String name,String description,ConnectionRegistry.Ref ref,String invokerBean,BusinessApiInvoker.Options options,Test test,ConnectionDisplay display) {}
    public record SystemDefinition(String key,String name,String description,Map<String,Connection> connections) {public SystemDefinition{connections=Collections.unmodifiableMap(new TreeMap<>(connections));}}
    private final String environment,settingsId;
    private final Map<String,SystemDefinition> systems;
    private final ConnectionRegistry registry;
    private final BusinessApiInvokers invokers;
    public TargetConnections(ConfigurableEnvironment environment,ConnectionRegistry registry,BusinessApiInvokers invokers) {
        this.registry=registry;this.invokers=invokers;
        this.environment=environment.getProperty("app.secrets.environment","default");
        if(!this.environment.matches("[A-Za-z0-9_-]{1,100}"))throw unavailable();
        try {
            var definitions=SecretEnvironment.binder(environment).bind("app.deployment.target-systems",Bindable.mapOf(String.class,Definition.class),new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElse(Map.of());
            var loaded=new TreeMap<String,SystemDefinition>();var assigned=new HashSet<String>();
            for(var item:definitions.entrySet()) {
                String key=id(item.getKey());var value=item.getValue();name(value.name());description(value.description());
                var connections=new TreeMap<String,Connection>();
                for(var entry:(value.connections()==null?Map.<String,ConnectionDefinition>of():value.connections()).entrySet()) {
                    String localId=id(entry.getKey());var def=entry.getValue();name(def.name());description(def.description());
                    if(def.registryKey()==null||!assigned.add(def.registryKey()))throw unavailable();
                    var ref=new ConnectionRegistry.Ref(ConnectionRegistry.Kind.BUSINESS_API,id(def.registryKey()),registry.generation().id());
                    registry.describe("target-system-settings",ref);
                    var loadedConnection=registry.withResolvedConnection("target-system-settings",ref,resolved->{
                        var options=def.options()==null?new BusinessApiInvoker.Options(1000,10_000,1_048_576,null):def.options();
                        invokers.require(def.invokerBean(),"REST_JSON",resolved.authentication());
                        if(resolved.authentication().equals("API_KEY")&&options.apiKeyHeader()==null||resolved.authentication().equals("BASIC")&&(resolved.principal()==null||resolved.principal().isBlank()||resolved.principal().contains(":")))throw unavailable();
                        var display=ConnectionDisplay.project(resolved.endpoint(),resolved.authentication(),def.publicPathSegments()==null?Set.of():def.publicPathSegments());
                        if(display.baseUrl()==null)throw unavailable();
                        return new Connection(localId,def.name(),def.description(),ref,def.invokerBean(),options,def.test(),display);
                    });connections.put(localId,loadedConnection);
                }
                loaded.put(key,new SystemDefinition(key,value.name(),value.description(),connections));
            }
            systems=Collections.unmodifiableMap(loaded);settingsId=fingerprint();
        }catch(RuntimeException failure){throw unavailable();}
    }
    private static String id(String value){if(value==null||!value.matches("[a-z][a-z0-9-]{0,99}"))throw unavailable();return value;}
    private static void name(String value){if(value!=null&&(value.isBlank()||value.length()>100))throw unavailable();}
    private static void description(String value){if(value!=null&&value.length()>10000)throw unavailable();}
    private static ConnectionRegistry.Rejected unavailable(){return new ConnectionRegistry.Rejected(ConnectionRegistry.Status.UNSUPPORTED);}
    public String environment(){return environment;}
    public String settingsId(){return settingsId;}
    public UUID bootId(){return registry.generation().id();}
    public Map<String,SystemDefinition> systems(){return systems;}
    public Connection require(String system,String connection){var parent=systems.get(system);var selected=parent==null?null:parent.connections().get(connection);if(selected==null)throw new ConnectionRegistry.Rejected(ConnectionRegistry.Status.MISSING);return selected;}
    public BusinessApiInvoker.Response invoke(String serverCaller,String system,String connection,BusinessApiInvoker.Options options,BusinessApiInvoker.Request request){
        var selected=require(system,connection);
        return registry.withResolvedConnection(serverCaller,selected.ref(),resolved->invokers.require(selected.invokerBean(),"REST_JSON",resolved.authentication()).invoke(resolved,options,request));
    }
    private String fingerprint() {
        try {
            var bytes=new ByteArrayOutputStream();var output=new DataOutputStream(bytes);
            field(output,"target-settings/v1");field(output,environment);
            for(var system:systems.values()) {
                field(output,system.key());field(output,system.name());field(output,system.description());output.writeInt(system.connections().size());
                for(var c:system.connections().values()) {
                    for(String value:Arrays.asList(c.id(),c.name(),c.description(),c.ref().key(),c.invokerBean(),c.display().baseUrl(),c.display().authenticationType()))field(output,value);
                    output.writeInt(c.options().connectTimeoutMs());output.writeInt(c.options().requestTimeoutMs());output.writeInt(c.options().maxResponseBytes());field(output,c.options().apiKeyHeader());
                    output.writeBoolean(c.test()!=null);
                    if(c.test()!=null){field(output,c.test().method());field(output,c.test().path());output.writeInt(c.test().timeoutMs());var statuses=new TreeSet<>(c.test().successStatuses());output.writeInt(statuses.size());for(int status:statuses)output.writeInt(status);}
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException failure){throw new IllegalStateException("연결 설정 지문을 만들 수 없다");}
    }
    private static void field(DataOutputStream output,String value)throws IOException {if(value==null){output.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);output.writeInt(bytes.length);output.write(bytes);}
    @Override public String toString(){return "대상 시스템 불변 설정[보호됨]";}
}
