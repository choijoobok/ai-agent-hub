// @feat FEAT-305
package com.aiagent.portal.change;

/** 외부 증거/본문/원천 예외를 보존하지 않는 안전한 수신 실패. */
public final class ChangeFailure extends RuntimeException {
    private static final java.util.Set<String> CODES=java.util.Set.of("INVALID_BODY","STORE_UNAVAILABLE","CURSOR_CHANGED","ROUTE_DENIED","SOURCE_PROOF_REQUIRED","VERIFIER_UNAVAILABLE","SOURCE_UNREGISTERED","SOURCE_CHANGED","SOURCE_PROOF_REJECTED","VERIFIER_CHANGED","EVIDENCE_EXPIRED","BINDING_CHANGED","FRESHNESS_REQUIRED","RECEIPT_UNAVAILABLE","EXPIRED","EVENT_BODY_CONFLICT","PORTAL_REVISION_CHANGED","SOURCE_VERSION_REPLAY","NOTIFICATION_UNAVAILABLE");
    private final int status;private final String code;
    public ChangeFailure(int status,String code){super("외부 사건 수신을 완료할 수 없다");this.status=java.util.Set.of(400,401,403,409,422,503).contains(status)?status:503;this.code=code!=null&&CODES.contains(code)?code:"RECEIPT_UNAVAILABLE";}
    public int status(){return status;}
    public String code(){return code;}
}
