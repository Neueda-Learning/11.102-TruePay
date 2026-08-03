package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.truepay.model.UserProfile;
import org.example.truepay.service.AuthService;
import org.example.truepay.service.ProfileService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final ProfileService profileService;

    public AuthController(AuthService authService, SessionService sessionService, ProfileService profileService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.profileService = profileService;
    }

    @PostMapping("/register")
    public AuthUserResponse register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        UserProfile user = authService.register(request);
        sessionService.login(session, user.getId());
        return toResponse(user);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        UserProfile user = authService.login(request);
        sessionService.login(session, user.getId());
        return toResponse(user);
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        sessionService.logout(session);
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(profileService.getUserOrThrow(userId));
    }

    private AuthUserResponse toResponse(UserProfile user) {
        return new AuthUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getMobile());
    }
}

