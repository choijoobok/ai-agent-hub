// @feat FEAT-006
package com.aiagent.admin.api.group;

import com.aiagent.admin.group.*;
import com.aiagent.admin.managed.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.api.authz.ApiActionType;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/service/userGroup")
public class UserGroupController {
    public record Target(String targetId) {}
    public record Detail(String targetId,UserGroupLinkSource.Source scope) {}
    private final UserGroupQueryService queries;
    private final ManagedCommandService commands;
    public UserGroupController(UserGroupQueryService queries,ManagedCommandService commands){this.queries=queries;this.commands=commands;}
    @PostMapping("/getList") @Operation(summary="사용자 그룹 목록 조회")
    public ApiResponse<PageResponse<UserGroupQueryService.Row>> getList(@Valid @RequestBody SearchRequest<Void> request) {return ok(queries.getList(request));}
    @PostMapping("/getOne") @Operation(summary="사용자 그룹 정의 조회")
    public ApiResponse<UserGroupQueryService.Row> getOne(@RequestBody Target request) {return ok(queries.getOne(request.targetId()));}
    @PostMapping("/getMembers") @Operation(summary="사용자 그룹 소속 연결 조회")
    public ApiResponse<UserGroupLinkSource.Projection> getMembers(@RequestBody Target request) {return ok(queries.projection(request.targetId(),UserGroupLinkSource.Source.membership));}
    @PostMapping("/getAssets") @Operation(summary="사용자 그룹 자산 역조회")
    public ApiResponse<UserGroupLinkSource.Projection> getAssets(@RequestBody Detail request) {
        if(request.scope()==null||request.scope()==UserGroupLinkSource.Source.membership||request.scope()==UserGroupLinkSource.Source.datasource)throw ManagedErrorCode.VALIDATION.failure();
        return ok(queries.projection(request.targetId(),request.scope()));
    }
    @PostMapping("/save") @Operation(summary="사용자 그룹 정의 저장")
    public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand request) {return ok(commands.save("API-085",request));}
    @PostMapping("/remove") @Operation(summary="사용자 그룹 정의 삭제")
    @ApiActionType(ActionType.SAVE)
    public ApiResponse<ManagedResult> remove(@RequestBody ManagedCommand request) {return ok(commands.save("API-109",request));}
    private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.current());}
}
