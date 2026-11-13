// @feat FEAT-509
package com.aiagent.system.api.notice;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.notification.notice.NoticeBrief;
import com.aiagent.domain.notification.notice.NoticeDetail;
import com.aiagent.domain.notification.notice.NoticeFeedService;
import com.aiagent.domain.notification.notice.NoticePopup;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지를 <b>읽는</b> 세 경로 — 알림 모달의 공지 탭(screen/_standalone/README.md)과 로그인 팝업(screen/_standalone/README.md).
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). 공지는 전체 공개이므로(screen/admin/system/notice/requirements.md) 판정 축이
 * 없고, 실제로 <b>메뉴 밖 화면</b>이 부른다 — 팝업과 모달에는 {@code MENU_CD} 가
 * 없다(screen/_standalone/README.md). {@code NOTICE_MGMT} 메뉴의 {@link NoticeController} 와 <b>같은 테이블을
 * 다른 계층에서</b> 여는 자리이며, 그 둘을 가르는 것은 <b>보이는 범위</b>다:
 * 저쪽은 전건, 여기는 게시중인 것뿐이다.
 *
 * <p><b>조회조건 봉투를 쓰지 않는다</b>(backend/api.md — 동적 조회가 아니다). 조건이 코드에
 * 박혀 있는 것이 보안 경계이며, 클라이언트가 조건을 주면 <i>즉시 내림</i>(screen/admin/system/notice/requirements.md)이
 * 화면에서만 내려간 것이 된다.
 *
 * <p><b>읽음을 기록하지 않는다</b>(screen/admin/system/notice/requirements.md). 공지에는 읽음 상태가 없으므로 {@code GET} 이
 * 부수 효과를 갖지 않는다 — 같은 모달의 <i>내 알림</i> 탭과 갈리는 자리다.
 */
@RestController
public class NoticeFeedController {

    private final NoticeFeedService notices;

    public NoticeFeedController(NoticeFeedService notices) {
        this.notices = notices;
    }

    /**
     * 공지 탭의 목록.
     *
     * @param includeExpired {@code NI-04} 의 <i>"지난 공지 포함"</i> 체크박스. 기본은
     * 꺼져 있고, 켜도 <b>내린 공지는 오지 않는다</b>(screen/admin/system/notice/requirements.md)
     */
    @GetMapping("/api/system/common/notice/getList")
    public ApiResponse<List<NoticeBrief>> getList(
            @RequestParam(defaultValue = "false") boolean includeExpired) {

        return ApiResponse.success(notices.findVisible(includeExpired), TraceId.current());
    }

    /**
     * 고른 공지 하나 — 본문이 여기서 온다.
     *
     * <p>목록이 본문을 싣지 않으므로(NoticeBrief) 상세를 열 때 한 번 더 묻는다. 왕복이
     * 하나 느는 대신 <b>안 볼 것을 안 싣는다</b>.
     */
    @GetMapping("/api/system/common/notice/getOne")
    public ApiResponse<NoticeDetail> getOne(
            @RequestParam Long noticeId,
            @RequestParam(defaultValue = "false") boolean includeExpired) {

        return ApiResponse.success(notices.findOne(noticeId, includeExpired), TraceId.current());
    }

    /**
     * 로그인 뒤 띄울 것 — screen/admin/system/notice/requirements.md C안.
     *
     * <p><b>실패해도 셸은 동작한다</b>({@code NP-02}). 셸 부팅을 막는 것은 메시지 번들
     * 하나뿐이고 공지는 <b>셸과 병렬로</b> 받아 나중에 띄운다 — 부르는 쪽이
     * 기다리지 않는다.
     *
     * <p><b>비어 있는 것이 정상</b>이다: 팝업 대상이 없는 날이 대부분이며 그때
     * {@code notice} 가 {@code null} 이다.
     */
    @GetMapping("/api/system/common/notice/getPopup")
    public ApiResponse<NoticePopup> getPopup() {
        return ApiResponse.success(notices.findPopup(), TraceId.current());
    }
}
