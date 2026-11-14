// @feat FEAT-305
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.core.security.portal.PortalChangeOwner;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** admin 소유 원장·전역 명령키·정확 HIST 보호의 중립 공개 조립이다. */
@Service
public class PortalChangeOwnerService implements PortalChangeOwner {
    private final PortalUserChangeService users;private final ManagedLocks locks;private final ManagedRegistry registry;
    private final ManagedCommandGuard guard;private final ManagedHistoryService history;private final JdbcTemplate jdbc;
    public PortalChangeOwnerService(PortalUserChangeService users,ManagedLocks locks,ManagedRegistry registry,ManagedCommandGuard guard,ManagedHistoryService history,DataSource source){this.users=users;this.locks=locks;this.registry=registry;this.guard=guard;this.history=history;jdbc=new JdbcTemplate(source);}
    public Binding readBinding(String source,String subject){return users.readBinding(source,subject);}
    public Binding protectBinding(Binding expected){return users.protectBinding(expected);}
    public Applied apply(Origin origin,Binding binding,Instant at){return users.apply(origin,binding,at);}
    public void protectCommand(String key){locks.command(key);if(guard.expired(key)||registry.find(key).filter(e->!e.feature().equals("FEAT-305")).isPresent())throw ManagedErrorCode.COMMAND_CONFLICT.failure();}
    public boolean expiredEvent(String digest){if(digest==null||!digest.matches("[a-f0-9]{64}"))throw ManagedErrorCode.VALIDATION.failure();return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_COMMAND_GUARD WHERE COMMAND_HASH=?",Long.class,digest)>0;}
    private ManagedHistoryService.Use use(String receipt){return new ManagedHistoryService.Use("integration","EXTERNAL_USER_CHANGE",receipt,"ORIGIN");}
    public void protectHistory(String receipt,Applied applied) {
        var ref=new ManagedHistoryStore.Ref("PORTAL_USER",applied.portalUserId(),applied.revision());
        if(registry.owner("PORTAL_USER").history().exact(ref).historyNo()!=applied.historyNo())throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        history.protect(use(receipt),List.of(ref));
    }
    public void expire(String eventDigest,String command,String receipt) {
        locks.transaction();
        jdbc.update("INSERT INTO AI_ADMIN_COMMAND_GUARD(COMMAND_HASH,OWNER_FEATURE,TERMINAL_CD) VALUES (?,'FEAT-305','EXPIRED'),(?,'FEAT-305','EXPIRED')",eventDigest,ManagedDigest.hash(command));
        history.release(use(receipt));
    }
}
