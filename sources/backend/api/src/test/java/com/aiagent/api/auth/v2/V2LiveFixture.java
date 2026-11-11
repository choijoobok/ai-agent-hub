// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.Identity;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import com.aiagent.api.auth.boundary.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionSystemException;

/** 전용 실제 PG/JPA·Redis만 사용하는 공통 fixture. Portal 계정 업무는 구현하지 않는다. */
final class V2LiveFixture implements AutoCloseable {
    final com.zaxxer.hikari.HikariDataSource source;
    final JdbcTemplate jdbc;
    final LocalContainerEntityManagerFactoryBean emf;
    final FaultManager manager;
    final LettuceConnectionFactory redis;
    final JdbcSessionAdmissionLedger ledger;
    final SessionTransactions transactions;
    final SessionStoreAdapter store;
    final Provider provider;
    final Map<AuthDomain,Settings> selected=new EnumMap<>(AuthDomain.class);
    final Map<AuthDomain,SessionAdmissionsV2.Producer> producers=new EnumMap<>(AuthDomain.class);
    SessionAuthority authority;
    static final class FaultManager extends JpaTransactionManager {
        boolean unknown;
        FaultManager(jakarta.persistence.EntityManagerFactory factory){super(factory);}
        @Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);if(unknown){unknown=false;throw new TransactionSystemException("시험 최종 commit 결과 불명");}}
    }
    V2LiveFixture(Provider provider)throws Exception{
        this.provider=provider;String url=System.getenv("AUTH_V2_TEST_JDBC");
        if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/auth308[a-z0-9]+"))throw new IllegalStateException("전용 실제 PostgreSQL 환경이 필요하다");
        var initializer=new DriverManagerDataSource(url,System.getenv("AUTH_V2_TEST_USER"),System.getenv("AUTH_V2_TEST_PASSWORD"));
        String schema="auth308_"+UUID.randomUUID().toString().replace("-","");new JdbcTemplate(initializer).execute("CREATE SCHEMA "+schema);
        source=new com.zaxxer.hikari.HikariDataSource();source.setJdbcUrl(url+"?currentSchema="+schema);source.setUsername(System.getenv("AUTH_V2_TEST_USER"));source.setPassword(System.getenv("AUTH_V2_TEST_PASSWORD"));source.setMaximumPoolSize(8);source.setMinimumIdle(0);source.setConnectionTimeout(4000);source.setConnectionInitSql("SET lock_timeout='3000ms'");jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME VARCHAR(255) PRIMARY KEY,NEXT_VAL BIGINT NOT NULL)");
        try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new FileSystemResource("../bootstrap/src/main/resources/db/changelog/modules/platform/002-auth-session-v2.sql"));}
        jdbc.execute("CREATE TABLE AUTH307_CREDENTIAL(ID VARCHAR(80) PRIMARY KEY,VALUETEXT VARCHAR(80))");
        jdbc.execute("CREATE TABLE AUTH_OWNER(AUTH_DOMAIN VARCHAR(8),SUBJECT_ID VARCHAR(50),ENABLED BOOLEAN,ALLOW_ACTION BOOLEAN,PRIMARY KEY(AUTH_DOMAIN,SUBJECT_ID))");for(AuthDomain domain:AuthDomain.values())jdbc.update("INSERT INTO AUTH_OWNER VALUES(?,'owner',true,true)",domain.name());
        for(AuthDomain domain:AuthDomain.values()){
            selected.put(domain,new Settings(provider,UUID.randomUUID(),"live-"+domain));producers.put(domain,SessionAdmissionsV2.producer(domain,"live-"+domain));var settings=selected.get(domain);
            jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE VALUES(?,?,?,?, 'ACTIVE',clock_timestamp())",domain.name(),provider.name(),settings.epoch(),settings.storeInstance());
            String table=domain==AuthDomain.ADMIN?"SPRING_SESSION":"AI_PORTAL_SESSION";
            // Portal payload는308 fixture DDL이며 제품 ENT252/253 설치를 주장하지 않는다.
            jdbc.execute("CREATE TABLE "+table+"(PRIMARY_ID CHAR(36) PRIMARY KEY,SESSION_ID CHAR(36) UNIQUE,CREATION_TIME BIGINT NOT NULL,LAST_ACCESS_TIME BIGINT NOT NULL,MAX_INACTIVE_INTERVAL INT NOT NULL,EXPIRY_TIME BIGINT NOT NULL,PRINCIPAL_NAME VARCHAR(100))");
            jdbc.execute("CREATE TABLE "+table+"_ATTRIBUTES(SESSION_PRIMARY_ID CHAR(36) REFERENCES "+table+"(PRIMARY_ID) ON DELETE CASCADE,ATTRIBUTE_NAME VARCHAR(200),ATTRIBUTE_BYTES BYTEA NOT NULL,PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME))");
        }
        emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(source);emf.setPackagesToScan("com.aiagent.api.auth.boundary.fixture");emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none"));emf.afterPropertiesSet();
        manager=new FaultManager(emf.getObject());manager.setDataSource(source);transactions=new SessionTransactions(source,manager);ledger=new JdbcSessionAdmissionLedger(source,manager);
        String port=System.getenv("AUTH_V2_REDIS_PORT"),password=System.getenv("AUTH_V2_REDIS_PASSWORD");if(port==null||password==null)throw new IllegalStateException("전용 실제 Redis 환경이 필요하다");
        var config=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(port));config.setPassword(RedisPassword.of(password));var client=org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).shutdownTimeout(Duration.ZERO).clientOptions(io.lettuce.core.ClientOptions.builder().socketOptions(io.lettuce.core.SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build()).build()).build();redis=new LettuceConnectionFactory(config,client);redis.afterPropertiesSet();redis.start();
        store=provider==Provider.JDBC?new JdbcSessionStore(source,manager,Set.of(AuthDomain.ADMIN,AuthDomain.PORTAL)):new RedisSessionStore(redis);
        authority=(context,exclusive,command)->{
            String function=exclusive?"pg_advisory_xact_lock":"pg_advisory_xact_lock_shared";jdbc.execute("SELECT "+function+"("+(308000+context.domain().ordinal())+")");
            if(context.principal()!=null){Boolean enabled=jdbc.queryForObject("SELECT ENABLED FROM AUTH_OWNER WHERE AUTH_DOMAIN=? AND SUBJECT_ID=?",Boolean.class,context.domain().name(),context.principal().subjectId());if(!Boolean.TRUE.equals(enabled))throw new AuthBoundaryAssembly.Rejected(401);
                if(context.requiredAction()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT ALLOW_ACTION FROM AUTH_OWNER WHERE AUTH_DOMAIN=? AND SUBJECT_ID=?",Boolean.class,context.domain().name(),context.principal().subjectId())))throw new AuthBoundaryAssembly.Rejected(403);}
            command.run();
        };
    }
    SessionRuntime runtime(){return runtime(store,authority);}
    SessionRuntime runtime(SessionStoreAdapter adapter,SessionAuthority owner){return new SessionRuntime(ledger,adapter,transactions,selected,new SessionRuntime.AuthorityBinding(source,manager,owner),Duration.ofMinutes(30));}
    ModuleApiCatalog.Operation operation(AuthDomain domain,Capability cap){
        String module=domain==AuthDomain.ADMIN?"system":"portal";String name=switch(cap){case PREPARE_CSRF->"getCsrf";case LOGIN->"login";case PASSWORD_CHANGE->"changePassword";case LOGOUT->"logout";default->"getMe";};
        String layer=Set.of(Capability.LOGIN,Capability.PREPARE_CSRF).contains(cap)?"public":"common";
        Set<AuthLevel> levels=Set.of(Capability.LOGIN,Capability.PREPARE_CSRF).contains(cap)?Set.of(AuthLevel.values()):Set.of(AuthLevel.AUTHENTICATED,AuthLevel.PASSWORD_CHANGE_REQUIRED);
        return new ModuleApiCatalog.Operation(domain+name,cap==Capability.PREPARE_CSRF||cap==Capability.READ?"GET":"POST","/api/"+module+"/"+layer+"/auth/"+name,ModuleApiCatalog.Domain.valueOf(domain.name()),levels,null,true,cap,null,null);
    }
    SessionRuntime.Scope scope(SessionRuntime runtime,AuthDomain domain,Capability cap,String cookie){return runtime.open(new SessionExchange(domain,UUID.randomUUID().toString(),cap,producers.get(domain)),operation(domain,cap),cookie);}
    record Issued(String cookie,String csrf,Ref ref){}
    Issued emit(SessionRuntime runtime,SessionRuntime.Scope scope){if(runtime.finish(scope)!=Outcome.DELIVERABLE)throw new AssertionError("발급이 확정되지 않았다");String[] values=new String[2];scope.emitCookies((domain,cookie,csrf)->{values[0]=cookie;values[1]=csrf;});return new Issued(values[0],values[1],scope.current().orElseThrow().ref());}
    Issued anonymous(SessionRuntime runtime,AuthDomain domain){var scope=scope(runtime,domain,Capability.PREPARE_CSRF,null);runtime.execute(scope,()->{scope.prepareCsrf();return null;});return emit(runtime,scope);}
    Identity identity(AuthDomain domain,AuthLevel level){return new Identity(new PrincipalRef(domain,"owner"),level,"LOCAL",domain==AuthDomain.PORTAL?1L:null,ledger.now(),"이름","ko");}
    SessionRuntime.Scope stageLogin(SessionRuntime runtime,AuthDomain domain,Issued entry,SessionRuntime.SlotPolicy policy){var scope=scope(runtime,domain,Capability.LOGIN,entry.cookie());runtime.execute(scope,()->{scope.publish(producers.get(domain).login(scope.exchange(),entry.ref(),identity(domain,AuthLevel.AUTHENTICATED),ledger.now().plusSeconds(60)),policy);return null;});return scope;}
    Issued login(SessionRuntime runtime,AuthDomain domain,Issued entry){return emit(runtime,stageLogin(runtime,domain,entry,new SessionRuntime.SlotPolicy(false,1,false)));}
    void effect(String id){var entityManager=EntityManagerFactoryUtils.getTransactionalEntityManager(emf.getObject());entityManager.persist(new com.aiagent.api.auth.boundary.fixture.Auth307Credential(id,"effect"));entityManager.flush();}
    BoundaryResponseWriter responses(){var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var messages=org.mockito.Mockito.mock(com.aiagent.api.error.ErrorMessages.class);org.mockito.Mockito.when(messages.of(org.mockito.ArgumentMatchers.any())).thenAnswer(call->((com.aiagent.core.error.ErrorCode)call.getArgument(0)).defaultMessage());return new BoundaryResponseWriter(mapper,new com.aiagent.api.error.ApiErrorWriter(mapper,messages));}
    @Override public void close(){redis.destroy();emf.destroy();source.close();}
}
