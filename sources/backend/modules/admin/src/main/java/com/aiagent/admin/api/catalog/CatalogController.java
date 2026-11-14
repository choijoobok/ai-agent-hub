// @feat FEAT-003
package com.aiagent.admin.api.catalog;
import com.aiagent.admin.catalog.*;
import com.aiagent.admin.managed.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.*;
import com.aiagent.core.deployment.CatalogSource;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/service/catalog")
public class CatalogController {
 public record Key(String id,String targetId,String catalogId,String revision,String historyNo,String kind) {public String asset(){return id!=null?id:targetId!=null?targetId:catalogId;}}
 public record CommandKey(String commandKey){}
 private final CatalogQueryService queries;private final ManagedCommandService commands;private final CatalogCommandQueryService results;
 public CatalogController(CatalogQueryService queries,ManagedCommandService commands,CatalogCommandQueryService results){this.queries=queries;this.commands=commands;this.results=results;}
 @PostMapping("/getList") @Operation(summary="카탈로그 목록 조회") public ApiResponse<PageResponse<Map<String,Object>>> getList(@Valid @RequestBody SearchRequest<CatalogQueryService.Filter> r){return ok(queries.getList(r));}
 @PostMapping("/getOne") @Operation(summary="카탈로그 현재 전체 구성 조회") public ApiResponse<CatalogQueryService.Detail> getOne(@RequestBody Key k){return ok(queries.getOne(k.asset()));}
 @PostMapping("/saveCreate") @Operation(summary="카탈로그 마스터 신규 저장") public ApiResponse<Object> saveCreate(@RequestBody ManagedCommand c){if(c.targetId()!=null||!"MASTER".equals(c.scope()))throw ManagedErrorCode.VALIDATION.failure();return ok(CatalogResults.management(commands.save("API-023",c),"0",queries.mappings(c.commandKey())));}
 @PostMapping("/saveChange") @Operation(summary="카탈로그 지정 영역 저장") public ApiResponse<Object> saveChange(@RequestBody ManagedCommand c){if(c.targetId()==null)throw ManagedErrorCode.VALIDATION.failure();return ok(CatalogResults.management(commands.save("API-024",c),c.expectedRevision(),queries.mappings(c.commandKey())));}

 @PostMapping("/getCommandResult") @Operation(summary="카탈로그 저장 원 결과 확인") public ApiResponse<CatalogCommandQueryService.Result> getCommandResult(@RequestBody CommandKey k){return ok(results.getResult(k.commandKey()));}
 @PostMapping("/getResolvedObject") @Operation(summary="선정 공개 객체의 정확 원천 식별 조회") public ApiResponse<CatalogSource.Observation> getResolvedObject(@RequestBody Map<String,Object> r){return ok(queries.getResolvedObject(r));}
 @PostMapping("/getStructure") @Operation(summary="등록 카탈로그의 현재 원천 구조 조회") public ApiResponse<CatalogSource.Observation> getStructure(@RequestBody Key k){return ok(queries.getStructure(k.asset(),k.revision()));}
 @PostMapping("/getRevision") @Operation(summary="카탈로그 명시 적용 이력 조회") public ApiResponse<Object> getRevision(@RequestBody Key k){return ok("CURRENT".equals(k.kind())?queries.getOne(k.asset()):queries.revision(k.asset(),k.revision(),k.historyNo()));}
 @PostMapping("/getReferences") @Operation(summary="카탈로그 연관 지식 연결 상태 조회") public ApiResponse<Object> getReferences(@RequestBody Key k){return ok(queries.getOne(k.asset()).dependencies().get("references"));}
 @PostMapping("/getRelatedJoins") @Operation(summary="카탈로그 관계 연결 상태 조회") public ApiResponse<Object> getRelatedJoins(@RequestBody Key k){return ok(queries.getOne(k.asset()).dependencies().get("relations"));}
 @PostMapping("/getValidation") @Operation(summary="카탈로그 현재 검증 상태 조회") public ApiResponse<Object> getValidation(@RequestBody Key k){queries.getOne(k.asset());return ok(Map.of("state","NOT_CONNECTED","sqlSupport","NOT_CONNECTED"));}
 @PostMapping("/getReferenceCandidates") @Operation(summary="카탈로그 현재 입력 참조 후보 조회") public ApiResponse<Map<String,Object>> getReferenceCandidates(){return ok(queries.getOptions());}

 private static <T> ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.current());}
}
