// @feat FEAT-305
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.QuotaGradeGuard;
import com.aiagent.core.security.portal.PortalChangeOwner.*;
import com.aiagent.support.time.BusinessCalendar;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 외부 검증 생산자의 두 비활성화 명령만 수락한다. 관리자 CRUD 권한으로 위장하지 않는다. */
@Service
public class PortalUserChangeService {
    private final JdbcTemplate jdbc;private final ManagedLocks locks;private final ManagedIds ids;
    private final PortalPolicy policy;private final PortalUserService users;private final PortalDependencies dependencies;private final BusinessCalendar calendar;
    public PortalUserChangeService(DataSource source,ManagedLocks locks,ManagedIds ids,PortalPolicy policy,PortalUserService users,PortalDependencies dependencies,BusinessCalendar calendar){jdbc=new JdbcTemplate(source);this.locks=locks;this.ids=ids;this.policy=policy;this.users=users;this.dependencies=dependencies;this.calendar=calendar;}
    public Binding readBinding(String source,String subject) {
        var rows=jdbc.queryForList("SELECT U.PORTAL_USER_ID,U.AUTH_MODE_CD,U.REVISION,E.BINDING_KEY FROM AI_ADMIN_PORTAL_EXTERNAL_ID E JOIN AI_ADMIN_PORTAL_USER U ON U.PORTAL_USER_ID=E.PORTAL_USER_ID WHERE E.SOURCE_KEY=? AND E.SUBJECT_ID=?",source,subject);
        if(rows.size()!=1)return null;var row=rows.getFirst();
        return new Binding(source,subject,row.get("PORTAL_USER_ID").toString(),row.get("AUTH_MODE_CD").toString(),row.get("REVISION").toString(),(UUID)row.get("BINDING_KEY"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Binding protectBinding(Binding expected) {
        locks.ordered(List.of(QuotaGradeGuard.boundary()));
        String month=policy.now().atZone(calendar.zone()).toLocalDate().toString().substring(0,7);
        locks.ordered(List.of(new ManagedLocks.Key(1,"QUOTA_MONTH",month)));policy.protect(true);
        if(expected==null)return null;
        locks.ordered(List.of(PortalPolicy.user(expected.portalUserId())));
        return readBinding(expected.sourceKey(),expected.subject());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Applied apply(Origin origin,Binding expected,Instant at) {
        locks.transaction();var current=readBinding(expected.sourceKey(),expected.subject());
        if(!Objects.equals(current,expected)||!"EXTERNAL".equals(expected.mode())||!origin.sourceKey().equals(expected.sourceKey())||origin.kind()==null)throw ManagedErrorCode.REVISION_CONFLICT.failure();
        long id=ManagedCommand.positive(expected.portalUserId());
        if(!"Y".equals(jdbc.queryForObject("SELECT ACTIVE_YN FROM AI_ADMIN_PORTAL_USER WHERE PORTAL_USER_ID=?",String.class,id)))return new Applied("NO_CHANGE",expected.portalUserId(),expected.portalRevision(),null,null);
        String revision=Long.toString(Math.addExact(Long.parseLong(expected.portalRevision()),1));
        var stamp=new ManagedAssetAdapter.Stamp(ManagedCommand.requireKey(origin.commandKey()),null,revision,ids.next("AI_ADMIN_HISTORY_NO"),at,"CHANGE","검증된 외부 원천의 이용 중지");
        jdbc.update("UPDATE AI_ADMIN_PORTAL_USER SET ACTIVE_YN='N',REVISION=?,ACCEPTED_DTM=?,CHANGE_REASON=?,MOD_ID=NULL,MOD_DTM=? WHERE PORTAL_USER_ID=?",Long.parseLong(revision),Timestamp.from(at),stamp.reason(),Timestamp.from(at),id);
        dependencies.changed(expected.portalUserId(),stamp);
        users.history().captureExternal(expected.portalUserId(),stamp);
        return new Applied("APPLIED",expected.portalUserId(),revision,stamp.historyNo(),at);
    }
}
