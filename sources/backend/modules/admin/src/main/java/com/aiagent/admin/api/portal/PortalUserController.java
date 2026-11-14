// @feat FEAT-005
package com.aiagent.admin.api.portal;

import com.aiagent.admin.managed.*;
import com.aiagent.admin.portal.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.api.authz.ApiActionType;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/service/portalUser")
public class PortalUserController {
    public record Target(String targetId,String scope,String effectiveMonth) {
        public Target {if(scope==null||!Set.of("defaults","profile","memberships","grades","extraTokens","identitySystem","identityDataSource").contains(scope))throw ManagedErrorCode.VALIDATION.failure();}
    }
    private final PortalUserQueryService queries;
    private final ManagedCommandService commands;
    private final PortalSecretDelivery secrets;
    public PortalUserController(PortalUserQueryService queries,ManagedCommandService commands,PortalSecretDelivery secrets){this.queries=queries;this.commands=commands;this.secrets=secrets;}
    @PostMapping("/getList") @Operation(summary="Portal 이용자 목록 조회")
    public ApiResponse<PageResponse<PortalUserQueryService.Row>> getList(@Valid @RequestBody SearchRequest<Void> request){return ok(queries.getList(request));}
    @PostMapping("/getOne") @Operation(summary="Portal 이용자 영역 조회")
    public ApiResponse<PortalUserQueryService.Detail> getOne(@RequestBody Target request){return ok(queries.getOne(request.targetId(),request.scope(),request.effectiveMonth()));}
    @PostMapping("/getIdentityTargets") @Operation(summary="Portal 기술 신원 후보 조회")
    public ApiResponse<PortalUserQueryService.Detail> getIdentityTargets(@RequestBody Target request){if(!Set.of("identitySystem","identityDataSource").contains(request.scope()))throw ManagedErrorCode.VALIDATION.failure();return getOne(request);}
    @PostMapping("/save") @Operation(summary="Portal 이용자 영역 저장")
    public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand request){if(!Set.of("profile","memberships","grades","identitySystem","identityDataSource").contains(request.scope()))throw ManagedErrorCode.VALIDATION.failure();return ok(commands.save("API-084",request));}
    @PostMapping("/resetCredential") @Operation(summary="Portal 임시 비밀번호 단회 발급") @ApiActionType(ActionType.SAVE)
    public ApiResponse<PortalSecretDelivery.Result> resetCredential(@RequestBody ManagedCommand request,HttpServletResponse response){if(!"resetCredential".equals(request.scope()))throw ManagedErrorCode.VALIDATION.failure();response.setHeader("Cache-Control","no-store");return ok(secrets.capture(()->commands.save("API-104",request)));}
    @PostMapping("/unlockCredential") @Operation(summary="Portal 계정 잠금 해제") @ApiActionType(ActionType.SAVE)
    public ApiResponse<ManagedResult> unlockCredential(@RequestBody ManagedCommand request){if(!"unlockCredential".equals(request.scope()))throw ManagedErrorCode.VALIDATION.failure();return ok(commands.save("API-142",request));}
    private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.current());}
}
