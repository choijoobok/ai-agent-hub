// @feat FEAT-301
package com.aiagent.portal.api.auth;

import com.aiagent.api.auth.boundary.BoundaryErrorCode;
import com.aiagent.api.error.ErrorMessages;
import com.aiagent.api.error.GlobalExceptionHandler;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 필수 인증 저장 장애는 정상 거부와 구별하고 현재 요청 전체를 롤백한다. */
@RestControllerAdvice(basePackageClasses=PortalAuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PortalAuthExceptionHandler {
    private final ErrorMessages messages;
    public PortalAuthExceptionHandler(ErrorMessages messages){this.messages=messages;}
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(HttpServletRequest request,DataAccessException failure) {
        request.setAttribute(GlobalExceptionHandler.HANDLED_FAILURE,Boolean.TRUE);
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Portal 인증 저장을 완료하지 못했다 type={}",failure.getClass().getSimpleName());
        var code=BoundaryErrorCode.UNAVAILABLE;
        return ResponseEntity.status(503).header("Cache-Control","no-store").body(ApiResponse.failure(code.code(),messages.of(code),TraceId.current()));
    }
}
