// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
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
    public record Filter(String causeKind,String systemKey,String status) {}
    public record Row(String alertId,String causeKind,String targetKey,String appliedSettingsId,String status,Instant firstDetectedAt,Instant lastCheckedAt,Instant recoveredAt,Map<String,Long> impactCounts,String coverage,String safeReason,List<String> authorizedLinks) {}
    public record Page(PageResponse<Row> page,Map<String,Long> summary,Instant checkedAt,List<String> allowedAppliedSettingsIds,String coverage) {}
    private static final PathBuilder<TargetAlert> A=new PathBuilder<>(TargetAlert.class,"targetAlert");
    private final JPAQueryFactory query;private final QueryDslBuilder builder;private final TargetSystemAccess access;private final TargetConnections loaded;
    private final TargetDeploymentService deployment;private final ManagedLocks locks;private final Clock clock;
    public TargetAlertQueryService(JPAQueryFactory query,QueryDslBuilder builder,TargetSystemAccess access,TargetConnections loaded,TargetDeploymentService deployment,ManagedLocks locks,Clock clock){this.query=query;this.builder=builder;this.access=access;this.loaded=loaded;this.deployment=deployment;this.locks=locks;this.clock=clock;}
    public Page getAlerts(SearchRequest<Filter> request){
        access.require(ActionType.READ);locks.ordered(List.of(TargetDeploymentService.boundary(loaded.environment())));
        var boots=deployment.inspect(loaded.environment()).stream().filter(b->"Y".equals(b.get("ALLOWED_YN"))).toList();
        var configurations=boots.stream().map(b->b.get("CONFIG_ID").toString()).distinct().sorted().toList();
        boolean unknown=boots.isEmpty()||boots.stream().anyMatch(b->!"Y".equals(b.get("READY_YN")));
        var spec=QuerySpec.of("targetAlerts").from(A).col("targetKey",A.getString("key")).sortable().col("causeKind",A.getString("kind")).sortable()
            .col("status",A.getString("status")).sortable().col("lastCheckedAt",A.getDateTime("checked",Instant.class)).sortable()
            .defaultSort("lastCheckedAt",SortDir.desc).pk(A.getNumber("id",Long.class)).build();
        var conditions=new ArrayList<>(request.conditionsOrEmpty());var params=request.params();
        if(params!=null){
            if(params.causeKind()!=null){String kind=switch(params.causeKind()){case "TARGET_SYSTEM"->"SYSTEM";case "TARGET_CONNECTION"->"CONNECTION";default->throw ManagedErrorCode.UNAVAILABLE.failure();};conditions.add(new Condition("causeKind",Op.eq,kind));}
            if(params.status()!=null)conditions.add(new Condition("status",Op.eq,params.status()));
        }
        var built=builder.build(new SearchRequest<>(conditions,request.sorts(),request.page(),null),spec,PageMode.offset);
        var where=A.getString("environment").eq(loaded.environment()).and(A.getString("configId").in(configurations)).and(A.getString("kind").in("SYSTEM","CONNECTION"));
        if(params!=null&&params.systemKey()!=null&&!params.systemKey().isBlank())where=where.and(A.getString("key").eq(params.systemKey()).or(A.getString("key").startsWith(params.systemKey()+"/")));
        var missing=new LinkedHashMap<String,Long>();for(String key:List.of("api","knowledge","tool","workflow"))missing.put(key,null);
        var rows=built.apply(query.select(A).where(where)).fetch().stream().map(a->new Row(a.id.toString(),a.kind.equals("SYSTEM")?"TARGET_SYSTEM":"TARGET_CONNECTION",a.key,a.configId,a.status,a.first,a.checked,a.recovered,Collections.unmodifiableMap(missing),"PARTIAL",a.status.equals("OPEN")?"MISSING":"CAUSE_RESOLVED",List.of("/admin/hub/target-system"))).toList();
        Long total=built.count(query).where(where).fetchOne();
        var summary=new TreeMap<String,Long>();
        for(String status:List.of("OPEN","RECOVERED","UNKNOWN")){Long count=built.count(query).where(where.and(A.getString("status").eq(status))).fetchOne();summary.put(status,count==null?0:count);}
        return new Page(built.respond(rows,total==null?0:total),summary,clock.instant(),configurations,unknown?"FAILED":"PARTIAL");
    }
}
