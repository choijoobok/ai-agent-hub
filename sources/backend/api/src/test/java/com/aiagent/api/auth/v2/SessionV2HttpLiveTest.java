// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.api.auth.boundary.*;
import java.util.*;
import java.time.Duration;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

@Tag("authV2Live")
class SessionV2HttpLiveTest {
    record Targets(String userId,List<String> targets) {}
    private HttpResponse<String> send(HttpClient client,String origin,String method,String path,Map<String,String> cookies,String csrf,String domain,String requestOrigin,String body)throws Exception{
        var request=HttpRequest.newBuilder(java.net.URI.create(origin+path)).timeout(Duration.ofSeconds(10));
        if(cookies!=null&&!cookies.isEmpty())request.header("Cookie",cookies.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue()).collect(java.util.stream.Collectors.joining("; ")));
        if(csrf!=null)request.header("X-AIHUB-"+domain+"-CSRF",csrf);if(requestOrigin!=null)request.header("Origin",requestOrigin);
        if(body!=null)request.header("Content-Type",body.startsWith("{")?"application/json":"application/x-www-form-urlencoded");
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private Map<String,String> cookies(HttpResponse<String> response){var values=new HashMap<String,String>();for(String header:response.headers().allValues("Set-Cookie")){String[] pair=header.split(";",2)[0].split("=",2);values.put(pair[0],pair[1]);}return values;}
    @ParameterizedTest @EnumSource(Provider.class) @DisplayName("TC-30121/22/28/29 실제PG/JPA/양provider Tomcat의정확카탈로그·영역CSRF·발급봉투")
    void actualHttpTwoDomainsAndExactRoutes(Provider provider)throws Exception{
        try(var fixture=new V2LiveFixture(provider)){
            int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}String origin="http://127.0.0.1:"+port;
            List<ModuleApiCatalog.Operation> operations=new ArrayList<>();for(AuthDomain domain:AuthDomain.values())for(Capability cap:List.of(Capability.PREPARE_CSRF,Capability.LOGIN,Capability.PASSWORD_CHANGE,Capability.LOGOUT,Capability.READ))operations.add(fixture.operation(domain,cap));
            operations.add(new ModuleApiCatalog.Operation("write","POST","/api/system/service/item/save",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"WRITE",false,Capability.READ,null,null));
            operations.add(new ModuleApiCatalog.Operation("bundle","GET","/api/system/public/message/getBundle",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.values()),null,true,Capability.READ,null,null));
            operations.add(new ModuleApiCatalog.Operation("source","POST","/api/integration/public/person/receive",ModuleApiCatalog.Domain.NONE,Set.of(),null,false,Capability.READ,request->{assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();assertThatThrownBy(()->request.getSession(false)).isInstanceOf(IllegalStateException.class);if(!"trusted".equals(request.getHeader("X-Source"))||!"trusted-body".equals(new String(request.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)))throw new IllegalArgumentException("시험 원천 거부");},null));
            operations.add(new ModuleApiCatalog.Operation("sessions","POST","/api/system/service/user/getSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"READ",false,Capability.READ,null,null));
            operations.add(new ModuleApiCatalog.Operation("kill","POST","/api/system/service/user/killSessions",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),"EXECUTE",false,Capability.READ,null,null,true));
            operations.add(new ModuleApiCatalog.Operation("progress","GET","/api/system/common/progress/getProgress/{jobId}",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,(principal,job)->job.endsWith("0001")));
            var catalog=new ModuleApiCatalog(operations,false);var config=new SessionV2Assembly.Configuration(provider,fixture.selected,origin,true,Duration.ofMinutes(30));
            SessionV2Filter.Preflight preflight=(request,inspection)->{var input=tools.jackson.databind.json.JsonMapper.builder().build().readValue(request.getInputStream(),Targets.class);return inspection.prepare(new PrincipalRef(AuthDomain.ADMIN,input.userId()));};
            var assembly=SessionV2Assembly.create(config,fixture.source,fixture.manager,fixture.redis,new SessionRuntime.AuthorityBinding(fixture.source,fixture.manager,fixture.authority),catalog,fixture.producers,fixture.responses(),Map.of("sessions",preflight,"kill",preflight));
            var server=new org.apache.catalina.startup.Tomcat();var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/auth308-http",UUID.randomUUID().toString())).toAbsolutePath();server.setBaseDir(directory.toString());server.setPort(port);server.getConnector().setProperty("address","127.0.0.1");var context=server.addContext("",directory.toString());
            var definition=new org.apache.tomcat.util.descriptor.web.FilterDef();definition.setFilterName("v2");definition.setFilterClass(SessionV2Filter.class.getName());definition.setFilter(assembly.filter());context.addFilterDef(definition);var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("v2");mapping.addURLPattern("/*");context.addFilterMap(mapping);
            var outer=new org.apache.tomcat.util.descriptor.web.FilterDef();outer.setFilterName("outerContext");outer.setFilterClass("fixture.outer");outer.setFilter((request,response,chain)->{org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("foreign-admin",null,List.of()));try{chain.doFilter(request,response);}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});context.addFilterDef(outer);var outerMapping=new org.apache.tomcat.util.descriptor.web.FilterMap();outerMapping.setFilterName("outerContext");outerMapping.addURLPattern("/*");context.addFilterMapBefore(outerMapping);
            org.apache.catalina.startup.Tomcat.addServlet(context,"probe",new jakarta.servlet.http.HttpServlet(){
                @Override protected void service(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{
                    var scope=(SessionRuntime.Scope)request.getAttribute(SessionV2Filter.SCOPE);String path=request.getRequestURI();
                    if(request.getParameter("raw")!=null){try{switch(request.getParameter("raw")){case "login"->request.login("fake","fake");case "logout"->request.logout();case "rotate"->request.changeSessionId();case "session"->request.getSession();case "async"->request.startAsync();case "asyncContext"->request.getAsyncContext();default->throw new IllegalArgumentException("시험 raw동작오류");}}catch(jakarta.servlet.ServletException failure){throw new IllegalStateException("시험 raw명령 거부",failure);}}
                    if(path.endsWith("/login")||path.endsWith("/changePassword")){
                        if(!"correct".equals(request.getParameter("password")))throw new AuthBoundaryAssembly.Rejected(401);
                        var domain=scope.exchange().domain();var current=scope.current().orElseThrow();var identity=fixture.identity(domain,"yes".equals(request.getParameter("restricted"))?AuthLevel.PASSWORD_CHANGE_REQUIRED:AuthLevel.AUTHENTICATED);
                        fixture.effect(UUID.randomUUID().toString());scope.publish(path.endsWith("/login")?fixture.producers.get(domain).login(scope.exchange(),current.ref(),identity,fixture.ledger.now().plusSeconds(60)):fixture.producers.get(domain).rotation(scope.exchange(),current.ref(),identity,fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));
                        if("yes".equals(request.getParameter("rollback")))throw new AuthBoundaryAssembly.Rejected(503);
                        if("yes".equals(request.getParameter("early")))response.sendRedirect("/escape");
                    }else if(path.endsWith("/logout"))scope.logout();else if(path.endsWith("/save")||path.endsWith("/receive")){
                        if(path.endsWith("/receive")){assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();assertThat(new String(request.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("trusted-body");}
                        fixture.jdbc.update("INSERT INTO AUTH307_CREDENTIAL VALUES(?, 'accepted')",UUID.randomUUID().toString());
                    }
                    Object result=Map.of("accepted",true);
                    if(path.endsWith("/getSessions")||path.endsWith("/killSessions")){var input=tools.jackson.databind.json.JsonMapper.builder().build().readValue(request.getInputStream(),Targets.class);var plan=scope.inspectionFor(new PrincipalRef(AuthDomain.ADMIN,input.userId()));result=path.endsWith("/getSessions")?scope.inspect(plan):scope.revoke(plan,input.targets());}
                    response.setContentType("application/json");tools.jackson.databind.json.JsonMapper.builder().build().writeValue(response.getWriter(),ApiResponse.success(result,com.aiagent.api.trace.TraceId.current()));
                }
            });context.addServletMappingDecoded("/*","probe");
            try{
                server.start();assembly.verifyFilterInstallation(new DefaultListableBeanFactory(),context.getServletContext());var client=HttpClient.newHttpClient();
                Map<AuthDomain,Map<String,String>> current=new EnumMap<>(AuthDomain.class);
                for(AuthDomain domain:AuthDomain.values()){
                    String module=domain==AuthDomain.ADMIN?"system":"portal",base="/api/"+module+"/",session="AIHUB_"+domain+"_SESSION",csrf="AIHUB_"+domain+"_CSRF";
                    var prepared=send(client,origin,"GET",base+"public/auth/getCsrf",null,null,domain.name(),null,null);assertThat(prepared.statusCode()).isEqualTo(200);var anonymous=cookies(prepared);
                    assertThat(prepared.body()).contains("\"success\":true","\"data\"","csrfHeaderName","traceId").doesNotContain(anonymous.get(session),anonymous.get(csrf));assertThat(anonymous.get(session)).hasSize(43);
                    assertThat(send(client,origin,"GET",base+"common/auth/getMe",anonymous,null,domain.name(),null,null).statusCode()).isEqualTo(401);
                    var denied=send(client,origin,"POST",base+"public/auth/login",anonymous,anonymous.get(csrf),domain.name(),"https://evil.example","password=correct");assertThat(denied.statusCode()).isEqualTo(403);assertThat(denied.headers().allValues("Set-Cookie")).isEmpty();
                    Integer effectCount=fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class);for(String raw:List.of("login","logout","rotate","session","async","asyncContext")){var direct=send(client,origin,"POST",base+"public/auth/login",anonymous,anonymous.get(csrf),domain.name(),origin,"password=correct&raw="+raw);assertThat(direct.statusCode()).isEqualTo(503);assertThat(direct.headers().allValues("Set-Cookie")).isEmpty();}assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(effectCount);
                    var early=send(client,origin,"POST",base+"public/auth/login",anonymous,anonymous.get(csrf),domain.name(),origin,"password=correct&early=yes");assertThat(early.statusCode()).isEqualTo(503);assertThat(early.headers().firstValue("Location")).isEmpty();assertThat(early.headers().allValues("Set-Cookie")).isEmpty();assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(effectCount);
                    var rollback=send(client,origin,"POST",base+"public/auth/login",anonymous,anonymous.get(csrf),domain.name(),origin,"password=correct&rollback=yes");assertThat(rollback.statusCode()).isEqualTo(503);assertThat(rollback.headers().allValues("Set-Cookie")).isEmpty();
                    var login=send(client,origin,"POST",base+"public/auth/login",anonymous,anonymous.get(csrf),domain.name(),origin,"password=correct");assertThat(login.statusCode()).isEqualTo(200);current.put(domain,cookies(login));
                    assertThat(send(client,origin,"GET",base+"common/auth/getMe",current.get(domain),null,domain.name(),null,null).statusCode()).isEqualTo(200);
                    assertThat(send(client,origin,"GET",base+"common/auth/getMe",Map.of(session,current.get(domain).get(csrf)),null,domain.name(),null,null).statusCode()).isEqualTo(401);
                    var rotated=send(client,origin,"POST",base+"common/auth/changePassword",current.get(domain),current.get(domain).get(csrf),domain.name(),origin,"password=correct");assertThat(rotated.statusCode()).isEqualTo(200);var old=current.put(domain,cookies(rotated));
                    assertThat(send(client,origin,"GET",base+"common/auth/getMe",old,null,domain.name(),null,null).statusCode()).isEqualTo(401);
                }
                var admin=current.get(AuthDomain.ADMIN);var portal=current.get(AuthDomain.PORTAL);
                for(String job:List.of("00000000-0000-0000-0000-000000000002","missing","not-a-uuid"))assertThat(send(client,origin,"GET","/api/system/common/progress/getProgress/"+job,admin,null,"ADMIN",null,null).statusCode()).isEqualTo(400);
                assertThat(send(client,origin,"GET","/api/system/common/progress/getProgress/00000000-0000-0000-0000-000000000001",admin,null,"ADMIN",null,null).statusCode()).isEqualTo(200);
                assertThat(send(client,origin,"GET","/api/system/common/auth/getMe",Map.of("AIHUB_ADMIN_SESSION",portal.get("AIHUB_PORTAL_SESSION")),null,"ADMIN",null,null).statusCode()).isEqualTo(401);
                assertThat(send(client,origin,"POST","/api/system/service/item/save",admin,portal.get("AIHUB_PORTAL_CSRF"),"PORTAL",origin,null).statusCode()).isEqualTo(403);
                assertThat(send(client,origin,"POST","/api/system/service/item/save",admin,admin.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,null).statusCode()).isEqualTo(200);
                for(String path:List.of("/api","/internal","/%61pi/system/common/auth/getMe","/api/common/system/auth/getMe","/api/sys/common/auth/getMe","/api/system/common/auth/getMe;alias"))assertThat(send(client,origin,"GET",path,admin,null,"ADMIN",null,null).statusCode()).isIn(400,403);
                var before=fixture.jdbc.queryForObject("SELECT SUM(EXTRACT(EPOCH FROM LAST_ACCESS_AT)) FROM AI_AUTH_SESSION_GRANT",java.math.BigDecimal.class);var bundle=send(client,origin,"GET","/api/system/public/message/getBundle",admin,null,"ADMIN",null,null);assertThat(bundle.statusCode()).isEqualTo(200);assertThat(bundle.headers().allValues("Set-Cookie")).isEmpty();assertThat(fixture.jdbc.queryForObject("SELECT SUM(EXTRACT(EPOCH FROM LAST_ACCESS_AT)) FROM AI_AUTH_SESSION_GRANT",java.math.BigDecimal.class)).isEqualByComparingTo(before);
                var source=send(client,origin,"POST","/api/integration/public/person/receive",admin,null,"ADMIN",origin,null);assertThat(source.statusCode()).isEqualTo(403);
                var trusted=HttpRequest.newBuilder(java.net.URI.create(origin+"/api/integration/public/person/receive")).header("X-Source","trusted").header("Cookie","AIHUB_ADMIN_SESSION="+admin.get("AIHUB_ADMIN_SESSION")).POST(HttpRequest.BodyPublishers.ofString("trusted-body")).build();assertThat(client.send(trusted,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                var duplicate=HttpRequest.newBuilder(java.net.URI.create(origin+"/api/system/common/auth/getMe")).header("Cookie","AIHUB_ADMIN_SESSION="+admin.get("AIHUB_ADMIN_SESSION")+"; AIHUB_ADMIN_SESSION="+admin.get("AIHUB_ADMIN_SESSION")).GET().build();assertThat(client.send(duplicate,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
                var duplicateHeader=HttpRequest.newBuilder(java.net.URI.create(origin+"/api/system/service/item/save")).header("Cookie","AIHUB_ADMIN_SESSION="+admin.get("AIHUB_ADMIN_SESSION")).header("Origin",origin).header("X-AIHUB-ADMIN-CSRF",admin.get("AIHUB_ADMIN_CSRF")).header("X-AIHUB-ADMIN-CSRF",admin.get("AIHUB_ADMIN_CSRF")).POST(HttpRequest.BodyPublishers.noBody()).build();assertThat(client.send(duplicateHeader,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
                var restrictedEntry=cookies(send(client,origin,"GET","/api/system/public/auth/getCsrf",null,null,"ADMIN",null,null));var restricted=cookies(send(client,origin,"POST","/api/system/public/auth/login",restrictedEntry,restrictedEntry.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,"password=correct&restricted=yes"));
                Long grants=fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT",Long.class);
                for(var state:List.of(Map.<String,String>of(),restricted,admin))assertThat(send(client,origin,"GET","/api/system/public/message/getBundle",state,null,"ADMIN",null,null).statusCode()).isEqualTo(200);
                assertThat(fixture.jdbc.queryForObject("SELECT FIRST_USED_AT FROM AI_AUTH_SESSION_GRANT WHERE AUTH_LEVEL='PASSWORD_CHANGE_REQUIRED' AND STATE_CD='ACTIVE'",java.sql.Timestamp.class)).isNull();assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AI_AUTH_SESSION_GRANT",Long.class)).isEqualTo(grants);
                assertThat(send(client,origin,"POST","/api/system/service/item/save",restricted,restricted.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,null).statusCode()).isEqualTo(403);assertThat(send(client,origin,"GET","/api/system/common/auth/getMe",restricted,null,"ADMIN",null,null).statusCode()).isEqualTo(200);
                String json="{\"userId\":\"owner\",\"targets\":[]}";var listed=send(client,origin,"POST","/api/system/service/user/getSessions",admin,admin.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,json);assertThat(listed.statusCode()).isEqualTo(200);assertThat(listed.body()).contains("sessionId","current").doesNotContain(admin.get("AIHUB_ADMIN_SESSION"),portal.get("AIHUB_PORTAL_SESSION"));
                var rows=tools.jackson.databind.json.JsonMapper.builder().build().readTree(listed.body()).get("data");String management=rows.get(0).get("sessionId").asString();assertThat(send(client,origin,"GET","/api/system/common/auth/getMe",Map.of("AIHUB_ADMIN_SESSION",management),null,"ADMIN",null,null).statusCode()).isEqualTo(401);
                var killed=send(client,origin,"POST","/api/system/service/user/killSessions",admin,admin.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,"{\"userId\":\"owner\",\"targets\":[\""+management+"\",\""+management+"\"]}");assertThat(killed.statusCode()).isEqualTo(200);assertThat(killed.body()).contains("\"data\":1");
                // 대상이 현재admin이면 새anonymous준비로 재로그인할 수 있고 Portal은 보존된다.
                var preparedAgain=send(client,origin,"GET","/api/system/public/auth/getCsrf",Map.of("AIHUB_ADMIN_SESSION",admin.get("AIHUB_ADMIN_SESSION")),null,"ADMIN",null,null);assertThat(preparedAgain.statusCode()).isEqualTo(200);
                var reentry=cookies(preparedAgain);var recovered=cookies(send(client,origin,"POST","/api/system/public/auth/login",reentry,reentry.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,"password=correct"));admin.clear();admin.putAll(recovered);
                var logout=send(client,origin,"POST","/api/system/common/auth/logout",admin,admin.get("AIHUB_ADMIN_CSRF"),"ADMIN",origin,null);assertThat(logout.statusCode()).isEqualTo(200);assertThat(logout.headers().allValues("Set-Cookie")).hasSize(2).allMatch(value->value.contains("Max-Age=0"));assertThat(send(client,origin,"GET","/api/portal/common/auth/getMe",portal,null,"PORTAL",null,null).statusCode()).isEqualTo(200);
            }finally{server.stop();server.destroy();}
        }
    }
}
