package com.decor.common.servlet.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/*
Encargada de realmente dar el objeto de db connection
 */
public class DatabaseMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantSchemaManager schemaManager;

    public DatabaseMultiTenantConnectionProvider(DataSource dataSource, TenantSchemaManager schemaManager) {
        this.dataSource = dataSource;
        this.schemaManager = schemaManager;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }
    /*
    Busca la conexion y la apunta al schema del tenant. Antes le pide al
    TenantSchemaManager del servicio que el schema exista y este migrado.
    El tenantIdentifier lo setea el TenantFilter en el TenantContext y
    lo resuelve el TenantIdentifierResolver.
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        this.schemaManager.prepareSchema(tenantIdentifier);
        connection.setSchema(tenantIdentifier);
        return connection;
    }
    @Override public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema("public");
        connection.close();
    }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}
