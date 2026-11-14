// @feat FEAT-305
package com.aiagent.portal.change;

import java.time.Instant;

/** 고객 보존/조사·참조 종료를 검수한 소유 포트. 미설치면 파기를 하지 않는다. 임의 기본 기간은 없다. */
public interface ChangeRetentionPolicy {
    record Eligibility(Instant deleteAfter,boolean investigationProtected,boolean expireUnnotified,String policyVersion) {}
    Eligibility inspect(ChangeStore.Stored receipt,Instant now);
}
