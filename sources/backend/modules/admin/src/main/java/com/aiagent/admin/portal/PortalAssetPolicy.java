// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.core.security.PrincipalRef;
import java.util.*;

/** 자산 소유자가 현재 허용 그룹·필드·마스킹을 판정한다. 미설치 소유자는 허용으로 간주하지 않는다. */
public interface PortalAssetPolicy {
    record Request(String ownerFeature,String assetKind,String assetId,String purpose,boolean quotaRequired,String identityKind,String identityTarget) {}
    record Decision(String outcome,Set<String> allowedGroups,Set<String> fields,Map<String,String> masking,String scope) {
        public Decision {allowedGroups=Set.copyOf(allowedGroups);fields=Set.copyOf(fields);masking=Map.copyOf(masking);}
    }
    String ownerFeature();
    Decision evaluate(PrincipalRef principal,Set<String> activeGroups,Request request);
}
