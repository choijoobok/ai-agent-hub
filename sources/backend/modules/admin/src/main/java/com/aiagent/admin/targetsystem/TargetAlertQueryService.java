// @feat FEAT-001 FEAT-002
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.datasource.DataSourceAccess;
import com.aiagent.core.deployment.ConfigurationSnapshot;
import com.aiagent.support.deployment.ExternalDataSources;
import com.querydsl.core.types.dsl.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.support.deployment.TargetConnections;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class TargetAlertQueryService {
    public record Filter(String causeKind,String systemKey,String status,String dataSourceKey) {}
    public record Row(String alertId,String causeKind,String targetKey,String appliedSettingsId,String status,Instant firstDetectedAt,Instant lastCheckedAt,Instant recoveredAt,Map<String,Long> impactCounts,String coverage,String safeReason,List<String> authorizedLinks) {}
    public record Page(PageResponse<Row> page,Map<String,Long> summary,Instant checkedAt,List<String> allowedAppliedSettingsIds,String coverage) {}
    private static final PathBuilder<TargetAlert> A=new PathBuilder<>(TargetAlert.class,"targetAlert");
    private final JPAQueryFactory query;private final QueryDslBuilder builder;private final TargetSystemAccess access;private final TargetConnections loaded;
    private final ExternalDataSources dataSources;private final DataSourceAccess dataSourceAccess;
    private final ConfigurationDeploymentService deployment;private final ManagedLocks locks;private final Clock clock;
    public TargetAlertQueryService(JPAQueryFactory query,QueryDslBuilder builder,TargetSystemAccess access,TargetConnections loaded,ConfigurationDeploymentService deployment,ManagedLocks locks,Clock clock,ExternalDataSources dataSources,DataSourceAccess dataSourceAccess){this.query=query;this.builder=builder;this.access=access;this.loaded=loaded;this.deployment=deployment;this.locks=locks;this.clock=clock;this.dataSources=dataSources;this.dataSourceAccess=dataSourceAccess;}
    public Page getAlerts(SearchRequest<Filter> request){
        var params=request.params();String kind=params==null?null:params.causeKind();
        if(kind!=null&&!Set.of("TARGET_SYSTEM","TARGET_CONNECTION","DATA_SOURCE").contains(kind))throw ManagedErrorCode.VALIDATION.failure();
        var snapshots=new ArrayList<ConfigurationSnapshot>();
        if((kind==null||!kind.equals("DATA_SOURCE"))&&access.allows(ActionType.READ))snapshots.add(loaded);
        if((kind==null||kind.equals("DATA_SOURCE"))&&dataSourceAccess.allows(ActionType.READ))snapshots.add(dataSources);
        if(snapshots.isEmpty())throw ManagedErrorCode.FORBIDDEN.failure();
        locks.ordered(snapshots.stream().map(s->ConfigurationDeploymentService.boundary(s.environment())).toList());
        var configurations=new TreeSet<String>();BooleanExpression where=Expressions.FALSE;boolean unknown=false;
        for(var snapshot:snapshots){var boots=deployment.inspect(snapshot.environment()).stream().filter(b->"Y".equals(b.get("ALLOWED_YN"))).toList();
            var config=boots.stream().map(b->b.get("CONFIG_ID").toString()).distinct().toList();configurations.addAll(config);
            unknown|=boots.isEmpty()||boots.stream().anyMatch(b->!"Y".equals(b.get("READY_YN")));
            where=where.or(A.getString("environment").eq(snapshot.environment()).and(A.getString("configId").in(config)).and(A.getString("kind").in(snapshot==dataSources?List.of("DATASOURCE"):List.of("SYSTEM","CONNECTION"))));
        }
        var spec=QuerySpec.of("targetAlerts").from(A).col("targetKey",A.getString("key")).sortable().col("causeKind",A.getString("kind")).sortable()
            .col("status",A.getString("status")).sortable().col("lastCheckedAt",A.getDateTime("checked",Instant.class)).sortable()
            .defaultSort("lastCheckedAt",SortDir.desc).pk(A.getNumber("id",Long.class)).build();
        var conditions=new ArrayList<>(request.conditionsOrEmpty());
        if(params!=null){
            if(params.causeKind()!=null){String selectedKind=switch(params.causeKind()){case "TARGET_SYSTEM"->"SYSTEM";case "TARGET_CONNECTION"->"CONNECTION";case "DATA_SOURCE"->"DATASOURCE";default->throw ManagedErrorCode.UNAVAILABLE.failure();};conditions.add(new Condition("causeKind",Op.eq,selectedKind));}
            if(params.status()!=null)conditions.add(new Condition("status",Op.eq,params.status()));
        }
        var built=builder.build(new SearchRequest<>(conditions,request.sorts(),request.page(),null),spec,PageMode.offset);
        if(params!=null&&params.systemKey()!=null&&!params.systemKey().isBlank())where=where.and(A.getString("kind").ne("DATASOURCE")).and(A.getString("key").eq(params.systemKey()).or(A.getString("key").startsWith(params.systemKey()+"/")));
        if(params!=null&&params.dataSourceKey()!=null&&!params.dataSourceKey().isBlank())where=where.and(A.getString("kind").eq("DATASOURCE")).and(A.getString("key").eq(params.dataSourceKey()));
        var missing=new LinkedHashMap<String,Long>();for(String key:List.of("api","knowledge","tool","workflow"))missing.put(key,null);
        var rows=built.apply(query.select(A).where(where)).fetch().stream().map(a->new Row(a.id.toString(),a.kind.equals("DATASOURCE")?"DATA_SOURCE":a.kind.equals("SYSTEM")?"TARGET_SYSTEM":"TARGET_CONNECTION",a.key,a.configId,a.status,a.first,a.checked,a.recovered,Collections.unmodifiableMap(missing),"PARTIAL",a.status.equals("OPEN")?"MISSING":"CAUSE_RESOLVED",List.of(a.kind.equals("DATASOURCE")?"/admin/hub/data-source":"/admin/hub/target-system"))).toList();
        Long total=built.count(query).where(where).fetchOne();
        var summary=new TreeMap<String,Long>();
        for(String status:List.of("OPEN","RECOVERED","UNKNOWN")){Long count=built.count(query).where(where.and(A.getString("status").eq(status))).fetchOne();summary.put(status,count==null?0:count);}
        return new Page(built.respond(rows,total==null?0:total),summary,clock.instant(),List.copyOf(configurations),unknown?"FAILED":"PARTIAL");
    }
}
