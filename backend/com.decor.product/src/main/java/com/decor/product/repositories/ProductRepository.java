package com.decor.product.repositories;

import com.decor.product.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name);
    boolean existsByName(String name);

    // Bloquea la fila hasta terminar la transaccion para que dos movimientos de stock no se pisen
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // Igual que findByIdForUpdate para varios productos; ordenados por id para que dos reservas no se traben
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("SELECT p FROM Product p WHERE p.minStock > 0 AND p.stock <= p.minStock ORDER BY p.stock ASC")
    List<Product> findLowStock();

    @Query("SELECT p FROM Product p WHERE p.featured = true ORDER BY p.featuredOrder ASC NULLS LAST, p.name ASC")
    List<Product> findFeatured();

    List<Product> findAllByOrderByNameAsc();

    // Incluye los borrados (soft delete): el nombre es unique en la tabla aunque el producto este borrado
    @Query(value = "SELECT COUNT(*) > 0 FROM product WHERE name = :name", nativeQuery = true)
    boolean existsByNameIncludingDeleted(@Param("name") String name);

    // Igual que el nombre: el slug es unique aunque el producto este borrado
    @Query(value = "SELECT COUNT(*) > 0 FROM product WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

    Optional<Product> findBySlug(String slug);
}
