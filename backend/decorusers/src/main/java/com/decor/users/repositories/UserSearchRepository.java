package com.decor.users.repositories;

import com.decor.users.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
Listado paginado de usuarios del panel. JPQL armado a mano (como el catalogo de products) para no
mandarle a Postgres parametros null sin tipo. Los usuarios y roles borrados quedan afuera por el
@SQLRestriction de cada entidad.
 */
@Repository
public class UserSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // query: busca en nombre y email. role: solo usuarios que tengan ese rol
    public record Filter(String query, String role) {
    }

    public List<User> search(Filter filter, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);
        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u" + where + " ORDER BY LOWER(u.name) ASC, u.id ASC", User.class);
        params.forEach(query::setParameter);
        return query.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    public long count(Filter filter) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);
        TypedQuery<Long> query = entityManager.createQuery("SELECT COUNT(u) FROM User u" + where, Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private static String where(Filter filter, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filter.query() != null && !filter.query().isBlank()) {
            // Sin distinguir mayusculas; '%' y '_' se buscan tal cual
            where.append(" AND (LOWER(u.name) LIKE :q ESCAPE '\\' OR LOWER(u.email) LIKE :q ESCAPE '\\')");
            params.put("q", "%" + escapeLike(filter.query().trim().toLowerCase(Locale.ROOT)) + "%");
        }
        if (filter.role() != null && !filter.role().isBlank()) {
            where.append(" AND EXISTS (SELECT r FROM Role r WHERE r.user = u AND r.type = :role)");
            params.put("role", filter.role());
        }
        return where.toString();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
