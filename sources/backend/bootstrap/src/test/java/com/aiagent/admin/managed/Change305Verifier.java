// @feat FEAT-305
package com.aiagent.admin.managed;

import com.aiagent.portal.change.*;
import com.aiagent.core.security.portal.PortalChangeOwner.Binding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 합성 원천의 실제 HMAC/nonce 검증. 제품 어댑터/고객 원천 검수로 세지 않는다. */
final class Change305Verifier implements SourceChangeVerifier {
    volatile String generation="key-1",epoch="epoch-1",mode="MONOTONIC";
    volatile Duration lifetime=Duration.ofMinutes(2);
    volatile CountDownLatch entered,release;
    private final Set<String> nonces=ConcurrentHashMap.newKeySet();
    public Configuration configuration(){return new Configuration("test-hr","synthetic-hr",epoch,generation);}
    public boolean testOnly(){return true;}
    Map<String,String> signed(String body,UUID binding) {
        var headers=new LinkedHashMap<String,String>();headers.put("X-AIHUB-Source","test-hr");headers.put("X-Nonce",UUID.randomUUID().toString());headers.put("X-Time",Instant.now().toString());headers.put("X-Binding",binding.toString());
        headers.put("X-Signature",signature("POST",SourceChangeService.PATH,headers.get("X-Nonce"),headers.get("X-Time"),headers.get("X-Binding"),body));return headers;
    }
    private String signature(String method,String path,String nonce,String time,String binding,String body) {
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(("synthetic-test-secret-"+generation).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(ExternalUserChange.digest("test-hr",epoch,method,path,nonce,time,binding,body).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.GeneralSecurityException failure){throw new IllegalStateException("합성 서명 실패",failure);}
    }
    private String one(Map<String,List<String>> headers,String key){var values=headers.get(key);if(values==null||values.size()!=1)throw new ChangeFailure(401,"SOURCE_PROOF_REJECTED");return values.getFirst();}
    public Envelope verifyEnvelope(String method,String path,Map<String,List<String>> headers,byte[] body,ExternalUserChange event) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("검증이 DB 잠금 안에 있다");
        var nonce=one(headers,"x-nonce");var time=one(headers,"x-time");var binding=one(headers,"x-binding");
        var signature=signature(method,path,nonce,time,binding,new String(body,StandardCharsets.UTF_8));
        if(!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),one(headers,"x-signature").getBytes(StandardCharsets.US_ASCII))||!nonces.add(nonce))throw new ChangeFailure(401,"SOURCE_PROOF_REJECTED");
        var at=Instant.parse(time);return new Envelope(event.businessDigest(),UUID.fromString(binding),at,at.plus(lifetime),ExternalUserChange.digest(signature));
    }
    public Freshness verifyFreshness(ExternalUserChange event,Envelope envelope,Binding binding,Cursor cursor) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("최신성 확인이 DB 잠금 안에 있다");
        var signal=entered;var wait=release;if(signal!=null){signal.countDown();await(wait);}
        long next=Long.parseLong(event.sourceVersion().substring(2));long before=cursor.sourceVersion()==null?0:Long.parseLong(cursor.sourceVersion().substring(2));
        return new Freshness(binding.bindingKey(),cursor.version(),event.sourceVersion(),mode,next<before?Order.PAST:Order.ADVANCE,ExternalUserChange.digest("fresh",event.businessDigest(),Long.toString(cursor.version()),binding.bindingKey().toString()),envelope.expiresAt());
    }
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("합성 검증 대기 초과");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("합성 검증 중단",failure);}}
}
