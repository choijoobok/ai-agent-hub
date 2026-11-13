// @feat FEAT-509
package com.aiagent.system.notification;

import static org.assertj.core.api.Assertions.*;

import com.aiagent.AiAgentApplication;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.v2.SessionAuthority.Context;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.MenuAction;
import com.aiagent.domain.notification.*;
import com.aiagent.domain.notification.inbox.MyNotiService;
import com.aiagent.domain.notification.repository.SysNotiInboxRepository;
import com.aiagent.domain.notification.source.SourceNotification.*;
import com.aiagent.domain.notification.source.SourceNotificationService;
import com.aiagent.system.api.auth.AdminAuthorityGuard;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import com.aiagent.support.config.ConfigLookup;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 제품 Liquibase·JPA·공개 포트·관리자 보호를 전용 PostgreSQL에서 검증한다. */
@Tag("sourceNotificationLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceNotificationLiveTest {
    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;
    private SourceNotificationService port;
    private NotificationSender sender;
    private TransactionTemplate tx;
    private AdminAuthorityGuard guard;
    @RegisterExtension final InvocationInterceptor configScope = new InvocationInterceptor() {
        @Override public void interceptTestMethod(Invocation<Void> invocation,
                ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
            var failure = new AtomicReference<Throwable>();
            app.getBean(ConfigLookup.class).inNewSnapshot(() -> {
                try { invocation.proceed(); } catch (Throwable caught) { failure.set(caught); }
            });
            if (failure.get() != null) throw failure.get();
        }
    };
    private final RecipientPolicyRef policy = new RecipientPolicyRef("deployment-1", "policy-1",
            List.of("NOTI_A", "NOTI_B"), Set.of(new MenuAction("USER_MGMT", ActionType.READ)));
    private final VerifiedSourceNotificationOrigin origin = new VerifiedSourceNotificationOrigin(
            "receipt-1", "a".repeat(64), ChangeKind.RETIRED, new SafeTargetRef("USER_MGMT", "alice"));

    @BeforeAll void start() {
        String url = System.getenv("SOURCE_NOTI_TEST_JDBC");
        if (url == null || !url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/system521_[A-Za-z0-9_]+"))
            throw new IllegalStateException("전용 localhost system521_ 시험 DB가 필요하다");
        String user = System.getenv("SOURCE_NOTI_TEST_USER");
        String password = System.getenv().getOrDefault("SOURCE_NOTI_TEST_PASSWORD", "");
        String schema = "noti521_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, user, password)).execute("CREATE SCHEMA " + schema);
        app = new SpringApplication(AiAgentApplication.class).run("--spring.profiles.active=system-auth-test",
                "--server.port=0", "--spring.datasource.url=" + url + "?currentSchema=" + schema,
                "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                "--app.auth.session.origin=http://127.0.0.1:18521", "--app.auth.session.local-http=true",
                "--app.auth.session.epoch=" + UUID.randomUUID(), "--app.auth.session.store-instance=noti521-test",
                "--app.auth.session.provider=jdbc", "--app.logging.sql.enabled=false", "--logging.level.root=WARN");
        jdbc = new JdbcTemplate(app.getBean(DataSource.class));
        port = app.getBean(SourceNotificationService.class);
        sender = app.getBean(NotificationSender.class);
        guard = app.getBean(AdminAuthorityGuard.class);
        tx = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        // 실제 ENT-266은 SLICE-305 소유다. 여기서는 동일 DB 호출자의 참조 저장 경계만 검증한다.
        jdbc.execute("CREATE TABLE noti521_reference(noti_id bigint REFERENCES sys_noti_inbox(noti_id), marker int CHECK(marker=1))");
        for (String id : List.of("alice", "bob", "disabled", "expired", "outsider", "no_read"))
            jdbc.update("INSERT INTO sys_user(user_id,user_nm,pwd,use_yn,lock_yn,login_fail_cnt,pwd_init_yn,dormant_yn,locale_cd) VALUES (?,?,?,'Y','N',0,'N','N','ko')", id, id, "unused");
        for (String role : List.of("NOTI_A", "NOTI_B")) {
            jdbc.update("INSERT INTO sys_role(role_id,role_nm,use_yn) VALUES (?,?,'Y')", role, role);
            jdbc.update("INSERT INTO sys_role_menu_auth(role_id,menu_cd,action_cd) VALUES (?,'USER_MGMT','READ')", role);
        }
        jdbc.update("INSERT INTO sys_role(role_id,role_nm,use_yn) VALUES ('NOTI_NO_READ','조회권한없음','Y')");
        for (String id : List.of("alice", "bob", "disabled", "expired"))
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,'NOTI_A')", id);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES ('alice','NOTI_B'),('no_read','NOTI_NO_READ')");
        jdbc.update("UPDATE sys_user SET use_yn='N' WHERE user_id='disabled'");
        jdbc.update("UPDATE sys_user_role SET valid_to_dt=CURRENT_DATE-1 WHERE user_id='expired'");
    }
    @AfterAll void stop() { if (app != null) app.close(); }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM noti521_reference");
        jdbc.update("DELETE FROM sys_noti_inbox");
        jdbc.update("UPDATE sys_user SET use_yn='Y' WHERE user_id IN ('alice','bob')");
    }
    private Result deliver() {
        return app.getBean(ConfigLookup.class).inNewSnapshot(() -> port.withCurrentSourceNotificationRecipients(origin, policy,
                snapshot -> sender.storeSourceChangeNotification(origin, snapshot)));
    }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM sys_noti_inbox", Integer.class); }

    @Test @DisplayName("TC-5098 역할 합집합·현재 자격·정확 저장 ID·Portal 동일문자열 수신자")
    void storesExactRecipientsAndIdsInCallerTransaction() {
        tx.executeWithoutResult(status -> {
            Result result = port.withCurrentSourceNotificationRecipients(origin, policy, snapshot -> {
                assertThat(snapshot.sortedDistinctAdminIds()).containsExactly("alice", "bob");
                Stored stored = sender.storeSourceChangeNotification(origin, snapshot);
                stored.notifications().forEach(ref -> jdbc.update("INSERT INTO noti521_reference VALUES (?,1)", ref.notiId()));
                return stored;
            });
            assertThat(result).isInstanceOf(Stored.class);
            Stored stored = (Stored) result;
            assertThat(stored.policyVersion()).isEqualTo("policy-1");
            assertThat(stored.notifications()).allSatisfy(ref -> assertThat(jdbc.queryForObject(
                    "SELECT user_id FROM sys_noti_inbox WHERE noti_id=?", String.class, ref.notiId())).isEqualTo(ref.adminUserId()));
        });
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT content FROM sys_noti_inbox", String.class))
                .allSatisfy(content -> assertThat(content).doesNotContain("alice", "receipt-1", "aaaaaaaa"));
        assertThat(jdbc.queryForList("SELECT title FROM sys_noti_inbox", String.class))
                .containsOnly("퇴직에 따른 Portal 이용 상태 변경");
        tx.executeWithoutResult(status -> {
            var moved = new VerifiedSourceNotificationOrigin("receipt-2", "b".repeat(64), ChangeKind.ORG_MOVED, origin.safeTargetRef());
            var result = (Stored) port.withCurrentSourceNotificationRecipients(moved, policy,
                    snapshot -> sender.storeSourceChangeNotification(moved, snapshot));
            assertThat(jdbc.queryForObject("SELECT title FROM sys_noti_inbox WHERE noti_id=?", String.class,
                    result.notifications().getFirst().notiId())).isEqualTo("조직 이동에 따른 Portal 이용 상태 변경");
            status.setRollbackOnly();
        });
        assertThat(port.withCurrentSourceNotificationRecipients(origin,
                new RecipientPolicyRef("d", "p", List.of("NOTI_NO_READ"), policy.requiredReadMenuActions()), s -> fail("조회 권한 없는 수신자")))
                .isInstanceOf(NoRecipients.class);
    }

    @Test @DisplayName("TC-5099 정상0명과 실제 조회 장애 구분·호출자 쓰기 전체 취소")
    void zeroIsNotLookupFailure() {
        assertThat(port.withCurrentSourceNotificationRecipients(origin,
                new RecipientPolicyRef("d", "p", List.of(), policy.requiredReadMenuActions()), s -> fail("빈 수신자")))
                .isInstanceOf(NoRecipients.class);
        jdbc.execute("ALTER TABLE sys_user_role RENAME TO noti521_hidden_roles");
        try {
            assertThat(deliver()).isInstanceOf(Unavailable.class);
            assertThat(count()).isZero();
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE sys_user SET user_nm='취소되어야 함' WHERE user_id='bob'");
                assertThat(deliver()).isInstanceOf(Unavailable.class);
            })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
            assertThat(jdbc.queryForObject("SELECT user_nm FROM sys_user WHERE user_id='bob'", String.class)).isEqualTo("bob");
        } finally { jdbc.execute("ALTER TABLE noti521_hidden_roles RENAME TO sys_user_role"); }
    }

    @Test @DisplayName("TC-5099 관리자 권한 회수 확정 뒤 현재 수신자만 선정")
    void waitsForCurrentAuthorityBeforeChoosingRecipients() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> revocation = pool.submit(() -> tx.executeWithoutResult(status -> guard.protect(
                    new Context(AuthDomain.ADMIN, null, null, null, null, null, true, null, "521"), true, () -> {
                        jdbc.update("UPDATE sys_user SET use_yn='N' WHERE user_id='bob'");
                        locked.countDown();
                        await(release);
                    })));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Result> delivery = pool.submit(this::deliver);
            assertThatThrownBy(() -> delivery.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            revocation.get(5, TimeUnit.SECONDS);
            assertThat(((Stored) delivery.get(5, TimeUnit.SECONDS)).notifications())
                    .extracting(StoredNotification::adminUserId).containsExactly("alice");
        } finally { release.countDown(); }
    }

    @Test @DisplayName("TC-50910 일부 알림 저장 실패와 호출자 참조 실패 모두 전체 rollback")
    void partialStorageAndReferenceFailureRollbackTogether() {
        jdbc.execute("ALTER TABLE sys_noti_inbox ADD CONSTRAINT noti521_reject CHECK(user_id <> 'bob')");
        try {
            assertThatThrownBy(this::deliver).hasStackTraceContaining("noti521_reject");
            assertThat(count()).isZero();
        } finally { jdbc.execute("ALTER TABLE sys_noti_inbox DROP CONSTRAINT noti521_reject"); }
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> port.withCurrentSourceNotificationRecipients(origin, policy, snapshot -> {
            Stored stored = sender.storeSourceChangeNotification(origin, snapshot);
            jdbc.update("INSERT INTO noti521_reference VALUES (?,2)", stored.notifications().getFirst().notiId());
            return stored;
        }))).hasStackTraceContaining("noti521_reference_marker_check");
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM noti521_reference", Integer.class)).isZero();
    }

    @Test @DisplayName("TC-5099 보호 구간 밖·독립 트랜잭션·재사용 snapshot 저장 차단")
    void snapshotCannotEscapeOrStoreTwice() {
        var saved = new AtomicReference<RecipientSnapshot>();
        port.withCurrentSourceNotificationRecipients(origin, policy, snapshot -> {
            saved.set(snapshot);
            var independent = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(() -> independent.execute(status -> sender.storeSourceChangeNotification(origin, snapshot)))
                    .isInstanceOf(IllegalStateException.class);
            return sender.storeSourceChangeNotification(origin, snapshot);
        });
        assertThatThrownBy(() -> sender.storeSourceChangeNotification(origin, saved.get())).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isEqualTo(2);
    }

    @Test @DisplayName("TC-50911 새 종류와 기존4종 조회·본인 읽음·AUTHZ 중복판정·90일 파기")
    void existingInboxAndExpiryQueriesRemainIndependent() {
        Stored stored = (Stored) deliver();
        var repository = app.getBean(SysNotiInboxRepository.class);
        var inbox = app.getBean(MyNotiService.class);
        assertThat(repository.findUserIdsNotifiedSince(NotiType.AUTHZ, Instant.EPOCH)).isEmpty();
        for (NotiType type : List.of(NotiType.AUTH, NotiType.AUTHZ, NotiType.BATCH, NotiType.REQUEST))
            sender.send("alice", type, "기존 알림", "기존 본문");
        assertThat(inbox.findMine("alice").rows()).extracting(row -> row.notiTypeCd()).containsExactlyInAnyOrder(NotiType.values());
        assertThat(repository.findUserIdsNotifiedSince(NotiType.AUTHZ, Instant.EPOCH)).containsExactly("alice");
        long id = stored.notifications().getFirst().notiId();
        assertThatThrownBy(() -> inbox.markRead("bob", id)).isInstanceOf(RuntimeException.class);
        inbox.markRead("alice", id);
        assertThat(repository.findById(id).orElseThrow().getReadYn()).isTrue();
        assertThat(inbox.markAllRead("alice")).isEqualTo(4);
        jdbc.update("UPDATE sys_noti_inbox SET cre_dtm=CURRENT_TIMESTAMP-INTERVAL '91 days' WHERE noti_id=?", id);
        var expired = repository.findIdsCreatedBefore(Instant.now().minus(Duration.ofDays(90)), org.springframework.data.domain.PageRequest.ofSize(100));
        assertThat(expired).containsExactly(id);
        repository.deleteAllByIdInBatch(expired);
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test @DisplayName("TC-50911 메시지 변경 SQL의 rollback·재적용은 기존 메시지를 보존한다")
    void messageMigrationCanBeRolledBackAndReapplied() throws Exception {
        var file = java.nio.file.Path.of("src/main/resources/db/changelog/modules/system/008-source-notification-message.sql");
        var lines = java.nio.file.Files.readAllLines(file);
        long before = jdbc.queryForObject("SELECT count(*) FROM sys_message", Long.class);
        try {
            lines.stream().filter(line -> line.startsWith("--rollback "))
                    .forEach(line -> jdbc.execute(line.substring("--rollback ".length())));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_message", Long.class)).isEqualTo(before - 8);
        } finally {
            lines.stream().filter(line -> !line.isBlank() && !line.startsWith("--")).forEach(jdbc::execute);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_message", Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT msg_val FROM sys_message WHERE msg_key='noti.type.PORTAL_CHANGE' AND lang_cd='ko'", String.class))
                .isEqualTo("Portal 이용 상태 변경");
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
