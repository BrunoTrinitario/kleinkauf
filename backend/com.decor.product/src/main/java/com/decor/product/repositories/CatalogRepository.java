package com.decor.product.repositories;

import com.decor.product.dto.catalog.CatalogSort;
import com.decor.product.dto.catalog.CategoryDto;
import com.decor.product.entities.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
Consultas del catalogo publico. JPQL armado a mano para poder ordenar por precio con
los productos sin precio ('consultar') siempre al final. Los borrados quedan afuera
por el @SQLRestriction de Product.
 */
@Repository
public class CatalogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public record Filter(String query, String category) {
    }

    public List<Product> search(Filter filter, CatalogSort sort, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);
        TypedQuery<Product> query = entityManager.createQuery(
                "SELECT p FROM Product p" + where + orderBy(sort), Product.class);
        params.forEach(query::setParameter);
        return query.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    public long count(Filter filter) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);
        TypedQuery<Long> query = entityManager.createQuery("SELECT COUNT(p) FROM Product p" + where, Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    public List<CategoryDto> categories() {
        return entityManager.createQuery(
                        "SELECT new com.decor.product.dto.catalog.CategoryDto(p.category, COUNT(p)) FROM Product p "
                                + "WHERE p.category IS NOT NULL AND p.category <> '' "
                                + "GROUP BY p.category ORDER BY p.category", CategoryDto.class)
                .getResultList();
    }

    private static String where(Filter filter, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filter.query() != null && !filter.query().isBlank()) {
            // Busca en nombre, descripcion y categoria sin distinguir mayusculas; '%' y '_' se buscan tal cual
            where.append(" AND (LOWER(p.name) LIKE :q ESCAPE '\\' OR LOWER(p.description) LIKE :q ESCAPE '\\'"
                    + " OR LOWER(p.category) LIKE :q ESCAPE '\\')");
            params.put("q", "%" + escapeLike(filter.query().trim().toLowerCase(Locale.ROOT)) + "%");
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            where.append(" AND LOWER(p.category) = :category");
            params.put("category", filter.category().trim().toLowerCase(Locale.ROOT));
        }
        return where.toString();
    }

    private static String orderBy(CatalogSort sort) {
        return switch (sort) {
            case NAME -> " ORDER BY p.name ASC";
            case PRICE_ASC -> " ORDER BY p.price ASC NULLS LAST, p.name ASC";
            case PRICE_DESC -> " ORDER BY p.price DESC NULLS LAST, p.name ASC";
            case BEST_SELLERS -> " ORDER BY p.soldCount DESC, p.name ASC";
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
