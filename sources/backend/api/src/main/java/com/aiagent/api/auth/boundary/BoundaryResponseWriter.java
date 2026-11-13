// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** 기존 공통 응답 봉투/메시지 번역을 그대로 사용한다. */
public final class BoundaryResponseWriter {
    private final ObjectMapper mapper;
    private final ApiErrorWriter errors;
    public BoundaryResponseWriter(ObjectMapper mapper, ApiErrorWriter errors) {
        this.mapper=Objects.requireNonNull(mapper);this.errors=Objects.requireNonNull(errors);
    }
    public void csrf(HttpServletResponse response, String headerName) throws IOException {
        response.setContentType("application/json");response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(TraceId.HEADER,TraceId.current());
        mapper.writeValue(response.getWriter(),ApiResponse.success(Map.of("csrfHeaderName",headerName),TraceId.current()));
    }
    public void denied(HttpServletResponse response, int status) throws IOException {
        denied(response,status,()->{});
    }
    public void denied(HttpServletResponse response, int status, Runnable headers) throws IOException {
        response.reset();response.setHeader(TraceId.HEADER,TraceId.current());
        headers.run();
        errors.write(response,status==503?BoundaryErrorCode.UNAVAILABLE:status==401?CommonErrorCode.UNAUTHENTICATED:status==400?CommonErrorCode.VALIDATION:CommonErrorCode.FORBIDDEN);
    }
    public void passwordReauthentication(HttpServletResponse response)throws IOException {
        response.reset();response.setHeader(TraceId.HEADER,TraceId.current());errors.write(response,BoundaryErrorCode.PASSWORD_CHANGED);
    }
}
