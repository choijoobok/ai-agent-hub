// @feat FEAT-010
package com.aiagent.admin.chathelp;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="AI_ADMIN_CHAT_HELP")
public class ChatHelp {
    @Id @Column(name="HELP_CD",length=30) String helpCd;
    @Column(name="FORMAT_CD",nullable=false,length=10) String formatCd;
    @Column(name="BODY",nullable=false,columnDefinition="text") String body;
    @Column(name="REVISION",nullable=false) Long revision;
    @Column(name="ACCEPTED_DTM",nullable=false) Instant acceptedDtm;
    @Column(name="CHANGE_REASON",nullable=false,columnDefinition="text") String changeReason;
    @Column(name="REG_ID",length=50) String regId;
    @Column(name="REG_DTM",nullable=false) Instant regDtm;
    @Column(name="MOD_ID",length=50) String modId;
    @Column(name="MOD_DTM",nullable=false) Instant modDtm;
    protected ChatHelp(){}
}
