package org.example.truepay.service;

import org.example.truepay.api.BeneficiaryRequest;
import org.example.truepay.model.Beneficiary;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.repository.BeneficiaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BeneficiaryService {
    private final BeneficiaryRepository beneficiaryRepository;
    private final ProfileService profileService;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository, ProfileService profileService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.profileService = profileService;
    }

    public Beneficiary create(Long userId, BeneficiaryRequest request) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(profileService.getUserOrThrow(userId));
        beneficiary.setName(request.name());
        beneficiary.setAccountNumber(request.accountNumber());
        beneficiary.setIfscCode(request.ifscCode().toUpperCase());
        return beneficiaryRepository.save(beneficiary);
    }

    public List<Beneficiary> list(Long userId) {
        profileService.getUserOrThrow(userId);
        return beneficiaryRepository.findByUserIdOrderByNameAsc(userId);
    }

    public Beneficiary getOwnedOrThrow(Long userId, Long beneficiaryId) {
        return beneficiaryRepository.findByIdAndUserId(beneficiaryId, userId)
                .orElseThrow(() -> new TruePayException(ErrorCode.BENEFICIARY_NOT_FOUND, HttpStatus.NOT_FOUND, "Beneficiary not found"));
    }

    public void delete(Long userId, Long beneficiaryId) {
        Beneficiary beneficiary = getOwnedOrThrow(userId, beneficiaryId);
        beneficiaryRepository.delete(beneficiary);
    }
}

