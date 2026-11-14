// @feat FEAT-001 FEAT-302
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.ManagedLocks;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** 보호된 OS 운영 도구. 접속 비밀은 인자·출력에 포함하지 않는다. */
public final class TargetDeploymentTool {
    private TargetDeploymentTool() {}
    private static String required(String key){var value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("운영 환경변수가 필요하다: "+key);return value;}
    public static void main(String[] args){
        if(args.length!=2&&args.length!=7)throw new IllegalArgumentException("inspect environment 또는 allow environment bootId expectedVersion commandKey operator retainExisting 형식이 필요하다");
        var source=new DriverManagerDataSource(required("TARGET_DEPLOYMENT_JDBC"),required("TARGET_DEPLOYMENT_USER"),System.getenv().getOrDefault("TARGET_DEPLOYMENT_PASSWORD",""));
        if(!Objects.requireNonNull(source.getUrl()).startsWith("jdbc:postgresql:"))throw new IllegalArgumentException("PostgreSQL 운영 연결이 필요하다");
        var service=new ConfigurationDeploymentService(source,new ManagedLocks(source),Clock.systemUTC());
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        Object result=tx.execute(status->{
            if(args.length==2&&args[0].equals("inspect"))return service.inspect(args[1]);
            if(args.length!=7||!args[0].equals("allow")||!Set.of("true","false").contains(args[6]))throw new IllegalArgumentException("운영 명령이 올바르지 않다");
            return service.allow(args[1],UUID.fromString(args[2]),Long.parseLong(args[3]),UUID.fromString(args[4]),args[5],Boolean.parseBoolean(args[6]));
        });
        System.out.println(result);
    }
}
