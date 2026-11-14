// @feat FEAT-002
package com.aiagent.admin.api.datasource;
import com.aiagent.admin.datasource.*;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.ConfigurationCleanup;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import com.aiagent.support.time.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/service/dataSource")
public class DataSourceController {
    public record Key(String id){}public record GuideFilter(String dataSourceId){}
    private final DataSourceQueryService queries;private final DataSourceReferenceService refs;private final ManagedCommandService commands;private final DataSourceCommandQueryService results;private final DataSourceCleanupService cleanup;private final BusinessCalendar clock;
    public DataSourceController(DataSourceQueryService queries,DataSourceReferenceService refs,ManagedCommandService commands,DataSourceCommandQueryService results,DataSourceCleanupService cleanup,BusinessCalendar clock){this.queries=queries;this.refs=refs;this.commands=commands;this.results=results;this.cleanup=cleanup;this.clock=clock;}
    @PostMapping("/getList") @Operation(summary="데이터소스 목록 조회") public ApiResponse<PageResponse<DataSourceQueryService.Row>> getList(@Valid @RequestBody SearchRequest<DataSourceQueryService.Filter> r){return ok(queries.getList(r));}
    @PostMapping("/getOne") @Operation(summary="데이터소스 전체 메타·그룹·가이드 조회") public ApiResponse<DataSourceQueryService.Detail> getOne(@RequestBody Key k){return ok(queries.getOne(k.id()));}
    @PostMapping("/getConnectionCandidates") @Operation(summary="현재 데이터소스 정확 연결 진단") public ApiResponse<List<Map<String,Object>>> getConnectionCandidates(@Valid @RequestBody SearchRequest<DataSourceQueryService.Key> r){if(r.params()==null)throw ManagedErrorCode.VALIDATION.failure();return ok(queries.connection(r.params().id()));}
    @PostMapping("/getReferences") @Operation(summary="데이터소스 사용 카탈로그 조회") public ApiResponse<DataSourceReferenceService.Projection> getReferences(@Valid @RequestBody SearchRequest<DataSourceQueryService.Key> r){if(r.params()==null)throw ManagedErrorCode.VALIDATION.failure();queries.getOne(r.params().id());return ok(refs.read(r.params().id()));}
    @PostMapping("/save") @Operation(summary="데이터소스 메타·그룹·가이드 전체 저장") public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand c){return ok(commands.save("API-014",c));}
    @PostMapping("/getCommandResult") @Operation(summary="데이터소스 관리 명령 결과 확인") public ApiResponse<DataSourceCommandQueryService.Result> getCommandResult(@RequestBody DataSourceCommandQueryService.Key k){return ok(results.getResult(k));}
    @PostMapping("/getTimezoneList") @Operation(summary="데이터소스 시간대 선택 목록") public ApiResponse<List<String>> getTimezoneList(){return ok(Timezones.candidates(clock.zone()));}
    @PostMapping("/getQueryGuides") @Operation(summary="현재 데이터소스 데이터 조회 가이드 목록") public ApiResponse<PageResponse<Map<String,Object>>> getQueryGuides(@Valid @RequestBody SearchRequest<GuideFilter> r){if(r.params()==null)throw ManagedErrorCode.VALIDATION.failure();return ok(PageResponse.all(queries.guides(r.params().dataSourceId())));}
    @PostMapping("/getConfigurationCleanupPreview") @Operation(summary="설정 누락 데이터소스 일괄 정리 미리보기") public ApiResponse<ConfigurationCleanup.Preview> getConfigurationCleanupPreview(@RequestBody ConfigurationCleanup.Query r){return ok(cleanup.preview(r));}
    @PostMapping("/deleteConfigurationMismatches") @Operation(summary="확정 누락 데이터소스 일괄 정리") public ApiResponse<ConfigurationCleanup.Result> deleteConfigurationMismatches(@RequestBody ManagedCommand c){return ok(cleanup.execute(c));}
    private static <T> ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.current());}
}
