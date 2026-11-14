// @feat FEAT-002
package com.aiagent.admin.datasource;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_DATA_SOURCE")
public class DataSourceRecord {
    @Id @Column(name="DATA_SOURCE_ID") Long id;
    @Column(name="DATA_SOURCE_KEY") String key;
    @Column(name="ALIAS_NM") String alias;
    @Column(name="DBMS_CD") String dbmsCode;
    @Column(name="DBMS_VERSION") String dbmsVersion;
    @Column(name="SOURCE_TYPE_CD") String sourceType;
    @Column(name="DW_SYNC_MEMO") String syncMethodNote;
    @Column(name="TIME_ZONE_ID") String timeZoneId;
    @Column(name="DESCRIPTION") String description;
    @Column(name="DATA_BASIS") String dataBasis;
    @Column(name="MANAGEMENT_VERSION") Long version;
    @Column(name="REG_ID") String regId;@Column(name="REG_DTM") Instant regDtm;
    @Column(name="MOD_ID") String modId;@Column(name="MOD_DTM") Instant modDtm;
    protected DataSourceRecord(){}
}
