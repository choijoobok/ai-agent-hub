// @feat FEAT-301
package com.aiagent.core.security.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.SessionAdmissionLedger.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionGrantTest {
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");
    private Grant grant(Status state,AuthLevel level,Instant expiry,Instant publish,Instant first,Instant confirm){return new Grant(new Ref(AuthDomain.ADMIN,1,1,UUID.randomUUID()),Provider.JDBC,"hash","ref",level==AuthLevel.ANONYMOUS?null:"owner",level,state,level==AuthLevel.ANONYMOUS?null:now,now,now,expiry,publish,first,confirm,null);}
    @Test void currentUsesStrictDbDeadlineAndFirstUse(){
        assertThat(grant(Status.ACTIVE,AuthLevel.AUTHENTICATED,now.plusSeconds(1),null,null,now.plusSeconds(1)).current(now)).isTrue();
        for(Status status:Status.values())for(AuthLevel level:AuthLevel.values()){
            assertThat(grant(status,level,now,null,now,now.plusSeconds(1)).current(now)).isFalse();
            assertThat(grant(status,level,now.minusSeconds(1),null,now,now.plusSeconds(1)).current(now)).isFalse();
            assertThat(grant(status,level,now.plusSeconds(1),null,null,now).current(now)).isEqualTo(status==Status.ACTIVE&&level==AuthLevel.ANONYMOUS);
            assertThat(grant(status,level,now.plusSeconds(1),null,null,null).current(now)).isEqualTo(status==Status.ACTIVE&&level==AuthLevel.ANONYMOUS);
            assertThat(grant(status,level,now.plusSeconds(1),null,now,null).current(now)).isEqualTo(status==Status.ACTIVE);
        }
    }
    @Test void onlyCurrentAuthenticationOrLivePendingReservesSlot(){
        for(Status status:Status.values())for(AuthLevel level:AuthLevel.values()){
            assertThat(grant(status,level,now.plusSeconds(1),now.plusSeconds(1),now,null).reservesSlot(now)).isEqualTo(level!=AuthLevel.ANONYMOUS&&status!=Status.REVOKED);
            assertThat(grant(status,level,now,now.plusSeconds(1),now,null).reservesSlot(now)).isFalse();
            assertThat(grant(status,level,now.plusSeconds(1),now,null,null).reservesSlot(now)).isFalse();
            assertThat(grant(status,level,now.plusSeconds(1),null,null,null).reservesSlot(now)).isFalse();
        }
    }
}
