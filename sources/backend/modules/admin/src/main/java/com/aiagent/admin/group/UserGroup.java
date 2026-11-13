// @feat FEAT-006
package com.aiagent.admin.group;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="AI_ADMIN_USER_GROUP")
public class UserGroup {
    @Id @Column(name="GROUP_ID") Long groupId;
    @Column(name="GROUP_NM",nullable=false,length=100) String groupNm;
    @Column(name="DESCRIPTION",nullable=false,columnDefinition="text") String description;
    @Column(name="REVISION",nullable=false) Long revision;
    @Column(name="ACCEPTED_DTM",nullable=false) Instant acceptedDtm;
    @Column(name="CHANGE_REASON",nullable=false,columnDefinition="text") String changeReason;
    @Column(name="REG_ID",length=50) String regId;
    @Column(name="REG_DTM",nullable=false) Instant regDtm;
    @Column(name="MOD_ID",length=50) String modId;
    @Column(name="MOD_DTM",nullable=false) Instant modDtm;
    protected UserGroup() {}
}
