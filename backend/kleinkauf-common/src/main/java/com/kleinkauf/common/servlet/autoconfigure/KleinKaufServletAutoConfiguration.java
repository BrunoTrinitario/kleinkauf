package com.kleinkauf.common.servlet.autoconfigure;

import com.kleinkauf.common.audit.AuditHibernateListener;
import com.kleinkauf.common.audit.AuditIntegrator;
import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
import com.kleinkauf.common.servlet.tenant.DatabaseMultiTenantConnectionProvider;
import com.kleinkauf.common.servlet.tenant.TenantFilter;
import com.kleinkauf.common.servlet.tenant.TenantIdentifierResolver;
import com.kleinkauf.common.servlet.tenant.TenantSchemaManager;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Configuracion comun de los microservicios servlet:
- Hibernate multi-tenant por schema (el servicio tiene que tener un bean TenantSchemaManager)
- Filtro de tenant (kleinkauf.tenant.required=false lo hace opcional)
- Manejo global de excepciones
- Auditoria de las entidades @Audited, si esta activa (KleinKaufAuditAutoConfiguration)
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = HibernateJpaAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider")
@EnableConfigurationProperties(JpaProperties.class)
public class KleinKaufServletAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean
    public TenantFilter tenantFilter(@Value("${kleinkauf.tenant.required:true}") boolean tenantRequired,
                                     @Value("${kleinkauf.tenant.public-paths:}") List<String> publicPaths) {
        return new TenantFilter(tenantRequired, publicPaths);
    }

    @Bean
    @ConditionalOnMissingBean(CurrentTenantIdentifierResolver.class)
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    /*
    El TenantSchemaManager es un @Component del servicio; se inyecta @Lazy porque
    en algunos servicios depende de beans que a su vez necesitan el EntityManager.
     */
    @Bean
    @ConditionalOnMissingBean(MultiTenantConnectionProvider.class)
    public DatabaseMultiTenantConnectionProvider databaseMultiTenantConnectionProvider(
            DataSource dataSource, @Lazy TenantSchemaManager schemaManager) {
        return new DatabaseMultiTenantConnectionProvider(dataSource, schemaManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public JpaVendorAdapter jpaVendorAdapter() {
        return new HibernateJpaVendorAdapter();
    }

    /*
    Configuracion de HIBERNATE como ORM para cada schema de la multitenancy.
    Escanea las entidades del paquete de la aplicacion (@SpringBootApplication).
     */
    @Bean
    @ConditionalOnMissingBean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            BeanFactory beanFactory,
            JpaProperties jpaProperties,
            JpaVendorAdapter jpaVendorAdapter,
            DataSource dataSource,
            MultiTenantConnectionProvider<String> multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver,
            ObjectProvider<AuditHibernateListener> auditListener
    ) {

        Map<String, Object> jpaPropertiesMap = new HashMap<>(jpaProperties.getProperties());

        jpaPropertiesMap.put("hibernate.multiTenancy",
                "DATABASE");

        jpaPropertiesMap.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER,
                multiTenantConnectionProvider);

        jpaPropertiesMap.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER,
                currentTenantIdentifierResolver);

        jpaPropertiesMap.put(Environment.FORMAT_SQL, true);
        jpaPropertiesMap.put(Environment.SHOW_SQL, true);
        jpaPropertiesMap.put(Environment.HBM2DDL_AUTO, "update");

        AuditHibernateListener listener = auditListener.getIfAvailable();
        if (listener != null) {
            jpaPropertiesMap.put("hibernate.integrator_provider",
                    (IntegratorProvider) () -> List.of(new AuditIntegrator(listener)));
        }

        List<String> packages = AutoConfigurationPackages.get(beanFactory);

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();

        em.setDataSource(dataSource);
        em.setPackagesToScan(packages.toArray(new String[0]));
        em.setJpaVendorAdapter(jpaVendorAdapter);
        em.setJpaPropertyMap(jpaPropertiesMap);

        return em;
    }
}
