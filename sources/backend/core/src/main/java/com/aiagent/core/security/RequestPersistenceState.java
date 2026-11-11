// @feat FEAT-301
package com.aiagent.core.security;

import java.util.Objects;

/** 한 요청의 명시 저장·최종 커밋·단일 응답 발급 상태. DB 롤백 뒤에도 재사용하지 않는다. */
public final class RequestPersistenceState {
    public enum State { OPEN, PENDING_COMMIT, PERSISTED, CLOSED }
    public enum Completion { COMMITTED, ROLLED_BACK, UNKNOWN }
    public enum Capability { READ, PREPARE_CSRF, LOGIN, RECOVERY, PASSWORD_CHANGE, RESET, LOGOUT }
    private final AuthDomain domain;
    private final String requestTraceId;
    private final Capability capability;
    private final SessionAdmissions.Producer producer;
    private State state = State.OPEN;
    private boolean closing;
    private boolean committed;
    private boolean delivered;
    private Object attempt;

    public RequestPersistenceState(AuthDomain domain, String requestTraceId) {
        this(domain,requestTraceId,Capability.READ,null);
    }
    public RequestPersistenceState(AuthDomain domain, String requestTraceId, Capability capability,
                                   SessionAdmissions.Producer producer) {
        this.domain = Objects.requireNonNull(domain);
        this.requestTraceId = Objects.requireNonNull(requestTraceId);
        this.capability = Objects.requireNonNull(capability);
        this.producer = producer;
        if (producer != null && producer.domain() != domain) throw new IllegalArgumentException("검증 생산자의 영역이 다르다");
    }
    public AuthDomain domain() { return domain; }
    public String requestTraceId() { return requestTraceId; }
    public Capability capability() { return capability; }
    public boolean producedBy(SessionAdmissions.Producer expected) { return producer == expected; }
    public synchronized State state() { return state; }
    public synchronized boolean canFlush() { return state == State.OPEN; }
    public synchronized boolean beginAttempt(Object identity) {
        Objects.requireNonNull(identity);
        if(state!=State.OPEN || attempt!=null && attempt!=identity)throw new IllegalStateException("다른 트랜잭션 시도에서 요청을 재사용할 수 없다");
        if(attempt!=null)return false;
        attempt=identity;return true;
    }
    synchronized Object attemptIdentity() { return attempt; }
    public synchronized void completeAttempt(Completion completion) {
        if(state==State.PENDING_COMMIT)complete(completion);
        else if(state==State.OPEN){committed=false;state=State.CLOSED;}
    }
    public synchronized void beginMutation(boolean closesSession) {
        if (state != State.OPEN) throw new IllegalStateException("이미 마감한 요청은 다시 저장할 수 없다");
        closing = closesSession;
        state = State.PENDING_COMMIT;
    }
    public synchronized void complete(Completion completion) {
        if (state != State.PENDING_COMMIT) throw new IllegalStateException("저장 중인 요청이 아니다");
        committed = completion == Completion.COMMITTED;
        state = committed && !closing ? State.PERSISTED : State.CLOSED;
    }
    public synchronized void claimDelivery() {
        if (!committed || delivered) throw new IllegalStateException("확정된 응답만 한 번 발급할 수 있다");
        delivered = true;
    }
    @Override public String toString() { return "RequestPersistenceState[" + domain + "," + state + "]"; }
}
