// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;

import com.aiagent.core.deployment.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** 연결별 공통 HTTP JSON 구현. 쿠키·리다이렉트·업무 재시도와 OData 자동 처리는 없다. */
@Component("restJsonInvoker")
public final class RestJsonInvoker implements BusinessApiInvoker,AutoCloseable,org.springframework.beans.factory.DisposableBean {
    private final Map<Integer,HttpClient> clients=new ConcurrentHashMap<>();
    public Set<String> kinds(){return Set.of("REST_JSON");}
    public Set<String> authenticationTypes(){return Set.of("NONE","BASIC","BEARER","API_KEY");}
    public Response invoke(ConnectionRegistry.Resolved connection,Options options,Request request) {
        if(!authenticationTypes().contains(connection.authentication()))throw new Failure("INVOKER_UNSUPPORTED");
        URI target=target(connection.endpoint(),request);
        var builder=HttpRequest.newBuilder(target).timeout(Duration.ofMillis(options.requestTimeoutMs())).header("Accept","application/json");
        headers(builder,connection.headers(),options.apiKeyHeader());headers(builder,request.headers(),options.apiKeyHeader());
        authentication(builder,connection,options);
        byte[] body=request.body();
        if(body!=null&&body.length>options.maxResponseBytes())throw new Failure("REQUEST_LIMIT");
        if(body!=null)builder.header("Content-Type","application/json");
        if(!Set.of("GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS").contains(request.method())||Set.of("GET","HEAD").contains(request.method())&&body!=null)throw new Failure("METHOD");
        var client=clients.computeIfAbsent(options.connectTimeoutMs(),timeout->HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).followRedirects(HttpClient.Redirect.NEVER).build());
        var subscriber=new LimitedBody(options.maxResponseBytes());
        CompletableFuture<HttpResponse<byte[]>> pending;
        try {pending=client.sendAsync(builder.method(request.method(),body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),info->subscriber);}
        catch(RuntimeException failure){throw new Failure("REQUEST");}
        try {
            var response=pending.get(options.requestTimeoutMs(),TimeUnit.MILLISECONDS);
            var exposed=new LinkedHashMap<String,List<String>>();
            response.headers().map().forEach((key,value)->{if(Set.of("content-type","etag","retry-after","location").contains(key.toLowerCase(Locale.ROOT)))exposed.put(key,value);});
            return new Response(response.statusCode(),exposed,response.body());
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw new Failure("INTERRUPTED");}
        catch(TimeoutException failure){throw new Failure("TIMEOUT");}
        catch(ExecutionException failure){if(subscriber.exceeded)throw new Failure("RESPONSE_LIMIT");throw new Failure(failure.getCause() instanceof HttpTimeoutException?"TIMEOUT":"TRANSPORT");}
        finally {if(!pending.isDone()){subscriber.cancel();pending.cancel(true);}}
    }
    static URI target(String endpoint,Request request) {
        try {
            var base=URI.create(endpoint);String path=request.relativePath();
            if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!path.startsWith("/")||path.startsWith("//")||path.contains("?")||path.contains("#")||path.contains("\\")||path.matches("(?i).*%(2e|2f|5c|0a|0d|00|25).*"))throw new Failure("PATH");
            if(Arrays.stream(path.split("/",-1)).anyMatch(segment->segment.equals(".")||segment.equals("..")))throw new Failure("PATH");
            String address=endpoint.endsWith("/")?endpoint.substring(0,endpoint.length()-1):endpoint;
            var pairs=new ArrayList<String>();request.query().forEach((key,values)->values.forEach(value->pairs.add(encode(key)+"="+encode(value))));
            return URI.create(address+path+(pairs.isEmpty()?"":"?"+String.join("&",pairs)));
        }catch(IllegalArgumentException failure){throw new Failure("PATH");}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private static void headers(HttpRequest.Builder builder,Map<String,String> headers,String apiKeyHeader) {
        var seen=new HashSet<String>();
        for(var entry:headers.entrySet()) {
            String key=entry.getKey(),value=entry.getValue(),lower=key.toLowerCase(Locale.ROOT);
            if(!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||!seen.add(lower)||value.chars().anyMatch(c->c<32||c==127)||lower.matches(".*(authorization|cookie|token|secret|api[-_]?key).*")||Set.of("host","connection","content-length","transfer-encoding","content-type","accept","expect","upgrade").contains(lower)||key.equalsIgnoreCase(apiKeyHeader))throw new Failure("HEADER");
            builder.header(key,value);
        }
    }
    private static void authentication(HttpRequest.Builder builder,ConnectionRegistry.Resolved connection,Options options) {
        if(connection.authentication().equals("NONE"))return;
        connection.withSecret(value->{
            String secret=new String(value);if(secret.isEmpty()||secret.chars().anyMatch(c->c<32||c==127))throw new Failure("AUTHENTICATION");
            switch(connection.authentication()) {
                case "BEARER"->builder.header("Authorization","Bearer "+secret);
                case "BASIC"->{String principal=connection.principal();if(principal==null||principal.contains(":"))throw new Failure("AUTHENTICATION");builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((principal+":"+secret).getBytes(StandardCharsets.UTF_8)));}
                case "API_KEY"->{if(options.apiKeyHeader()==null)throw new Failure("AUTHENTICATION");builder.header(options.apiKeyHeader(),secret);}
                default->throw new Failure("AUTHENTICATION");
            }
            return null;
        });
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private volatile Flow.Subscription subscription;private volatile boolean exceeded;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        public void onNext(List<ByteBuffer> items){for(var item:items){if(item.remaining()>limit-bytes.size()){exceeded=true;cancel();result.completeExceptionally(new Failure("RESPONSE_LIMIT"));return;}byte[] value=new byte[item.remaining()];item.get(value);bytes.writeBytes(value);}subscription.request(1);}
        public void onError(Throwable failure){result.completeExceptionally(new Failure("TRANSPORT"));}
        public void onComplete(){result.complete(bytes.toByteArray());}
        void cancel(){var current=subscription;if(current!=null)current.cancel();}
    }
    @Override public void close(){clients.values().forEach(HttpClient::shutdownNow);clients.clear();}
    @Override public void destroy(){close();}
}
