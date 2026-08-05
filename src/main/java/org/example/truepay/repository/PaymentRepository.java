package org.example.truepay.repository;

import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    java.util.Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant timestamp);
}

