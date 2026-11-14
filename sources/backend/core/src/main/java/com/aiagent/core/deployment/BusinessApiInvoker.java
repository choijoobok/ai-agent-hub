// @feat FEAT-001 FEAT-302
package com.aiagent.core.deployment;

import java.util.*;

/** 서버가 검증한 불변 연결을 소비한다. 현재 권한·승인·감사·재시도는 호출 소유자가 지킨다. */
public interface BusinessApiInvoker {
    Set<String> kinds();
    Set<String> authenticationTypes();
    record Options(int connectTimeoutMs,int requestTimeoutMs,int maxResponseBytes,String apiKeyHeader) {
        public Options {
            if(connectTimeoutMs<1||connectTimeoutMs>60_000||requestTimeoutMs<connectTimeoutMs||requestTimeoutMs>86_400_000||maxResponseBytes<1||maxResponseBytes>10_485_760)
                throw new Failure("OPTIONS");
            if(apiKeyHeader!=null&&(!apiKeyHeader.matches("[A-Za-z][A-Za-z0-9-]{0,99}")||Set.of("host","cookie","content-length","connection","transfer-encoding").contains(apiKeyHeader.toLowerCase(Locale.ROOT))))throw new Failure("OPTIONS");
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreType
    record Request(String method,String relativePath,Map<String,List<String>> query,Map<String,String> headers,byte[] body) {
        public Request {
            if(method==null||relativePath==null||query==null||headers==null)throw new Failure("REQUEST");
            var copy=new LinkedHashMap<String,List<String>>();query.forEach((k,v)->copy.put(k,List.copyOf(v)));query=Collections.unmodifiableMap(copy);
            headers=Map.copyOf(headers);body=body==null?null:body.clone();
        }
        public byte[] body(){return body==null?null:body.clone();}
        @Override public String toString(){return "업무 API 요청[보호됨]";}
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreType
    record Response(int status,Map<String,List<String>> headers,byte[] body) {
        public Response {var copy=new LinkedHashMap<String,List<String>>();headers.forEach((k,v)->copy.put(k,List.copyOf(v)));headers=Collections.unmodifiableMap(copy);body=body.clone();}
        public byte[] body(){return body.clone();}
        @Override public String toString(){return "업무 API 응답[보호됨]";}
    }
    final class Failure extends IllegalStateException {
        private final String code;
        public Failure(String code){super("업무 API 호출을 처리할 수 없다 ["+code+"]");this.code=code;}
        public String code(){return code;}
    }
    Response invoke(ConnectionRegistry.Resolved connection,Options options,Request request);
}
