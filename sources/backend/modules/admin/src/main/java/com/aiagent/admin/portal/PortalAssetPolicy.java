// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.core.security.PrincipalRef;
import java.util.*;

/** 초기 FEAT-005 연결 계약. 현재 제품 구현자는 없다.
 * 신규 자산은 내용/정책판과 현재 필수 관문을 가진 PortalDataPolicySource/CurrentDataAccess를 사용한다. */
@Deprecated(since="SLICE-303")
public interface PortalAssetPolicy {
    record Request(String ownerFeature,String assetKind,String assetId,String purpose,boolean quotaRequired,String identityKind,String identityTarget) {}
    record Decision(String outcome,Set<String> allowedGroups,Set<String> fields,Map<String,String> masking,String scope) {
        public Decision {allowedGroups=Set.copyOf(allowedGroups);fields=Set.copyOf(fields);masking=Map.copyOf(masking);}
    }
    String ownerFeature();
    Decision evaluate(PrincipalRef principal,Set<String> activeGroups,Request request);
}
