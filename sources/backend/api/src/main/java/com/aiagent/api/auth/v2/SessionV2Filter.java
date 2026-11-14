// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.*;
import com.aiagent.api.auth.boundary.BoundaryResponseWriter;
import com.aiagent.api.auth.boundary.DeferredAuthResponse;
import com.aiagent.api.auth.boundary.AuthBoundaryAssembly;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.v2.SessionV2.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.*;
import org.slf4j.MDC;

/** v2 exact module-first HTTP 설치점. 제품 설치/활성화는520이다. */
public final class SessionV2Filter extends OncePerRequestFilter {
    public static final String SCOPE=SessionV2Filter.class.getName()+".scope";
    public static final String INSPECTION=SessionV2Filter.class.getName()+".inspection";
    private static final String SOURCE_ACCEPTANCE=SessionV2Filter.class.getName()+".sourceAcceptance";
    private record SourceAcceptance(String method,String path,Thread thread) {}
    /** 정확 NONE 원천 검증을 마친 동기 filter 범위만 MVC 경계가 확인할 수 있다. */
    public static boolean sourceVerified(HttpServletRequest request){return request.getAttribute(SOURCE_ACCEPTANCE) instanceof SourceAcceptance accepted&&accepted.thread()==Thread.currentThread()&&accepted.method().equals(request.getMethod())&&accepted.path().equals(request.getRequestURI());}
    @FunctionalInterface public interface Preflight {SessionInspectionPort.Plan prepare(HttpServletRequest request,SessionInspectionPort inspection)throws Exception;}
    private final SessionRuntime runtime;private final ModuleApiCatalog catalog;private final SessionV2Assembly.Configuration configuration;
    private final Map<AuthDomain,SessionAdmissionsV2.Producer> producers;private final BoundaryResponseWriter responses;private final Map<String,Preflight> preflights;
    public SessionV2Filter(SessionRuntime runtime,ModuleApiCatalog catalog,SessionV2Assembly.Configuration configuration,Map<AuthDomain,SessionAdmissionsV2.Producer> producers,BoundaryResponseWriter responses){this(runtime,catalog,configuration,producers,responses,Map.of());}
    public SessionV2Filter(SessionRuntime runtime,ModuleApiCatalog catalog,SessionV2Assembly.Configuration configuration,Map<AuthDomain,SessionAdmissionsV2.Producer> producers,BoundaryResponseWriter responses,Map<String,Preflight> preflights){
        this.runtime=Objects.requireNonNull(runtime);this.catalog=Objects.requireNonNull(catalog);this.configuration=configuration;this.producers=Map.copyOf(producers);this.responses=responses;this.preflights=Map.copyOf(preflights);
        Set<String> ids=catalog.entries().stream().map(ModuleApiCatalog.Operation::id).collect(java.util.stream.Collectors.toSet());if(!ids.containsAll(preflights.keySet()))throw new IllegalArgumentException("조회 준비는 정확 등록된 operation에만 연결한다");
    }
    private static boolean api(String path){return path!=null&&(path.equals("/api")||path.startsWith("/api/")||path.equals("/internal")||path.startsWith("/internal/"));}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String prior=TraceId.current();if(prior==null)MDC.put(TraceId.MDC_KEY,UUID.randomUUID().toString());
        SecurityContextHolder.clearContext();try{dispatch(request,response,chain);}finally{SecurityContextHolder.clearContext();if(prior==null)MDC.remove(TraceId.MDC_KEY);}
    }
    private void dispatch(HttpServletRequest original,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String path=original.getRequestURI(),decoded;try{decoded=java.net.URLDecoder.decode(path,StandardCharsets.UTF_8);}catch(IllegalArgumentException failure){responses.denied(response,403);return;}
        if(!api(path)&&!api(decoded)&&!api(original.getServletPath())){chain.doFilter(original,response);return;}
        var match=catalog.resolve(original.getMethod(),path);if(match==null){responses.denied(response,403);return;}
        var operation=match.operation();var buffered=new DeferredAuthResponse(response);SessionRuntime.Scope scope=null;
        try{
            if(operation.domain()==ModuleApiCatalog.Domain.NONE){
                var source=new ReplayRequest(withoutWasSession(original));
                try{operation.sourceVerifier().verify(source);}
                catch(AuthBoundaryAssembly.Rejected failure){throw failure;}
                catch(Exception failure){throw new AuthBoundaryAssembly.Rejected(403);}
                SecurityContextHolder.clearContext();
                source.setAttribute(SOURCE_ACCEPTANCE,new SourceAcceptance(source.getMethod(),source.getRequestURI(),Thread.currentThread()));
                try{chain.doFilter(source,buffered);}finally{source.removeAttribute(SOURCE_ACCEPTANCE);}
            }else{
                AuthDomain domain=AuthDomain.valueOf(operation.domain().name());if(!configuration.domains().containsKey(domain))throw new AuthBoundaryAssembly.Rejected(403);
                String cookie=cookie(original,"AIHUB_"+domain+"_SESSION");cookie(original,"AIHUB_"+domain+"_CSRF");
                var exchange=new SessionExchange(domain,TraceId.current(),operation.capability(),producers.get(domain));scope=runtime.open(exchange,operation,cookie);final var active=scope;
                if(!Set.of("GET","HEAD","OPTIONS").contains(original.getMethod())){
                    if(!Collections.list(original.getHeaders("Origin")).equals(List.of(configuration.origin())))throw new AuthBoundaryAssembly.Rejected(403);
                    var tokens=Collections.list(original.getHeaders("X-AIHUB-"+domain+"-CSRF"));if(tokens.size()!=1||!scope.matchesCsrf(tokens.getFirst()))throw new AuthBoundaryAssembly.Rejected(403);
                }
                if(match.jobId()!=null&&!match.ownsJob(scope.current().map(Snapshot::identity).map(identity->identity.principal()).orElse(null))){scope.exchange().close();responses.denied(response,400);return;}
                HttpServletRequest request=withoutWasSession(original);
                Preflight prepare=preflights.get(operation.id());if(prepare!=null){request=new ReplayRequest(request);var plan=prepare.prepare(request,runtime);scope.bindInspection(plan);request.setAttribute(INSPECTION,plan);}
                request.setAttribute(SCOPE,scope);var identity=scope.current().map(Snapshot::identity).orElse(null);var context=new SecurityContextImpl();
                if(identity!=null){var auth=UsernamePasswordAuthenticationToken.authenticated(identity.principal().subjectId(),null,List.of());auth.setDetails(identity);context.setAuthentication(auth);}SecurityContextHolder.setContext(context);
                final HttpServletRequest secured=request;
                runtime.execute(scope,()->{try{if(operation.capability()==Capability.PREPARE_CSRF){active.prepareCsrf();responses.csrf(buffered,"X-AIHUB-"+domain+"-CSRF");}else chain.doFilter(secured,buffered);
                    if(Boolean.TRUE.equals(secured.getAttribute(com.aiagent.api.error.GlobalExceptionHandler.HANDLED_FAILURE)))throw new HandledFailure();
                    return null;}catch(IOException|ServletException failure){throw new DispatchFailure(failure);}});
                if(runtime.finish(scope)==Outcome.DELIVERABLE)scope.emitCookies((realm,session,csrf)->buffered.writeCookies(()->{
                    buffered.addHeader("Set-Cookie",cookie("AIHUB_"+realm+"_SESSION",session,true));buffered.addHeader("Set-Cookie",cookie("AIHUB_"+realm+"_CSRF",csrf,false));
                }));
            }
        }catch(HandledFailure failure){if(scope!=null)scope.exchange().close();buffered.copyBodyToResponse();return;}
        catch(Failure failure){if(scope!=null)scope.exchange().close();if(failure.outcome()==Outcome.REAUTH_REQUIRED)responses.passwordReauthentication(response);else responses.denied(response,failure.outcome()==Outcome.INVALID_SESSION?401:503);return;}
        catch(SessionAuthority.Unusable unusable){
            boolean invalidated=scope!=null&&scope.invalidated();
            responses.denied(response,401,()->{if(invalidated) {
                response.addHeader("Set-Cookie",cookie("AIHUB_"+operation.domain()+"_SESSION","",true));
                response.addHeader("Set-Cookie",cookie("AIHUB_"+operation.domain()+"_CSRF","",false));
            }});return;
        }
        catch(AuthBoundaryAssembly.Rejected rejected){if(scope!=null)scope.exchange().close();responses.denied(response,rejected.status(),()->{
            if(rejected.status()==403&&original.getAttribute(com.aiagent.api.authz.AuthzVersionHeader.NAME) instanceof String version)
                response.setHeader(com.aiagent.api.authz.AuthzVersionHeader.NAME,version);
            });return;}
        catch(Exception failure){
            Throwable cause=failure;while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
            org.slf4j.LoggerFactory.getLogger(SessionV2Filter.class).warn("인증 경계 처리 실패 type={} at={}",cause.getClass().getName(),java.util.Arrays.stream(cause.getStackTrace()).limit(5).toList());
            if(scope!=null)scope.exchange().close();responses.denied(response,503);return;
        }
        // 응답 전달 실패는 이미 확정된 DB업무/발급을 다시 실행하거나 보상하지 않는다.
        buffered.copyBodyToResponse();
    }
    private String cookie(String name,String value,boolean httpOnly){var cookie=ResponseCookie.from(name,value).path("/").httpOnly(httpOnly).secure(configuration.origin().startsWith("https:")).sameSite("Lax");if(value.isEmpty())cookie.maxAge(0);return cookie.build().toString();}
    private static String cookie(HttpServletRequest request,String name){String value=null;if(request.getCookies()!=null)for(Cookie cookie:request.getCookies())if(cookie.getName().equals(name)){if(value!=null)throw new AuthBoundaryAssembly.Rejected(403);value=cookie.getValue();}return value;}
    private static HttpServletRequest withoutWasSession(HttpServletRequest request){return new HttpServletRequestWrapper(request){
        @Override public HttpSession getSession(boolean create){throw new IllegalStateException("공통 인증 포트로 세션을 사용해야 한다");}
        @Override public HttpSession getSession(){return getSession(true);}
        @Override public java.security.Principal getUserPrincipal(){return SecurityContextHolder.getContext().getAuthentication();}
        @Override public String getRemoteUser(){var principal=getUserPrincipal();return principal==null?null:principal.getName();}
        @Override public String getAuthType(){return getUserPrincipal()==null?null:"COMMON_SESSION";}
        @Override public boolean isUserInRole(String role){return false;}
        @Override public String changeSessionId(){throw new IllegalStateException("명시 회전 포트를 사용해야 한다");}
        @Override public void login(String username,String password)throws ServletException{throw new ServletException("명시 자격 검증 포트를 사용해야 한다");}
        @Override public void logout()throws ServletException{throw new ServletException("명시 종료 포트를 사용해야 한다");}
        @Override public boolean authenticate(HttpServletResponse response)throws ServletException{throw new ServletException("공통 인증 경계를 사용해야 한다");}
        @Override public AsyncContext startAsync(){throw new IllegalStateException("인증 경계는 동기 요청만 허용한다");}
        @Override public AsyncContext startAsync(ServletRequest request,ServletResponse response){return startAsync();}
        @Override public AsyncContext getAsyncContext(){throw new IllegalStateException("인증 경계는 동기 요청만 허용한다");}
    };}
    private static final class DispatchFailure extends RuntimeException {DispatchFailure(Exception cause){super("보호 요청 처리 실패",cause);}}
    private static final class HandledFailure extends RuntimeException {HandledFailure(){super("업무 오류 응답을 보존하고 요청을 롤백한다");}}
    private static final class ReplayRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        ReplayRequest(HttpServletRequest request)throws IOException{super(request);body=request.getInputStream().readNBytes(1_048_577);if(body.length>1_048_576)throw new AuthBoundaryAssembly.Rejected(400);}
        @Override public ServletInputStream getInputStream(){var input=new java.io.ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new IllegalStateException("조회 준비는 동기 요청만 허용한다");}};}
        @Override public java.io.BufferedReader getReader(){return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
