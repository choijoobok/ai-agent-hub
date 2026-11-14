// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.*;

/** 없는 생산 기능과 고장/누락된 생산 계약을 구별한다. */
public final class PortalDependencies {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<PortalQuotaPort> quota;
    private final ObjectProvider<PortalIdentityTargets> targets;
    private final ObjectProvider<PortalExternalDirectory> external;
    public PortalDependencies(DataSource source,ObjectProvider<PortalQuotaPort> quota,ObjectProvider<PortalIdentityTargets> targets,ObjectProvider<PortalExternalDirectory> external) {
        jdbc=new JdbcTemplate(source);this.quota=quota;this.targets=targets;this.external=external;
    }
    private PortalQuotaPort quota() {
        var found=quota.orderedStream().toList();if(found.size()>1)throw ManagedErrorCode.UNAVAILABLE.failure();
        if(!found.isEmpty())return found.getFirst();
        boolean absent=Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection->{
            String schema=connection.getSchema();if(schema==null||schema.isBlank())return false;
            try(var rows=connection.getMetaData().getTables(connection.getCatalog(),schema,null,null)) {
                while(rows.next()) {String name=rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT);
                    if(name.startsWith("AI_ADMIN_QUOTA_")&&!name.startsWith("AI_ADMIN_QUOTA_GRADE"))return false;
                }
            }
            return true;
        }));
        if(!absent)throw ManagedErrorCode.UNAVAILABLE.failure();return null;
    }
    public PortalQuotaPort.Allocation allocation(String user,String month) {
        var provider=quota();return provider==null?new PortalQuotaPort.Allocation("NOT_INSTALLED",null,null,null):provider.allocation(user,month);
    }
    public void mutable(String user,String month){var provider=quota();if(provider!=null)provider.requireGradeMutable(user,month);}
    public void changed(String user,ManagedAssetAdapter.Stamp stamp){var provider=quota();if(provider!=null)provider.eligibilityChanged(user,stamp);}
    public void requireQuota(){quota();}
    public List<Map<String,Object>> extras(String user){var provider=quota();return provider==null?List.of():provider.extraTokens(user);}
    private PortalIdentityTargets targetOwner(String kind){var owners=targets.orderedStream().filter(p->p.supports(kind)).toList();if(owners.size()>1)throw ManagedErrorCode.UNAVAILABLE.failure();return owners.isEmpty()?null:owners.getFirst();}
    public PortalIdentityTargets.Candidates targets(String kind) {
        var provider=targetOwner(kind);return provider==null?new PortalIdentityTargets.Candidates("NOT_CONNECTED",List.of()):provider.list(kind);
    }
    public void requireTarget(String kind,String id){var provider=targetOwner(kind);if(provider==null)throw ManagedErrorCode.UNAVAILABLE.failure();provider.requireWritable(kind,id);}
    public List<com.aiagent.admin.managed.ManagedLocks.Key> targetLocks(String kind,List<String> ids){var provider=targetOwner(kind);if(provider==null)throw ManagedErrorCode.UNAVAILABLE.failure();return provider.locks(kind,ids);}
    public PortalExternalDirectory.Identity external(String source,String subject) {
        var provider=external.getIfUnique();if(provider==null)throw ManagedErrorCode.UNAVAILABLE.failure();
        var identity=provider.verified(source,subject);
        if(identity==null||!source.equals(identity.sourceKey())||!subject.equals(identity.subject()))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();return identity;
    }
}
