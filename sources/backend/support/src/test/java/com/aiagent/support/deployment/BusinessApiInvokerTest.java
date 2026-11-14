// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.deployment.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.*;

class BusinessApiInvokerTest {
    @Test @DisplayName("TC-00122 선택한 유틸·연결·인증과 안전 요청, 우회 URL/헤더 거부")
    void selectedConnection() throws Exception {
        var received=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/base/check",exchange->{
            received.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-secret");
            assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("%24filter=name%20eq%20%27a%20b%27");
            byte[] body="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try(var invoker=new RestJsonInvoker()) {
            var registry=new BusinessApiInvokers(Map.of("restJsonInvoker",invoker));
            var selected=registry.require("restJsonInvoker","REST_JSON","BEARER");
            var connection=connection("http://127.0.0.1:"+server.getAddress().getPort()+"/base","BEARER");
            var options=new BusinessApiInvoker.Options(1000,2000,1024,null);
            var request=new BusinessApiInvoker.Request("GET","/check",Map.of("$filter",List.of("name eq 'a b'")),Map.of(),null);
            assertThat(selected.invoke(connection,options,request).status()).isEqualTo(200);
            for(String path:List.of("https://outside.invalid/check","//outside.invalid/check","/../check","/%2e%2e/check","/check?other=yes"))
                assertThatThrownBy(()->selected.invoke(connection,options,new BusinessApiInvoker.Request("GET",path,Map.of(),Map.of(),null))).isInstanceOf(BusinessApiInvoker.Failure.class);
            assertThatThrownBy(()->selected.invoke(connection,options,new BusinessApiInvoker.Request("GET","/check",Map.of(),Map.of("Authorization","override"),null))).isInstanceOf(BusinessApiInvoker.Failure.class);
            assertThat(received.get()).isEqualTo(1);
            assertThatThrownBy(()->registry.require("missing","REST_JSON","NONE")).isInstanceOf(BusinessApiInvoker.Failure.class);
            assertThatThrownBy(()->registry.require("restJsonInvoker","ODATA_V2","NONE")).isInstanceOf(BusinessApiInvoker.Failure.class);
            assertThat(connection.toString()).doesNotContain("fixture-secret");
        }finally{server.stop(0);}
    }

    @Test @DisplayName("TC-00122 응답 크기 제한·리다이렉트 미추적·잘못된 옵션 거부")
    void boundedResponse() throws Exception {
        var redirected=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/large",x->{byte[] b=new byte[2048];x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
        server.createContext("/redirect",x->{x.getResponseHeaders().add("Location","/destination");x.sendResponseHeaders(302,-1);x.close();});
        server.createContext("/destination",x->{redirected.incrementAndGet();x.sendResponseHeaders(200,-1);x.close();});server.start();
        try(var invoker=new RestJsonInvoker()) {
            var connection=connection("http://127.0.0.1:"+server.getAddress().getPort(),"NONE");
            var options=new BusinessApiInvoker.Options(1000,2000,1024,null);
            assertThatThrownBy(()->invoker.invoke(connection,options,new BusinessApiInvoker.Request("GET","/large",Map.of(),Map.of(),null)))
                .isInstanceOf(BusinessApiInvoker.Failure.class).hasMessageContaining("RESPONSE_LIMIT");
            assertThat(invoker.invoke(connection,options,new BusinessApiInvoker.Request("GET","/redirect",Map.of(),Map.of(),null)).status()).isEqualTo(302);
            assertThat(redirected.get()).isZero();
            assertThatThrownBy(()->new BusinessApiInvoker.Options(0,1000,1024,null)).isInstanceOf(BusinessApiInvoker.Failure.class);
        }finally{server.stop(0);}
    }

    static ConnectionRegistry.Resolved connection(String endpoint,String auth) {
        return new ConnectionRegistry.Resolved(){
            public ConnectionRegistry.Ref ref(){return new ConnectionRegistry.Ref(ConnectionRegistry.Kind.BUSINESS_API,"fixture",UUID.randomUUID());}
            public String endpoint(){return endpoint;}public String authentication(){return auth;}public String principal(){return "fixture";}
            public Map<String,String> headers(){return Map.of();}public Set<String> capabilities(){return Set.of();}
            public <T>T withSecret(Function<char[],T> action){var value="fixture-secret".toCharArray();try{return action.apply(value);}finally{Arrays.fill(value,'\0');}}
            public String toString(){return "보호된 시험 연결";}
        };
    }
}
