// @feat FEAT-509
package com.aiagent.domain.notification.source;

import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.MenuAction;
import com.aiagent.domain.authz.repository.SysUserRoleRepository;
import com.aiagent.domain.authz.service.AdminAuthorityProtection;
import com.aiagent.domain.authz.service.EffectiveAuthorityService;
import com.aiagent.domain.notification.source.SourceNotification.*;
import com.aiagent.domain.user.repository.SysUserRepository;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthLoginProps;
import com.aiagent.support.time.BusinessCalendar;
import java.util.LinkedHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 현재 수신자 선정→호출자 저장/참조 인계를 같은 REQUIRED 트랜잭션으로 묶는다. */
@Service
public class SourceNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SourceNotificationService.class);
    private final AdminAuthorityProtection protection;
    private final SysUserRoleRepository roles;
    private final SysUserRepository users;
    private final EffectiveAuthorityService authorities;
    private final ConfigLookup configs;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    public SourceNotificationService(AdminAuthorityProtection protection, SysUserRoleRepository roles,
            SysUserRepository users, EffectiveAuthorityService authorities, ConfigLookup configs,
            BusinessCalendar calendar, PlatformTransactionManager transactions) {
        this.protection = protection; this.roles = roles; this.users = users; this.authorities = authorities;
        this.configs = configs; this.calendar = calendar; this.transaction = new TransactionTemplate(transactions);
    }

    /** calendar/Portal 자원 잠금보다 먼저 호출한다. callback은 동기 실행하며 저장 예외는 그대로 전파한다. */
    public Result withCurrentSourceNotificationRecipients(VerifiedSourceNotificationOrigin origin,
            RecipientPolicyRef policy, Function<RecipientSnapshot, Result> callback) {
        if (!policy.requiredReadMenuActions().contains(new MenuAction(origin.safeTargetRef().menuCd(), ActionType.READ)))
            throw new IllegalArgumentException("상세 링크의 READ 권한이 수신 정책에 필요하다");
        return transaction.execute(status -> {
            RecipientSnapshot snapshot;
            try {
                protection.acquire(false);
                var recipients = new LinkedHashMap<String, String>();
                if (!policy.roleCodes().isEmpty()) {
                    var today = calendar.today();
                    var now = calendar.now();
                    int releaseMinutes = configs.get(AuthLoginProps.class).lockReleaseMinutes();
                    for (String id : roles.findCurrentRecipientIds(policy.roleCodes(), today)) {
                        var user = users.findById(id).orElseThrow(() -> new org.springframework.dao.DataRetrievalFailureException("수신 계정 조회가 불완전하다"));
                        if (Boolean.TRUE.equals(user.getUseYn()) && !Boolean.TRUE.equals(user.getDormantYn())
                                && user.isWithinValidPeriod(today) && !user.isLocked(now, releaseMinutes)
                                && authorities.calculate(id).grants().containsAll(policy.requiredReadMenuActions()))
                            recipients.put(id, user.getLocaleCd() == null ? "ko" : user.getLocaleCd());
                    }
                }
                if (recipients.isEmpty()) return new NoRecipients();
                snapshot = new RecipientSnapshot(policy.policyVersion(), recipients, calendar.now(), origin);
            } catch (DataAccessException failure) {
                status.setRollbackOnly();
                log.warn("외부 사건 알림 수신자 조회 실패 — 전체 트랜잭션을 취소한다 ({})", failure.getClass().getSimpleName());
                return new Unavailable();
            }
            try { return callback.apply(snapshot); }
            finally { snapshot.close(); }
        });
    }
}
