// @feat FEAT-002 FEAT-302
package com.aiagent.support.deployment;
import com.aiagent.core.deployment.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
/** 외부 연결의 전체 구조를 먼저 검증한다. 비밀·Hub 자체 DB를 관리 메타에 담지 않는다. */
@Component
public final class ExternalDataSources implements ConfigurationSnapshot {
    public record Definition(String alias,String registryKey){}
    public record Entry(String key,String alias,String dbmsCode,String status,ConnectionRegistry.Ref ref){}
    private final String environment,settingsId;private final UUID bootId;private final Map<String,Entry> entries;
    public ExternalDataSources(ConfigurableEnvironment env,ConnectionRegistry registry){
        String environment=env.getProperty("app.secrets.environment","default");
        if(!environment.matches("[A-Za-z0-9_-]{1,89}"))throw unavailable();this.environment="datasource_"+environment;
        bootId=UUID.nameUUIDFromBytes((registry.generation().id()+"/datasource").getBytes(StandardCharsets.UTF_8));
        try {
            var definitions=SecretEnvironment.binder(env).bind("app.external-datasources",Bindable.mapOf(String.class,Definition.class),new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElse(Map.of());
            var rows=new TreeMap<String,Entry>();var aliases=new HashSet<String>();var connections=new HashSet<String>();var fingerprint=new ArrayList<String>();fingerprint.add("datasource-settings/v1");
            for(var item:definitions.entrySet()){
                var d=item.getValue();String key=item.getKey();
                if(!key.matches("[a-z][a-z0-9-]{0,99}")||d.alias()==null||d.alias().isBlank()||d.alias().length()>200||!d.alias().equals(d.alias().trim())||!aliases.add(d.alias().toLowerCase(Locale.ROOT))||d.registryKey()==null||!d.registryKey().matches("[a-z][a-z0-9-]{0,99}")||!connections.add(d.registryKey()))throw unavailable();
                var ref=new ConnectionRegistry.Ref(ConnectionRegistry.Kind.DATASOURCE,d.registryKey(),registry.generation().id());
                String dbms="UNKNOWN",status="UNKNOWN",endpoint="";
                try {
                    var resolved=registry.withResolvedConnection("datasource-settings",ref,r->List.of(r.endpoint(),r.ref().key()));endpoint=resolved.getFirst();
                    dbms=dbms(endpoint);status=dbms.equals("UNKNOWN")?"UNSUPPORTED":"AVAILABLE";
                }catch(ConnectionRegistry.Rejected failure){status=failure.status().name();}
                rows.put(key,new Entry(key,d.alias(),dbms,status,ref));
                fingerprint.add(key);fingerprint.add(d.alias());fingerprint.add(d.registryKey());fingerprint.add(dbms);fingerprint.add(status);fingerprint.add(endpoint);
            }
            entries=Collections.unmodifiableMap(rows);
            settingsId=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.stream().map(v->v.length()+":"+v).collect(java.util.stream.Collectors.joining()).getBytes(StandardCharsets.UTF_8)));
        }catch(NoSuchAlgorithmException failure){throw new IllegalStateException("데이터소스 설정 지문을 만들 수 없다");}
        catch(RuntimeException failure){throw unavailable();}
    }
    public static String dbms(String url){if(url==null)return "UNKNOWN";for(String kind:List.of("postgresql","mysql","mariadb","oracle","sqlserver"))if(url.startsWith("jdbc:"+kind+":"))return kind.toUpperCase(Locale.ROOT);return "UNKNOWN";}
    private static ConnectionRegistry.Rejected unavailable(){return new ConnectionRegistry.Rejected(ConnectionRegistry.Status.UNSUPPORTED);}
    public String environment(){return environment;}public String settingsId(){return settingsId;}public UUID bootId(){return bootId;}
    public Map<String,Entry> entries(){return entries;}
    @Override public String toString(){return "외부 데이터소스 불변 설정[보호됨]";}
}
