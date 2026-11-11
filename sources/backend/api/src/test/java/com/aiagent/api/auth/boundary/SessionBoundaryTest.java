// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import static com.aiagent.core.security.SessionPersistencePort.*;
import static org.assertj.core.api.Assertions.*;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.RequestPersistenceState;
import com.aiagent.core.security.SessionAdmissions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockServletContext;
import jakarta.servlet.http.Cookie;
import org.springframework.session.Session;

/** 실제 계정 검증은 SLICE-520/301의 소비 시험이다. 여기서는 공통 조립을 검증한다. */
class SessionBoundaryTest {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private JdbcSessionPersistence first;
    private JdbcSessionPersistence second;
    private final java.util.Map<AuthDomain,SessionAdmissions.Producer> producers = java.util.Map.of(
            AuthDomain.ADMIN,SessionAdmissions.producer(AuthDomain.ADMIN,"test-current-admin"),
            AuthDomain.PORTAL,SessionAdmissions.producer(AuthDomain.PORTAL,"test-current-portal"));

    @AfterEach void releaseInMemoryDatabase() {
        if(dataSource!=null && dataSource.getUrl()!=null && dataSource.getUrl().startsWith("jdbc:h2:mem:"))new JdbcTemplate(dataSource).execute("SHUTDOWN");
    }

