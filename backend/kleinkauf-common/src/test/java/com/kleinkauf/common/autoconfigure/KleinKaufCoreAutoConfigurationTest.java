package com.kleinkauf.common.autoconfigure;

import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.security.InternalTokenService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class KleinKaufCoreAutoConfigurationTest {

    private static final String SECRET = "secretomagico123supermegaseguro123";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KleinKaufCoreAutoConfiguration.class, RedisAutoConfiguration.class));

    @Test
    void creaLosBeansComunes() {
        runner.withPropertyValues("api.internal.key=" + SECRET).run(context -> {
            assertThat(context).hasSingleBean(InternalTokenService.class);
            assertThat(context).hasBean("caffeineCache");
            assertThat(context.getBean(Cache.class)).isNotNull();

            RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
            assertThat(redisTemplate.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        });
    }

    @Test
    void elPublicadorDeEventosEsSoloParaServlet() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KleinKaufCoreAutoConfiguration.class, RedisAutoConfiguration.class))
                .run(context -> assertThat(context).hasSingleBean(KleinKaufEventPublisher.class));
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KleinKaufCoreAutoConfiguration.class, RedisAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(KleinKaufEventPublisher.class));
    }

    @Test
    void sinClaveInternaNoCreaElServicioDeTokens() {
        runner.run(context -> assertThat(context).doesNotHaveBean(InternalTokenService.class));
    }

    @Test
    void tokenInternoGeneradoEsValido() {
        InternalTokenService service = new InternalTokenService(SECRET);
        String token = service.generateMicroServiceToken("products");

        assertThat(service.validateInternalToken(token)).isTrue();
    }

    @Test
    void tokenFirmadoConOtraClaveEsInvalido() {
        InternalTokenService service = new InternalTokenService(SECRET);
        InternalTokenService otro = new InternalTokenService("otraClaveDistintaDeAlMenos32Bytes!!");

        assertThat(service.validateInternalToken(otro.generateMicroServiceToken("products"))).isFalse();
        assertThat(service.validateInternalToken(null)).isFalse();
        assertThat(service.validateInternalToken("basura")).isFalse();
    }
}
