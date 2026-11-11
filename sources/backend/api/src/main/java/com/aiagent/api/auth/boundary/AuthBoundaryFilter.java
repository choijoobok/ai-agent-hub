// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import static com.aiagent.core.security.SessionPersistencePort.*;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState;
import com.aiagent.core.security.SessionAdmissions;
import com.aiagent.core.security.SessionRef;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import com.aiagent.api.trace.TraceId;
import org.slf4j.MDC;

/** 정확 카탈로그 → 영역 저장소/CSRF → 인증 수준 → 소유 권한 포트 → 업무 순서. */
public final class AuthBoundaryFilter extends OncePerRequestFilter {
    public static final String REQUEST = AuthBoundaryFilter.class.getName()+".request";
    public static final String SESSION = AuthBoundaryFilter.class.getName()+".session";
    static final String RESPONSE = AuthBoundaryFilter.class.getName()+".response";
    private final JdbcSessionPersistence persistence;
    private final ExactApiCatalog catalog;
    private final String origin;
    private final boolean secure;
    private final boolean portalEnabled;
    private final AuthBoundaryAssembly.AuthorityBoundary authority;
    private final BoundaryResponseWriter responses;
    private final EnumMap<AuthDomain,SessionRepositoryFilter<Session>> filters = new EnumMap<>(AuthDomain.class);
    private final ThreadLocal<HttpServletRequest> activeRequest = new ThreadLocal<>();
    private final EnumMap<AuthDomain,ExistingOnlyRepository> adapters = new EnumMap<>(AuthDomain.class);

