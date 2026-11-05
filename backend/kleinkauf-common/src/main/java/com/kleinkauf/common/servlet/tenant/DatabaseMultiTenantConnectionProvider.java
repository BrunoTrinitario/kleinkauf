package com.kleinkauf.common.servlet.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
In charge of actually providing the db connection object
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
    Gets the connection and points it at the tenant's schema. Before that it asks the
    service's TenantSchemaManager to make sure the schema exists and is migrated.
    The tenantIdentifier is set by TenantFilter in TenantContext and
    resolved by TenantIdentifierResolver.
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        // Prepared before taking the connection: preparing uses its own connections, and if it took one
        // first, with several requests at once each would hold one while waiting for another and the pool would lock up
        ensurePrepared(tenantIdentifier);
        Connection connection = getAnyConnection();
        connection.setSchema(tenantIdentifier);
        return connection;
    }

    // Schemas already prepared in this process: they are prepared only once (it used to be on every connection,
    // and in users that re-ran the full migration on every query)
    private final Set<String> preparedSchemas = ConcurrentHashMap.newKeySet();

    private void ensurePrepared(String tenantIdentifier) {
        if (preparedSchemas.contains(tenantIdentifier)) {
            return;
        }
        synchronized (preparedSchemas) {
            if (!preparedSchemas.contains(tenantIdentifier)) {
                // If it fails it is not marked: the next request tries again
                this.schemaManager.prepareSchema(tenantIdentifier);
                preparedSchemas.add(tenantIdentifier);
            }
        }
    }
    @Override public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema("public");
        connection.close();
    }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}
