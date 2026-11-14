// @feat FEAT-001
package com.aiagent.support.deployment;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.*;
class ConnectionDisplayTest {
    @Test @DisplayName("TC-00112 TC-00113 현재 주소의 안전한 경로·포트와 비밀 제거")
    void projectsOnlyVerifiedSegments() {
        var display=ConnectionDisplay.project("https://user:password@example.test:8443/v1/private-token?token=secret#secret","BEARER",Set.of("v1"));
        assertThat(display.status()).isEqualTo("REDACTED");
        assertThat(display.baseUrl()).isEqualTo("https://example.test:8443/v1/[REDACTED]");
        assertThat(display.toString()).doesNotContain("user","password","private-token","secret");
        assertThat(display.authenticationType()).isEqualTo("BEARER");
        assertThat(ConnectionDisplay.project("https://example.test:8443/v1/orders","NONE",Set.of("v1","orders")).status()).isEqualTo("AVAILABLE");
        assertThat(ConnectionDisplay.project("https://example.test/v1/a%2Fb","NONE",Set.of("v1","a","b")).baseUrl()).endsWith("/v1/[REDACTED]");
        assertThat(ConnectionDisplay.project("https://example.test/%74oken","NONE",Set.of("v1")).baseUrl()).endsWith("/[REDACTED]");
    }
    @Test @DisplayName("TC-00113 잘못된 URL·인증 문자열·암호문 반사 차단")
    void neverReflectsUntrustedAuthOrInvalidAddresses() {
        for(String raw:new String[]{"ENC(secret)","https://<script>/x","file:///secret","https://example.test/\nsecret","https://example.test/%zz"}) {
            var display=ConnectionDisplay.project(raw,"UNKNOWN(secret)",Set.of());
            assertThat(display.baseUrl()).isNull();assertThat(display.authenticationType()).isNull();assertThat(display.status()).isEqualTo("UNAVAILABLE");
            assertThat(display.toString()).doesNotContain("secret","script");
        }
        assertThat(ConnectionDisplay.project("https://example.test/", "UNKNOWN(secret)",Set.of()).authenticationType()).isNull();
    }
}
