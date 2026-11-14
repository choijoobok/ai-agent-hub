// @feat FEAT-001
package com.aiagent.support.deployment;
import java.net.URI;
import java.util.*;
/** 원문을 반환하지 않는 표시 전용 투영. 경로 허용 집합은 서버 배포 정의에서만 공급한다. */
public record ConnectionDisplay(String baseUrl,String authenticationType,String status,String reason) {
    public static ConnectionDisplay project(String endpoint,String authentication,Set<String> publicSegments) {
        String auth=Set.of("NONE","BASIC","BEARER","API_KEY","EXTERNAL").contains(authentication==null?"":authentication)?authentication:null;
        try {
            if(endpoint==null||endpoint.chars().anyMatch(c->c<32||c==127))return unavailable(auth);
            URI uri=URI.create(endpoint);
            if(uri.getScheme()==null||!Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getPort()>65535)return unavailable(auth);
            boolean redacted=uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null;
            var path=new StringBuilder();String raw=uri.getRawPath();
            if(raw!=null&&!raw.isEmpty()) {
                var segments=raw.split("/",-1);
                for(int index=1;index<segments.length;index++) {
                    String segment=segments[index];path.append('/');
                    if(segment.isEmpty())continue;
                    if(segment.matches("[A-Za-z0-9._~-]+")&&!Set.of(".","..").contains(segment)&&publicSegments.contains(segment))path.append(segment);
                    else {path.append("[REDACTED]");redacted=true;}
                }
            }
            String address=uri.getScheme().toLowerCase(Locale.ROOT)+"://"+uri.getHost()+(uri.getPort()<0?"":":"+uri.getPort())+path;
            return new ConnectionDisplay(address,auth,redacted?"REDACTED":"AVAILABLE",redacted?"SENSITIVE_PARTS_REMOVED":null);
        } catch(IllegalArgumentException failure){return unavailable(auth);}
    }
    private static ConnectionDisplay unavailable(String auth){return new ConnectionDisplay(null,auth,"UNAVAILABLE","UNSAFE_ADDRESS");}
}
