package com.kleinkauf.gateway;

import com.kleinkauf.gateway.config.ReactiveRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ReactiveRedisConfigTest {

    // users stores the tenant as a JSON String with kleinkauf-common's redisTemplate
    // (hash values with GenericJackson2JsonRedisSerializer); the reactive template must read it the same way
    @Test
    void readsWhatTheUsersRedisTemplateWrites() {
        String tenantJson = "{\"id\":1,\"configId\":\"cfg\",\"schema\":\"tienda1\",\"enable\":true}";
        byte[] stored = new GenericJackson2JsonRedisSerializer().serialize(tenantJson);

        ReactiveRedisTemplate<String, Object> template =
                new ReactiveRedisConfig().reactiveJsonRedisTemplate(mock(ReactiveRedisConnectionFactory.class));
        Object read = template.getSerializationContext()
                .getHashValueSerializationPair()
                .read(ByteBuffer.wrap(stored));

        assertEquals(tenantJson, read);
    }
}
