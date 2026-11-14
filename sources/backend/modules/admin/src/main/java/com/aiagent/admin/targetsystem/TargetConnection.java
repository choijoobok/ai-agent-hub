// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_TARGET_SYSTEM_CONN")
public class TargetConnection {
    @Id @Column(name="CONNECTION_RECORD_ID") Long recordId;
    @Column(name="TARGET_SYSTEM_ID") Long systemId;
    @Column(name="CONNECTION_ID") String connectionId;
    @Column(name="CONNECTION_NM") String nameOverride;
    @Column(name="DESCRIPTION") String descriptionOverride;
    @Column(name="YAML_CONNECTION_NM") String yamlName;
    @Column(name="YAML_DESCRIPTION") String yamlDescription;
    @Column(name="REG_ID") String regId;
    @Column(name="REG_DTM") Instant regDtm;
    @Column(name="MOD_ID") String modId;
    @Column(name="MOD_DTM") Instant modDtm;
    protected TargetConnection(){}
}
