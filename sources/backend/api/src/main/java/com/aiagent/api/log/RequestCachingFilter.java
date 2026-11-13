package com.aiagent.api.log;

import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.RequestCacheProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 요청 body 를 <b>다시 읽을 수 있게</b> 감싼다 — backend/packages.md.
 *
 * <p>{@code HttpServletRequest} 의 body 는 한 번만 읽힌다. 컨트롤러가 읽은 뒤에도
 * 인터셉터({@link UserActionLogInterceptor})와 예외 핸들러가 <b>같은 내용</b>을 보려면
 * 필터에서 미리 감싸야 한다.
 *
 * <p><b>{@link com.aiagent.api.trace.RequestIdMdcFilter} 다음이고 Security 앞이다.</b>
 * 추적 ID 는 이 필터가 실패해도 붙어야 하고, Security 는 {@code x-www-form-urlencoded} 를
 * 파싱하며 body 를 읽으므로 그 앞에서 감싸지 않으면 <b>로그인 요청의 전문이 비어 있다</b>.
 *
 * <h2>담기지 않는 요청 둘</h2>
 *
 * <table><caption></caption>
 * <tr><td>{@code /api/} 밖</td><td>정적 리소스·SPA 라우트다. 이력의 대상이 아니다(backend/packages.md)</td></tr>
 * <tr><td>{@code multipart/}</td><td>업로드 바이너리를 통째로 버퍼링하면 <b>OOM</b> 이다.
 *     그 요청도 이력에는 남고 전문만 비운다</td></tr>
 * </table>
 *
 * <p><b>상한을 넘으면 조용히 자른다 — 그리고 잘렸음을 화면이 말한다</b>(screen/admin/system/log/access/requirements.md).
 * 판({@link ContentCachingRequestWrapper})이 상한 뒤를 캐시하지 않을 뿐 <b>애플리케이션에는
 * 전량이 전달된다</b> — 잘린 것은 <i>기록</i> 이지 <i>처리</i> 가 아니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCachingFilter extends OncePerRequestFilter {

    private final ConfigLookup configs;

    public RequestCachingFilter(ConfigLookup configs) {
        this.configs = configs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RequestCacheProps props = configs.get(RequestCacheProps.class);
        if (!props.enabled() || SensitiveRequest.excluded(request) || !shouldCache(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new ContentCachingRequestWrapper(request, props.maxBytes()), response);
    }

    private static boolean shouldCache(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType == null || !contentType.toLowerCase().startsWith("multipart/");
    }
}
