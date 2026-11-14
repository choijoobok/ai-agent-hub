// @feat FEAT-303
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.ManagedCommand;
import com.aiagent.core.access.*;
import com.aiagent.core.access.GroupAccess.*;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.support.config.ConfigLookup;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 현재 원장→공통 판정→내부 수락을 하나의 DB 보호/트랜잭션으로 조립한다. */
@Service
public class PortalDataAccess implements CurrentDataAccess {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final PortalAccessService accounts;
    private final ObjectProvider<PortalDataPolicySource> policies;
    private final ObjectProvider<PortalDataControl> controls;
    private final MaskingRegistry masks;
    private final GroupAccess evaluator;
    private final TransactionTemplate tx;
    private final ConfigLookup configs;
    public PortalDataAccess(DataSource source,PortalAccessService accounts,ObjectProvider<PortalDataPolicySource> policies,ObjectProvider<PortalDataControl> controls,ObjectProvider<MaskingRegistry.Rule> masks,PlatformTransactionManager manager,ConfigLookup configs) {
        this.source=source;jdbc=new JdbcTemplate(source);this.accounts=accounts;this.policies=policies;this.controls=controls;
        this.masks=new MaskingRegistry(masks.orderedStream().toList());evaluator=new GroupAccess(this.masks);tx=new TransactionTemplate(manager);this.configs=configs;
    }
    public <T> Outcome<T> withCurrentAccess(Request request,Function<Use,T> acceptance) {
        Objects.requireNonNull(acceptance);
        if(request==null||request.principal().domain()!=AuthDomain.PORTAL||!request.principal().subjectId().matches("[1-9][0-9]{0,17}"))return new Outcome<>(OutcomeStatus.DENIED,"PRINCIPAL_DENIED",null);
        var entered=new AtomicBoolean();
        try {
            return configs.inNewSnapshot(()->tx.execute(status->accounts.inspectCurrentAccount(request.principal(),account->{
                if(!account.outcome().equals("ALLOW"))return new Outcome<T>(account.outcome().equals("DENY")?OutcomeStatus.DENIED:OutcomeStatus.UNAVAILABLE,"ACCOUNT_UNAVAILABLE",null);
                var owners=policies.orderedStream().filter(p->p.assetKind().equals(request.target().kind())).toList();
                if(owners.size()!=1||owners.getFirst().dataSource()!=source)return new Outcome<T>(OutcomeStatus.UNAVAILABLE,"POLICY_SOURCE_UNAVAILABLE",null);
                var policy=owners.getFirst().read(request);
                if(policy==null)return new Outcome<T>(OutcomeStatus.UNAVAILABLE,"POLICY_SOURCE_UNAVAILABLE",null);
                var checks=checks(request,policy,account);
                var snapshot=new Snapshot(account.principal(),true,account.revision(),account.checkedAt(),account.groups(),policy,checks);
                return evaluator.withSnapshot(request,snapshot,use->{entered.set(true);return acceptance.apply(use);});
            })));
        } catch(RuntimeException failure) {
            // 판정 원천 장애만 안전 상태로 변환한다. 수락 콜백/commit 장애는 성공으로 숨기지 않고 전파한다.
            if(entered.get())throw failure;
            return new Outcome<>(OutcomeStatus.UNAVAILABLE,"CURRENT_SOURCE_UNAVAILABLE",null);
        }
    }
    private Map<Control,Check> checks(Request request,Policy policy,PortalAccessService.Access account) {
        var result=new EnumMap<Control,Check>(Control.class);
        for(var control:policy.requirements().all()) {
            Check check;
            if(control==Control.QUOTA)check=account.allocation()!=null&&account.allocation().state().equals("READY")?Check.PASSED:Check.UNAVAILABLE;
            else if(control==Control.IDENTITY)check=identity(request,policy.requirements());
            else {
                var owners=controls.orderedStream().filter(p->p.control()==control).toList();
                check=owners.size()==1&&owners.getFirst().dataSource()==source?owners.getFirst().check(request,policy,account):Check.UNAVAILABLE;
                if(check==null)check=Check.UNAVAILABLE;
            }
            result.put(control,check);
        }
        return result;
    }
    private Check identity(Request request,Requirements requirements) {
        if(!Set.of("SYSTEM","DATA_SOURCE").contains(requirements.identityKind())||requirements.identityTarget()==null)return Check.UNAVAILABLE;
        var ids=jdbc.queryForList("SELECT EXTERNAL_USER_ID FROM AI_ADMIN_USER_IDENTITY WHERE PORTAL_USER_ID=? AND TARGET_KIND=? AND TARGET_ID=? AND MAPPING_ACTIVE_YN='Y'",String.class,ManagedCommand.positive(request.principal().subjectId()),requirements.identityKind(),ManagedCommand.positive(requirements.identityTarget()));
        return ids.size()==1&&!ids.getFirst().isBlank()?Check.PASSED:Check.DENIED;
    }
}
