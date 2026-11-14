// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class QuotaGradeQueryService {
    private static final PathBuilder<QuotaGradeSet> G=new PathBuilder<>(QuotaGradeSet.class,"quotaGradeSet");
    private static final PathBuilder<QuotaGradeItem> I=new PathBuilder<>(QuotaGradeItem.class,"quotaGradeItem");
    private static final QuerySpec SPEC=QuerySpec.of("quotaGradeSet").from(G)
            .col("startMonth",G.getString("startMonth")).sortable().col("memo",G.getString("memo"))
            .derived("gradeNm",(op,value)-> {
                if(op!=Op.eq || !(value instanceof String name) || name.isBlank())throw new IllegalArgumentException("등급명은 비어 있지 않은 eq 조건만 받는다");
                return JPAExpressions.selectOne().from(I).where(I.getNumber("gradeSetId",Long.class).eq(G.getNumber("gradeSetId",Long.class)).and(I.getString("gradeNm").eq(name))).exists();
            })
            .col("regDtm",G.getDateTime("regDtm",Instant.class)).sortable().col("modDtm",G.getDateTime("modDtm",Instant.class)).sortable()
            .defaultSort("startMonth",SortDir.desc).pk(G.getNumber("gradeSetId",Long.class)).build();
    public record Row(String gradeSetId,String startMonth,String memo,String revision,boolean editable,
                      String regId,Instant regDtm,String modId,Instant modDtm) {}
    public record Grade(String gradeId,String gradeNm,int rankNo,String weight,String description,Long referenceUsers) {}
    public record Detail(Row basis,List<Grade> grades,String referenceState) {}
    private final JPAQueryFactory query;
    private final QueryDslBuilder builder;
    private final JdbcTemplate jdbc;
    private final QuotaGradeGuard guard;
    public QuotaGradeQueryService(JPAQueryFactory query,QueryDslBuilder builder,DataSource source,QuotaGradeGuard guard) {
        this.query=query;this.builder=builder;jdbc=new JdbcTemplate(source);this.guard=guard;
    }
    private Row row(QuotaGradeSet value,Instant now){return new Row(value.gradeSetId.toString(),value.startMonth,value.memo==null?"":value.memo,value.revision.toString(),guard.future(value.startMonth,now),value.regId,value.regDtm,value.modId,value.modDtm);}
    public PageResponse<Row> getList(SearchRequest<Void> request) {
        var built=builder.build(request,SPEC,PageMode.offset);Instant now=guard.now();
        var rows=built.apply(query.select(G)).fetch().stream().map(value->row(value,now)).toList();
        Long count=built.count(query).fetchOne();return built.respond(rows,count==null?0:count);
    }
    private Detail detail(QuotaGradeSet value) {
        if(value==null)return null;
        var ids=jdbc.queryForList("SELECT GRADE_ID FROM AI_ADMIN_QUOTA_GRADE_ITEM WHERE GRADE_SET_ID=?",String.class,value.gradeSetId);
        var refs=guard.references(ids);
        var rows=jdbc.query("SELECT GRADE_ID,GRADE_NM,RANK_NO,WEIGHT,COALESCE(DESCRIPTION,'') FROM AI_ADMIN_QUOTA_GRADE_ITEM WHERE GRADE_SET_ID=? ORDER BY RANK_NO",
                (r,n)->new Grade(r.getString(1),r.getString(2),r.getInt(3),r.getBigDecimal(4).toPlainString(),r.getString(5),refs.users().get(r.getString(1))),value.gradeSetId);
        return new Detail(row(value,guard.now()),rows,refs.state());
    }
    public Detail getOne(String id,String beforeMonth) {
        if((id==null)==(beforeMonth==null))throw ManagedErrorCode.VALIDATION.failure();
        if(beforeMonth!=null) {
            QuotaGradeDefinition.month(beforeMonth);
            return detail(query.selectFrom(G).where(G.getString("startMonth").lt(beforeMonth)).orderBy(G.getString("startMonth").desc()).fetchFirst());
        }
        var value=query.selectFrom(G).where(G.getNumber("gradeSetId",Long.class).eq(ManagedCommand.positive(id))).fetchOne();
        if(value==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return detail(value);
    }
    /** 후속 배분자는 같은 달력/월 잠금 안에서 호출한다. 적용 기준 없음은 null이며 기본 가중치로 대체하지 않는다. */
    public Detail effective(String month) {
        QuotaGradeDefinition.month(month);
        return detail(query.selectFrom(G).where(G.getString("startMonth").loe(month)).orderBy(G.getString("startMonth").desc()).fetchFirst());
    }
}
