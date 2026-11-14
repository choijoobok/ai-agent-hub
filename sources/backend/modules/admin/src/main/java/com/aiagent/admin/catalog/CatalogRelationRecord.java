// @feat FEAT-011
package com.aiagent.admin.catalog;
import jakarta.persistence.*;
@Entity @Table(name="AI_ADMIN_CATALOG_RELATION")
class CatalogRelationRecord {
 @Id @Column(name="RELATION_ID") Long id;
 @Column(name="CATALOG_ID") Long ownerCatalogId;
 @Column(name="LEFT_CATALOG_ID") Long leftCatalogId;
 @Column(name="RIGHT_CATALOG_ID") Long rightCatalogId;
 @Column(name="LEFT_REVISION_ID") Long leftRevision;
 @Column(name="RIGHT_REVISION_ID") Long rightRevision;
 @Column(name="RELATION_NM") String relationName;
 @Column(name="CARDINALITY_CD") String cardinality;
 @Column(name="REVIEW_STATUS") String reviewStatus;
 protected CatalogRelationRecord(){}
}
