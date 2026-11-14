// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="AI_ADMIN_CONFIG_ALERT")
public class TargetAlert {
    @Id @Column(name="ALERT_ID") Long id;
    @Column(name="ENVIRONMENT_ID") String environment;
    @Column(name="TARGET_KIND") String kind;
    @Column(name="TARGET_KEY") String key;
    @Column(name="CONFIG_ID") String configId;
    @Column(name="STATUS_CD") String status;
    @Column(name="FIRST_SEEN_DTM") Instant first;
    @Column(name="LAST_CHECKED_DTM") Instant checked;
    @Column(name="RECOVERED_DTM") Instant recovered;
    protected TargetAlert(){}
}
