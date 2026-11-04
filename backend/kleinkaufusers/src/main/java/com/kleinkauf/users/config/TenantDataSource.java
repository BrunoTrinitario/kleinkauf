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
Encargada de definir el buffer de conexiones de todas lsa bds
 */
@Slf4j
@Component
public class TenantDataSource implements Serializable, TenantSchemaManager {

    @Autowired
    private DataSource dataSource;


    /*
    Al arrancar migra public y el schema de cada tienda no borrada, como products y sales: un cambio en las entidades
    llega a todas las tiendas en el deploy. Despues, prepareSchema solo crea el schema si falta (antes migraba cada
    tienda en su primera request despues de cada reinicio, y esa request tardaba).
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
                // Una tienda con problemas no frena el arranque del resto
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

    // Las tiendas existentes ya se migraron al arrancar: aca solo se crea el schema de una que todavia no lo tiene
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

            // roles.permission (int por usuario, siempre 0) se reemplazo por role_permissions.
            // ddl-auto=update no borra columnas y quedaba NOT NULL: sin esto fallaria cada alta de rol
            conn.createStatement().execute("ALTER TABLE " + schema + ".roles DROP COLUMN IF EXISTS permission");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}