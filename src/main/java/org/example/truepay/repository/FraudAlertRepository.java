package org.example.truepay.repository;

import org.example.truepay.model.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {
	long countByPaymentUserId(Long userId);
}

