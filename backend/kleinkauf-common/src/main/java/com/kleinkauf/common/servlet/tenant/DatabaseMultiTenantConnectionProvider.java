package com.kleinkauf.common.servlet.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        // Se prepara antes de tomar la conexion: preparar usa sus propias conexiones, y si se tomara
        // primero, con varios pedidos a la vez cada uno quedaria con una esperando otra y el pool se traba
        ensurePrepared(tenantIdentifier);
        Connection connection = getAnyConnection();
        connection.setSchema(tenantIdentifier);
        return connection;
    }

    // Schemas ya preparados en este proceso: se preparan una sola vez (antes era en cada conexion,
    // y en users eso volvia a correr la migracion completa por cada consulta)
    private final Set<String> preparedSchemas = ConcurrentHashMap.newKeySet();

    private void ensurePrepared(String tenantIdentifier) {
        if (preparedSchemas.contains(tenantIdentifier)) {
            return;
        }
        synchronized (preparedSchemas) {
            if (!preparedSchemas.contains(tenantIdentifier)) {
                // Si falla no se marca: el proximo pedido lo vuelve a intentar
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
