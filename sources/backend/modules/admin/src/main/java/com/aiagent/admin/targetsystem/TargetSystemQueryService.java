// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.support.deployment.*;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class TargetSystemQueryService {
    static final PathBuilder<TargetSystem> S=new PathBuilder<>(TargetSystem.class,"targetSystem");
    static final PathBuilder<TargetConnection> C=new PathBuilder<>(TargetConnection.class,"targetConnection");
    static final PathBuilder<TargetDiagnosis> D=new PathBuilder<>(TargetDiagnosis.class,"targetDiagnosis");
    public record Filter(String systemName,String systemKey,Boolean configurationMismatch) {}
    public record ConnectionFilter(String systemId,Boolean validOnly,String keyword) {}
    public record Override(String nameOverride,String descriptionOverride) {}
    public record Row(String id,String version,String systemKey,String systemName,String description,String systemNameOverride,String descriptionOverride,
        String yamlSystemName,String yamlDescription,String systemNameSource,String descriptionSource,Boolean configurationMismatch,String mismatchReason,String systemDefinitionStatus,String appliedSettingsId,Instant checkedAt,
        String regId,Instant regDtm,String modId,Instant modDtm,List<ConnectionRow> connections) {}
    public record ConnectionRow(String systemId,String connectionId,String connectionName,String description,Override metadataOverrides,
        String yamlName,String yamlDescription,Boolean configurationMismatch,String mismatchReason,String appliedSettingsId,Instant checkedAt,ConnectionDisplay currentDisplay,
        Boolean testConfigured,boolean testAllowed,String disabledReason,String regId,Instant regDtm,String modId,Instant modDtm) {}
    private final JPAQueryFactory query;private final QueryDslBuilder builder;private final TargetConnections loaded;private final TargetDeploymentService deployment;private final TargetSystemAccess access;
    public TargetSystemQueryService(JPAQueryFactory query,QueryDslBuilder builder,TargetConnections loaded,TargetDeploymentService deployment,TargetSystemAccess access){this.query=query;this.builder=builder;this.loaded=loaded;this.deployment=deployment;this.access=access;}
    private StringExpression name(){return S.getString("nameOverride").coalesce(S.getString("yamlName"),S.getString("systemKey"));}
    private BooleanExpression ownDiagnosis(){return D.getString("environment").eq(loaded.environment()).and(D.getString("configId").eq(loaded.settingsId()));}
    private BooleanExpression missing(){
        var system = D.getString("kind").eq("SYSTEM").and(D.getString("key").eq(S.getString("systemKey")));
        var connection = D.getString("kind").eq("CONNECTION").and(JPAExpressions.selectOne().from(C)
            .where(C.getNumber("systemId",Long.class).eq(S.getNumber("id",Long.class)),D.getString("key").eq(S.getString("systemKey").concat("/").concat(C.getString("connectionId")))).exists());
        return JPAExpressions.selectOne().from(D)
            .where(ownDiagnosis(), D.getString("mismatch").eq("Y"), system.or(connection)).exists();
    }
    private BooleanExpression matched(){
        var root=JPAExpressions.selectOne().from(D).where(ownDiagnosis(),D.getString("kind").eq("SYSTEM"),D.getString("key").eq(S.getString("systemKey")),D.getString("mismatch").eq("N")).exists();
        var child=JPAExpressions.selectOne().from(D).where(ownDiagnosis(),D.getString("kind").eq("CONNECTION"),D.getString("key").eq(S.getString("systemKey").concat("/").concat(C.getString("connectionId"))),D.getString("mismatch").eq("N")).exists();
        return root.and(JPAExpressions.selectOne().from(C).where(C.getNumber("systemId",Long.class).eq(S.getNumber("id",Long.class)),child.not()).notExists());
    }
    QuerySpec spec(boolean ready){return QuerySpec.of("targetSystem").from(S).col("id",S.getNumber("id",Long.class)).sortable()
        .col("systemName",name()).sortable().col("systemKey",S.getString("systemKey")).sortable()
        .col("modifiedAt",S.getDateTime("modDtm",Instant.class)).sortable()
        .col("regId",S.getString("regId")).sortable().col("regDtm",S.getDateTime("regDtm",Instant.class)).sortable()
        .col("modId",S.getString("modId")).sortable().col("modDtm",S.getDateTime("modDtm",Instant.class)).sortable()
        .derived("configurationMismatch",(op,value)->{if(op==Op.isNull&&value==null)return ready?missing().not().and(matched().not()):Expressions.TRUE;if(op!=Op.eq||!(value instanceof Boolean))throw ManagedErrorCode.VALIDATION.failure();return ready?((Boolean)value?missing():matched()):Expressions.FALSE;})
        .defaultSort("systemName",SortDir.asc).pk(S.getNumber("id",Long.class)).build();}
    SearchRequest<Filter> filters(SearchRequest<Filter> request){
        var all=new ArrayList<>(request.conditionsOrEmpty());var params=request.params();
        if(params!=null){if(params.systemName()!=null&&!params.systemName().isBlank())all.add(new Condition("systemName",Op.contains,params.systemName()));if(params.systemKey()!=null&&!params.systemKey().isBlank())all.add(new Condition("systemKey",Op.contains,params.systemKey()));if(params.configurationMismatch()!=null)all.add(new Condition("configurationMismatch",Op.eq,params.configurationMismatch()));}
        return new SearchRequest<>(all,request.sorts(),request.page(),null);
    }
    public PageResponse<Row> getList(SearchRequest<Filter> request){
        boolean ready=deployment.ready(loaded);
        var built=builder.build(filters(request),spec(ready),PageMode.offset);
        var roots=built.apply(query.select(S)).fetch();
        var ids=roots.stream().map(s->s.id).toList();
        var children=ids.isEmpty()?List.<TargetConnection>of():query.selectFrom(C)
            .where(C.getNumber("systemId",Long.class).in(ids)).orderBy(C.getString("connectionId").asc()).fetch();
        var grouped=children.stream().collect(java.util.stream.Collectors.groupingBy(c->c.systemId));
        var diagnoses=diagnoses(roots,children,ready);
        var rows=roots.stream().map(s->row(s,grouped.getOrDefault(s.id,List.of()),diagnoses,false,false)).toList();
        Long count=built.count(query).fetchOne();
        return built.respond(rows,count==null?0:count);
    }
    TargetSystem root(String id){var root=query.selectFrom(S).where(S.getNumber("id",Long.class).eq(ManagedCommand.positive(id))).fetchOne();if(root==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return root;}
    public Row getOne(String id){
        var root=root(id);var children=query.selectFrom(C).where(C.getNumber("systemId",Long.class).eq(root.id)).orderBy(C.getString("connectionId").asc()).fetch();
        return row(root,children,diagnoses(List.of(root),children,deployment.ready(loaded)),true,access.allows(ActionType.EXECUTE));
    }
    private Map<String,TargetDiagnosis> diagnoses(List<TargetSystem> roots,List<TargetConnection> children,boolean ready){
        if(!ready||roots.isEmpty())return Map.of();
        var keys=new HashSet<String>();var parents=new HashMap<Long,String>();
        for(var root:roots){keys.add(root.systemKey);parents.put(root.id,root.systemKey);}
        for(var child:children)keys.add(parents.get(child.systemId)+"/"+child.connectionId);
        var result=new HashMap<String,TargetDiagnosis>();
        for(var d:query.selectFrom(D).where(ownDiagnosis(),D.getString("key").in(keys)).fetch())
            if(result.putIfAbsent(d.kind+"/"+d.key,d)!=null)throw ManagedErrorCode.UNAVAILABLE.failure();
        return result;
    }
    private Row row(TargetSystem root,List<TargetConnection> children,Map<String,TargetDiagnosis> diagnoses,boolean includeConnections,boolean execute){
        var d=diagnoses.get("SYSTEM/"+root.systemKey);
        var connections=children.stream().map(c->connection(root,c,diagnoses,execute)).toList();
        Boolean mismatch=d==null||d.mismatch==null?null:d.mismatch.equals("Y");
        if(connections.stream().anyMatch(c->Boolean.TRUE.equals(c.configurationMismatch())))mismatch=true;
        else if(!Boolean.TRUE.equals(mismatch)&&connections.stream().anyMatch(c->c.configurationMismatch()==null))mismatch=null;
        return new Row(root.id.toString(),root.version.toString(),root.systemKey,display(root.nameOverride,root.yamlName,root.systemKey),display(root.descriptionOverride,root.yamlDescription,null),root.nameOverride,root.descriptionOverride,root.yamlName,root.yamlDescription,source(root.nameOverride,root.yamlName),source(root.descriptionOverride,root.yamlDescription),mismatch,mismatch==null?"UNKNOWN":mismatch?"MISSING":"MATCHED",d==null?"UNKNOWN":"Y".equals(d.mismatch)?"MISSING":"PRESENT",loaded.settingsId(),d==null?null:d.checkedAt,root.regId,root.regDtm,root.modId,root.modDtm,includeConnections?connections:List.of());
    }
    private ConnectionRow connection(TargetSystem root,TargetConnection child,Map<String,TargetDiagnosis> diagnoses,boolean execute){
        var d=diagnoses.get("CONNECTION/"+root.systemKey+"/"+child.connectionId);Boolean mismatch=d==null||d.mismatch==null?null:d.mismatch.equals("Y");
        var system=loaded.systems().get(root.systemKey);var current=system==null?null:system.connections().get(child.connectionId);
        boolean available=Boolean.FALSE.equals(mismatch)&&current!=null;
        var display=available?current.display():new ConnectionDisplay(null,null,"UNAVAILABLE",mismatch==null?"UNKNOWN":"MISSING");
        boolean configured=current!=null&&current.test()!=null;
        return new ConnectionRow(root.id.toString(),child.connectionId,display(child.nameOverride,child.yamlName,child.connectionId),display(child.descriptionOverride,child.yamlDescription,null),new Override(child.nameOverride,child.descriptionOverride),child.yamlName,child.yamlDescription,mismatch,d==null?"UNKNOWN":d.reason,loaded.settingsId(),d==null?null:d.checkedAt,display,current==null?null:configured,available&&configured&&execute,!execute?"FORBIDDEN":available?(configured?null:"NOT_CONFIGURED"):"UNAVAILABLE",child.regId,child.regDtm,child.modId,child.modDtm);
    }
    public PageResponse<ConnectionRow> getConnections(SearchRequest<ConnectionFilter> request){
        if(request.params()==null)throw ManagedErrorCode.VALIDATION.failure();var params=request.params();var root=root(params.systemId());boolean ready=deployment.ready(loaded);
        var spec=QuerySpec.of("targetConnection").from(C).col("connectionId",C.getString("connectionId")).sortable().col("connectionName",C.getString("nameOverride").coalesce(C.getString("yamlName"),C.getString("connectionId"))).sortable().defaultSort("connectionId",SortDir.asc).pk(C.getNumber("recordId",Long.class)).build();
        var conditions=new ArrayList<>(request.conditionsOrEmpty());if(params.keyword()!=null&&!params.keyword().isBlank())conditions.add(new Condition("connectionName",Op.contains,params.keyword()));
        var built=builder.build(new SearchRequest<>(conditions,request.sorts(),request.page(),null),spec,PageMode.offset);
        var valid=JPAExpressions.selectOne().from(D).where(ownDiagnosis(),D.getString("kind").eq("CONNECTION"),D.getString("key").eq(Expressions.asString(root.systemKey+"/").concat(C.getString("connectionId"))),D.getString("mismatch").eq("N")).exists();
        var where=C.getNumber("systemId",Long.class).eq(root.id);if(Boolean.TRUE.equals(params.validOnly()))where=where.and(ready?valid:Expressions.FALSE);
        var children=built.apply(query.select(C).where(where)).fetch();
        var diagnoses=diagnoses(List.of(root),children,ready);boolean execute=access.allows(ActionType.EXECUTE);
        var rows=children.stream().map(c->connection(root,c,diagnoses,execute)).toList();
        Long count=built.count(query).where(where).fetchOne();return built.respond(rows,count==null?0:count);
    }
    static String display(String override,String yaml,String fallback){return override!=null?override:yaml!=null?yaml:fallback;}
    List<TargetCleanup.Candidate> cleanupCandidates(TargetCleanup.Query request){
        var input=new SearchRequest<>(request.conditions(),List.of(),null,null);
        if(request.scope().equals("SYSTEM")){
            var built=builder.build(input,spec(true),PageMode.all);
            var roots=built.apply(query.select(S)).fetch();
            if(roots.size()>PageSpec.ALL_LIMIT)throw ManagedErrorCode.VALIDATION.failure();
            var diagnoses=diagnoses(roots,List.of(),true);
            return roots.stream().filter(s->{var d=diagnoses.get("SYSTEM/"+s.systemKey);return d!=null&&"Y".equals(d.mismatch);})
                .map(s->new TargetCleanup.Candidate(new TargetCleanup.Confirmation(s.id.toString(),"SYSTEM",null,s.version.toString()),display(s.nameOverride,s.yamlName,s.systemKey),null,diagnoses.get("SYSTEM/"+s.systemKey).id.toString()))
                .sorted(Comparator.comparing(c->ManagedCommand.positive(c.target().id()))).toList();
        }
        var parent=root(request.parentId());
        var spec=QuerySpec.of("targetCleanupConnection").from(C).col("connectionId",C.getString("connectionId")).sortable()
            .col("connectionName",C.getString("nameOverride").coalesce(C.getString("yamlName"),C.getString("connectionId"))).sortable()
            .defaultSort("connectionId",SortDir.asc).pk(C.getNumber("recordId",Long.class)).build();
        var children=builder.build(input,spec,PageMode.all).apply(query.select(C).where(C.getNumber("systemId",Long.class).eq(parent.id))).fetch();
        if(children.size()>PageSpec.ALL_LIMIT)throw ManagedErrorCode.VALIDATION.failure();
        var diagnoses=diagnoses(List.of(parent),children,true);
        return children.stream().filter(c->{var d=diagnoses.get("CONNECTION/"+parent.systemKey+"/"+c.connectionId);return d!=null&&"Y".equals(d.mismatch);})
            .map(c->new TargetCleanup.Candidate(new TargetCleanup.Confirmation(c.recordId.toString(),"CONNECTION",parent.id.toString(),parent.version.toString()),display(c.nameOverride,c.yamlName,c.connectionId),c.connectionId,diagnoses.get("CONNECTION/"+parent.systemKey+"/"+c.connectionId).id.toString()))
            .sorted(Comparator.comparing(c->ManagedCommand.positive(c.target().id()))).toList();
    }
    private static String source(String override,String yaml){return override!=null?"ADMIN":yaml!=null?"YAML":"KEY";}
}
