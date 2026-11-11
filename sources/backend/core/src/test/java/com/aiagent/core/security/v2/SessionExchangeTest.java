// @feat FEAT-301
package com.aiagent.core.security.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionExchangeTest {
    private SessionExchange request(){return new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN);}
    @Test @DisplayName("TC-30123 DB확정만으로 Redis 쿠키 발급을 허용하지 않는다")
    void deliveryNeedsDatabaseStoreAndActivation() {
        var request=request();request.beginAttempt(new Object());request.mutation();request.completeDatabase(true);
        assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
        request.storeConfirmed();assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
        request.activated();request.claimDelivery();
        assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("TC-30123 rollback와UNKNOWN 뒤 admission/요청 재사용·쿠키발급은 없다")
    void rollbackClosesAttemptBeforeMutationToo() {
        for(boolean mutate:new boolean[]{false,true}){
            var request=request();request.beginAttempt(new Object());if(mutate)request.mutation();request.completeDatabase(false);
            assertThat(request.state()).isEqualTo(SessionExchange.State.CLOSED);
            assertThatThrownBy(()->request.beginAttempt(new Object())).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(request::storeConfirmed).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test @DisplayName("TC-30127 같은DB시도만 재진입하며 내부REQUIRES_NEW는 거부")
    void attemptIdentityCannotChange() {
        var request=request();var identity=new Object();
        assertThat(request.beginAttempt(identity)).isTrue();assertThat(request.beginAttempt(identity)).isFalse();
        assertThatThrownBy(()->request.beginAttempt(new Object())).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("TC-30120 DB확정뒤출력실패는요청을닫고재발급하지않는다")
    void closeStopsAllLaterDelivery() {
        var request=request();request.beginAttempt(new Object());request.mutation();request.completeDatabase(true);
        request.storeConfirmed();request.activated();request.close();
        assertThatThrownBy(request::claimDelivery).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::mutation).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("TC-30123 무변경 공개읽기 commit은 세션 발급으로 승격되지 않는다")
    void readOnlyCommitNeverBecomesDelivery() {
        var request=request();request.beginAttempt(new Object());request.completeDatabase(true);
        assertThat(request.state()).isEqualTo(SessionExchange.State.CLOSED);
        assertThatThrownBy(request::storeConfirmed).isInstanceOf(IllegalStateException.class);
    }
}
