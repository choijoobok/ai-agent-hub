// @feat FEAT-006
package com.aiagent.admin.group;

import static com.aiagent.admin.group.UserGroupLinkSource.*;
import com.aiagent.admin.managed.ManagedErrorCode;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/** 상세 목록과 참조 존재 검사는 분리한다. 소유자 누락은 DB상 전체 미설치 증거가 있어야만 참조 없음이다. */
public final class UserGroupLinks {
    private final Map<Source,UserGroupLinkSource> sources=new EnumMap<>(Source.class);
    private final Supplier<Set<Source>> uninstalled;
    public UserGroupLinks(List<UserGroupLinkSource> installed) {
        this(installed,Set::of);
    }
    public UserGroupLinks(List<UserGroupLinkSource> installed,Supplier<Set<Source>> uninstalled) {
        this.uninstalled=uninstalled;
        for(var source:installed)if(sources.putIfAbsent(source.source(),source)!=null)throw new IllegalStateException("그룹 연결 소유자가 중복됐다");
    }
    public Projection read(Source source,long id) {
        var provider=sources.get(source);
        if(provider==null)return new Projection(State.NOT_CONNECTED,List.of());
        try {return Objects.requireNonNull(provider.read(id));}
        catch(DataAccessException failure){return new Projection(State.UNAVAILABLE,List.of());}
    }
    public String useState(long id) {
        return switch(reference(id)){case PRESENT->"USED";case ABSENT->"UNUSED";case UNKNOWN->"UNKNOWN";};
    }
    public void requireUnreferenced(long id) {
        var reference=reference(id);
        if(reference==Reference.PRESENT)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        if(reference!=Reference.ABSENT)throw ManagedErrorCode.UNAVAILABLE.failure();
    }
    private Reference reference(long id) {
        Set<Source> absent=Set.of();
        if(sources.size()!=Source.values().length) {
            try {absent=uninstalled.get();}
            catch(DataAccessException failure){absent=Set.of();} // 미설치 증거 실패는 확인 불가로 유지한다.
        }
        boolean unknown=false;
        for(var source:Source.values()) {
            var provider=sources.get(source);
            if(provider==null&&absent.contains(source))continue;
            Reference found=Reference.UNKNOWN;
            if(provider!=null) {
                try {found=provider.references(id);}
                catch(DataAccessException failure){found=Reference.UNKNOWN;} // 설치된 검사 실패를 미설치로 우회하지 않는다.
            }
            if(found==Reference.PRESENT)return Reference.PRESENT;
            if(found!=Reference.ABSENT)unknown=true;
        }
        return unknown?Reference.UNKNOWN:Reference.ABSENT;
    }
}
