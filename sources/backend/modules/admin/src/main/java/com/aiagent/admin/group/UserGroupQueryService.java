// @feat FEAT-006
package com.aiagent.admin.group;

import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.domain.search.QueryDslBuilder;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class UserGroupQueryService {
    private static final PathBuilder<UserGroup> G=new PathBuilder<>(UserGroup.class,"userGroup");
    private static final QuerySpec SPEC=QuerySpec.of("userGroup").from(G)
            .col("groupNm",G.getString("groupNm")).sortable()
            .col("groupId",G.getNumber("groupId",Long.class)).sortable()
            .col("description",G.getString("description"))
            .col("regDtm",G.getDateTime("regDtm",Instant.class)).sortable()
            .col("modDtm",G.getDateTime("modDtm",Instant.class)).sortable()
            .defaultSort("groupNm",SortDir.asc).pk(G.getNumber("groupId",Long.class)).build();
    public record Row(String groupId,String groupNm,String description,String revision,Instant acceptedDtm,String changeReason,
                      String regId,Instant regDtm,String modId,Instant modDtm,String useState) {}
    private final JPAQueryFactory query;
    private final QueryDslBuilder builder;
    private final UserGroupLinks links;
    public UserGroupQueryService(JPAQueryFactory query,QueryDslBuilder builder,UserGroupLinks links){this.query=query;this.builder=builder;this.links=links;}
    private Row row(UserGroup group,String state) {
        return new Row(group.groupId.toString(),group.groupNm,group.description,group.revision.toString(),group.acceptedDtm,group.changeReason,
                group.regId,group.regDtm,group.modId,group.modDtm,state);
    }
    public PageResponse<Row> getList(SearchRequest<Void> request) {
        var built=builder.build(request,SPEC,PageMode.offset);
        // 마스터 목록에서는 참조를 조회하지 않는다. 선택 후 하단 조회에서 확인된 행만 삭제를 연다.
        var rows=built.apply(query.select(G)).fetch().stream().map(group->row(group,"UNKNOWN")).toList();
        Long count=built.count(query).fetchOne();return built.respond(rows,count==null?0:count);
    }
    public Row getOne(String id) {
        var found=query.selectFrom(G).where(G.getNumber("groupId",Long.class).eq(ManagedCommand.positive(id))).fetchOne();
        if(found==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return row(found,links.useState(found.groupId));
    }
    public UserGroupLinkSource.Projection projection(String id,UserGroupLinkSource.Source source) {
        var found=query.selectFrom(G).where(G.getNumber("groupId",Long.class).eq(ManagedCommand.positive(id))).fetchOne();
        if(found==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        return links.read(source,Long.parseLong(id));
    }
}
