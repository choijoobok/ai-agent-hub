// @feat FEAT-501
package com.aiagent.system.api.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 강제 로그아웃의 대상 — AUTH-10 · screen/admin/system/user/requirements.md. <b>대상이 세션이지 사용자가 아니다.</b>
 *
 * <p>{@link UserTargetRequest} 를 쓰지 않는 이유가 그것이다. 저것의 대상은
 * {@code USER_ID} 이고 여기는 <b>세션 ID</b> 다 — 한 사람에게 세션이 여럿일 수 있고
 * (동시 접속 상한이 2 이상인 배포 — screen/admin/system/user/requirements.md), 그때 <i>어느 것을 끊나</i> 가 이 탭이
 * 하는 일 전부다.
 *
 * <p><b>{@code userId} 도 함께 받는다.</b> 세션 ID 만으로도 끊을 수는 있지만 그러면
 * <b>남의 세션 ID 를 알아낸 요청</b>이 무엇이든 끊을 수 있다 — 목록에 뜬 것만 끊게 하려면
 * 서버가 <i>이 세션이 그 사람의 것인가</i> 를 다시 봐야 하고, 그 확인의 입력이 이 값이다
 * (sys/menu.md 원칙 P1 — 화면이 본 것을 서버가 다시 본다).
 */
public record UserSessionKillRequest(
        @NotBlank @Size(max = 30) String userId,
        @NotEmpty @Size(max = 100) List<@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String> targets) {}
