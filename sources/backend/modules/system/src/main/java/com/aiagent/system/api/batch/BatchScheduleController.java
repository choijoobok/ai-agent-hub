// @feat FEAT-508
package com.aiagent.system.api.batch;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.batch.history.BatchHistoryQueryService;
import com.aiagent.domain.batch.history.BatchIdParams;
import com.aiagent.domain.batch.history.BatchHistoryRow;
import com.aiagent.domain.batch.run.BatchRunner;
import com.aiagent.domain.batch.schedule.BatchScheduleService;
import com.aiagent.domain.batch.schedule.BatchScheduleQueryService;
import com.aiagent.domain.batch.schedule.BatchScheduleRow;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.support.time.Timezones;
import com.aiagent.system.api.batch.request.BatchRunRequest;
import com.aiagent.system.api.batch.request.BatchScheduleSaveRequest;
import com.aiagent.system.api.batch.response.BatchRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code BATCH_SCHEDULE} — screen/admin/system/batch/schedule/requirements.md. <b>경로가 넷이고 액션이 셋이다.</b>
 *
 * <table><caption></caption>
 *   <tr><td>{@code getList}</td><td>{@code READ}</td><td>마스터 목록</td></tr>
 *   <tr><td>{@code getBeanList}</td><td>{@code READ}</td><td>{@code BEAN_ID} 셀렉트 — §3.3</td></tr>
 *   <tr><td>{@code getRecentHistory}</td><td>{@code READ}</td><td>하단 pane — §3.9</td></tr>
 *   <tr><td>{@code save}</td><td>{@code SAVE}</td><td>추가 · 수정 · <b>물리 삭제</b> — §3.8</td></tr>
 *   <tr><td>{@code runNow}</td><td><b>{@code EXECUTE}</b></td><td>즉시 실행 — §3.7</td></tr>
 * </table>
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md) — {@code get*} 는 {@code READ},
 * {@code save*} 는 {@code SAVE}, <b>그 밖은 {@code EXECUTE}</b>. 선언에 또 적지 않는다.
 *
 * <h2>{@code EXPORT} 에 경로가 없다 — 그리고 화면에도 없다</h2>
 *
 * <p>sys/menu.md 의 액션 열이 {@code R S X} 이며 <b>엑셀 버튼(②)이 통째로 사라지는 첫
 * 화면</b>이다(screen/admin/system/batch/schedule/requirements.md · frontend/grid.md). {@code ROLE_MGMT} · {@code ROLE_AUTH_MGMT} 에 이어
 * 셋째이지만, 앞의 둘은 <i>액션 미정의</i> 였고 여기는 <b>일부러 뺐다</b> — 등록이 수십
 * 건이고 <b>원본이 화면</b>이며(시드가 아니다), {@code PARAM_JSON} 중괄호가 셀에 들어간
 * 파일은 읽기도 어렵다.
 *
 * <h2>{@code runNow} 가 {@code EXECUTE} 인 것에 예외가 없다</h2>
 *
 * <p>screen/admin/system/batch/schedule/requirements.md — screen/admin/system/user/requirements.md 의 기준(<i>"우리 테이블의 컬럼을 쓰는가"</i>)으로 봐도 그렇다.
 * 즉시 실행은 {@code SYS_SCHEDULE_BATCH} 를 <b>읽기만</b> 하고 쓰는 것은 이력이다.
 * <b>절차 발동이지 저장이 아니다.</b>
 *
 * <h2>{@code getRecentHistory} 가 이력 서비스를 부른다</h2>
 *
 * <p>경로는 이 화면의 것이고(screen/admin/system/batch/schedule/requirements.md) 계산은 {@code BatchHistoryQueryService} 의
 * 것이다 — screen/admin/system/batch/schedule/requirements.md 가 하단 pane 을 <i>"§4.2 와 같은 컬럼"</i> 으로 정했으므로 유령 판정도
 * 같아야 하고, 서비스를 나누면 <b>같은 사실을 두 곳이 계산</b>하게 된다.
 * {@code RoleController} 가 하단 pane 의 경로를 {@code roleAuth} 로 둔 것과 방향이
 * 반대인데(그쪽은 경로를 합쳤고 여기는 서비스를 합쳤다), 근거는 같다 — <b>같은 질문에
 * 답이 둘이면 안 된다</b>.
 */
@RestController
@RequestMapping("/api/system/service/batchSchedule")
public class BatchScheduleController {

    private final BatchScheduleQueryService queries;
    private final BatchScheduleService admin;
    private final BatchHistoryQueryService histories;
    private final BatchRunner runner;
    private final BusinessCalendar calendar;

    public BatchScheduleController(
            BatchScheduleQueryService queries,
            BatchScheduleService admin,
            BatchHistoryQueryService histories,
            BatchRunner runner,
            BusinessCalendar calendar) {
        this.queries = queries;
        this.admin = admin;
        this.histories = histories;
        this.runner = runner;
        this.calendar = calendar;
    }

