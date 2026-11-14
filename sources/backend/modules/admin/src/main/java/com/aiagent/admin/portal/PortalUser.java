// @feat FEAT-005
package com.aiagent.admin.portal;

import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="AI_ADMIN_PORTAL_USER")
public class PortalUser {
    @Id @Column(name="PORTAL_USER_ID") Long portalUserId;
    @Column(name="LOGIN_ID",nullable=false,length=100) String loginId;
    @Column(name="USER_NM",nullable=false,length=100) String userNm;
    @Column(name="ORGANIZATION_NM",nullable=false,length=200) String organizationNm;
    @Column(name="AUTH_MODE_CD",nullable=false,length=20) String authModeCd;
    @Column(name="SOURCE_CD",nullable=false,length=100) String sourceCd;
    @Column(name="USE_START_DT",nullable=false) LocalDate useStartDt;
    @Column(name="USE_END_DT") LocalDate useEndDt;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="ACTIVE_YN",nullable=false,length=1,columnDefinition="character(1)") String activeYn;
    @Column(name="REVISION",nullable=false) Long revision;
    @Column(name="ACCEPTED_DTM",nullable=false) Instant acceptedDtm;
    @Column(name="CHANGE_REASON",nullable=false,columnDefinition="text") String changeReason;
    @Column(name="REG_ID",length=50) String regId;
    @Column(name="REG_DTM",nullable=false) Instant regDtm;
    @Column(name="MOD_ID",length=50) String modId;
    @Column(name="MOD_DTM",nullable=false) Instant modDtm;
    protected PortalUser() {}
}
