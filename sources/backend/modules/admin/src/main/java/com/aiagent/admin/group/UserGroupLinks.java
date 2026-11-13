// @feat FEAT-006
package com.aiagent.admin.group;

import static com.aiagent.admin.group.UserGroupLinkSource.*;
import com.aiagent.admin.managed.ManagedErrorCode;
import java.util.*;
import org.springframework.dao.DataAccessException;

/** 소유자 누락은 0건이 아니다. 삭제는 여섯 소유자의 완전한 확인이 필요하다. */
public final class UserGroupLinks {
    private final Map<Source,UserGroupLinkSource> sources=new EnumMap<>(Source.class);
    public UserGroupLinks(List<UserGroupLinkSource> installed) {
        for(var source:installed)if(sources.putIfAbsent(source.source(),source)!=null)throw new IllegalStateException("그룹 연결 소유자가 중복됐다");
    }
    public Projection read(Source source,long id) {
        var provider=sources.get(source);
        if(provider==null)return new Projection(State.NOT_CONNECTED,List.of());
        try {return Objects.requireNonNull(provider.read(id));}
        catch(DataAccessException failure){return new Projection(State.UNAVAILABLE,List.of());}
    }
    public String useState(long id) {
        boolean unknown=false;
        for(var source:Source.values()) {
            var result=read(source,id);
            if(result.state()!=State.READY)unknown=true;
            else if(!result.rows().isEmpty())return "USED";
        }
        return unknown?"UNKNOWN":"UNUSED";
    }
    public void requireUnreferenced(long id) {
        for(var source:Source.values()) {
            var provider=sources.get(source);
            if(provider==null)throw ManagedErrorCode.UNAVAILABLE.failure();
            var reference=provider.references(id);
            if(reference==Reference.PRESENT)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            if(reference!=Reference.ABSENT)throw ManagedErrorCode.UNAVAILABLE.failure();
        }
    }
}
