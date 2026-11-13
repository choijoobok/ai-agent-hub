package com.aiagent.domain.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiagent.core.error.BusinessException;
import com.aiagent.core.grid.RowState;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.run.BatchRowLock;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.repository.SysScheduleBatchRepository;
import com.aiagent.domain.batch.schedule.BatchScheduleService;
import com.aiagent.domain.batch.schedule.BatchScheduleSaveRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 배치 등록의 저장 — screen/admin/system/batch/schedule/requirements.md.
 *
 * <p><b>여기서 막는 것 절반은 화면이 못 하는 것</b>이다 — 등록된 빈 목록은 스프링
 * 컨텍스트가 알고, 크론의 최소 간격은 파서가 있어야 안다(frontend/grid.md).
 *
 * <p><b>한때 화면으로는 여기 닿을 수 없었다.</b> 템플릿에 {@code BatchJob} 구현이 하나도
 * 없어(screen/admin/system/batch/schedule/requirements.md) 실제 화면에서는 <i>빈이 없다</i> 에서 언제나 먼저 걸렸다. 보존 파기
 * 배치({@code retentionPurgeJob})가 서면서 셀렉트에 값이 하나 생겼고, 이제 그 뒤의 검증들이
 * <b>화면에서도 실행된다</b> — 이 테스트가 유일한 확인 자리는 아니게 됐지만, 크론 파서와
 * 쿨다운의 관계는 <b>값싸게 지킬 자리가 여전히 여기</b>다(frontend/grid.md).
 */
class BatchScheduleServiceTest {

    private SysScheduleBatchRepository batches;
    private SysBatchHistoryRepository histories;
    private BatchJobRegistry registry;
    private BatchScheduleService service;

    @BeforeEach
    void setUp() {
        batches = mock(SysScheduleBatchRepository.class);
        histories = mock(SysBatchHistoryRepository.class);
        // 빈 하나가 등록돼 있다 — 그래야 그 뒤의 검증에 닿는다.
        registry = new BatchJobRegistry(java.util.Map.of("settleDailyJob", context -> null));
        BatchRowLock rowLocks = mock(BatchRowLock.class);
        when(rowLocks.schedule(anyString())).thenAnswer(call -> batches.findById(call.getArgument(0)));
        service = new BatchScheduleService(
                batches, histories, registry, mock(ApplicationEventPublisher.class), rowLocks);
    }

    /**
     * 그 <b>첫 행</b>의 칸들 — 행 단위로 모으면 칸이 `rowErrors[i].fields` 로 간다.
     *
     * <p>한 행만 보낸 저장이라 <i>첫 행</i> 이 곧 <i>그 행</i> 이다.
     */
    private static List<BusinessException.FieldError> fieldsOfFirstRow(BusinessException thrown) {
        assertThat(thrown.rowErrors()).isNotEmpty();
        return thrown.rowErrors().getFirst().fields();
    }

