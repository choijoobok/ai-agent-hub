// @feat FEAT-303
package com.aiagent.core.access;

import static com.aiagent.core.access.GroupAccess.*;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.*;
import java.util.function.Function;

/** 검증된 PORTAL 요청의 내부 수락만 보호 콜백에서 수행한다. 외부 I/O는 반환 뒤 소유 실행기가 한다. */
public interface CurrentDataAccess {
    enum OutcomeStatus { COMPLETED, DENIED, UNAVAILABLE }
    enum ProjectionStatus { CONTROLLED, MISMATCH }
    record Outcome<T>(OutcomeStatus status,String reason,T value) {}
    @JsonIgnoreType
    record Payload(Binding binding,List<Map<String,Object>> rows) {
        public Payload {Objects.requireNonNull(binding);rows=copy(rows);}
        @Override public String toString(){return "Payload[보호 자료]";}
    }
    record Projection(ProjectionStatus status,List<Map<String,Object>> rows) {
        public Projection {rows=copy(rows);}
        @Override public String toString(){return "Projection["+status+", rows="+rows.size()+"]";}
    }
    @JsonIgnoreType
    interface Use {Decision decision();Projection project(Payload payload);}
    <T> Outcome<T> withCurrentAccess(Request request,Function<Use,T> acceptance);
    private static List<Map<String,Object>> copy(List<Map<String,Object>> rows) {
        var result=new ArrayList<Map<String,Object>>(rows.size());
        for(var row:rows)result.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        return List.copyOf(result);
    }
}

/** 임의 객체/배열은 통과하지 않는다. 등록된 잎 필드의 스칼라 행으로 소유 어댑터가 정규화한다. */
final class ProjectionScope implements CurrentDataAccess.Use {
    private final GroupAccess.Decision decision;
    private final MaskingRegistry masks;
    private final Thread owner=Thread.currentThread();
    boolean active=true;
    ProjectionScope(GroupAccess.Decision decision,MaskingRegistry masks){this.decision=decision;this.masks=masks;}
    public GroupAccess.Decision decision(){return decision;}
    public CurrentDataAccess.Projection project(CurrentDataAccess.Payload payload) {
        if(!active||owner!=Thread.currentThread()||payload==null||!decision.binding().equals(payload.binding()))return mismatch();
        var output=new ArrayList<Map<String,Object>>();
        for(var row:payload.rows()) {
            if(!decision.fields().keySet().containsAll(row.keySet()))return mismatch();
            var safe=new LinkedHashMap<String,Object>();
            for(var cell:row.entrySet()) {
                var grant=decision.fields().get(cell.getKey());var type=decision.types().get(cell.getKey());
                if(grant.mode()==GroupAccess.Mode.DENIED)continue;
                if(!MaskingRegistry.matches(type,cell.getValue()))return mismatch();
                if(grant.mode()==GroupAccess.Mode.RAW)safe.put(cell.getKey(),cell.getValue());
                else {
                    try{safe.put(cell.getKey(),masks.apply(grant.mask(),type,cell.getValue()));}
                    catch(RuntimeException failure){return mismatch();}
                }
            }
            output.add(safe);
        }
        return new CurrentDataAccess.Projection(CurrentDataAccess.ProjectionStatus.CONTROLLED,output);
    }
    private CurrentDataAccess.Projection mismatch(){return new CurrentDataAccess.Projection(CurrentDataAccess.ProjectionStatus.MISMATCH,List.of());}
}
