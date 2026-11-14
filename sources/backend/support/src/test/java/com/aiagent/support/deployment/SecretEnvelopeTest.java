// @feat FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SecretEnvelopeTest {
    private final byte[] key=new byte[32];
    private final SecretEnvelope.Binding binding=new SecretEnvelope.Binding("synthetic","test","spring.datasource.password");
    @Test @DisplayName("TC-3023 표준 GCM·용도 결합·정규 형식·비밀 비노출")
    void boundEnvelope() {
        new java.security.SecureRandom().nextBytes(key);
        String one=SecretEnvelope.encrypt(key,binding,"시험 Secret!".toCharArray());
        String two=SecretEnvelope.encrypt(key,binding,"시험 Secret!".toCharArray());
        assertThat(one).startsWith("ENC(v1.A256GCM.").isNotEqualTo(two);
        assertThat(SecretEnvelope.decrypt(key,binding,one)).isEqualTo("시험 Secret!".toCharArray());
        assertThatThrownBy(()->SecretEnvelope.decrypt(new byte[32],binding,one)).isInstanceOf(SecretFailure.class).hasMessageNotContaining(one).hasMessageNotContaining("시험 Secret!");
        for(var other:List.of(new SecretEnvelope.Binding("other","test",binding.property()),new SecretEnvelope.Binding("synthetic","prod",binding.property()),new SecretEnvelope.Binding("synthetic","test","spring.mail.password")))
            assertThatThrownBy(()->SecretEnvelope.decrypt(key,other,one)).isInstanceOf(SecretFailure.class);
        for(String invalid:List.of(one.replace("v1.","v2."),one.replace("A256GCM","A128GCM"),one.substring(0,one.length()-1)+"=)","ENC(v1.A256GCM.AA)","ENC(v1.A256GCM."+"A".repeat(90000)+")","plaintext"))
            assertThatThrownBy(()->SecretEnvelope.decrypt(key,binding,invalid)).isInstanceOf(SecretFailure.class);
        assertThatThrownBy(()->SecretEnvelope.encrypt(key,binding,new char[16385])).isInstanceOf(SecretFailure.class);
        assertThatThrownBy(()->SecretEnvelope.key(Base64.getEncoder().encodeToString(key))).isInstanceOf(SecretFailure.class);
        assertThat(SecretEnvelope.key(Base64.getUrlEncoder().withoutPadding().encodeToString(key))).isEqualTo(key);
    }
    @Test @DisplayName("TC-3023 인증된 내부 payload도 길이/UTF-8/잔여 데이터 엄격 검사")
    void malformedPayload()throws Exception {
        var valid=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(valid);
        for(String field:List.of("v1","A256GCM","synthetic","test","spring.datasource.password","secret")){byte[] bytes=field.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
        byte[] negative=valid.toByteArray();java.nio.ByteBuffer.wrap(negative).putInt(-1);
        byte[] excessive=valid.toByteArray();java.nio.ByteBuffer.wrap(excessive).putInt(16385);
        byte[] utf8=valid.toByteArray();utf8[4]=(byte)0xff;
        byte[] remaining=Arrays.copyOf(valid.toByteArray(),valid.size()+1);
        for(byte[] invalid:List.of(negative,excessive,utf8,remaining,Arrays.copyOf(valid.toByteArray(),valid.size()-1))) {
            byte[] cipher=org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor.withSecretKey(new javax.crypto.spec.SecretKeySpec(key,"AES")).build().encrypt(invalid);
            String encoded="ENC(v1.A256GCM."+Base64.getUrlEncoder().withoutPadding().encodeToString(cipher)+")";
            assertThatThrownBy(()->SecretEnvelope.decrypt(key,binding,encoded)).isInstanceOf(SecretFailure.class).hasMessageNotContaining(encoded);
        }
    }
    @Test @DisplayName("TC-3024 새 키 부분 교체는 거부·이전 짝과 새 짝만 복구")
    void keyRotation() {
        byte[] next=new byte[32];new java.security.SecureRandom().nextBytes(next);
        String old=SecretEnvelope.encrypt(key,binding,"old-password".toCharArray());
        assertThatThrownBy(()->SecretEnvelope.decrypt(next,binding,old)).isInstanceOf(SecretFailure.class);
        String fresh=SecretEnvelope.encrypt(next,binding,SecretEnvelope.decrypt(key,binding,old));
        assertThat(SecretEnvelope.decrypt(next,binding,fresh)).isEqualTo(SecretEnvelope.decrypt(key,binding,old));
    }
}
