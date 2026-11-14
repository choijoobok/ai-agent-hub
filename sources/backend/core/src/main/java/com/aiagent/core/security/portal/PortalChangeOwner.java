// @feat FEAT-305
package com.aiagent.core.security.portal;

import java.time.Instant;
import java.util.UUID;

/** bootstrap이 admin의 원장/명령/이력 공개 소유자에 연결한다. 외부 요청 DTO로 사용하지 않는다. */
public interface PortalChangeOwner {
    enum Kind { RETIRED, ORG_MOVED }
    record Binding(String sourceKey,String subject,String portalUserId,String mode,String portalRevision,UUID bindingKey) {
        @Override public String toString(){return "PortalChangeBinding[보호됨]";}
    }
    record Origin(String sourceKey,String receiptId,String commandKey,String eventKeyDigest,Kind kind) {}
    record Applied(String outcome,String portalUserId,String revision,Long historyNo,Instant at) {}
    record JournalEntry(String commandKey,String digest,String portalUserId,String revision,Long historyNo,String outcome,Instant appliedAt,Instant receivedAt) {}
    Binding readBinding(String sourceKey,String subject);
    Binding protectBinding(Binding expected);
    Applied apply(Origin origin,Binding binding,Instant at);
    void protectCommand(String commandKey);
    boolean expiredEvent(String eventDigest);
    void protectHistory(String receiptId,Applied applied);
    void expire(String eventDigest,String commandKey,String receiptId);
}
