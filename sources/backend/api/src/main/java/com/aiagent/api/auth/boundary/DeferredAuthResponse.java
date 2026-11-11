// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 인증 명령은 JSON 응답만 사용한다. 최종 커밋 전 즉시 응답/리다이렉트를 금지한다. */
final class DeferredAuthResponse extends ContentCachingResponseWrapper {
    private boolean cookieWriter;
    DeferredAuthResponse(HttpServletResponse response) { super(response); }
    void writeCookies(Runnable command) {
        if(cookieWriter)throw new IllegalStateException("쿠키 writer가 중복 진입했다");
        cookieWriter=true;
        try{command.run();}finally{cookieWriter=false;}
    }
    private void checkHeader(String name) {
        if("Set-Cookie".equalsIgnoreCase(name) && !cookieWriter)
            throw new IllegalStateException("쿠키는 커밋 뒤 영역 writer만 발급한다");
    }
    @Override public void addHeader(String name,String value) {checkHeader(name);super.addHeader(name,value);}
    @Override public void setHeader(String name,String value) {checkHeader(name);super.setHeader(name,value);}
    @Override public void addCookie(Cookie cookie) {
        if(!cookieWriter)throw new IllegalStateException("직접 쿠키 발급을 허용하지 않는다");
        super.addCookie(cookie);
    }
    @Override public void sendRedirect(String location) {throw early();}
    @Override public void sendRedirect(String location,int status,boolean clearBuffer) {throw early();}
    @Override public void sendError(int status) {throw early();}
    @Override public void sendError(int status,String message) {throw early();}
    @Override public void flushBuffer() {throw early();}
    private IllegalStateException early() {return new IllegalStateException("인증 명령은 커밋 전에 응답을 확정할 수 없다");}
}
