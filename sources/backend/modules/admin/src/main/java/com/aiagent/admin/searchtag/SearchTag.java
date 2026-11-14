// @feat FEAT-024
package com.aiagent.admin.searchtag;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_SEARCH_TAG")
public class SearchTag {
    @Id @Column(name="TAG_ID") Long tagId;
    @Column(name="SEARCH_TEXT",nullable=false,length=200) String searchText;
    @Column(name="NORMALIZED_TEXT",nullable=false,length=400) String normalizedText;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name="USE_YN",nullable=false,columnDefinition="character(1)") String useYn;
    @Column(name="REVISION",nullable=false) Long revision;
    @Column(name="ACCEPTED_DTM",nullable=false) Instant acceptedDtm;
    @Column(name="CHANGE_REASON",nullable=false,columnDefinition="text") String changeReason;
    @Column(name="REG_ID",length=50) String regId;
    @Column(name="REG_DTM",nullable=false) Instant regDtm;
    @Column(name="MOD_ID",length=50) String modId;
    @Column(name="MOD_DTM",nullable=false) Instant modDtm;
    protected SearchTag() {}
}
