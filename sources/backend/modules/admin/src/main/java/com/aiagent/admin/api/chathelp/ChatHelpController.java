// @feat FEAT-010
package com.aiagent.admin.api.chathelp;
import com.aiagent.admin.chathelp.*;
import com.aiagent.admin.managed.*;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.support.content.SafeContentRenderer;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/service/chatHelp")
public class ChatHelpController {
    public record Target(String helpCd){}
    public record Preview(String formatCd,String body){}
    private final ChatHelpQueryService queries;private final ChatHelpService owner;private final ManagedCommandService commands;
    public ChatHelpController(ChatHelpQueryService queries,ChatHelpService owner,ManagedCommandService commands){this.queries=queries;this.owner=owner;this.commands=commands;}
    @PostMapping("/getList") @Operation(summary="채팅 안내 고정 구분 조회")
    public ApiResponse<List<ChatHelpQueryService.View>> getList(@RequestBody Map<String,Object> request,Locale locale){if(!request.isEmpty())throw ManagedErrorCode.VALIDATION.failure();return ok(queries.getList(locale.getLanguage()));}
    @PostMapping("/getOne") @Operation(summary="채팅 안내 본문 조회")
    public ApiResponse<ChatHelpQueryService.View> getOne(@RequestBody Target request,Locale locale){return ok(queries.getOne(request.helpCd(),locale.getLanguage()));}
    @PostMapping("/getPreview") @Operation(summary="채팅 안내 안전 미리보기")
    public ApiResponse<SafeContentRenderer.Rendered> getPreview(@RequestBody Preview request){return ok(owner.preview(request.formatCd(),request.body()));}
    @PostMapping("/save") @Operation(summary="채팅 안내 본문 저장")
    public ApiResponse<ManagedResult> save(@RequestBody ManagedCommand request){return ok(commands.save("API-141",request));}
    private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.current());}
}
