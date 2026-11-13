// @feat FEAT-502
package com.aiagent.system.api.role;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.system.api.role.request.RoleSaveRequest;
import com.aiagent.system.domain.role.RoleQueryService;
import com.aiagent.system.domain.role.RoleRow;
import com.aiagent.system.domain.role.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할 관리 API — SCR-507. */
@RestController
@RequestMapping("/api/system/service/role")
public class RoleController {

    private final RoleQueryService queries;
    private final RoleService admin;

    public RoleController(
            RoleQueryService queries,
            RoleService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @Operation(
            summary = "역할 목록 조회",
            description = "역할을 페이지 단위로 반환한다. 보유 인원·유효 인원·권한 건수가 함께 실리며 삭제 게이트가 그것을 읽는다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<RoleRow>> getList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getList(request), TraceId.current());
    }

    @Operation(
            summary = "역할 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영하고, 사용 여부가 바뀐 역할의 보유자 세션에 권한 재계산을 예약한다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(
            @RequestBody @Valid RoleSaveRequest request,
            Principal principal) {
        Set<String> affected = admin.save(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
