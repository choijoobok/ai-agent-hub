// @feat FEAT-509
package com.aiagent.system.api.notification;

import com.aiagent.system.api.notification.request.NotiReadRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.notification.inbox.MyNotiService;
import com.aiagent.domain.notification.inbox.MyNotifications;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <i>내 알림</i> — frontend/tabs.md · screen/_standalone/README.md.
 *
 * <p><b>{@code common} 계층이다</b>(backend/security.md). 알림 모달에는 {@code MENU_CD} 가 없으므로
 * (screen/_standalone/README.md) {@code (메뉴, 액션)} 판정을 걸 자리가 없고, 그 대신 <b>범위가 인가</b>다 —
 * 넷 다 대상이 세션의 주인이며 {@code userId} 를 <b>본문으로 받지 않는다</b>.
 *
 * <p><b>{@code FavoriteController} 처럼 목록을 되돌린다.</b> 읽음으로 바꾼 뒤 화면이 다시
 * 조회하지 않아도 되고, 다른 탭에서 생긴 변화가 그 자리에서 보인다 — 멀티탭 셸이다
 * ({@link com.aiagent.system.api.favorite.FavoriteController} · frontend/tabs.md).
 */
@RestController
public class NotiInboxController {

    private final MyNotiService notifications;

    public NotiInboxController(MyNotiService notifications) {
        this.notifications = notifications;
    }

    /**
     * 목록 · 미읽음 · 전건.
     *
     * <p><b>여는 것만으로 읽음이 되지 않는다</b>(frontend/tabs.md) — {@code GET} 이고 서비스가
     * {@code readOnly} 다. 훑어만 봐도 배지가 사라지면 놓친 알림을 찾을 수 없다.
     */
    @GetMapping("/api/system/common/noti/getList")
    public ApiResponse<MyNotifications> getList(Principal principal) {
        return ApiResponse.success(notifications.findMine(userId(principal)), TraceId.current());
    }

    /**
     * 배지 하나 — frontend/tabs.md.
     *
     * <p>모달을 열지 않은 채로도 벨이 숫자를 보여야 한다. 목록을 통째로 받아 세지 않는
     * 이유는 <b>상한이 있기 때문</b>이다 — 목록은 잘려도 배지는 전건이다.
     */
    @GetMapping("/api/system/common/noti/getUnreadCount")
    public ApiResponse<Long> getUnreadCount(Principal principal) {
        return ApiResponse.success(notifications.countUnread(userId(principal)), TraceId.current());
    }

    /** 이 항목을 읽었다 — <b>그 항목만</b>이다(frontend/tabs.md). */
    @PostMapping("/api/system/common/noti/saveRead")
    public ApiResponse<MyNotifications> markRead(
            @Valid @RequestBody NotiReadRequest request, Principal principal) {

        String userId = userId(principal);
        notifications.markRead(userId, request.notiId());
        return ApiResponse.success(notifications.findMine(userId), TraceId.current());
    }

    /**
     * 모두 읽음 — frontend/tabs.md 가 <b>두기로 한</b> 명시적 경로다.
     *
     * <p>본문이 없다. 대상은 언제나 <i>내 안 읽은 것 전부</i> 이며, 고를 것이 있으면
     * 그것은 목록에서 하나씩 여는 일이다.
     */
    @PostMapping("/api/system/common/noti/saveAllRead")
    public ApiResponse<MyNotifications> markAllRead(Principal principal) {
        String userId = userId(principal);
        notifications.markAllRead(userId);
        return ApiResponse.success(notifications.findMine(userId), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
