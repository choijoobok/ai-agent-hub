// @feat FEAT-510
package com.aiagent.system.api.favorite;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.favorite.FavoriteMenu;
import com.aiagent.domain.favorite.service.FavoriteService;
import com.aiagent.system.api.favorite.request.FavoriteMenuRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** system 공개 계약의 내 즐겨찾기 상태다. 대상 사용자는 언제나 세션 주인이다. */
@RestController
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping("/api/system/common/favorite/getList")
    public ApiResponse<List<FavoriteMenu>> getList(Principal principal) {
        return ApiResponse.success(favorites.findMine(userId(principal)), TraceId.current());
    }

    @PostMapping("/api/system/common/favorite/save")
    public ApiResponse<List<FavoriteMenu>> add(
            @Valid @RequestBody FavoriteMenuRequest request, Principal principal) {
        String userId = userId(principal);
        favorites.add(userId, request.menuCd());
        return ApiResponse.success(favorites.findMine(userId), TraceId.current());
    }

    @PostMapping("/api/system/common/favorite/delete")
    public ApiResponse<List<FavoriteMenu>> remove(
            @Valid @RequestBody FavoriteMenuRequest request, Principal principal) {
        String userId = userId(principal);
        favorites.remove(userId, request.menuCd());
        return ApiResponse.success(favorites.findMine(userId), TraceId.current());
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return principal.getName();
    }
}
