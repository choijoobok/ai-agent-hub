package com.aiagent.domain.batch.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.RowTx;
import com.aiagent.core.grid.RowState;
import com.aiagent.domain.batch.BatchJobRegistry;
import com.aiagent.domain.batch.run.BatchRowLock;
import com.aiagent.domain.batch.entity.SysScheduleBatch;
import com.aiagent.domain.batch.repository.SysBatchHistoryRepository;
import com.aiagent.domain.batch.repository.SysScheduleBatchRepository;
import java.util.HashSet;
import com.aiagent.support.time.Timezones;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code BATCH_SCHEDULE} 의 저장 — screen/admin/system/batch/schedule/requirements.md. <b>위험한 편집을 다루는 첫 화면</b>이다.
 *
 * <p>screen/menu/requirements.md 의 메뉴 트리는 잘못 고치면 화면이 안 보인다. 배치는 <b>잘못 고치면 조용히
 * 안 돈다</b> — 그것이 이 서비스의 검증이 촘촘한 이유이며, screen/admin/system/batch/schedule/requirements.md 와 §3.6 의 전제다.
 *
 * <h2>검증이 넷이고 <b>둘은 서버만 할 수 있다</b></h2>
 *
 * <table><caption></caption>
 * <tr><td>이름 · 설명 필수</td><td>화면도 한다 — 여기는 마지막 방어다(backend/security.md)</td></tr>
 * <tr><td>{@code PARAM_JSON} 구문</td><td>화면이 {@code JSON.parse} 로 먼저 본다(screen/admin/system/batch/schedule/requirements.md)</td></tr>
 *   <tr><td><b>{@code BEAN_ID} 가 실재하는가</b></td>
 * <td><b>서버만</b> — 목록의 출처가 스프링 컨텍스트다(screen/admin/system/batch/schedule/requirements.md)</td></tr>
 *   <tr><td><b>쿨다운 × 크론</b></td>
 * <td><b>서버만</b> — 크론 파서가 프론트 의존성에 없다(screen/admin/system/batch/schedule/requirements.md · frontend/grid.md)</td></tr>
 * </table>
 *
 * <h2>삭제 게이트가 <b>자식이 아니라 상태</b>다</h2>
 *
 * <p>frontend/grid.md 의 게이트가 여기서는 다른 모양으로 나타난다 — {@code ROLE_MGMT} 는 자식
 * 건수를 셌고 여기는 <b>켜져 있는가</b>와 <b>도는가</b>를 본다(screen/admin/system/batch/schedule/requirements.md).
 *
 * <p><b>실행 이력은 게이트가 아니다.</b> 이력에 FK 가 없으므로(sys/batch.md) 등록을 지워도
 * 행은 남으며 남아야 한다 — sys/logs.md 의 <i>"존재하지 않는 주체의 기록도 남아야 한다"</i>
 * 와 같은 규칙이고, 게이트로 삼으면 <b>오래된 배치는 영영 못 지운다</b>.
 */
@Service
public class BatchScheduleService {

    /**
     * <b>주입받지 않는다.</b> {@code PARAM_JSON} 의 <i>구문만</i> 보는 자리라
     * 스프링이 설정한 인스턴스(모듈 · 직렬화 옵션)가 필요하지 않다 — 읽고 버릴 뿐
     * 응답에 쓰지 않으므로 설정이 갈릴 여지도 없다.
     *
     * <p>주입으로 두었더니 <b>기동이 실패했다</b>(2026-08-30 실측) —
     * {@code ObjectMapper} 빈을 만드는 것은 {@code JacksonAutoConfiguration} 이고
     * 그것이 도는지는 이 애플리케이션의 자동설정 구성에 달려 있다. <b>의존성이 있는
     * 것과 빈이 컨텍스트에 있는 것은 다르다.</b>
     *
     * <p>{@code ObjectMapper} 는 <b>스레드 안전</b>하므로(설정을 바꾸지 않는 한)
     * {@code static} 하나로 충분하다.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SysScheduleBatchRepository batches;
    private final SysBatchHistoryRepository histories;
    private final BatchJobRegistry registry;
    private final ApplicationEventPublisher events;
    private final BatchRowLock rowLocks;

    public BatchScheduleService(
            SysScheduleBatchRepository batches,
            SysBatchHistoryRepository histories,
            BatchJobRegistry registry,
            ApplicationEventPublisher events, BatchRowLock rowLocks) {
        this.batches = batches;
        this.histories = histories;
        this.registry = registry;
        this.events = events;
        this.rowLocks = rowLocks;
    }

    /**
     * 일괄 저장 — frontend/grid.md. <b>한 트랜잭션이다</b>(UX-14).
     *
     * <p>커밋 뒤에 {@link BatchScheduleChanged} 가 나가고 스케줄러가 전건을 다시 건다.
     * <b>여기서 스케줄러를 직접 부르지 않는다</b> — 롤백된 저장으로 크론을 걸면 없던
     * 등록이 걸린다.
     */
    @Transactional
    public void save(List<BatchScheduleSaveRow> rows) {
        /*
          <b>전건을 먼저 검증하고 그다음에 쓴다.</b> 섞으면 <i>거부되는 요청이 쓰기를 한 번
          내고</i> 돌아간다 — 트랜잭션이 롤백하므로 데이터는 남지 않지만 <b>SQL 로그에는
          남고</b>, 그것을 읽는 사람은 *검증에 걸린 저장이 왜 INSERT 를 했나* 를 묻게 된다.
          시퀀스를 쓰는 테이블이었다면 번호도 뛴다.

          <b>중복 검사도 이 패스에 있다.</b> 같은 요청 안의 중복은 화면이 막지만, 막혔다고
          서버가 안 봐도 되는 것은 아니다 — 두 번째 {@code C} 의 존재 확인이 첫 번째의
          flush 를 만나지 못해 <b>조용히 덮인다</b>(RoleService 와 같은 자리다).
        */
        Set<String> seen = new HashSet<>();
        RowTx.oneTxForAll(rows, row -> {
            if (!seen.add(row.batchId())) {
                throw duplicate(row.batchId());
            }
            if (row.rowState() != RowState.D) {
                validate(row);
            }
        });

        rows.stream().filter(row -> row.rowState() != RowState.C).map(BatchScheduleSaveRow::batchId)
                .distinct().sorted().forEach(rowLocks::schedule);
        RowTx.oneTxForAll(rows, row -> {
            switch (row.rowState()) {
                case C -> create(row);
                case U -> update(row);
                case D -> delete(row);
            }
        });
        events.publishEvent(new BatchScheduleChanged());
    }

