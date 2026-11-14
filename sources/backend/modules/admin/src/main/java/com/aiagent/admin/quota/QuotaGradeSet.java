// @feat FEAT-042
package com.aiagent.admin.quota;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="AI_ADMIN_QUOTA_GRADE_SET")
public class QuotaGradeSet {
    @Id @Column(name="GRADE_SET_ID") Long gradeSetId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name="START_MONTH",nullable=false,length=7,columnDefinition="character(7)") String startMonth;
    @Column(name="MEMO",columnDefinition="text") String memo;
    @Column(name="REVISION",nullable=false) Long revision;
    @Column(name="ACCEPTED_DTM",nullable=false) Instant acceptedDtm;
    @Column(name="CHANGE_REASON",nullable=false,columnDefinition="text") String changeReason;
    @Column(name="REG_ID",length=50) String regId;
    @Column(name="REG_DTM",nullable=false) Instant regDtm;
    @Column(name="MOD_ID",length=50) String modId;
    @Column(name="MOD_DTM",nullable=false) Instant modDtm;
    protected QuotaGradeSet() {}
}
