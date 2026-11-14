// @feat FEAT-302
package com.aiagent.bootstrap.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** JDBC 배포는 미사용 Redis 자격을 바인딩하거나 연결하지 않는다. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.auth.session.provider",havingValue="redis")
@Import(DataRedisAutoConfiguration.class)
public class SessionRedisConfiguration {}
