// @feat FEAT-302
package com.aiagent.support.deployment;

import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** ConfigData가 암호문 위치/프로파일을 처리하기 전에 순환 입력을 거부한다. */
public final class SecretBootstrapGuard implements EnvironmentPostProcessor,Ordered {
    @Override public int getOrder(){return ConfigDataEnvironmentPostProcessor.ORDER-1;}
    @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app){check(env);}
    static void check(ConfigurableEnvironment env) {
        for(String name:List.of("spring.profiles.active","spring.profiles.include","spring.config.location","spring.config.additional-location","spring.config.import","spring.config.name","app.secrets.tenant","app.secrets.environment","app.secrets.allow-plaintext","AIHUB_CONFIG_MASTER_KEY_FILE")) {
            String value=env.getProperty(name);if(value!=null&&value.contains("ENC("))throw new SecretFailure("BOOTSTRAP",name);
        }
    }
}
