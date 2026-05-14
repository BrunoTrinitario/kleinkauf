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

    public Connection getTenantConnection(String name) throws SQLException {
        return this.connectionProvider.getDataSource(name).getConnection();
    }

    @Override public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }
    /*
    Este metodo es CLAVE, lo que hace es buscar en el buffer de conexiones,
    la de la base de datos que necesita y el schema necsario, usa en este caso
    el buffer es "TenantDataSource" y se setea al iniciar el container, importante
    luego el tema del tenantidentifier nace de varias cosas.
    1. Se setea el identificador en el hilo de ejecucion mediante el "TenantContext"
    2. Luego se consulta al "TenantIdentifierResolver" si esta devuelve el mismo nombre, si no null
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = null;
        if (tenantIdentifier.equals("public"))
            connection = getAnyConnection();
        else
            connection = this.getTenantConnection(tenantIdentifier);
        //connection.setSchema(tenantIdentifier);
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