// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.ManagedAssetAdapter;
import com.aiagent.admin.managed.ManagedLocks;
import java.util.*;

/** 후속005/017/045가 함께 제공할 원장 어댑터. 달력 잠금→관련 월→사용자 순서를 지킨다. */
public interface QuotaGradeUsage {
    record Change(String targetId,String startMonth,String nextMonth,boolean deletion,Set<String> beforeIds,QuotaGradeDefinition after) {}
    record References(String state,Map<String,Long> users) {}
    /** 달력 경계가 이미 잠겼다. 관련 월(1)·사용자(2) 키를 반환하며 부모(3)보다 먼저 획득된다. */
    List<ManagedLocks.Key> locks(Change change);
    /** 미래 예약·후속 기준·확정 배정을 잠금 후 검사하고 필요한 이전 대상 누락을 거부한다. */
    void validate(Change change);
    /** 원 승인 이력 보존, 별도 이전/취소 근거와 예정본 낡음을 현재값/HIST와 같은 트랜잭션에 기록한다. */
    void apply(Change change,ManagedAssetAdapter.Stamp stamp);
    References references(Collection<String> gradeIds);
}
