// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.ManagedAssetAdapter;
import java.util.*;

/** 017/045/018이 함께 제공할 배정 원장 경계. 단순 조회와 같은 트랜잭션의 자격 변경을 분리한다. */
public interface PortalQuotaPort {
    record Allocation(String state,String tokenLimit,String usedTokens,String remainingTokens) {}
    Allocation allocation(String portalUserId,String month);
    void requireGradeMutable(String portalUserId,String month);
    void eligibilityChanged(String portalUserId,ManagedAssetAdapter.Stamp stamp);
    List<Map<String,Object>> extraTokens(String portalUserId);
}
