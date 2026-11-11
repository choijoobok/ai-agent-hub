// @feat FEAT-301
package com.aiagent.core.security.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import java.util.Objects;

/** 단일 HTTP 요청의 DB 시도·선택 store 확인·발급 가능 상태. */
public final class SessionExchange {
    public enum State { OPEN,DB_PENDING,DB_COMMITTED,STORE_CONFIRMED,DELIVERABLE,CLOSED }
    private final AuthDomain domain;
    private final String traceId;
    private final Capability capability;
    private State state=State.OPEN;
    private Object attempt;
    private final SessionAdmissionsV2.Producer producer;
    public SessionExchange(AuthDomain domain,String traceId,Capability capability){
        this(domain,traceId,capability,null);
    }
    public SessionExchange(AuthDomain domain,String traceId,Capability capability,SessionAdmissionsV2.Producer producer){
        this.domain=Objects.requireNonNull(domain);this.traceId=Objects.requireNonNull(traceId);this.capability=Objects.requireNonNull(capability);
        this.producer=producer;
        if(traceId.isBlank())throw new IllegalArgumentException("요청 추적 식별자가 필요하다");
    }
    public AuthDomain domain(){return domain;}
    public String traceId(){return traceId;}
    public Capability capability(){return capability;}
    public boolean producedBy(SessionAdmissionsV2.Producer expected){return producer==expected;}
    public synchronized State state(){return state;}
    public synchronized Object attempt(){return attempt;}
    public synchronized boolean beginAttempt(Object identity){
        Objects.requireNonNull(identity);
        if(attempt==identity && (state==State.OPEN || state==State.DB_PENDING))return false;
        if(state!=State.OPEN || attempt!=null)throw new IllegalStateException("요청은 같은 단일 DB 시도만 사용할 수 있다");
        attempt=identity;return true;
    }
    public synchronized void mutation(){
        if(state!=State.OPEN || attempt==null)throw new IllegalStateException("현재 DB 시도 안에서 한 번만 저장한다");
        state=State.DB_PENDING;
    }
    public synchronized void completeDatabase(boolean committed){
        if(attempt==null || state!=State.OPEN && state!=State.DB_PENDING)throw new IllegalStateException("진행 중인 DB 시도가 아니다");
        attempt=null;state=committed&&state==State.DB_PENDING?State.DB_COMMITTED:State.CLOSED;
    }
    private void advance(State expected,State next){
        if(state!=expected)throw new IllegalStateException("발급 확인 순서가 올바르지 않다");state=next;
    }
    public synchronized void storeConfirmed(){advance(State.DB_COMMITTED,State.STORE_CONFIRMED);}
    public synchronized void activated(){advance(State.STORE_CONFIRMED,State.DELIVERABLE);}
    public synchronized void claimDelivery(){advance(State.DELIVERABLE,State.CLOSED);}
    public synchronized void close(){attempt=null;state=State.CLOSED;}
}
