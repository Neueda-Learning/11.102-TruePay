package org.example.truepay.repository;

import jakarta.persistence.LockModeType;
import org.example.truepay.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUserId(Long userId);

    Optional<BankAccount> findByAccountNumberAndIfscCode(String accountNumber, String ifscCode);

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    Optional<BankAccount> findFirstByUserIdOrderByIdAsc(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BankAccount b where b.id = :id")
    Optional<BankAccount> findByIdForUpdate(Long id);

    @Query("select coalesce(sum(b.balance), 0) from BankAccount b where b.user.id = :userId")
    BigDecimal getCombinedBalance(Long userId);
}

