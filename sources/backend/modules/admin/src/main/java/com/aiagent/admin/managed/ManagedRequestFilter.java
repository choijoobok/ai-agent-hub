// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.core.error.BusinessException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** 정확 등록 경로의 형식만 준비한다. 인증/권한은 기존 필터에서 매 요청 판정한다. */
public final class ManagedRequestFilter implements Filter {
    public static final String RESULT_PATH="/api/admin/service/managementCommand/getResult";
    private final ManagedRegistry registry;
    private final ManagedAdmission admission;
    private final ObjectMapper mapper;
    private final ApiErrorWriter errors;
    public ManagedRequestFilter(ManagedRegistry registry,ManagedAdmission admission,ObjectMapper mapper,ApiErrorWriter errors) {
        this.registry=registry;this.admission=admission;this.mapper=mapper;this.errors=errors;
    }
    @Override public void doFilter(ServletRequest raw,ServletResponse response,FilterChain chain) throws IOException,ServletException {
        var request=(HttpServletRequest)raw; var operation=registry.path(request.getRequestURI());
        boolean query=request.getRequestURI().equals(RESULT_PATH);
        if(!request.getMethod().equals("POST")||(!query&&operation.isEmpty())) { chain.doFilter(raw,response);return; }
        Replay replay;
        try {
            replay=new Replay(request);
            @SuppressWarnings("unchecked") Map<String,Object> body=mapper.readValue(replay.body,Map.class);
            if(body==null)throw ManagedErrorCode.VALIDATION.failure();
            if(query) {
                if(!body.keySet().equals(Set.of("commandKey","ownerFeature"))||!(body.get("ownerFeature") instanceof String feature)||!feature.matches("FEAT-[0-9]+"))throw ManagedErrorCode.VALIDATION.failure();
                var key=ManagedCommand.requireKey((String)body.get("commandKey"));
                admission.bind(new ManagedAdmission.Prepared("API-082",key,null,feature));
            } else {
                if(!body.keySet().equals(Set.of("commandKey","targetId","expectedRevision","scope","values","reason")))throw ManagedErrorCode.VALIDATION.failure();
                var input=mapper.convertValue(body,ManagedCommand.class);
                var normalized=ManagedCommandService.normalize(operation.get().owner(),input);
                admission.bind(new ManagedAdmission.Prepared(operation.get().id(),normalized.commandKey(),normalized,null));
            }
        } catch(BusinessException failure) { errors.write((HttpServletResponse)response,failure.errorCode());return; }
        catch(tools.jackson.core.JacksonException|IllegalArgumentException|ClassCastException failure) { errors.write((HttpServletResponse)response,ManagedErrorCode.VALIDATION);return; }
        try { chain.doFilter(replay,response); } finally { admission.clear(); }
    }
    private static final class Replay extends HttpServletRequestWrapper {
        private final byte[] body;
        Replay(HttpServletRequest request) throws IOException {
            super(request); body=request.getInputStream().readNBytes(2_097_153);
            if(body.length>2_097_152)throw ManagedErrorCode.VALIDATION.failure();
        }
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available()==0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("관리 저장은 동기 요청이다"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
    }
}
