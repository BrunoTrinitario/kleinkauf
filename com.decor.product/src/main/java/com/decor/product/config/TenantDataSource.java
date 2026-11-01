package com.decor.product.config;

import com.decor.product.dto.TenantDto;
import com.decor.product.services.UserService;
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
import java.util.List;

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
        if (tenants != null && !tenants.isEmpty()) {
            for (TenantDto tenant : tenants) {
                if (!this.existsSchema(tenant.schema()))
                    createSchemaIfNotExists(tenant.schema());
                runMigration(tenant.schema());
            }
        }
    }

    public boolean existsSchema(String name) {
        TenantDto tenant = userService.getTenant(name);
        if (tenant == null) {
            return false;
        }
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