    private static BatchScheduleSaveRow row(RowState state) {
        return new BatchScheduleSaveRow(
                state, "SETTLE_DAILY", "야간 정산", "끄면 정산 화면이 전일 데이터를 못 본다",
                "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 10);
    }

    private static BusinessException save(BatchScheduleService service, BatchScheduleSaveRow row) {
        try {
            service.save(List.of(row));
            throw new AssertionError("거부됐어야 한다");
        } catch (BusinessException e) {
            return e;
        }
    }

    @Test
    @DisplayName("등록되지 않은 빈은 막는다 — 통과시키면 크론이 깨운 새벽에 터진다")
    void rejectsUnknownBean() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "noSuchJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.bean-unknown");
        // 셀에 붙어야 사용자가 어느 칸을 고칠지 안다(frontend/grid.md).
        assertThat(fieldsOfFirstRow(thrown)).extracting(BusinessException.FieldError::field)
                .containsExactly("beanId");
        verify(batches, never()).save(any());
    }

    @Test
    @DisplayName("설명이 비면 막는다 — 끄는 확인 창이 읽어 줄 문장이 없다")
    void rejectsEmptyDescription() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "  ", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.desc-required");
    }

    @Test
    @DisplayName("파서가 못 읽는 크론은 막는다 — 스케줄러가 쓰는 그 파서다")
    void rejectsUnreadableCron() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "매일 두시", "Asia/Seoul", null, 300, 0, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.cron-invalid");
    }

    @Test
    @DisplayName("TC-5082 알 수 없는 시간대는 저장 전에 거부하고 일정 행을 쓰지 않는다")
    void rejectsUnknownTimezone() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "X/Y", null, 300, 0, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.timezone-invalid");
        assertThat(fieldsOfFirstRow(thrown)).extracting(BusinessException.FieldError::field)
                .containsExactly("timezone");
        verify(batches, never()).save(any());
    }

    @Test
    @DisplayName("쿨다운이 크론 최소 간격 이상이면 막는다 — 실행이 조용히 건너뛰어진다")
    void rejectsCooldownLongerThanCron() {
        // 5분 주기(300초)에 쿨다운 600초 — 한 주기 걸러 한 번씩만 돈다.
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "0 */5 * * * *", "Asia/Seoul", null, 300, 600, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.cooldown-too-long");
        /*
          <b>오류가 두 셀에 걸린다</b>(screen/admin/system/batch/schedule/requirements.md) — 어느 쪽이 틀렸는지 서버가 말할 수 없다.
          틀린 것은 값이 아니라 <b>관계</b>이며, 문구가 두 값을 함께 읽어 준다.
        */
        assertThat(fieldsOfFirstRow(thrown)).extracting(BusinessException.FieldError::field)
                .containsExactly("cron", "lockMinIntervalSec");
    }

    @Test
    @DisplayName("같은 값도 막는다 — 경계에서 한 주기가 통째로 누락된다")
    void rejectsCooldownEqualToCron() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "0 */5 * * * *", "Asia/Seoul", null, 300, 300, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.cooldown-too-long");
    }

    @Test
    @DisplayName("JSON 이 아닌 파라미터는 막는다 — 화면도 보지만 화면 밖 경로가 있다")
    void rejectsBrokenParamJson() {
        BusinessException thrown = save(service, new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", "{oops", 300, 0, true, 0));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.param-json");
    }

    @Test
    @DisplayName("빈 파라미터는 검증하지 않는다 — 파라미터 없는 배치가 정상이다")
    void blankParamIsNotJson() {
        when(batches.existsById(anyString())).thenReturn(false);

        service.save(List.of(new BatchScheduleSaveRow(
                RowState.C, "X", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", "  ", 300, 0, true, 0)));

        verify(batches).save(any());
    }

    @Test
    @DisplayName("켜져 있으면 못 지운다 — 다음 주기가 조용히 사라진다")
    void cannotDeleteWhileEnabled() {
        SysScheduleBatch enabled = SysScheduleBatch.created(
                "SETTLE_DAILY", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);
        when(batches.findById("SETTLE_DAILY")).thenReturn(Optional.of(enabled));

        BusinessException thrown = save(service, row(RowState.D));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.not-deletable");
        verify(batches, never()).delete(any());
    }

    @Test
    @DisplayName("실행 중이면 못 지운다 — 락이 잡힌 채 등록이 사라진다")
    void cannotDeleteWhileRunning() {
        SysScheduleBatch off = SysScheduleBatch.created(
                "SETTLE_DAILY", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, false, 0);
        when(batches.findById("SETTLE_DAILY")).thenReturn(Optional.of(off));
        when(histories.findRunningBatchIds(List.of("SETTLE_DAILY")))
                .thenReturn(List.of("SETTLE_DAILY"));

        BusinessException thrown = save(service, row(RowState.D));

        assertThat(thrown.errorCode().code()).isEqualTo("err.batch.not-deletable");
        verify(batches, never()).delete(any());
    }

    @Test
    @DisplayName("꺼져 있고 돌지 않으면 지운다 — 실행 이력은 게이트가 아니다")
    void deletesWhenOffAndIdle() {
        SysScheduleBatch off = SysScheduleBatch.created(
                "SETTLE_DAILY", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, false, 0);
        when(batches.findById("SETTLE_DAILY")).thenReturn(Optional.of(off));
        when(histories.findRunningBatchIds(List.of("SETTLE_DAILY"))).thenReturn(List.of());

        service.save(List.of(row(RowState.D)));

        // 이력이 남아 있어도 지운다 — FK 가 없고(sys/batch.md) 게이트로 삼으면 영영 못 지운다.
        verify(batches).delete(off);
    }

    @Test
    @DisplayName("한 행이 검증에 걸리면 앞 행도 쓰지 않는다 — 거부되는 요청이 쓰기를 내면 안 된다")
    void validatesEveryRowBeforeWritingAny() {
        when(batches.existsById(anyString())).thenReturn(false);

        BatchScheduleSaveRow good = new BatchScheduleSaveRow(
                RowState.C, "A", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);
        BatchScheduleSaveRow bad = new BatchScheduleSaveRow(
                RowState.C, "B", "이름", "설명", "noSuchJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);

        assertThatThrownBy(() -> service.save(List.of(good, bad)))
                .isInstanceOf(BusinessException.class);

        /*
          트랜잭션이 롤백하므로 데이터는 남지 않지만 <b>SQL 로그에는 남는다</b> — 그것을
          읽는 사람이 *검증에 걸린 저장이 왜 INSERT 를 했나* 를 묻게 된다.
        */
        verify(batches, never()).save(any());
    }

    @Test
    @DisplayName("같은 요청 안의 중복 ID 를 막는다 — 두 번째의 존재 확인이 첫 번째를 못 본다")
    void rejectsDuplicateIdsInOneRequest() {
        BatchScheduleSaveRow first = new BatchScheduleSaveRow(
                RowState.C, "SAME", "이름", "설명", "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0);

        assertThatThrownBy(() -> service.save(List.of(first, first)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().code())
                        .isEqualTo("err.batch.duplicate"));
    }

    /**
     * <b>고칠 것이 몇 개인지 한 번에 말한다</b> — core/error/RowTx.
     *
     * <p>하나씩 던지면 사용자가 저장을 N 번 눌러 오류를 하나씩 발견하고, 끝까지
     * <i>몇 개가 남았나</i> 를 모른다.
     */
    @Nested
    @DisplayName("여러 행이 걸리면 함께 모인다")
    class ManyRows {

        @Test
        @DisplayName("실패한 행이 있어도 끝까지 돌고, 순번은 요청 배열의 것이다")
        void collectsEveryFailureWithRequestIndex() {
            BusinessException thrown = saveAll(
                    // 0 — 없는 빈
                    new BatchScheduleSaveRow(RowState.C, "A", "이름", "설명",
                            "noSuchJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0),
                    // 1 — 통과
                    new BatchScheduleSaveRow(RowState.C, "B", "이름", "설명",
                            "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0),
                    // 2 — 쿨다운이 크론 주기 이상
                    new BatchScheduleSaveRow(RowState.C, "C", "이름", "설명",
                            "settleDailyJob", "0 */5 * * * *", "Asia/Seoul", null, 300, 600, true, 0));

            assertThat(thrown.rowErrors()).extracting(BusinessException.RowError::index)
                    .containsExactly(0, 2);
            verify(batches, never()).save(any());
        }

        @Test
        @DisplayName("사유가 달라도 함께 모이고, 봉투의 코드는 맨 앞 실패의 것이다")
        void mixedReasonsKeepTheFirstCode() {
            BusinessException thrown = saveAll(
                    new BatchScheduleSaveRow(RowState.C, "A", "이름", "설명",
                            "noSuchJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0),
                    new BatchScheduleSaveRow(RowState.C, "B", "이름", "  ",
                            "settleDailyJob", "0 0 2 * * *", "Asia/Seoul", null, 300, 0, true, 0));

            assertThat(thrown.errorCode().code()).isEqualTo("err.batch.bean-unknown");
            assertThat(thrown.rowErrors()).extracting(BusinessException.RowError::index)
                    .containsExactly(0, 1);
        }

        private BusinessException saveAll(BatchScheduleSaveRow... rows) {
            try {
                service.save(List.of(rows));
                throw new AssertionError("거부됐어야 한다");
            } catch (BusinessException e) {
                return e;
            }
        }
    }
}
