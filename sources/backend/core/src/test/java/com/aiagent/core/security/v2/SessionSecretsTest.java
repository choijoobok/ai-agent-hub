// @feat FEAT-301
package com.aiagent.core.security.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionSecretsTest {
    @Test @DisplayName("TC-30128 외부인증쿠키는43자이며JDBC내부UUID를발급할수없다")
    void rejectsInternalPhysicalCookie(){
        var request=new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN);
        var csrf=new SessionV2.Secret("c".repeat(43));
        for(String cookie:new String[]{UUID.randomUUID().toString(),"", "a".repeat(44),"%".repeat(43)})
            assertThatThrownBy(()->new SessionV2.CookieIssue(request,cookie,csrf)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("TC-30128 csrf상수시간대조·마스킹·단일요청쿠키발급")
    void secretAndIssueAreRequestBound(){
        var request=new SessionExchange(AuthDomain.ADMIN,"trace",Capability.LOGIN);
        var csrf=new SessionV2.Secret("c".repeat(43));
        assertThat(csrf.matches(null)).isFalse();assertThat(csrf.matches("c".repeat(43))).isTrue();assertThat(csrf.matches("wrong")).isFalse();
        var issue=new SessionV2.CookieIssue(request,"a".repeat(43),csrf);
        assertThat(issue.toString()).doesNotContain("a".repeat(43),"c".repeat(43));
        assertThatThrownBy(()->issue.emit(new SessionExchange(AuthDomain.ADMIN,"other",Capability.LOGIN),(d,c,t)->{})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->issue.emit(request,(d,c,t)->{})).isInstanceOf(IllegalStateException.class);
        request.beginAttempt(new Object());request.mutation();request.completeDatabase(true);request.storeConfirmed();request.activated();
        String[] observed=new String[2];issue.emit(request,(d,c,t)->{observed[0]=c;observed[1]=t;});assertThat(observed).containsExactly("a".repeat(43),"c".repeat(43));
        assertThatThrownBy(()->issue.emit(request,(d,c,t)->{})).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("TC-30128 전용codec비밀쓰기·동등성·로그마스킹과영역종료쿠키")
    void dedicatedSecretEncodingAndLogoutDelivery()throws Exception{
        var secret=new SessionV2.Secret("c".repeat(43));assertThat(secret).isEqualTo(new SessionV2.Secret("c".repeat(43))).isNotEqualTo(new SessionV2.Secret("x".repeat(43))).isNotEqualTo("c".repeat(43));assertThat(secret.toString()).isEqualTo("SessionSecret[보호됨]");
        var bytes=new java.io.ByteArrayOutputStream();secret.writeTo(new java.io.DataOutputStream(bytes));assertThat(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())).readUTF()).isEqualTo("c".repeat(43));
        var logout=new SessionExchange(AuthDomain.ADMIN,"logout",Capability.LOGOUT);var issue=SessionV2.CookieIssue.expiration(logout);logout.beginAttempt(new Object());logout.mutation();logout.completeDatabase(true);logout.storeConfirmed();logout.activated();String[] observed=new String[2];issue.emit(logout,(d,c,t)->{assertThat(d).isEqualTo(AuthDomain.ADMIN);observed[0]=c;observed[1]=t;});assertThat(observed).containsExactly("","");
        assertThatThrownBy(()->SessionV2.CookieIssue.expiration(new SessionExchange(AuthDomain.ADMIN,"bad",Capability.READ))).isInstanceOf(IllegalArgumentException.class);
    }
}
