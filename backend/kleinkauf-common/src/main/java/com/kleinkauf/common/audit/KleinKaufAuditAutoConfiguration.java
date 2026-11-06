package com.kleinkauf.common.audit;

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
Audit for the services with a database (users, products, sales), docs/features/audit.md:
- AuditHibernateListener: creates, updates and deletes of @Audited entities (hooked by KleinKaufServletAutoConfiguration).
- AuditLog: manual recording.
- The public.outbox_events table is created at startup and OutboxPublisher drains it into Redis Streams.
kleinkauf.audit.enabled=false turns it off entirely; kleinkauf.audit.publisher.enabled=false, only the sending.
It does not require a web app: without a request nothing is recorded (AuditRequest), so it is also present in
@DataJpaTest tests (see META-INF/spring/...AutoConfigureDataJpa.imports), where services need AuditLog.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, RedisAutoConfiguration.class})
@ConditionalOnClass(name = {"org.hibernate.event.spi.PostInsertEventListener", "org.springframework.jdbc.core.JdbcTemplate"})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = "kleinkauf.audit.enabled", havingValue = "true", matchIfMissing = true)
public class KleinKaufAuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KleinKaufAuditAutoConfiguration.class);

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

    // If the database is down at startup the service does not start anyway; here it is only logged just in case
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
    @ConditionalOnProperty(name = "kleinkauf.audit.publisher.enabled", havingValue = "true", matchIfMissing = true)
    static class PublisherConfig {

        @Bean
        public OutboxPublisher outboxPublisher(DataSource dataSource, RedisConnectionFactory factory,
                                               @Value("${kleinkauf.audit.publish-interval:2s}") Duration interval) {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            return new OutboxPublisher(dataSource, redis, interval, Clock.systemUTC());
        }
    }
}
