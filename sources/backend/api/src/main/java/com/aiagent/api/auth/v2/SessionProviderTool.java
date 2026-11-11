// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import com.aiagent.core.security.v2.SessionV2.Provider;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

/** API/화면 없이 운영자가 명시 실행하는 초기설정·배출·expected epoch CAS. 연결 비밀은 환경변수만 쓴다. */
public final class SessionProviderTool {
    private SessionProviderTool(){}
    private static String environment(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("운영 도구 환경변수가 필요하다: "+key);return value;}
    private static Settings settings(String[] args,int start){return new Settings(Provider.valueOf(args[start].toUpperCase(Locale.ROOT)),UUID.fromString(args[start+1]),args[start+2]);}
    public static void main(String[] args){
        if(args.length!=5&&args.length!=8)throw new IllegalArgumentException("명령 domain provider epoch store-instance [새provider 새epoch 새store-instance] 형식이 필요하다");
        var source=new DriverManagerDataSource(environment("AUTH_SESSION_JDBC"),environment("AUTH_SESSION_USER"),environment("AUTH_SESSION_PASSWORD"));
        if(!Objects.requireNonNull(source.getUrl()).startsWith("jdbc:postgresql:"))throw new IllegalArgumentException("운영 설정판은 PostgreSQL만 지원한다");
        execute(source,args);System.out.println("인증 저장소 운영 명령을 확정했다");
    }
    public static void execute(javax.sql.DataSource source,String[] args){
        if(args.length!=5&&args.length!=8)throw new IllegalArgumentException("운영 명령 인자 수가 올바르지 않다");
        AuthDomain domain=AuthDomain.valueOf(args[1]);Settings expected=settings(args,2);
        if(expected.storeInstance().isBlank()||expected.storeInstance().length()>100)throw new IllegalArgumentException("설치 식별자가 올바르지 않다");
        var manager=new DataSourceTransactionManager(source);var tool=new SessionProviderTransition(source,manager);
        switch(args[0]){
            case "init"->{if(args.length!=5)throw new IllegalArgumentException("초기화 인자가 올바르지 않다");var transactions=new SessionTransactions(source,manager);var jdbc=new JdbcTemplate(source);transactions.required(()->{
                if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT WHERE AUTH_DOMAIN=?",Long.class,domain.name())!=0)throw new IllegalStateException("기존 수락 원장이 있는 영역은 초기화할 수 없다");
                jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE(AUTH_DOMAIN,PROVIDER_TYPE,PROVIDER_EPOCH,STORE_INSTANCE,STATE_CD,CHANGED_AT) VALUES(?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP)",domain.name(),expected.provider().name(),expected.epoch(),expected.storeInstance());return null;});}
            case "drain"->{if(args.length!=5)throw new IllegalArgumentException("배출 인자가 올바르지 않다");tool.drain(domain,expected);}
            case "switch"->{if(args.length!=8)throw new IllegalArgumentException("전환 인자가 올바르지 않다");tool.switchDrained(domain,expected,settings(args,5));}
            case "cleanup"->{if(args.length!=5)throw new IllegalArgumentException("정리 인자가 올바르지 않다");var ledger=new JdbcSessionAdmissionLedger(source,manager);if(!ledger.settings(domain,false).equals(expected))throw new IllegalStateException("기대 저장소 설정판이 다르다");if(expected.provider()==Provider.JDBC)new JdbcSessionStore(source,manager,Set.of(domain)).cleanupExpired();ledger.cleanup();}
            default->throw new IllegalArgumentException("init/drain/switch/cleanup 명령만 허용한다");
        }
    }
}
