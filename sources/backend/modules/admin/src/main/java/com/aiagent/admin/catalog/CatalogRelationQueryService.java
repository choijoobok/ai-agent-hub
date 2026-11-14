// @feat FEAT-011
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.domain.authz.ActionType;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class CatalogRelationQueryService {
 public record Filter(String dataSourceId,String keyword,String catalogId){}
 private static final PathBuilder<CatalogRelationRecord> R=new PathBuilder<>(CatalogRelationRecord.class,"relation");
 private static final PathBuilder<CatalogRecord> O=new PathBuilder<>(CatalogRecord.class,"relationOwner"),L=new PathBuilder<>(CatalogRecord.class,"relationLeft"),H=new PathBuilder<>(CatalogRecord.class,"relationRight");
 private final JPAQueryFactory query;private final QueryDslBuilder builder;private final CatalogService owner;private final CatalogAccess access;
 public CatalogRelationQueryService(JPAQueryFactory query,QueryDslBuilder builder,CatalogService owner,CatalogAccess access){this.query=query;this.builder=builder;this.owner=owner;this.access=access;}
 private QuerySpec spec(){return QuerySpec.of("catalogRelation").from(R).join(O).on(O.getNumber("id",Long.class).eq(R.getNumber("ownerCatalogId",Long.class))).join(L).on(L.getNumber("id",Long.class).eq(R.getNumber("leftCatalogId",Long.class))).join(H).on(H.getNumber("id",Long.class).eq(R.getNumber("rightCatalogId",Long.class)))
   .col("dataSourceId",O.getNumber("dataSourceId",Long.class)).col("relationId",R.getNumber("id",Long.class)).sortable().col("relationName",R.getString("relationName")).sortable().col("cardinality",R.getString("cardinality")).sortable().col("reviewStatus",R.getString("reviewStatus")).sortable()
   .col("keyword",R.getString("relationName").coalesce("").concat(" ").concat(L.getString("objectName")).concat(" ").concat(H.getString("objectName")))
   .derived("catalogId",(op,value)->{if(op!=Op.eq)throw ManagedErrorCode.VALIDATION.failure();long id=ManagedCommand.positive(value.toString());return R.getNumber("leftCatalogId",Long.class).eq(id).or(R.getNumber("rightCatalogId",Long.class).eq(id));})
   .defaultSort("relationName",SortDir.asc).pk(R.getNumber("id",Long.class)).build();}
 public PageResponse<Map<String,Object>> getList(SearchRequest<Filter> request){access.require(ActionType.READ);var conditions=new ArrayList<>(request.conditionsOrEmpty());if(request.params()!=null){var p=request.params();if(p.dataSourceId()!=null)conditions.add(new Condition("dataSourceId",Op.eq,p.dataSourceId()));if(p.keyword()!=null&&!p.keyword().isBlank())conditions.add(new Condition("keyword",Op.contains,p.keyword()));if(p.catalogId()!=null)conditions.add(new Condition("catalogId",Op.eq,p.catalogId()));}
   var built=builder.build(new SearchRequest<>(conditions,request.sorts(),request.page(),null),spec(),PageMode.offset);var records=built.apply(query.select(R)).fetch();var rows=records.stream().map(r->owner.relations().find(r.id.toString())).toList();Long count=built.count(query).fetchOne();return built.respond(rows,count==null?0:count);}
}
