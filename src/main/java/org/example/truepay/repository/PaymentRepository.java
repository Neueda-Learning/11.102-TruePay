package org.example.truepay.repository;

import jakarta.persistence.LockModeType;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    java.util.Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant timestamp);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    java.util.Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.user.id = :userId
              and p.status = :status
              and p.method in :methods
              and p.createdAt >= :fromInclusive
            """)
    BigDecimal sumSuccessfulAmountsByUserAndCreatedAtAfter(@Param("userId") Long userId,
                                                            @Param("status") PaymentStatus status,
                                                            @Param("methods") List<PaymentMethod> methods,
                                                            @Param("fromInclusive") Instant fromInclusive);

    @Modifying
    @Query("update Payment p set p.sourceAccount = null where p.sourceAccount.id = :accountId")
    int clearSourceAccountReferences(@Param("accountId") Long accountId);
}

