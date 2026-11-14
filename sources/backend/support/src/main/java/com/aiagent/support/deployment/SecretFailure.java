// @feat FEAT-302
package com.aiagent.support.deployment;

/** 값과 하위 암호/파일 예외를 담지 않는 설정 오류. */
public final class SecretFailure extends IllegalStateException {
    public SecretFailure(String code,String property){super("설정 비밀 검증 실패 ["+code+"] "+(SecretEnvelope.canonicalProperty(property)?property:"설정키"));}
}
