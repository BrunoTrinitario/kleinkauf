package com.decor.sales.repositories;

import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    boolean existsByNumber(String number);

    Optional<Order> findByNumber(String number);

    Page<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    // Numeros de los pedidos que vencieron sin comprobante (indice idx_orders_expiration)
    @Query("SELECT o.number FROM Order o WHERE o.status IN :statuses AND o.expiresAt < :now")
    List<String> findExpiredNumbers(@Param("statuses") Collection<OrderStatus> statuses, @Param("now") LocalDateTime now);
}
