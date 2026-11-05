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
    Sales for the reports: orders in the statuses that count as a sale, with payment date in [from, to).
    channel and paymentMethod null = all. Orders paid before stage 3 have no paid_at: created_at is used.
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

    // Numbers of the orders that expired without a receipt (index idx_orders_expiration)
    @Query("SELECT o.number FROM Order o WHERE o.status IN :statuses AND o.expiresAt < :now")
    List<String> findExpiredNumbers(@Param("statuses") Collection<OrderStatus> statuses, @Param("now") LocalDateTime now);
}
