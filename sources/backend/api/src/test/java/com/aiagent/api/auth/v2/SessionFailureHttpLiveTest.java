// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.security.v2.*;
import java.time.Duration;
import java.util.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;

/** 실제 프로세스 중단은 직렬 독립 실행한다. PIT 병렬 클래스 목록에는 넣지 않는다. */
@Tag("authV2Live")
class SessionFailureHttpLiveTest {
    @Test @DisplayName("TC-30123/28 실제HTTP관리회수DB확정뒤Redis전체장애도회수1건/200·쿠키0이다")
    void committedManagementRevocationReturnsCountWhenRedisUnavailable()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            fixture.jdbc.update("INSERT INTO AUTH_OWNER VALUES('ADMIN','other',true,true)");int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}String origin="http://127.0.0.1:"+port;
            var op=new ModuleApiCatalog.Operation("kill","POST","/api/system/service/user/killSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"EXECUTE",false,Capability.READ,null,null,true);var catalog=new ModuleApiCatalog(List.of(op),false);SessionV2Filter.Preflight prepare=(request,inspection)->{var input=tools.jackson.databind.json.JsonMapper.builder().build().readValue(request.getInputStream(),SessionV2HttpLiveTest.Targets.class);return inspection.prepare(new PrincipalRef(AuthDomain.ADMIN,input.userId()));};
            var assembly=SessionV2Assembly.create(new SessionV2Assembly.Configuration(Provider.REDIS,fixture.selected,origin,true,Duration.ofMinutes(30)),fixture.source,fixture.manager,fixture.redis,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),catalog,fixture.producers,fixture.responses(),Map.of("kill",prepare));var runtime=assembly.runtime();var actor=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var entry=fixture.anonymous(runtime,AuthDomain.ADMIN);var target=fixture.scope(runtime,AuthDomain.ADMIN,Capability.LOGIN,entry.cookie());
            runtime.execute(target,()->{var identity=new com.aiagent.core.security.SessionPersistencePort.Identity(new PrincipalRef(AuthDomain.ADMIN,"other"),AuthLevel.AUTHENTICATED,"LOCAL",null,fixture.ledger.now(),"다른관리자","ko");target.publish(fixture.producers.get(AuthDomain.ADMIN).login(target.exchange(),entry.ref(),identity,fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;});var user=fixture.emit(runtime,target);String management=fixture.ledger.find(user.ref(),false).orElseThrow().managementRef();var observed=new java.util.concurrent.atomic.AtomicReference<SessionRuntime.Scope>();
            var server=new org.apache.catalina.startup.Tomcat();var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/auth308-http",UUID.randomUUID().toString())).toAbsolutePath();server.setBaseDir(directory.toString());server.setPort(port);server.getConnector().setProperty("address","127.0.0.1");var context=server.addContext("",directory.toString());var definition=new org.apache.tomcat.util.descriptor.web.FilterDef();definition.setFilterName("v2");definition.setFilterClass(SessionV2Filter.class.getName());definition.setFilter(assembly.filter());context.addFilterDef(definition);var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("v2");mapping.addURLPattern("/*");context.addFilterMap(mapping);
            org.apache.catalina.startup.Tomcat.addServlet(context,"kill",new jakarta.servlet.http.HttpServlet(){@Override protected void doPost(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{
                var input=tools.jackson.databind.json.JsonMapper.builder().build().readValue(request.getInputStream(),SessionV2HttpLiveTest.Targets.class);var scope=(SessionRuntime.Scope)request.getAttribute(SessionV2Filter.SCOPE);observed.set(scope);int count=scope.revoke(scope.inspectionFor(new PrincipalRef(AuthDomain.ADMIN,input.userId())),input.targets());TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{SessionRedisFaultLiveTest.stopRedis();}catch(Exception failure){throw new IllegalStateException("시험 Redis중단 실패",failure);}}});tools.jackson.databind.json.JsonMapper.builder().build().writeValue(response.getWriter(),com.aiagent.core.response.ApiResponse.success(count,com.aiagent.api.trace.TraceId.current()));
            }});context.addServletMappingDecoded("/*","kill");
            try{
                server.start();String json="{\"userId\":\"other\",\"targets\":[\""+management+"\"]}";var request=HttpRequest.newBuilder(java.net.URI.create(origin+op.path())).timeout(Duration.ofSeconds(10)).header("Origin",origin).header("Cookie","AIHUB_ADMIN_SESSION="+actor.cookie()).header("X-AIHUB-ADMIN-CSRF",actor.csrf()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("\"success\":true","\"data\":1");assertThat(response.headers().allValues("Set-Cookie")).isEmpty();assertThat(observed.get().cleanupFailures()).isGreaterThanOrEqualTo(1);assertThat(fixture.ledger.find(user.ref(),false).orElseThrow().status()).isEqualTo(SessionAdmissionLedger.Status.REVOKED);
            }finally{SessionRedisFaultLiveTest.restart(fixture);server.stop();server.destroy();}
            assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,user.cookie())).isInstanceOf(Failure.class);
        }
    }
    @Test @DisplayName("TC-30123 실제HTTP암호DB확정후Redis장애는정규503/재로그인코드·data-null·trace·쿠키0")
    void passwordCommittedButPublicationUnavailableHasRegularEnvelope()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}String origin="http://127.0.0.1:"+port;var catalog=new ModuleApiCatalog(List.of(fixture.operation(AuthDomain.ADMIN,Capability.PASSWORD_CHANGE),fixture.operation(AuthDomain.ADMIN,Capability.PREPARE_CSRF)),false);
            var assembly=SessionV2Assembly.create(new SessionV2Assembly.Configuration(Provider.REDIS,fixture.selected,origin,true,Duration.ofMinutes(30)),fixture.source,fixture.manager,fixture.redis,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),catalog,fixture.producers,fixture.responses());var runtime=assembly.runtime();var old=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));
            var server=new org.apache.catalina.startup.Tomcat();var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/auth308-http",UUID.randomUUID().toString())).toAbsolutePath();server.setBaseDir(directory.toString());server.setPort(port);server.getConnector().setProperty("address","127.0.0.1");var context=server.addContext("",directory.toString());var definition=new org.apache.tomcat.util.descriptor.web.FilterDef();definition.setFilterName("v2");definition.setFilterClass(SessionV2Filter.class.getName());definition.setFilter(assembly.filter());context.addFilterDef(definition);var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("v2");mapping.addURLPattern("/*");context.addFilterMap(mapping);
            org.apache.catalina.startup.Tomcat.addServlet(context,"change",new jakarta.servlet.http.HttpServlet(){@Override protected void doPost(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{
                assertThat(request.getParameter("password")).isEqualTo("new-synthetic");var scope=(SessionRuntime.Scope)request.getAttribute(SessionV2Filter.SCOPE);fixture.effect("password-committed");scope.publish(fixture.producers.get(AuthDomain.ADMIN).rotation(scope.exchange(),scope.current().orElseThrow().ref(),fixture.identity(AuthDomain.ADMIN,AuthLevel.AUTHENTICATED),fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{SessionRedisFaultLiveTest.stopRedis();}catch(Exception failure){throw new IllegalStateException("시험 Redis중단 실패",failure);}}});response.getWriter().write("reserved-success");
            }});context.addServletMappingDecoded("/*","change");
            try{
                server.start();var request=HttpRequest.newBuilder(java.net.URI.create(origin+"/api/system/common/auth/changePassword")).timeout(Duration.ofSeconds(10)).header("Origin",origin).header("Cookie","AIHUB_ADMIN_SESSION="+old.cookie()+"; AIHUB_ADMIN_CSRF="+old.csrf()).header("X-AIHUB-ADMIN-CSRF",old.csrf()).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("password=new-synthetic")).build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(503);assertThat(response.body()).contains("\"success\":false","PASSWORD_CHANGED_REAUTH_REQUIRED","\"traceId\"").doesNotContain("\"data\"","reserved-success",old.cookie(),old.csrf(),"new-synthetic");assertThat(com.aiagent.core.response.ApiResponse.failure("test","test","trace").data()).isNull();assertThat(response.headers().allValues("Set-Cookie")).isEmpty();assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(1);assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isZero();
            }finally{SessionRedisFaultLiveTest.restart(fixture);server.stop();server.destroy();}
            assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);assertThat(fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN)).cookie()).hasSize(43);
        }
    }
}
