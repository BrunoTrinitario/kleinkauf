package com.kleinkauf.product.config;

import com.kleinkauf.product.util.Slugs;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/*
Completa el slug de los productos creados antes del catalogo publico.
Corre despues de migrar el schema de cada tenant (TenantDataSource); si no falta ninguno no hace nada.
El nombre del schema viene de la tabla de tenants (identificador validado: a-z, 0-9 y _).
 */
public class SlugBackfill {

    public static int fill(DataSource dataSource, String schema) throws SQLException {
        String table = schema + ".product";
        try (Connection conn = dataSource.getConnection()) {
            Set<String> used = new HashSet<>();
            Map<Long, String> missing = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, slug FROM " + table + " ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String slug = rs.getString("slug");
                    if (slug != null) used.add(slug);
                    else missing.put(rs.getLong("id"), rs.getString("name"));
                }
            }
            if (missing.isEmpty()) return 0;

            try (PreparedStatement update = conn.prepareStatement("UPDATE " + table + " SET slug = ? WHERE id = ?")) {
                for (Map.Entry<Long, String> product : missing.entrySet()) {
                    String slug = Slugs.unique(product.getValue(), used::contains);
                    used.add(slug);
                    update.setString(1, slug);
                    update.setLong(2, product.getKey());
                    update.addBatch();
                }
                update.executeBatch();
            }
            return missing.size();
        }
    }
}
