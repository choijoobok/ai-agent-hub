// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.security.portal.PortalChangeOwner.Binding;
import java.time.Instant;
import java.util.*;

/** 고객 원천 프로토콜 소유자. 검증 함수는 잠금 밖, configuration은 로컬 현재값 조회만 수행한다. */
public interface SourceChangeVerifier {
    record Configuration(String sourceKey,String stableNamespace,String epoch,String generation) {
        public Configuration {ExternalUserChange.text(sourceKey,100);ExternalUserChange.text(stableNamespace,128);ExternalUserChange.text(epoch,128);ExternalUserChange.text(generation,128);}
    }
    record Envelope(String businessDigest,UUID bindingKey,Instant verifiedAt,Instant expiresAt,String evidenceDigest) {}
    record Cursor(long version,String sourceVersion,String orderMode) {}
    enum Order { ADVANCE, PAST, UNKNOWN }
    record Freshness(UUID bindingKey,long previousCursorVersion,String sourceVersion,String orderMode,Order order,String digest,Instant expiresAt) {}
    Configuration configuration();
    /** 서명/인증을 정확 method/path·등록 원천·원 body·기한/nonce에 결합하고 재생 증거를 검사한다. */
    Envelope verifyEnvelope(String method,String exactPath,Map<String,List<String>> headers,byte[] body,ExternalUserChange event);
    /** 원천 판은 어댑터가 비교한다. DB가 사전순이나 occurredAt으로 최신성을 추정하지 않는다. */
    Freshness verifyFreshness(ExternalUserChange event,Envelope envelope,Binding binding,Cursor cursor);
    default boolean testOnly(){return false;}
}
