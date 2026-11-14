// @feat FEAT-302
package com.aiagent.core.deployment;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** 배포 연결의 공개 투영과 서버 내부 한정 소비. HTTP API가 아니다. */
public interface ConnectionRegistry {
    enum Kind {DATASOURCE,BUSINESS_API,MODEL,EXTERNAL_IDENTITY}
    enum Operation {READ,CONNECT}
    enum Status {AVAILABLE,SECRET_NOT_VERIFIED,MISSING,UNSUPPORTED,GENERATION_MISMATCH,SECRET_UNAVAILABLE,FORBIDDEN}
    record Generation(UUID id,String deploymentId,String contractVersion,Instant loadedAt) {}
    record Ref(Kind kind,String key,UUID expectedGeneration) {}
    record PublicView(Ref ref,String displayName,Generation generation,Status status) {}
    final class Rejected extends IllegalStateException {
        private final Status status;
        public Rejected(Status status){super("배포 연결을 사용할 수 없다 ["+status+"]");this.status=status;}
        public Status status(){return status;}
    }
    /** 소유 모듈이 서버 bean으로 등록하며 현재 READ/실행 권한을 매 호출 검사한다. */
    interface Consumer {
        String id();
        Set<Kind> kinds();
        void authorize(Kind kind,Operation operation);
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreType
    interface Resolved {
        Ref ref();
        String endpoint();
        String authentication();
        String principal();
        Map<String,String> headers();
        Set<String> capabilities();
        <T> T withSecret(Function<char[],T> action);
    }
    Generation generation();
    List<PublicView> listPublicKinds(String serverCaller);
    PublicView describe(String serverCaller,Ref ref);
    <T> T withResolvedConnection(String serverCaller,Ref ref,Function<Resolved,T> action);
    void validateBoot();
}
