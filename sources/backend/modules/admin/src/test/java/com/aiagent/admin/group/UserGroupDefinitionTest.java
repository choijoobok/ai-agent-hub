// @feat FEAT-006
package com.aiagent.admin.group;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.managed.ManagedCommand;
import java.util.*;
import org.junit.jupiter.api.Test;

class UserGroupDefinitionTest {
    private ManagedCommand command(Map<String,Object> values) {
        return new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",values,"업무 담당 그룹 등록");
    }
    @Test void trimsOwnedFieldsAndRejectsServerOrMembershipFields() {
        assertThat(UserGroupDefinition.normalize(command(Map.of("groupNm"," 물류 ","description"," 조회 담당 "))))
                .containsEntry("groupNm","물류").containsEntry("description","조회 담당");
        for(String field:List.of("activeYn","regId","revision","members","groupId")) {
            var values=new HashMap<String,Object>(Map.of("groupNm","물류","description","설명"));values.put(field,"위조");
            assertThatThrownBy(()->UserGroupDefinition.normalize(command(values)));
        }
    }
    @Test void requiredNameAndDescriptionAreValidatedBeforePersistence() {
        for(var values:List.of(Map.<String,Object>of("groupNm"," ","description","설명"),Map.<String,Object>of("groupNm","가".repeat(101),"description","설명"),Map.<String,Object>of("groupNm","물류","description","")))
            assertThatThrownBy(()->UserGroupDefinition.normalize(command(values)));
    }
}
