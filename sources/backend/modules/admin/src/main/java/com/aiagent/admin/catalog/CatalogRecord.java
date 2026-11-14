// @feat FEAT-003
package com.aiagent.admin.catalog;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_CATALOG") @SecondaryTable(name="AI_ADMIN_CATALOG_CONTENT",pkJoinColumns=@PrimaryKeyJoinColumn(name="CATALOG_ID"))
class CatalogRecord {
 @Id @Column(name="CATALOG_ID") Long id;
 @Column(name="DATA_SOURCE_ID") Long dataSourceId;
 @Column(name="SERVER_NAME") String serverName;
 @Column(name="DATABASE_NAME") String databaseName;
 @Column(name="SCHEMA_NAME") String schemaName;
 @Column(name="OBJECT_NAME") String objectName;
 @Column(name="LIFECYCLE_CD") String lifecycle;
 @Column(name="REVISION") Long revision;
 @Column(name="USE_YN",columnDefinition="character(1)") Character useYn;
 @Column(name="REG_ID") String regId;
 @Column(name="REG_DTM") Instant regDtm;
 @Column(name="APPLIED_DTM") Instant appliedAt;
 @Column(name="BUSINESS_NAME",table="AI_ADMIN_CATALOG_CONTENT") String businessName;
 @Column(name="OBJECT_TYPE",table="AI_ADMIN_CATALOG_CONTENT") String objectType;
 protected CatalogRecord(){}
}
