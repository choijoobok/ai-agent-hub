// @feat FEAT-301
package com.aiagent.core.security;

import static com.aiagent.core.security.SessionPersistencePort.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionContractTest {
    private static final Instant NOW=Instant.parse("2026-11-10T01:00:00Z");
    private RequestPersistenceState request(AuthDomain domain,RequestPersistenceState.Capability capability,SessionAdmissions.Producer producer) {
        var request=new RequestPersistenceState(domain,"trace",capability,producer);request.beginAttempt(new Object());return request;
    }
    private Identity identity(AuthDomain domain) {
        return new Identity(new PrincipalRef(domain,"7"),AuthLevel.AUTHENTICATED,"LOCAL",domain==AuthDomain.PORTAL?1L:null,NOW,"name","ko");
    }

    @Test void completionAndSingleDeliveryStateMatrix() {
        for(var completion:RequestPersistenceState.Completion.values())for(boolean closing:new boolean[]{false,true}) {
            var request=new RequestPersistenceState(AuthDomain.ADMIN,"trace");
            assertThat(request.domain()).isEqualTo(AuthDomain.ADMIN);assertThat(request.requestTraceId()).isEqualTo("trace");
            assertThat(request.capability()).isEqualTo(RequestPersistenceState.Capability.READ);
            assertThat(request.canFlush()).isTrue();assertThat(request.state()).isEqualTo(RequestPersistenceState.State.OPEN);
            assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->request.complete(completion)).isInstanceOf(IllegalStateException.class);
            request.beginMutation(closing);
            assertThat(request.canFlush()).isFalse();assertThat(request.state()).isEqualTo(RequestPersistenceState.State.PENDING_COMMIT);
            assertThatThrownBy(()->request.beginMutation(false)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
            request.complete(completion);
            assertThat(request.canFlush()).isFalse();
            assertThat(request.state()).isEqualTo(completion==RequestPersistenceState.Completion.COMMITTED && !closing?RequestPersistenceState.State.PERSISTED:RequestPersistenceState.State.CLOSED);
            assertThatThrownBy(()->request.complete(completion)).isInstanceOf(IllegalStateException.class);
            if(completion==RequestPersistenceState.Completion.COMMITTED)request.claimDelivery();
            assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->request.beginMutation(true)).isInstanceOf(IllegalStateException.class);
            assertThat(request.toString()).contains("ADMIN",request.state().name());
        }
    }

    @Test void operationAndProducerBindEveryAdmission() {
        for(AuthDomain domain:AuthDomain.values())for(var capability:RequestPersistenceState.Capability.values()) {
            var producer=SessionAdmissions.producer(domain,"registered-current-credentials");
            var request=request(domain,capability,producer);
            var ref=new SessionRef(domain,"primary","session-secret");
            var identity=identity(domain);
            assertThat(producer.domain()).isEqualTo(domain);assertThat(producer.toString()).contains(domain.name(),"registered-current-credentials");
            if(capability==RequestPersistenceState.Capability.PREPARE_CSRF) {
                var anonymous=SessionAdmissions.anonymous(request,NOW.plusSeconds(30));
                assertThat(anonymous.sessionExpiresAt()).isEqualTo(NOW.plusSeconds(30));
                assertThat(anonymous.consume(request,NOW)).isTrue();assertThat(anonymous.consume(request,NOW)).isFalse();
            } else assertThatThrownBy(()->SessionAdmissions.anonymous(request,NOW.plusSeconds(30))).isInstanceOf(IllegalArgumentException.class);
            if(capability==RequestPersistenceState.Capability.LOGIN || capability==RequestPersistenceState.Capability.RECOVERY) {
                var admission=producer.login(request,ref,identity,NOW.plusSeconds(30));
                assertThat(admission.entry()).isEqualTo(ref);assertThat(admission.identity()).isEqualTo(identity);
                assertThat(admission.consume(new RequestPersistenceState(domain,"trace"),NOW)).isFalse();
                assertThat(admission.consume(request,NOW.plusSeconds(30))).isFalse();
                assertThat(admission.consume(request,NOW)).isTrue();assertThat(admission.consume(request,NOW)).isFalse();
                assertThat(admission.toString()).doesNotContain("session-secret");
            } else assertThatThrownBy(()->producer.login(request,ref,identity,NOW.plusSeconds(30))).isInstanceOf(IllegalArgumentException.class);
            if(capability==RequestPersistenceState.Capability.PASSWORD_CHANGE || capability==RequestPersistenceState.Capability.RESET) {
                var admission=producer.rotation(request,ref,identity,NOW.plusSeconds(30));
                assertThat(admission.expected()).isEqualTo(ref);assertThat(admission.identity()).isEqualTo(identity);
                assertThat(admission.toString()).isEqualTo("RotationAdmission[보호됨]");
                assertThat(admission.consume(request,NOW)).isTrue();assertThat(admission.consume(request,NOW)).isFalse();
            } else assertThatThrownBy(()->producer.rotation(request,ref,identity,NOW.plusSeconds(30))).isInstanceOf(IllegalArgumentException.class);
            var impostor=SessionAdmissions.producer(domain,"registered-current-credentials");
            assertThatThrownBy(()->impostor.login(request,ref,identity,NOW.plusSeconds(30))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void admissionCannotCrossDomainOrRollbackAttempt() {
        var producer=SessionAdmissions.producer(AuthDomain.ADMIN,"admin");
        var request=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.LOGIN,producer);
        assertThatThrownBy(()->new RequestPersistenceState(AuthDomain.PORTAL,"trace",RequestPersistenceState.Capability.LOGIN,producer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->producer.login(request,new SessionRef(AuthDomain.PORTAL,"p","s"),identity(AuthDomain.ADMIN),NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->producer.login(request,null,identity(AuthDomain.PORTAL),NOW)).isInstanceOf(IllegalArgumentException.class);
        var changing=request(AuthDomain.ADMIN,RequestPersistenceState.Capability.PASSWORD_CHANGE,producer);
        assertThatThrownBy(()->producer.rotation(changing,new SessionRef(AuthDomain.PORTAL,"p","s"),identity(AuthDomain.ADMIN),NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->producer.rotation(changing,new SessionRef(AuthDomain.ADMIN,"p","s"),identity(AuthDomain.PORTAL),NOW)).isInstanceOf(IllegalArgumentException.class);
        var admission=producer.login(request,null,identity(AuthDomain.ADMIN),NOW.plusSeconds(30));
        request.beginMutation(false);request.complete(RequestPersistenceState.Completion.ROLLED_BACK);
        assertThat(admission.consume(request,NOW)).isFalse();assertThat(request.producedBy(producer)).isTrue();
    }

    @Test void secretDeliveryIsOwnerBoundAndConsumedBeforeWriterFailure() {
        var request=new RequestPersistenceState(AuthDomain.PORTAL,"trace");
        var issue=new CsrfIssue(request,"session-secret","csrf-secret");
        var deliveries=new AtomicInteger();
        assertThatThrownBy(()->issue.emit(new RequestPersistenceState(AuthDomain.PORTAL,"trace"),(d,s,c)->deliveries.incrementAndGet())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->issue.emit(request,(d,s,c)->deliveries.incrementAndGet())).isInstanceOf(IllegalStateException.class);
        request.beginMutation(false);request.complete(RequestPersistenceState.Completion.COMMITTED);
        assertThatThrownBy(()->issue.emit(request,(d,s,c)->{
            assertThat(d).isEqualTo(AuthDomain.PORTAL);assertThat(s).isEqualTo("session-secret");assertThat(c).isEqualTo("csrf-secret");
            deliveries.incrementAndGet();throw new IllegalStateException("응답 유실");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->issue.emit(request,(d,s,c)->deliveries.incrementAndGet())).isInstanceOf(IllegalStateException.class);
        assertThat(deliveries).hasValue(1);assertThat(issue.toString()).isEqualTo("CsrfIssue[보호됨]");
    }

    @Test void principalAndReferenceHaveDomainAwareStableIdentity() {
        var admin=new PrincipalRef(AuthDomain.ADMIN,"7");var portal=new PrincipalRef(AuthDomain.PORTAL,"7");
        assertThat(admin.indexKey()).isEqualTo("7");assertThat(portal.indexKey()).isEqualTo("PORTAL:7");
        assertThat(admin).isNotEqualTo(portal);
        for(String value:new String[]{null,""," ","x".repeat(81)})assertThatThrownBy(()->new PrincipalRef(AuthDomain.ADMIN,value)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PrincipalRef(AuthDomain.ADMIN,"x".repeat(80)).subjectId()).hasSize(80);
        var ref=new SessionRef(AuthDomain.ADMIN,"p","s");
        assertThat(ref).isEqualTo(new SessionRef(AuthDomain.ADMIN,"p","s")).isNotEqualTo(null).isNotEqualTo("s");
        assertThat(ref).isNotEqualTo(new SessionRef(AuthDomain.PORTAL,"p","s")).isNotEqualTo(new SessionRef(AuthDomain.ADMIN,"other","s")).isNotEqualTo(new SessionRef(AuthDomain.ADMIN,"p","other"));
        assertThat(ref.hashCode()).isEqualTo(new SessionRef(AuthDomain.ADMIN,"p","s").hashCode());
        assertThat(ref.hashCode()).isEqualTo(java.util.Objects.hash(AuthDomain.ADMIN,"p","s"));
        assertThat(ref.domain()).isEqualTo(AuthDomain.ADMIN);assertThat(ref.primaryId()).isEqualTo("p");assertThat(ref.sessionId()).isEqualTo("s");
        assertThat(ref.toString()).isEqualTo("SessionRef[ADMIN,보호됨]");
    }

    @Test void identityAndDeltaOnlyCarryTypedValues() {
        assertThat(identity(AuthDomain.ADMIN).credentialVersion()).isNull();assertThat(identity(AuthDomain.PORTAL).credentialVersion()).isEqualTo(1);
        assertThatThrownBy(()->new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.ANONYMOUS,"LOCAL",null,NOW,"n","ko")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.AUTHENTICATED,"LOCAL",1L,NOW,"n","ko")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Identity(new PrincipalRef(AuthDomain.PORTAL,"7"),AuthLevel.AUTHENTICATED,"EXTERNAL",1L,NOW,"n","ko")).isInstanceOf(IllegalArgumentException.class);
        for(Long version:new Long[]{null,0L,-1L})assertThatThrownBy(()->new Identity(new PrincipalRef(AuthDomain.PORTAL,"7"),AuthLevel.AUTHENTICATED,"LOCAL",version,NOW,"n","ko")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Identity(new PrincipalRef(AuthDomain.PORTAL,"7"),AuthLevel.PASSWORD_CHANGE_REQUIRED,"EXTERNAL",null,NOW,"n","en").authMode()).isEqualTo("EXTERNAL");
        assertThatThrownBy(()->new Identity(new PrincipalRef(AuthDomain.ADMIN,"7"),AuthLevel.AUTHENTICATED,"unknown",null,NOW,"n","ko")).isInstanceOf(IllegalArgumentException.class);
        var source=new java.util.HashSet<>(Set.of("MENU:R"));var delta=new SessionDelta(NOW,"name","ko",source,true);source.clear();
        assertThat(delta.authoritySnapshot()).containsExactly("MENU:R");assertThat(delta.lastAccessedAt()).isEqualTo(NOW);
        assertThat(delta.displayName()).isEqualTo("name");assertThat(delta.locale()).isEqualTo("ko");assertThat(delta.dirtySeen()).isTrue();
        var absent=Result.of(Outcome.ABSENT);assertThat(absent.outcome()).isEqualTo(Outcome.ABSENT);assertThat(absent.ref()).isNull();
        assertThat(absent.identity()).isNull();assertThat(absent.expiresAt()).isNull();assertThat(absent.csrf()).isNull();assertThat(absent.toString()).contains("ABSENT");
    }

    @Test void transactionAttemptIsUniqueAndClosesEvenBeforeMutation() {
        for(var completion:RequestPersistenceState.Completion.values()) {
            var state=new RequestPersistenceState(AuthDomain.ADMIN,"trace");var token=new Object();
            assertThat(state.beginAttempt(token)).isTrue();assertThat(state.attemptIdentity()).isSameAs(token);
            assertThat(state.beginAttempt(token)).isFalse();
            assertThatThrownBy(()->state.beginAttempt(new Object())).isInstanceOf(IllegalStateException.class);
            state.completeAttempt(completion);assertThat(state.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
            assertThatThrownBy(state::claimDelivery).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->state.beginAttempt(token)).isInstanceOf(IllegalStateException.class);
            state.completeAttempt(completion);assertThat(state.state()).isEqualTo(RequestPersistenceState.State.CLOSED);
            var pending=new RequestPersistenceState(AuthDomain.PORTAL,"trace");pending.beginAttempt(new Object());pending.beginMutation(false);
            pending.completeAttempt(completion);
            assertThat(pending.state()).isEqualTo(completion==RequestPersistenceState.Completion.COMMITTED?RequestPersistenceState.State.PERSISTED:RequestPersistenceState.State.CLOSED);
            pending.completeAttempt(RequestPersistenceState.Completion.UNKNOWN);
            assertThat(pending.state()).isEqualTo(completion==RequestPersistenceState.Completion.COMMITTED?RequestPersistenceState.State.PERSISTED:RequestPersistenceState.State.CLOSED);
        }
        var producer=SessionAdmissions.producer(AuthDomain.ADMIN,"producer");
        var unbound=new RequestPersistenceState(AuthDomain.ADMIN,"trace",RequestPersistenceState.Capability.LOGIN,producer);
        assertThatThrownBy(()->producer.login(unbound,null,identity(AuthDomain.ADMIN),NOW.plusSeconds(1))).isInstanceOf(NullPointerException.class);
    }
}
