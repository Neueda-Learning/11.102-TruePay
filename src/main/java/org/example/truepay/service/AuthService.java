package org.example.truepay.service;

import org.example.truepay.api.LoginRequest;
import org.example.truepay.api.RegisterRequest;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfile register(RegisterRequest request) {
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

    public UserProfile login(LoginRequest request) {
        UserProfile user = userProfileRepository.findByEmail(request.email())
                .orElseThrow(() -> new TruePayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new TruePayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return user;
    }
}

