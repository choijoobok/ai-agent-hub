// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;
import com.aiagent.core.deployment.ConnectionRegistry;
import java.util.Set;
import org.springframework.context.annotation.*;

@Configuration
public class TargetConnectionConfiguration {
    /** 신뢰 기동의 불변 설정 검증 전용이다. HTTP/업무 실행 소비자는 자기 인가 빈을 별도 등록한다. */
    @Bean static ConnectionRegistry.Consumer consumer(){return new ConnectionRegistry.Consumer(){
        public String id(){return "target-system-settings";}
        public Set<ConnectionRegistry.Kind> kinds(){return Set.of(ConnectionRegistry.Kind.BUSINESS_API);}
        public void authorize(ConnectionRegistry.Kind kind,ConnectionRegistry.Operation operation){if(kind!=ConnectionRegistry.Kind.BUSINESS_API)throw new ConnectionRegistry.Rejected(ConnectionRegistry.Status.FORBIDDEN);}
    };}
}