    @BeforeEach
    void setUp() {
        String url = System.getenv().getOrDefault("AUTH_TEST_JDBC",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("AUTH_TEST_USER", "sa"),
                System.getenv().getOrDefault("AUTH_TEST_PASSWORD", ""));
        if (url.startsWith("jdbc:postgresql:")) {
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/auth307[a-z0-9_]*"))
                throw new IllegalArgumentException("전용 임시 시험 DB만 허용한다");
            String schema = "auth307_" + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(dataSource).execute("CREATE SCHEMA " + schema);
            dataSource.setUrl(url + "?currentSchema=" + schema);
        }
        jdbc = new JdbcTemplate(dataSource);
        for (String table : List.of("SPRING_SESSION", "AI_PORTAL_SESSION")) {
            jdbc.execute("CREATE TABLE " + table + " (PRIMARY_ID VARCHAR(36) PRIMARY KEY, SESSION_ID VARCHAR(36) UNIQUE,"
                    + " CREATION_TIME BIGINT NOT NULL, LAST_ACCESS_TIME BIGINT NOT NULL, MAX_INACTIVE_INTERVAL INT NOT NULL,"
                    + " EXPIRY_TIME BIGINT NOT NULL, PRINCIPAL_NAME VARCHAR(100))");
            jdbc.execute("CREATE TABLE " + table + "_ATTRIBUTES (SESSION_PRIMARY_ID VARCHAR(36) NOT NULL REFERENCES "
                    + table + "(PRIMARY_ID) ON DELETE CASCADE, ATTRIBUTE_NAME VARCHAR(200) NOT NULL, ATTRIBUTE_BYTES "
                    + (url.startsWith("jdbc:postgresql:") ? "BYTEA" : "VARBINARY")
                    + " NOT NULL, PRIMARY KEY(SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
        }
        var tm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(tm);
        first = new JdbcSessionPersistence(dataSource, tm);
        second = new JdbcSessionPersistence(dataSource, tm);
    }

    private RequestPersistenceState request(AuthDomain domain) {
        return new RequestPersistenceState(domain, UUID.randomUUID().toString());
    }
    private RequestPersistenceState request(AuthDomain domain, RequestPersistenceState.Capability capability) {
        var request=new RequestPersistenceState(domain,UUID.randomUUID().toString(),capability,producers.get(domain));
        if(first!=null && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())first.attachAttempt(request);
        return request;
    }

    private Result anonymous(AuthDomain domain) {
        var request = request(domain,RequestPersistenceState.Capability.PREPARE_CSRF);
        return first.inTransaction(request,() -> first.createAnonymous(request,
                SessionAdmissions.anonymous(request, Instant.now().plusSeconds(600))));
    }

    private Result login(AuthDomain domain, Result entry) {
        var request = request(domain,RequestPersistenceState.Capability.LOGIN);
        var identity = new Identity(new PrincipalRef(domain, "7"), AuthLevel.AUTHENTICATED,
                "LOCAL", domain == AuthDomain.PORTAL ? 1L : null, Instant.now(), "관리자", "ko");
        return first.inTransaction(request,() -> first.createAuthorized(request,
                producers.get(domain).login(request, entry.ref(), identity, Instant.now().plusSeconds(60)),
                Instant.now().plusSeconds(900)));
    }

    @Test @DisplayName("TC-30116: 영역별 표준 JDBC 저장소와 principal 인덱스가 격리된다")
    void isolatedStoresAndPrincipalIndexes() {
        var admin = login(AuthDomain.ADMIN, anonymous(AuthDomain.ADMIN));
        var portal = login(AuthDomain.PORTAL, anonymous(AuthDomain.PORTAL));
        assertThat(first.load(AuthDomain.ADMIN, admin.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        assertThat(second.load(AuthDomain.PORTAL, portal.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        assertThat(first.load(AuthDomain.ADMIN, portal.ref().sessionId()).outcome()).isEqualTo(Outcome.ABSENT);
        assertThat(first.findByPrincipal(new PrincipalRef(AuthDomain.ADMIN, "7"))).hasSize(1);
        assertThat(second.findByPrincipal(new PrincipalRef(AuthDomain.PORTAL, "7"))).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT PRINCIPAL_NAME FROM SPRING_SESSION", String.class)).isEqualTo("7");
        assertThat(jdbc.queryForObject("SELECT PRINCIPAL_NAME FROM AI_PORTAL_SESSION", String.class)).isEqualTo("PORTAL:7");
    }

    @Test @DisplayName("TC-30116/30120: 참여 트랜잭션 롤백은 새 세션·쿠키를 함께 취소한다")
    void joinsCallerTransactionAndSuppressesRollbackDelivery() {
        var request = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
        Result created = tx.execute(status -> {
            first.attachAttempt(request);
            var result = first.createAnonymous(request, SessionAdmissions.anonymous(request, Instant.now().plusSeconds(300)));
            assertThat(request.state()).isEqualTo(RequestPersistenceState.State.PENDING_COMMIT);
            assertThatThrownBy(() -> result.csrf().emit(request, (d,s,c) -> fail("커밋 전 발급")))
                    .isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
            return result;
        });
        assertThat(request.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
        assertThat(first.load(AuthDomain.ADMIN, created.ref().sessionId()).outcome()).isEqualTo(Outcome.ABSENT);
        assertThatThrownBy(() -> created.csrf().emit(request, (d,s,c) -> fail("롤백 발급")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("TC-30118: 인증 CSRF 재준비는 ID·주체·토큰을 보존한다")
    void preparesExistingCsrfWithoutAnonymousDowngrade() {
        var original = login(AuthDomain.ADMIN, anonymous(AuthDomain.ADMIN));
        var request = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
        var prepared = tx.execute(status -> second.prepareCsrf(request, original.ref()));
        assertThat(prepared.ref()).isEqualTo(original.ref());
        assertThat(prepared.identity()).isEqualTo(original.identity());
        assertThat(secret(prepared, request)).isEqualTo(storedSecret(original));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isEqualTo(1);
    }

    @Test @DisplayName("TC-30119: 회전 증거는 요청·영역·세션·단일 소비에 결합된다")
    void rotationRequiresMatchingOneUseAdmissionAndChangesCsrf() {
        var original = login(AuthDomain.ADMIN, anonymous(AuthDomain.ADMIN));
        String oldCsrf = storedSecret(original);
        var request = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PASSWORD_CHANGE);
        var proof=new java.util.concurrent.atomic.AtomicReference<SessionAdmissions.RotationAdmission>();
        var rotated=first.inTransaction(request,()->{
            var admission=producers.get(AuthDomain.ADMIN).rotation(request,original.ref(),original.identity(),Instant.now().plusSeconds(30));proof.set(admission);
            var wrongRequest=request(AuthDomain.ADMIN);
            assertThat(first.rotate(wrongRequest,original.ref(),admission,Instant.now().plusSeconds(300)).outcome()).isEqualTo(Outcome.REJECTED);
            return first.rotate(request,original.ref(),admission,Instant.now().plusSeconds(300));
        });
        assertThat(rotated.outcome()).isEqualTo(Outcome.ROTATED);
        assertThat(rotated.ref().sessionId()).isNotEqualTo(original.ref().sessionId());
        assertThat(secret(rotated, request)).isNotEqualTo(oldCsrf);
        assertThat(first.load(AuthDomain.ADMIN, original.ref().sessionId()).outcome()).isEqualTo(Outcome.ABSENT);
        assertThat(tx.execute(status -> first.rotate(request(AuthDomain.ADMIN), rotated.ref(), proof.get(), Instant.now().plusSeconds(300))).outcome())
                .isEqualTo(Outcome.REJECTED);
    }

    @Test @DisplayName("TC-30117/30119: 로그인 증거 재사용과 오래된 진입 세대는 거부한다")
    void rejectsReplayExpiredAndStaleEntry() {
        var entry = anonymous(AuthDomain.ADMIN);
        var request = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.LOGIN);
        var identity = new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.AUTHENTICATED,"LOCAL",null,Instant.now(),"관리자","ko");
        assertThat(first.inTransaction(request,()->first.createAuthorized(request,
                producers.get(AuthDomain.ADMIN).login(request,entry.ref(),identity,Instant.now().minusSeconds(1)),Instant.now().plusSeconds(300))).outcome()).isEqualTo(Outcome.REJECTED);
        var current = login(AuthDomain.ADMIN,entry);
        var staleRequest = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.LOGIN);
        assertThat(first.inTransaction(staleRequest,() -> first.createAuthorized(staleRequest,
                producers.get(AuthDomain.ADMIN).login(staleRequest,entry.ref(),identity,Instant.now().plusSeconds(30)),Instant.now().plusSeconds(300))).outcome())
                .isEqualTo(Outcome.STALE);
        assertThat(first.load(AuthDomain.ADMIN,current.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
    }

    @Test @DisplayName("TC-30116/30120: 종료·회전 뒤 늦은 기존행 flush는 새 행을 만들지 않는다")
    void rejectsLateSaveAcrossTwoRepositoryInstances() {
        var original = login(AuthDomain.ADMIN, anonymous(AuthDomain.ADMIN));
        var loaded = second.load(AuthDomain.ADMIN, original.ref().sessionId());
        tx.executeWithoutResult(status -> first.delete(request(AuthDomain.ADMIN), original.ref()));
        var late = tx.execute(status -> second.updateExisting(request(AuthDomain.ADMIN), loaded.ref(),
                new SessionDelta(Instant.now(), "늦은 이름", "en", Set.of("MENU:R"), false)));
        assertThat(late.outcome()).isEqualTo(Outcome.STALE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES",Integer.class)).isZero();
    }

    @Test @DisplayName("TC-30117/30118: DB 만료 시각은 청소와 무관하게 집행한다")
    void databaseExpiryAndAttributeFailureAreAtomic() {
        var original = anonymous(AuthDomain.PORTAL);
        jdbc.update("UPDATE AI_PORTAL_SESSION SET EXPIRY_TIME=0");
        assertThat(second.load(AuthDomain.PORTAL,original.ref().sessionId()).outcome()).isEqualTo(Outcome.EXPIRED);
        assertThat(tx.execute(status -> second.prepareCsrf(request(AuthDomain.PORTAL,RequestPersistenceState.Capability.PREPARE_CSRF), original.ref())).outcome()).isEqualTo(Outcome.STALE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_PORTAL_SESSION",Integer.class)).isEqualTo(1);
    }

    @Test @DisplayName("TC-30120: commit UNKNOWN·응답 실패에 재발급·보상 쓰기하지 않는다")
    void unknownAndResponseFailureCannotRepeatDelivery() {
        var request = request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
        var created = first.inTransaction(request,() -> first.createAnonymous(request,SessionAdmissions.anonymous(request,Instant.now().plusSeconds(300))));
        assertThatThrownBy(() -> created.csrf().emit(request,(d,s,c)->{throw new IllegalStateException("응답 단절");})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.csrf().emit(request,(d,s,c)->fail("재발급"))).isInstanceOf(IllegalStateException.class);
        assertThat(first.load(AuthDomain.ADMIN,created.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        var unknown = request(AuthDomain.ADMIN);
        unknown.beginMutation(false);
        unknown.complete(RequestPersistenceState.Completion.UNKNOWN);
        assertThat(unknown.canFlush()).isFalse();
        assertThat(unknown.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
        assertThat(created.csrf().toString()).doesNotContain(storedSecret(created),created.ref().sessionId());
    }

    @Test @DisplayName("TC-30117: 정확 카탈로그·쿠키·Origin·CSRF와 닫힌 Portal 슬롯")
    void exactHttpBoundaryAndCookieIsolation() throws Exception {
        var catalog = new ExactApiCatalog(List.of(
                operation("ADMIN_CSRF","GET","/api/public/system/auth/getCsrf",AuthDomain.ADMIN,true,true),
                operation("ADMIN_WRITE","POST","/api/service/system/item/save",AuthDomain.ADMIN,false,false),
                operation("PORTAL_CSRF","GET","/api/public/portal/auth/getCsrf",AuthDomain.PORTAL,true,true)));
        var filter = AuthBoundaryAssembly.create(first,catalog,"http://localhost:18080",true,false,binding((o,c,r,command)->command.run()),responses()).filter();
        var prepare = new MockHttpServletRequest("GET","/api/public/system/auth/getCsrf");
        var response = new MockHttpServletResponse();
        filter.doFilter(prepare,response,(req,res)->{});
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("csrfHeaderName").doesNotContain("sessionId","csrfToken");
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(v->v.startsWith("AIHUB_ADMIN_SESSION="))
                .anyMatch(v->v.startsWith("AIHUB_ADMIN_CSRF="));
        for(String path:List.of("/api/public/system/auth/getCsrf/extra","/api/public/system/auth/%67etCsrf","/internal/debug")) {
            var denied = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET",path),denied,(req,res)->fail("미등록 통과"));
            assertThat(denied.getStatus()).isEqualTo(403);
        }
        var portal = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET","/api/public/portal/auth/getCsrf"),portal,(req,res)->fail("미검수 Portal"));
        assertThat(portal.getStatus()).isEqualTo(403);
        var cross = new MockHttpServletRequest("POST","/api/service/system/item/save");
        cross.addHeader("Origin","https://evil.example");
        cross.addHeader("X-AIHUB-PORTAL-CSRF","forged");
        var denied = new MockHttpServletResponse();
        filter.doFilter(cross,denied,(req,res)->fail("위조 요청"));
        assertThat(denied.getStatus()).isIn(401,403);
    }

    @Test @DisplayName("TC-30117: 정상 인증의 Origin·CSRF·제한 수준을 각각 집행한다")
    void authenticatedOriginCsrfAndRestrictedLevel() throws Exception {
        var logged = login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));
        String token=storedSecret(logged);
        var catalog=new ExactApiCatalog(List.of(operation("WRITE","POST","/api/service/system/item/save",AuthDomain.ADMIN,false,false)));
        var callbacks=new AtomicInteger();
        var filter=AuthBoundaryAssembly.create(first,catalog,"http://localhost:18080",true,false,binding((o,c,r,command)->{
            assertThat(c.identity().principal().subjectId()).isEqualTo("7");
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("7");
            callbacks.incrementAndGet();command.run();
        }),responses()).filter();
        for(int mode=0;mode<5;mode++) {
            var request=new MockHttpServletRequest("POST","/api/service/system/item/save");
            request.setCookies(new Cookie("AIHUB_ADMIN_SESSION",logged.ref().sessionId()));
            request.addHeader("Origin",mode==1?"https://evil.example":"http://localhost:18080");
            request.addHeader(mode==2?"X-AIHUB-PORTAL-CSRF":"X-AIHUB-ADMIN-CSRF",mode==3?"wrong":token);
            if(mode==4)request.setCookies(new Cookie("AIHUB_ADMIN_SESSION",logged.ref().sessionId()),new Cookie("AIHUB_ADMIN_SESSION",logged.ref().sessionId()));
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->res.getWriter().write("accepted"));
            assertThat(response.getStatus()).isEqualTo(mode==0?200:403);
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(((ThreadLocal<?>)org.springframework.test.util.ReflectionTestUtils.getField(filter,"activeRequest")).get()).isNull();
            var adapters=(java.util.Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(filter,"adapters");
            for(Object adapter:adapters.values())assertThat(((ThreadLocal<?>)org.springframework.test.util.ReflectionTestUtils.getField(adapter,"original")).get()).isNull();
        }
        assertThat(callbacks).hasValue(1);
        var entry=anonymous(AuthDomain.ADMIN);
        var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.LOGIN);
        var restricted=new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.PASSWORD_CHANGE_REQUIRED,"LOCAL",null,Instant.now(),"관리자","ko");
        var session=first.inTransaction(request,()->first.createAuthorized(request,producers.get(AuthDomain.ADMIN).login(request,entry.ref(),restricted,Instant.now().plusSeconds(60)),Instant.now().plusSeconds(300)));
        var http=new MockHttpServletRequest("POST","/api/service/system/item/save");
        http.setCookies(new Cookie("AIHUB_ADMIN_SESSION",session.ref().sessionId()));
        var response=new MockHttpServletResponse();
        filter.doFilter(http,response,(req,res)->fail("제한 세션의 업무 호출"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test @DisplayName("TC-30116/30117: 잘못된 영역·중복·범위·트랜잭션 배선은 기동 실패")
    void rejectsMiswiringAndMissingCatalogueMetadata() {
        var op=operation("READ","GET","/api/common/system/item/list",AuthDomain.ADMIN,false,false);
        assertThatThrownBy(()->new ExactApiCatalog(List.of(op,op))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->operation("BAD","GET","/api/common/system/item/list",AuthDomain.PORTAL,false,false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ExactApiCatalog.Operation("BAD","POST","/api/service/system/item/save",AuthDomain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),null,false,RequestPersistenceState.Capability.READ,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ExactApiCatalog.Operation("BAD","POST","/api/public/system/auth/login",AuthDomain.ADMIN,Set.of(AuthLevel.ANONYMOUS),null,true,RequestPersistenceState.Capability.LOGIN,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JdbcSessionPersistence(dataSource,new DataSourceTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:wrong","sa","")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AuthBoundaryAssembly.create(first,new ExactApiCatalog(List.of(op)),"http://localhost:18080",false,false,binding((o,c,r,cmd)->cmd.run()),responses())).isInstanceOf(IllegalArgumentException.class);
        var assembly=AuthBoundaryAssembly.create(first,new ExactApiCatalog(List.of(op)),"https://hub.example",false,false,binding((o,c,r,cmd)->cmd.run()),responses());
        assertThatThrownBy(()->AuthBoundaryAssembly.create(first,new ExactApiCatalog(List.of(op)),"https://hub.example",false,false,
                new AuthBoundaryAssembly.AuthorityBinding(dataSource,new DataSourceTransactionManager(dataSource),(o,c,r,cmd)->cmd.run()),responses())).isInstanceOf(IllegalArgumentException.class);
        var beans=new DefaultListableBeanFactory();
        assertThatThrownBy(()->assembly.verifyInstallation(beans,new MockServletContext())).isInstanceOf(IllegalStateException.class);
        beans.registerSingleton("springSessionRepositoryFilter",new Object());
        assertThatThrownBy(()->assembly.verifyInstallation(beans,new MockServletContext())).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("TC-30116/30120: 속성 저장 장애는 부모 행과 발급까지 롤백")
    void attributeFailureRollsBackSessionCreation() {
        jdbc.execute("ALTER TABLE SPRING_SESSION_ATTRIBUTES ADD CONSTRAINT AUTH307_BLOCK CHECK (ATTRIBUTE_NAME <> 'ADMIN_CSRF')");
        var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
        assertThatThrownBy(()->first.inTransaction(request,()->first.createAnonymous(request,SessionAdmissions.anonymous(request,Instant.now().plusSeconds(300)))))
                .isInstanceOf(org.springframework.transaction.TransactionException.class);
        assertThat(request.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES",Integer.class)).isZero();
    }

    @Test @DisplayName("TC-30117/30118: 앱 시계상 과거인 세션도 DB 유효 기한이면 삭제하지 않는다")
    void usesDatabaseExpiryWithoutSpringSessionClockDeletion() {
        var original=anonymous(AuthDomain.ADMIN);
        jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME=0,MAX_INACTIVE_INTERVAL=1");
        assertThat(first.load(AuthDomain.ADMIN,original.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        assertThat(second.load(AuthDomain.ADMIN,original.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
    }

    @Test @DisplayName("TC-30117: 비밀 holder와 참조의 자동 JSON에 원값이 없다")
    void secretAndReferenceDoNotSerialize() {
        var original=anonymous(AuthDomain.ADMIN);
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(original.csrf())).isEqualTo("{}");
        assertThat(mapper.writeValueAsString(original.ref())).isEqualTo("{}");
        assertThat(original.toString()).doesNotContain(original.ref().sessionId());
    }

    @Test @DisplayName("TC-30116~30120: 실제 Tomcat HTTP에서 커밋 뒤 로그인·회전·격리·롤백")
    void actualHttpLoginRotationRollbackAndEncodedRoutes() throws Exception {
        int port;
        try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        String origin="http://127.0.0.1:"+port;
        var catalog=new ExactApiCatalog(List.of(
                operation("CSRF","GET","/api/public/system/auth/getCsrf",AuthDomain.ADMIN,true,true),
                new ExactApiCatalog.Operation("LOGIN","POST","/api/public/system/auth/login",AuthDomain.ADMIN,Set.of(AuthLevel.values()),null,true,RequestPersistenceState.Capability.LOGIN,producers.get(AuthDomain.ADMIN)),
                new ExactApiCatalog.Operation("ROTATE","POST","/api/common/system/auth/changePassword",AuthDomain.ADMIN,Set.of(AuthLevel.AUTHENTICATED,AuthLevel.PASSWORD_CHANGE_REQUIRED),null,true,RequestPersistenceState.Capability.PASSWORD_CHANGE,producers.get(AuthDomain.ADMIN)),
                new ExactApiCatalog.Operation("LOGOUT","POST","/api/common/system/auth/logout",AuthDomain.ADMIN,Set.of(AuthLevel.AUTHENTICATED,AuthLevel.PASSWORD_CHANGE_REQUIRED),null,true,RequestPersistenceState.Capability.LOGOUT,null),
                operation("WRITE","POST","/api/service/system/item/save",AuthDomain.ADMIN,false,false)));
        jdbc.execute("CREATE TABLE AUTH307_EFFECT (VALUE_TEXT VARCHAR(30))");
        var assembly=AuthBoundaryAssembly.create(first,catalog,origin,true,false,binding((o,current,state,command)->command.run()),responses());
        var server=new org.apache.catalina.startup.Tomcat();
        var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/auth307-http",UUID.randomUUID().toString())).toAbsolutePath();
        server.setBaseDir(directory.toString());server.setPort(port);server.getConnector().setProperty("address","127.0.0.1");
        var context=server.addContext("",directory.toString());
        var definition=new org.apache.tomcat.util.descriptor.web.FilterDef();
        definition.setFilterName("authBoundary");definition.setFilterClass(AuthBoundaryFilter.class.getName());definition.setFilter(assembly.filter());context.addFilterDef(definition);
        var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("authBoundary");mapping.addURLPattern("/*");context.addFilterMap(mapping);
        org.apache.catalina.startup.Tomcat.addServlet(context,"probe",new jakarta.servlet.http.HttpServlet(){
            @Override protected void service(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res)throws java.io.IOException {
                var state=(RequestPersistenceState)req.getAttribute(AuthBoundaryFilter.REQUEST);
                var current=(Result)req.getAttribute(AuthBoundaryFilter.SESSION);
                if(req.getRequestURI().endsWith("/login") || req.getRequestURI().endsWith("/changePassword")) {
                    if(!"correct".equals(req.getParameter("password")))throw new AuthBoundaryAssembly.Rejected(401);
                    var identity=new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.AUTHENTICATED,"LOCAL",null,Instant.now(),"관리자","ko");
                    Result result=state.capability()==RequestPersistenceState.Capability.LOGIN?
                            first.createAuthorized(state,producers.get(AuthDomain.ADMIN).login(state,current.ref(),identity,Instant.now().plusSeconds(30)),Instant.now().plusSeconds(600)):
                            first.rotate(state,current.ref(),producers.get(AuthDomain.ADMIN).rotation(state,current.ref(),identity,Instant.now().plusSeconds(30)),Instant.now().plusSeconds(600));
                    assertThat(state.state()).isEqualTo(RequestPersistenceState.State.PENDING_COMMIT);
                    AuthBoundaryAssembly.respondAfterCommit(req,result);
                    if("redirect".equals(req.getParameter("early")))res.sendRedirect("/escape");
                    if("error".equals(req.getParameter("early")))res.sendError(403);
                    if("flush".equals(req.getParameter("early")))res.flushBuffer();
                    if("yes".equals(req.getParameter("rollback")))throw new AuthBoundaryAssembly.Rejected(503);
                    res.getWriter().write("authenticated");
                } else if(state.capability()==RequestPersistenceState.Capability.LOGOUT) {
                    AuthBoundaryAssembly.respondAfterCommit(req,first.delete(state,current.ref()));res.getWriter().write("logged-out");
                } else {
                    assertThat(req.getSession(false)).isNotNull();
                    jdbc.update("INSERT INTO AUTH307_EFFECT(VALUE_TEXT) VALUES ('accepted')");
                    res.getWriter().write("accepted");
                }
            }
        });context.addServletMappingDecoded("/*","probe");
        try {
            server.start();assembly.verifyInstallation(new DefaultListableBeanFactory(),context.getServletContext());
            var client=java.net.http.HttpClient.newHttpClient();
            var prepared=send(client,origin,"GET","/api/public/system/auth/getCsrf",null,null,null);
            assertThat(prepared.statusCode()).isEqualTo(200);
            var initial=cookies(prepared);
            var rejected=send(client,origin,"POST","/api/public/system/auth/login",initial,initial.get("AIHUB_ADMIN_CSRF"),origin,"password=correct&rollback=yes");
            assertThat(rejected.statusCode()).isEqualTo(503);assertThat(rejected.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(rejected.body()).contains("\"success\":false","AUTH_CONTEXT_UNAVAILABLE","\"traceId\"");
            assertThat(prepared.body()).contains("\"success\":true","\"data\"","csrfHeaderName","\"traceId\"");
            assertThat(prepared.body()).doesNotContain(initial.get("AIHUB_ADMIN_SESSION"),initial.get("AIHUB_ADMIN_CSRF"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
            for(String early:List.of("redirect","error","flush")) {
                var premature=send(client,origin,"POST","/api/public/system/auth/login",initial,initial.get("AIHUB_ADMIN_CSRF"),origin,"password=correct&early="+early);
                assertThat(premature.statusCode()).isEqualTo(503);assertThat(premature.headers().allValues("Set-Cookie")).isEmpty();
                assertThat(premature.headers().firstValue("Location")).isEmpty();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
            }
            var logged=send(client,origin,"POST","/api/public/system/auth/login",initial,initial.get("AIHUB_ADMIN_CSRF"),origin,"password=correct");
            assertThat(logged.statusCode()).isEqualTo(200);assertThat(logged.body()).isEqualTo("authenticated");
            var authorized=cookies(logged);assertThat(authorized.get("AIHUB_ADMIN_SESSION")).isNotEqualTo(initial.get("AIHUB_ADMIN_SESSION"));
            var denied=send(client,origin,"POST","/api/service/system/item/save",authorized,authorized.get("AIHUB_ADMIN_CSRF"),"https://evil.example");
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(send(client,origin,"POST","/api/service/system/item/save",authorized,authorized.get("AIHUB_ADMIN_CSRF"),origin).statusCode()).isEqualTo(200);
            var rotated=send(client,origin,"POST","/api/common/system/auth/changePassword",authorized,authorized.get("AIHUB_ADMIN_CSRF"),origin,"password=correct");
            assertThat(rotated.statusCode()).isEqualTo(200);var fresh=cookies(rotated);
            assertThat(send(client,origin,"POST","/api/service/system/item/save",fresh,authorized.get("AIHUB_ADMIN_CSRF"),origin).statusCode()).isEqualTo(403);
            assertThat(send(client,origin,"POST","/api/service/system/item/save",fresh,fresh.get("AIHUB_ADMIN_CSRF"),origin).statusCode()).isEqualTo(200);
            assertThat(send(client,origin,"POST","/api/service/system/item/save",authorized,authorized.get("AIHUB_ADMIN_CSRF"),origin).statusCode()).isEqualTo(401);
            for(String path:List.of("/api","/internal","/%61pi/service/system/item/save","/api/service/system/item/save;alias"))
                assertThat(send(client,origin,"GET",path,null,null,null).statusCode()).isIn(400,403);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_EFFECT",Integer.class)).isEqualTo(2);
            var portal=anonymous(AuthDomain.PORTAL);fresh.put("AIHUB_PORTAL_SESSION",portal.ref().sessionId());
            var logout=send(client,origin,"POST","/api/common/system/auth/logout",fresh,fresh.get("AIHUB_ADMIN_CSRF"),origin);
            assertThat(logout.statusCode()).isEqualTo(200);
            assertThat(logout.headers().allValues("Set-Cookie")).hasSize(2).allMatch(value->value.contains("Max-Age=0")).noneMatch(value->value.startsWith("AIHUB_PORTAL_"));
            assertThat(first.load(AuthDomain.ADMIN,fresh.get("AIHUB_ADMIN_SESSION")).outcome()).isEqualTo(Outcome.ABSENT);
            assertThat(first.load(AuthDomain.PORTAL,portal.ref().sessionId()).outcome()).isEqualTo(Outcome.CURRENT);
        } finally {server.stop();server.destroy();}
    }

    private java.net.http.HttpResponse<String> send(java.net.http.HttpClient client,String origin,String method,String path,
            java.util.Map<String,String> cookies,String csrf,String requestOrigin)throws Exception {
        return send(client,origin,method,path,cookies,csrf,requestOrigin,null);
    }
    private java.net.http.HttpResponse<String> send(java.net.http.HttpClient client,String origin,String method,String path,
            java.util.Map<String,String> cookies,String csrf,String requestOrigin,String body)throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+path)).method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if(body!=null)request.header("Content-Type","application/x-www-form-urlencoded");
        if(cookies!=null)request.header("Cookie",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
        if(csrf!=null)request.header("X-AIHUB-ADMIN-CSRF",csrf);
        if(requestOrigin!=null)request.header("Origin",requestOrigin);
        return client.send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    private java.util.Map<String,String> cookies(java.net.http.HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream().map(value->value.split(";",2)[0].split("=",2))
                .collect(java.util.stream.Collectors.toMap(parts->parts[0],parts->parts[1]));
    }
    private BoundaryResponseWriter responses() {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var messages=org.mockito.Mockito.mock(com.aiagent.api.error.ErrorMessages.class);
        org.mockito.Mockito.when(messages.of(org.mockito.ArgumentMatchers.any())).thenAnswer(call->((com.aiagent.core.error.ErrorCode)call.getArgument(0)).defaultMessage());
        return new BoundaryResponseWriter(mapper,new com.aiagent.api.error.ApiErrorWriter(mapper,messages));
    }
    private AuthBoundaryAssembly.AuthorityBinding binding(AuthBoundaryAssembly.AuthorityBoundary authority) {
        return new AuthBoundaryAssembly.AuthorityBinding(dataSource,tx.getTransactionManager(),authority);
    }

    @Test @DisplayName("TC-30117/30118: 손상 인증 속성에 새 익명 세션으로 폴백하지 않는다")
    void corruptedAttributesDoNotBecomeAnonymous()throws Exception {
        var original=anonymous(AuthDomain.ADMIN);
        jdbc.update("UPDATE SPRING_SESSION_ATTRIBUTES SET ATTRIBUTE_BYTES=?",new byte[]{1,2,3});
        assertThat(first.load(AuthDomain.ADMIN,original.ref().sessionId()).outcome()).isEqualTo(Outcome.REJECTED);
        var catalog=new ExactApiCatalog(List.of(operation("CSRF","GET","/api/public/system/auth/getCsrf",AuthDomain.ADMIN,true,true)));
        var filter=AuthBoundaryAssembly.create(first,catalog,"https://hub.example",false,false,binding((o,c,r,cmd)->cmd.run()),responses()).filter();
        var request=new MockHttpServletRequest("GET","/api/public/system/auth/getCsrf");
        request.setCookies(new Cookie("AIHUB_ADMIN_SESSION",original.ref().sessionId()));
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("손상 세션 허용"));
        assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
    }

    @Test @DisplayName("TC-30116: 실제 JpaTransactionManager의 업무 엔티티·JDBC 세션이 같은 커밋/롤백")
    void jpaAndSessionSharePhysicalTransaction() {
        jdbc.execute("CREATE TABLE AUTH307_CREDENTIAL(ID VARCHAR(80) PRIMARY KEY,VALUETEXT VARCHAR(80))");
        var factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);factory.setPackagesToScan("com.aiagent.api.auth.boundary.fixture");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","none"));factory.afterPropertiesSet();
        var emf=factory.getObject();
        try {
            var manager=new org.springframework.orm.jpa.JpaTransactionManager(emf);manager.setDataSource(dataSource);
            var store=new JdbcSessionPersistence(dataSource,manager);
            var jpaTx=new TransactionTemplate(manager);
            for(boolean rollback:new boolean[]{true,false}) {
                var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
                var result=jpaTx.execute(status->{
                    store.attachAttempt(request);
                    var em=org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
                    em.persist(new com.aiagent.api.auth.boundary.fixture.Auth307Credential(rollback?"rollback":"commit","synthetic"));em.flush();
                    Result created=store.createAnonymous(request,SessionAdmissions.anonymous(request,Instant.now().plusSeconds(300)));
                    assertThat(created.outcome()).isEqualTo(Outcome.CREATED);
                    if(rollback)status.setRollbackOnly();return created;
                });
                assertThat(store.load(AuthDomain.ADMIN,result.ref().sessionId()).outcome()).isEqualTo(rollback?Outcome.ABSENT:Outcome.CURRENT);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(rollback?0:1);
            }
            AuthBoundaryAssembly.create(store,new ExactApiCatalog(List.of(operation("CSRF","GET","/api/public/system/auth/getCsrf",AuthDomain.ADMIN,true,true))),
                    "https://hub.example",false,false,new AuthBoundaryAssembly.AuthorityBinding(dataSource,manager,(o,c,r,cmd)->cmd.run()),responses());
        } finally {factory.destroy();}
    }

    @Test @DisplayName("TC-30117: 저장 전 롤백된 자격 증거는 같은 요청의 다음 트랜잭션에서도 거부")
    void rollsBackAdmissionBeforeFirstSessionWrite() {
        var entry=anonymous(AuthDomain.ADMIN);
        var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.LOGIN);
        var proof=new java.util.concurrent.atomic.AtomicReference<SessionAdmissions.CredentialAdmission>();
        assertThatThrownBy(()->first.inTransaction(request,()->{
            proof.set(producers.get(AuthDomain.ADMIN).login(request,entry.ref(),
                    new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.AUTHENTICATED,"LOCAL",null,Instant.now(),"n","ko"),Instant.now().plusSeconds(60)));
            throw new IllegalStateException("저장 전 검증 이후 장애");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(request.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
        assertThat(first.createAuthorized(request,proof.get(),Instant.now().plusSeconds(300)).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
        assertThat(first.load(AuthDomain.ADMIN,entry.ref().sessionId()).identity()).isNull();
    }

    @Test @DisplayName("TC-30116/30117: 자동 필터·무자격/Primary 저장소·중복 영역 필터를 설치하지 않는다")
    void installationAssertionsInspectActualRegistrationsAndBeans() {
        var catalog=new ExactApiCatalog(List.of(operation("READ","GET","/api/common/system/list",AuthDomain.ADMIN,false,false)));
        var assembly=AuthBoundaryAssembly.create(first,catalog,"https://hub.example",false,false,binding((o,c,r,cmd)->cmd.run()),responses());
        var servlet=org.mockito.Mockito.mock(jakarta.servlet.ServletContext.class);
        var own=org.mockito.Mockito.mock(jakarta.servlet.FilterRegistration.class);
        org.mockito.Mockito.when(own.getClassName()).thenReturn(AuthBoundaryFilter.class.getName());
        org.mockito.Mockito.doReturn(java.util.Map.of("authBoundary",own)).when(servlet).getFilterRegistrations();
        var beans=new DefaultListableBeanFactory();assembly.verifyInstallation(beans,servlet);
        var automatic=org.mockito.Mockito.mock(jakarta.servlet.FilterRegistration.class);
        org.mockito.Mockito.when(automatic.getClassName()).thenReturn(org.springframework.session.web.http.SessionRepositoryFilter.class.getName());
        org.mockito.Mockito.doReturn(java.util.Map.of("authBoundary",own,"automatic",automatic)).when(servlet).getFilterRegistrations();
        assertThatThrownBy(()->assembly.verifyInstallation(beans,servlet)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.doReturn(java.util.Map.of("authBoundary",own,"second",own)).when(servlet).getFilterRegistrations();
        assertThatThrownBy(()->assembly.verifyInstallation(beans,servlet)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.doReturn(java.util.Map.of("authBoundary",own)).when(servlet).getFilterRegistrations();
        beans.registerSingleton("unqualifiedPortalRepository",new org.springframework.session.MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));
        assertThatThrownBy(()->assembly.verifyInstallation(beans,servlet)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("TC-30118: CSRF 속성 누락은 같은 세션 안에서만 재발급한다")
    void missingCsrfIsPreparedOnExistingGeneration() {
        var original=login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));
        String old=storedSecret(original);
        jdbc.update("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME='ADMIN_CSRF'");
        var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PREPARE_CSRF);
        var issued=first.prepareCsrf(request,original.ref());
        assertThat(issued.outcome()).isEqualTo(Outcome.PREPARED);assertThat(issued.ref()).isEqualTo(original.ref());
        assertThat(issued.identity()).isEqualTo(original.identity());assertThat(secret(issued,request)).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
    }

    @Test @DisplayName("TC-30116/30117: Delta는 표시/캐시만 갱신하고 참조의 부모ID·영역을 검증한다")
    void structuredDeltaCannotReplaceAuthenticationOrParent() {
        var original=login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));String csrf=storedSecret(original);
        var forged=new com.aiagent.core.security.SessionRef(AuthDomain.ADMIN,"different-primary",original.ref().sessionId());
        var delta=new SessionDelta(Instant.now().plusSeconds(999999),"새 이름","en",Set.of("MENU:R"),true);
        assertThat(first.updateExisting(request(AuthDomain.ADMIN),forged,delta).outcome()).isEqualTo(Outcome.STALE);
        assertThat(first.updateExisting(request(AuthDomain.PORTAL),original.ref(),delta).outcome()).isEqualTo(Outcome.REJECTED);
        var updated=first.updateExisting(request(AuthDomain.ADMIN),original.ref(),delta);
        assertThat(updated.outcome()).isEqualTo(Outcome.APPLIED);assertThat(updated.identity().principal()).isEqualTo(original.identity().principal());
        assertThat(updated.identity().authLevel()).isEqualTo(original.identity().authLevel());assertThat(updated.identity().displayName()).isEqualTo("새 이름");
        assertThat(updated.identity().locale()).isEqualTo("en");assertThat(storedSecret(updated)).isEqualTo(csrf);
        assertThat(updated.expiresAt()).isBefore(Instant.now().plusSeconds(2000));
        Session stored=first.rawSession(AuthDomain.ADMIN,original.ref().sessionId());
        assertThat((Set<String>)stored.getAttribute("ADMIN_AUTHORITY_CACHE")).containsExactly("MENU:R");
        assertThat((Boolean)stored.getAttribute("ADMIN_DIRTY_SEEN")).isTrue();
    }

    @Test @DisplayName("TC-30116: 같은 TM의 숨은 REQUIRES_NEW도 업무 callback 전에 거부한다")
    void hiddenRequiresNewCannotCommitBusinessBeforeSession()throws Exception {
        var logged=login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));
        jdbc.execute("CREATE TABLE AUTH307_EFFECT(VALUE_TEXT VARCHAR(30))");
        var independent=new TransactionTemplate(tx.getTransactionManager());independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var catalogue=new ExactApiCatalog(List.of(operation("READ","GET","/api/common/system/probe",AuthDomain.ADMIN,false,false)));
        var assembly=AuthBoundaryAssembly.create(first,catalogue,"https://hub.example",false,false,binding((o,c,r,command)->
                independent.executeWithoutResult(status->{try{command.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("시험 명령 실패",e);}})),responses());
        var request=new MockHttpServletRequest("GET","/api/common/system/probe");request.setCookies(new Cookie("AIHUB_ADMIN_SESSION",logged.ref().sessionId()));
        var response=new MockHttpServletResponse();
        assembly.filter().doFilter(request,response,(req,res)->jdbc.update("INSERT INTO AUTH307_EFFECT(VALUE_TEXT) VALUES ('wrong')"));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_EFFECT",Integer.class)).isZero();
    }

    @Test @DisplayName("TC-30116/30117: 실제 HTTP 양 영역의 동일 주체ID·cookie/CSRF 교차를 격리")
    void actualHttpBothDomainsUseSeparateCookiesAndContexts()throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        String origin="http://127.0.0.1:"+port;
        var operations=new java.util.ArrayList<ExactApiCatalog.Operation>();
        for(AuthDomain domain:AuthDomain.values()) {
            String module=domain==AuthDomain.ADMIN?"system":"portal";
            operations.add(operation(domain+"_CSRF","GET","/api/public/"+module+"/auth/getCsrf",domain,true,true));
            operations.add(new ExactApiCatalog.Operation(domain+"_LOGIN","POST","/api/public/"+module+"/auth/login",domain,Set.of(AuthLevel.values()),null,true,RequestPersistenceState.Capability.LOGIN,producers.get(domain)));
            operations.add(operation(domain+"_IDENTITY","GET","/api/common/"+module+"/identity",domain,false,false));
        }
        var assembly=AuthBoundaryAssembly.create(first,new ExactApiCatalog(operations),origin,true,true,binding((o,c,r,cmd)->cmd.run()),responses());
        var server=new org.apache.catalina.startup.Tomcat();
        var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/auth307-http",UUID.randomUUID().toString())).toAbsolutePath();
        server.setBaseDir(directory.toString());server.setPort(port);server.getConnector().setProperty("address","127.0.0.1");
        var context=server.addContext("",directory.toString());
        var definition=new org.apache.tomcat.util.descriptor.web.FilterDef();definition.setFilterName("authBoundary");definition.setFilterClass(AuthBoundaryFilter.class.getName());definition.setFilter(assembly.filter());context.addFilterDef(definition);
        var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("authBoundary");mapping.addURLPattern("/*");context.addFilterMap(mapping);
        org.apache.catalina.startup.Tomcat.addServlet(context,"probe",new jakarta.servlet.http.HttpServlet(){
            @Override protected void service(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res)throws java.io.IOException {
                var state=(RequestPersistenceState)req.getAttribute(AuthBoundaryFilter.REQUEST);var current=(Result)req.getAttribute(AuthBoundaryFilter.SESSION);
                if(state.capability()==RequestPersistenceState.Capability.LOGIN) {
                    if(!"correct".equals(req.getParameter("password")))throw new AuthBoundaryAssembly.Rejected(401);
                    var identity=new Identity(new PrincipalRef(state.domain(),"7"),AuthLevel.AUTHENTICATED,"LOCAL",state.domain()==AuthDomain.PORTAL?1L:null,Instant.now(),"합성 사용자","ko");
                    var created=first.createAuthorized(state,producers.get(state.domain()).login(state,current.ref(),identity,Instant.now().plusSeconds(30)),Instant.now().plusSeconds(600));
                    AuthBoundaryAssembly.respondAfterCommit(req,created);
                }
                res.getWriter().write(state.domain()+":7");
            }
        });context.addServletMappingDecoded("/*","probe");
        try {
            server.start();var client=java.net.http.HttpClient.newHttpClient();
            var sessions=new java.util.EnumMap<AuthDomain,java.util.Map<String,String>>(AuthDomain.class);
            for(AuthDomain domain:AuthDomain.values()) {
                String module=domain==AuthDomain.ADMIN?"system":"portal";
                var prepared=send(client,origin,"GET","/api/public/"+module+"/auth/getCsrf",null,null,null);
                assertThat(prepared.statusCode()).isEqualTo(200);
                var anonymous=cookies(prepared);
                var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+"/api/public/"+module+"/auth/login"))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("password=correct")).header("Content-Type","application/x-www-form-urlencoded")
                        .header("Origin",origin).header("Cookie",anonymous.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; ")))
                        .header("X-AIHUB-"+domain+"-CSRF",anonymous.get("AIHUB_"+domain+"_CSRF")).build();
                var logged=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(logged.statusCode()).isEqualTo(200);assertThat(logged.body()).isEqualTo(domain+":7");sessions.put(domain,cookies(logged));
            }
            var combined=new java.util.HashMap<String,String>();sessions.values().forEach(combined::putAll);
            for(AuthDomain domain:AuthDomain.values()) {
                String module=domain==AuthDomain.ADMIN?"system":"portal";AuthDomain other=domain==AuthDomain.ADMIN?AuthDomain.PORTAL:AuthDomain.ADMIN;
                assertThat(send(client,origin,"GET","/api/common/"+module+"/identity",combined,null,null).body()).isEqualTo(domain+":7");
                assertThat(send(client,origin,"GET","/api/common/"+module+"/identity",sessions.get(other),null,null).statusCode()).isEqualTo(401);
                var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+"/api/public/"+module+"/auth/login"))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("password=correct")).header("Content-Type","application/x-www-form-urlencoded")
                        .header("Origin",origin).header("Cookie",combined.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; ")))
                        .header("X-AIHUB-"+other+"-CSRF",combined.get("AIHUB_"+other+"_CSRF")).build();
                assertThat(client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            }
        } finally{server.stop();server.destroy();}
    }

    @Test @DisplayName("TC-30116/30117: DB 세션 메타와 명시 거부 결과를 보존한다")
    void databaseMetadataAndRejectionsAreExplicit() {
        var original=anonymous(AuthDomain.ADMIN);
        jdbc.update("UPDATE SPRING_SESSION SET CREATION_TIME=1,LAST_ACCESS_TIME=2,MAX_INACTIVE_INTERVAL=37");
        Session raw=first.rawSession(AuthDomain.ADMIN,original.ref().sessionId());
        assertThat(raw.getCreationTime()).isEqualTo(Instant.ofEpochMilli(1));assertThat(raw.getLastAccessedTime()).isEqualTo(Instant.ofEpochMilli(2));
        assertThat(raw.getMaxInactiveInterval()).isEqualTo(java.time.Duration.ofSeconds(37));assertThat(raw.isExpired()).isFalse();
        raw.setAttribute("local-view","x");raw.removeAttribute("local-view");assertThat(raw.getAttributeNames()).doesNotContain("local-view");
        assertThat(first.prepareCsrf(request(AuthDomain.ADMIN),original.ref()).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(first.delete(request(AuthDomain.PORTAL),original.ref()).outcome()).isEqualTo(Outcome.REJECTED);
        var expired=request(AuthDomain.PORTAL,RequestPersistenceState.Capability.PREPARE_CSRF);
        assertThat(first.inTransaction(expired,()->first.createAnonymous(expired,SessionAdmissions.anonymous(expired,Instant.now().minusSeconds(1)))).outcome()).isEqualTo(Outcome.REJECTED);
        var current=request(AuthDomain.ADMIN);assertThat(first.delete(current,original.ref()).outcome()).isEqualTo(Outcome.DELETED);
        assertThat(first.delete(request(AuthDomain.ADMIN),original.ref()).outcome()).isEqualTo(Outcome.STALE);
        assertThat(first.updateExisting(current,original.ref(),new SessionDelta(Instant.now(),null,null,Set.of(),false)).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(first.load(AuthDomain.ADMIN,null).outcome()).isEqualTo(Outcome.ABSENT);
    }

    @Test @DisplayName("TC-30117: 정적 자원은 통과하고 디코딩된 API·미등록 문맥은 닫는다")
    void staticResourcesAndDecodedApiCannotMix()throws Exception {
        var catalogue=new ExactApiCatalog(List.of(operation("READ","GET","/api/common/system/probe",AuthDomain.ADMIN,false,false)));
        assertThat(catalogue.entries()).hasSize(1);assertThat(catalogue.entries().iterator().next().anonymous()).isFalse();
        var filter=AuthBoundaryAssembly.create(first,catalogue,"https://hub.example",false,false,binding((o,c,r,cmd)->cmd.run()),responses()).filter();
        var calls=new AtomicInteger();
        filter.doFilter(new MockHttpServletRequest("GET","/index.html"),new MockHttpServletResponse(),(req,res)->calls.incrementAndGet());
        assertThat(calls).hasValue(1);
        for(String path:List.of("/%61pi/common/system/probe","/api","/internal/unknown")) {
            var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET",path),response,(req,res)->fail("미등록 API 통과"));
            assertThat(response.getStatus()).isEqualTo(403);
        }
        var contextPath=new MockHttpServletRequest("GET","/ctx/api/common/system/probe");contextPath.setServletPath("/api/common/system/probe");
        var denied=new MockHttpServletResponse();filter.doFilter(contextPath,denied,(req,res)->fail("미등록 context 경로 통과"));assertThat(denied.getStatus()).isEqualTo(403);
    }

    @Test @DisplayName("TC-30117/30120: raw 인증문맥·세션 회전/종료·직접 cookie는 업무 효과와 함께 거부")
    void rawSessionAndResponseBypassesRollbackBusinessEffect()throws Exception {
        var original=login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));
        jdbc.execute("CREATE TABLE AUTH307_EFFECT(VALUE_TEXT VARCHAR(30))");
        var catalogue=new ExactApiCatalog(List.of(operation("READ","GET","/api/common/system/probe",AuthDomain.ADMIN,false,false)));
        var filter=AuthBoundaryAssembly.create(first,catalogue,"https://hub.example",false,false,binding((o,c,r,cmd)->cmd.run()),responses()).filter();
        for(int mode=0;mode<5;mode++) {
            var request=new MockHttpServletRequest("GET","/api/common/system/probe");request.setCookies(new Cookie("AIHUB_ADMIN_SESSION",original.ref().sessionId()));
            var response=new MockHttpServletResponse();int action=mode;
            filter.doFilter(request,response,(req,res)->{
                jdbc.update("INSERT INTO AUTH307_EFFECT(VALUE_TEXT) VALUES ('must-rollback')");
                var http=(jakarta.servlet.http.HttpServletRequest)req;
                if(action==0)http.getSession(false).setAttribute("SPRING_SECURITY_CONTEXT",new org.springframework.security.core.context.SecurityContextImpl());
                if(action==1){http.getSession(false);http.changeSessionId();}
                if(action==2)http.getSession(false).invalidate();
                if(action==3)((jakarta.servlet.http.HttpServletResponse)res).addCookie(new Cookie("forged","value"));
                if(action==4)http.getSession(false).setAttribute("ADMIN_AUTHORITY_CACHE",java.util.Set.of(7L));
            });
            assertThat(response.getStatus()).isEqualTo(503);assertThat(response.getHeaders("Set-Cookie")).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_EFFECT",Integer.class)).isZero();
            assertThat(first.load(AuthDomain.ADMIN,original.ref().sessionId()).identity().principal().subjectId()).isEqualTo("7");
        }
    }

    @Test @DisplayName("TC-30116/30120: 실제 두 트랜잭션의 부모 행 잠금이 회전·늦은 속성 flush를 직렬화")
    void concurrentRotationAndLateFlush() throws Exception {
        var original=login(AuthDomain.ADMIN,anonymous(AuthDomain.ADMIN));
        var locked=new java.util.concurrent.CountDownLatch(1);
        var finish=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var rotation=executor.submit(()->tx.execute(status->{
                var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PASSWORD_CHANGE);
                var result=first.rotate(request,original.ref(),producers.get(AuthDomain.ADMIN).rotation(request,original.ref(),original.identity(),Instant.now().plusSeconds(30)),Instant.now().plusSeconds(600));
                locked.countDown();
                try{if(!finish.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("경합 시험 시간 초과");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("경합 시험 중단",e);}
                return result;
            }));
            assertThat(locked.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var started=new java.util.concurrent.CountDownLatch(1);
            var late=executor.submit(()->{started.countDown();return second.updateExisting(request(AuthDomain.ADMIN),original.ref(),new SessionDelta(Instant.now(),"old","en",Set.of(),false));});
            assertThat(started.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->late.get(100,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            finish.countDown();
            var current=rotation.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(late.get(10,java.util.concurrent.TimeUnit.SECONDS).outcome()).isEqualTo(Outcome.STALE);
            assertThat(second.load(AuthDomain.ADMIN,current.ref().sessionId()).identity().displayName()).isEqualTo("관리자");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isEqualTo(1);
        } finally {finish.countDown();}
    }

    private String storedSecret(Result result) {
        var request = request(result.ref().domain(),RequestPersistenceState.Capability.PREPARE_CSRF);
        return secret(tx.execute(status -> first.prepareCsrf(request, result.ref())), request);
    }
    private ExactApiCatalog.Operation operation(String id,String method,String path,AuthDomain domain,boolean anonymous,boolean prepare) {
        return new ExactApiCatalog.Operation(id,method,path,domain,anonymous?Set.of(AuthLevel.values()):Set.of(AuthLevel.AUTHENTICATED),
                null,true,prepare?RequestPersistenceState.Capability.PREPARE_CSRF:RequestPersistenceState.Capability.READ,null);
    }

    private String secret(Result result, RequestPersistenceState request) {
        var value = new java.util.concurrent.atomic.AtomicReference<String>();
        result.csrf().emit(request,(domain,session,csrf)->value.set(csrf));
        return value.get();
    }
}
