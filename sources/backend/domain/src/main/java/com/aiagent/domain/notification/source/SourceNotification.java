// @feat FEAT-509
package com.aiagent.domain.notification.source;

import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.MenuAction;
import com.aiagent.domain.authz.service.AdminAuthorityProtection;
import java.time.Instant;
import java.util.*;

/** CR-034 내부 공개 계약. HTTP 요청 DTO로 사용하지 않으며 원천 검증은 SLICE-305가 소유한다. */
public final class SourceNotification {
    private SourceNotification() {}
    public enum ChangeKind { RETIRED, ORG_MOVED }

    public record RecipientPolicyRef(String configGeneration, String policyVersion,
            List<String> roleCodes, Set<MenuAction> requiredReadMenuActions) {
        public RecipientPolicyRef {
            text(configGeneration, 128); text(policyVersion, 128);
            roleCodes = roleCodes.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().sorted().toList();
            roleCodes.forEach(role -> text(role, 30));
            requiredReadMenuActions = Set.copyOf(requiredReadMenuActions);
            if (requiredReadMenuActions.isEmpty() || requiredReadMenuActions.stream().anyMatch(a -> a.action() != ActionType.READ))
                throw new IllegalArgumentException("수신 정책은 필요한 READ 권한을 명시해야 한다");
        }
    }
    /** 검증된 내부 대상의 메뉴 코드와 불투명 참조만 받는다. 원천 subject나 원문 본문은 받지 않는다. */
    public record SafeTargetRef(String menuCd, String targetRef) {
        public SafeTargetRef { text(menuCd, 50); text(targetRef, 500); }
    }
    public record VerifiedSourceNotificationOrigin(String receiptId, String eventKeyDigest,
            ChangeKind kind, SafeTargetRef safeTargetRef) {
        public VerifiedSourceNotificationOrigin {
            text(receiptId, 200);
            if (eventKeyDigest == null || !eventKeyDigest.matches("[a-fA-F0-9]{64}"))
                throw new IllegalArgumentException("검증된 사건 지문이 필요하다");
            Objects.requireNonNull(kind); Objects.requireNonNull(safeTargetRef);
        }
    }
    public sealed interface Result permits Stored, NoRecipients, Unavailable {}
    public record StoredNotification(String adminUserId, long notiId) {}
    /** 같은 트랜잭션의 임시 저장 결과다. 최외곽 commit 뒤에만 도달로 확정한다. */
    public record Stored(List<StoredNotification> notifications, String policyVersion) implements Result {
        public Stored { notifications = List.copyOf(notifications); }
    }
    public record NoRecipients() implements Result {}
    /** 호출자는 503으로 인계한다. 수신자 없음으로 처리하거나 부분 업무를 확정하면 안 된다. */
    public record Unavailable() implements Result {}

    /** 보호 callback 안에서만 한 번 저장할 수 있다. 외부에서 수신자 목록을 구성할 수 없다. */
    public static final class RecipientSnapshot {
        private final String policyVersion;
        private final Map<String, String> languages;
        private final List<String> ids;
        private final Instant checkedAt;
        private final VerifiedSourceNotificationOrigin origin;
        private final Object token = AdminAuthorityProtection.currentToken();
        private final Thread thread = Thread.currentThread();
        private boolean active = true;
        private boolean consumed;

        RecipientSnapshot(String policyVersion, Map<String, String> languages, Instant checkedAt,
                VerifiedSourceNotificationOrigin origin) {
            this.policyVersion = policyVersion;
            this.languages = Map.copyOf(languages);
            this.ids = languages.keySet().stream().sorted().toList();
            this.checkedAt = checkedAt;
            this.origin = origin;
        }
        public String policyVersion() { return policyVersion; }
        public List<String> sortedDistinctAdminIds() { return ids; }
        public Instant checkedAt() { return checkedAt; }
        public String language(String adminId) { return languages.get(adminId); }
        public void consume(VerifiedSourceNotificationOrigin requestedOrigin) {
            if (!active || consumed || thread != Thread.currentThread() || token == null
                    || token != AdminAuthorityProtection.currentToken() || !origin.equals(requestedOrigin))
                throw new IllegalStateException("수신자 snapshot은 같은 보호 트랜잭션·사건에서 한 번만 저장할 수 있다");
            consumed = true;
        }
        void close() { active = false; }
    }
    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("통지 계약 값의 길이 또는 형식이 올바르지 않다");
    }
}
