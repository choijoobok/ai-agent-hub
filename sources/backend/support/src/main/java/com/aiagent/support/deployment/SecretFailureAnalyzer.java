// @feat FEAT-302
package com.aiagent.support.deployment;

import org.springframework.boot.diagnostics.*;
import org.springframework.core.Ordered;

/** ConfigData 단계의 잘못된 암호문 위치도 원 예외/암호문을 진단에 싣지 않는다. */
public final class SecretFailureAnalyzer implements FailureAnalyzer,Ordered {
    @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
    @Override public FailureAnalysis analyze(Throwable failure) {
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        for(Throwable error=failure;error!=null&&seen.add(error);error=error.getCause()) {
            String message=error.getMessage();
            if(error instanceof SecretFailure||message!=null&&(message.contains("ENC(")||message.contains("ProtectedValue")))
                return new FailureAnalysis("설정 비밀 검증 실패. 원문과 암호문은 진단에 표시하지 않습니다.","운영 도구로 고객사·환경·정규 설정키와 키/암호문 짝을 검증하세요. 프로파일·설정 파일 위치는 암호화하지 않습니다.",new SecretFailure("CONFIGURATION",null));
        }
        return null;
    }
}
