package com.decor.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;

/*
Auditoria de los servicios con base (users, products, sales), docs/auditoria.md:
- AuditHibernateListener: altas, cambios y bajas de las entidades @Audited (lo engancha DecorServletAutoConfiguration).
- AuditLog: el registro a mano.
- La tabla public.outbox_events se crea al arrancar y OutboxPublisher la vacia hacia Redis Streams.
decor.audit.enabled=false la apaga entera; decor.audit.publisher.enabled=false, solo el envio.
No exige una app web: sin request no se registra nada (AuditRequest), y asi tambien esta en los tests de
@DataJpaTest (ver META-INF/spring/...AutoConfigureDataJpa.imports), donde los servicios necesitan AuditLog.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, RedisAutoConfiguration.class})
@ConditionalOnClass(name = {"org.hibernate.event.spi.PostInsertEventListener", "org.springframework.jdbc.core.JdbcTemplate"})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = "decor.audit.enabled", havingValue = "true", matchIfMissing = true)
public class DecorAuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DecorAuditAutoConfiguration.class);

    static ObjectMapper auditMapper() {
        return JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    @Bean
    public AuditEvents auditEvents(@Value("${spring.application.name:}") String applicationName) {
        return new AuditEvents(AuditEvents.serviceName(applicationName), Clock.systemUTC());
    }

    @Bean
    public OutboxStore outboxStore() {
        return new OutboxStore(auditMapper());
    }

    @Bean
    public AuditHibernateListener auditHibernateListener(AuditEvents events, OutboxStore outbox) {
        return new AuditHibernateListener(events, outbox);
    }

    @Bean
    public AuditLog auditLog(AuditEvents events, OutboxStore outbox, DataSource dataSource) {
        return new AuditLog(events, outbox, new JdbcTemplate(dataSource));
    }

    // Si la base no esta al arrancar, el servicio no arranca igual; aca solo se loguea por las dudas
    @Bean
    public InitializingBean outboxTable(OutboxStore outbox, DataSource dataSource) {
        return () -> {
            try {
                outbox.createTable(new JdbcTemplate(dataSource));
            } catch (RuntimeException e) {
                log.error("No se pudo crear la tabla de la outbox de auditoria: {}", e.toString());
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(name = "decor.audit.publisher.enabled", havingValue = "true", matchIfMissing = true)
    static class PublisherConfig {

        @Bean
        public OutboxPublisher outboxPublisher(DataSource dataSource, RedisConnectionFactory factory,
                                               @Value("${decor.audit.publish-interval:2s}") Duration interval) {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            return new OutboxPublisher(dataSource, redis, interval, Clock.systemUTC());
        }
    }
}
