// @feat FEAT-007
package com.aiagent.admin.managed;

import static com.aiagent.core.access.GroupAccess.*;
import static com.aiagent.core.access.CurrentDataAccess.*;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.portal.PortalAccountService;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.admin.datasource.DataSourceSyncService;
import com.aiagent.core.access.CurrentDataAccess;
import com.aiagent.core.security.*;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.deployment.ExternalDataSources;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogPolicyLiveTest {
    private ManagedLiveFixture fixture;
    @BeforeAll void start() throws Exception {
        fixture = new ManagedLiveFixture("LOCAL", true, CatalogLiveTest.Settings.class);
        var loaded = fixture.bean(ExternalDataSources.class);
        fixture.bean(ConfigurationDeploymentService.class).allow(loaded.environment(), loaded.bootId(), 0, UUID.randomUUID(), "fixture", false);
        fixture.bean(DataSourceSyncService.class).synchronize(loaded);
    }
    @AfterAll void close() { if (fixture != null) fixture.close(); }

    @Test @DisplayName("TC-0072 TC-0073 현재 메타정보 그룹 판정·정확 판·회수와 실제 데이터 경계")
    void metadataUsesCurrentAccountAndGroupsOnly() throws Exception {
        var jdbc = fixture.database();
        String month = jdbc.queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')", String.class);
        jdbc.update("INSERT INTO ai_admin_quota_grade VALUES(900707,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_admin_quota_grade_set VALUES(900707,?,'정책 시험',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)", month);
        jdbc.update("INSERT INTO ai_admin_quota_grade_item VALUES(900707,900707,'기본',1,1,NULL,'')");
        String group = fixture.groupCall("save", command(null, null, "definition", Map.of("groupNm", "메타정보 허용", "description", "정책 연결 시험")), 200).path("targetId").asText();
        var profile = new LinkedHashMap<String,Object>(Map.of("loginId", "catalog707", "userNm", "정책 시험", "organizationNm", "시험", "activeYn", "N", "useStartDt", month + "-01"));
        profile.put("useEndDt", null);
        String user = fixture.adminCall("portalUser", "save", command(null, null, "profile", profile), 200).path("targetId").asText();
        fixture.adminCall("portalUser", "save", command(user, "1", "memberships", Map.of("memberships", List.of(Map.of("groupId", group, "activeYn", "Y", "memo", "시험")), "deactivateUser", false)), 200);
        profile.put("activeYn", "Y");
        fixture.adminCall("portalUser", "save", command(user, "2", "profile", profile), 200);
        String secret = fixture.adminCall("portalUser", "resetCredential", command(user, "3", "resetCredential", Map.of()), 200).path("password").asText();
        var principal = new PrincipalRef(AuthDomain.PORTAL, user);
        fixture.bean(ConfigLookup.class).inNewSnapshot(() -> {
            var accounts = fixture.bean(PortalAccountService.class);
            var verified = accounts.verifyLocalCredentials("catalog707", secret);
            return accounts.changeOwnLocalPassword(principal, verified.credentialVersion(), secret, "Catalog707!Changed9");
        });
        String ds = jdbc.queryForObject("SELECT DATA_SOURCE_ID::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_KEY='orders'", String.class);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        @SuppressWarnings("unchecked") Map<String,Object> dataSource = mapper.convertValue(fixture.adminCall("dataSource", "getOne", Map.of("id", ds), 200).path("values"), Map.class);
        dataSource.put("applicableGroups", List.of(Map.of("groupId", group, "mappingActive", true)));
        String dsRevision = jdbc.queryForObject("SELECT MANAGEMENT_VERSION::text FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?", String.class, Long.valueOf(ds));
        fixture.adminCall("dataSource", "save", command(ds, dsRevision, "main", dataSource), 200);
        var master = Map.<String,Object>of("dataSourceId", ds, "databaseName", "managed040_slice", "schemaName", "public", "objectName", "catalog707", "objectType", "TABLE", "businessName", "메타정보", "useYn", true);
        String id = fixture.adminCall("catalog", "saveCreate", command(null, null, "MASTER", master), 200).path("targets").get(0).path("targetId").asText();
        var access = fixture.bean(CurrentDataAccess.class);
        assertThat(access.withCurrentAccess(request(principal, id, "1", Purpose.MODEL_INPUT, Set.of()), use -> "메타정보").status()).isEqualTo(OutcomeStatus.DENIED);
        fixture.adminCall("catalog", "saveChange", command(id, "1", "ACCESS_GROUPS", Map.of("groups", List.of(Map.of("groupId", group, "mappingActive", true)))), 200);
        var allowed = access.withCurrentAccess(request(principal, id, "2", Purpose.MODEL_INPUT, Set.of()), use -> {
            assertThat(use.project(new Payload(use.decision().binding(), List.of(Map.of("secret", "업무 원값")))).status()).isEqualTo(ProjectionStatus.MISMATCH);
            return use.decision().binding();
        });
        assertThat(allowed.status()).isEqualTo(OutcomeStatus.COMPLETED);
        assertThat(allowed.value().policyRevisions()).containsEntry("CATALOG:" + id, "2");
        assertThat(access.withCurrentAccess(request(principal, id, "1", Purpose.MODEL_INPUT, Set.of()), use -> "낡은 판").status()).isEqualTo(OutcomeStatus.UNAVAILABLE);
        for (var purpose : List.of(Purpose.SEARCH, Purpose.QUERY, Purpose.EXECUTE, Purpose.RESULT))
            assertThat(access.withCurrentAccess(request(principal, id, "2", purpose, Set.of()), use -> "미승인 목적").status()).isEqualTo(OutcomeStatus.UNAVAILABLE);
        assertThat(access.withCurrentAccess(request(principal, id, "2", Purpose.MODEL_INPUT, Set.of("secret")), use -> "업무 값").status()).isEqualTo(OutcomeStatus.UNAVAILABLE);
        fixture.adminCall("catalog", "saveChange", command(id, "2", "ACCESS_GROUPS", Map.of("groups", List.of(Map.of("groupId", group, "mappingActive", false)))), 200);
        assertThat(access.withCurrentAccess(request(principal, id, "3", Purpose.MODEL_INPUT, Set.of()), use -> "회수됨").status()).isEqualTo(OutcomeStatus.DENIED);
        fixture.adminCall("catalog", "saveChange", command(id, "3", "ACCESS_GROUPS", Map.of("groups", List.of(Map.of("groupId", group, "mappingActive", true)))), 200);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var accepted = workers.submit(() -> access.withCurrentAccess(request(principal, id, "4", Purpose.MODEL_INPUT, Set.of()), use -> {
                entered.countDown(); await(release); return "수락 보호";
            }));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var revoked = workers.submit(() -> { started.countDown(); return fixture.adminCall("catalog", "saveChange", command(id, "4", "ACCESS_GROUPS", Map.of("groups", List.of(Map.of("groupId", group, "mappingActive", false)))), 200); });
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> revoked.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(accepted.get(10, TimeUnit.SECONDS).status()).isEqualTo(OutcomeStatus.COMPLETED);
            revoked.get(10, TimeUnit.SECONDS);
        }
        assertThat(access.withCurrentAccess(request(principal, id, "5", Purpose.MODEL_INPUT, Set.of()), use -> "회수 후").status()).isEqualTo(OutcomeStatus.DENIED);
    }
    @Test @DisplayName("TC-0073 등록 근거 컬럼 적용·롤백·재적용")
    void auditMigrationRoundTrip() throws Exception {
        String schema = "catalog_migration007_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("MANAGED_TEST_JDBC"), System.getenv("MANAGED_TEST_USER"), System.getenv().getOrDefault("MANAGED_TEST_PASSWORD", ""))) {
            connection.createStatement().execute("CREATE SCHEMA " + schema); connection.setSchema(schema);
            try (var migration = new liquibase.Liquibase("db/catalog007-migration.yaml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection))) {
                migration.update(new liquibase.Contexts()); assertAuditColumns(connection, 4);
                migration.rollback(1, ""); assertAuditColumns(connection, 0);
                migration.update(new liquibase.Contexts()); assertAuditColumns(connection, 4);
            }
        }
    }
    private static void assertAuditColumns(java.sql.Connection connection, int count) throws Exception {
        try (var rows = connection.createStatement().executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('ai_admin_column_access_policy','ai_admin_column_access_policy_hist') AND column_name IN ('reg_id','reg_dtm')")) {
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(count);
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시험 보호 시간 초과"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("시험 중단", failure); }
    }
    private static Request request(PrincipalRef principal, String id, String revision, Purpose purpose, Set<String> fields) {
        return new Request(principal, purpose, new Target("CATALOG", id, revision), fields, UUID.randomUUID().toString());
    }
    private static ManagedCommand command(String id, String revision, String scope, Map<String,Object> values) {
        return new ManagedCommand(UUID.randomUUID().toString(), id, revision, scope, values, "카탈로그 정책 연결 검증");
    }
}
