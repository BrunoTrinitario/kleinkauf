package com.kleinkauf.product.repositories;

import com.kleinkauf.product.entities.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    // Locks the reservation so two releases of the same order do not return the stock twice
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservation r WHERE r.reference = :reference")
    Optional<StockReservation> findByReferenceForUpdate(@Param("reference") String reference);

    Optional<StockReservation> findByReference(String reference);
}