    @Operation(
            summary = "배치 등록 목록 조회",
            description = "등록된 배치를 정렬 순서대로 반환한다. 다음 실행 시각과 최근 실행 결과가 함께 실린다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<BatchScheduleRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * {@code BEAN_ID} 셀렉트의 후보 — <b>스프링 컨텍스트가 원본</b>이다(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p><b>비어 있는 것이 정상이다.</b> 템플릿에는 {@code BatchJob} 구현이 하나도 없으며,
     * 그 비어 있음은 고장이 아니라 <i>아직 배치가 없다</i> 는 사실이다.
     */
    @Operation(
            summary = "실행 빈 목록 조회",
            description = "스프링 컨텍스트에 등록된 BatchJob 구현의 빈 이름을 반환한다. 사용 프로젝트가 구현을 넣기 전에는 비어 있다.")
    @PostMapping("/getBeanList")
    public ApiResponse<List<String>> getBeanList() {
        return ApiResponse.success(queries.getBeanIds(), TraceId.current());
    }

    /**
     * {@code TIMEZONE} 셀렉트의 후보 — <b>tzdb 가 원본</b>이다(screen/admin/system/batch/schedule/requirements.md §4-1).
     *
     * <p><b>후보를 좁히지 않는다.</b> 자주 쓰는 것만 골라 두면 고객사가 바뀔 때마다 소스를
     * 고쳐야 한다 — 600개는 화면의 검색이 감당한다.
     *
     * <p><b>맨 앞이 업무 시간대다.</b> 그 값이 신규 행의 기본값이라 가장 자주 고르며,
     * 목록 안 제자리에도 남아 있다(알파벳 순으로 찾는 사람이 그 자리에서 찾는다).
     */
    @Operation(
            summary = "시간대 후보 조회",
            description = "크론의 기준 시간대로 고를 수 있는 IANA 시간대 이름을 반환한다. 맨 앞이 서버의 업무 시간대이고 나머지는 오름차순이다.")
    @PostMapping("/getTimezoneList")
    public ApiResponse<List<String>> getTimezoneList() {
        return ApiResponse.success(Timezones.candidates(calendar.zone()), TraceId.current());
    }

    /**
     * 하단 pane — <b>이 배치의</b> 최근 실행(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p>배치를 <b>조건이 아니라 파라미터로</b> 받는다({@code SearchRequest<BatchIdParams>}) —
     * 조건 목록에 담으면 사용자가 지울 수 있고, 지우면 이 pane 이 <b>전 배치의 최근
     * 실행</b>을 낸다(backend/api.md). {@code roleAuth/getAffectedUsers} 와 같은 모양이다.
     *
     * <p>건수는 <b>화면이 정한다</b> — pane 이 10건을 요청한다. 전량을 묻는 질문에는
     * {@code BATCH_HISTORY} 가 답한다.
     */
    @Operation(
            summary = "이 배치의 최근 실행 이력",
            description = "한 배치의 최근 실행을 최신 순으로 반환한다. 화면이 10건을 요청하며 전량은 실행 이력 화면이 답한다.")
    @PostMapping("/getRecentHistory")
    public ApiResponse<PageResponse<BatchHistoryRow>> getRecentHistory(
            @RequestBody @Valid SearchRequest<BatchIdParams> request) {
        return ApiResponse.success(histories.getRecent(request), TraceId.current());
    }

    /**
     * 저장 — 추가 · 수정 · <b>물리 삭제</b>가 한 트랜잭션이다.
     *
     * <p><b>커밋 뒤에 스케줄러가 크론을 다시 건다.</b> 그 순서는 도메인이 이벤트로
     * 표현하며({@code BatchScheduleChanged}) 여기서 부르지 않는다 —
     * {@code RoleController} 가 세션 무효화를 <b>컨트롤러에서</b> 한 것과 갈리는
     * 지점이고, 근거는 <i>세션은 도메인이 모르지만 스케줄은 도메인이 안다</i> 이다.
     */
    @Operation(
            summary = "배치 등록 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영한다. 크론과 쿨다운의 관계를 검증하고, 커밋 뒤에 스케줄러가 크론을 다시 건다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid BatchScheduleSaveRequest request) {
        admin.save(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    /**
     * 즉시 실행 — <b>던지고 끝낸다</b>(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p>진행률 모달이 뜨지 않는다. 진행 상황을 보여줄 자리는 이미 있다 — 실행 이력의
     * {@code RUNNING} 행이며, 화면은 응답을 받고 <b>하단 pane 을 재조회</b>한다.
     *
     * <p><b>거절을 예외로 던지지 않는다.</b> 응답이 건별이라(frontend/grid.md) 첫 실패에서 던지면
     * 나머지가 시작조차 못 한다 — {@link BatchRunResponse} 가 그 결정의 실물이다.
     *
     * <p><b>없는 배치는 예외다.</b> 그쪽은 건별 사정이 아니라 <i>요청이 틀린 것</i>이며,
     * 화면이 낡았거나 화면 밖 경로다.
     */
    @Operation(
            summary = "배치 즉시 실행",
            description = "선택한 배치를 지금 실행한다. 쿨다운을 우회하며 이미 실행 중이면 그 건만 거절하고 노드와 시작 시각을 함께 반환한다.")
    @PostMapping("/runNow")
    public ApiResponse<BatchRunResponse> runNow(@RequestBody @Valid BatchRunRequest request) {
        List<BatchRunResponse.Item> items = new ArrayList<>(request.targets().size());
        List<BatchRunner.RunOutcome> outcomes = runner.runNow(request.targets());
        for (int index = 0; index < request.targets().size(); index++) {
            String batchId = request.targets().get(index);
            BatchRunner.RunOutcome outcome = outcomes.get(index);
            items.add(new BatchRunResponse.Item(
                    batchId,
                    outcome.started(),
                    outcome.runningNodeId(),
                    outcome.runningStartedAt()));
        }
        return ApiResponse.success(new BatchRunResponse(items), TraceId.current());
    }
}
