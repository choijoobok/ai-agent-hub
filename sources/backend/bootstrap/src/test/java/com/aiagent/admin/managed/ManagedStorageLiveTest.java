// @feat FEAT-040
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** 실제 인증 필터·제품 공통 저장·Liquibase·전용 PostgreSQL. 업무 테이블은 소유 어댑터 fixture다. */
@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagedStorageLiveTest {
    private ManagedLiveFixture f;
    @BeforeAll void start() throws Exception { f = new ManagedLiveFixture(); }
    @AfterAll void stop() { if (f != null) f.close(); }

    @Test @DisplayName("TC-0331 TC-0332 TC-0336 TC-0401 전체 이력·원 시각·성공 결과 원자 저장")
    void completeTypedHistoryAndResult() throws Exception {
        var result = f.save(f.command(null, null, "첫 구성", List.of("가", "나")), 200);
        String id = result.path("targetId").asText();
        assertThat(result.path("revision").asText()).isEqualTo("1");
        assertThat(f.children(id)).isEqualTo(2);
        assertThat(f.historyChildren(result.path("historyNo").asText())).isEqualTo(2);
        var next = f.save(f.command(id, "1", "수정", List.of("가")), 200);
        assertThat(next.path("revision").asText()).isEqualTo("2");
        assertThat(f.historyChildren(result.path("historyNo").asText())).isEqualTo(2);
        f.assertTypedHistoryShapeAndOriginalTime(id);
    }

    @Test @DisplayName("TC-0334 TC-0337 TC-0401 이력 일부 실패는 현재값·결과까지 롤백")
    void historyFailureRollsBackEverything() throws Exception {
        long before = f.currentCount();
        var command = f.command(null, null, "이력 실패", List.of("실패"));
        f.failNextHistory();
        f.save(command, 503);
        assertThat(f.currentCount()).isEqualTo(before);
        assertThat(f.result(command.commandKey()).path("status").asText()).isEqualTo("UNKNOWN");
        f.save(command, 200);
        f.assertResultFailureRollback();
    }

    @Test @DisplayName("TC-0402 TC-0409 동시 동일키·다른 입력·원장 충돌·응답 유실")
    void replayIsExactlyOnceAcrossConcurrentRequests() throws Exception {
        var command = f.command(null, null, "재수신", List.of());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> f.save(command, 200));
            var second = executor.submit(() -> f.save(command, 200));
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        var result = f.result(command.commandKey());
        assertThat(result.path("status").asText()).isEqualTo("APPLIED");
        f.save(new ManagedCommand(command.commandKey(), null, null, "main", Map.of("name", "다른 입력", "children", List.of()), "사유"), 409);
        f.assertOtherActorUnknown(command.commandKey());
        f.assertOtherJournalConflictAndFailure();
    }

    @Test @DisplayName("TC-0334 TC-04014 독립 요청 부분 성공·같은 기준본 경합·복합 전체 롤백")
    void rowsAreIndependentAndStaleRevisionFails() throws Exception {
        var first = f.save(f.command(null, null, "독립1", List.of()), 200);
        var id = first.path("targetId").asText();
        var a = f.command(id, "1", "변경A", List.of());
        var b = f.command(id, "1", "변경B", List.of());
        f.save(a, 200); f.save(b, 409);
        f.save(f.command(null, null, "독립3", List.of()), 200);
        assertThat(f.currentName(id)).isEqualTo("변경A");
        assertThat(f.result(b.commandKey()).path("status").asText()).isEqualTo("UNKNOWN");
        f.assertConcurrentRevisions();
    }

    @Test @DisplayName("TC-0338 TC-0408 무변경·소유 허용 입력·비밀/관측 배제")
    void noChangeDoesNotMakeHistoryAndUnknownInputFails() throws Exception {
        var before = f.save(f.command(null, null, "그대로", List.of()), 200);
        var next = f.save(f.command(before.path("targetId").asText(), "1", "그대로", List.of()), 200);
        assertThat(next.path("outcome").asText()).isEqualTo("NO_CHANGE");
        assertThat(next.path("historyNo").isNull()).isTrue();
        assertThat(next.path("revision").asText()).isEqualTo("1");
        f.assertSecretAndForeignOwnerRejected();
        f.assertNoChangeRetention(next);
    }

    @Test @DisplayName("TC-03310 TC-03312 TC-0404 TC-04010 삭제 전 이력·정확 판·전이 보호·파기")
    void exactHistoryAndRetentionProtection() throws Exception { f.assertHistoryProtectionAndPurge(); }

    @Test @DisplayName("TC-0403 부모 참조 생성과 삭제의 양순서 경합")
    void referenceChangesShareParentLocks() throws Exception { f.assertReferenceRaces(); }

    @Test @DisplayName("TC-0339 TC-04013 TC-04014 실제 인증·명령 키 선행·회수·진입 증거 수명")
    void authenticatedAdmissionOrdersLocksAndRejectsBypass() throws Exception { f.assertAuthorityBoundary(); }

    @Test @DisplayName("TC-04011 TC-04012 자식 재귀속·도구/API 소유 혼입·추출 근거 위조")
    void ownersValidateTheirEntireInput() throws Exception { f.assertOwnerValidation(); }

    @Test @DisplayName("TC-0233 TC-0401 공통 스키마 적용/복구와 비재사용 ID")
    void migrationAndIdentifierContract() throws Exception { f.assertMigrationAndIds(); }
}
