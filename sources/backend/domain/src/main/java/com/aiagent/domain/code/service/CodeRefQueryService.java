// @feat FEAT-504
package com.aiagent.domain.code.service;

import com.aiagent.domain.code.entity.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CodeRefQueryService {
    private final JPAQueryFactory queries;
    public CodeRefQueryService(JPAQueryFactory queries){this.queries=queries;}
    /** 세 값은 바인딩한다. 각 참조 PK가 한 행을 보장하므로 정렬 후 상세 코드도 중복되지 않는다. */
    public List<String> codesByRef(String group,String reference,String value) {
        var code=QSysCode.sysCode;var ref=QSysCodeRef.sysCodeRef;var definition=QSysCodeGrpRef.sysCodeGrpRef;
        return queries.select(code.dtlCd).from(code)
                .join(ref).on(ref.grpCd.eq(code.grpCd),ref.dtlCd.eq(code.dtlCd))
                .join(definition).on(definition.grpCd.eq(ref.grpCd),definition.refCd.eq(ref.refCd))
                .where(code.grpCd.eq(group),ref.refCd.eq(reference),ref.refVal.eq(value),code.useYn.eq(true))
                .orderBy(code.sortNo.asc(),code.dtlCd.asc()).fetch();
    }
}
