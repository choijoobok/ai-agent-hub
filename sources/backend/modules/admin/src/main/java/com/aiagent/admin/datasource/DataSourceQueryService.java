// @feat FEAT-002
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.support.deployment.ExternalDataSources;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class DataSourceQueryService {
    private static final PathBuilder<DataSourceRecord> R=new PathBuilder<>(DataSourceRecord.class,"dataSource");
    private static final PathBuilder<ConfigurationDiagnosis> D=new PathBuilder<>(ConfigurationDiagnosis.class,"dsDiagnosis");
    public record Filter(String dataSourceKey,String alias,String dbmsCode,String sourceType,Boolean configurationMismatch){}
    public record Key(String id){}
    public record Row(String id,String dataSourceKey,String alias,String dbmsCode,String dbmsVersion,String sourceType,String syncMethodNote,String timeZoneId,String description,String dataBasis,String managementVersion,
        Boolean configurationMismatch,String reason,String appliedSettingsId,Instant checkedAt,String connectionStatus,String parserSupport,String comparisonSummary,String regId,Instant regDtm,String modId,Instant modDtm){}
    public record Detail(Row row,Map<String,Object> values,List<Map<String,Object>> groups,List<Map<String,Object>> guides,List<Map<String,Object>> groupCandidates,DataSourceReferenceService.Projection catalogReferences){}
    private final JPAQueryFactory query;private final QueryDslBuilder builder;private final JdbcTemplate jdbc;
    private final ExternalDataSources loaded;private final ConfigurationDeploymentService deployment;private final DataSourceService owner;private final DataSourceReferenceService refs;
    public DataSourceQueryService(JPAQueryFactory query,QueryDslBuilder builder,DataSource source,ExternalDataSources loaded,ConfigurationDeploymentService deployment,DataSourceService owner,DataSourceReferenceService refs){this.query=query;this.builder=builder;jdbc=new JdbcTemplate(source);this.loaded=loaded;this.deployment=deployment;this.owner=owner;this.refs=refs;}
    private BooleanExpression matchesDiagnosis(String value){return JPAExpressions.selectOne().from(D).where(D.getString("environment").eq(loaded.environment()),D.getString("configId").eq(loaded.settingsId()),D.getString("kind").eq("DATASOURCE"),D.getString("key").eq(R.getString("key")),D.getString("mismatch").eq(value)).exists();}
    private QuerySpec spec(boolean ready){var b=QuerySpec.of("dataSource").from(R).col("id",R.getNumber("id",Long.class)).sortable();
        for(String field:List.of("alias","dbmsCode","sourceType","dataSourceKey"))b.col(field,R.getString(field.equals("dataSourceKey")?"key":field)).sortable();
        b.col("modDtm",R.getDateTime("modDtm",Instant.class)).sortable();
        return b.derived("configurationMismatch",(op,value)->{if(op==Op.isNull&&value==null)return ready?matchesDiagnosis("Y").not().and(matchesDiagnosis("N").not()):Expressions.TRUE;
            if(op!=Op.eq||!(value instanceof Boolean))throw ManagedErrorCode.VALIDATION.failure();return ready?matchesDiagnosis((Boolean)value?"Y":"N"):Expressions.FALSE;}).defaultSort("alias",SortDir.asc).pk(R.getNumber("id",Long.class)).build();
    }
    private SearchRequest<Void> filters(SearchRequest<Filter> r){var conditions=new ArrayList<>(r.conditionsOrEmpty());if(r.params()!=null){var p=r.params();
        for(var pair:List.of(new String[]{"alias",p.alias()},new String[]{"dataSourceKey",p.dataSourceKey()},new String[]{"dbmsCode",p.dbmsCode()},new String[]{"sourceType",p.sourceType()}))if(pair[1]!=null&&!pair[1].isBlank())conditions.add(new Condition(pair[0],pair[0].equals("alias")||pair[0].equals("dataSourceKey")?Op.contains:Op.eq,pair[1]));
        if(p.configurationMismatch()!=null)conditions.add(new Condition("configurationMismatch",Op.eq,p.configurationMismatch()));}
        return new SearchRequest<>(conditions,r.sorts(),r.page(),null);
    }
    public PageResponse<Row> getList(SearchRequest<Filter> r){var built=builder.build(filters(r),spec(deployment.ready(loaded)),PageMode.offset);var rows=built.apply(query.select(R)).fetch().stream().map(this::row).toList();Long total=built.count(query).fetchOne();return built.respond(rows,total==null?0:total);}
    DataSourceRecord root(String id){var root=query.selectFrom(R).where(R.getNumber("id",Long.class).eq(ManagedCommand.positive(id))).fetchOne();if(root==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return root;}
    Row row(DataSourceRecord r){
        var status=diagnosis(r.key);var current=loaded.entries().get(r.key);String connection=Boolean.FALSE.equals(status.mismatch())&&current!=null?current.status():Boolean.TRUE.equals(status.mismatch())?"MISSING":"UNKNOWN";
        return new Row(r.id.toString(),r.key,r.alias,r.dbmsCode,r.dbmsVersion,r.sourceType,r.syncMethodNote,r.timeZoneId,r.description,r.dataBasis,r.version.toString(),status.mismatch(),status.reason(),loaded.settingsId(),status.at(),connection,"NOT_CONNECTED","NOT_CONNECTED",r.regId,r.regDtm,r.modId,r.modDtm);
    }
    public record Diagnosis(Boolean mismatch,String reason,Instant at,String id){}
    Diagnosis diagnosis(String key){if(!deployment.ready(loaded))return new Diagnosis(null,"BOOT_NOT_READY",null,null);
        var found=jdbc.query("SELECT MISMATCH_YN,SAFE_REASON,CHECKED_DTM,DIAGNOSIS_ID FROM AI_ADMIN_CONFIG_DIAGNOSIS WHERE ENVIRONMENT_ID=? AND CONFIG_ID=? AND TARGET_KIND='DATASOURCE' AND TARGET_KEY=?",(rs,n)->new Diagnosis(rs.getString(1)==null?null:rs.getString(1).equals("Y"),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getString(4)),loaded.environment(),loaded.settingsId(),key);
        return found.size()==1?found.getFirst():new Diagnosis(null,"DIAGNOSIS_UNKNOWN",null,null);
    }
    public Detail getOne(String id){var r=root(id);var detail=new Detail(row(r),owner.values(id),jdbc.queryForList("SELECT G.GROUP_ID::text AS \"groupId\",U.GROUP_NM AS \"groupName\",G.ACTIVE_YN AS \"mappingActiveYn\",G.REG_ID AS \"regId\",G.REG_DTM AS \"regDtm\",G.MOD_ID AS \"modId\",G.MOD_DTM AS \"modDtm\" FROM AI_ADMIN_DATA_SOURCE_GROUP G JOIN AI_ADMIN_USER_GROUP U ON U.GROUP_ID=G.GROUP_ID WHERE G.DATA_SOURCE_ID=? ORDER BY G.GROUP_ID",Long.valueOf(id)),guides(id),jdbc.queryForList("SELECT CAST(GROUP_ID AS varchar) AS \"groupId\",GROUP_NM AS \"groupName\" FROM AI_ADMIN_USER_GROUP ORDER BY GROUP_NM"),refs.read(id));requireRevision(id,r.version.toString());return detail;}
    public List<Map<String,Object>> guides(String id){var r=root(id);var guides=jdbc.query("SELECT * FROM AI_ADMIN_QUERY_GUIDE WHERE DATA_SOURCE_ID=? ORDER BY GUIDE_ID",(rs,n)->{Map<String,Object> value=new LinkedHashMap<>();value.put("guideId",rs.getString("GUIDE_ID"));value.put("dataSourceId",id);value.put("dataSourceRevision",r.version.toString());value.put("title",rs.getString("TITLE"));value.put("description",rs.getString("DETAIL_TEXT"));for(String f:List.of("REG_ID","REG_DTM","MOD_ID","MOD_DTM"))value.put(switch(f){case "REG_ID"->"regId";case "REG_DTM"->"regDtm";case "MOD_ID"->"modId";default->"modDtm";},rs.getObject(f));return value;},Long.valueOf(id));requireRevision(id,r.version.toString());return guides;}
    public void requireRevision(String id,String revision){if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=? AND MANAGEMENT_VERSION=?",Long.class,ManagedCommand.positive(id),ManagedCommand.positive(revision))!=1)throw ManagedErrorCode.REVISION_CONFLICT.failure();}
    public List<Map<String,Object>> connection(String id){var r=root(id);var row=row(r);var v=new LinkedHashMap<String,Object>();v.put("dataSourceId",id);v.put("dataSourceKey",r.key);v.put("dbmsCode",r.dbmsCode);v.put("connectionStatus",row.connectionStatus());v.put("appliedSettingsId",loaded.settingsId());v.put("bootId",loaded.bootId().toString());v.put("parserSupport","NOT_CONNECTED");v.put("observedProductName",null);v.put("observedProductVersion",null);v.put("observedAt",null);return List.of(v);}
    List<ConfigurationCleanup.Candidate> cleanupCandidates(ConfigurationCleanup.Query r){if(!r.scope().equals("DATASOURCE")||r.parentId()!=null)throw ManagedErrorCode.VALIDATION.failure();
        var built=builder.build(new SearchRequest<>(r.conditions(),List.of(),null,null),spec(true),PageMode.all);var rows=built.apply(query.select(R).where(matchesDiagnosis("Y"))).fetch();if(rows.size()>PageSpec.ALL_LIMIT)throw ManagedErrorCode.VALIDATION.failure();
        return rows.stream().map(row->new ConfigurationCleanup.Candidate(new ConfigurationCleanup.Confirmation(row.id.toString(),"DATASOURCE",null,row.version.toString()),row.alias,null,diagnosis(row.key).id())).sorted(Comparator.comparing(c->ManagedCommand.positive(c.target().id()))).toList();
    }
}
