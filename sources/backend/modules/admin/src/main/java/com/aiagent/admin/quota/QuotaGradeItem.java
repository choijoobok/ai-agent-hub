// @feat FEAT-042
package com.aiagent.admin.quota;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** 등급명 조건의 EXISTS 조회용 투영. 쓰기와 전체 이력은 QuotaGradeService가 소유한다. */
@Entity @Table(name="AI_ADMIN_QUOTA_GRADE_ITEM") @IdClass(QuotaGradeItem.Key.class)
public class QuotaGradeItem {
    @Id @Column(name="GRADE_SET_ID") Long gradeSetId;
    @Id @Column(name="GRADE_ID") Long gradeId;
    @Column(name="GRADE_NM",nullable=false,length=100) String gradeNm;
    protected QuotaGradeItem() {}
    public static class Key implements Serializable {
        public Long gradeSetId;
        public Long gradeId;
        public Key() {}
        @Override public boolean equals(Object value) {
            return value instanceof Key other && Objects.equals(gradeSetId,other.gradeSetId) && Objects.equals(gradeId,other.gradeId);
        }
        @Override public int hashCode(){return Objects.hash(gradeSetId,gradeId);}
    }
}
