// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class SessionV2AssemblyTest {
    private MockEnvironment environment(String provider){return new MockEnvironment().withProperty("app.auth.session.provider",provider).withProperty("app.auth.session.origin","http://127.0.0.1:8080").withProperty("app.auth.session.local-http","true").withProperty("app.auth.session.epoch",UUID.randomUUID().toString()).withProperty("app.auth.session.store-instance","fixture");}
    @Test @DisplayName("TC-30121 같은artifact의기동설정은jdbc/redis정확선택과ADMIN기본·Portal닫힘이다")
    void settingsChooseOnlyOneProvider(){
        for(String provider:new String[]{"jdbc","redis"}){
            var settings=SessionV2Assembly.Configuration.from(environment(provider));assertThat(settings.provider()).isEqualTo(Provider.valueOf(provider.toUpperCase()));
            assertThat(settings.domains()).containsOnlyKeys(AuthDomain.ADMIN);assertThat(settings.timeout()).hasMinutes(30);
        }
        var defaults=environment("jdbc");defaults.getPropertySources().remove("mockProperties");
        var global=new MockEnvironment().withProperty("app.auth.session.epoch",UUID.randomUUID().toString()).withProperty("app.auth.session.store-instance","global").withProperty("app.auth.session.origin","https://hub.example");
        assertThat(SessionV2Assembly.Configuration.from(global).provider()).isEqualTo(Provider.JDBC);
    }
    @Test @DisplayName("TC-30121 누락/none/memory/was·불완전epoch/namespace·비보안Origin은기동거부")
    void missingOrUnsupportedSettingsFailClosed(){
        assertThatThrownBy(()->SessionV2Assembly.Configuration.from(new MockEnvironment())).isInstanceOf(IllegalArgumentException.class);
        for(String provider:new String[]{"none","memory","was","JDBC",""})assertThatThrownBy(()->SessionV2Assembly.Configuration.from(environment(provider))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SessionV2Assembly.Configuration.from(environment("jdbc").withProperty("app.auth.session.domains.admin.epoch",""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SessionV2Assembly.Configuration.from(environment("jdbc").withProperty("app.auth.session.origin","https://user@host/path"))).isInstanceOf(IllegalArgumentException.class);
    }
}
