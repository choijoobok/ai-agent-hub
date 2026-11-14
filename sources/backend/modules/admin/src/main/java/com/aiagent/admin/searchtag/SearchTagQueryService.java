// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class SearchTagQueryService {
    private static final PathBuilder<SearchTag> T=new PathBuilder<>(SearchTag.class,"searchTag");
    private static final PathBuilder<TagSynonym> S=new PathBuilder<>(TagSynonym.class,"tagSynonym");
    private static final QuerySpec SPEC=QuerySpec.of("searchTag").from(T)
        .col("tagId",T.getNumber("tagId",Long.class)).sortable()
        .derived("searchText",(op,value)->{
            if(op!=Op.contains||!(value instanceof String))throw SearchTagErrorCode.INVALID.failure();
            String text=SearchTagText.normalize((String)value);
            return T.getString("normalizedText").contains(text).or(JPAExpressions.selectOne().from(S).where(S.getNumber("tagId",Long.class).eq(T.getNumber("tagId",Long.class)).and(S.getString("normalizedText").contains(text))).exists());
        }).col("useYn",T.getString("useYn"))
        .col("regDtm",T.getDateTime("regDtm",Instant.class)).sortable().col("modDtm",T.getDateTime("modDtm",Instant.class)).sortable()
        .defaultSort("tagId",SortDir.asc).pk(T.getNumber("tagId",Long.class)).build();
    public record Row(String tagId,String searchText,String useYn,String revision,String regId,Instant regDtm,String modId,Instant modDtm) {}
    public record Synonym(String synonymId,String searchText,String description) {}
    public record Detail(Row tag,List<Synonym> synonyms) {}
    public record References(String revision,SearchTagReferences.State state,List<SearchTagReferences.Knowledge> rows,SearchTagReferences.Reference reference) {}
    private final JPAQueryFactory query;
    private final QueryDslBuilder builder;
    private final JdbcTemplate jdbc;
    private final SearchTagReferenceQueryService references;
    public SearchTagQueryService(JPAQueryFactory query,QueryDslBuilder builder,DataSource source,SearchTagReferenceQueryService references){this.query=query;this.builder=builder;jdbc=new JdbcTemplate(source);this.references=references;}
    private Row row(SearchTag tag){return new Row(tag.tagId.toString(),tag.searchText,tag.useYn,tag.revision.toString(),tag.regId,tag.regDtm,tag.modId,tag.modDtm);}
    private SearchTag find(String id) {
        var tag=query.selectFrom(T).where(T.getNumber("tagId",Long.class).eq(ManagedCommand.positive(id))).fetchOne();
        if(tag==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return tag;
    }
    public PageResponse<Row> getList(SearchRequest<Void> request) {
        var built=builder.build(request,SPEC,PageMode.offset);var rows=built.apply(query.select(T)).fetch().stream().map(this::row).toList();
        Long total=built.count(query).fetchOne();return built.respond(rows,total==null?0:total);
    }
    public Detail getOne(String id) {
        var tag=find(id);return new Detail(row(tag),jdbc.query("SELECT SYNONYM_ID,SEARCH_TEXT,DESCRIPTION FROM AI_ADMIN_TAG_SYNONYM WHERE TAG_ID=? ORDER BY SYNONYM_ID",(r,n)->new Synonym(r.getString(1),r.getString(2),r.getString(3)),tag.tagId));
    }
    public References getReferences(String id) {
        var tag=find(id);var found=references.read(id);return new References(tag.revision.toString(),found.state(),found.rows(),references.reference(id));
    }
}
