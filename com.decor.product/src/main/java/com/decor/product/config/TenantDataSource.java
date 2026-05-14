package com.decor.product.config;

import com.decor.product.dto.TenantDto;
import com.decor.product.services.UserService;
import jakarta.annotation.PostConstruct;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Encargada de definir el buffer de conexiones de todas lsa bds
 */
@Component
public class TenantDataSource implements Serializable {

    private final HashMap<String, DataSource> dataSources = new HashMap<>();

    @Autowired
    private UserService userService;

    public DataSource getDataSource(String name) {

        if (dataSources.containsKey(name)) {
            return dataSources.get(name);
        }

        DataSource dataSource = createDataSource(name);

        if (dataSource != null) {

            initializeTenantDatabase(dataSource, name);

            dataSources.put(name, dataSource);
        }

        return dataSource;
    }

    @PostConstruct
    public Map<String, DataSource> getAll() {

        List<TenantDto> configList = userService.getTenants();

        Map<String, DataSource> result = new HashMap<>();

        for (TenantDto config : configList) {
            DataSource dataSource = getDataSource(config.name());
            result.put(config.name(), dataSource);
        }

        return result;
    }

    private DataSource createDataSource(String name) {

        TenantDto config = userService.getTenant(name);

        if (config != null) {

            return DataSourceBuilder.create()
                    .driverClassName(config.driverClassName())
                    .username(config.dbUser())
                    .password(config.dbPassword())
                    .url(config.url())
                    .build();
        }

        return null;
    }

    private void initializeTenantDatabase(DataSource dataSource, String tenant) {
        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(dataSource);

        emfb.setPackagesToScan("com.decor.product.entities");

        emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        emfb.setJpaPropertyMap(properties);

        try {
            emfb.afterPropertiesSet();
        } catch (Exception e) {
            System.err.println("Error actualizando el esquema para el tenant " + tenant + ": " + e.getMessage());
        } finally {
            emfb.destroy();
        }
    }
}