// @feat FEAT-302
package com.aiagent.support.deployment;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.origin.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** 선택 전 값은 불투명하게 운반하고 선택된 값의 변환에서만 복호화한다. */
public final class SecretEnvironment implements EnvironmentPostProcessor,Ordered {
    static final String MASTER="AIHUB_CONFIG_MASTER_KEY",MASTER_FILE="AIHUB_CONFIG_MASTER_KEY_FILE";
    private static final Set<String> STANDARD=Set.of("spring.datasource.password","spring.liquibase.password","spring.data.redis.password","spring.mail.password","server.ssl.key-store-password","server.ssl.key-password","server.ssl.trust-store-password");
    @Override public int getOrder(){return ConfigDataEnvironmentPostProcessor.ORDER+1;}
    @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app){install(env,app,snapshotKey(System.getenv()));}
    static Supplier<byte[]> snapshotKey(Map<String,String> external) {
        String key=external.get(MASTER),file=external.get(MASTER_FILE);
        if(key!=null&&file!=null)return ()->{throw new SecretFailure("KEY_SOURCE",null);};
        if(file!=null)try{key=ProtectedInput.read(Path.of(file),128).strip();}catch(RuntimeException failure){return ()->{throw new SecretFailure("KEY_INPUT",null);};}
        String selected=key;return ()->SecretEnvelope.key(selected);
    }
    static void install(ConfigurableEnvironment env,SpringApplication app,Supplier<byte[]> key) {
        SecretBootstrapGuard.check(env);
        String tenant=env.getProperty("app.secrets.tenant"),environment=env.getProperty("app.secrets.environment");
        boolean allowPlain=env.getProperty("app.secrets.allow-plaintext",Boolean.class,false);
        var properties=new HashSet<>(STANDARD);
        properties.addAll(Binder.get(env).bind("app.secrets.properties",Bindable.listOf(String.class)).orElse(List.of()));
        var names=new HashMap<String,String>();
        for(String property:properties) {
            if(!SecretEnvelope.canonicalProperty(property)||property.startsWith("sys-config.")||property.startsWith("app.secrets."))throw new SecretFailure("PROPERTY_REGISTRATION",property);
            String previous=names.putIfAbsent(alias(property),property);if(previous!=null&&!previous.equals(property))throw new SecretFailure("PROPERTY_ALIAS",property);
        }
        var protector=new Protector(tenant,environment,allowPlain,Map.copyOf(names),key);
        var conversion=new ApplicationConversionService();
        conversion.addConverter(ProtectedValue.class,String.class,ProtectedValue::consume);
        env.setConversionService(conversion);
        app.addInitializers(context->{
            context.getBeanFactory().registerSingleton("conversionService",conversion);
            context.getBeanFactory().registerSingleton("deploymentSecretProtector",protector);
            context.getBeanFactory().registerSingleton("deploymentSecretBindHandlerAdvisor",(org.springframework.boot.context.properties.ConfigurationPropertiesBindHandlerAdvisor)handler->safeBinding(handler,properties));
        });
        var sources=new ArrayList<PropertySource<?>>();env.getPropertySources().forEach(sources::add);
        for(var source:sources) {
            if(source.getName().equals("configurationProperties"))continue;
            PropertySource<?> wrapped;var originalSource=source;
            if(source instanceof SystemEnvironmentPropertySource system)wrapped=new WrappedSystem(system,protector);
            else if(source instanceof EnumerablePropertySource<?> enumerable)wrapped=new Wrapped(enumerable,protector);
            else wrapped=new PropertySource<Object>(source.getName(),"보호된 설정 원천") {public Object getProperty(String name){return protector.protect(originalSource.getProperty(name),name);}};
            env.getPropertySources().replace(source.getName(),wrapped);
        }
        env.getPropertySources().remove("configurationProperties");ConfigurationPropertySources.attach(env);
        if(!allowPlain&&env.getProperty("spring.datasource.url")!=null) {
            try {String required=env.getProperty("spring.datasource.password");if(required==null||required.isEmpty())throw new SecretFailure("REQUIRED","spring.datasource.password");}
            catch(RuntimeException failure){throw new SecretFailure("REQUIRED","spring.datasource.password");}
        }
        for(String endpoint:List.of("env","configprops"))if(!env.getProperty("management.endpoint."+endpoint+".show-values","never").equalsIgnoreCase("never"))throw new SecretFailure("DIAGNOSTIC","management.endpoint."+endpoint+".show-values");
    }
    public static Binder binder(ConfigurableEnvironment env){
        var names=new HashSet<>(STANDARD);names.addAll(Binder.get(env).bind("app.secrets.properties",Bindable.listOf(String.class)).orElse(List.of()));
        return new Binder(ConfigurationPropertySources.get(env),new PropertySourcesPlaceholdersResolver(env),env.getConversionService(),null,safeBinding(BindHandler.DEFAULT,names));
    }
    private static BindHandler safeBinding(BindHandler parent,Set<String> names) {
        return new AbstractBindHandler(parent) {
            @Override public Object onFailure(org.springframework.boot.context.properties.source.ConfigurationPropertyName name,Bindable<?> target,BindContext context,Exception error)throws Exception {
                String key=name.toString();if(names.stream().anyMatch(value->value.equals(key)||value.startsWith(key+".")||value.startsWith(key+"[")))throw new SecretFailure("BINDING",key);
                return super.onFailure(name,target,context,error);
            }
        };
    }
    private static String alias(String value){return value.replace("-","").replace("_","").replace(".","").replace("[","").replace("]","").toLowerCase(Locale.ROOT);}
    static final class Protector {
        private final String tenant,environment;private final boolean allowPlain;private final Map<String,String> names;private final Supplier<byte[]> key;
        private Protector(String tenant,String environment,boolean allowPlain,Map<String,String> names,Supplier<byte[]> key){this.tenant=tenant;this.environment=environment;this.allowPlain=allowPlain;this.names=names;this.key=key;}
        Object protect(Object raw,String name) {
            if(raw==null||raw instanceof ProtectedValue)return raw;
            String property=names.get(alias(name));boolean encrypted=raw instanceof String value&&value.startsWith("ENC(");
            if(name.equals(MASTER))return new ProtectedValue(this,"app.secrets.master-key",raw,false,true);
            if(!encrypted&&(property==null||allowPlain))return raw;
            if(property==null)property=SecretEnvelope.canonicalProperty(name)?name:"unregistered.secret";
            return new ProtectedValue(this,property,raw,names.containsValue(property),false);
        }
        String consume(ProtectedValue value) {
            if(value.keyMaterial||!value.registered)throw new SecretFailure("UNREGISTERED",value.property);
            if(!(value.raw instanceof String text))throw new SecretFailure("TYPE",value.property);
            if(!text.startsWith("ENC(")) {
                if(allowPlain)return text;
                throw new SecretFailure("PLAINTEXT",value.property);
            }
            byte[] active=key.get();char[] plain=null;
            try {plain=SecretEnvelope.decrypt(active,new SecretEnvelope.Binding(tenant,environment,value.property),text);return new String(plain);}
            finally {Arrays.fill(active,(byte)0);if(plain!=null)Arrays.fill(plain,'\0');}
        }
        @Override public String toString(){return "설정 비밀 보호기[보호됨]";}
    }
    static final class ProtectedValue {
        private final Protector owner;private final String property;private final Object raw;private final boolean registered,keyMaterial;
        private ProtectedValue(Protector owner,String property,Object raw,boolean registered,boolean keyMaterial){this.owner=owner;this.property=property;this.raw=raw;this.registered=registered;this.keyMaterial=keyMaterial;}
        String consume(){return owner.consume(this);}
        @Override public String toString(){return "[보호된 설정]";}
    }
    private static final class SafeValues extends AbstractMap<String,Object> {
        private final Map<String,Object> values;
        SafeValues(EnumerablePropertySource<?> source,Protector protector){var copy=new LinkedHashMap<String,Object>();for(String name:source.getPropertyNames())copy.put(name,protector.protect(source.getProperty(name),name));values=Collections.unmodifiableMap(copy);}
        @Override public Object get(Object key){return values.get(key);}
        @Override public Set<Entry<String,Object>> entrySet(){return values.entrySet();}
        @Override public String toString(){return "[보호된 설정 원천]";}
    }
    private static final class Wrapped extends EnumerablePropertySource<SafeValues> implements OriginLookup<String> {
        private final EnumerablePropertySource<?> original;
        Wrapped(EnumerablePropertySource<?> source,Protector protector){super(source.getName(),new SafeValues(source,protector));original=source;}
        public String[] getPropertyNames(){return source.keySet().toArray(String[]::new);}
        public Object getProperty(String name){return source.get(name);}
        public Origin getOrigin(String name){return OriginLookup.getOrigin(original,name);}
    }
    private static final class WrappedSystem extends SystemEnvironmentPropertySource implements OriginLookup<String> {
        private final SystemEnvironmentPropertySource original;
        WrappedSystem(SystemEnvironmentPropertySource source,Protector protector){super(source.getName(),new SafeValues(source,protector));original=source;}
        public Origin getOrigin(String name){return OriginLookup.getOrigin(original,name);}
    }
}
