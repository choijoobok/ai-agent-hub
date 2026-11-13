// @feat FEAT-509
package com.aiagent.system.api.notirequest;

import com.aiagent.system.api.notirequest.request.MyNotiRequestCreateRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.notification.request.MyNotiRequestService;
import com.aiagent.domain.notification.request.MyRequests;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <i>내 요청</i> — 알림 모달의 셋째 탭(screen/_standalone/README.md · NOTI-06 사용자 측).
 *
 * <p><b>{@link NotiRequestController} 와 방향이 반대다.</b> 저쪽은
 * {@code /api/system/service/notiRequest/**} 이고 {@code (NOTI_REQUEST, SAVE)} 권한을 요구하지만,
 * 여기는 {@code common} 계층이라 <b>권한이 없는 사람도 부른다</b> — 실제로 <b>권한이
 * 없어서</b> 요청을 내는 것이 이 기능의 첫 용도다(권한 신청).
 *
 * <p>같은 패키지에 둔 것은 <b>같은 테이블의 두 얼굴</b>이기 때문이다. 나누면 요청의 규칙이
 * 두 폴더에 흩어지고, 그 둘이 어긋나는 것을 아무도 못 본다.
 */
@RestController
public class MyNotiRequestController {

    private final MyNotiRequestService requests;

    public MyNotiRequestController(MyNotiRequestService requests) {
        this.requests = requests;
    }

    /**
     * 내가 낸 것 — <b>최신이 위</b>다.
     *
     * <p>{@code NOTI_REQUEST} 메뉴의 대기열이 오름차순인 것과 반대이며 그것이 의도다
     * (screen/admin/system/notice/requirements.md) — 저쪽은 오래 기다린 것을 먼저 처리해야 하고, 여기는 <b>방금 낸 것이
     * 어떻게 됐는지</b> 가 물음이다.
     */
    @GetMapping("/api/system/common/notiRequest/getMyList")
    public ApiResponse<MyRequests> getMyList(Principal principal) {
        return ApiResponse.success(requests.findMine(userId(principal)), TraceId.current());
    }

    /**
     * 새 요청 — 목록을 되돌린다.
     *
     * <p>같은 유형이 <b>접수 상태로 열려 있으면 거부</b>한다. 화면도 버튼을 막지만 그것은
     * 마지막 방어가 아니다 — 더블클릭 한 번이 여벌을 만들고, 그 여벌은 관리자가 하나씩
     * 반려해야 하며 반려마다 알림이 나간다(screen/admin/system/notice/requirements.md).
     */
    @PostMapping("/api/system/common/notiRequest/save")
    public ApiResponse<MyRequests> create(
            @Valid @RequestBody MyNotiRequestCreateRequest request, Principal principal) {

        String userId = userId(principal);
        requests.create(userId, request.reqTypeCd(), request.reqContent());
        return ApiResponse.success(requests.findMine(userId), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
