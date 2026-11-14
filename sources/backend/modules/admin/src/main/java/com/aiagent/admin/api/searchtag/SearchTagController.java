// @feat FEAT-024
package com.aiagent.admin.api.searchtag;
import com.aiagent.admin.searchtag.*;
import com.aiagent.admin.managed.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.api.authz.ApiActionType;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/service/searchTag")
public class SearchTagController {
    public record Target(String targetId) {}
    private final SearchTagQueryService queries;
    private final SearchTagService owner;
    private final ManagedCommandService commands;
    public SearchTagController(SearchTagQueryService queries,SearchTagService owner,ManagedCommandService commands){this.queries=queries;this.owner=owner;this.commands=commands;}
    @PostMapping("/getList") @Operation(summary="검색 태그 목록 조회")
    public ApiResponse<PageResponse<SearchTagQueryService.Row>> getList(@Valid @RequestBody SearchRequest<Void> request){return ok(queries.getList(request));}
    @PostMapping("/getOne") @Operation(summary="검색 태그 전체 정의 조회")
    public ApiResponse<SearchTagQueryService.Detail> getOne(@RequestBody Target request){return ok(queries.getOne(request.targetId()));}
    @PostMapping("/getReferences") @Operation(summary="검색 태그 연결 지식 조회")
    public ApiResponse<SearchTagQueryService.References> getReferences(@RequestBody Target request){return ok(queries.getReferences(request.targetId()));}
    @PostMapping("/save") @Operation(summary="검색 태그 전체 정의 저장")
    public ApiResponse<SearchTagService.Saved> save(@RequestBody ManagedCommand request){return ok(owner.result(commands.save("API-112",request),request));}
    @PostMapping("/remove") @Operation(summary="검색 태그 삭제") @ApiActionType(ActionType.SAVE)
    public ApiResponse<ManagedResult> remove(@RequestBody ManagedCommand request){return ok(commands.save("API-113",request));}
    private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.current());}
}
