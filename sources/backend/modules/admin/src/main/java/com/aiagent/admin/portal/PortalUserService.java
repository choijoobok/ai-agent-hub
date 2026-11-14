// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.quota.QuotaGradeGuard;
import com.aiagent.support.time.BusinessCalendar;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

/** 프로필·소속·등급·두 신원 영역은 따로 저장하되 같은 부모 판과 전체 이력을 사용한다. */
public class PortalUserService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/portalUser",ROOT="AI_ADMIN_PORTAL_USER";
    private final JdbcTemplate jdbc;
    private final ManagedIds ids;
    private final ManagedLocks locks;
    private final PortalPolicy policy;
    private final BusinessCalendar calendar;
    private final PortalCredentials credentials;
    private final PortalDependencies dependencies;
    private final PortalGrades grades;
    private final String mode;
    private final ManagedHistoryStore history;
    public PortalUserService(DataSource source,ManagedIds ids,ManagedLocks locks,PortalPolicy policy,BusinessCalendar calendar,
                             PortalCredentials credentials,PortalDependencies dependencies,PortalGrades grades,String mode) {
        jdbc=new JdbcTemplate(source);this.ids=ids;this.locks=locks;this.policy=policy;this.calendar=calendar;this.credentials=credentials;this.dependencies=dependencies;this.grades=grades;this.mode=mode;
        if(!Set.of("LOCAL","EXTERNAL").contains(mode))throw new IllegalArgumentException("Portal 인증 모드는 LOCAL 또는 EXTERNAL이어야 한다");
        history=PortalHistory.create(source,ids);
    }
    public DataSource dataSource(){return jdbc.getDataSource();}
    public String feature(){return "FEAT-005";}
    public String kind(){return "PORTAL_USER";}
    public String readPath(){return BASE+"/getList";}
    public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("profile","memberships","grades","identitySystem","identityDataSource","resetCredential","unlockCredential");}
    public Map<String,Object> normalize(ManagedCommand command){return PortalDefinition.normalize(command);}
    public List<ManagedLocks.Key> locks(ManagedCommand command) {
        locks.ordered(List.of(QuotaGradeGuard.boundary()));
        var keys=new ArrayList<ManagedLocks.Key>();keys.add(new ManagedLocks.Key(1,"QUOTA_MONTH",today().toString().substring(0,7)));keys.add(PortalPolicy.KEY);
        if(command.targetId()!=null)keys.add(PortalPolicy.user(command.targetId()));
        if(command.scope().equals("memberships"))for(var row:PortalDefinition.rows(command.values(),"memberships"))keys.add(new ManagedLocks.Key(3,"USER_GROUP",(String)row.get("groupId")));
        if(command.scope().equals("grades"))for(var row:PortalDefinition.rows(command.values(),"grades")) {
            keys.add(new ManagedLocks.Key(1,"QUOTA_MONTH",(String)row.get("effectiveMonth")));
            if(row.get("originalMonth")!=null)keys.add(new ManagedLocks.Key(1,"QUOTA_MONTH",(String)row.get("originalMonth")));
        }
        return keys;
    }
    LocalDate today(){return policy.now().atZone(calendar.zone()).toLocalDate();}
    Map<String,Object> root(long id){var rows=jdbc.queryForList("SELECT * FROM "+ROOT+" WHERE PORTAL_USER_ID=?",id);return rows.isEmpty()?null:rows.getFirst();}
    public Current current(ManagedCommand command){if(command.targetId()==null)return null;var row=root(Long.parseLong(command.targetId()));return row==null?null:new Current(command.targetId(),row.get("REVISION").toString());}
    public Instant acceptedAt(Instant fallback){return policy.now();}
    public void validate(ManagedCommand command,Current before) {
        var values=command.values();String scope=command.scope();
        if(before==null&&!scope.equals("profile"))throw ManagedErrorCode.VALIDATION.failure();
        long id=before==null?0:Long.parseLong(before.targetId());var root=before==null?null:root(id);
        if(Set.of("profile","memberships","grades").contains(scope))dependencies.requireQuota();
        switch(scope) {
            case "profile" -> {
                if(before==null) {
                    if(!"N".equals(values.get("activeYn")))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM "+ROOT+" WHERE LOGIN_ID=?",Integer.class,values.get("loginId"))>0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    grades.initialGrade(today().toString().substring(0,7));
                    if(mode.equals("LOCAL")&&values.containsKey("externalSource")||mode.equals("EXTERNAL")&&!values.containsKey("externalSource"))throw ManagedErrorCode.VALIDATION.failure();
                    if(mode.equals("EXTERNAL")) {
                        var external=dependencies.external((String)values.get("externalSource"),(String)values.get("externalSubject"));
                        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+ROOT+" WHERE LOGIN_ID=?",Integer.class,external.loginId())>0
                                ||jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_PORTAL_EXTERNAL_ID WHERE SOURCE_KEY=? AND SUBJECT_ID=?",Integer.class,external.sourceKey(),external.subject())>0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    }
                } else {
                    if(!root.get("LOGIN_ID").equals(values.get("loginId"))||values.containsKey("externalSource"))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    if("EXTERNAL".equals(root.get("AUTH_MODE_CD"))&&(!root.get("USER_NM").equals(values.get("userNm"))||!root.get("ORGANIZATION_NM").equals(values.get("organizationNm"))))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    if("Y".equals(values.get("activeYn"))) {
                        if(activeGroups(id)==0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                        grades.requireEffective(id,today().toString().substring(0,7));
                    }
                }
            }
            case "memberships" -> {
                int active=activeGroups(id);
                for(var row:PortalDefinition.rows(values,"memberships")) {
                    long group=Long.parseLong((String)row.get("groupId"));
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_GROUP WHERE GROUP_ID=?",Integer.class,group)!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
                    var old=member(id,group);active+=("Y".equals(row.get("activeYn"))?1:0)-(old!=null&&"Y".equals(old.get("ACTIVE_YN"))?1:0);
                }
                if("Y".equals(root.get("ACTIVE_YN"))&&active==0&&!Boolean.TRUE.equals(values.get("deactivateUser")))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
            }
            case "grades" -> grades.validate(id,PortalDefinition.rows(values,"grades"),today());
            case "identitySystem","identityDataSource" -> {
                for(var row:PortalDefinition.rows(values,"identities"))dependencies.requireTarget(identityKind(scope),(String)row.get("targetId"));
            }
            case "resetCredential","unlockCredential" -> {if(!"LOCAL".equals(root.get("AUTH_MODE_CD")))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();credentials.require(id);}
            default -> throw ManagedErrorCode.VALIDATION.failure();
        }
    }
    int activeGroups(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_MEMBERSHIP WHERE PORTAL_USER_ID=? AND ACTIVE_YN='Y'",Integer.class,user);}
    private Map<String,Object> member(long user,long group){var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_USER_MEMBERSHIP WHERE PORTAL_USER_ID=? AND GROUP_ID=?",user,group);return rows.isEmpty()?null:rows.getFirst();}
    static String identityKind(String scope){return scope.equals("identitySystem")?"SYSTEM":"DATA_SOURCE";}
    public boolean changed(ManagedCommand command,Current before) {
        long id=Long.parseLong(before.targetId());var values=command.values();
        return switch(command.scope()) {
            case "profile" -> {var row=root(id);yield !same(row,"USER_NM",values,"userNm")||!same(row,"ORGANIZATION_NM",values,"organizationNm")||!same(row,"USE_START_DT",values,"useStartDt")||!same(row,"USE_END_DT",values,"useEndDt")||!same(row,"ACTIVE_YN",values,"activeYn");}
            case "memberships" -> Boolean.TRUE.equals(values.get("deactivateUser"))&&"Y".equals(root(id).get("ACTIVE_YN"))||PortalDefinition.rows(values,"memberships").stream().anyMatch(row->{var old=member(id,Long.parseLong((String)row.get("groupId")));return old==null||!same(old,"ACTIVE_YN",row,"activeYn")||!same(old,"MEMO",row,"memo");});
            case "grades" -> grades.changed(id,PortalDefinition.rows(values,"grades"));
            case "identitySystem","identityDataSource" -> PortalDefinition.rows(values,"identities").stream().anyMatch(row->{var old=identity(id,identityKind(command.scope()),(String)row.get("targetId"));return old==null||!same(old,"EXTERNAL_USER_ID",row,"externalUserId")||!same(old,"MAPPING_ACTIVE_YN",row,"mappingActiveYn")||!same(old,"IDENTITY_EVIDENCE",row,"identityEvidence");});
            case "unlockCredential" -> {var old=credentials.require(id);yield old.locked()||old.failures()!=0;}
            case "resetCredential" -> true;
            default -> throw ManagedErrorCode.VALIDATION.failure();
        };
    }
    static boolean same(Map<String,Object> old,String column,Map<String,Object> value,String key){return Objects.equals(old.get(column)==null?null:old.get(column).toString(),value.get(key));}
    private Map<String,Object> identity(long user,String kind,String target){var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_USER_IDENTITY WHERE PORTAL_USER_ID=? AND TARGET_KIND=? AND TARGET_ID=?",user,kind,Long.parseLong(target));return rows.isEmpty()?null:rows.getFirst();}
    @Transactional(propagation=Propagation.MANDATORY)
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        long user=before==null?ids.next(ROOT):Long.parseLong(before.targetId());var value=command.values();var at=Timestamp.from(stamp.at());
        switch(command.scope()) {
            case "profile" -> {
                if(before==null)create(user,value,stamp);
                else jdbc.update("UPDATE "+ROOT+" SET USER_NM=?,ORGANIZATION_NM=?,USE_START_DT=?,USE_END_DT=?,ACTIVE_YN=? WHERE PORTAL_USER_ID=?",value.get("userNm"),value.get("organizationNm"),java.sql.Date.valueOf((String)value.get("useStartDt")),date(value.get("useEndDt")),value.get("activeYn"),user);
            }
            case "memberships" -> {
                for(var row:PortalDefinition.rows(value,"memberships")) {
                    long group=Long.parseLong((String)row.get("groupId"));var old=member(user,group);
                    if(old!=null&&same(old,"ACTIVE_YN",row,"activeYn")&&same(old,"MEMO",row,"memo"))continue;
                    if(old==null)jdbc.update("INSERT INTO AI_ADMIN_USER_MEMBERSHIP(PORTAL_USER_ID,GROUP_ID,ACTIVE_YN,MEMO,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?)",user,group,row.get("activeYn"),row.get("memo"),stamp.actor(),at,stamp.actor(),at);
                    else jdbc.update("UPDATE AI_ADMIN_USER_MEMBERSHIP SET ACTIVE_YN=?,MEMO=?,MOD_ID=?,MOD_DTM=? WHERE PORTAL_USER_ID=? AND GROUP_ID=?",row.get("activeYn"),row.get("memo"),stamp.actor(),at,user,group);
                }
                if(Boolean.TRUE.equals(value.get("deactivateUser")))jdbc.update("UPDATE "+ROOT+" SET ACTIVE_YN='N' WHERE PORTAL_USER_ID=?",user);
            }
            case "grades" -> {grades.apply(user,PortalDefinition.rows(value,"grades"),stamp);grades.requireValidSchedule(user,today().toString().substring(0,7));}
            case "identitySystem","identityDataSource" -> saveIdentities(user,identityKind(command.scope()),PortalDefinition.rows(value,"identities"),stamp);
            case "resetCredential" -> credentials.reset(user,stamp.at());
            case "unlockCredential" -> credentials.unlock(user);
            default -> throw ManagedErrorCode.VALIDATION.failure();
        }
        touch(user,stamp);
        if(Set.of("profile","memberships","grades").contains(command.scope()))dependencies.changed(Long.toString(user),stamp);
        return Long.toString(user);
    }
    private void create(long user,Map<String,Object> values,Stamp stamp) {
        String login=(String)values.get("loginId"),name=(String)values.get("userNm"),organization=(String)values.get("organizationNm"),source="LOCAL";
        PortalExternalDirectory.Identity external=null;
        if(mode.equals("EXTERNAL")){external=dependencies.external((String)values.get("externalSource"),(String)values.get("externalSubject"));login=external.loginId();name=external.userNm();organization=external.organizationNm();source=external.sourceKey();}
        var at=Timestamp.from(stamp.at());
        jdbc.update("INSERT INTO "+ROOT+"(PORTAL_USER_ID,LOGIN_ID,USER_NM,ORGANIZATION_NM,AUTH_MODE_CD,SOURCE_CD,USE_START_DT,USE_END_DT,ACTIVE_YN,REVISION,ACCEPTED_DTM,CHANGE_REASON,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,'N',1,?,?,?,?,?,?)",
                user,login,name,organization,mode,source,java.sql.Date.valueOf((String)values.get("useStartDt")),date(values.get("useEndDt")),at,stamp.reason(),stamp.actor(),at,stamp.actor(),at);
        grades.initial(user,today().toString().substring(0,7),stamp);
        if(external==null)credentials.create(user,stamp.at());
        else jdbc.update("INSERT INTO AI_ADMIN_PORTAL_EXTERNAL_ID(PORTAL_USER_ID,SOURCE_KEY,SUBJECT_ID,LINKED_DTM,SOURCE_REVISION,BINDING_KEY) VALUES (?,?,?,?,?,?)",user,external.sourceKey(),external.subject(),at,external.sourceRevision(),UUID.randomUUID());
    }
    private void saveIdentities(long user,String kind,List<Map<String,Object>> rows,Stamp stamp) {
        var at=Timestamp.from(stamp.at());
        for(var row:rows) {
            long target=Long.parseLong((String)row.get("targetId"));var old=identity(user,kind,Long.toString(target));
            if(old!=null&&same(old,"EXTERNAL_USER_ID",row,"externalUserId")&&same(old,"MAPPING_ACTIVE_YN",row,"mappingActiveYn")&&same(old,"IDENTITY_EVIDENCE",row,"identityEvidence"))continue;
            if(old==null)jdbc.update("INSERT INTO AI_ADMIN_USER_IDENTITY(PORTAL_USER_ID,TARGET_KIND,TARGET_ID,EXTERNAL_USER_ID,MAPPING_ACTIVE_YN,IDENTITY_EVIDENCE,CHANGE_REASON,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?)",user,kind,target,row.get("externalUserId"),row.get("mappingActiveYn"),row.get("identityEvidence"),stamp.reason(),stamp.actor(),at,stamp.actor(),at);
            else jdbc.update("UPDATE AI_ADMIN_USER_IDENTITY SET EXTERNAL_USER_ID=?,MAPPING_ACTIVE_YN=?,IDENTITY_EVIDENCE=?,CHANGE_REASON=?,MOD_ID=?,MOD_DTM=? WHERE PORTAL_USER_ID=? AND TARGET_KIND=? AND TARGET_ID=?",row.get("externalUserId"),row.get("mappingActiveYn"),row.get("identityEvidence"),stamp.reason(),stamp.actor(),at,user,kind,target);
        }
    }
    void touch(long user,Stamp stamp){jdbc.update("UPDATE "+ROOT+" SET REVISION=?,ACCEPTED_DTM=?,CHANGE_REASON=?,MOD_ID=?,MOD_DTM=? WHERE PORTAL_USER_ID=?",Long.parseLong(stamp.revision()),Timestamp.from(stamp.at()),stamp.reason(),stamp.actor(),Timestamp.from(stamp.at()),user);}
    static java.sql.Date date(Object value){return value==null?null:java.sql.Date.valueOf((String)value);}
    public void delete(ManagedCommand command,Current before){throw ManagedErrorCode.REFERENCE_CONFLICT.failure();}
    public ManagedHistoryStore history(){return history;}
}
