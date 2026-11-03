package com.kleinkauf.sales.repositories;

import com.kleinkauf.sales.entities.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    Optional<PaymentReceipt> findByIdAndOrder_Number(Long id, String number);
}
