// @feat FEAT-005
package com.aiagent.admin.portal;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.managed.ManagedCommand;
import com.aiagent.core.error.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;

class PortalDefinitionTest {
    private ManagedCommand command(String scope,Map<String,Object> values) {
        return new ManagedCommand(UUID.randomUUID().toString(),null,null,scope,values,"이용자 등록");
    }
    private Map<String,Object> profile() {
        var value=new LinkedHashMap<String,Object>();
        value.put("loginId","worker");value.put("userNm","업무 이용자");value.put("organizationNm","물류");
        value.put("useStartDt","2026-11-14");value.put("useEndDt",null);value.put("activeYn","N");return value;
    }
    @Test void unlimitedPeriodAndStrictOwnedFields() {
        assertThat(PortalDefinition.normalize(command("profile",profile()))).containsEntry("useEndDt",null);
        var value=profile();value.put("useEndDt","2026-11-13");
        assertThatThrownBy(()->PortalDefinition.normalize(command("profile",value))).isInstanceOf(BusinessException.class);
        var secret=profile();secret.put("password","forbidden");
        assertThatThrownBy(()->PortalDefinition.normalize(command("profile",secret))).isInstanceOf(BusinessException.class);
    }
    @Test void membershipDuplicatesAndMissingReasonCannotBecomeGrants() {
        var row=Map.of("groupId","1","activeYn","Y","memo","업무 부여");
        assertThatThrownBy(()->PortalDefinition.normalize(command("memberships",Map.of("memberships",List.of(row,row),"deactivateUser",false)))).isInstanceOf(BusinessException.class);
        var empty=Map.of("groupId","1","activeYn","Y","memo"," ");
        assertThatThrownBy(()->PortalDefinition.normalize(command("memberships",Map.of("memberships",List.of(empty),"deactivateUser",false)))).isInstanceOf(BusinessException.class);
    }
    @Test void activatedIdentityNeedsAccountAndEvidence() {
        var row=new HashMap<String,Object>(Map.of("targetId","1","mappingActiveYn","Y","externalUserId","person","identityEvidence",""));
        assertThatThrownBy(()->PortalDefinition.normalize(command("identitySystem",Map.of("identities",List.of(row))))).isInstanceOf(BusinessException.class);
        row.put("identityEvidence","사내 원장 확인");
        assertThat(PortalDefinition.normalize(command("identitySystem",Map.of("identities",List.of(row))))).containsKey("identities");
    }
}
