package com.decor.product.config;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/*
Encargada de realmente dar el objeto de db connection
 */
@Component @RequiredArgsConstructor
public class DatabaseMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    @Autowired
    private TenantDataSource connectionProvider;
    private final DataSource dataSource;
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        if (!this.connectionProvider.existsSchema(tenantIdentifier)) {
            this.connectionProvider.createSchema(tenantIdentifier);
        }
        connection.setSchema(tenantIdentifier);
        return connection;
    }
    @Override public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema("public");
        connection.close();
    }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}