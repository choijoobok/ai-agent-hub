// @feat FEAT-006
package com.aiagent.admin.group;

import com.aiagent.admin.managed.*;
import java.util.*;

final class UserGroupDefinition {
    private UserGroupDefinition() {}
    static Map<String,Object> normalize(ManagedCommand command) {
        var values=command.values();
        if(!values.keySet().equals(Set.of("groupNm","description")))throw ManagedErrorCode.VALIDATION.failure();
        if(!(values.get("groupNm") instanceof String name)||!(values.get("description") instanceof String description)
                ||name.isBlank()||name.strip().length()>100||description.isBlank())throw ManagedErrorCode.VALIDATION.failure();
        return Map.of("groupNm",name.strip(),"description",description.strip());
    }
}
