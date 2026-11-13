// @feat FEAT-510
package com.aiagent.system.api.menustat;

import com.aiagent.system.api.auth.AdminRequestContext;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.EffectiveAuthority;
import com.aiagent.domain.menustat.FrequentMenus;
import com.aiagent.domain.menustat.service.MenuStatService;
import com.aiagent.system.api.menustat.request.MenuSelectRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** system 공개 계약의 메뉴 사용 통계다. */
@RestController
public class MenuStatController {

    private final MenuStatService stats;

    public MenuStatController(MenuStatService stats) {
        this.stats = stats;
    }

    @PostMapping("/api/system/common/menu/saveSelect")
    public ApiResponse<Void> select(
            @Valid @RequestBody MenuSelectRequest request,
            HttpServletRequest http,
            Principal principal) {
        if (authority(http).allows(request.menuCd(), ActionType.READ)) {
            stats.record(userId(principal), request.menuCd());
        }
        return ApiResponse.success(null, TraceId.current());
    }

    @GetMapping("/api/system/common/menu/getFrequent")
    public ApiResponse<FrequentMenus> frequent(Principal principal) {
        return ApiResponse.success(stats.findMine(userId(principal)), TraceId.current());
    }

    private static EffectiveAuthority authority(HttpServletRequest request) {
        return AdminRequestContext.authority();
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
