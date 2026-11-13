// @feat FEAT-510
package com.aiagent.system.api.searchcond;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.searchcond.SavedSearchCond;
import com.aiagent.domain.searchcond.service.SearchCondService;
import com.aiagent.system.api.searchcond.request.SearchCondDeleteRequest;
import com.aiagent.system.api.searchcond.request.SearchCondSaveRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** system 공개 계약의 사용자별 저장 조회조건이다. */
@RestController
public class SearchCondController {

    private final SearchCondService conditions;

    public SearchCondController(SearchCondService conditions) {
        this.conditions = conditions;
    }

    @GetMapping("/api/system/common/searchCond/getList")
    public ApiResponse<List<SavedSearchCond>> getList(
            @RequestParam String menuCd, Principal principal) {
        return ApiResponse.success(
                conditions.findMine(userId(principal), menuCd), TraceId.current());
    }

    @PostMapping("/api/system/common/searchCond/save")
    public ApiResponse<List<SavedSearchCond>> save(
            @Valid @RequestBody SearchCondSaveRequest request, Principal principal) {
        String userId = userId(principal);
        conditions.save(
                userId, request.menuCd(), request.condNm(), request.condJson(), request.dflt());
        return ApiResponse.success(conditions.findMine(userId, request.menuCd()), TraceId.current());
    }

    @PostMapping("/api/system/common/searchCond/delete")
    public ApiResponse<List<SavedSearchCond>> delete(
            @Valid @RequestBody SearchCondDeleteRequest request, Principal principal) {
        String userId = userId(principal);
        conditions.delete(userId, request.menuCd(), request.condNm());
        return ApiResponse.success(conditions.findMine(userId, request.menuCd()), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