    /** <b>검증은 위에서 이미 끝났다</b> — 여기서 다시 부르지 않는다. */
    private void create(BatchScheduleSaveRow row) {
        if (batches.existsById(row.batchId())) {
            throw duplicate(row.batchId());
        }
        batches.save(SysScheduleBatch.created(
                row.batchId(),
                row.batchNm(),
                row.batchDesc(),
                row.beanId(),
                row.cron(),
                row.timezone(),
                row.paramJson(),
                row.lockExpirySec(),
                row.lockMinIntervalSec(),
                row.useYnOrTrue(),
                row.sortNoOrZero()));
    }

    private void update(BatchScheduleSaveRow row) {
        require(row.batchId())
                .apply(
                        row.batchNm(),
                        row.batchDesc(),
                        row.beanId(),
                        row.cron(),
                        row.timezone(),
                        row.paramJson(),
                        row.lockExpirySec(),
                        row.lockMinIntervalSec(),
                        row.useYnOrTrue(),
                        row.sortNoOrZero());
    }

    /**
     * 삭제 — <b>물리 삭제다</b>(screen/admin/system/batch/schedule/requirements.md). {@code SYS_USER} 와 갈리는 자리다.
     *
     * <p>지울 수 있는 것은 <b>꺼져 있고 돌지 않는</b> 등록뿐이다. 도는 배치를 지우면
     * <i>다음 주기가 조용히 사라지고</i>, 실행 중인 것을 지우면 <b>락이 잡힌 채 등록이
     * 사라진다</b> — 그 락은 만료까지 아무도 못 잡는데 만료 시간이 방금 지워진 행에 있었다.
     */
    private void delete(BatchScheduleSaveRow row) {
        SysScheduleBatch batch = require(row.batchId());

        if (Boolean.TRUE.equals(batch.getUseYn())) {
            throw new BusinessException(
                    BatchScheduleErrorCode.NOT_DELETABLE,
                    "켜져 있는 배치다 — " + row.batchId(),
                    List.of(new BusinessException.FieldError(
                            "useYn", "먼저 사용을 끄고 삭제하세요")));
        }
        if (!histories.findRunningBatchIds(List.of(row.batchId())).isEmpty()) {
            throw new BusinessException(
                    BatchScheduleErrorCode.NOT_DELETABLE,
                    "실행 중인 배치다 — " + row.batchId(),
                    List.of(new BusinessException.FieldError(
                            "batchId", "실행이 끝난 뒤에 삭제하세요")));
        }
        batches.delete(batch);
    }

    // ── 검증 (screen/admin/system/batch/schedule/requirements.md) ───────────────────────────────────────

