// @feat FEAT-301
package com.aiagent.portal.auth;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.portal.api.auth.PortalAuthController;
import com.aiagent.core.security.portal.PortalAccounts;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class PortalAuthContractTest {
    @Test @DisplayName("TC-3015 TC-30114 인증 입력의 주체 주입·비밀 문자열 출력과 외부/상대 영역 복귀 거부")
    void rejectInjectedIdentityAndUnsafeReturn() {
        var json=JsonMapper.builder().build();
        assertThatThrownBy(()->json.readValue("{\"loginId\":\"user\",\"password\":\"secret-marker\",\"authDomain\":\"ADMIN\"}",PortalAuthController.Login.class));
        assertThatThrownBy(()->json.readValue("{\"currentPassword\":\"old-marker\",\"newPassword\":\"new-marker\",\"portalUserId\":\"999\"}",PortalAuthController.Password.class));
        assertThat(new PortalAuthController.Login("user","secret-marker").toString()).doesNotContain("secret-marker");
        for(String path:List.of("https://evil.invalid","//evil.invalid","/admin","/%70ortal/chat","/portal/../admin","/portal/login","/portal/chat?secret=x","/portal\\admin"))assertThat(PortalExternalAuthService.safeReturnPath(path)).isEqualTo("/");
        assertThat(PortalExternalAuthService.safeReturnPath("/portal/chat")).isEqualTo("/portal/chat");
    }
    @Test @DisplayName("TC-3017 TC-30115 EXTERNAL 검증기 미설치·제품의 시험 검증기와 잘못된 기간 설정 거부")
    void externalDeploymentMustBeExplicit() {
        var source=org.mockito.Mockito.mock(DataSource.class);var settings=new PortalAuthSettings("EXTERNAL",Duration.ofMinutes(30),1,false,Duration.ofMinutes(10),Duration.ofMinutes(5));
        var environment=new MockEnvironment().withProperty("app.auth.session.domains.portal.enabled","true");
        assertThatThrownBy(()->new PortalExternalAuthService(source,null,null,null,settings,null,List.of(),environment)).isInstanceOf(IllegalStateException.class);
        var test=new ExternalAuthProvider(){public String sourceKey(){return "fixture";}public String issuer(){return "issuer";}public String audience(){return "audience";}public boolean testOnly(){return true;}public Proof verify(Object raw){throw new UnsupportedOperationException("호출 금지");}};
        assertThatThrownBy(()->new PortalExternalAuthService(source,null,null,null,settings,null,List.of(test),environment)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new PortalAuthSettings("LOCAL",Duration.ZERO,1,false,Duration.ofMinutes(10),Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PortalAuthSettings("LOCAL",Duration.ofMinutes(30),1,false,Duration.ofMinutes(2),Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
    }
}
