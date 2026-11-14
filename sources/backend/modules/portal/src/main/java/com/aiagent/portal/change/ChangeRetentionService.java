// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.security.portal.PortalChangeOwner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 사건/명령 최소키→HIST 보호 해제→소유 개인 기록 파기. 자동 배치나 HTTP 파기 API는 없다. */
@Service
public class ChangeRetentionService {
    private final ChangeStore store;private final PortalChangeOwner owner;private final ObjectProvider<ChangeRetentionPolicy> policies;private final TransactionTemplate tx;
    public ChangeRetentionService(ChangeStore store,PortalChangeOwner owner,ObjectProvider<ChangeRetentionPolicy> policies,PlatformTransactionManager manager){this.store=store;this.owner=owner;this.policies=policies;tx=new TransactionTemplate(manager);}
    public boolean purge(String receipt) {
        var policy=policies.getIfUnique();if(policy==null)return false;
        return Boolean.TRUE.equals(tx.execute(status->{
            var old=store.findReceipt(receipt);if(old==null)return false;store.lock(old.eventDigest());owner.protectCommand(old.commandKey());
            var row=store.findReceipt(receipt);if(row==null)return false;var now=store.now();var eligibility=policy.inspect(row,now);
            if(eligibility==null||eligibility.deleteAfter()==null||eligibility.policyVersion()==null||eligibility.policyVersion().isBlank()||eligibility.investigationProtected()||now.isBefore(eligibility.deleteAfter())||row.receipt().notificationState().equals("PENDING_NO_RECIPIENT")&&!eligibility.expireUnnotified())return false;
            store.ending(receipt,()->{owner.expire(row.eventDigest(),row.commandKey(),receipt);store.delete(receipt);});return true;
        }));
    }
}
