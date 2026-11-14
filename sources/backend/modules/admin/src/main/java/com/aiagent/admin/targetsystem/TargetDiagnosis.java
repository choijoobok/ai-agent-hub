// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_CONFIG_DIAGNOSIS")
public class TargetDiagnosis {
    @Id @Column(name="DIAGNOSIS_ID") Long id;
    @Column(name="ENVIRONMENT_ID") String environment;
    @Column(name="CONFIG_ID") String configId;
    @Column(name="TARGET_KIND") String kind;
    @Column(name="TARGET_KEY") String key;
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name="MISMATCH_YN", columnDefinition="char(1)") String mismatch;
    @Column(name="SAFE_REASON") String reason;
    @Column(name="CHECKED_DTM") Instant checkedAt;
    protected TargetDiagnosis(){}
}
