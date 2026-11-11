// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import com.aiagent.core.security.RequestPersistenceState;
import com.aiagent.core.security.SessionPersistencePort.Result;
import jakarta.servlet.ServletContext;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.session.SessionRepository;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;

/** bootstrap이 설치하는 공통 조립. 현재 ADMIN 소비 전환은 SLICE-520에서 수행한다. */
public final class AuthBoundaryAssembly {
    @FunctionalInterface public interface Command { void run() throws Exception; }
    /** 소유 현재 상태/권한 포트. 업무 callback까지 같은 보호 트랜잭션으로 감싸야 한다. */
    @FunctionalInterface public interface AuthorityBoundary {
        void invoke(ExactApiCatalog.Operation operation, Result current, RequestPersistenceState request, Command command) throws Exception;
    }
    public record AuthorityBinding(DataSource dataSource,PlatformTransactionManager manager,AuthorityBoundary boundary) {
        public AuthorityBinding {Objects.requireNonNull(dataSource);Objects.requireNonNull(manager);Objects.requireNonNull(boundary);}
    }
    public static final class Rejected extends RuntimeException {
        private final int status;
        public Rejected(int status) {
            super("인증 또는 현재 권한 판정을 거부했다");
            if (status!=401 && status!=403 && status!=503) throw new IllegalArgumentException("인증 오류 상태가 올바르지 않다");
            this.status=status;
        }
        public int status() { return status; }
    }
    private final AuthBoundaryFilter filter;
    private AuthBoundaryAssembly(JdbcSessionPersistence persistence, ExactApiCatalog catalog, String origin,
                                 boolean localHttp, boolean portalEnabled, AuthorityBinding authority, BoundaryResponseWriter responses) {
        URI uri = URI.create(origin);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || uri.getPath()!=null && !uri.getPath().isEmpty()
                || !("https".equals(uri.getScheme()) || localHttp && "http".equals(uri.getScheme())))
            throw new IllegalArgumentException("운영 인증은 정확 HTTPS Origin을 사용해야 한다");
        if(!persistence.boundTo(authority.dataSource(),authority.manager()))throw new IllegalArgumentException("소유 권한 보호와 세션 저장의 DB/REQUIRED 트랜잭션이 다르다");
        filter = new AuthBoundaryFilter(Objects.requireNonNull(persistence),Objects.requireNonNull(catalog),
                origin,"https".equals(uri.getScheme()),portalEnabled,authority.boundary(),Objects.requireNonNull(responses));
    }
    public static AuthBoundaryAssembly create(JdbcSessionPersistence persistence, ExactApiCatalog catalog,
            String origin, boolean localHttp, boolean portalEnabled, AuthorityBinding authority, BoundaryResponseWriter responses) {
        return new AuthBoundaryAssembly(persistence,catalog,origin,localHttp,portalEnabled,authority,responses);
    }
    public AuthBoundaryFilter filter() { return filter; }
    /** controller/소유 callback은 응답을 예약하고, 바깥 조립이 최종 커밋 뒤 쿠키를 발급한다. */
    public static void respondAfterCommit(jakarta.servlet.http.HttpServletRequest request, Result result) {
        if (request.getAttribute(AuthBoundaryFilter.RESPONSE)!=null || result.csrf()==null)
            throw new IllegalStateException("응답 결과를 한 번만 예약할 수 있다");
        request.setAttribute(AuthBoundaryFilter.RESPONSE,result);
    }
    /** 기본 자동 필터·전역 Repository 빈과 조용히 공존하지 않는다. 설치 시 반드시 호출한다. */
    public void verifyInstallation(ConfigurableListableBeanFactory beans, ServletContext servletContext) {
        if (beans.getBeanNamesForType(SessionRepository.class,false,false).length != 0
                || beans.containsBean("springSessionRepositoryFilter")
                || servletContext.getFilterRegistrations().entrySet().stream().anyMatch(entry ->
                        entry.getKey().equals("springSessionRepositoryFilter")
                        || entry.getValue().getClassName().contains("SessionRepositoryFilter")))
            throw new IllegalStateException("기본 세션 필터 또는 무자격 저장소가 중복 설치됐다");
        long count = servletContext.getFilterRegistrations().values().stream()
                .filter(registration -> registration.getClassName().equals(AuthBoundaryFilter.class.getName())).count();
        if (count != 1) throw new IllegalStateException("인증 영역 진입부는 한 번만 설치해야 한다");
    }
}
