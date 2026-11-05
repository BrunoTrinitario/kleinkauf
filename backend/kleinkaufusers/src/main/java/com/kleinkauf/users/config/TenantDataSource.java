package com.kleinkauf.users.config;

import com.kleinkauf.common.servlet.tenant.TenantSchemaManager;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*
In charge of defining the connection pool of all the dbs
 */
@Slf4j
@Component
public class TenantDataSource implements Serializable, TenantSchemaManager {

    @Autowired
    private DataSource dataSource;


    /*
    At startup it migrates public and the schema of each non-deleted store, like products and sales: an entity change
    reaches every store on deploy. After that, prepareSchema only creates the schema if it is missing (it used to migrate each
    store on its first request after every restart, and that request was slow).
     */
    @PostConstruct
    public void initAllSchemas() {
        createSchemaIfNotExists("public");
        runMigration("public");
        for (String schema : tenantSchemas()) {
            try {
                createSchemaIfNotExists(schema);
                runMigration(schema);
            } catch (RuntimeException e) {
                // A store with problems does not hold back the startup of the rest
                log.error("No se pudo migrar el schema {}", schema, e);
            }
        }
    }

    private List<String> tenantSchemas() {
        List<String> schemas = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT schema FROM public.tenant WHERE deleted_at IS NULL")) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo las tiendas", e);
        }
        return schemas;
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

    // Existing stores were already migrated at startup: here only the schema of one that does not have it yet is created
    @Override
    public void prepareSchema(String tenantIdentifier) {
        if (!existsSchema(tenantIdentifier)) {
            createSchema(tenantIdentifier);
        }
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

            emfb.setPackagesToScan("com.kleinkauf.users.entities");
            emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            HashMap<String, Object> props = new HashMap<>();
            props.put("hibernate.hbm2ddl.auto", "update");
            props.put("hibernate.default_schema", schema);
            props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

            emfb.setJpaPropertyMap(props);

            emfb.afterPropertiesSet();
            emfb.destroy();

            // roles.permission (int per user, always 0) was replaced by role_permissions.
            // ddl-auto=update does not drop columns and it was left NOT NULL: without this every role insert would fail
            conn.createStatement().execute("ALTER TABLE " + schema + ".roles DROP COLUMN IF EXISTS permission");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}