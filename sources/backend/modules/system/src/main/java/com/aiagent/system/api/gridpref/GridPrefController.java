// @feat FEAT-510
package com.aiagent.system.api.gridpref;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.gridpref.GridPref;
import com.aiagent.domain.gridpref.service.GridPrefService;
import com.aiagent.system.api.gridpref.request.GridPrefResetRequest;
import com.aiagent.system.api.gridpref.request.GridPrefSaveRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** system 공개 계약의 사용자별 그리드 표시 설정이다. */
@RestController
public class GridPrefController {

    private final GridPrefService preferences;

    public GridPrefController(GridPrefService preferences) {
        this.preferences = preferences;
    }

    @GetMapping("/api/system/common/gridPref/getOne")
    public ApiResponse<GridPref> getOne(
            @RequestParam String menuCd,
            @RequestParam String gridId,
            @RequestParam String signature,
            Principal principal) {
        return ApiResponse.success(
                preferences.find(userId(principal), menuCd, gridId, signature), TraceId.current());
    }

    @PostMapping("/api/system/common/gridPref/save")
    public ApiResponse<Void> save(
            @Valid @RequestBody GridPrefSaveRequest request, Principal principal) {
        preferences.save(
                userId(principal),
                request.menuCd(),
                request.gridId(),
                request.prefJson(),
                request.signature());
        return ApiResponse.success(null, TraceId.current());
    }

    @PostMapping("/api/system/common/gridPref/delete")
    public ApiResponse<Void> reset(
            @Valid @RequestBody GridPrefResetRequest request, Principal principal) {
        preferences.reset(userId(principal), request.menuCd(), request.gridId());
        return ApiResponse.success(null, TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
