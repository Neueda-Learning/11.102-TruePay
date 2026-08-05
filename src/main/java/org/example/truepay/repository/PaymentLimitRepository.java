package org.example.truepay.repository;

import org.example.truepay.model.PaymentLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentLimitRepository extends JpaRepository<PaymentLimit, Long> {
    Optional<PaymentLimit> findByUserId(Long userId);
}

