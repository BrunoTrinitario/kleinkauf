package com.decor.users.config;

import com.decor.common.servlet.tenant.TenantSchemaManager;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

/*
Encargada de definir el buffer de conexiones de todas lsa bds
 */
@Component
public class TenantDataSource implements Serializable, TenantSchemaManager {

    @Autowired
    private DataSource dataSource;


    @PostConstruct
    public void initAllSchemas() {
        createSchemaIfNotExists("public");
        runMigration("public");
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

    @Override
    public void prepareSchema(String tenantIdentifier) {
        createSchema(tenantIdentifier);
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

            emfb.setPackagesToScan("com.decor.users.entities");
            emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            HashMap<String, Object> props = new HashMap<>();
            props.put("hibernate.hbm2ddl.auto", "update");
            props.put("hibernate.default_schema", schema);
            props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

            emfb.setJpaPropertyMap(props);

            emfb.afterPropertiesSet();
            emfb.destroy();

            // roles.permission (int por usuario, siempre 0) se reemplazo por role_permissions.
            // ddl-auto=update no borra columnas y quedaba NOT NULL: sin esto fallaria cada alta de rol
            conn.createStatement().execute("ALTER TABLE " + schema + ".roles DROP COLUMN IF EXISTS permission");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}