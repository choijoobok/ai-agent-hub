// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;

class SessionRuntimeTest {
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    @DisplayName("TC-30122/27 회전은기존인증주체에고정되고익명승격/타주체교체를거부한다")
    void rotationCannotChangePrincipalOrPromoteAnonymous(boolean anonymous){
        var entry=anonymous();var existing=anonymous?entry:login(entry,AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(false,1,false));var change=scope(Capability.PASSWORD_CHANGE,existing.cookie);
        var wrong=new Identity(new PrincipalRef(AuthDomain.ADMIN,anonymous?"owner":"other"),AuthLevel.AUTHENTICATED,"LOCAL",null,ledger.now(),"이름","ko");
        assertThatThrownBy(()->runtime.execute(change,()->{jdbc.update("UPDATE CREDENTIAL_FIXTURE SET PWD='forbidden'");change.publish(producer.rotation(change.exchange(),existing.ref,wrong,ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;})).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(SessionV2.Outcome.INVALID_SESSION));
        assertThat(jdbc.queryForObject("SELECT PWD FROM CREDENTIAL_FIXTURE",String.class)).isEqualTo("before");assertThat(scope(Capability.READ,existing.cookie).current()).isPresent();assertThatThrownBy(()->change.emitCookies((d,c,t)->fail("주체변경쿠키"))).isInstanceOf(IllegalStateException.class);
    }
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private java.sql.Connection schema;
    private JdbcSessionAdmissionLedger ledger;
    private SessionRuntime runtime;
    private final UUID epoch=UUID.randomUUID();
    private final SessionAdmissionsV2.Producer producer=SessionAdmissionsV2.producer(AuthDomain.ADMIN,"fixture-admin");
    @BeforeEach void setup()throws Exception{
        source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE SYS_SEQUENCE(SEQ_NAME VARCHAR(255) PRIMARY KEY,NEXT_VAL BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE CREDENTIAL_FIXTURE(ID INT PRIMARY KEY,PWD VARCHAR(100))");jdbc.update("INSERT INTO CREDENTIAL_FIXTURE VALUES(1,'before')");
        schema=source.getConnection();ScriptUtils.executeSqlScript(schema,new FileSystemResource("../bootstrap/src/main/resources/db/changelog/modules/platform/002-auth-session-v2.sql"));
        jdbc.update("INSERT INTO AI_AUTH_DOMAIN_STATE VALUES('ADMIN','JDBC',?,'fixture','ACTIVE',CURRENT_TIMESTAMP)",epoch);
        for(String table:List.of("SPRING_SESSION","AI_PORTAL_SESSION")){
            jdbc.execute("CREATE TABLE "+table+"(PRIMARY_ID CHAR(36) PRIMARY KEY,SESSION_ID CHAR(36) UNIQUE,CREATION_TIME BIGINT NOT NULL,LAST_ACCESS_TIME BIGINT NOT NULL,MAX_INACTIVE_INTERVAL INT NOT NULL,EXPIRY_TIME BIGINT NOT NULL,PRINCIPAL_NAME VARCHAR(100))");
            jdbc.execute("CREATE TABLE "+table+"_ATTRIBUTES(SESSION_PRIMARY_ID CHAR(36) REFERENCES "+table+"(PRIMARY_ID) ON DELETE CASCADE,ATTRIBUTE_NAME VARCHAR(200),ATTRIBUTE_BYTES VARBINARY NOT NULL,PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME))");
        }
        ledger=new JdbcSessionAdmissionLedger(source,manager);var transactions=new SessionTransactions(source,manager);
        runtime=new SessionRuntime(ledger,new JdbcSessionStore(source,manager,Set.of(AuthDomain.ADMIN)),transactions,new Settings(Provider.JDBC,epoch,"fixture"),
                Set.of(AuthDomain.ADMIN),new SessionRuntime.AuthorityBinding(source,manager,(context,exclusive,command)->command.run()),Duration.ofMinutes(30));
    }
    @AfterEach void close()throws Exception{if(schema!=null)schema.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");}
    private ModuleApiCatalog.Operation operation(Capability cap,String action){
        String layer=Set.of(Capability.LOGIN,Capability.PREPARE_CSRF).contains(cap)?"public":"common";
        String name=switch(cap){case PREPARE_CSRF->"getCsrf";case LOGIN->"login";case PASSWORD_CHANGE->"changePassword";case LOGOUT->"logout";default->"getMe";};
        return new ModuleApiCatalog.Operation(name,cap==Capability.READ||cap==Capability.PREPARE_CSRF?"GET":"POST","/api/system/"+layer+"/auth/"+name,
                ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.ANONYMOUS,AuthLevel.PASSWORD_CHANGE_REQUIRED,AuthLevel.AUTHENTICATED),action,action==null,cap,null,null);
    }
    private SessionRuntime.Scope scope(Capability cap,String cookie){return runtime.open(new SessionExchange(AuthDomain.ADMIN,UUID.randomUUID().toString(),cap,producer),operation(cap,null),cookie);}
    private Identity identity(AuthLevel level){return new Identity(new PrincipalRef(AuthDomain.ADMIN,"owner"),level,"LOCAL",null,ledger.now(),"이름","ko");}
    private record Issued(String cookie,String csrf,SessionV2.Ref ref) {}
    private Issued emit(SessionRuntime.Scope scope){
        assertThat(runtime.finish(scope)).isEqualTo(SessionV2.Outcome.DELIVERABLE);
        var cookie=new AtomicReference<String>();var csrf=new AtomicReference<String>();scope.emitCookies((domain,c,t)->{cookie.set(c);csrf.set(t);});
        return new Issued(cookie.get(),csrf.get(),scope.current().orElseThrow().ref());
    }
    private Issued anonymous(){var scope=scope(Capability.PREPARE_CSRF,null);runtime.execute(scope,()->{scope.prepareCsrf();return null;});return emit(scope);}
    private Issued login(Issued entry,AuthLevel level,SessionRuntime.SlotPolicy policy){
        var scope=scope(Capability.LOGIN,entry.cookie);runtime.execute(scope,()->{scope.publish(producer.login(scope.exchange(),entry.ref,identity(level),ledger.now().plusSeconds(60)),policy);return null;});return emit(scope);
    }
    @Test @DisplayName("TC-30122 JDBC익명준비/로그인/회전은원장과payload를결합하고구쿠키를폐기한다")
    void anonymousLoginAndRotation(){
        var entry=anonymous();var user=login(entry,AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(false,1,false));
        assertThat(user.cookie).hasSize(43).isNotEqualTo(entry.cookie);assertThat(user.csrf).isNotEqualTo(entry.csrf);
        assertThatThrownBy(()->scope(Capability.READ,entry.cookie)).isInstanceOf(Failure.class);
        var change=scope(Capability.PASSWORD_CHANGE,user.cookie);runtime.execute(change,()->{jdbc.update("UPDATE CREDENTIAL_FIXTURE SET PWD='after'");change.publish(producer.rotation(change.exchange(),user.ref,identity(AuthLevel.AUTHENTICATED),ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;});
        var rotated=emit(change);assertThat(rotated.ref.grantId()).isEqualTo(user.ref.grantId());assertThat(rotated.ref.generation()).isEqualTo(user.ref.generation()+1);
        assertThatThrownBy(()->scope(Capability.READ,user.cookie)).isInstanceOf(Failure.class);
    }
    @Test @DisplayName("TC-30127 JDBC암호/새grant/payload는같은REQUIREDrollback이며쿠키0")
    void passwordRollbackPreservesOldGeneration(){
        var user=login(anonymous(),AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(false,1,false));var change=scope(Capability.PASSWORD_CHANGE,user.cookie);
        assertThatThrownBy(()->runtime.execute(change,()->{jdbc.update("UPDATE CREDENTIAL_FIXTURE SET PWD='after'");change.publish(producer.rotation(change.exchange(),user.ref,identity(AuthLevel.AUTHENTICATED),ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));throw new IllegalStateException("시험 롤백");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT PWD FROM CREDENTIAL_FIXTURE",String.class)).isEqualTo("before");
        assertThat(scope(Capability.READ,user.cookie).current()).isPresent();
        assertThatThrownBy(()->change.emitCookies((d,c,t)->{})).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("TC-30123 익명10분/한도제외·인증60초미확인예약·첫실제사용뒤일반sliding")
    void firstUseReservationAndAnonymousExclusion(){
        var entry=anonymous();assertThat(ledger.find(entry.ref,false).orElseThrow().confirmDeadline()).isNull();
        var user=login(entry,AuthLevel.PASSWORD_CHANGE_REQUIRED,new SessionRuntime.SlotPolicy(true,1,true));
        assertThat(ledger.find(user.ref,false).orElseThrow().firstUsedAt()).isNull();
        var read=scope(Capability.READ,user.cookie);runtime.execute(read,()->null);runtime.finish(read);
        assertThat(ledger.find(user.ref,false).orElseThrow().firstUsedAt()).isNotNull();
        jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET CONFIRM_DEADLINE_AT=? WHERE GRANT_ID=?",java.sql.Timestamp.from(ledger.now().minusSeconds(1)),user.ref.grantId());
        assertThat(scope(Capability.READ,user.cookie).current()).isPresent();
        var other=anonymous();assertThatThrownBy(()->login(other,AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(true,1,true))).isInstanceOf(Failure.class);
    }
    @Test @DisplayName("TC-30126 쿠키미수신ACTIVE는60초뒤슬롯을풀고새로그인가능")
    void unconfirmedPublicationExpires(){
        var user=login(anonymous(),AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(true,1,true));
        jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET CONFIRM_DEADLINE_AT=? WHERE GRANT_ID=?",java.sql.Timestamp.from(ledger.now().minusSeconds(1)),user.ref.grantId());
        assertThatThrownBy(()->scope(Capability.READ,user.cookie)).isInstanceOf(Failure.class);
        assertThat(login(anonymous(),AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(true,1,true)).cookie).hasSize(43);
    }
    @Test @DisplayName("TC-30129 카탈로그우회직접조립도publicREAD인증수준을무시하지않는다")
    void publicReadCannotBypassLevels(){
        var op=new ModuleApiCatalog.Operation("sensitive","GET","/api/system/public/user/getSensitive",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,null);
        assertThatThrownBy(()->runtime.open(new SessionExchange(AuthDomain.ADMIN,"public",Capability.READ),op,null)).isInstanceOf(Failure.class);
    }
    private com.aiagent.api.auth.boundary.BoundaryResponseWriter responses(){var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var messages=org.mockito.Mockito.mock(com.aiagent.api.error.ErrorMessages.class);org.mockito.Mockito.when(messages.of(org.mockito.ArgumentMatchers.any())).thenAnswer(call->((com.aiagent.core.error.ErrorCode)call.getArgument(0)).defaultMessage());return new com.aiagent.api.auth.boundary.BoundaryResponseWriter(mapper,new com.aiagent.api.error.ApiErrorWriter(mapper,messages));}
    private SessionV2Filter filter(List<ModuleApiCatalog.Operation> operations,Map<String,SessionV2Filter.Preflight> preflights){return new SessionV2Filter(runtime,new ModuleApiCatalog(operations,false),new SessionV2Assembly.Configuration(Provider.JDBC,Map.of(AuthDomain.ADMIN,new Settings(Provider.JDBC,epoch,"fixture")),"http://localhost",true,Duration.ofMinutes(30)),Map.of(AuthDomain.ADMIN,producer),responses(),preflights);}
    @Test @DisplayName("TC-30122/29 빠른실SQL HTTP경계는정확Origin/CSRF·중복쿠키·메서드·수준거부를집행한다")
    void focusedHttpBoundaryRules()throws Exception{
        var user=login(anonymous(),AuthLevel.AUTHENTICATED,new SessionRuntime.SlotPolicy(false,1,false));var op=new ModuleApiCatalog.Operation("save","POST","/api/system/service/item/save",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"WRITE",false,Capability.READ,null,null);var boundary=filter(List.of(op),Map.of());
        for(int mode=0;mode<6;mode++){
            var request=new org.springframework.mock.web.MockHttpServletRequest(mode==4?"GET":"POST",op.path());request.setCookies(new jakarta.servlet.http.Cookie("AIHUB_ADMIN_SESSION",user.cookie));request.addHeader("Origin",mode==1?"https://evil.example":"http://localhost");if(mode!=2)request.addHeader("X-AIHUB-ADMIN-CSRF",mode==3?"wrong":user.csrf);if(mode==5)request.setCookies(new jakarta.servlet.http.Cookie("AIHUB_ADMIN_SESSION",user.cookie),new jakarta.servlet.http.Cookie("AIHUB_ADMIN_SESSION",user.cookie));var response=new org.springframework.mock.web.MockHttpServletResponse();var effects=new java.util.concurrent.atomic.AtomicInteger();boundary.doFilter(request,response,(req,res)->{effects.incrementAndGet();res.getWriter().write("accepted");});assertThat(response.getStatus()).isEqualTo(mode==0?200:403);assertThat(effects).hasValue(mode==0?1:0);
        }
        var restricted=login(anonymous(),AuthLevel.PASSWORD_CHANGE_REQUIRED,new SessionRuntime.SlotPolicy(false,1,false));var request=new org.springframework.mock.web.MockHttpServletRequest("POST",op.path());request.setCookies(new jakarta.servlet.http.Cookie("AIHUB_ADMIN_SESSION",restricted.cookie));var response=new org.springframework.mock.web.MockHttpServletResponse();boundary.doFilter(request,response,(req,res)->fail("제한세션업무"));assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test @DisplayName("TC-30129 NONE은외부인증문맥0·원천본문검증/재독·raw명령0이고공개GET은비발급이다")
    void focusedSourceAndPublicRead()throws Exception{
        var sourceOp=new ModuleApiCatalog.Operation("source","POST","/api/integration/public/person/receive",ModuleApiCatalog.Domain.NONE,Set.of(),null,false,Capability.READ,request->{assertThat(request.getUserPrincipal()).isNull();assertThat(new String(request.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("signed");},null);var publicOp=new ModuleApiCatalog.Operation("bundle","GET","/api/system/public/message/getBundle",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.values()),null,true,Capability.READ,null,null);var boundary=filter(List.of(sourceOp,publicOp),Map.of());
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST",sourceOp.path());request.setContent("signed".getBytes(java.nio.charset.StandardCharsets.UTF_8));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("foreign",null,List.of()));var response=new org.springframework.mock.web.MockHttpServletResponse();boundary.doFilter(request,response,(req,res)->{assertThat(new String(req.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("signed");var http=(jakarta.servlet.http.HttpServletRequest)req;assertThatThrownBy(()->http.getSession()).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->http.changeSessionId()).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->http.login("x","x")).isInstanceOf(jakarta.servlet.ServletException.class);assertThatThrownBy(http::logout).isInstanceOf(jakarta.servlet.ServletException.class);assertThatThrownBy(http::startAsync).isInstanceOf(IllegalStateException.class);assertThatThrownBy(http::getAsyncContext).isInstanceOf(IllegalStateException.class);res.getWriter().write("source");});assertThat(response.getContentAsString()).isEqualTo("source");
        var publicRequest=new org.springframework.mock.web.MockHttpServletRequest("GET",publicOp.path());var publicResponse=new org.springframework.mock.web.MockHttpServletResponse();boundary.doFilter(publicRequest,publicResponse,(req,res)->res.getWriter().write("bundle"));assertThat(publicResponse.getStatus()).isEqualTo(200);assertThat(publicResponse.getHeaders("Set-Cookie")).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT",Integer.class)).isZero();
    }
    @Test @DisplayName("TC-30121/28 정확HTTPS쿠키속성·자동JSON비밀부재·분리TM/중복필터기동거부")
    void publicAssemblyStartupAndSecrets()throws Exception{
        jdbc.execute("ALTER TABLE AI_PORTAL_SESSION_ATTRIBUTES RENAME TO CLOSED_PORTAL_ATTRIBUTES");jdbc.execute("ALTER TABLE AI_PORTAL_SESSION RENAME TO CLOSED_PORTAL_SESSION");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var messages=org.mockito.Mockito.mock(com.aiagent.api.error.ErrorMessages.class);org.mockito.Mockito.when(messages.of(org.mockito.ArgumentMatchers.any())).thenAnswer(call->((com.aiagent.core.error.ErrorCode)call.getArgument(0)).defaultMessage());var writer=new com.aiagent.api.auth.boundary.BoundaryResponseWriter(mapper,new com.aiagent.api.error.ApiErrorWriter(mapper,messages));
        var config=new SessionV2Assembly.Configuration(Provider.JDBC,Map.of(AuthDomain.ADMIN,new Settings(Provider.JDBC,epoch,"fixture")),"https://hub.example",false,Duration.ofMinutes(30));var catalog=new ModuleApiCatalog(List.of(operation(Capability.PREPARE_CSRF,null)),false);
        var assembly=SessionV2Assembly.create(config,source,manager,null,new SessionRuntime.AuthorityBinding(source,manager,(context,exclusive,command)->command.run()),catalog,Map.of(AuthDomain.ADMIN,producer),writer);
        var http=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/system/public/auth/getCsrf");var response=new org.springframework.mock.web.MockHttpServletResponse();assembly.filter().doFilter(http,response,(request,res)->fail("정확CSRF는내부처리"));assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeaders("Set-Cookie")).hasSize(2).allMatch(cookie->cookie.contains("Secure")&&cookie.contains("SameSite=Lax")&&cookie.contains("Path=/"));assertThat(response.getHeaders("Set-Cookie").stream().filter(cookie->cookie.startsWith("AIHUB_ADMIN_SESSION")).findFirst().orElseThrow()).contains("HttpOnly");
        var scope=(SessionRuntime.Scope)http.getAttribute(SessionV2Filter.SCOPE);var snapshot=scope.current().orElseThrow();assertThat(mapper.writeValueAsString(snapshot.ref())).isEqualTo("{}");assertThat(mapper.writeValueAsString(new Secret("c".repeat(43)))).isEqualTo("{}");assertThat(mapper.writeValueAsString(com.aiagent.core.response.ApiResponse.success(snapshot,"trace"))).doesNotContain(snapshot.managementRef(),"c".repeat(43));
        var another=new DataSourceTransactionManager(source);assertThatThrownBy(()->SessionV2Assembly.create(config,source,manager,null,new SessionRuntime.AuthorityBinding(source,another,(context,exclusive,command)->command.run()),catalog,Map.of(),writer)).isInstanceOf(IllegalArgumentException.class);
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();var context=org.mockito.Mockito.mock(jakarta.servlet.ServletContext.class);var registration=org.mockito.Mockito.mock(jakarta.servlet.FilterRegistration.class);org.mockito.Mockito.when(registration.getClassName()).thenReturn(SessionV2Filter.class.getName());org.mockito.Mockito.doReturn(Map.of("v2",registration)).when(context).getFilterRegistrations();assembly.verifyInstallation(beans,context,Map.of("getCsrf","Fixture#getCsrf"),List.of(new ModuleApiCatalog.Handler("GET","/api/system/public/auth/getCsrf","Fixture#getCsrf")));
        org.mockito.Mockito.doReturn(Map.of("first",registration,"second",registration)).when(context).getFilterRegistrations();assertThatThrownBy(()->assembly.verifyFilterInstallation(beans,context)).isInstanceOf(IllegalStateException.class);org.mockito.Mockito.doReturn(Map.of("v2",registration)).when(context).getFilterRegistrations();beans.registerSingleton("springSessionRepositoryFilter",new Object());assertThatThrownBy(()->assembly.verifyFilterInstallation(beans,context)).isInstanceOf(IllegalStateException.class);
    }
}
