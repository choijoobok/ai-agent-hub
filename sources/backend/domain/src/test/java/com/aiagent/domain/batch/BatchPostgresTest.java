// @feat FEAT-508
package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.aiagent.core.error.BusinessException;
import com.aiagent.domain.batch.entity.SysBatchHistory;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.history.BatchCancelService;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.repository.SysScheduleBatchRepository;
import com.aiagent.domain.batch.run.*;
import com.aiagent.domain.lock.BatchLeaseStore;
import com.aiagent.domain.notification.producer.BatchFailNotifier;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.lock.NodeId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** TC-5086~5089. 독립 PostgreSQL/JPA/실제 행 잠금이며 권한 생산자는 root의 HTTP 시험 소유다. */
class BatchPostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static EntityManager entities;
    static PlatformTransactionManager transactions;
    static BatchRunner runner;
    static BatchHistoryWriter writer;
    static BatchCancelService cancel;
    static BatchLeaseStore leases;
    static BatchRowLock rows;
    static SysBatchHistoryRepository histories;
    static Queue queue;
    static BatchHeartbeat heartbeat;
    static final AtomicReference<Gate> gate = new AtomicReference<>();

    @BeforeAll static void open() {
        assertThat(System.getProperty("batch.pg.url")).isEqualTo("jdbc:postgresql://127.0.0.1:55453/batch_test");
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        entities = context.getBean("entities", EntityManager.class);
        transactions = context.getBean(PlatformTransactionManager.class);
        runner = context.getBean(BatchRunner.class);
        writer = context.getBean(BatchHistoryWriter.class);
        cancel = context.getBean(BatchCancelService.class);
        leases = context.getBean(BatchLeaseStore.class);
        rows = context.getBean(BatchRowLock.class);
        histories = context.getBean(SysBatchHistoryRepository.class);
        queue = context.getBean(Queue.class);
        heartbeat = context.getBean(BatchHeartbeat.class);
        BatchRowLock target = AopTestUtils.getTargetObject(rows);
        doAnswer(call -> {
            Object row = call.callRealMethod();
            Gate current = gate.get();
            if (current != null && current.owner == Thread.currentThread()) {
                current.locked.countDown();
                assertThat(current.release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return row;
        }).when(target).history(anyLong());
    }

    @AfterAll static void close() { if (context != null) context.close(); }

    @BeforeEach void resetDatabase() {
        gate.set(null);
        queue.clear();
        clearInvocations(heartbeat);
        jdbc.execute("truncate table SYS_BATCH_HISTORY, SYS_DATA_LOCK, SYS_SCHEDULE_BATCH");
        tx().executeWithoutResult(status -> {
            entities.persist(batch("A", 0));
            entities.persist(batch("B", 0));
        });
    }

    @Test void multiAdmissionIsInvisibleUntilCommitAndRollbackLeavesNothing() {
        tx().executeWithoutResult(status -> {
            assertThat(runner.runNow(List.of("B", "A"))).allMatch(BatchRunner.RunOutcome::started);
            assertThat(queue.tasks).isEmpty();
            verify(heartbeat, never()).start(anyString(), anyString(), anyLong(), anyInt());
            assertThat(jdbc.queryForObject("select count(*) from SYS_DATA_LOCK", Integer.class)).isEqualTo(2);
            status.setRollbackOnly();
        });
        assertThat(count("SYS_BATCH_HISTORY")).isZero();
        assertThat(count("SYS_DATA_LOCK")).isZero();
        assertThat(queue.tasks).isEmpty();
        assertThat(runner.runNow(List.of("B", "A"))).allMatch(BatchRunner.RunOutcome::started);
        assertThat(count("SYS_BATCH_HISTORY")).isEqualTo(2);
        assertThat(queue.tasks).hasSize(2);
    }

    @Test void missingScheduleIsRejectedBeforeAnyAdmissionAndDbFailureRollsBackAllTargets() {
        assertThatThrownBy(() -> runner.runNow(List.of("A", "MISSING"))).isInstanceOf(BusinessException.class);
        assertThat(count("SYS_BATCH_HISTORY")).isZero();
        assertThat(queue.tasks).isEmpty();
        jdbc.execute("alter table SYS_BATCH_HISTORY add constraint BATCH_TEST_REJECT_B check (BATCH_ID <> 'B')");
        try {
            assertThatThrownBy(() -> runner.runNow(List.of("A", "B"))).isInstanceOf(RuntimeException.class);
            assertThat(count("SYS_BATCH_HISTORY")).isZero();
            assertThat(count("SYS_DATA_LOCK")).isZero();
            assertThat(queue.tasks).isEmpty();
        } finally { jdbc.execute("alter table SYS_BATCH_HISTORY drop constraint BATCH_TEST_REJECT_B"); }
    }

    @Test void aRejectedQueueEndsOnlyThatHistoryAndOtherCommittedTargetsStillRun() {
        queue.rejectAt.set(1);
        assertThat(runner.runNow(List.of("A", "B"))).allMatch(BatchRunner.RunOutcome::started);
        assertThat(result("A")).isEqualTo("FAIL");
        assertThat(result("B")).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select count(*) from SYS_DATA_LOCK where LOCK_KEY='A'", Integer.class)).isZero();
        assertThat(queue.tasks).hasSize(1);
        queue.tasks.getFirst().run();
        assertThat(result("B")).isEqualTo("SUCCESS");
        assertThat(count("SYS_DATA_LOCK")).isZero();
    }

    @Test void concurrentNodesCannotAdmitTheSameScheduleTwice() throws Exception {
        CountDownLatch admitted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<BatchRunner.RunOutcome>> first = pool.submit(() -> tx().execute(status -> {
                var outcome = runner.runNow(List.of("A"));
                admitted.countDown(); await(commit);
                return outcome;
            }));
            assertThat(admitted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<List<BatchRunner.RunOutcome>> second = pool.submit(() -> runner.runNow(List.of("A")));
            try { awaitBlocked(); assertThat(queue.tasks).isEmpty(); }
            finally { commit.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).getFirst().started()).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS).getFirst().started()).isFalse();
            assertThat(count("SYS_BATCH_HISTORY")).isEqualTo(1);
            assertThat(queue.tasks).hasSize(1);
        }
    }

    @Test void cancelThenFinishKeepsTheFirstRequestAndActualSuccess() throws Exception {
        long id = start();
        race(() -> cancel.cancel(List.of(id, id)), () -> writer.finish(id, BatchResultCode.SUCCESS, "완료", null, null), false);
        Instant first = jdbc.queryForObject("select CANCEL_REQ_DTM from SYS_BATCH_HISTORY where HIST_ID=?", java.sql.Timestamp.class, id).toInstant();
        assertThat(result("A")).isEqualTo("SUCCESS");
        assertThatThrownBy(() -> cancel.cancel(List.of(id))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select CANCEL_REQ_DTM from SYS_BATCH_HISTORY where HIST_ID=?", java.sql.Timestamp.class, id).toInstant()).isEqualTo(first);
    }

    @Test void finishThenCancelRejectsWithoutRestoringRunningOrClearingTheResult() throws Exception {
        long id = start();
        race(() -> writer.finish(id, BatchResultCode.SUCCESS, "실제 결과", null, null), () -> cancel.cancel(List.of(id)), true);
        assertThat(result("A")).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("select RSLT_MSG from SYS_BATCH_HISTORY where HIST_ID=?", String.class, id)).isEqualTo("실제 결과");
        assertThat(jdbc.queryForObject("select CANCEL_REQ_DTM from SYS_BATCH_HISTORY where HIST_ID=?", java.sql.Timestamp.class, id)).isNull();
    }

    @Test void lostThenActualFinishCorrectsTheResultAndFinishThenLostDoesNothing() throws Exception {
        long id = start();
        race(() -> writer.markLost(id, "유실"), () -> writer.finish(id, BatchResultCode.SUCCESS, "실제 완료", null, null), false);
        assertThat(result("A")).isEqualTo("SUCCESS");
        long second = tx().execute(status -> writer.start("B", BatchExecType.MANUAL));
        race(() -> writer.finish(second, BatchResultCode.FAIL, "실제 실패", null, null), () -> assertThat(writer.markLost(second, "유실")).isFalse(), false);
        assertThat(result("B")).isEqualTo("FAIL");
        writer.finish(second, BatchResultCode.SUCCESS, "중복 종결", null, null);
        assertThat(result("B")).isEqualTo("FAIL");
    }

    @Test void reverseAndDuplicateCancellationListsUseTheSameDatabaseLockOrder() throws Exception {
        runner.runNow(List.of("A", "B"));
        long a = jdbc.queryForObject("select HIST_ID from SYS_BATCH_HISTORY where BATCH_ID='A'", Long.class);
        long b = jdbc.queryForObject("select HIST_ID from SYS_BATCH_HISTORY where BATCH_ID='B'", Long.class);
        var first = new AtomicReference<BatchCancelService.CancelResult>();
        var second = new AtomicReference<BatchCancelService.CancelResult>();
        race(() -> first.set(cancel.cancel(List.of(b, a, b))), () -> second.set(cancel.cancel(List.of(a, b))), false);
        assertThat(first.get().requested()).isEqualTo(2);
        assertThat(second.get().requested()).isZero();
        assertThat(second.get().alreadyRequested()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from SYS_BATCH_HISTORY where CANCEL_REQ_DTM is not null", Integer.class)).isEqualTo(2);
    }

    @Test void aFinishedTargetRollsBackTheWholeCancellationList() {
        runner.runNow(List.of("A", "B"));
        long a = jdbc.queryForObject("select HIST_ID from SYS_BATCH_HISTORY where BATCH_ID='A'", Long.class);
        long b = jdbc.queryForObject("select HIST_ID from SYS_BATCH_HISTORY where BATCH_ID='B'", Long.class);
        writer.finish(b, BatchResultCode.SUCCESS, "완료", null, null);
        assertThatThrownBy(() -> cancel.cancel(List.of(a, b))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select CANCEL_REQ_DTM from SYS_BATCH_HISTORY where HIST_ID=?", java.sql.Timestamp.class, a)).isNull();
        assertThat(result("B")).isEqualTo("SUCCESS");
    }

    @Test void stalePersistenceContextIsRefreshedAfterTheRowLock() throws Exception {
        long id = start();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            tx().executeWithoutResult(status -> {
                SysBatchHistory stale = entities.find(SysBatchHistory.class, id);
                assertThat(stale.getCancelReqDtm()).isNull();
                try { pool.submit(() -> cancel.cancel(List.of(id))).get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
                assertThat(stale.getCancelReqDtm()).isNull();
                assertThat(cancel.cancel(List.of(id)).alreadyRequested()).isEqualTo(1);
                assertThat(stale.getCancelReqDtm()).isNotNull();
            });
            tx().executeWithoutResult(status -> {
                SysBatchHistory stale = entities.find(SysBatchHistory.class, id);
                assertThat(stale.getRsltCd()).isEqualTo(BatchResultCode.RUNNING);
                try { pool.submit(() -> writer.finish(id, BatchResultCode.SUCCESS, "실제 완료", null, null)).get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
                assertThatThrownBy(() -> cancel.cancel(List.of(id))).isInstanceOf(BusinessException.class);
                status.setRollbackOnly();
            });
        }
        assertThat(result("A")).isEqualTo("SUCCESS");
    }

    @Test void oldSameNodeWorkerCannotReleaseOrRenewTheNewLeaseAndCooldownHasNoOwner() {
        Instant now = Instant.now();
        tx().executeWithoutResult(status -> assertThat(leases.tryAcquire("A", 9000L, now.plusSeconds(300), null, true)).isTrue());
        tx().executeWithoutResult(status -> assertThat(leases.tryAcquire("A", 7L, now.plusSeconds(300), now.plusSeconds(120), true)).isTrue());
        leases.release("A", 9000L);
        assertThat(leases.heartbeat("A", 9000L, now.plusSeconds(600))).isFalse();
        assertThat(leases.isHeldBy("A", 9000L, "batch-test-node")).isFalse();
        assertThat(leases.isHeldBy("A", 7L, "batch-test-node")).isTrue();
        leases.release("A", 7L);
        assertThat(count("SYS_DATA_LOCK")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select BATCH_HIST_ID from SYS_DATA_LOCK", Long.class)).isNull();
        assertThat(leases.heartbeat("A", 7L, now.plusSeconds(600))).isFalse();
        assertThat(leases.isHeldBy("A", 7L, "batch-test-node")).isFalse();
    }

    @Test void newLeaseChangesetAppliesRollsBackAndReappliesAgainstTheExistingTable() throws Exception {
        var root = java.nio.file.Path.of("../bootstrap/src/main/resources/db/changelog/modules/platform");
        String baseline = java.nio.file.Files.readString(root.resolve("001-platform-schema.sql"));
        String migration = java.nio.file.Files.readString(root.resolve("003-batch-lease.sql"));
        var table = java.util.regex.Pattern.compile("(?s)CREATE TABLE sys_data_lock \\(.*?\\);").matcher(baseline);
        assertThat(table.find()).isTrue();
        String rollback = migration.lines().filter(line -> line.startsWith("--rollback "))
                .map(line -> line.substring("--rollback ".length())).collect(java.util.stream.Collectors.joining("\n"));
        String schema = "batch_migration_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("create schema " + schema);
                try {
                    statement.execute("set search_path to " + schema);
                    statement.execute(table.group());
                    statement.execute(migration);
                    try (var columns = statement.executeQuery("select data_type,is_nullable from information_schema.columns where table_schema='" + schema + "' and column_name='batch_hist_id'")) {
                        assertThat(columns.next()).isTrue();
                        assertThat(columns.getString(1)).isEqualTo("bigint");
                        assertThat(columns.getString(2)).isEqualTo("YES");
                    }
                    statement.execute(rollback);
                    try (var columns = statement.executeQuery("select count(*) from information_schema.columns where table_schema='" + schema + "' and column_name='batch_hist_id'")) {
                        assertThat(columns.next()).isTrue();
                        assertThat(columns.getInt(1)).isZero();
                    }
                    statement.execute(migration);
                } finally { statement.execute("drop schema " + schema + " cascade"); }
            }
            return null;
        });
    }

    @Test void leasePreservesInstantsWithTheExistingTimestampPhysicalColumns() {
        jdbc.execute("alter table SYS_DATA_LOCK alter column EXPIRE_DTM type timestamp without time zone, alter column RELEASE_DTM type timestamp without time zone, alter column CRE_DTM type timestamp without time zone");
        try {
            Instant until = Instant.now().plusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            tx().executeWithoutResult(status -> assertThat(leases.tryAcquire("A", 77L, until, null, true)).isTrue());
            Instant stored = jdbc.queryForObject("select EXPIRE_DTM AT TIME ZONE current_setting('TimeZone') from SYS_DATA_LOCK", java.time.OffsetDateTime.class).toInstant();
            assertThat(stored).isEqualTo(until);
            assertThat(leases.isHeldBy("A", 77L, "batch-test-node")).isTrue();
            assertThat(leases.heartbeat("A", 77L, until.plusSeconds(100))).isTrue();
            leases.release("A", 77L);
            assertThat(count("SYS_DATA_LOCK")).isZero();
        } finally {
            jdbc.execute("alter table SYS_DATA_LOCK alter column EXPIRE_DTM type timestamp with time zone, alter column RELEASE_DTM type timestamp with time zone, alter column CRE_DTM type timestamp with time zone");
        }
    }

    @Test void deletedScheduleLeavesRunningForOperatorReview() {
        start();
        jdbc.update("delete from SYS_SCHEDULE_BATCH where BATCH_ID='A'");
        assertThat(context.getBean(BatchGhostSweeper.class).sweep()).isZero();
        assertThat(result("A")).isEqualTo("RUNNING");
    }

    private long start() {
        runner.runNow(List.of("A"));
        return jdbc.queryForObject("select HIST_ID from SYS_BATCH_HISTORY where BATCH_ID='A'", Long.class);
    }
    private static TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private String result(String batch) { return jdbc.queryForObject("select RSLT_CD from SYS_BATCH_HISTORY where BATCH_ID=?", String.class, batch); }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }
    private static void awaitBlocked() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            if (jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class) > 0) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL 행 잠금 대기를 확인하지 못했다");
    }
    private static void race(Runnable first, Runnable second, boolean rejected) throws Exception {
        Gate block = new Gate(); gate.set(block);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> one = pool.submit(() -> { block.owner = Thread.currentThread(); first.run(); });
            assertThat(block.locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> two = pool.submit(second);
            try { awaitBlocked(); } finally { block.release.countDown(); }
            one.get(10, TimeUnit.SECONDS);
            if (rejected) assertThatThrownBy(() -> two.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            else two.get(10, TimeUnit.SECONDS);
        } finally { block.release.countDown(); gate.set(null); }
    }
    static final class Gate {
        volatile Thread owner;
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }
    static final class Queue implements Executor {
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();
        final AtomicInteger rejectAt = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        @Override public void execute(Runnable task) {
            if (calls.incrementAndGet() == rejectAt.get()) throw new RejectedExecutionException("검수용 큐 거절");
            tasks.add(task);
        }
        void clear() { tasks.clear(); rejectAt.set(0); calls.set(0); }
    }
    private static SysScheduleBatch batch(String id, int cooldown) {
        return SysScheduleBatch.created(id, id, "검수", "testJob", "0 0 2 * * *", "Asia/Seoul", null, 300, cooldown, true, 0);
    }

    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = SysBatchHistoryRepository.class)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getProperty("batch.pg.url"), "batch_test", ""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource data) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(data);
            factory.setPackagesToScan("com.aiagent.domain.batch.entity", "com.aiagent.domain.lock.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }
        @Bean EntityManager entities(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate jdbc(DataSource data) { return new JdbcTemplate(data); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean NodeId node() { return new NodeId("batch-test-node"); }
        @Bean BatchRowLock rows(EntityManager entities) { return spy(new BatchRowLock(entities)); }
        @Bean BatchLeaseStore leases(JdbcTemplate jdbc, NodeId node, Clock clock) { return new BatchLeaseStore(jdbc, node, clock); }
        @Bean BatchFailNotifier notifier() { return mock(BatchFailNotifier.class); }
        @Bean BatchHistoryWriter writer(SysBatchHistoryRepository histories, BatchFailNotifier notifier, NodeId node, Clock clock, BatchRowLock rows) {
            return new BatchHistoryWriter(histories, notifier, node, clock, rows);
        }
        @Bean BatchCancelService cancel(BatchRowLock rows, Clock clock) { return new BatchCancelService(rows, clock); }
        @Bean BatchHeartbeat heartbeat() {
            var value = mock(BatchHeartbeat.class);
            when(value.start(anyString(), anyString(), anyLong(), anyInt())).thenReturn(mock(BatchHeartbeat.Beat.class));
            return value;
        }
        @Bean(name=BatchExecutorConfig.EXECUTOR) Queue queue() { return new Queue(); }
        @Bean ConfigLookup configs() {
            var configs = mock(ConfigLookup.class);
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(configs).inNewSnapshot(any(Runnable.class));
            return configs;
        }
        @Bean BatchRunner runner(SysBatchHistoryRepository histories, BatchHistoryWriter writer, BatchLeaseStore leases, BatchHeartbeat heartbeat, ConfigLookup configs, Queue queue, BatchRowLock rows) {
            return new BatchRunner(histories, writer, new BatchJobRegistry(Map.of("testJob", ctx -> BatchJobResult.of("완료", null))), leases, heartbeat, configs, queue, rows);
        }
        @Bean BatchGhostSweeper sweeper(BatchHistoryWriter writer, BatchLeaseStore leases, SysScheduleBatchRepository batches) { return new BatchGhostSweeper(writer, leases, batches); }
    }
}