    AuthBoundaryFilter(JdbcSessionPersistence persistence, ExactApiCatalog catalog, String origin,
                       boolean secure, boolean portalEnabled, AuthBoundaryAssembly.AuthorityBoundary authority, BoundaryResponseWriter responses) {
        this.persistence=persistence;this.catalog=catalog;this.origin=origin;this.secure=secure;
        this.portalEnabled=portalEnabled;this.authority=authority;this.responses=responses;
        for (AuthDomain domain : AuthDomain.values()) {
            var adapter = new ExistingOnlyRepository(domain); adapters.put(domain,adapter);
            var filter = new SessionRepositoryFilter<Session>(adapter);
            var serializer = new DefaultCookieSerializer();
            serializer.setCookieName(sessionCookie(domain)); serializer.setCookiePath("/");
            serializer.setUseHttpOnlyCookie(true); serializer.setUseSecureCookie(secure); serializer.setSameSite("Lax");
            serializer.setUseBase64Encoding(false);
            var resolver = new CookieHttpSessionIdResolver(); resolver.setCookieSerializer(serializer);
            filter.setHttpSessionIdResolver(resolver);
            filters.put(domain,filter);
        }
    }
    static String sessionCookie(AuthDomain domain) { return "AIHUB_"+domain+"_SESSION"; }
    static String csrfCookie(AuthDomain domain) { return "AIHUB_"+domain+"_CSRF"; }
    static String csrfHeader(AuthDomain domain) { return "X-AIHUB-"+domain+"-CSRF"; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String prior=TraceId.current();
        if(prior==null)MDC.put(TraceId.MDC_KEY,UUID.randomUUID().toString());
        try { dispatch(request,response,chain); }
        finally {if(prior==null)MDC.remove(TraceId.MDC_KEY);}
    }
    private void dispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)throws ServletException,IOException {
        String path=request.getRequestURI();
        String decoded;
        try { decoded=java.net.URLDecoder.decode(path,StandardCharsets.UTF_8); }
        catch (IllegalArgumentException invalid) { deny(response,403);return; }
        if (!protectedPath(path) && !protectedPath(decoded) && !protectedPath(request.getServletPath())) {
            chain.doFilter(request,response); return;
        }
        var operation = catalog.find(request.getMethod(),request.getRequestURI());
        if (operation == null || operation.domain()==AuthDomain.PORTAL && !portalEnabled) { deny(response,403); return; }
        var state = new RequestPersistenceState(operation.domain(),TraceId.current(),operation.capability(),operation.producer());
        request.setAttribute(REQUEST,state);
        activeRequest.set(request);
        var buffered = new DeferredAuthResponse(response);
        try {
            String id = cookie(request,sessionCookie(operation.domain()));
            Result current = persistence.load(operation.domain(),id);
            if (current.outcome()==Outcome.UNAVAILABLE) throw new AuthBoundaryAssembly.Rejected(503);
            if (operation.prepareCsrf()) {
                if(current.outcome()!=Outcome.CURRENT && current.outcome()!=Outcome.ABSENT && current.outcome()!=Outcome.EXPIRED)
                    throw new AuthBoundaryAssembly.Rejected(401);
                Result prepared = persistence.inTransaction(state,()->current.outcome()==Outcome.CURRENT ? persistence.prepareCsrf(state,current.ref())
                        : persistence.createAnonymous(state,SessionAdmissions.anonymous(state,persistence.databaseNow().plusSeconds(600))));
                requireSuccess(prepared);
                filters.get(operation.domain()).doFilter(request,buffered,(req,res)->{
                    prepared.csrf().emit(state,(domain,session,csrf)->writeCookies(buffered,domain,session,csrf));
                    responses.csrf(buffered,csrfHeader(operation.domain()));
                });
            } else {
                AuthLevel level = current.identity()==null ? AuthLevel.ANONYMOUS : current.identity().authLevel();
                if (!operation.levels().contains(level)) throw new AuthBoundaryAssembly.Rejected(level==AuthLevel.ANONYMOUS?401:403);
                if (!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
                    if (!Collections.list(request.getHeaders("Origin")).equals(java.util.List.of(origin))) throw new AuthBoundaryAssembly.Rejected(403);
                    var headers = Collections.list(request.getHeaders(csrfHeader(operation.domain())));
                    if (current.ref()==null || headers.size()!=1 || !persistence.csrfMatches(current.ref(),headers.getFirst()))
                        throw new AuthBoundaryAssembly.Rejected(403);
                }
                request.setAttribute(SESSION,current);
                var context = new SecurityContextImpl();
                if (current.identity()!=null) context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current.identity().principal().subjectId(),null,java.util.List.of()));
                SecurityContextHolder.setContext(context);
                persistence.inRequired(state,()->authority.invoke(operation,current,state,()->{
                    persistence.attachAttempt(state); // 소유 guard의 숨은 REQUIRES_NEW도 업무 callback 전에 거부한다.
                    filters.get(operation.domain()).doFilter(request,buffered,chain);
                }));
                if(request.getAttribute(RESPONSE) instanceof Result result)
                    result.csrf().emit(state,(domain,session,csrf)->writeCookies(buffered,domain,session,csrf));
            }
            buffered.copyBodyToResponse();
        } catch (AuthBoundaryAssembly.Rejected rejected) { deny(response,rejected.status()); }
        catch (Exception failure) {
            if (failure instanceof IOException io) throw io;
            deny(response,503); // 원문 비밀이 포함될 수 있는 예외/세션을 일반 로그에 넘기지 않는다.
        } finally {
            SecurityContextHolder.clearContext();activeRequest.remove();
            adapters.values().forEach(adapter->adapter.original.remove());
        }
    }
    private static boolean protectedPath(String path) {
        return path!=null && (path.equals("/api") || path.startsWith("/api/") || path.equals("/internal") || path.startsWith("/internal/"));
    }
    private void requireSuccess(Result result) {
        if (result.outcome()==Outcome.UNAVAILABLE) throw new AuthBoundaryAssembly.Rejected(503);
        if (result.outcome()!=Outcome.CREATED && result.outcome()!=Outcome.PREPARED) throw new AuthBoundaryAssembly.Rejected(401);
    }
    private static String cookie(HttpServletRequest request, String name) {
        String value=null;
        if (request.getCookies()!=null) for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) {
            if (value!=null) throw new AuthBoundaryAssembly.Rejected(403);
            value=cookie.getValue();
        }
        return value;
    }
    private void writeCookies(HttpServletResponse response, AuthDomain domain, String session, String csrf) {
        ((DeferredAuthResponse)response).writeCookies(()->{
            response.addHeader("Set-Cookie",cookie(sessionCookie(domain),session,true,session.isEmpty()));
            response.addHeader("Set-Cookie",cookie(csrfCookie(domain),csrf,false,session.isEmpty()));
        });
    }
    private String cookie(String name,String value,boolean httpOnly,boolean expire) {
        var builder=ResponseCookie.from(name,value).path("/").httpOnly(httpOnly).secure(secure).sameSite("Lax");
        if(expire)builder.maxAge(java.time.Duration.ZERO);
        return builder.build().toString();
    }
    private void deny(HttpServletResponse response, int status) throws IOException { responses.denied(response,status); }
    /** 자동 flush에는 기존 세대의 허용 표시/캐시 갱신만 주며 생성·회전·raw invalidate는 없다. */
    private final class ExistingOnlyRepository implements SessionRepository<Session> {
        private final AuthDomain domain;
        private final ThreadLocal<Session> original = new ThreadLocal<>();
        private ExistingOnlyRepository(AuthDomain domain) { this.domain=domain; }
        @Override public Session createSession() { throw new IllegalStateException("명시 수락 없이 세션을 만들 수 없다"); }
        @Override public Session findById(String id) {
            Session session=persistence.rawSession(domain,id);
            if (session!=null) original.set((Session)org.springframework.util.SerializationUtils.deserialize(
                    org.springframework.util.SerializationUtils.serialize(new MapSession(session))));
            return session;
        }
        @Override public void deleteById(String id) { throw new IllegalStateException("명시 종료 포트를 사용해야 한다"); }
        @Override public void save(Session session) {
            HttpServletRequest request=activeRequest.get();
            var state=(RequestPersistenceState)request.getAttribute(REQUEST);
            if(!state.canFlush())return;
            Session old=original.get();
            Result current=(Result)request.getAttribute(SESSION);
            if(old==null || current==null || current.ref()==null || !old.getId().equals(session.getId()))
                throw new IllegalStateException("원 세션의 같은 세대만 갱신할 수 있다");
            var keys=new HashSet<>(old.getAttributeNames());keys.addAll(session.getAttributeNames());
            Set<String> mutable=Set.of("DISPLAY_NAME","LOCALE","ADMIN_AUTHORITY_CACHE","ADMIN_DIRTY_SEEN");
            for(String key:keys)if(!mutable.contains(key) && !Objects.equals(old.getAttribute(key),session.getAttribute(key)))
                throw new IllegalStateException("일반 flush에서 인증 문맥을 바꿀 수 없다");
            var identity=current.identity();
            Object cache=session.getAttribute("ADMIN_AUTHORITY_CACHE");
            Set<String> authorities=cache instanceof Set<?> values ? values.stream().map(value->{
                if(!(value instanceof String text))throw new IllegalStateException("권한 캐시는 계산 결과 문자열만 허용한다");return text;
            }).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
            Result result=persistence.updateExisting(state,current.ref(),new SessionDelta(session.getLastAccessedTime(),
                    session.getAttribute("DISPLAY_NAME") instanceof String text?text:identity==null?null:identity.displayName(),
                    session.getAttribute("LOCALE") instanceof String text?text:identity==null?null:identity.locale(),
                    authorities,Boolean.TRUE.equals(session.getAttribute("ADMIN_DIRTY_SEEN"))));
            if(result.outcome()==Outcome.UNAVAILABLE)throw new AuthBoundaryAssembly.Rejected(503);
        }
    }
}
