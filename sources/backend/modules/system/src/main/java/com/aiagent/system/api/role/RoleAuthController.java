// @feat FEAT-502
package com.aiagent.system.api.role;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.system.api.role.request.RoleAuthSaveRequest;
import com.aiagent.system.api.role.request.RoleMenuTreeRequest;
import com.aiagent.system.domain.role.RoleAuthService;
import com.aiagent.system.domain.role.RoleAuthTreeRow;
import com.aiagent.system.domain.role.RoleHolderRow;
import com.aiagent.system.domain.role.RoleIdParams;
import com.aiagent.system.domain.role.RolePickRow;
import com.aiagent.system.domain.role.RoleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할 권한 API — SCR-508. */
@RestController
@RequestMapping("/api/system/service/roleAuth")
public class RoleAuthController {

    private final RoleQueryService queries;
    private final RoleAuthService admin;
    private final RequestLocale locale;

    public RoleAuthController(
            RoleQueryService queries,
            RoleAuthService admin,
            RequestLocale locale) {
        this.queries = queries;
        this.admin = admin;
        this.locale = locale;
    }

    @Operation(
            summary = "역할 목록 조회",
            description = "권한을 부여할 역할을 고르는 목록이다. 비활성 역할도 함께 반환하며 보유 인원이 실린다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<RolePickRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getRoleList(request), TraceId.current());
    }

    @Operation(
            summary = "역할 권한 트리 조회",
            description = "메뉴 계층 전건과 이 역할의 부여 상태를 반환한다. 메뉴마다 정의된 액션과 부여된 액션이 함께 실린다.")
    @PostMapping("/getMenuTree")
    public ApiResponse<PageResponse<RoleAuthTreeRow>> getMenuTree(
            @RequestBody @Valid RoleMenuTreeRequest request) {
        return ApiResponse.success(
                queries.getMenuTree(request.roleId(), locale.current()), TraceId.current());
    }

    @Operation(
            summary = "역할 권한 저장",
            description = "이 역할의 권한을 보낸 집합으로 맞추고, 보유자 세션에 권한 재계산을 예약한다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(
            @RequestBody @Valid RoleAuthSaveRequest request,
            Principal principal) {
        Set<String> affected = admin.save(request.roleId(), request.grants());
        return ApiResponse.success(null, TraceId.current());
    }

    @Operation(
            summary = "역할 보유 사용자 조회",
            description = "이 역할을 가진 사용자를 페이지 단위로 반환한다. 부여 상태와 기간, 공집합 판정의 입력 둘이 함께 실린다.")
    @PostMapping("/getAffectedUsers")
    public ApiResponse<PageResponse<RoleHolderRow>> getAffectedUsers(
            @RequestBody @Valid SearchRequest<RoleIdParams> request) {
        return ApiResponse.success(queries.getAffectedUsers(request), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
