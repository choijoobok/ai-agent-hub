// @feat FEAT-001 FEAT-002
package com.aiagent.admin.shared.configuration;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_CONFIG_DIAGNOSIS")
public class ConfigurationDiagnosis {
    @Id @Column(name="DIAGNOSIS_ID") public Long id;
    @Column(name="ENVIRONMENT_ID") public String environment;
    @Column(name="CONFIG_ID") public String configId;
    @Column(name="TARGET_KIND") public String kind;
    @Column(name="TARGET_KEY") public String key;
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name="MISMATCH_YN", columnDefinition="char(1)") public String mismatch;
    @Column(name="SAFE_REASON") public String reason;
    @Column(name="CHECKED_DTM") public Instant checkedAt;
    protected ConfigurationDiagnosis(){}
}
