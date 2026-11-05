package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name);
    boolean existsByName(String name);

    // Locks the row until the transaction ends so two stock movements do not overwrite each other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // Same as findByIdForUpdate for several products; ordered by id so two reservations do not deadlock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("SELECT p FROM Product p WHERE p.minStock > 0 AND p.stock <= p.minStock ORDER BY p.stock ASC")
    List<Product> findLowStock();

    @Query("SELECT p FROM Product p WHERE p.featured = true ORDER BY p.featuredOrder ASC NULLS LAST, p.name ASC")
    List<Product> findFeatured();

    List<Product> findAllByOrderByNameAsc();

    // Includes deleted ones (soft delete): the name is unique in the table even if the product is deleted
    @Query(value = "SELECT COUNT(*) > 0 FROM product WHERE name = :name", nativeQuery = true)
    boolean existsByNameIncludingDeleted(@Param("name") String name);

    // Same as the name: the slug is unique even if the product is deleted
    @Query(value = "SELECT COUNT(*) > 0 FROM product WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

    Optional<Product> findBySlug(String slug);

    // Store dashboard (DashboardService)

    // [category, count], highest to lowest; those without a category in a row with category null
    @Query("SELECT p.category, COUNT(p) FROM Product p GROUP BY p.category ORDER BY COUNT(p) DESC, p.category ASC NULLS LAST")
    List<Object[]> countByCategory();

    long countByPriceIsNull();

    long countByFeaturedTrue();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.images IS EMPTY")
    long countWithoutImages();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.minStock > 0 AND p.stock <= p.minStock")
    long countLowStock();

    long countByStockLessThanEqual(int stock);

    @Query("SELECT SUM(p.stock * p.cost) FROM Product p WHERE p.cost IS NOT NULL AND p.stock > 0")
    BigDecimal inventoryAtCost();

    @Query("SELECT SUM(p.stock * p.price) FROM Product p WHERE p.price IS NOT NULL AND p.stock > 0")
    BigDecimal inventoryAtPrice();

    long countByStockGreaterThanAndCostIsNull(int stock);

    long countByStockGreaterThanAndPriceIsNull(int stock);

    // [id, name] including deleted ones (soft delete): to name old movements
    @Query(value = "SELECT id, name FROM product WHERE id IN (:ids)", nativeQuery = true)
    List<Object[]> findNamesIncludingDeleted(@Param("ids") Collection<Long> ids);
}
