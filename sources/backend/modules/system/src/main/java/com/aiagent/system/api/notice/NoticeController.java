// @feat FEAT-509
package com.aiagent.system.api.notice;

import com.aiagent.system.api.notice.request.NoticeSaveRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.notification.notice.NoticeService;
import com.aiagent.domain.notification.notice.NoticeQueryService;
import com.aiagent.domain.notification.notice.NoticeRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code NOTICE_MGMT} — screen/admin/system/notice/requirements.md. <b>경로가 둘이고 액션이 둘이다.</b>
 *
 * <table><caption></caption>
 *   <tr><td>{@code getList}</td><td>{@code READ}</td><td>마스터 목록 — §4.2</td></tr>
 *   <tr><td>{@code save}</td><td>{@code SAVE}</td><td>추가 · 수정 · <b>물리 삭제</b> — §4.6</td></tr>
 * </table>
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md). 선언에 또 적지 않는다.
 *
 * <h2>{@code EXPORT} 에 경로가 없다 — 그리고 화면에도 없다</h2>
 *
 * <p>sys/menu.md 의 액션 열이 {@code R S} 이고, frontend/grid.md 의 <i>"{@code E} 가 없으면 ②도
 * 감춘다"</i> 가 {@code BATCH_SCHEDULE} 에 이어 <b>두 번째로</b> 쓰인다.
 *
 * <p><b>근거가 저쪽과 다르다</b>(screen/admin/system/notice/requirements.md) — 배치는 <i>원본이 화면</i> 이었고 여기는
 * <b>본문이 {@code nclob} 이라 엑셀 셀 상한에서 잘린다</b>(frontend/grid.md). 잘린 공지 파일은
 * 원본도 참고 자료도 아니다.
 *
 * <h2>표시 측은 여기 없다</h2>
 *
 * <p>공지 팝업과 알림 모달의 공지 탭은 <b>전 사용자에게 열리는 화면</b>이라
 * {@code /api/common/**} 이며 {@code SYS_API} 스캔 대상이 아니다(backend/api.md · screen/_standalone/README.md).
 * sys/menu.md 이 NOTI-01 을 <i>관리는 권한이 걸리는 화면, 팝업은 전 사용자에게 뜨는 모달</i>
 * 로 쪼갠 것이 여기서 경로의 갈림으로 나타난다.
 */
@RestController
@RequestMapping("/api/system/service/notice")
public class NoticeController {

    private final NoticeQueryService queries;
    private final NoticeService admin;

    public NoticeController(NoticeQueryService queries, NoticeService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "공지사항 목록 조회",
            description = "공지를 고정 순서와 게시 시작 역순으로 반환한다. 게시 상태는 서버가 판정해 함께 싣는다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<NoticeRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    /**
     * 저장 — 추가 · 수정 · <b>물리 삭제</b>가 한 트랜잭션이다(screen/admin/system/notice/requirements.md).
     *
     * <p><b>팝업 기간이 게시 기간을 벗어나면 거부한다</b>. 이 화면 고유의 검증이며,
     * 없으면 <b>목록에 없는 공지가 팝업으로 뜬다</b> — 팝업의 <i>"공지 N건 더 있음"</i>
     * 링크가 빈 목록으로 간다.
     */
    @Operation(
            summary = "공지사항 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영한다. 게시 기간과 팝업 기간의 포함 관계를 검증한다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid NoticeSaveRequest request) {
        admin.save(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
