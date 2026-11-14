// @feat FEAT-011
package com.aiagent.admin.catalog;
import jakarta.persistence.*;
/** 관계 검색은 표시와 동일한 정확 적용 판의 객체명을 사용한다. */
@Entity @Table(name="AI_ADMIN_CATALOG_HIST")
class CatalogRelationHistoryRecord {
 @Id @Column(name="HIST_ID") Long id;
 @Column(name="CATALOG_ID") Long catalogId;
 @Column(name="RESULT_REVISION") Long revision;
 @Column(name="OBJECT_NAME") String objectName;
 protected CatalogRelationHistoryRecord(){}
}
