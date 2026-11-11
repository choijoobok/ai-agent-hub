// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import com.aiagent.api.auth.boundary.BoundaryResponseWriter;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import com.aiagent.core.security.v2.SessionAdmissionsV2.Producer;
import java.net.URI;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import jakarta.servlet.ServletContext;
import org.springframework.session.SessionRepository;

/** 제품 bootstrap이 같은 artifact의 기동 설정을 소비하는 공개 설치점. 제품 활성화는520이다. */
public final class SessionV2Assembly {
    public record Configuration(Provider provider,Map<AuthDomain,Settings> domains,String origin,boolean localHttp,Duration timeout){
        public Configuration {
            domains=Map.copyOf(domains);Objects.requireNonNull(provider);Objects.requireNonNull(timeout);
            URI uri=URI.create(origin);
            if(domains.isEmpty()||timeout.isNegative()||timeout.isZero()||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty()
                    ||!("https".equals(uri.getScheme())||localHttp&&"http".equals(uri.getScheme()))||domains.values().stream().anyMatch(setting->setting.provider()!=provider||setting.storeInstance()==null||setting.storeInstance().isBlank()))
                throw new IllegalArgumentException("인증 저장소/영역/정확 Origin 설정이 올바르지 않다");
        }
        public static Configuration from(Environment environment){
            String provider=environment.getProperty("app.auth.session.provider","jdbc");
            Provider selected=switch(provider){case "jdbc"->Provider.JDBC;case "redis"->Provider.REDIS;default->throw new IllegalArgumentException("jdbc/redis 저장소를 명시해야 한다");};
            Map<AuthDomain,Settings> domains=new EnumMap<>(AuthDomain.class);
            for(AuthDomain domain:AuthDomain.values()){
                String prefix="app.auth.session.domains."+domain.name().toLowerCase(Locale.ROOT)+".";
                if(environment.getProperty(prefix+"enabled",Boolean.class,domain==AuthDomain.ADMIN))domains.put(domain,new Settings(selected,UUID.fromString(required(environment,prefix+"epoch","app.auth.session.epoch")),required(environment,prefix+"store-instance","app.auth.session.store-instance")));
            }
            return new Configuration(selected,domains,required(environment,"app.auth.session.origin",null),environment.getProperty("app.auth.session.local-http",Boolean.class,false),Duration.ofMinutes(environment.getProperty("app.auth.session.timeout-minutes",Long.class,30L)));
        }
        private static String required(Environment environment,String key,String fallback){String value=environment.getProperty(key);if(value==null&&fallback!=null)value=environment.getProperty(fallback);if(value==null||value.isBlank())throw new IllegalArgumentException("인증 기동 설정이 필요하다: "+key);return value;}
    }
    private final SessionRuntime runtime;
    private final SessionV2Filter filter;
    private final ModuleApiCatalog catalog;
    private SessionV2Assembly(SessionRuntime runtime,SessionV2Filter filter,ModuleApiCatalog catalog){this.runtime=runtime;this.filter=filter;this.catalog=catalog;}
    public static SessionV2Assembly create(Configuration configuration,DataSource source,PlatformTransactionManager manager,RedisConnectionFactory redis,
            SessionRuntime.AuthorityBinding authority,ModuleApiCatalog catalog,Map<AuthDomain,Producer> producers,BoundaryResponseWriter responses){
        return create(configuration,source,manager,redis,authority,catalog,producers,responses,Map.of());
    }
    public static SessionV2Assembly create(Configuration configuration,DataSource source,PlatformTransactionManager manager,RedisConnectionFactory redis,
            SessionRuntime.AuthorityBinding authority,ModuleApiCatalog catalog,Map<AuthDomain,Producer> producers,BoundaryResponseWriter responses,Map<String,SessionV2Filter.Preflight> preflights){
        SessionStoreAdapter store=switch(configuration.provider()){
            case JDBC->new JdbcSessionStore(source,manager,configuration.domains().keySet());
            case REDIS->new RedisSessionStore(Objects.requireNonNull(redis,"Redis 연결 조립이 필요하다"));
        };
        var runtime=new SessionRuntime(new JdbcSessionAdmissionLedger(source,manager),store,new SessionTransactions(source,manager),configuration.domains(),authority,configuration.timeout());
        return new SessionV2Assembly(runtime,new SessionV2Filter(runtime,catalog,configuration,producers,responses,preflights),catalog);
    }
    public SessionRuntime runtime(){return runtime;}
    public SessionV2Filter filter(){return filter;}
    /** 전역 기본Repository/자동필터 및 중복v1/v2설치를 함께 거부한다. */
    public void verifyInstallation(ConfigurableListableBeanFactory beans,ServletContext context,Map<String,String> expectedHandlers,Collection<ModuleApiCatalog.Handler> observedHandlers){verifyFilterInstallation(beans,context);catalog.verifyHandlers(expectedHandlers,observedHandlers);}
    /** Servlet fixture의 경계만 검사한다. 제품설치는 verifyInstallation의 관측handler대조까지 필수다. */
    public void verifyFilterInstallation(ConfigurableListableBeanFactory beans,ServletContext context){
        if(beans.getBeanNamesForType(SessionRepository.class,false,false).length!=0||beans.containsBean("springSessionRepositoryFilter")||context.getFilterRegistrations().values().stream().anyMatch(registration->registration.getClassName().contains("SessionRepositoryFilter")||registration.getClassName().endsWith("AuthBoundaryFilter")))throw new IllegalStateException("기본 또는 이전 인증 세션 필터가 중복 설치됐다");
        if(context.getFilterRegistrations().values().stream().filter(registration->registration.getClassName().equals(SessionV2Filter.class.getName())).count()!=1)throw new IllegalStateException("인증 영역 공통 필터는 한 번만 설치해야 한다");
    }
}
