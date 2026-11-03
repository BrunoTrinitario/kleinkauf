package com.kleinkauf.sales.repositories;

import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;
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

    /*
    Ventas para los reportes: pedidos en los estados que cuentan como venta, con fecha de pago en [from, to).
    channel y paymentMethod null = todos. Pedidos pagos antes de la etapa 3 no tienen paid_at: se usa created_at.
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items "
            + "WHERE o.status IN :statuses "
            + "AND COALESCE(o.paidAt, o.createdAt) >= :from AND COALESCE(o.paidAt, o.createdAt) < :to "
            + "AND (:channel IS NULL OR o.channel = :channel) "
            + "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)")
    List<Order> findSales(@Param("statuses") Collection<OrderStatus> statuses,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("channel") SalesChannel channel,
                          @Param("paymentMethod") PaymentMethod paymentMethod);

    // Numeros de los pedidos que vencieron sin comprobante (indice idx_orders_expiration)
    @Query("SELECT o.number FROM Order o WHERE o.status IN :statuses AND o.expiresAt < :now")
    List<String> findExpiredNumbers(@Param("statuses") Collection<OrderStatus> statuses, @Param("now") LocalDateTime now);
}
