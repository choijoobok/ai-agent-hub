// @feat FEAT-501
package com.aiagent.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;

/** MVC의 기본 FlashMap/처리 이벤트가 WAS 세션을 암묵 조회하지 않도록 명시한다. */
@Configuration
public class StatelessMvcConfiguration {
    @Bean public static BeanPostProcessor statelessDispatcherEvents() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof FrameworkServlet servlet) servlet.setPublishEvents(false);
                return bean;
            }
        };
    }
    @Bean(name=DispatcherServlet.FLASH_MAP_MANAGER_BEAN_NAME)
    public FlashMapManager flashMapManager() {
        return new FlashMapManager() {
            @Override public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) { return null; }
            @Override public void saveOutputFlashMap(FlashMap values, HttpServletRequest request, HttpServletResponse response) {
                if (!values.isEmpty()) throw new IllegalStateException("API에서 WAS 세션 기반 redirect 속성을 사용할 수 없다");
            }
        };
    }
}
