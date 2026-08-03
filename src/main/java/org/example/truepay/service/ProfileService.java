package org.example.truepay.service;

import org.example.truepay.api.ProfileRequest;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfile createProfile(ProfileRequest request) {
        if (userProfileRepository.findByEmail(request.email()).isPresent()) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Email already exists");
        }

        UserProfile profile = new UserProfile();
        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setMobile(request.mobile());
        profile.setAppPinHash(passwordEncoder.encode(request.appPin()));
        profile.setPasswordHash(passwordEncoder.encode(request.password()));

        return userProfileRepository.save(profile);
    }

    public UserProfile getUserOrThrow(Long userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new TruePayException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
    }

    public void validateAppPin(UserProfile user, String appPin) {
        if (!passwordEncoder.matches(appPin, user.getAppPinHash())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Invalid app PIN");
        }
    }
}