    private void validate(BatchScheduleSaveRow row) {
        requireText(row.batchNm(), BatchScheduleErrorCode.NAME_REQUIRED, "batchNm", "배치명은 필수입니다");
        requireText(
                row.batchDesc(),
                BatchScheduleErrorCode.DESC_REQUIRED,
                "batchDesc",
                "설명은 필수입니다 — 끄면 무엇이 멈추는지 적습니다");

        if (!registry.exists(row.beanId())) {
            throw new BusinessException(
                    BatchScheduleErrorCode.BEAN_UNKNOWN,
                    "등록된 BatchJob 구현이 아니다 — " + row.beanId(),
                    List.of(new BusinessException.FieldError(
                            "beanId", "등록된 배치 구현이 아닙니다")));
        }
        if (!CronRules.isValid(row.cron())) {
            throw new BusinessException(
                    BatchScheduleErrorCode.CRON_INVALID,
                    "크론을 파서가 읽지 못했다 — " + row.cron(),
                    List.of(new BusinessException.FieldError(
                            "cron", "크론 표현식이 올바르지 않습니다")));
        }
        /*
          화면은 셀렉트라 이 값이 만들어지지 않는다 — **API 를 직접 부르는 경로**의 마지막
          방어다(요건서 §6-1 의 13). 고정 오프셋(`+09:00`)도 거부한다: 파싱은 되지만
          서머타임이 없어 *현지 시각을 유지한다* 는 이 컬럼의 목적을 조용히 깬다.
        */
        if (!Timezones.isKnown(row.timezone())) {
            throw new BusinessException(
                    BatchScheduleErrorCode.TIMEZONE_INVALID,
                    "아는 시간대 이름이 아니다 — " + row.timezone(),
                    List.of(new BusinessException.FieldError(
                            "timezone", "시간대가 올바르지 않습니다")));
        }
        requireJson(row);
        requireCooldownShorterThanCron(row);
    }

    /**
     * 빈 문자열은 {@code NULL} 이다 — <b>검증하지 않는다</b>(sys/batch.md).
     *
     * <p>파라미터 없는 배치가 정상이며, 엔티티가 빈 문자열을 {@code NULL} 로 바꿔 넣는다
     * (Oracle 에서 둘이 같다 — backend/persistence.md).
     */
    private void requireJson(BatchScheduleSaveRow row) {
        String paramJson = row.paramJson();
        if (paramJson == null || paramJson.isBlank()) {
            return;
        }
        try {
            JSON.readTree(paramJson);
        } catch (Exception e) {
            throw new BusinessException(
                    BatchScheduleErrorCode.PARAM_JSON_INVALID,
                    "PARAM_JSON 이 JSON 이 아니다 — " + row.batchId(),
                    List.of(new BusinessException.FieldError(
                            "paramJson", "올바른 JSON 이 아닙니다")));
        }
    }

    /**
     * <b>쿨다운 ≥ 크론 최소 간격이면 거부</b> — backend/persistence.md · screen/admin/system/batch/schedule/requirements.md.
     *
     * <p><b>같은 값도 거부한다.</b> 경계에서 한 주기가 통째로 누락될 수 있고, 그 누락이
     * 조용하다(실행이 건너뛰어지는 것을 아무도 안 본다). 그 조용함이 backend/persistence.md 이 이 검증을
     * <i>등록 시점</i>에 요구한 이유 전부다.
     *
     * <p><b>오류를 두 셀에 건다.</b> 어느 쪽이 틀렸는지 서버가 말할 수 없다 — 틀린 것은
     * 값이 아니라 <b>관계</b>다(frontend/grid.md 이 셀 단위 표시를 요구하므로 둘 다 표시하고
     * 문구가 관계를 말한다).
     *
     * <p><b>최소 간격을 못 재면 건너뛴다.</b> 표본에서 간격이 하나도 안 나오는 크론
     * (연 1회 등)이 그렇고, <i>모르는 것</i>을 <i>짧다</i>고 읽으면 정상 크론이 저장되지
     * 않는다 — 검증이 하는 일은 사고를 막는 것이지 의심스러운 것을 다 막는 것이 아니다.
     */
    private void requireCooldownShorterThanCron(BatchScheduleSaveRow row) {
        int cooldown = row.lockMinIntervalSec() == null ? 0 : row.lockMinIntervalSec();
        if (cooldown <= 0) {
            return;
        }
        if (!CronRules.cooldownSwallowsCycles(row.cron(), cooldown)) {
            return;
        }
        // 문구가 두 값을 함께 읽어 줘야 하므로 간격을 다시 얻는다 — 위 판정이 참이면 있다.
        Optional<Long> minInterval = CronRules.minIntervalSeconds(row.cron());
        String message = "쿨다운("
                + cooldown
                + "초)이 크론 최소 간격("
                + minInterval.get()
                + "초)보다 깁니다. 실행이 건너뛰어집니다.";
        throw new BusinessException(
                BatchScheduleErrorCode.COOLDOWN_TOO_LONG,
                "쿨다운이 크론 주기 이상이다 — " + row.batchId(),
                List.of(
                        new BusinessException.FieldError("cron", message),
                        new BusinessException.FieldError("lockMinIntervalSec", message)));
    }

    private void requireText(String value, BatchScheduleErrorCode code, String field, String text) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    code,
                    field + " 가 비었다",
                    List.of(new BusinessException.FieldError(field, text)));
        }
    }

    private SysScheduleBatch require(String batchId) {
        return rowLocks.schedule(batchId)
                .orElseThrow(() -> new BusinessException(
                        BatchScheduleErrorCode.NOT_FOUND, "없는 배치다 — " + batchId));
    }

    private BusinessException duplicate(String batchId) {
        return new BusinessException(
                BatchScheduleErrorCode.DUPLICATE_ID,
                "이미 있는 배치 ID 다 — " + batchId,
                List.of(new BusinessException.FieldError(
                        "batchId", "이미 있는 배치 ID 입니다", batchId)));
    }
}
