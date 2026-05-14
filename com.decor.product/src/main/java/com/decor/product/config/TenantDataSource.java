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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Encargada de definir el buffer de conexiones de todas lsa bds
 */
@Component
public class TenantDataSource implements Serializable {

    @Autowired
    private UserService userService;

    @Autowired
    private DataSource dataSource;


    @PostConstruct
    public void initAllSchemas() {
        List<TenantDto> tenants = this.userService.getTenants();
        for (TenantDto tenant : tenants) {
            if (!this.existsSchema(tenant.dbName()))
                createSchemaIfNotExists(tenant.dbName());
            runMigration(tenant.dbName());
        }
    }

    public boolean existsSchema(String name) {
        String sql = "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error checking schema existence: " + name, e);
        }
    }

    public void createSchema(String name) {
        createSchemaIfNotExists(name);
        runMigration(name);
    }

    private void createSchemaIfNotExists(String schema) {

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
             st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runMigration(String schema) {

        try (Connection conn = dataSource.getConnection()) {

            conn.createStatement().execute("SET search_path TO " + schema);

            LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
            emfb.setDataSource(dataSource);

            emfb.setPackagesToScan("com.decor.product.entities");
            emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            HashMap<String, Object> props = new HashMap<>();
            props.put("hibernate.hbm2ddl.auto", "update");
            props.put("hibernate.default_schema", schema);
            props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

            emfb.setJpaPropertyMap(props);

            emfb.afterPropertiesSet();
            emfb.destroy();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}