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

    // users guarda el tenant como String JSON con el redisTemplate de kleinkauf-common
    // (hash values con GenericJackson2JsonRedisSerializer); el template reactivo tiene que leerlo igual
    @Test
    void leeLoQueEscribeElRedisTemplateDeUsers() {
        String tenantJson = "{\"id\":1,\"configId\":\"cfg\",\"schema\":\"tienda1\",\"enable\":true}";
        byte[] guardado = new GenericJackson2JsonRedisSerializer().serialize(tenantJson);

        ReactiveRedisTemplate<String, Object> template =
                new ReactiveRedisConfig().reactiveJsonRedisTemplate(mock(ReactiveRedisConnectionFactory.class));
        Object leido = template.getSerializationContext()
                .getHashValueSerializationPair()
                .read(ByteBuffer.wrap(guardado));

        assertEquals(tenantJson, leido);
    }
}
