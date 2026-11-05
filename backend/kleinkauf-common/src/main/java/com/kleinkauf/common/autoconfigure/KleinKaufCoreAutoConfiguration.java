package com.kleinkauf.common.autoconfigure;

import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.security.InternalTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/*
Beans shared by all microservices. Each one is only created if the service
has the required dependency and did not define its own bean.
 */
@AutoConfiguration(before = RedisAutoConfiguration.class)
public class KleinKaufCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("api.internal.key")
    public InternalTokenService internalTokenService(@Value("${api.internal.key}") String apiSecret) {
        return new InternalTokenService(apiSecret);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisTemplate.class)
    static class RedisConfig {

        @Bean
        @ConditionalOnMissingBean(name = "redisTemplate")
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);

            // Serializers so keys/values are readable
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

            return template;
        }
    }

    // Events for observability (Redis Streams). Servlet only: it is blocking and the gateway does not publish
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({StringRedisTemplate.class, ObjectMapper.class})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class EventsConfig {

        @Bean
        @ConditionalOnMissingBean
        public KleinKaufEventPublisher kleinkaufEventPublisher(RedisConnectionFactory factory) {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            return new KleinKaufEventPublisher(redis, JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Caffeine.class)
    static class CaffeineConfig {

        @Bean
        @ConditionalOnMissingBean(name = "caffeineCache")
        public Cache<String, Object> caffeineCache() {
            return Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .recordStats()
                    .build();
        }
    }
}
