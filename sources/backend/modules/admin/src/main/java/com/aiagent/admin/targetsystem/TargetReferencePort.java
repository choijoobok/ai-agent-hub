// @feat FEAT-001
package com.aiagent.admin.targetsystem;

import java.util.*;

/** 후속 API·워크플로우 소유자가 현재/대기 참조를 같은 대상 잠금 아래 제공한다. */
public interface TargetReferencePort {
    enum Reference { ABSENT,PRESENT,UNKNOWN }
    Set<String> tables();
    Reference reference(String systemId,String connectionId);
    record Api(String id,String name,String apiKind,String connectionId,String useYn) {}
    default List<Api> readableApis(String systemId){return List.of();}
}
