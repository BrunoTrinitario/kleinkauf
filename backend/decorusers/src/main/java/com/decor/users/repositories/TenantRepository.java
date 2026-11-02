package com.decor.users.repositories;

import com.decor.users.entities.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/*
Los tenants borrados (deleted_at) no existen para la app: las busquedas los excluyen.
existsBySchema los incluye, porque su schema y sus datos siguen en la base y el
identificador no se puede volver a usar.
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySchemaAndDeletedAtIsNull(String schema);
    Optional<Tenant> findByUrlAndDeletedAtIsNull(String url);
    Optional<Tenant> findByIdAndDeletedAtIsNull(Long id);
    List<Tenant> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    boolean existsByUrlAndDeletedAtIsNull(String url);
    boolean existsBySchema(String schema);
}
