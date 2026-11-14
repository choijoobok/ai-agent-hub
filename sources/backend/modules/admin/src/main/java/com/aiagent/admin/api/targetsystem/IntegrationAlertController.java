// @feat FEAT-001
package com.aiagent.admin.api.targetsystem;
import com.aiagent.admin.targetsystem.TargetAlertQueryService;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.api.trace.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/service/integration")
public class IntegrationAlertController {
    private final TargetAlertQueryService service;
    public IntegrationAlertController(TargetAlertQueryService service){this.service=service;}
    @PostMapping("/getConfigurationAlerts") @Operation(summary="현재 허용 설정의 연결 경고 조회")
    public ApiResponse<TargetAlertQueryService.Page> getConfigurationAlerts(@RequestBody SearchRequest<TargetAlertQueryService.Filter> request){return ApiResponse.success(service.getAlerts(request),TraceId.current());}
}
