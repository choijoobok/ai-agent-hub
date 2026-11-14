// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_TARGET_SYSTEM")
public class TargetSystem {
    @Id @Column(name="TARGET_SYSTEM_ID") Long id;
    @Column(name="SYSTEM_KEY") String systemKey;
    @Column(name="SYSTEM_NM") String nameOverride;
    @Column(name="DESCRIPTION") String descriptionOverride;
    @Column(name="YAML_SYSTEM_NM") String yamlName;
    @Column(name="YAML_DESCRIPTION") String yamlDescription;
    @Column(name="MANAGEMENT_VERSION") Long version;
    @Column(name="SYNC_CONFIG_ID") String syncConfigId;
    @Column(name="REG_ID") String regId;
    @Column(name="REG_DTM") Instant regDtm;
    @Column(name="MOD_ID") String modId;
    @Column(name="MOD_DTM") Instant modDtm;
    protected TargetSystem(){}
}
