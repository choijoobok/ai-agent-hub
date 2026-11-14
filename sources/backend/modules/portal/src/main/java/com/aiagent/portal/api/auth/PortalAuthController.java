// @feat FEAT-301
package com.aiagent.portal.api.auth;

import com.aiagent.api.auth.v2.*;
import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.portal.auth.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name="app.auth.session.domains.portal.enabled",havingValue="true")
public class PortalAuthController {
    public record Login(@NotBlank @Size(max=100) String loginId,@NotEmpty @Size(max=1024) String password) {public String toString(){return "PortalLogin[보호됨]";} @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("인증 입력 필드가 올바르지 않다");}}
    public record Password(@NotEmpty @Size(max=1024) String currentPassword,@NotEmpty @Size(max=1024) String newPassword) {public String toString(){return "PortalPasswordChange[보호됨]";} @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("인증 입력 필드가 올바르지 않다");}}
    private final PortalAuthService service;private final ApiErrorWriter errors;
    public PortalAuthController(PortalAuthService service,ApiErrorWriter errors){this.service=service;this.errors=errors;}
    private SessionRuntime.Scope scope(HttpServletRequest request){return (SessionRuntime.Scope)request.getAttribute(SessionV2Filter.SCOPE);}
    @GetMapping("/api/portal/public/auth/getCsrf") public ApiResponse<Void> getCsrf(){throw new IllegalStateException("공통 인증 필터를 거쳐야 한다");}
    @PostMapping("/api/portal/public/auth/login") public ApiResponse<PortalAuthService.State> login(@Valid @RequestBody Login form,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{return result(service.login(scope(request),form.loginId(),form.password()),response);}
    @GetMapping("/api/portal/common/auth/getMe") public ApiResponse<PortalAuthService.State> getMe(HttpServletRequest request){return ApiResponse.success(service.state(scope(request)),TraceId.current());}
    @PostMapping("/api/portal/common/auth/heartbeat") public ApiResponse<PortalAuthService.State> heartbeat(HttpServletRequest request){return getMe(request);}
    @PostMapping("/api/portal/common/auth/changePassword") public ApiResponse<PortalAuthService.State> changePassword(@Valid @RequestBody Password form,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{return result(service.changePassword(scope(request),form.currentPassword(),form.newPassword()),response);}
    @PostMapping("/api/portal/common/auth/logout") public ApiResponse<Void> logout(HttpServletRequest request){service.logout(scope(request));return ApiResponse.success(null,TraceId.current());}
    private ApiResponse<PortalAuthService.State> result(PortalAuthService.Attempt result,HttpServletResponse response)throws java.io.IOException {response.setHeader("Cache-Control","no-store");if(result.error()!=null){errors.write(response,result.error());return null;}return ApiResponse.success(result.state(),TraceId.current());}
}
