// @feat FEAT-301
package com.aiagent.core.security.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionAdmissionsV2Test {
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");
    private Identity identity(){return new Identity(new PrincipalRef(AuthDomain.ADMIN,"admin"),AuthLevel.AUTHENTICATED,"LOCAL",null,now,"관리자","ko");}
    private SessionV2.Ref ref(){return new SessionV2.Ref(AuthDomain.ADMIN,1,1,UUID.randomUUID());}
    @Test @DisplayName("TC-30123 정확CSRF와변경증거의종류/entry/검증주체를저장층에전달한다")
    void anonymousAndRotationEvidenceKeepVerifiedIdentity(){
        var request=new SessionExchange(AuthDomain.ADMIN,"csrf-trace",Capability.PREPARE_CSRF);request.beginAttempt(new Object());var anonymous=SessionAdmissionsV2.anonymous(request,now.plusSeconds(60));assertThat(anonymous.kind()).isEqualTo(SessionAdmissionsV2.Kind.ANONYMOUS);assertThat(anonymous.entry()).isNull();assertThat(anonymous.identity()).isNull();assertThat(anonymous.consume(request,now)).isTrue();assertThat(anonymous.toString()).isEqualTo("SessionAdmission[보호됨]");
        var producer=SessionAdmissionsV2.producer(AuthDomain.ADMIN,"admin-local");assertThat(producer.domain()).isEqualTo(AuthDomain.ADMIN);assertThat(producer.toString()).contains("CredentialProducer");var rotation=new SessionExchange(AuthDomain.ADMIN,"rotation-trace",Capability.PASSWORD_CHANGE,producer);rotation.beginAttempt(new Object());var expected=ref();var identity=identity();var proof=producer.rotation(rotation,expected,identity,now.plusSeconds(60));assertThat(proof.kind()).isEqualTo(SessionAdmissionsV2.Kind.ROTATION);assertThat(proof.entry()).isEqualTo(expected);assertThat(proof.identity()).isEqualTo(identity);assertThat(proof.consume(rotation,now)).isTrue();assertThat(rotation.traceId()).isEqualTo("rotation-trace");
        assertThatThrownBy(()->SessionAdmissionsV2.anonymous(rotation,now.plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("TC-30123 v2 admission은 정확생산자·단일요청/DB시도에서1회만소비한다")
    void credentialIsBoundAndSingleUse() {
        var producer=SessionAdmissionsV2.producer(AuthDomain.ADMIN,"admin-local");
        var request=new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN,producer);request.beginAttempt(new Object());
        var proof=producer.login(request,ref(),identity(),now.plusSeconds(60));
        var other=new SessionExchange(AuthDomain.ADMIN,"other",Capability.LOGIN,producer);other.beginAttempt(new Object());
        assertThat(proof.consume(other,now)).isFalse();assertThat(proof.consume(request,now)).isTrue();assertThat(proof.consume(request,now)).isFalse();
    }
    @Test @DisplayName("TC-30123 DB확정/rollback뒤끝난admission을Redis후처리에서재사용할수없다")
    void finishedAttemptCannotBeReused() {
        for(boolean committed:new boolean[]{true,false}){
            var producer=SessionAdmissionsV2.producer(AuthDomain.ADMIN,"admin-local");
            var request=new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN,producer);request.beginAttempt(new Object());
            var proof=producer.login(request,ref(),identity(),now.plusSeconds(60));request.completeDatabase(committed);
            assertThat(proof.consume(request,now)).isFalse();
        }
    }
    @Test @DisplayName("TC-30128 잘못된생산자/동작/영역/만료·LOGIN회전증거는거부한다")
    void rejectsWrongProducerOperationDomainAndExpiry() {
        var producer=SessionAdmissionsV2.producer(AuthDomain.ADMIN,"admin-local");
        var request=new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN,producer);request.beginAttempt(new Object());
        assertThatThrownBy(()->SessionAdmissionsV2.producer(AuthDomain.ADMIN,"other").login(request,ref(),identity(),now.plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->producer.rotation(request,ref(),identity(),now.plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
        assertThat(producer.login(request,ref(),identity(),now).consume(request,now)).isFalse();
        var portal=new SessionV2.Ref(AuthDomain.PORTAL,1,1,UUID.randomUUID());
        assertThatThrownBy(()->producer.login(request,portal,identity(),now.plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
    }
}
