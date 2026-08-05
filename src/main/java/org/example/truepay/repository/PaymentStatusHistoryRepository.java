package org.example.truepay.repository;

import org.example.truepay.model.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
    List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId);

    @Query("""
            select history
            from PaymentStatusHistory history
            join fetch history.payment payment
            where payment.user.id = :userId
            order by history.changedAt desc, history.id desc
            """)
    List<PaymentStatusHistory> findAuditHistoryByUserIdOrderByChangedAtDesc(@Param("userId") Long userId);
}

