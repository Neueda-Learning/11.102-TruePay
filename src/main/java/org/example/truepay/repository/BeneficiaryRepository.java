package org.example.truepay.repository;

import org.example.truepay.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserIdOrderByNameAsc(Long userId);

    Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);
}

