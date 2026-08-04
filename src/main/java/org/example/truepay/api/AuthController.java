package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.UserProfile;
import org.example.truepay.service.ProfileService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SessionService sessionService;
    private final ProfileService profileService;

    public AuthController(SessionService sessionService, ProfileService profileService) {
        this.sessionService = sessionService;
        this.profileService = profileService;
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

