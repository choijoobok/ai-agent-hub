// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.service.*;

public final class ManagedAuthorization {
    private final EffectiveAuthorityService authorities;
    private final ApiAuthorityRegistry apis;
    public ManagedAuthorization(EffectiveAuthorityService authorities,ApiAuthorityRegistry apis) { this.authorities=authorities; this.apis=apis; }
    public void require(String actor,ManagedAssetAdapter owner,boolean read) {
        if(AdminAuthorityProtection.currentToken()==null)throw ManagedErrorCode.FORBIDDEN.failure();
        var authority=authorities.calculate(actor);
        var write=apis.find(owner.writePath()).orElseThrow(ManagedErrorCode.FORBIDDEN::failure);
        if(write.action()!=ActionType.SAVE||!write.isAllowedBy(authority))throw ManagedErrorCode.FORBIDDEN.failure();
        if(read) {
            var query=apis.find(owner.readPath()).orElseThrow(ManagedErrorCode.FORBIDDEN::failure);
            if(query.action()!=ActionType.READ||!query.isAllowedBy(authority))throw ManagedErrorCode.FORBIDDEN.failure();
        }
    }
}
