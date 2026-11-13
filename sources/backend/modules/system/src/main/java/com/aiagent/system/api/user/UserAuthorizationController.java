// @feat FEAT-501
// @feat FEAT-502
package com.aiagent.system.api.user;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.system.api.user.request.UserRoleSaveRequest;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.user.detail.EffectiveAuthParams;
import com.aiagent.domain.user.detail.EffectiveAuthRow;
import com.aiagent.domain.user.detail.RoleCandidate;
import com.aiagent.domain.user.detail.UserDetailQueryService;
import com.aiagent.domain.user.detail.UserDetailService;
import com.aiagent.domain.user.detail.UserGrantRow;
import com.aiagent.domain.user.detail.UserIdParams;
import com.aiagent.domain.user.detail.UserRoleRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 관리 화면의 역할·개인 예외 조회·유효 권한 API — FEAT-502. */
@RestController
@RequestMapping("/api/system/service/user")
public class UserAuthorizationController {

    private final UserDetailQueryService queries;
    private final UserDetailService detail;
    private final RequestLocale locale;

    public UserAuthorizationController(
            UserDetailQueryService queries,
            UserDetailService detail,
            RequestLocale locale) {
        this.queries = queries;
        this.detail = detail;
        this.locale = locale;
    }

    @Operation(
            summary = "보유 역할 조회",
            description = "한 사용자의 역할 부여를 기간과 함께 반환한다. 만료된 부여와 꺼진 역할의 부여도 포함하며 역할 자체의 사용 여부가 함께 실린다.")
    @PostMapping("/getRoleList")
    public ApiResponse<PageResponse<UserRoleRow>> getRoleList(
            @RequestBody @Valid SearchRequest<UserIdParams> request) {
        return ApiResponse.success(queries.getRoleList(request), TraceId.current());
    }

    @Operation(
            summary = "보유 역할 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영하고, 그 사용자의 세션에 권한 재계산을 예약한다.")
    @PostMapping("/saveRole")
    public ApiResponse<Void> saveRole(
            @RequestBody @Valid UserRoleSaveRequest request,
            Principal principal) {
        Set<String> changed = detail.saveRoles(request.userId(), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    @Operation(
            summary = "역할 후보 조회",
            description = "부여할 수 있는 역할 전건을 이름 순으로 반환한다. 꺼진 역할도 포함하며 사용 여부가 함께 실린다.")
    @PostMapping("/getRoleCandidates")
    public ApiResponse<PageResponse<RoleCandidate>> getRoleCandidates() {
        return ApiResponse.success(queries.getRoleCandidates(), TraceId.current());
    }

    @Operation(
            summary = "개인 예외 조회",
            description = "한 사용자의 개인 예외를 메뉴 경로·기간·사유와 함께 반환한다. 조회 전용이며 부여는 개인 예외 권한 화면의 일이다.")
    @PostMapping("/getUserMenuAuth")
    public ApiResponse<PageResponse<UserGrantRow>> getUserMenuAuth(
            @RequestBody @Valid SearchRequest<UserIdParams> request) {
        return ApiResponse.success(
                queries.getUserMenuAuth(request, locale.current()), TraceId.current());
    }

    @Operation(
            summary = "유효 권한 조회",
            description = "역할과 개인 예외를 출처별로 편 목록을 반환한다. 메뉴 게이트와 지금 실제로 효력이 있는지가 행마다 실리며, 전체 메뉴 모드에서는 부여가 없는 메뉴도 나온다.")
    @PostMapping("/getEffectiveAuthList")
    public ApiResponse<PageResponse<EffectiveAuthRow>> getEffectiveAuthList(
            @RequestBody @Valid SearchRequest<EffectiveAuthParams> request) {
        return ApiResponse.success(
                queries.getEffectiveAuthList(request, locale.current()), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
