// @feat FEAT-501
package com.aiagent.system.api.auth;

import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.api.auth.LoginContexts;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.v2.SessionAuthority;
import com.aiagent.domain.authz.EffectiveAuthority;
import com.aiagent.domain.authz.service.ApiAuthorityRegistry;
import com.aiagent.domain.authz.service.EffectiveAuthorityService;
import com.aiagent.domain.user.service.UserAccountService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthLoginProps;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.system.domain.auth.AllowedIpService;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 현재 계정·권한 판정과 업무 쓰기를 같은 PostgreSQL 보호 트랜잭션으로 묶는다. */
@Component
public class AdminAuthorityGuard implements SessionAuthority {
    private static final Object MODE = new Object();
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final UserAccountService users;
    private final EffectiveAuthorityService authorities;
    private final ApiAuthorityRegistry registry;
    private final AllowedIpService ips;
    private final ConfigLookup configs;
    private final BusinessCalendar calendar;
    private final AdminApiCatalog catalog;

    public AdminAuthorityGuard(DataSource source, EntityManager entities, UserAccountService users,
            EffectiveAuthorityService authorities, ApiAuthorityRegistry registry, AllowedIpService ips,
            ConfigLookup configs, BusinessCalendar calendar, AdminApiCatalog catalog) {
        this.jdbc = new JdbcTemplate(source); this.entities = entities; this.users = users;
        this.authorities = authorities; this.registry = registry; this.ips = ips;
        this.configs = configs; this.calendar = calendar; this.catalog = catalog;
    }

    @Override public void protect(Context context, boolean exclusive, Runnable command) {
        if (context.domain() != AuthDomain.ADMIN || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("관리자 보호는 쓰기 가능한 같은 REQUIRED 트랜잭션이어야 한다");
        boolean firstProtection = acquire(exclusive);
        EffectiveAuthority authority = null;
        if (context.principal() != null) {
            if (context.principal().domain() != AuthDomain.ADMIN) throw new Rejected(401);
            var user = users.find(context.principal().subjectId()).orElseThrow(SessionAuthority.Unusable::new);
            if (firstProtection && entities.contains(user)) entities.refresh(user);
            if (!Boolean.TRUE.equals(user.getUseYn()) || !user.isWithinValidPeriod(calendar.today())
                    || user.isLocked(calendar.now(), configs.get(AuthLoginProps.class).lockReleaseMinutes())
                    || Boolean.TRUE.equals(user.getDormantYn())
                    || !ips.isAllowed(user.getUserId(), LoginContexts.of(AdminRequestContext.request()).clientIp()))
                throw new SessionAuthority.Unusable();
            AdminRequestContext.request().setAttribute("admin.audit.userId", user.getUserId());
            authority = authorities.calculate(user.getUserId());
            AdminRequestContext.request().setAttribute(com.aiagent.api.authz.AuthzVersionHeader.NAME, authority.version());
            if (!context.selfScope()) {
                var entry = catalog.entry(context.operationId());
                var fact = registry.find(entry.path()).orElseThrow(() -> new Rejected(403));
                if (!fact.action().name().equals(context.requiredAction()) || !fact.isMapped() || !fact.isAllowedBy(authority))
                    throw new Rejected(403);
            }
        } else if (!context.selfScope()) throw new Rejected(401);
        AdminRequestContext.withAuthority(authority, command);
    }

    private boolean acquire(boolean exclusive) {
        Object held = TransactionSynchronizationManager.getResource(MODE);
        if (held != null) {
            if (exclusive && Boolean.FALSE.equals(held)) throw new IllegalStateException("공유 보호를 배타 보호로 승격할 수 없다");
            return false;
        }
        jdbc.execute("SELECT " + (exclusive ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared") + "(1095321685,1)");
        TransactionSynchronizationManager.bindResource(MODE, exclusive);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() { TransactionSynchronizationManager.unbindResourceIfPossible(MODE); }
            @Override public void resume() { TransactionSynchronizationManager.bindResource(MODE, exclusive); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(MODE); }
        });
        // 잠금 대기 전에 읽힌 1차 캐시가 현재 권한 판정을 대체하지 않는다.
        entities.clear();
        return true;
    }
}
