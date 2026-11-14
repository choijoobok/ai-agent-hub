// @feat FEAT-042
package com.aiagent.admin.api.quota;

import com.aiagent.admin.quota.*;
import com.aiagent.admin.managed.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.api.authz.ApiActionType;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/service/quotaGrade")
public class QuotaGradeController {
    public record Target(String targetId,String beforeMonth) {}
    private final QuotaGradeQueryService queries;
    private final ManagedCommandService commands;
    public QuotaGradeController(QuotaGradeQueryService queries,ManagedCommandService commands){this.queries=queries;this.commands=commands;}
    @PostMapping("/getList") @Operation(summary="쿼터 등급 기준 목록 조회")
    public ApiResponse<PageResponse<QuotaGradeQueryService.Row>> getList(@Valid @RequestBody SearchRequest<Void> request){return ok(queries.getList(request));}
    @PostMapping("/getOne") @Operation(summary="쿼터 등급 전체 기준 조회")
    public ApiResponse<QuotaGradeQueryService.Detail> getOne(@RequestBody Target request){return ok(queries.getOne(request.targetId(),request.beforeMonth()));}
    @PostMapping("/save") @Operation(summary="시작월 쿼터 등급 전체 저장")
    public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand request){return ok(commands.save("API-083",request));}
    @PostMapping("/remove") @Operation(summary="미래 쿼터 등급 기준 삭제") @ApiActionType(ActionType.SAVE)
    public ApiResponse<ManagedResult> remove(@RequestBody ManagedCommand request){return ok(commands.save("API-143",request));}
    private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.current());}
}
