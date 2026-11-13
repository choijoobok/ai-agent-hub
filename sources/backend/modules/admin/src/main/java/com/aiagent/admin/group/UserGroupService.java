// @feat FEAT-006
package com.aiagent.admin.group;

import com.aiagent.admin.managed.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.transaction.annotation.*;

/** 그룹 정의만 저장한다. 소속은 Portal 부모, 자산 링크는 각 자산 소유자가 기록한다. */
public class UserGroupService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/userGroup";
    private final DataSource source;
    private final EntityManager em;
    private final ManagedIds ids;
    private final ManagedLocks locks;
    private final UserGroupLinks links;
    private final ManagedHistoryStore history;
    public UserGroupService(DataSource source,EntityManager em,ManagedIds ids,ManagedLocks locks,UserGroupLinks links) {
        this.source=source;this.em=em;this.ids=ids;this.locks=locks;this.links=links;
        history=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(new TypedHistoryStore.Table("AI_ADMIN_USER_GROUP","GROUP_ID",
                List.of("GROUP_ID","GROUP_NM","DESCRIPTION","REVISION","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM"))),rows->true,rows->List.of());
    }
    public DataSource dataSource(){return source;}
    public String feature(){return "FEAT-006";}
    public String kind(){return "USER_GROUP";}
    public String readPath(){return BASE+"/getList";}
    public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("definition");}
    public Map<String,Object> normalize(ManagedCommand command) {
        // remove는 빈 values를 쓰며 changed/apply/delete가 연산별 형식을 재검사한다.
        if(command.targetId()!=null&&command.values().isEmpty())return Map.of();
        return UserGroupDefinition.normalize(command);
    }
    public List<ManagedLocks.Key> locks(ManagedCommand command){return List.of();}
    public Current current(ManagedCommand command) {
        if(command.targetId()==null)return null;
        var row=em.find(UserGroup.class,ManagedCommand.positive(command.targetId()));
        return row==null?null:new Current(row.groupId.toString(),row.revision.toString());
    }
    public void validate(ManagedCommand command,Current before) {}
    public boolean changed(ManagedCommand command,Current before) {
        var values=UserGroupDefinition.normalize(command);
        var row=em.find(UserGroup.class,Long.parseLong(before.targetId()));
        return !row.groupNm.equals(values.get("groupNm"))||!row.description.equals(values.get("description"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        var values=UserGroupDefinition.normalize(command);
        var row=before==null?new UserGroup():em.find(UserGroup.class,Long.parseLong(before.targetId()));
        if(before==null){row.groupId=ids.next("AI_ADMIN_USER_GROUP");row.regId=stamp.actor();row.regDtm=stamp.at();}
        row.groupNm=(String)values.get("groupNm");row.description=(String)values.get("description");row.revision=Long.parseLong(stamp.revision());
        row.acceptedDtm=stamp.at();row.changeReason=stamp.reason();row.modId=stamp.actor();row.modDtm=stamp.at();
        if(before==null)em.persist(row);
        em.flush();return row.groupId.toString();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void delete(ManagedCommand command,Current before) {
        if(!command.values().isEmpty())throw ManagedErrorCode.VALIDATION.failure();
        long id=Long.parseLong(before.targetId());links.requireUnreferenced(id);
        em.remove(em.find(UserGroup.class,id));em.flush();
    }
    public ManagedHistoryStore history(){return history;}
    /** 원 소유 매핑 저장은 동일 트랜잭션에서 이 포트를 먼저 호출한다. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireExisting(Collection<String> groupIds) {
        var keys=groupIds.stream().map(id->{ManagedCommand.positive(id);return new ManagedLocks.Key(3,kind(),id);}).toList();
        locks.ordered(keys);
        for(String id:groupIds)if(em.find(UserGroup.class,Long.parseLong(id))==null)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
    }
}
