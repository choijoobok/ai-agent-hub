// @feat FEAT-040
package com.aiagent.admin.api.management;

import com.aiagent.admin.managed.ManagedCommandQueryService;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/service/managementCommand")
public class ManagementCommandController {
    private final ManagedCommandQueryService service;
    public ManagementCommandController(ManagedCommandQueryService service) { this.service=service; }
    @PostMapping("/getResult")
    @Operation(summary="공통 관리 저장의 원 명령 결과 확인")
    public ApiResponse<ManagedCommandQueryService.Result> getResult(@RequestBody ManagedCommandQueryService.Query request) {
        return ApiResponse.success(service.getResult(request),TraceId.current());
    }
}
