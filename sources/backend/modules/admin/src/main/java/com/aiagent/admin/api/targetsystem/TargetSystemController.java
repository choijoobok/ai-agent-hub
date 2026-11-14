// @feat FEAT-001
package com.aiagent.admin.api.targetsystem;
import com.aiagent.admin.targetsystem.*;
import com.aiagent.admin.managed.*;
import com.aiagent.core.search.*;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.api.trace.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/service/targetSystem")
public class TargetSystemController {
    public record Key(String id) {}
    private final TargetSystemQueryService queries;private final ManagedCommandService commands;private final TargetConnectionTestService tests;
    private final TargetCleanupService cleanup;private final TargetCommandQueryService results;private final TargetReferenceService references;
    public TargetSystemController(TargetSystemQueryService queries,ManagedCommandService commands,TargetConnectionTestService tests,TargetCleanupService cleanup,TargetCommandQueryService results,TargetReferenceService references){this.queries=queries;this.commands=commands;this.tests=tests;this.cleanup=cleanup;this.results=results;this.references=references;}
    @PostMapping("/getList") @Operation(summary="대상 시스템 목록 조회")
    public ApiResponse<PageResponse<TargetSystemQueryService.Row>> getList(@Valid @RequestBody SearchRequest<TargetSystemQueryService.Filter> request){return ok(queries.getList(request));}
    @PostMapping("/getOne") @Operation(summary="대상 시스템 전체 표시 정의 조회")
    public ApiResponse<TargetSystemQueryService.Row> getOne(@RequestBody Key request){return ok(queries.getOne(request.id()));}
    @PostMapping("/getConnections") @Operation(summary="대상 시스템 연결 정보 조회")
    public ApiResponse<PageResponse<TargetSystemQueryService.ConnectionRow>> getConnections(@Valid @RequestBody SearchRequest<TargetSystemQueryService.ConnectionFilter> request){return ok(queries.getConnections(request));}
    @PostMapping("/save") @Operation(summary="대상 시스템 표시 보완 저장")
    public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand request){return ok(commands.save("API-005",request));}
    @PostMapping("/testConnection") @Operation(summary="설정에 정의된 연결 시험 실행")
    public ApiResponse<TargetConnectionTestService.Result> testConnection(@RequestBody TargetConnectionTestService.Request request){return ok(tests.test(request));}
    @PostMapping("/getUsedApis") @Operation(summary="사용 API 연결 현황 조회")
    public ApiResponse<TargetReferenceService.Projection> getUsedApis(@RequestBody Key request){queries.getOne(request.id());return ok(references.read(request.id()));}
    @PostMapping("/getConfigurationCleanupPreview") @Operation(summary="설정 누락 시스템·연결 정리 미리보기")
    public ApiResponse<TargetCleanup.Preview> getConfigurationCleanupPreview(@RequestBody TargetCleanup.Query request){return ok(cleanup.preview(request));}
    @PostMapping("/deleteConfigurationMismatches") @Operation(summary="확인한 설정 누락 항목 일괄 정리")
    public ApiResponse<TargetCleanup.Result> deleteConfigurationMismatches(@RequestBody ManagedCommand request){return ok(cleanup.execute(request));}
    @PostMapping("/getCommandResult") @Operation(summary="대상 시스템 관리 명령 결과 확인")
    public ApiResponse<TargetCommandQueryService.Result> getCommandResult(@RequestBody TargetCommandQueryService.Key request){return ok(results.getResult(request));}
    private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.current());}
}
