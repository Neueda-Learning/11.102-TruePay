package org.example.truepay.service;

import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.UserProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    public static final String SESSION_USER_ID = "TRUEPAY_USER_ID";
    private static final String DEFAULT_USER_EMAIL = "demo@truepay.local";
    private static final String DEFAULT_USER_NAME = "TruePay Demo User";
    private static final String DEFAULT_USER_MOBILE = "9999999999";
    private static final String DEFAULT_APP_PIN = "1234";
    private static final String DEFAULT_PASSWORD = "demo-password";

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public SessionService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Long requireUserId(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_ID);
        if (value instanceof Long userId) {
            return userId;
        }

        Long userId = getOrCreateDefaultUserId();
        session.setAttribute(SESSION_USER_ID, userId);
        return userId;
    }

    private synchronized Long getOrCreateDefaultUserId() {
        return userProfileRepository.findByEmail(DEFAULT_USER_EMAIL)
                .map(UserProfile::getId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setFullName(DEFAULT_USER_NAME);
                    profile.setEmail(DEFAULT_USER_EMAIL);
                    profile.setMobile(DEFAULT_USER_MOBILE);
                    profile.setAppPinHash(passwordEncoder.encode(DEFAULT_APP_PIN));
                    profile.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
                    return userProfileRepository.save(profile).getId();
                });
    }
}

